//! IK 求解器 - 参考 nphysics 约束求解思想重新实现

use glam::{Vec3, Quat, Mat3};
use std::f32::consts::PI;

use super::bone_link::{BoneLink, BoneFlags, IkConfig, IkLink};


/// IK 链节点状态
#[derive(Clone, Debug, Default)]
struct IkChainState {
    /// 上一帧的欧拉角（用于连续性）
    prev_angle: Vec3,
    /// 单轴模式下的累积角度
    plane_mode_angle: f32,
    /// 最佳 IK 旋转（用于回退）
    best_ik_rotate: Quat,
}

/// 求解轴类型
#[derive(Clone, Copy, Debug, PartialEq)]
enum SolveAxis {
    X,
    Y,
    Z,
}


/// IK 求解器
#[derive(Clone, Debug)]
pub struct IkSolver {
    /// 目标骨骼索引（IK 骨骼本身）
    pub bone_index: usize,
    /// IK 配置
    pub config: IkConfig,
    /// 是否启用
    pub enabled: bool,
}

impl IkSolver {
    /// 创建新的 IK 求解器
    pub fn new(bone_index: usize, config: IkConfig) -> Self {
        Self {
            bone_index,
            config,
            enabled: true,
        }
    }
    
    /// 求解 IK
    pub fn solve(&self, bones: &mut [BoneLink], children_cache: &[Vec<usize>]) {
        if !self.enabled {
            return;
        }
        
        let target_idx = self.config.target_bone as usize;
        if target_idx >= bones.len() || self.bone_index >= bones.len() {
            return;
        }
        
        // 初始化 IK 链状态
        let mut chain_states: Vec<IkChainState> = self.config.links
            .iter()
            .map(|_| IkChainState::default())
            .collect();
        
        // 初始化 IK 链骨骼
        for link in &self.config.links {
            let link_idx = link.bone_index as usize;
            if link_idx < bones.len() {
                bones[link_idx].ik_rotate = Quat::IDENTITY;
                bones[link_idx].flags |= BoneFlags::IK_ENABLED;
                bones[link_idx].compute_local_transform();
                Self::update_global_transform_recursive(bones, children_cache, link_idx);
            }
        }
        
        let mut best_distance = f32::MAX;
        
        // 迭代求解
        for iteration in 0..self.config.iterations {
            self.solve_iteration(bones, children_cache, target_idx, iteration, &mut chain_states);
            
            // 检查距离
            let target_pos = bones[target_idx].local_to_world.col(3).truncate();
            let ik_pos = bones[self.bone_index].local_to_world.col(3).truncate();
            let distance = (target_pos - ik_pos).length();
            
            if distance < best_distance {
                best_distance = distance;
                // 保存最佳结果
                for (i, link) in self.config.links.iter().enumerate() {
                    let link_idx = link.bone_index as usize;
                    if link_idx < bones.len() {
                        chain_states[i].best_ik_rotate = bones[link_idx].ik_rotate;
                    }
                }
            } else {
                // 恢复最佳结果并退出
                for (i, link) in self.config.links.iter().enumerate() {
                    let link_idx = link.bone_index as usize;
                    if link_idx < bones.len() {
                        bones[link_idx].ik_rotate = chain_states[i].best_ik_rotate;
                        bones[link_idx].compute_local_transform();
                        Self::update_global_transform_recursive(bones, children_cache, link_idx);
                    }
                }
                break;
            }
        }
    }
    
