//! 负责把模型拓扑拆分为受 48 骨骼 uniform palette 约束的 GPU 蒙皮批次。

use std::collections::{hash_map::Entry, HashMap};

use thiserror::Error;

use super::{RuntimeVertex, SubMesh, VertexWeight};

pub const MAX_PALETTE_BONES: usize = 48;
pub const IDENTITY_BONE: u32 = u32::MAX;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct GpuSkinningVertex {
    pub source_vertex: u32,
    pub local_bones: [u16; 4],
    pub weights: [u8; 4],
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GpuSkinningDraw {
    pub material_id: i32,
    pub first_index: u32,
    pub index_count: u32,
    pub palette_offset: u32,
    pub palette_count: u32,
    pub center: [f32; 3],
}

#[derive(Debug)]
pub struct GpuSkinningTopology {
    pub vertices: Vec<GpuSkinningVertex>,
    pub indices: Vec<u32>,
    pub palette_bones: Vec<u32>,
    pub draws: Vec<GpuSkinningDraw>,
}

#[derive(Debug, Error, Clone, Eq, PartialEq)]
pub enum GpuSkinningError {
    #[error("顶点与权重数量不一致: vertices={vertices}, weights={weights}")]
    WeightCountMismatch { vertices: usize, weights: usize },
    #[error("子网格索引范围越界: {begin}..{end}/{index_count}")]
    SubmeshOutOfBounds {
        begin: usize,
        end: usize,
        index_count: usize,
    },
    #[error("子网格索引数不是三角形的整数倍: {0}")]
    NonTriangleSubmesh(usize),
    #[error("顶点索引越界: {index}/{vertex_count}")]
    VertexOutOfBounds { index: u32, vertex_count: usize },
    #[error("顶点 {vertex} 包含无法归一化的骨骼权重")]
    InvalidWeights { vertex: u32 },
    #[error("GPU 蒙皮拓扑尺寸超出 u32")]
    SizeOverflow,
}

impl GpuSkinningTopology {
    /// 构建 GPU 蒙皮拓扑。
    ///
    /// `head_mask` 是可选的头部顶点掩码（true = 该顶点属于脖子分界线以上的头部）。
    /// 传入后，凡是命中掩码的三角形会被整块丢弃 —— 材质级隐藏救不了「身体皮肤和
    /// 脖子皮肤在同一个材质里」的模型（整块隐藏会把四肢一起弄没），
    /// 只有在索引层面剔除才能既去掉脖子又不动身体。
    pub fn build(
        vertices: &[RuntimeVertex],
        indices: &[u32],
        weights: &[VertexWeight],
        submeshes: &[SubMesh],
        bone_count: usize,
        head_mask: Option<&[bool]>,
    ) -> Result<Self, GpuSkinningError> {
        if vertices.len() != weights.len() {
            return Err(GpuSkinningError::WeightCountMismatch {
                vertices: vertices.len(),
                weights: weights.len(),
            });
        }
        validate_indices(indices, vertices.len())?;

        let mut topology = Self {
            vertices: Vec::with_capacity(vertices.len()),
            indices: Vec::with_capacity(indices.len()),
            palette_bones: Vec::new(),
            draws: Vec::with_capacity(submeshes.len()),
        };

        for submesh in submeshes {
            topology.append_submesh(vertices, indices, weights, submesh, bone_count, head_mask)?;
        }
        Ok(topology)
    }

    fn append_submesh(
        &mut self,
        vertices: &[RuntimeVertex],
        indices: &[u32],
        weights: &[VertexWeight],
        submesh: &SubMesh,
        bone_count: usize,
        head_mask: Option<&[bool]>,
    ) -> Result<(), GpuSkinningError> {
        let begin = submesh.begin_index as usize;
        let end = begin
            .checked_add(submesh.index_count as usize)
            .ok_or(GpuSkinningError::SizeOverflow)?;
        if end > indices.len() {
            return Err(GpuSkinningError::SubmeshOutOfBounds {
                begin,
                end,
                index_count: indices.len(),
            });
        }
        if !(end - begin).is_multiple_of(3) {
            return Err(GpuSkinningError::NonTriangleSubmesh(end - begin));
        }

        let mut cursor = begin;
        while cursor < end {
            let batch_begin = cursor;
            let mut palette = Vec::with_capacity(MAX_PALETTE_BONES);
            let mut palette_lookup = HashMap::with_capacity(MAX_PALETTE_BONES);
            let mut kept_triangles = 0_usize;

            while cursor < end {
                let triangle = &indices[cursor..cursor + 3];
                // 命中头部掩码的三角形整块丢弃：
                // 只丢掉一部分顶点会让剩下的顶点被身体骨骼拉出长尖刺。
                if is_head_masked(triangle, head_mask) {
                    cursor += 3;
                    continue;
                }
                let triangle_bones = triangle_bones(triangle, weights, bone_count)?;
                let missing = triangle_bones
                    .iter()
                    .filter(|bone| !palette_lookup.contains_key(*bone))
                    .count();
                if !palette.is_empty() && palette.len() + missing > MAX_PALETTE_BONES {
                    break;
                }
                for bone in triangle_bones {
                    if let Entry::Vacant(entry) = palette_lookup.entry(bone) {
                        let local = u16::try_from(palette.len())
                            .map_err(|_| GpuSkinningError::SizeOverflow)?;
                        entry.insert(local);
                        palette.push(bone);
                    }
                }
                kept_triangles += 1;
                cursor += 3;
            }

            // 整个子网格都被头部掩码吃掉时不要产生空批次：
            // 空批次的 palette 长度为 0，Java 侧会绑到 0 长度的骨骼槽位区间。
            // 没有掩码时必然至少保留一个三角形，这个条件不会成立（行为与原来完全一致）。
            if kept_triangles == 0 {
                continue;
            }

            self.append_batch(
                vertices,
                indices,
                weights,
                submesh.material_id,
                batch_begin,
                cursor,
                bone_count,
                &palette,
                &palette_lookup,
                head_mask,
            )?;
        }
        Ok(())
    }

    #[expect(
        clippy::too_many_arguments,
        reason = "批次编码需要显式传入拓扑切片与 palette"
    )]
    fn append_batch(
        &mut self,
        vertices: &[RuntimeVertex],
        indices: &[u32],
        weights: &[VertexWeight],
        material_id: i32,
        begin: usize,
        end: usize,
        bone_count: usize,
        palette: &[u32],
        palette_lookup: &HashMap<u32, u16>,
        head_mask: Option<&[bool]>,
    ) -> Result<(), GpuSkinningError> {
        let first_index = checked_u32(self.indices.len())?;
        let palette_offset = checked_u32(self.palette_bones.len())?;
        self.palette_bones.extend_from_slice(palette);

        let mut vertex_remap = HashMap::<u32, u32>::new();
        let mut center = [0.0_f32; 3];
        let mut center_count = 0_u32;
        let mut kept_indices = 0_u32;
        let mut cursor = begin;
        while cursor < end {
            let triangle = &indices[cursor..cursor + 3];
            // 与 append_submesh 用同一把尺子过滤，保证 palette 里有的骨骼
            // 一定出现在保留下来的三角形上。
            if !is_head_masked(triangle, head_mask) {
                for source_index in triangle.iter().copied() {
                    let mapped = if let Some(mapped) = vertex_remap.get(&source_index) {
                        *mapped
                    } else {
                        let source = vertices.get(source_index as usize).ok_or(
                            GpuSkinningError::VertexOutOfBounds {
                                index: source_index,
                                vertex_count: vertices.len(),
                            },
                        )?;
                        let encoded = encode_weight(
                            source_index,
                            &weights[source_index as usize],
                            bone_count,
                            palette_lookup,
                        )?;
                        let mapped = checked_u32(self.vertices.len())?;
                        self.vertices.push(encoded);
                        vertex_remap.insert(source_index, mapped);
                        center[0] += source.position.x;
                        center[1] += source.position.y;
                        center[2] += source.position.z;
                        center_count += 1;
                        mapped
                    };
                    self.indices.push(mapped);
                }
                kept_indices += 3;
            }
            cursor += 3;
        }

        if center_count != 0 {
            let inverse = 1.0 / center_count as f32;
            center[0] *= inverse;
            center[1] *= inverse;
            center[2] *= inverse;
        }
        self.draws.push(GpuSkinningDraw {
            material_id,
            first_index,
            index_count: kept_indices,
            palette_offset,
            palette_count: checked_u32(palette.len())?,
            center,
        });
        Ok(())
    }
}

