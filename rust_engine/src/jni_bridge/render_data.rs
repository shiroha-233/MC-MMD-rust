//! 负责将 CPU/GPU 蒙皮数据编码为 Minecraft 1.21.5 可上传的受检缓冲区。

use crate::model::{
    CpuHeadFilter, GpuSkinningDraw, GpuSkinningTopology, MmdModel, SubMesh, IDENTITY_BONE,
    MAX_PALETTE_BONES,
};
use crate::texture::Texture;
use std::sync::Arc;

use super::{BridgeError, BridgeResult};

pub const ABI_VERSION: i32 = 4;
pub const VERTEX_STRIDE: usize = 28;
pub const GPU_VERTEX_STRIDE: usize = 36;
pub const DRAW_COMMAND_STRIDE: usize = 48;
pub const MESH_DESCRIPTION_SIZE: usize = 88;
pub const MATRIX_SIZE: usize = 16 * std::mem::size_of::<f32>();

const MESH_GPU_SKINNING_ACTIVE: u32 = 1 << 0;
const MESH_GPU_SKINNING_SUPPORTED: u32 = 1 << 1;
const DRAW_VISIBLE: u32 = 1 << 0;
const DRAW_DOUBLE_SIDED: u32 = 1 << 1;
const DRAW_TRANSPARENT: u32 = 1 << 2;
const DRAW_OUTLINE: u32 = 1 << 3;

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum IndexFormat {
    Short = 2,
    Int = 4,
}

