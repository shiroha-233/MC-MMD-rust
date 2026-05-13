//! 子网格定义

/// 子网格
#[derive(Clone, Debug)]
pub struct SubMesh {
    pub begin_index: u32,
    pub index_count: u32,
    pub material_id: i32,
}

impl SubMesh {
    pub fn new(begin_index: u32, index_count: u32, material_id: i32) -> Self {
        Self {
            begin_index,
            index_count,
            material_id,
        }
    }
}