/// 三角形是否命中头部顶点掩码。
///
/// 只要有任意一个顶点属于头部就整块丢弃：部分丢弃会让剩下的顶点
/// 被身体骨骼拉到脖子位置，形成从身体里长出来的尖刺。
fn is_head_masked(triangle: &[u32], head_mask: Option<&[bool]>) -> bool {
    let Some(mask) = head_mask else {
        return false;
    };
    triangle
        .iter()
        .any(|vertex| mask.get(*vertex as usize).copied().unwrap_or(false))
}

fn validate_indices(indices: &[u32], vertex_count: usize) -> Result<(), GpuSkinningError> {
    if let Some(index) = indices
        .iter()
        .copied()
        .find(|index| *index as usize >= vertex_count)
    {
        return Err(GpuSkinningError::VertexOutOfBounds {
            index,
            vertex_count,
        });
    }
    Ok(())
}

fn triangle_bones(
    triangle: &[u32],
    weights: &[VertexWeight],
    bone_count: usize,
) -> Result<Vec<u32>, GpuSkinningError> {
    let mut bones = Vec::with_capacity(12);
    for vertex in triangle.iter().copied() {
        let weight = weights
            .get(vertex as usize)
            .ok_or(GpuSkinningError::VertexOutOfBounds {
                index: vertex,
                vertex_count: weights.len(),
            })?;
        let (source_bones, source_weights) = weight_components(weight);
        for (&bone, &weight) in source_bones.iter().zip(source_weights.iter()) {
            if weight > f32::EPSILON {
                let bone = valid_bone(bone, bone_count);
                if !bones.contains(&bone) {
                    bones.push(bone);
                }
            }
        }
    }
    if bones.is_empty() {
        bones.push(IDENTITY_BONE);
    }
    Ok(bones)
}