    /// 单次迭代求解
    fn solve_iteration(
        &self,
        bones: &mut [BoneLink],
        children_cache: &[Vec<usize>],
        target_idx: usize,
        iteration: u32,
        chain_states: &mut [IkChainState],
    ) {
        let ik_pos = bones[self.bone_index].local_to_world.col(3).truncate();
        
        for chain_idx in 0..self.config.links.len() {
            let link = &self.config.links[chain_idx];
            let link_idx = link.bone_index as usize;
            
            if link_idx >= bones.len() || link_idx == target_idx {
                continue;
            }
            
            // 检查是否使用单轴模式
            if link.has_limits {
                if let Some(axis) = Self::detect_plane_solve_axis(link) {
                    self.solve_plane(
                        bones, children_cache, target_idx,
                        iteration, chain_idx, axis, chain_states,
                    );
                    continue;
                }
            }
            
            // 通用 3 轴求解
            let target_pos = bones[target_idx].local_to_world.col(3).truncate();
            let inv_link = bones[link_idx].local_to_world.inverse();
            
            let local_ik_pos = inv_link.transform_point3(ik_pos);
            let local_target_pos = inv_link.transform_point3(target_pos);
            
            let ik_vec = local_ik_pos.normalize_or_zero();
            let target_vec = local_target_pos.normalize_or_zero();
            
            if ik_vec.length_squared() < 1e-8 || target_vec.length_squared() < 1e-8 {
                continue;
            }
            
            let dot = target_vec.dot(ik_vec).clamp(-1.0, 1.0);
            let angle = dot.acos();
            
            if angle.to_degrees() < 1e-3 {
                continue;
            }
            
            let angle = angle.clamp(-self.config.limit_angle, self.config.limit_angle);
            let axis = target_vec.cross(ik_vec).normalize_or_zero();
            
            if axis.length_squared() < 1e-8 {
                continue;
            }
            
            let delta_rot = Quat::from_axis_angle(axis, angle);
            
            let p = bones[link_idx].parent_rest_rotation;
            let chain_rot = bones[link_idx].ik_rotate * p.inverse() * bones[link_idx].animation_rotate * p * bones[link_idx].rest_rotation * delta_rot;
            
            // 应用角度限制
            let chain_rot = if link.has_limits {
                let rot_mat = Mat3::from_quat(chain_rot);
                let euler = Self::decompose_rotation(rot_mat, chain_states[chain_idx].prev_angle);
                
                // 限制到角度范围
                let mut clamped = Vec3::new(
                    euler.x.clamp(link.limit_min.x, link.limit_max.x),
                    euler.y.clamp(link.limit_min.y, link.limit_max.y),
                    euler.z.clamp(link.limit_min.z, link.limit_max.z),
                );
                
                // 增量限制
                let delta = clamped - chain_states[chain_idx].prev_angle;
                let limit = Vec3::splat(self.config.limit_angle);
                clamped = delta.clamp(-limit, limit) + chain_states[chain_idx].prev_angle;
                
                chain_states[chain_idx].prev_angle = clamped;
                
                Quat::from_euler(glam::EulerRot::XYZ, clamped.x, clamped.y, clamped.z)
            } else {
                chain_rot
            };
            
            bones[link_idx].ik_rotate = chain_rot * bones[link_idx].rest_rotation.inverse() * p.inverse() * bones[link_idx].animation_rotate.inverse() * p;
            bones[link_idx].compute_local_transform();
            Self::update_global_transform_recursive(bones, children_cache, link_idx);
        }
    }
    
