//! 6DOF 弹簧约束 — 移植自 Bullet3 btGeneric6DofSpringConstraint
//!
//! ## 工作原理
//!
//! Bullet3 的 `btGeneric6DofSpringConstraint` 在每个求解步中：
//! 1. 计算两个刚体约束参考系之间的线性位移和欧拉角偏差
//! 2. 根据胡克定律计算弹簧力：`F = k * delta`
//! 3. 将力转化为 motor 目标速度：`targetVel = fps * damping / iterations * force`
//! 4. 通过约束求解器施加
//!
//! 我们的实现：
//! - 使用与 Bullet3 **完全相同**的位移/角度计算公式
//! - 将弹簧力直接作为外力施加到 Rapier 刚体上
//! - Rapier 的 GenericJoint 仅负责限制/锁定轴（不使用 motor）
//!
//! ## 参考源码
//! - `bullet3/src/BulletDynamics/ConstraintSolver/btGeneric6DofSpringConstraint.cpp`
//! - `bullet3/src/BulletDynamics/ConstraintSolver/btGeneric6DofConstraint.cpp`

use rapier3d::prelude::*;
use rapier3d::na::{Matrix3, Vector3, Isometry3};

/// 6DOF 弹簧约束
///
/// 独立于 Rapier 约束系统，在每个物理步之前计算弹簧力并施加到刚体上。
/// 每个 `Spring6DofConstraint` 对应一个 MMD 关节中启用了弹簧的轴。
pub struct Spring6DofConstraint {
    /// 约束在刚体 A 局部空间的参考系
    pub frame_in_a: Isometry3<f32>,
    /// 约束在刚体 B 局部空间的参考系
    pub frame_in_b: Isometry3<f32>,
    /// 刚体 A 句柄
    pub body_a: RigidBodyHandle,
    /// 刚体 B 句柄
    pub body_b: RigidBodyHandle,

    // 弹簧参数 [0-2: 线性 XYZ, 3-5: 角度 XYZ]
    /// 弹簧是否启用
    pub spring_enabled: [bool; 6],
    /// 弹簧刚度（与 Bullet3 的 stiffness 含义一致）
    pub spring_stiffness: [f32; 6],
    /// 弹簧阻尼 (0.0~1.0, 1.0 = 无阻尼，与 Bullet3 一致)
    pub spring_damping: [f32; 6],
    /// 平衡点（通常为 0，表示约束参考系对齐时的静止位置）
    pub equilibrium_point: [f32; 6],
}

impl Spring6DofConstraint {
    /// 创建新的 6DOF 弹簧约束
    pub fn new(
        frame_in_a: Isometry3<f32>,
        frame_in_b: Isometry3<f32>,
        body_a: RigidBodyHandle,
        body_b: RigidBodyHandle,
    ) -> Self {
        Self {
            frame_in_a,
            frame_in_b,
            body_a,
            body_b,
            spring_enabled: [false; 6],
            spring_stiffness: [0.0; 6],
            spring_damping: [1.0; 6], // Bullet3 默认值：1.0 = 无阻尼
            equilibrium_point: [0.0; 6],
        }
    }

    /// 启用/禁用指定轴的弹簧
    /// index: 0-2 线性 XYZ, 3-5 角度 XYZ
    pub fn enable_spring(&mut self, index: usize, on: bool) {
        if index < 6 {
            self.spring_enabled[index] = on;
        }
    }

    /// 设置弹簧刚度
    pub fn set_stiffness(&mut self, index: usize, stiffness: f32) {
        if index < 6 {
            self.spring_stiffness[index] = stiffness;
        }
    }

    /// 设置弹簧阻尼 (0~1, 1=无阻尼)
    pub fn set_damping(&mut self, index: usize, damping: f32) {
        if index < 6 {
            self.spring_damping[index] = damping;
        }
    }

    /// 设置平衡点
    pub fn set_equilibrium_point(&mut self, index: usize, val: f32) {
        if index < 6 {
            self.equilibrium_point[index] = val;
        }
    }

