//! Morph 变形系统

mod manager;
mod morph;

pub use manager::{MaterialMorphResult, MorphManager};
pub use morph::Morph;

use glam::{Vec3, Vec4};

/// Morph 类型
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum MorphType {
    Group,
    Vertex,
    Bone,
    Uv,
    AdditionalUv1,
    AdditionalUv2,
    AdditionalUv3,
    AdditionalUv4,
    Material,
    Flip,
    Impulse,
}

/// 顶点 Morph 偏移
#[derive(Clone, Debug)]
pub struct VertexMorphOffset {
    pub vertex_index: u32,
    pub offset: Vec3,
}

/// 骨骼 Morph 偏移
#[derive(Clone, Debug)]
pub struct BoneMorphOffset {
    pub bone_index: u32,
    pub translation: Vec3,
    pub rotation: Vec4,
}

/// 材质 Morph 偏移
#[derive(Clone, Debug)]
pub struct MaterialMorphOffset {
    pub material_index: i32,
    /// 操作类型: 0=乘算, 1=加算
    pub operation: u8,
    pub diffuse: Vec4,
    pub specular: Vec3,
    pub specular_strength: f32,
    pub ambient: Vec3,
    pub edge_color: Vec4,
    pub edge_size: f32,
    pub texture_tint: Vec4,
    pub environment_tint: Vec4,
    pub toon_tint: Vec4,
}

/// UV Morph 偏移
#[derive(Clone, Debug)]
pub struct UvMorphOffset {
    pub vertex_index: u32,
    /// UV 偏移 (x, y 为主 UV 偏移，z, w 备用)
    pub offset: Vec4,
}

/// Group Morph 子项
#[derive(Clone, Debug)]
pub struct GroupMorphOffset {
    pub morph_index: u32,
    pub influence: f32,
}