impl IndexFormat {
    pub const fn byte_width(self) -> usize {
        self as usize
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct MeshDescription {
    pub vertex_count: usize,
    pub index_count: usize,
    pub draw_count: usize,
    pub vertex_stride: usize,
    pub index_format: IndexFormat,
    pub vertex_revision: u64,
    pub pose_revision: u64,
    pub material_revision: u64,
    pub gpu_skinning_supported: bool,
    pub gpu_skinning_enabled: bool,
    pub palette_entry_count: usize,
}

impl MeshDescription {
    pub fn vertex_bytes(self) -> BridgeResult<usize> {
        checked_size(self.vertex_count, self.vertex_stride)
    }

    pub fn index_bytes(self) -> BridgeResult<usize> {
        checked_size(self.index_count, self.index_format.byte_width())
    }

    pub fn draw_command_bytes(self) -> BridgeResult<usize> {
        checked_size(self.draw_count, DRAW_COMMAND_STRIDE)
    }

    pub fn palette_bytes(self) -> BridgeResult<usize> {
        checked_size(self.palette_entry_count, MATRIX_SIZE)
    }
}

pub fn describe_mesh(model: &MmdModel) -> BridgeResult<MeshDescription> {
    let topology = model.gpu_skinning_topology().ok();
    let gpu_skinning_supported = topology.is_some();
    let gpu_skinning_enabled = model.gpu_skinning_enabled() && gpu_skinning_supported;
    let (vertex_count, index_count, draw_count, vertex_stride, palette_entry_count) =
        if gpu_skinning_enabled {
            let topology = topology
                .ok_or_else(|| BridgeError::InvalidData("GPU 蒙皮已启用但拓扑不可用".to_owned()))?;
            (
                topology.vertices.len(),
                topology.indices.len(),
                topology.draws.len(),
                GPU_VERTEX_STRIDE,
                topology.palette_bones.len(),
            )
        } else {
            validate_indices(model)?;
            (
                model.vertices.len(),
                // 头部掩码生效时索引缓冲被过滤过，上传长度必须跟着变，
                // 否则 Java 侧会按旧长度重建缓冲并画到越界的索引。
                model.cpu_index_count(),
                model.submeshes.len(),
                VERTEX_STRIDE,
                0,
            )
        };
    Ok(MeshDescription {
        vertex_count,
        index_count,
        draw_count,
        vertex_stride,
        index_format: choose_index_format(vertex_count),
        vertex_revision: if gpu_skinning_enabled {
            model.geometry_revision()
        } else {
            model.render_revision()
        },
        pose_revision: model.pose_revision(),
        material_revision: model.material_revision(),
        gpu_skinning_supported,
        gpu_skinning_enabled,
        palette_entry_count,
    })
}

pub fn copy_mesh_description(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    let description = describe_mesh(model)?;
    let destination = require_direct_destination(Some(destination), MESH_DESCRIPTION_SIZE)?;
    destination[..MESH_DESCRIPTION_SIZE].fill(0);
    put_u32(destination, 0, ABI_VERSION as u32);
    put_u32(destination, 4, checked_u32(description.vertex_count)?);
    put_u32(destination, 8, checked_u32(description.index_count)?);
    put_u32(destination, 12, checked_u32(description.draw_count)?);
    put_u32(destination, 16, checked_u32(description.vertex_stride)?);
    put_u32(destination, 20, description.index_format as u32);
    put_u64(destination, 24, checked_u64(description.vertex_bytes()?)?);
    put_u64(destination, 32, checked_u64(description.index_bytes()?)?);
    put_u64(destination, 40, description.vertex_revision);
    let mut flags = 0_u32;
    if description.gpu_skinning_enabled {
        flags |= MESH_GPU_SKINNING_ACTIVE;
    }
    if description.gpu_skinning_supported {
        flags |= MESH_GPU_SKINNING_SUPPORTED;
    }
    put_u32(destination, 48, flags);
    put_u32(destination, 52, DRAW_COMMAND_STRIDE as u32);
    put_u32(
        destination,
        56,
        checked_u32(description.palette_entry_count)?,
    );
    put_u32(destination, 60, MAX_PALETTE_BONES as u32);
    put_u64(destination, 64, checked_u64(description.palette_bytes()?)?);
    put_u64(destination, 72, description.pose_revision);
    put_u64(destination, 80, description.material_revision);
    Ok(MESH_DESCRIPTION_SIZE)
}

pub fn copy_indices(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    let description = describe_mesh(model)?;
    let byte_count = description.index_bytes()?;
    let destination = require_direct_destination(Some(destination), byte_count)?;
    let topology;
    let indices = if description.gpu_skinning_enabled {
        topology = require_gpu_topology(model)?;
        topology.indices.as_slice()
    } else {
        // CPU 路径：头部掩码生效时写过滤后的索引缓冲；
        // 掩码关闭时直接写原始索引（与改动前逐字节一致）。
        match model.cpu_head_filter() {
            Some(filter) => filter.indices.as_slice(),
            None => model.indices.as_slice(),
        }
    };

    match description.index_format {
        IndexFormat::Short => {
            for (chunk, index) in destination[..byte_count]
                .chunks_exact_mut(IndexFormat::Short.byte_width())
                .zip(indices.iter().copied())
            {
                let index = u16::try_from(index).map_err(|_| {
                    BridgeError::InvalidData(format!("SHORT 索引超出范围: {index}"))
                })?;
                chunk.copy_from_slice(&index.to_le_bytes());
            }
        }
        IndexFormat::Int => {
            for (chunk, index) in destination[..byte_count]
                .chunks_exact_mut(IndexFormat::Int.byte_width())
                .zip(indices.iter().copied())
            {
                chunk.copy_from_slice(&index.to_le_bytes());
            }
        }
    }

    Ok(byte_count)
}

pub fn copy_frame_vertices(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    if model.gpu_skinning_enabled() {
        return copy_gpu_frame_vertices(model, destination);
    }
    let byte_count = checked_size(model.vertices.len(), VERTEX_STRIDE)?;
    let destination = require_direct_destination(Some(destination), byte_count)?;

    for (index, (chunk, source)) in destination[..byte_count]
        .chunks_exact_mut(VERTEX_STRIDE)
        .zip(model.vertices.iter())
        .enumerate()
    {
        let position = model
            .update_positions
            .get(index)
            .copied()
            .unwrap_or(source.position);
        let normal = model
            .update_normals
            .get(index)
            .copied()
            .unwrap_or(source.normal);
        let uv = model.update_uvs.get(index).copied().unwrap_or(source.uv);

        put_f32(chunk, 0, position.x);
        put_f32(chunk, 4, position.y);
        put_f32(chunk, 8, position.z);
        put_f32(chunk, 12, uv.x);
        put_f32(chunk, 16, uv.y);
        chunk[20..24].fill(u8::MAX);
        chunk[24] = quantize_normal(normal.x) as u8;
        chunk[25] = quantize_normal(normal.y) as u8;
        chunk[26] = quantize_normal(normal.z) as u8;
        chunk[27] = 0;
    }

    Ok(byte_count)
}

pub fn copy_gpu_frame_vertices(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    let topology = require_gpu_topology(model)?;
    let byte_count = checked_size(topology.vertices.len(), GPU_VERTEX_STRIDE)?;
    let destination = require_direct_destination(Some(destination), byte_count)?;

    for (chunk, gpu_vertex) in destination[..byte_count]
        .chunks_exact_mut(GPU_VERTEX_STRIDE)
        .zip(&topology.vertices)
    {
        let index = gpu_vertex.source_vertex as usize;
        let source = model
            .vertices
            .get(index)
            .ok_or_else(|| BridgeError::InvalidData(format!("GPU 顶点源索引越界: {index}")))?;
        let position = model
            .update_positions
            .get(index)
            .copied()
            .unwrap_or(source.position);
        let uv = model.update_uvs.get(index).copied().unwrap_or(source.uv);

        put_f32(chunk, 0, position.x);
        put_f32(chunk, 4, position.y);
        put_f32(chunk, 8, position.z);
        chunk[12..16].copy_from_slice(&gpu_vertex.weights);
        put_f32(chunk, 16, uv.x);
        put_f32(chunk, 20, uv.y);
        put_u16(chunk, 24, gpu_vertex.local_bones[0]);
        put_u16(chunk, 26, gpu_vertex.local_bones[1]);
        put_u16(chunk, 28, gpu_vertex.local_bones[2]);
        put_u16(chunk, 30, gpu_vertex.local_bones[3]);
        chunk[32] = quantize_normal(source.normal.x) as u8;
        chunk[33] = quantize_normal(source.normal.y) as u8;
        chunk[34] = quantize_normal(source.normal.z) as u8;
        chunk[35] = 0;
    }

    Ok(byte_count)
}

pub fn copy_bone_palette_matrices(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    let description = describe_mesh(model)?;
    if !description.gpu_skinning_enabled {
        return Err(BridgeError::InvalidData(
            "模型未启用 GPU palette 蒙皮".to_owned(),
        ));
    }
    let topology = require_gpu_topology(model)?;
    let byte_count = description.palette_bytes()?;
    let destination = require_direct_destination(Some(destination), byte_count)?;
    let matrices = model.bone_manager.get_skinning_matrices();

    for (entry, bone) in topology.palette_bones.iter().copied().enumerate() {
        let matrix = if bone == IDENTITY_BONE {
            glam::Mat4::IDENTITY
        } else {
            matrices
                .get(bone as usize)
                .copied()
                .unwrap_or(glam::Mat4::IDENTITY)
        };
        let offset = entry * MATRIX_SIZE;
        for (component, value) in matrix.to_cols_array().into_iter().enumerate() {
            put_f32(destination, offset + component * 4, value);
        }
    }

    Ok(byte_count)
}

pub fn copy_draw_commands(model: &MmdModel, destination: &mut [u8]) -> BridgeResult<usize> {
    let byte_count = describe_mesh(model)?.draw_command_bytes()?;
    let destination = require_direct_destination(Some(destination), byte_count)?;
    if model.gpu_skinning_enabled() {
        let topology = require_gpu_topology(model)?;
        for (chunk, draw) in destination[..byte_count]
            .chunks_exact_mut(DRAW_COMMAND_STRIDE)
            .zip(&topology.draws)
        {
            encode_draw_command(model, chunk, DrawSource::Gpu(draw))?;
        }
    } else {
        // CPU 路径：头部掩码生效时索引缓冲被过滤过，
        // draw command 的 firstIndex/indexCount 必须指向过滤后的区间，
        // 语义与 GPU 路径保持一致（都是相对各自索引缓冲的偏移）。
        let filter = model.cpu_head_filter();
        for (chunk, (submesh_index, submesh)) in destination[..byte_count]
            .chunks_exact_mut(DRAW_COMMAND_STRIDE)
            .zip(model.submeshes.iter().enumerate())
        {
            let source = cpu_draw_source(model, filter, submesh_index, submesh)?;
            encode_draw_command(model, chunk, source)?;
        }
    }

    Ok(byte_count)
}

/// CPU 路径的 draw 来源：子网格 + 它在（可能已过滤的）索引缓冲里的实际区间。
fn cpu_draw_source(
    model: &MmdModel,
    filter: Option<&CpuHeadFilter>,
    submesh_index: usize,
    submesh: &SubMesh,
) -> BridgeResult<DrawSource<'static>> {
    let (first_index, index_count, center) = match filter {
        Some(filter) => {
            let (first_index, index_count) =
                filter.ranges.get(submesh_index).copied().unwrap_or((0, 0));
            let begin = first_index as usize;
            let end = begin + index_count as usize;
            let center = static_center(model, &filter.indices[begin..end]);
            (first_index, index_count, center)
        }
        None => {
            let begin = submesh.begin_index as usize;
            let end = begin
                .checked_add(submesh.index_count as usize)
                .ok_or(BridgeError::SizeOverflow)?;
            if end > model.indices.len() {
                return Err(BridgeError::InvalidData(format!(
                    "子网格索引范围越界: {begin}..{end}/{}",
                    model.indices.len()
                )));
            }
            let center = static_center(model, &model.indices[begin..end]);
            (submesh.begin_index, submesh.index_count, center)
        }
    };

    Ok(DrawSource::Cpu {
        material_id: submesh.material_id,
        first_index,
        index_count,
        center,
    })
}

pub fn copy_texture_pixels(texture: &Texture, destination: &mut [u8]) -> BridgeResult<usize> {
    let expected = checked_size(
        checked_size(texture.width as usize, texture.height as usize)?,
        4,
    )?;
    if texture.data.len() != expected {
        return Err(BridgeError::InvalidData(format!(
            "纹理不是 RGBA8: 需要 {expected} 字节，实际 {} 字节",
            texture.data.len()
        )));
    }
    let destination = require_direct_destination(Some(destination), expected)?;
    destination[..expected].copy_from_slice(&texture.data);
    Ok(expected)
}

pub fn copy_matrix(matrix: glam::Mat4, destination: &mut [u8]) -> BridgeResult<usize> {
    let destination = require_direct_destination(Some(destination), MATRIX_SIZE)?;
    for (index, value) in matrix.to_cols_array().into_iter().enumerate() {
        put_f32(destination, index * std::mem::size_of::<f32>(), value);
    }
    Ok(MATRIX_SIZE)
}

pub(crate) fn require_direct_destination(
    destination: Option<&mut [u8]>,
    required: usize,
) -> BridgeResult<&mut [u8]> {
    let destination = destination.ok_or(BridgeError::NotDirectBuffer)?;
    if destination.len() < required {
        return Err(BridgeError::BufferTooSmall {
            required,
            actual: destination.len(),
        });
    }
    Ok(destination)
}

pub(crate) fn checked_size(count: usize, stride: usize) -> BridgeResult<usize> {
    count.checked_mul(stride).ok_or(BridgeError::SizeOverflow)
}

fn choose_index_format(vertex_count: usize) -> IndexFormat {
    if u16::try_from(vertex_count).is_ok() {
        IndexFormat::Short
    } else {
        IndexFormat::Int
    }
}

fn validate_indices(model: &MmdModel) -> BridgeResult<()> {
    if let Some(index) = model
        .indices
        .iter()
        .copied()
        .find(|index| *index as usize >= model.vertices.len())
    {
        return Err(BridgeError::InvalidData(format!(
            "顶点索引越界: {index}/{}",
            model.vertices.len()
        )));
    }
    Ok(())
}

fn require_gpu_topology(model: &MmdModel) -> BridgeResult<Arc<GpuSkinningTopology>> {
    model
        .gpu_skinning_topology()
        .map_err(|error| BridgeError::InvalidData(error.to_string()))
}

enum DrawSource<'a> {
    /// CPU 路径：子网格在索引缓冲里的实际区间
    /// （头部掩码生效时区间指向过滤后的缓冲，与 GPU 路径语义一致）
    Cpu {
        material_id: i32,
        first_index: u32,
        index_count: u32,
        center: [f32; 3],
    },
    Gpu(&'a GpuSkinningDraw),
}

impl DrawSource<'_> {
    fn material_id(&self) -> i32 {
        match self {
            Self::Cpu { material_id, .. } => *material_id,
            Self::Gpu(draw) => draw.material_id,
        }
    }