    /// 单轴求解（膝盖等关节）
    fn solve_plane(
        &self,
        bones: &mut [BoneLink],
        children_cache: &[Vec<usize>],
        target_idx: usize,
        iteration: u32,
        chain_idx: usize,
        solve_axis: SolveAxis,
        chain_states: &mut [IkChainState],
    ) {
        let (axis_index, rotate_axis) = match solve_axis {
            SolveAxis::X => (0, Vec3::X),
            SolveAxis::Y => (1, Vec3::Y),
            SolveAxis::Z => (2, Vec3::Z),
        };
        
        let link = &self.config.links[chain_idx];
        let link_idx = link.bone_index as usize;
        
        if link_idx >= bones.len() {
            return;
        }
        
        let ik_pos = bones[self.bone_index].local_to_world.col(3).truncate();
        let target_pos = bones[target_idx].local_to_world.col(3).truncate();
        
        let inv_link = bones[link_idx].local_to_world.inverse();
        let local_ik_pos = inv_link.transform_point3(ik_pos);
        let local_target_pos = inv_link.transform_point3(target_pos);
        
        let ik_vec = local_ik_pos.normalize_or_zero();
        let target_vec = local_target_pos.normalize_or_zero();
        
        if ik_vec.length_squared() < 1e-8 || target_vec.length_squared() < 1e-8 {
            return;
        }
        
        let dot = target_vec.dot(ik_vec).clamp(-1.0, 1.0);
        let angle = dot.acos().clamp(-self.config.limit_angle, self.config.limit_angle);
        
        // 测试两个方向
        let rot_pos = Quat::from_axis_angle(rotate_axis, angle);
        let rot_neg = Quat::from_axis_angle(rotate_axis, -angle);
        
        let dot_pos = (rot_pos * target_vec).dot(ik_vec);
        let dot_neg = (rot_neg * target_vec).dot(ik_vec);
        
        let mut new_angle = chain_states[chain_idx].plane_mode_angle;
        if dot_pos > dot_neg {
            new_angle += angle;
        } else {
            new_angle -= angle;
        }
        
        // 第 0 次迭代的特殊处理
        if iteration == 0 {
            let (limit_min, limit_max) = match axis_index {
                0 => (link.limit_min.x, link.limit_max.x),
                1 => (link.limit_min.y, link.limit_max.y),
                _ => (link.limit_min.z, link.limit_max.z),
            };
            
            if new_angle < limit_min || new_angle > limit_max {
                if -new_angle > limit_min && -new_angle < limit_max {
                    new_angle = -new_angle;
                } else {
                    let half = (limit_min + limit_max) * 0.5;
                    if (half - new_angle).abs() > (half + new_angle).abs() {
                        new_angle = -new_angle;
                    }
                }
            }
        }
        
        // 限制角度
        let (limit_min, limit_max) = match axis_index {
            0 => (link.limit_min.x, link.limit_max.x),
            1 => (link.limit_min.y, link.limit_max.y),
            _ => (link.limit_min.z, link.limit_max.z),
        };
        new_angle = new_angle.clamp(limit_min, limit_max);
        chain_states[chain_idx].plane_mode_angle = new_angle;
        
        let p = bones[link_idx].parent_rest_rotation;
        bones[link_idx].ik_rotate = Quat::from_axis_angle(rotate_axis, new_angle)
            * bones[link_idx].rest_rotation.inverse() * p.inverse() * bones[link_idx].animation_rotate.inverse() * p;
        bones[link_idx].compute_local_transform();
        Self::update_global_transform_recursive(bones, children_cache, link_idx);
    }
    
    /// 检测是否应使用单轴模式
    fn detect_plane_solve_axis(link: &IkLink) -> Option<SolveAxis> {
        let x_active = link.limit_min.x != 0.0 || link.limit_max.x != 0.0;
        let y_active = link.limit_min.y != 0.0 || link.limit_max.y != 0.0;
        let z_active = link.limit_min.z != 0.0 || link.limit_max.z != 0.0;
        
        let x_zero = link.limit_min.x == 0.0 && link.limit_max.x == 0.0;
        let y_zero = link.limit_min.y == 0.0 && link.limit_max.y == 0.0;
        let z_zero = link.limit_min.z == 0.0 && link.limit_max.z == 0.0;
        
        if x_active && y_zero && z_zero {
            Some(SolveAxis::X)
        } else if y_active && x_zero && z_zero {
            Some(SolveAxis::Y)
        } else if z_active && x_zero && y_zero {
            Some(SolveAxis::Z)
        } else {
            None
        }
    }
    