fn encode_weight(
    vertex: u32,
    weight: &VertexWeight,
    bone_count: usize,
    palette_lookup: &HashMap<u32, u16>,
) -> Result<GpuSkinningVertex, GpuSkinningError> {
    let (source_bones, source_weights) = weight_components(weight);
    let weights = quantize_weights(vertex, source_weights)?;
    let fallback = palette_lookup.get(&IDENTITY_BONE).copied().unwrap_or(0);
    let mut local_bones = [fallback; 4];
    for index in 0..4 {
        if weights[index] == 0 {
            continue;
        }
        let bone = valid_bone(source_bones[index], bone_count);
        local_bones[index] = palette_lookup
            .get(&bone)
            .copied()
            .ok_or(GpuSkinningError::InvalidWeights { vertex })?;
    }
    Ok(GpuSkinningVertex {
        source_vertex: vertex,
        local_bones,
        weights,
    })
}

fn weight_components(weight: &VertexWeight) -> ([i32; 4], [f32; 4]) {
    match weight {
        VertexWeight::Bdef1 { bone } => ([*bone, 0, 0, 0], [1.0, 0.0, 0.0, 0.0]),
        VertexWeight::Bdef2 { bones, weight } | VertexWeight::Sdef { bones, weight, .. } => (
            [bones[0], bones[1], 0, 0],
            [*weight, 1.0 - *weight, 0.0, 0.0],
        ),
        VertexWeight::Bdef4 { bones, weights } | VertexWeight::Qdef { bones, weights } => {
            (*bones, *weights)
        }
    }
}

fn quantize_weights(vertex: u32, source: [f32; 4]) -> Result<[u8; 4], GpuSkinningError> {
    if source
        .iter()
        .any(|weight| !weight.is_finite() || *weight < 0.0)
    {
        return Err(GpuSkinningError::InvalidWeights { vertex });
    }
    let sum: f32 = source.iter().sum();
    if sum <= f32::EPSILON {
        return Ok([u8::MAX, 0, 0, 0]);
    }

    let normalized = source.map(|weight| weight / sum);
    let scaled = normalized.map(|weight| weight * u8::MAX as f32);
    let mut quantized = scaled.map(|weight| weight.floor() as u8);
    let mut remainder = u8::MAX as i32 - quantized.iter().map(|value| *value as i32).sum::<i32>();
    while remainder > 0 {
        let mut selected = 0;
        let mut largest_fraction = f32::NEG_INFINITY;
        for index in 0..4 {
            if quantized[index] == u8::MAX {
                continue;
            }
            let fraction = scaled[index] - quantized[index] as f32;
            if fraction > largest_fraction {
                largest_fraction = fraction;
                selected = index;
            }
        }
        quantized[selected] += 1;
        remainder -= 1;
    }
    Ok(quantized)
}

fn valid_bone(bone: i32, bone_count: usize) -> u32 {
    usize::try_from(bone)
        .ok()
        .filter(|index| *index < bone_count)
        .and_then(|index| u32::try_from(index).ok())
        .unwrap_or(IDENTITY_BONE)
}

fn checked_u32(value: usize) -> Result<u32, GpuSkinningError> {
    u32::try_from(value).map_err(|_| GpuSkinningError::SizeOverflow)
}

#[cfg(test)]
mod tests {
    use glam::{Vec2, Vec3};

    use super::*;

    #[test]
    fn quantized_weights_should_sum_to_255() {
        let encoded = quantize_weights(0, [0.1, 0.2, 0.3, 0.4]).expect("weights should encode");

        assert_eq!(encoded.iter().map(|value| *value as u16).sum::<u16>(), 255);
    }

