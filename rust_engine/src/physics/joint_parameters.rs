//! PMX 关节参数规范化。

const SPRING_EPSILON: f32 = 1e-6;
const EXPORTED_PERCENT_SCALE: f32 = 0.01;
const SUSPICIOUS_LINEAR_LIMIT: f32 = 10.0;
const SUSPICIOUS_ANGULAR_LIMIT: f32 = std::f32::consts::PI;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(super) struct JointAxisParameters {
    pub lower: f32,
    pub upper: f32,
    pub stiffness: f32,
    pub spring_enabled: bool,
}

impl JointAxisParameters {
    fn from_raw(lower: f32, upper: f32, stiffness: f32) -> Self {
        let lower = finite_or_zero(lower);
        let upper = finite_or_zero(upper);
        // Bullet 6DOF 使用 lower > upper 表示自由轴。部分 PMX（例如横向裙摆连接）
        // 会有意采用这种编码，因此必须保留原始顺序，不能把自由轴收紧为硬限位。
        // 自由轴没有有效的约束区间可供 Bullet 定义弹簧回正目标；继续启用 motor
        // 会让横向裙环在自由移动和弹簧纠偏之间持续振荡。
        let spring_enabled =
            lower <= upper && stiffness.is_finite() && stiffness.abs() > SPRING_EPSILON;
        Self {
            lower,
            upper,
            // Bullet 刚度必须非负，导出器写入的负值会形成正反馈并导致物理发散。
            stiffness: if spring_enabled { stiffness.abs() } else { 0.0 },
            spring_enabled,
        }
    }

    /// 限制特定轴只允许向外旋转，屏蔽向身体内侧折倒的方向。
    /// 确保 0.0（绑定姿态）始终落在 [lower, upper] 内，且不把受限轴损坏为 lower > upper 自由轴。
    pub fn restrict_inward_rotation(&mut self, positive_is_outward: bool) {
        if self.lower > self.upper {
            // 原生声明为自由轴的无需收紧。
            return;
        }
        if positive_is_outward {
            // 正向旋转为向外，允许 [0.0, max(upper, 0.0)]
            self.lower = 0.0;
            self.upper = self.upper.max(0.0);
        } else {
            // 负向旋转为向外，允许 [min(lower, 0.0), 0.0]
            self.lower = self.lower.min(0.0);
            self.upper = 0.0;
        }
    }

    /// 为缺失弹簧的有效旋转轴补入兼容刚度，不覆盖模型原本的弹簧参数。
    pub fn install_fallback_spring(&mut self, stiffness: f32) {
        if self.spring_enabled
            || self.lower > self.upper
            || self.upper - self.lower <= SPRING_EPSILON
        {
            return;
        }

        self.stiffness = stiffness.max(0.0);
        self.spring_enabled = self.stiffness > SPRING_EPSILON;
    }

    /// 将有效旋转轴收紧到以绑定姿态为中心的对称范围。
    pub fn clamp_symmetric_rotation(&mut self, angle: f32) {
        if self.lower > self.upper || self.upper - self.lower <= SPRING_EPSILON {
            return;
        }

        let angle = angle.abs();
        self.lower = self.lower.max(-angle).min(0.0);
        self.upper = self.upper.min(angle).max(0.0);
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(super) struct JointParameters {
    pub linear: [JointAxisParameters; 3],
    pub angular: [JointAxisParameters; 3],
}

impl JointParameters {
    pub(super) fn from_pmx(
        mut position_min: [f32; 3],
        mut position_max: [f32; 3],
        mut rotation_min: [f32; 3],
        mut rotation_max: [f32; 3],
        position_spring: [f32; 3],
        rotation_spring: [f32; 3],
    ) -> Self {
        if has_percent_encoded_limits(position_min, position_max, rotation_min, rotation_max) {
            // 部分导出器会把小数限位按百分数写入 PMX；仅还原同组中的超尺度分量。
            scale_outliers(&mut position_min, SUSPICIOUS_LINEAR_LIMIT);
            scale_outliers(&mut position_max, SUSPICIOUS_LINEAR_LIMIT);
            scale_outliers(&mut rotation_min, SUSPICIOUS_ANGULAR_LIMIT);
            scale_outliers(&mut rotation_max, SUSPICIOUS_ANGULAR_LIMIT);
        }

        Self {
            linear: std::array::from_fn(|axis| {
                JointAxisParameters::from_raw(
                    position_min[axis],
                    position_max[axis],
                    position_spring[axis],
                )
            }),
            angular: std::array::from_fn(|axis| {
                JointAxisParameters::from_raw(
                    rotation_min[axis],
                    rotation_max[axis],
                    rotation_spring[axis],
                )
            }),
        }
    }
}

fn has_percent_encoded_limits(
    position_min: [f32; 3],
    position_max: [f32; 3],
    rotation_min: [f32; 3],
    rotation_max: [f32; 3],
) -> bool {
    let has_large_linear = position_min
        .into_iter()
        .chain(position_max)
        .any(|value| value.is_finite() && value.abs() >= SUSPICIOUS_LINEAR_LIMIT);
    let has_large_angular = rotation_min
        .into_iter()
        .chain(rotation_max)
        .any(|value| value.is_finite() && value.abs() > SUSPICIOUS_ANGULAR_LIMIT);
    has_large_linear && has_large_angular
}

fn scale_outliers(values: &mut [f32; 3], threshold: f32) {
    for value in values {
        if value.is_finite() && value.abs() >= threshold {
            *value *= EXPORTED_PERCENT_SCALE;
        }
    }
}

fn finite_or_zero(value: f32) -> f32 {
    if value.is_finite() {
        value
    } else {
        0.0
    }
}

#[cfg(test)]
mod tests {
    use super::JointParameters;

    #[test]
    fn zero_and_invalid_springs_are_disabled_on_all_axes() {
        let parameters = JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-0.2; 3],
            [0.3; 3],
            [0.0, f32::NAN, 0.5],
            [0.0, 1e-7, 2.0],
        );

        assert!(!parameters.linear[0].spring_enabled);
        assert!(!parameters.linear[1].spring_enabled);
        assert!(parameters.linear[2].spring_enabled);
        assert!(!parameters.angular[0].spring_enabled);
        assert!(!parameters.angular[1].spring_enabled);
        assert!(parameters.angular[2].spring_enabled);
    }

