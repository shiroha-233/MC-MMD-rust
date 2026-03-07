//! MMD 模型运行时管理

mod runtime;
mod loader;
mod material;
mod submesh;

mod vrm_loader;
mod vrm_mesh;
mod vrm_skeleton;
mod vrm_material;
mod vrm_morph;
mod vrm_extensions;
mod bone_mapping;

pub use runtime::MmdModel;
pub use loader::load_pmx;
pub use vrm_loader::load_vrm;
pub use material::MmdMaterial;
pub use submesh::SubMesh;

use glam::{Vec2, Vec3};

/// 运行时顶点数据
#[derive(Clone, Debug)]
pub struct RuntimeVertex {
    pub position: Vec3,
    pub normal: Vec3,
    pub uv: Vec2,
}

/// 模型顶点骨骼权重
#[derive(Clone, Debug)]
pub enum VertexWeight {
    Bdef1 { bone: i32 },
    Bdef2 { bones: [i32; 2], weight: f32 },
    Bdef4 { bones: [i32; 4], weights: [f32; 4] },
    Sdef { bones: [i32; 2], weight: f32, c: Vec3, r0: Vec3, r1: Vec3 },
    Qdef { bones: [i32; 4], weights: [f32; 4] },
}

impl Default for VertexWeight {
    fn default() -> Self {
        VertexWeight::Bdef1 { bone: 0 }
    }
}