    /// 从当前刚体状态计算并设置所有轴的平衡点
    ///
    /// 对应 Bullet3 `btGeneric6DofSpringConstraint::setEquilibriumPoint()`
    pub fn calculate_equilibrium_point(&mut self, rigid_body_set: &RigidBodySet) {
        let rb_a = match rigid_body_set.get(self.body_a) {
            Some(rb) => rb,
            None => return,
        };
        let rb_b = match rigid_body_set.get(self.body_b) {
            Some(rb) => rb,
            None => return,
        };

        let trans_a = *rb_a.position();
        let trans_b = *rb_b.position();

        let calc_trans_a = trans_a * self.frame_in_a;
        let calc_trans_b = trans_b * self.frame_in_b;

        // 线性位移
        let linear_diff = Self::calculate_linear_diff(&calc_trans_a, &calc_trans_b);
        for i in 0..3 {
            self.equilibrium_point[i] = linear_diff[i];
        }

        // 角度偏差
        let angle_diff = Self::calculate_angle_diff(&calc_trans_a, &calc_trans_b);
        for i in 0..3 {
            self.equilibrium_point[i + 3] = angle_diff[i];
        }
    }

    /// 是否有任何弹簧启用
    pub fn has_any_spring(&self) -> bool {
        self.spring_enabled.iter().any(|&e| e)
    }

