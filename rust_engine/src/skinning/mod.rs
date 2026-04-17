//! 顶点蒙皮计算

mod skinning;

pub use skinning::{compute_skinning, SkinningContext};

use crate::model::VertexWeight;
use glam::{Mat4, Vec3};

/// 蒙皮输入数据
pub struct SkinningInput<'a> {
    /// 原始顶点位置
    pub positions: &'a [Vec3],
    /// 原始顶点法线
    pub normals: &'a [Vec3],
    /// 顶点权重
    pub weights: &'a [VertexWeight],
    /// 骨骼变换矩阵（已乘以逆绑定矩阵）
    pub bone_matrices: &'a [Mat4],
}

/// 蒙皮输出数据
pub struct SkinningOutput {
    /// 变换后的顶点位置
    pub positions: Vec<Vec3>,
    /// 变换后的顶点法线
    pub normals: Vec<Vec3>,
}