    /// 从旋转矩阵分解欧拉角
    fn decompose_rotation(m: Mat3, prev: Vec3) -> Vec3 {
        let epsilon = 1.0e-6_f32;
        let sy = -m.col(0).z;
        
        let result = if (1.0 - sy.abs()) < epsilon {
            // Gimbal lock
            let ry = sy.asin();
            let sx = prev.x.sin();
            let sz = prev.z.sin();
            
            if sx.abs() < sz.abs() {
                let cx = prev.x.cos();
                if cx > 0.0 {
                    Vec3::new(0.0, ry, (-m.col(1).x).asin())
                } else {
                    Vec3::new(PI, ry, m.col(1).x.asin())
                }
            } else {
                let cz = prev.z.cos();
                if cz > 0.0 {
                    Vec3::new((-m.col(2).y).asin(), ry, 0.0)
                } else {
                    Vec3::new(m.col(2).y.asin(), ry, PI)
                }
            }
        } else {
            Vec3::new(
                m.col(1).z.atan2(m.col(2).z),
                (-m.col(0).z).asin(),
                m.col(0).y.atan2(m.col(0).x),
            )
        };
        
        // 寻找最接近 prev 的解
        Self::find_closest_euler(result, prev)
    }
    
    /// 找到最接近 prev 的欧拉角表示
    fn find_closest_euler(r: Vec3, prev: Vec3) -> Vec3 {
        let candidates = [
            r,
            Vec3::new(r.x + PI, PI - r.y, r.z + PI),
            Vec3::new(r.x + PI, PI - r.y, r.z - PI),
            Vec3::new(r.x + PI, -PI - r.y, r.z + PI),
            Vec3::new(r.x + PI, -PI - r.y, r.z - PI),
            Vec3::new(r.x - PI, PI - r.y, r.z + PI),
            Vec3::new(r.x - PI, PI - r.y, r.z - PI),
            Vec3::new(r.x - PI, -PI - r.y, r.z + PI),
            Vec3::new(r.x - PI, -PI - r.y, r.z - PI),
        ];
        
        let mut best = r;
        let mut best_error = Self::euler_error(r, prev);
        
        for candidate in &candidates[1..] {
            let error = Self::euler_error(*candidate, prev);
            if error < best_error {
                best_error = error;
                best = *candidate;
            }
        }
        
        best
    }
    
    /// 计算欧拉角误差
    fn euler_error(a: Vec3, b: Vec3) -> f32 {
        Self::angle_diff(a.x, b.x).abs()
            + Self::angle_diff(a.y, b.y).abs()
            + Self::angle_diff(a.z, b.z).abs()
    }
    
    /// 计算角度差（考虑周期性）
    fn angle_diff(a: f32, b: f32) -> f32 {
        let mut diff = Self::normalize_angle(a) - Self::normalize_angle(b);
        if diff > PI {
            diff -= 2.0 * PI;
        } else if diff < -PI {
            diff += 2.0 * PI;
        }
        diff
    }
    
    /// 角度归一化到 [0, 2π)
    fn normalize_angle(angle: f32) -> f32 {
        let mut r = angle % (2.0 * PI);
        if r < 0.0 {
            r += 2.0 * PI;
        }
        r
    }
    
    /// 递归更新全局变换
    pub(crate) fn update_global_transform_recursive(
        bones: &mut [BoneLink],
        children_cache: &[Vec<usize>],
        idx: usize,
    ) {
        if idx >= bones.len() {
            return;
        }
        
        let parent_idx = bones[idx].parent_index;
        if parent_idx >= 0 && (parent_idx as usize) < bones.len() {
            let parent_global = bones[parent_idx as usize].local_to_world;
            bones[idx].parent_to_world = parent_global;
            bones[idx].local_to_world = parent_global * bones[idx].local_to_parent;
        } else {
            bones[idx].parent_to_world = glam::Mat4::IDENTITY;
            bones[idx].local_to_world = bones[idx].local_to_parent;
        }
        
        // 递归更新子骨骼
        if idx < children_cache.len() {
            for &child_idx in &children_cache[idx] {
                Self::update_global_transform_recursive(bones, children_cache, child_idx);
            }
        }
    }
}