    /// 计算并施加弹簧力
    ///
    /// 移植自 `btGeneric6DofSpringConstraint::internalUpdateSprings`
    ///
    /// # 参数
    /// - `rigid_body_set`: Rapier 刚体集合
    /// - `dt`: 物理步的时间步长（秒）
    /// - `solver_iterations`: 求解器迭代次数（用于 Bullet 兼容的速度因子计算）
    /// - `stiffness_scale`: 弹簧刚度缩放因子（默认 1.0）
    pub fn apply_spring_forces(
        &self,
        rigid_body_set: &mut RigidBodySet,
        dt: f32,
        _solver_iterations: usize,
        stiffness_scale: f32,
    ) {
        if !self.has_any_spring() {
            return;
        }

        // 获取两个刚体的世界变换和速度（不可变借用）
        let (trans_a, trans_b, linvel_a, linvel_b, angvel_a, angvel_b) = {
            let rb_a = match rigid_body_set.get(self.body_a) {
                Some(rb) => rb,
                None => return,
            };
            let rb_b = match rigid_body_set.get(self.body_b) {
                Some(rb) => rb,
                None => return,
            };
            (
                *rb_a.position(), *rb_b.position(),
                *rb_a.linvel(), *rb_b.linvel(),
                *rb_a.angvel(), *rb_b.angvel(),
            )
        };

        // === calculateTransforms ===
        // Bullet: m_calculatedTransformA = transA * m_frameInA
        let calc_trans_a = trans_a * self.frame_in_a;
        let calc_trans_b = trans_b * self.frame_in_b;

        // === calculateLinearInfo ===
        let linear_diff = Self::calculate_linear_diff(&calc_trans_a, &calc_trans_b);

        // === calculateAngleInfo ===
        let angle_diff = Self::calculate_angle_diff(&calc_trans_a, &calc_trans_b);

        // 计算约束轴（世界空间）
        // 与 Bullet calculateAngleInfo 一致
        let calc_axis = Self::calculate_constraint_axes(&calc_trans_a, &calc_trans_b);

        // 收集弹簧力
        let mut force_on_b = Vector3::<f32>::zeros();
        let mut torque_on_b = Vector3::<f32>::zeros();

        // === internalUpdateSprings — 线性部分 ===
        // Bullet 原文：
        //   force = delta * m_springStiffness[i]
        //   velFactor = fps * m_springDamping[i] / numIterations
        //   m_linearLimits.m_targetVelocity[i] = velFactor * force
        //   m_linearLimits.m_maxMotorForce[i] = |force|
        //
        // 这里 targetVelocity 被约束求解器用来施加冲量。
        // 我们的等效做法：将弹簧力作为外力直接施加。
        // 力的方向沿约束 A 参考系的对应轴。
        for i in 0..3 {
            if self.spring_enabled[i] {
                let curr_pos = linear_diff[i];
                let delta = curr_pos - self.equilibrium_point[i];
                let stiffness = self.spring_stiffness[i] * stiffness_scale;

                // Bullet 原始公式：force = delta * stiffness
                // targetVelocity = (fps * damping / iterations) * force
                // 等效冲量 = targetVelocity * dt = (damping / iterations) * force
                //
                // 但通过约束求解器施加时还会除以有效质量，
                // 我们直接用力 = stiffness * delta，让 F=ma 自然处理质量
                let force_mag = stiffness * delta;

                // 力的方向：沿约束 A 参考系的第 i 个轴
                let axis = calc_trans_a.rotation * unit_vector(i);

                // 弹簧力施加到 B 上（正 delta → 正力 → 推 B 回到平衡点）
                // 注意：Bullet 中 targetVelocity 为正时驱动 B 回到 A，
                // 所以这里 force 的正方向与约束轴一致
                // 反向作用力施加到 A
                force_on_b += axis * force_mag;
                
                // 速度阻尼：沿约束轴的相对线速度分量
                // damping 值越接近 0 阻尼越强，1.0 = 无阻尼（与 Bullet3 一致）
                let damping_factor = 1.0 - self.spring_damping[i].clamp(0.0, 1.0);
                if damping_factor > 1e-6 && dt > 1e-6 {
                    let rel_vel = linvel_b - linvel_a;
                    let vel_along_axis = rel_vel.dot(&axis);
                    let damping_force = -damping_factor * stiffness.abs().sqrt() * vel_along_axis;
                    force_on_b += axis * damping_force;
                }
            }
        }

        // === internalUpdateSprings — 角度部分 ===
        // Bullet 原文：
        //   force = -delta * m_springStiffness[i + 3]  // 注意负号！
        //   velFactor = fps * m_springDamping[i + 3] / numIterations
        //   m_angularLimits[i].m_targetVelocity = velFactor * force
        for i in 0..3 {
            if self.spring_enabled[i + 3] {
                let curr_pos = angle_diff[i];
                let delta = curr_pos - self.equilibrium_point[i + 3];
                let stiffness = self.spring_stiffness[i + 3] * stiffness_scale;

                // 注意负号：与 Bullet 一致
                let torque_mag = -stiffness * delta;

                // 力矩方向：沿约束轴
                torque_on_b += calc_axis[i] * torque_mag;
                
                // 角速度阻尼
                let damping_factor = 1.0 - self.spring_damping[i + 3].clamp(0.0, 1.0);
                if damping_factor > 1e-6 && dt > 1e-6 {
                    let rel_angvel = angvel_b - angvel_a;
                    let angvel_along_axis = rel_angvel.dot(&calc_axis[i]);
                    let damping_torque = -damping_factor * stiffness.abs().sqrt() * angvel_along_axis;
                    torque_on_b += calc_axis[i] * damping_torque;
                }
            }
        }

        // 施加力到刚体（作用力与反作用力）
        // B 上施加弹簧力，A 上施加反作用力
        if let Some(rb_b) = rigid_body_set.get_mut(self.body_b) {
            if rb_b.is_dynamic() {
                rb_b.add_force(force_on_b, true);
                rb_b.add_torque(torque_on_b, true);
            }
        }
        if let Some(rb_a) = rigid_body_set.get_mut(self.body_a) {
            if rb_a.is_dynamic() {
                rb_a.add_force(-force_on_b, true);
                rb_a.add_torque(-torque_on_b, true);
            }
        }
    }

    // ========== 内部数学函数（与 Bullet3 完全一致）==========

    /// 计算线性位移（在约束 A 参考系的局部空间中）
    ///
    /// 对应 `btGeneric6DofConstraint::calculateLinearInfo`
    fn calculate_linear_diff(
        calc_trans_a: &Isometry3<f32>,
        calc_trans_b: &Isometry3<f32>,
    ) -> Vector3<f32> {
        // Bullet: m_calculatedLinearDiff = inv(basisA) * (originB - originA)
        let diff_world = calc_trans_b.translation.vector - calc_trans_a.translation.vector;
        calc_trans_a.rotation.inverse() * diff_world
    }

    /// 计算角度偏差（XYZ 欧拉角分解）
    ///
    /// 对应 `btGeneric6DofConstraint::calculateAngleInfo` 中调用的 `matrixToEulerXYZ`
    fn calculate_angle_diff(
        calc_trans_a: &Isometry3<f32>,
        calc_trans_b: &Isometry3<f32>,
    ) -> Vector3<f32> {
        // Bullet: relative_frame = inv(basisA) * basisB
        // matrixToEulerXYZ(relative_frame, angles)
        let relative_rot = calc_trans_a.rotation.inverse() * calc_trans_b.rotation;
        let mat = relative_rot.to_rotation_matrix();
        Self::matrix_to_euler_xyz(mat.matrix())
    }