    fn first_index(&self) -> u32 {
        match self {
            Self::Cpu { first_index, .. } => *first_index,
            Self::Gpu(draw) => draw.first_index,
        }
    }

    fn index_count(&self) -> u32 {
        match self {
            Self::Cpu { index_count, .. } => *index_count,
            Self::Gpu(draw) => draw.index_count,
        }
    }

    fn palette_range(&self) -> (u32, u32) {
        match self {
            Self::Cpu { .. } => (0, 0),
            Self::Gpu(draw) => (draw.palette_offset, draw.palette_count),
        }
    }

    fn center(&self) -> [f32; 3] {
        match self {
            Self::Gpu(draw) => draw.center,
            Self::Cpu { center, .. } => *center,
        }
    }
}

fn encode_draw_command(
    model: &MmdModel,
    destination: &mut [u8],
    source: DrawSource<'_>,
) -> BridgeResult<()> {
    let material_id = source.material_id();
    let material_index = usize::try_from(material_id)
        .map_err(|_| BridgeError::InvalidData(format!("无效材质索引: {material_id}")))?;
    let material = model
        .materials
        .get(material_index)
        .ok_or_else(|| BridgeError::InvalidData(format!("材质索引越界: {material_index}")))?;
    let diffuse = model
        .morph_manager
        .get_material_morph_result(material_index)
        .map_or(material.diffuse, |morph| {
            material.diffuse * morph.mul.diffuse + morph.add.diffuse
        });
    let alpha = diffuse.w.clamp(0.0, 1.0);
    let mut flags = 0;
    if model.is_material_visible(material_index) && alpha > f32::EPSILON {
        flags |= DRAW_VISIBLE;
    }
    if material.is_double_sided() {
        flags |= DRAW_DOUBLE_SIDED;
    }
    if alpha < 1.0 - f32::EPSILON {
        flags |= DRAW_TRANSPARENT;
    }
    if should_outline(model, material_index) {
        flags |= DRAW_OUTLINE;
    }
    let center = source.center();
    let (palette_offset, palette_count) = source.palette_range();

    destination.fill(0);
    put_i32(destination, 0, material_id);
    put_u32(destination, 4, source.first_index());
    put_u32(destination, 8, source.index_count());
    put_i32(destination, 12, material.texture_index);
    put_f32(destination, 16, alpha);
    put_u32(destination, 20, flags);
    put_f32(destination, 24, center[0]);
    put_f32(destination, 28, center[1]);
    put_f32(destination, 32, center[2]);
    put_u32(
        destination,
        36,
        pack_rgba8(diffuse.x, diffuse.y, diffuse.z, alpha),
    );
    put_u32(destination, 40, palette_offset);
    put_u32(destination, 44, palette_count);
    Ok(())
}