    #[test]
    fn negative_exported_stiffness_is_restored_to_a_positive_bullet_spring() {
        let parameters = JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-0.2; 3],
            [0.2; 3],
            [-300.0, 225.0, -300.0],
            [16.0, 8.0, 16.0],
        );

        assert_eq!(parameters.linear[0].stiffness, 300.0);
        assert_eq!(parameters.linear[1].stiffness, 225.0);
        assert_eq!(parameters.linear[2].stiffness, 300.0);
        assert!(parameters.linear.iter().all(|axis| axis.spring_enabled));
    }

    #[test]
    fn limits_remain_independent_from_spring_enablement() {
        let parameters = JointParameters::from_pmx(
            [-1.0, -2.0, -3.0],
            [1.0, 2.0, 3.0],
            [-0.1, -0.2, -0.3],
            [0.4, 0.5, 0.6],
            [0.0; 3],
            [0.0; 3],
        );

        assert_eq!(parameters.linear[1].lower, -2.0);
        assert_eq!(parameters.linear[1].upper, 2.0);
        assert_eq!(parameters.angular[2].lower, -0.3);
        assert_eq!(parameters.angular[2].upper, 0.6);
        assert!(!parameters.angular[2].spring_enabled);
    }

    #[test]
    fn percent_encoded_joint_limits_are_restored_as_a_group() {
        let parameters = JointParameters::from_pmx(
            [-51.5, 0.0, -51.5],
            [0.206, 51.5, 51.5],
            [-5.235_988, -5.235_988, 0.0],
            [5.235_988, 5.235_988, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        assert!((parameters.linear[0].lower + 0.515).abs() < 1e-6);
        assert!((parameters.linear[0].upper - 0.206).abs() < 1e-6);
        assert!((parameters.linear[1].upper - 0.515).abs() < 1e-6);
        assert!((parameters.angular[0].lower + 0.052_359_88).abs() < 1e-6);
        assert!((parameters.angular[1].upper - 0.052_359_88).abs() < 1e-6);
    }

    #[test]
    fn isolated_wide_limits_are_not_treated_as_percent_encoding() {
        let wide_linear = JointParameters::from_pmx(
            [-20.0, 0.0, 0.0],
            [20.0, 0.0, 0.0],
            [-0.2; 3],
            [0.2; 3],
            [0.0; 3],
            [0.0; 3],
        );
        let wide_angular = JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-5.0, 0.0, 0.0],
            [5.0, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        assert_eq!(wide_linear.linear[0].lower, -20.0);
        assert_eq!(wide_angular.angular[0].upper, 5.0);
    }

    #[test]
    fn reversed_teio_skirt_limits_remain_bullet_free_axes() {
        let parameters = JointParameters::from_pmx(
            [0.035_435_125, -0.05, 0.0375],
            [-0.035_435_125, 0.05, -0.0375],
            [-0.122_173_05, -0.069_813_17, -0.122_173_05],
            [0.122_173_05, 0.069_813_17, 0.122_173_05],
            [-300.0, 225.0, -300.0],
            [16.0, 8.0, 16.0],
        );

        assert_eq!(parameters.linear[0].lower, 0.035_435_125);
        assert_eq!(parameters.linear[0].upper, -0.035_435_125);
        assert_eq!(parameters.linear[1].lower, -0.05);
        assert_eq!(parameters.linear[1].upper, 0.05);
        assert_eq!(parameters.linear[2].lower, 0.0375);
        assert_eq!(parameters.linear[2].upper, -0.0375);
        assert!(!parameters.linear[0].spring_enabled);
        assert!(!parameters.linear[2].spring_enabled);
        assert_eq!(parameters.linear[0].stiffness, 0.0);
        assert_eq!(parameters.linear[2].stiffness, 0.0);
        assert_eq!(parameters.angular[0].lower, -0.122_173_05);
        assert_eq!(parameters.angular[0].upper, 0.122_173_05);
        assert_eq!(parameters.angular[2].lower, -0.122_173_05);
        assert_eq!(parameters.angular[2].upper, 0.122_173_05);
    }
}