    /// 计算约束轴（世界空间）
    ///
    /// 对应 `btGeneric6DofConstraint::calculateAngleInfo` 中的轴计算
    fn calculate_constraint_axes(
        calc_trans_a: &Isometry3<f32>,
        calc_trans_b: &Isometry3<f32>,
    ) -> [Vector3<f32>; 3] {
        // Bullet:
        //   axis0 = calculatedTransformB.basis.column(0)  // B 的 X 轴
        //   axis2 = calculatedTransformA.basis.column(2)  // A 的 Z 轴
        //   axis[1] = axis2.cross(axis0).normalized()
        //   axis[0] = axis[1].cross(axis2).normalized()
        //   axis[2] = axis0.cross(axis[1]).normalized()
        let axis0_b = calc_trans_b.rotation * Vector3::x();
        let axis2_a = calc_trans_a.rotation * Vector3::z();

        let mut axes = [Vector3::zeros(); 3];

        let cross_1 = axis2_a.cross(&axis0_b);
        axes[1] = safe_normalize(cross_1);
        axes[0] = safe_normalize(axes[1].cross(&axis2_a));
        axes[2] = safe_normalize(axis0_b.cross(&axes[1]));

        axes
    }

    /// 从旋转矩阵提取 XYZ 欧拉角
    ///
    /// **完全移植自** `bullet3/src/BulletDynamics/ConstraintSolver/btGeneric6DofConstraint.cpp`
    /// 中的 `matrixToEulerXYZ` 函数。
    ///
    /// 注意：Bullet 使用 `btGetMatrixElem(mat, index)` 按列优先顺序访问，
    /// 即 `mat[index % 3][index / 3]`（row, col）。
    ///
    /// nalgebra Matrix3 使用列优先存储，`mat[(row, col)]` 访问。
    fn matrix_to_euler_xyz(mat: &Matrix3<f32>) -> Vector3<f32> {
        // btGetMatrixElem(mat, 2) = mat[2%3][2/3] = mat[2][0] = mat[(2,0)]
        let fi = mat[(2, 0)];

        if fi < 1.0 {
            if fi > -1.0 {
                // btGetMatrixElem(mat, 5) = mat[2][1] = mat[(2,1)]
                // btGetMatrixElem(mat, 8) = mat[2][2] = mat[(2,2)]
                // btGetMatrixElem(mat, 1) = mat[1][0] = mat[(1,0)]
                // btGetMatrixElem(mat, 0) = mat[0][0] = mat[(0,0)]
                Vector3::new(
                    (-mat[(2, 1)]).atan2(mat[(2, 2)]),
                    fi.asin(),
                    (-mat[(1, 0)]).atan2(mat[(0, 0)]),
                )
            } else {
                // btGetMatrixElem(mat, 3) = mat[0][1] = mat[(0,1)]
                // btGetMatrixElem(mat, 4) = mat[1][1] = mat[(1,1)]
                Vector3::new(
                    -(mat[(0, 1)]).atan2(mat[(1, 1)]),  // -atan2(R01, R11)
                    -std::f32::consts::FRAC_PI_2,
                    0.0,
                )
            }
        } else {
            Vector3::new(
                (mat[(0, 1)]).atan2(mat[(1, 1)]),  // atan2(R01, R11)
                std::f32::consts::FRAC_PI_2,
                0.0,
            )
        }
    }
}

/// 返回第 i 个单位向量
fn unit_vector(i: usize) -> Vector3<f32> {
    match i {
        0 => Vector3::x(),
        1 => Vector3::y(),
        2 => Vector3::z(),
        _ => Vector3::zeros(),
    }
}

/// 安全归一化，零向量返回零向量
fn safe_normalize(v: Vector3<f32>) -> Vector3<f32> {
    let norm_sq = v.norm_squared();
    if norm_sq > 1e-12 {
        v / norm_sq.sqrt()
    } else {
        Vector3::zeros()
    }
}
