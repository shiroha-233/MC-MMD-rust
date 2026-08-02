use std::collections::{HashMap, HashSet};

use glam::{Mat4, Vec3, Vec4};

use super::{RuntimeVertex, SubMesh, VertexWeight};

/// 第一人称专用索引布局，以及模型加载时预计算的头颈候选三角形。
#[derive(Clone, Debug)]
pub(crate) struct FirstPersonMesh {
    pub indices: Vec<u32>,
    pub submeshes: Vec<SubMesh>,
    pub(crate) triangle_classes: Vec<TriangleClass>,
    pub(crate) dynamic_vertices: Vec<u32>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum TriangleClass {
    Body,
    Head,
    HeadNeckBoundary,
}

const HEAD_WEIGHT_THRESHOLD: f32 = 0.5;
const BOUNDARY_RINGS: usize = 3;
const HEAD_BOUNDS_EXPANSION: f32 = 1.6;
const WELD_EPSILON_FACTOR: f32 = 0.0001;
const FRUSTUM_MARGIN: f32 = 1.18;

pub(crate) fn build_first_person_mesh(
    vertices: &[RuntimeVertex],
    indices: &[u32],
    weights: &[VertexWeight],
    submeshes: &[SubMesh],
    head_bones: &HashSet<usize>,
) -> Option<FirstPersonMesh> {
    if vertices.is_empty() || indices.is_empty() || submeshes.is_empty() || head_bones.is_empty() {
        return None;
    }

    let triangle_count = indices.len() / 3;
    if triangle_count == 0 || triangle_count * 3 != indices.len() {
        return None;
    }

    let vertex_head_weights: Vec<f32> = weights
        .iter()
        .map(|weight| head_weight(weight, head_bones))
        .collect();
    let mut triangle_classes = vec![TriangleClass::Body; triangle_count];
    let mut head_vertices = HashSet::new();

    for (triangle_index, triangle) in indices.chunks_exact(3).enumerate() {
        let is_head = triangle.iter().all(|&vertex_index| {
            vertex_head_weights
                .get(vertex_index as usize)
                .copied()
                .unwrap_or(0.0)
                >= HEAD_WEIGHT_THRESHOLD
        });
        if is_head {
            triangle_classes[triangle_index] = TriangleClass::Head;
            head_vertices.extend(triangle.iter().copied());
        }
    }
    if head_vertices.is_empty() {
        return None;
    }

    // 头颈候选只向头部边界扩张有限邻接环，并限制在头部包围范围附近。
    let (head_center, head_radius) = head_bounds(vertices, &head_vertices)?;
    let max_boundary_distance = head_radius * HEAD_BOUNDS_EXPANSION;
    let weld_epsilon = (head_radius * WELD_EPSILON_FACTOR).max(f32::EPSILON * 16.0);
    let spatial_vertices = build_spatial_vertex_map(vertices, weld_epsilon);
    let mut frontier = head_vertices;
    for _ in 0..BOUNDARY_RINGS {
        // PMX 常在材质、UV 或法线接缝复制顶点；先按位置焊接，避免肉眼连续但索引断开。
        let welded_frontier =
            collect_welded_vertices(vertices, &spatial_vertices, &frontier, weld_epsilon);
        let mut next_frontier = HashSet::new();
        for (triangle_index, triangle) in indices.chunks_exact(3).enumerate() {
            if triangle_classes[triangle_index] != TriangleClass::Body
                || !triangle.iter().any(|index| welded_frontier.contains(index))
            {
                continue;
            }
            let near_head = triangle.iter().any(|&vertex_index| {
                vertices
                    .get(vertex_index as usize)
                    .map(|vertex| vertex.position.distance(head_center) <= max_boundary_distance)
                    .unwrap_or(false)
            });
            if near_head {
                triangle_classes[triangle_index] = TriangleClass::HeadNeckBoundary;
                next_frontier.extend(triangle.iter().copied());
            }
        }
        if next_frontier.is_empty() {
            break;
        }
        frontier = next_frontier;
    }

    let mut dynamic_vertices = HashSet::new();
    for (triangle_index, triangle) in indices.chunks_exact(3).enumerate() {
        if triangle_classes[triangle_index] == TriangleClass::HeadNeckBoundary {
            dynamic_vertices.extend(triangle.iter().copied());
        }
    }

    let mut mesh = FirstPersonMesh {
        indices: Vec::with_capacity(indices.len()),
        submeshes: Vec::with_capacity(submeshes.len()),
        triangle_classes,
        dynamic_vertices: dynamic_vertices.into_iter().collect(),
    };
    rebuild_indices(&mut mesh, indices, submeshes, None)?;
    (!mesh.indices.is_empty()).then_some(mesh)
}

type SpatialCell = (i64, i64, i64);

fn spatial_cell(position: Vec3, cell_size: f32) -> SpatialCell {
    (
        (position.x / cell_size).floor() as i64,
        (position.y / cell_size).floor() as i64,
        (position.z / cell_size).floor() as i64,
    )
}

fn build_spatial_vertex_map(
    vertices: &[RuntimeVertex],
    cell_size: f32,
) -> HashMap<SpatialCell, Vec<u32>> {
    let mut cells = HashMap::<SpatialCell, Vec<u32>>::new();
    for (index, vertex) in vertices.iter().enumerate() {
        cells
            .entry(spatial_cell(vertex.position, cell_size))
            .or_default()
            .push(index as u32);
    }
    cells
}

fn collect_welded_vertices(
    vertices: &[RuntimeVertex],
    cells: &HashMap<SpatialCell, Vec<u32>>,
    frontier: &HashSet<u32>,
    epsilon: f32,
) -> HashSet<u32> {
    let mut welded = frontier.clone();
    let epsilon_squared = epsilon * epsilon;
    for &frontier_index in frontier {
        let Some(frontier_vertex) = vertices.get(frontier_index as usize) else {
            continue;
        };
        let (cell_x, cell_y, cell_z) = spatial_cell(frontier_vertex.position, epsilon);
        for offset_x in -1..=1 {
            for offset_y in -1..=1 {
                for offset_z in -1..=1 {
                    let cell = (cell_x + offset_x, cell_y + offset_y, cell_z + offset_z);
                    let Some(indices) = cells.get(&cell) else {
                        continue;
                    };
                    for &index in indices {
                        if vertices
                            .get(index as usize)
                            .map(|vertex| {
                                vertex.position.distance_squared(frontier_vertex.position)
                                    <= epsilon_squared
                            })
                            .unwrap_or(false)
                        {
                            welded.insert(index);
                        }
                    }
                }
            }
        }
    }
    welded
}

impl FirstPersonMesh {
    pub(crate) fn dynamic_vertices(&self) -> &[u32] {
        &self.dynamic_vertices
    }
}

/// 使用本帧姿态和实际绘制矩阵刷新第一人称 EBO。
pub(crate) fn refresh_first_person_mesh(
    mesh: &mut FirstPersonMesh,
    positions: &[Vec3],
    indices: &[u32],
    submeshes: &[SubMesh],
    model_view: Mat4,
    projection: Mat4,
) -> Option<()> {
    if !model_view.is_finite() || !projection.is_finite() {
        return rebuild_indices(mesh, indices, submeshes, None);
    }
    let mvp = projection * model_view;
    rebuild_indices(mesh, indices, submeshes, Some((positions, mvp)))
}

fn rebuild_indices(
    mesh: &mut FirstPersonMesh,
    source_indices: &[u32],
    source_submeshes: &[SubMesh],
    dynamic_view: Option<(&[Vec3], Mat4)>,
) -> Option<()> {
    mesh.indices.clear();
    mesh.submeshes.clear();

    for submesh in source_submeshes {
        let begin = submesh.begin_index as usize;
        let end = begin.checked_add(submesh.index_count as usize)?;
        let source = source_indices.get(begin..end)?;
        if source.len() % 3 != 0 {
            return None;
        }

        let output_begin = mesh.indices.len();
        for (local_triangle, triangle) in source.chunks_exact(3).enumerate() {
            let triangle_index = begin / 3 + local_triangle;
            let class = *mesh.triangle_classes.get(triangle_index)?;
            let remove = match class {
                TriangleClass::Head => true,
                TriangleClass::HeadNeckBoundary => dynamic_view
                    .map(|(positions, mvp)| {
                        triangle_intersects_expanded_frustum(triangle, positions, mvp)
                    })
                    .unwrap_or(false),
                TriangleClass::Body => false,
            };
            if !remove {
                mesh.indices.extend_from_slice(triangle);
            }
        }

        mesh.submeshes.push(SubMesh::new(
            output_begin as u32,
            (mesh.indices.len() - output_begin) as u32,
            submesh.material_id,
        ));
    }
    Some(())
}

fn triangle_intersects_expanded_frustum(triangle: &[u32], positions: &[Vec3], mvp: Mat4) -> bool {
    let mut clip = [Vec4::ZERO; 3];
    for (slot, &vertex_index) in clip.iter_mut().zip(triangle.iter()) {
        let Some(position) = positions.get(vertex_index as usize) else {
            return false;
        };
        *slot = mvp * position.extend(1.0);
    }

    // 顶点均在相机后方时不可见；其余情况使用扩张后的齐次裁剪平面做保守相交测试。
    if clip.iter().all(|point| point.w <= f32::EPSILON) {
        return false;
    }
    let outside = |predicate: &dyn Fn(Vec4) -> bool| clip.iter().all(|&point| predicate(point));
    if outside(&|point| point.x < -FRUSTUM_MARGIN * point.w)
        || outside(&|point| point.x > FRUSTUM_MARGIN * point.w)
        || outside(&|point| point.y < -FRUSTUM_MARGIN * point.w)
        || outside(&|point| point.y > FRUSTUM_MARGIN * point.w)
        || outside(&|point| point.z < -point.w)
        || outside(&|point| point.z > point.w)
    {
        return false;
    }
    true
}

fn head_bounds(vertices: &[RuntimeVertex], head_vertices: &HashSet<u32>) -> Option<(Vec3, f32)> {
    let mut center = Vec3::ZERO;
    let mut count = 0usize;
    for &index in head_vertices {
        center += vertices.get(index as usize)?.position;
        count += 1;
    }
    center /= count as f32;
    let radius = head_vertices
        .iter()
        .filter_map(|&index| vertices.get(index as usize))
        .map(|vertex| vertex.position.distance(center))
        .fold(0.0f32, f32::max);
    (radius > f32::EPSILON).then_some((center, radius))
}

fn head_weight(weight: &VertexWeight, head_bones: &HashSet<usize>) -> f32 {
    let contains = |bone: i32| bone >= 0 && head_bones.contains(&(bone as usize));
    match weight {
        VertexWeight::Bdef1 { bone } => f32::from(contains(*bone)),
        VertexWeight::Bdef2 { bones, weight } | VertexWeight::Sdef { bones, weight, .. } => {
            (if contains(bones[0]) { *weight } else { 0.0 })
                + if contains(bones[1]) {
                    1.0 - *weight
                } else {
                    0.0
                }
        }
        VertexWeight::Bdef4 { bones, weights } | VertexWeight::Qdef { bones, weights } => bones
            .iter()
            .zip(weights.iter())
            .filter_map(|(&bone, &weight)| contains(bone).then_some(weight))
            .sum(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vertex(x: f32, y: f32, z: f32) -> RuntimeVertex {
        RuntimeVertex {
            position: Vec3::new(x, y, z),
            normal: Vec3::Z,
            uv: glam::Vec2::ZERO,
        }
    }

    #[test]
    fn removes_the_complete_head_instead_of_preserving_an_open_back_shell() {
        let vertices = vec![
            vertex(-1.0, 0.0, 0.0),
            vertex(1.0, 0.0, 0.0),
            vertex(0.0, 1.0, 0.0),
            vertex(-1.0, -1.0, 0.0),
            vertex(1.0, -1.0, 0.0),
        ];
        let weights = vec![VertexWeight::Bdef1 { bone: 1 }; 3]
            .into_iter()
            .chain(vec![VertexWeight::Bdef1 { bone: 0 }; 2])
            .collect::<Vec<_>>();
        let indices = vec![0, 1, 2, 0, 3, 4];
        let submeshes = vec![SubMesh::new(0, 6, 7)];
        let mesh = build_first_person_mesh(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            &HashSet::from([1]),
        )
        .unwrap();

        assert_eq!(mesh.indices, vec![0, 3, 4]);
        assert_eq!(mesh.submeshes[0].index_count, 3);
    }

    #[test]
    fn removes_a_boundary_triangle_when_it_enters_the_expanded_view() {
        let vertices = vec![
            vertex(-0.2, 0.0, 0.0),
            vertex(0.2, 0.0, 0.0),
            vertex(0.0, 0.2, 0.0),
            vertex(0.0, -0.2, 0.0),
            vertex(0.2, -0.2, 0.0),
        ];
        let weights = vec![VertexWeight::Bdef1 { bone: 1 }; 3]
            .into_iter()
            .chain(vec![VertexWeight::Bdef1 { bone: 0 }; 2])
            .collect::<Vec<_>>();
        let indices = vec![0, 1, 2, 0, 3, 4];
        let submeshes = vec![SubMesh::new(0, 6, 0)];
        let mut mesh = build_first_person_mesh(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            &HashSet::from([1]),
        )
        .unwrap();
        let positions = vertices
            .iter()
            .map(|vertex| vertex.position)
            .collect::<Vec<_>>();

        refresh_first_person_mesh(
            &mut mesh,
            &positions,
            &indices,
            &submeshes,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
        )
        .unwrap();
        assert!(mesh.indices.is_empty());
    }

    #[test]
    fn finds_a_boundary_across_duplicated_seam_vertices() {
        let vertices = vec![
            vertex(-0.2, 0.0, 0.0),
            vertex(0.2, 0.0, 0.0),
            vertex(0.0, 0.2, 0.0),
            // 后颈三角形使用另一组索引，并带有导出器可能产生的微小位置误差。
            vertex(-0.2 + 0.000001, 0.0, 0.0),
            vertex(0.2, -0.2, 0.0),
            vertex(-0.2, -0.2, 0.0),
        ];
        let weights = vec![VertexWeight::Bdef1 { bone: 1 }; 3]
            .into_iter()
            .chain(vec![VertexWeight::Bdef1 { bone: 0 }; 3])
            .collect::<Vec<_>>();
        let indices = vec![0, 1, 2, 3, 4, 5];
        let submeshes = vec![SubMesh::new(0, 6, 0)];
        let mut mesh = build_first_person_mesh(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            &HashSet::from([1]),
        )
        .unwrap();
        let positions = vertices
            .iter()
            .map(|vertex| vertex.position)
            .collect::<Vec<_>>();

        refresh_first_person_mesh(
            &mut mesh,
            &positions,
            &indices,
            &submeshes,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
        )
        .unwrap();

        assert!(mesh.indices.is_empty());
    }

    #[test]
    fn keeps_a_boundary_triangle_outside_the_expanded_view() {
        let vertices = vec![
            vertex(-0.2, 0.0, 0.0),
            vertex(0.2, 0.0, 0.0),
            vertex(0.0, 0.2, 0.0),
            vertex(0.0, -0.2, 0.0),
            vertex(0.2, -0.2, 0.0),
        ];
        let weights = vec![VertexWeight::Bdef1 { bone: 1 }; 3]
            .into_iter()
            .chain(vec![VertexWeight::Bdef1 { bone: 0 }; 2])
            .collect::<Vec<_>>();
        let indices = vec![0, 1, 2, 0, 3, 4];
        let submeshes = vec![SubMesh::new(0, 6, 0)];
        let mut mesh = build_first_person_mesh(
            &vertices,
            &indices,
            &weights,
            &submeshes,
            &HashSet::from([1]),
        )
        .unwrap();
        let positions = vertices
            .iter()
            .map(|vertex| vertex.position + Vec3::new(5.0, 0.0, 0.0))
            .collect::<Vec<_>>();

        refresh_first_person_mesh(
            &mut mesh,
            &positions,
            &indices,
            &submeshes,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
        )
        .unwrap();
        assert_eq!(mesh.indices, vec![0, 3, 4]);
    }
}
