//! Morph 定义

use super::{MorphType, VertexMorphOffset, BoneMorphOffset, MaterialMorphOffset, UvMorphOffset, GroupMorphOffset};

/// Morph 变形
#[derive(Clone, Debug)]
pub struct Morph {
    pub name: String,
    pub morph_type: MorphType,
    pub weight: f32,
    
    // 顶点 Morph
    pub vertex_offsets: Vec<VertexMorphOffset>,
    
    // 骨骼 Morph
    pub bone_offsets: Vec<BoneMorphOffset>,
    
    // 材质 Morph
    pub material_offsets: Vec<MaterialMorphOffset>,
    
    // UV Morph
    pub uv_offsets: Vec<UvMorphOffset>,
    
    // Group Morph 子项
    pub group_offsets: Vec<GroupMorphOffset>,
}

impl Morph {
    pub fn new(name: String, morph_type: MorphType) -> Self {
        Self {
            name,
            morph_type,
            weight: 0.0,
            vertex_offsets: Vec::new(),
            bone_offsets: Vec::new(),
            material_offsets: Vec::new(),
            uv_offsets: Vec::new(),
            group_offsets: Vec::new(),
        }
    }
    
    /// 获取名称
    pub fn get_name(&self) -> &str {
        &self.name
    }
    
    /// 获取权重
    pub fn get_weight(&self) -> f32 {
        self.weight
    }
    
    /// 设置权重
    pub fn set_weight(&mut self, weight: f32) {
        self.weight = weight;
    }
    
    /// 重置权重
    pub fn reset(&mut self) {
        self.weight = 0.0;
    }
}

impl Default for Morph {
    fn default() -> Self {
        Self::new(String::new(), MorphType::Vertex)
    }
}