    #[test]
    fn topology_should_split_batches_when_palette_exceeds_limit() {
        let triangle_count = MAX_PALETTE_BONES / 3 + 1;
        let vertices = (0..triangle_count * 3)
            .map(|index| RuntimeVertex {
                position: Vec3::new(index as f32, 0.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            })
            .collect::<Vec<_>>();
        let indices = (0..vertices.len() as u32).collect::<Vec<_>>();
        let weights = (0..vertices.len())
            .map(|index| VertexWeight::Bdef1 { bone: index as i32 })
            .collect::<Vec<_>>();
        let submeshes = [SubMesh {
            material_id: 0,
            begin_index: 0,
            index_count: indices.len() as u32,
        }];

        let topology = GpuSkinningTopology::build(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            vertices.len(),
            None,
        )
        .expect("topology should build");

        assert_eq!(topology.draws.len(), 2);
    }

    /// 造一个「身体 + 头部」两段的模型：前 3 个顶点是身体（骨骼 0），
    /// 后 3 个顶点是头（骨骼 1），两个子网格各一个三角形。
    fn head_body_fixture() -> (
        Vec<RuntimeVertex>,
        Vec<u32>,
        Vec<VertexWeight>,
        Vec<SubMesh>,
    ) {
        let vertices = vec![
            RuntimeVertex {
                position: Vec3::new(0.0, 5.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
            RuntimeVertex {
                position: Vec3::new(1.0, 5.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
            RuntimeVertex {
                position: Vec3::new(0.0, 6.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
            RuntimeVertex {
                position: Vec3::new(0.0, 15.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
            RuntimeVertex {
                position: Vec3::new(1.0, 15.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
            RuntimeVertex {
                position: Vec3::new(0.0, 16.0, 0.0),
                normal: Vec3::Y,
                uv: Vec2::ZERO,
            },
        ];
        let indices = vec![0, 1, 2, 3, 4, 5];
        let weights = vec![
            VertexWeight::Bdef1 { bone: 0 },
            VertexWeight::Bdef1 { bone: 0 },
            VertexWeight::Bdef1 { bone: 0 },
            VertexWeight::Bdef1 { bone: 1 },
            VertexWeight::Bdef1 { bone: 1 },
            VertexWeight::Bdef1 { bone: 1 },
        ];
        let submeshes = vec![
            SubMesh {
                material_id: 0,
                begin_index: 0,
                index_count: 3,
            },
            SubMesh {
                material_id: 1,
                begin_index: 3,
                index_count: 3,
            },
        ];
        (vertices, indices, weights, submeshes)
    }

    #[test]
    fn head_mask_should_drop_triangles_that_touch_masked_vertices() {
        let (vertices, indices, weights, submeshes) = head_body_fixture();
        // 掩码命中顶点 3/4/5（头部）
        let mask = [false, false, false, true, true, true];

        let topology =
            GpuSkinningTopology::build(&vertices, &indices, &weights, &submeshes, 2, Some(&mask))
                .expect("topology should build");

        // 头部三角形整块消失，索引缓冲里只剩身体的 3 个索引
        assert_eq!(topology.indices.len(), 3);
        assert_eq!(topology.draws.len(), 1);
        assert_eq!(topology.draws[0].material_id, 0);
        assert_eq!(topology.draws[0].first_index, 0);
        assert_eq!(topology.draws[0].index_count, 3);
        // 调色板只统计保留下来的三角形用到的骨骼（头骨 1 不该出现）
        assert_eq!(topology.palette_bones, vec![0]);
    }

    #[test]
    fn head_mask_should_keep_topology_identical_when_disabled() {
        let (vertices, indices, weights, submeshes) = head_body_fixture();

        let baseline =
            GpuSkinningTopology::build(&vertices, &indices, &weights, &submeshes, 2, None)
                .expect("topology should build");
        let empty_mask = vec![false; vertices.len()];
        let masked = GpuSkinningTopology::build(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            2,
            Some(&empty_mask),
        )
        .expect("topology should build");

        assert_eq!(baseline.indices, masked.indices);
        assert_eq!(baseline.palette_bones, masked.palette_bones);
        assert_eq!(baseline.draws.len(), masked.draws.len());
        for (left, right) in baseline.draws.iter().zip(masked.draws.iter()) {
            assert_eq!(left.first_index, right.first_index);
            assert_eq!(left.index_count, right.index_count);
            assert_eq!(left.palette_count, right.palette_count);
        }
    }

    #[test]
    fn partially_masked_triangle_should_be_dropped_entirely() {
        let (vertices, indices, weights, submeshes) = head_body_fixture();
        // 只掩码身体三角形的最后一个顶点 → 整个身体三角形都必须被丢掉
        let mask = [false, false, true, true, true, true];

        let topology =
            GpuSkinningTopology::build(&vertices, &indices, &weights, &submeshes, 2, Some(&mask))
                .expect("topology should build");

        assert!(topology.indices.is_empty());
        assert!(topology.draws.is_empty());
    }
}