fn should_outline(model: &MmdModel, material_index: usize) -> bool {
    const FACE_TOKENS: [&str; 12] = [
        "eye",
        "eyes",
        "eyebrow",
        "mouth",
        "pupil",
        "iris",
        "眼",
        "目",
        "眉",
        "口",
        "瞳",
        "まつげ",
    ];
    let Some(material) = model.materials.get(material_index) else {
        return false;
    };
    let texture = usize::try_from(material.texture_index)
        .ok()
        .and_then(|index| model.texture_paths.get(index))
        .map_or("", String::as_str);
    let name = material.name.to_lowercase();
    let texture = texture.to_lowercase();
    !FACE_TOKENS
        .iter()
        .any(|token| name.contains(token) || texture.contains(token))
}

/// 计算一段索引所引用顶点的中心（用于透明排序的距离估算）
fn static_center(model: &MmdModel, indices: &[u32]) -> [f32; 3] {
    let mut sum = [0.0; 3];
    let mut count = 0_u32;
    for vertex_index in indices.iter().copied() {
        let Some(source) = model.vertices.get(vertex_index as usize) else {
            continue;
        };
        sum[0] += source.position.x;
        sum[1] += source.position.y;
        sum[2] += source.position.z;
        count += 1;
    }
    if count == 0 {
        return sum;
    }
    let scale = 1.0 / count as f32;
    [sum[0] * scale, sum[1] * scale, sum[2] * scale]
}

