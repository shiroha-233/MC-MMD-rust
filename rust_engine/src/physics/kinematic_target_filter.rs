use glam::{Mat3, Mat4, Quat};

/// FollowBone 静止目标过滤阈值。低于阈值的逐帧变化仍精确跟随骨骼，
/// 但不交给 Bullet 差分成运动学速度，避免死区累计后形成周期性脉冲。
const TRANSLATION_DEAD_ZONE: f32 = 0.0015;
const ROTATION_DEAD_ZONE: f32 = 0.0015;

#[derive(Clone, Copy, Debug)]
pub(crate) struct FilteredTarget {
    pub transform: Mat4,
    pub suppressed: bool,
    pub translation_delta: f32,
    pub rotation_delta: f32,
}

#[derive(Default)]
pub(crate) struct KinematicTargetFilter {
    previous_requested: Vec<Option<Mat4>>,
}

impl KinematicTargetFilter {
    pub fn resize(&mut self, body_count: usize) {
        self.previous_requested.resize(body_count, None);
    }

    pub fn filter(&mut self, body_index: usize, requested: Mat4) -> FilteredTarget {
        if body_index >= self.previous_requested.len() {
            self.previous_requested.resize(body_index + 1, None);
        }

        let Some(previous) = self.previous_requested[body_index] else {
            self.previous_requested[body_index] = Some(requested);
            return FilteredTarget {
                transform: requested,
                suppressed: false,
                translation_delta: 0.0,
                rotation_delta: 0.0,
            };
        };

        let translation_delta = (requested.w_axis.truncate() - previous.w_axis.truncate()).length();
        let rotation_delta = rotation_delta(previous, requested);
        let finite = translation_delta.is_finite()
            && rotation_delta.is_finite()
            && requested
                .to_cols_array()
                .iter()
                .all(|value| value.is_finite());
        let suppressed = finite
            && translation_delta < TRANSLATION_DEAD_ZONE
            && rotation_delta < ROTATION_DEAD_ZONE;
        self.previous_requested[body_index] = Some(requested);

        FilteredTarget {
            transform: requested,
            suppressed,
            translation_delta,
            rotation_delta,
        }
    }

    /// 初始化、显式重同步和卡顿恢复使用硬重置后的精确姿态作为新基准。
    pub fn seed(&mut self, body_index: usize, transform: Mat4) {
        if body_index >= self.previous_requested.len() {
            self.previous_requested.resize(body_index + 1, None);
        }
        self.previous_requested[body_index] = Some(transform);
    }
}

fn rotation_delta(previous: Mat4, current: Mat4) -> f32 {
    let previous_rotation = Quat::from_mat3(&Mat3::from_mat4(previous)).normalize();
    let current_rotation = Quat::from_mat3(&Mat3::from_mat4(current)).normalize();
    (2.0 * previous_rotation
        .dot(current_rotation)
        .abs()
        .clamp(0.0, 1.0)
        .acos())
    .min(std::f32::consts::PI)
}

#[cfg(test)]
mod tests {
    use super::*;
    use glam::{Quat, Vec3};

    #[test]
    fn suppresses_stationary_noise_but_keeps_exact_current_target() {
        let mut filter = KinematicTargetFilter::default();
        let initial = Mat4::from_translation(Vec3::new(1.0, 2.0, 3.0));
        assert!(!filter.filter(0, initial).suppressed);

        let noisy = Mat4::from_rotation_translation(
            Quat::from_rotation_y(0.001),
            Vec3::new(1.001, 2.0, 3.0),
        );
        let result = filter.filter(0, noisy);
        assert!(result.suppressed);
        assert!(result.transform.abs_diff_eq(noisy, 1e-7));
    }

    #[test]
    fn repeated_small_motion_does_not_accumulate_into_a_velocity_pulse() {
        let mut filter = KinematicTargetFilter::default();
        filter.seed(0, Mat4::IDENTITY);

        for step in 1..=4 {
            let x = step as f32 * 0.001;
            let result = filter.filter(0, Mat4::from_translation(Vec3::new(x, 0.0, 0.0)));
            assert!(result.suppressed);
            assert_eq!(result.transform.w_axis.x, x);
        }
    }

    #[test]
    fn preserves_kinematic_velocity_for_large_per_frame_motion() {
        let mut filter = KinematicTargetFilter::default();
        filter.seed(0, Mat4::IDENTITY);

        let result = filter.filter(0, Mat4::from_translation(Vec3::new(0.002, 0.0, 0.0)));
        assert!(!result.suppressed);
        assert_eq!(result.transform.w_axis.x, 0.002);
    }

    #[test]
    fn seed_replaces_old_model_history() {
        let mut filter = KinematicTargetFilter::default();
        filter.seed(0, Mat4::from_translation(Vec3::X));
        filter.seed(0, Mat4::from_translation(Vec3::Y));

        let result = filter.filter(0, Mat4::from_translation(Vec3::new(0.0, 1.001, 0.0)));
        assert!(result.suppressed);
        assert!(result
            .transform
            .abs_diff_eq(Mat4::from_translation(Vec3::new(0.0, 1.001, 0.0)), 1e-7));
    }
}
