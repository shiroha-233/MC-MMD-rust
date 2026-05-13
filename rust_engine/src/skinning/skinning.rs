//! 顶点蒙皮计算

use super::{SkinningInput, SkinningOutput};
use crate::model::VertexWeight;
use glam::{Mat4, Vec3};

/// 蒙皮上下文
pub struct SkinningContext {
    pub parallel_count: u32,
}

impl Default for SkinningContext {
    fn default() -> Self {
        Self { parallel_count: 1 }
    }
}

/// 计算蒙皮
pub fn compute_skinning(input: &SkinningInput) -> SkinningOutput {
    let vertex_count = input.positions.len();
    let mut positions = Vec::with_capacity(vertex_count);
    let mut normals = Vec::with_capacity(vertex_count);

    for i in 0..vertex_count {
        let (pos, norm) = compute_single_vertex(
            input.positions[i],
            input.normals[i],
            &input.weights[i],
            input.bone_matrices,
        );
        positions.push(pos);
        normals.push(norm);
    }

    SkinningOutput { positions, normals }
}

/// 计算单个顶点的蒙皮
fn compute_single_vertex(
    position: Vec3,
    normal: Vec3,
    weight: &VertexWeight,
    matrices: &[Mat4],
) -> (Vec3, Vec3) {
    match weight {
        VertexWeight::Bdef1 { bone } => {
            let m = get_matrix(matrices, *bone);
            let pos = m.transform_point3(position);
            let norm = m.transform_vector3(normal).normalize_or_zero();
            (pos, norm)
        }
        VertexWeight::Bdef2 { bones, weight } => {
            let m0 = get_matrix(matrices, bones[0]);
            let m1 = get_matrix(matrices, bones[1]);
            let w0 = *weight;
            let w1 = 1.0 - w0;

            let pos = m0.transform_point3(position) * w0 + m1.transform_point3(position) * w1;
            let norm = (m0.transform_vector3(normal) * w0 + m1.transform_vector3(normal) * w1)
                .normalize_or_zero();
            (pos, norm)
        }
        VertexWeight::Bdef4 { bones, weights } => {
            let mut pos = Vec3::ZERO;
            let mut norm = Vec3::ZERO;

            for i in 0..4 {
                let m = get_matrix(matrices, bones[i]);
                let w = weights[i];
                pos += m.transform_point3(position) * w;
                norm += m.transform_vector3(normal) * w;
            }

            (pos, norm.normalize_or_zero())
        }
        VertexWeight::Sdef {
            bones,
            weight,
            c,
            r0: _,
            r1: _,
        } => {
            // SDEF 球面变形 - 简化实现
            let m0 = get_matrix(matrices, bones[0]);
            let m1 = get_matrix(matrices, bones[1]);
            let w0 = *weight;
            let w1 = 1.0 - w0;

            // 计算中心点变换
            let c_transformed = m0.transform_point3(*c) * w0 + m1.transform_point3(*c) * w1;

            // 计算相对于中心的偏移
            let offset = position - *c;

            // 使用球面插值旋转偏移
            let q0 = glam::Quat::from_mat4(&m0);
            let q1 = glam::Quat::from_mat4(&m1);
            let q = q0.slerp(q1, w1);

            let rotated_offset = q * offset;
            let pos = c_transformed + rotated_offset;

            let norm = (m0.transform_vector3(normal) * w0 + m1.transform_vector3(normal) * w1)
                .normalize_or_zero();
            (pos, norm)
        }
        VertexWeight::Qdef { bones, weights } => {
            // QDEF 与 BDEF4 类似
            let mut pos = Vec3::ZERO;
            let mut norm = Vec3::ZERO;

            for i in 0..4 {
                let m = get_matrix(matrices, bones[i]);
                let w = weights[i];
                pos += m.transform_point3(position) * w;
                norm += m.transform_vector3(normal) * w;
            }

            (pos, norm.normalize_or_zero())
        }
    }
}

fn get_matrix(matrices: &[Mat4], index: i32) -> Mat4 {
    if index < 0 {
        return Mat4::IDENTITY;
    }
    matrices
        .get(index as usize)
        .copied()
        .unwrap_or(Mat4::IDENTITY)
}