fn quantize_normal(value: f32) -> i8 {
    (value.clamp(-1.0, 1.0) * 127.0).round() as i8
}

fn pack_rgba8(red: f32, green: f32, blue: f32, alpha: f32) -> u32 {
    let channel = |value: f32| (value.clamp(0.0, 1.0) * 255.0).round() as u32;
    channel(red) | (channel(green) << 8) | (channel(blue) << 16) | (channel(alpha) << 24)
}

fn checked_u32(value: usize) -> BridgeResult<u32> {
    u32::try_from(value).map_err(|_| BridgeError::SizeOverflow)
}

fn checked_u64(value: usize) -> BridgeResult<u64> {
    u64::try_from(value).map_err(|_| BridgeError::SizeOverflow)
}

fn put_i32(destination: &mut [u8], offset: usize, value: i32) {
    destination[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn put_u16(destination: &mut [u8], offset: usize, value: u16) {
    destination[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
}

fn put_u32(destination: &mut [u8], offset: usize, value: u32) {
    destination[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn put_u64(destination: &mut [u8], offset: usize, value: u64) {
    destination[offset..offset + 8].copy_from_slice(&value.to_le_bytes());
}

fn put_f32(destination: &mut [u8], offset: usize, value: f32) {
    destination[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use glam::{Vec2, Vec3, Vec4};

    use crate::model::{MmdMaterial, RuntimeVertex, SubMesh, VertexWeight};

    use super::*;

    #[test]
    fn frame_vertices_should_use_position_tex_color_normal_28_byte_layout() {
        let mut model = model_with_vertices(1);
        model.update_positions[0] = Vec3::new(1.0, 2.0, 3.0);
        model.update_uvs[0] = Vec2::new(0.25, 0.75);
        model.update_normals[0] = Vec3::new(-1.0, 0.0, 1.0);
        let mut output = [0_u8; VERTEX_STRIDE];

        let written = copy_frame_vertices(&model, &mut output).expect("vertex should encode");

        assert_eq!(written, VERTEX_STRIDE);
        assert_eq!(read_f32(&output, 0), 1.0);
        assert_eq!(read_f32(&output, 12), 0.25);
        assert_eq!(&output[20..24], &[255, 255, 255, 255]);
        assert_eq!(&output[24..28], &[129, 0, 127, 0]);
    }

    #[test]
    fn normal_quantization_should_clamp_and_round_to_snorm8() {
        assert_eq!(quantize_normal(-2.0), -127);
        assert_eq!(quantize_normal(-0.5), -64);
        assert_eq!(quantize_normal(0.5), 64);
        assert_eq!(quantize_normal(2.0), 127);
    }

    #[test]
    fn index_format_should_follow_vertex_count() {
        assert_eq!(choose_index_format(u16::MAX as usize), IndexFormat::Short);
        assert_eq!(choose_index_format(u16::MAX as usize + 1), IndexFormat::Int);
    }

    #[test]
    fn copy_indices_should_expand_small_indices_to_short() {
        let mut model = model_with_vertices(3);
        model.indices = vec![0, 2, 1];
        let mut output = [0_u8; 6];

        copy_indices(&model, &mut output).expect("indices should encode");

        assert_eq!(output, [0, 0, 2, 0, 1, 0]);
    }

    #[test]
    fn copy_indices_should_encode_int_when_vertex_count_exceeds_short_range() {
        let mut model = model_with_vertices(u16::MAX as usize + 1);
        model.indices = vec![0, u16::MAX as u32];
        let mut output = [0_u8; 8];

        copy_indices(&model, &mut output).expect("INT indices should encode");

        assert_eq!(&output[0..4], &0_u32.to_le_bytes());
        assert_eq!(&output[4..8], &(u16::MAX as u32).to_le_bytes());
    }

    #[test]
    fn mesh_revision_should_change_after_cpu_skinning() {
        let mut model = model_with_vertices(0);
        let before = describe_mesh(&model)
            .expect("mesh should describe")
            .vertex_revision;

        model.update();

        let after = describe_mesh(&model)
            .expect("mesh should describe")
            .vertex_revision;
        assert_ne!(after, before);
    }

    #[test]
    fn mesh_description_should_publish_stable_abi_layout() {
        let mut model = model_with_vertices(3);
        model.indices = vec![0, 1, 2];
        let mut output = [0_u8; MESH_DESCRIPTION_SIZE];

        copy_mesh_description(&model, &mut output).expect("description should encode");

        assert_eq!(read_u32(&output, 0), ABI_VERSION as u32);
        assert_eq!(read_u32(&output, 4), 3);
        assert_eq!(read_u32(&output, 16), VERTEX_STRIDE as u32);
        assert_eq!(read_u32(&output, 20), IndexFormat::Short as u32);
    }

    #[test]
    fn texture_copy_should_require_rgba8_and_preserve_pixels() {
        let texture = Texture::new(1, 1, vec![1, 2, 3, 4], true);
        let mut output = [0_u8; 4];

        copy_texture_pixels(&texture, &mut output).expect("RGBA8 texture should copy");

        assert_eq!(output, [1, 2, 3, 4]);
    }

    #[test]
    fn texture_copy_should_reject_non_rgba_source() {
        let texture = Texture::new(1, 1, vec![1, 2, 3], false);
        let mut output = [0_u8; 4];

        assert!(matches!(
            copy_texture_pixels(&texture, &mut output),
            Err(BridgeError::InvalidData(_))
        ));
    }

    #[test]
    fn hand_matrix_should_use_column_major_64_byte_layout() {
        let matrix = glam::Mat4::from_translation(Vec3::new(1.0, 2.0, 3.0));
        let mut output = [0_u8; MATRIX_SIZE];

        let written = copy_matrix(matrix, &mut output).expect("matrix should encode");

        assert_eq!(written, MATRIX_SIZE);
        assert_eq!(read_f32(&output, 0), 1.0);
        assert_eq!(read_f32(&output, 48), 1.0);
        assert_eq!(read_f32(&output, 52), 2.0);
        assert_eq!(read_f32(&output, 56), 3.0);
    }

    #[test]
    fn copy_should_reject_small_or_non_direct_destination() {
        let small = &mut [0_u8; 3];

        assert!(matches!(
            require_direct_destination(Some(small), 4),
            Err(BridgeError::BufferTooSmall {
                required: 4,
                actual: 3
            })
        ));
        assert_eq!(
            require_direct_destination(None, 1).expect_err("non-direct buffer should fail"),
            BridgeError::NotDirectBuffer
        );
    }

    #[test]
    fn checked_size_should_reject_multiplication_overflow() {
        assert_eq!(
            checked_size(usize::MAX, 2).expect_err("overflow should fail"),
            BridgeError::SizeOverflow
        );
    }

    #[test]
    fn draw_commands_should_reject_out_of_bounds_submesh() {
        let mut model = model_with_vertices(1);
        model.indices = vec![0];
        model.materials = vec![MmdMaterial::default()];
        model.submeshes = vec![SubMesh::new(1, 1, 0)];
        let mut output = [0_u8; DRAW_COMMAND_STRIDE];

        assert!(matches!(
            copy_draw_commands(&model, &mut output),
            Err(BridgeError::InvalidData(_))
        ));
    }

    #[test]
    fn draw_commands_should_apply_morph_alpha_and_flags() {
        let mut model = model_with_vertices(1);
        model.indices = vec![0];
        let material = MmdMaterial {
            diffuse: Vec4::new(1.0, 1.0, 1.0, 0.5),
            draw_flags: 0x11,
            ..MmdMaterial::default()
        };
        model.materials = vec![material];
        model.submeshes = vec![SubMesh::new(0, 1, 0)];
        model.init_material_visibility();
        let mut output = [0_u8; DRAW_COMMAND_STRIDE];

        copy_draw_commands(&model, &mut output).expect("command should encode");

        assert_eq!(read_f32(&output, 16), 0.5);
        assert_eq!(read_u32(&output, 20), 0b1111);
        assert_eq!(read_u32(&output, 36), 0x80FF_FFFF);
    }

    /// CPU 路径 + 头部掩码：索引缓冲被过滤后，
    /// draw command 的 firstIndex/indexCount 必须指向过滤后的区间。
    #[test]
    fn head_mask_should_rewrite_cpu_indices_and_draw_ranges() {
        let mut model = model_with_head_and_body();
        model.set_first_person_mode(true);

        let description = describe_mesh(&model).expect("mesh should describe");
        assert_eq!(description.index_count, 3);
        assert_eq!(description.draw_count, 2);

        let mut indices = [0_u8; 6];
        let written = copy_indices(&model, &mut indices).expect("indices should encode");
        assert_eq!(written, 6);
        // 只剩身体的 3 个索引（顶点 0/1/2）
        assert_eq!(indices, [0, 0, 1, 0, 2, 0]);

        let mut commands = [0_u8; DRAW_COMMAND_STRIDE * 2];
        copy_draw_commands(&model, &mut commands).expect("commands should encode");
        // 身体子网格：firstIndex=0，indexCount=3
        assert_eq!(read_u32(&commands, 4), 0);
        assert_eq!(read_u32(&commands, 8), 3);
        // 头部子网格：区间被清空（Java 侧遇到 indexCount==0 会跳过）
        assert_eq!(read_u32(&commands, DRAW_COMMAND_STRIDE + 4), 3);
        assert_eq!(read_u32(&commands, DRAW_COMMAND_STRIDE + 8), 0);
    }

    /// 关掉第一人称后，CPU 路径必须回到原始索引与原始区间（逐字节一致）。
    #[test]
    fn disabling_head_mask_should_restore_cpu_geometry() {
        let mut model = model_with_head_and_body();
        model.set_first_person_mode(true);
        model.set_first_person_mode(false);

        let description = describe_mesh(&model).expect("mesh should describe");
        assert_eq!(description.index_count, model.indices.len());

        let mut indices = [0_u8; 12];
        let written = copy_indices(&model, &mut indices).expect("indices should encode");
        assert_eq!(written, 12);
        assert_eq!(indices, [0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0]);

        let mut commands = [0_u8; DRAW_COMMAND_STRIDE * 2];
        copy_draw_commands(&model, &mut commands).expect("commands should encode");
        assert_eq!(read_u32(&commands, 4), 0);
        assert_eq!(read_u32(&commands, 8), 3);
        assert_eq!(read_u32(&commands, DRAW_COMMAND_STRIDE + 4), 3);
        assert_eq!(read_u32(&commands, DRAW_COMMAND_STRIDE + 8), 3);
    }

    /// 造一个「身体 + 头部」两段的模型：脖子骨骼 Neck 在 Y=10，
    /// 后 3 个顶点（Y=15）属于头部。
    fn model_with_head_and_body() -> MmdModel {
        let vertex = |y: f32| RuntimeVertex {
            position: Vec3::new(0.0, y, 0.0),
            normal: Vec3::Y,
            uv: Vec2::ZERO,
        };
        let mut model = MmdModel::new();
        model.vertices = vec![
            vertex(5.0),
            vertex(5.0),
            vertex(6.0),
            vertex(15.0),
            vertex(15.0),
            vertex(16.0),
        ];
        model.update_positions = model.vertices.iter().map(|v| v.position).collect();
        model.update_normals = model.vertices.iter().map(|v| v.normal).collect();
        model.update_uvs = model.vertices.iter().map(|v| v.uv).collect();
        model.indices = vec![0, 1, 2, 3, 4, 5];
        model.weights = vec![VertexWeight::Bdef1 { bone: 0 }; 6];
        model.materials = vec![MmdMaterial::default(), MmdMaterial::default()];
        model.submeshes = vec![SubMesh::new(0, 3, 0), SubMesh::new(3, 3, 1)];

        let mut root = crate::skeleton::BoneLink::new("root".to_string());
        root.parent_index = -1;
        model.bone_manager.add_bone(root);
        let mut neck = crate::skeleton::BoneLink::new("Neck".to_string());
        neck.initial_position = Vec3::new(0.0, 10.0, 0.0);
        neck.parent_index = 0;
        model.bone_manager.add_bone(neck);
        model.bone_manager.build_hierarchy();

        model.init_material_visibility();
        model.init_head_detection();
        model
    }

    fn model_with_vertices(count: usize) -> MmdModel {
        let vertex = RuntimeVertex {
            position: Vec3::ZERO,
            normal: Vec3::Y,
            uv: Vec2::ZERO,
        };
        let mut model = MmdModel::new();
        model.vertices = vec![vertex; count];
        model.update_positions = vec![Vec3::ZERO; count];
        model.update_normals = vec![Vec3::Y; count];
        model.update_uvs = vec![Vec2::ZERO; count];
        model
    }

    fn read_f32(source: &[u8], offset: usize) -> f32 {
        f32::from_le_bytes(source[offset..offset + 4].try_into().expect("four bytes"))
    }

    fn read_u32(source: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(source[offset..offset + 4].try_into().expect("four bytes"))
    }
}
