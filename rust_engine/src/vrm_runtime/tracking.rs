use std::collections::HashMap;

use glam::{EulerRot, Quat, Vec3};

use super::expression::ExpressionKey;
use super::look_at::LookAtInput;

#[derive(Clone, Copy, Debug, Default)]
pub struct TrackedPose {
    pub position: Vec3,
    pub orientation: Quat,
    pub valid: bool,
}

impl TrackedPose {
    pub fn identity() -> Self {
        Self {
            position: Vec3::ZERO,
            orientation: Quat::IDENTITY,
            valid: false,
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub struct VrmTrackingInput {
    pub head: TrackedPose,
    pub left_hand: TrackedPose,
    pub right_hand: TrackedPose,
}

#[derive(Clone, Copy, Debug)]
pub struct HandGripOffset {
    pub position_offset: Vec3,
    pub orientation_offset: Quat,
}

impl Default for HandGripOffset {
    fn default() -> Self {
        Self {
            position_offset: Vec3::ZERO,
            orientation_offset: Quat::IDENTITY,
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub struct HandTrackingCalibration {
    pub left: HandGripOffset,
    pub right: HandGripOffset,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct ArmIkHandCalibration {
    pub wrist_offset_model: Vec3,
}

#[derive(Clone, Copy, Debug)]
pub struct ArmIkCalibration {
    pub left: ArmIkHandCalibration,
    pub right: ArmIkHandCalibration,
    pub forearm_twist_ratio: f32,
}

impl Default for ArmIkCalibration {
    fn default() -> Self {
        Self {
            left: ArmIkHandCalibration::default(),
            right: ArmIkHandCalibration::default(),
            forearm_twist_ratio: 0.4,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct BodyTrackingCalibration {
    pub head_rest_anchor_model: Vec3,
    pub shoulder_width_model: f32,
    pub shoulder_depth_model: f32,
    pub body_yaw_follow_gain: f32,
    pub horizontal_translation_follow_gain: f32,
    pub vertical_translation_follow_gain: f32,
    pub body_translation_clamp_model: f32,
    pub shoulder_follow_gain: f32,
}

impl Default for BodyTrackingCalibration {
    fn default() -> Self {
        Self {
            head_rest_anchor_model: Vec3::ZERO,
            shoulder_width_model: 0.0,
            shoulder_depth_model: 0.0,
            body_yaw_follow_gain: 0.0,
            horizontal_translation_follow_gain: 0.0,
            vertical_translation_follow_gain: 0.0,
            body_translation_clamp_model: f32::NAN,
            shoulder_follow_gain: 0.0,
        }
    }
}

impl BodyTrackingCalibration {
    pub(crate) fn resolve_with_defaults(self, defaults: Self) -> Self {
        Self {
            head_rest_anchor_model: if self.head_rest_anchor_model.length_squared() > 1e-6 {
                self.head_rest_anchor_model
            } else {
                defaults.head_rest_anchor_model
            },
            shoulder_width_model: if self.shoulder_width_model > 1e-6 {
                self.shoulder_width_model
            } else {
                defaults.shoulder_width_model
            },
            shoulder_depth_model: if self.shoulder_depth_model > 1e-6 {
                self.shoulder_depth_model
            } else {
                defaults.shoulder_depth_model
            },
            body_yaw_follow_gain: if self.body_yaw_follow_gain > 1e-6 {
                self.body_yaw_follow_gain
            } else {
                defaults.body_yaw_follow_gain
            },
            horizontal_translation_follow_gain: if self.horizontal_translation_follow_gain > 1e-6 {
                self.horizontal_translation_follow_gain
            } else {
                defaults.horizontal_translation_follow_gain
            },
            vertical_translation_follow_gain: if self.vertical_translation_follow_gain > 1e-6 {
                self.vertical_translation_follow_gain
            } else {
                defaults.vertical_translation_follow_gain
            },
            body_translation_clamp_model: if self.body_translation_clamp_model.is_nan() {
                defaults.body_translation_clamp_model
            } else if self.body_translation_clamp_model <= 0.0 {
                self.body_translation_clamp_model
            } else {
                self.body_translation_clamp_model
            },
            shoulder_follow_gain: if self.shoulder_follow_gain > 1e-6 {
                self.shoulder_follow_gain
            } else {
                defaults.shoulder_follow_gain
            },
        }
    }
}

const CONTROLLER_TO_PALM_POSITION_OFFSET_METERS: Vec3 = Vec3::new(0.0, -0.012, -0.018);
const VRM_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES: [f32; 3] = [0.0, 0.0, 0.0];
const VRM_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES: [f32; 3] = [0.0, 0.0, 0.0];
const PMX_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES: [f32; 3] = [90.0, 0.0, -90.0];
const PMX_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES: [f32; 3] = [90.0, 0.0, 90.0];

pub(crate) fn vrm_controller_hand_tracking_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: CONTROLLER_TO_PALM_POSITION_OFFSET_METERS,
            orientation_offset: rotation_degrees_to_quat(
                VRM_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES,
            ),
        },
        right: HandGripOffset {
            position_offset: CONTROLLER_TO_PALM_POSITION_OFFSET_METERS,
            orientation_offset: rotation_degrees_to_quat(
                VRM_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES,
            ),
        },
    }
}

pub(crate) fn pmx_controller_hand_tracking_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: CONTROLLER_TO_PALM_POSITION_OFFSET_METERS,
            orientation_offset: rotation_degrees_to_quat(
                PMX_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES,
            ),
        },
        right: HandGripOffset {
            position_offset: CONTROLLER_TO_PALM_POSITION_OFFSET_METERS,
            orientation_offset: rotation_degrees_to_quat(
                PMX_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES,
            ),
        },
    }
}

pub(crate) fn vivecraft_body_tracking_calibration() -> BodyTrackingCalibration {
    BodyTrackingCalibration {
        // Minecraft already owns the avatar's world-space root position and body yaw.
        // Vivecraft tracking should only drive local upper-body pose, not move the whole model.
        body_yaw_follow_gain: 0.0,
        horizontal_translation_follow_gain: 0.0,
        vertical_translation_follow_gain: 0.0,
        body_translation_clamp_model: 0.0,
        shoulder_follow_gain: 0.0,
        ..BodyTrackingCalibration::default()
    }
}

fn rotation_degrees_to_quat(value: [f32; 3]) -> Quat {
    Quat::from_euler(
        EulerRot::XYZ,
        value[0].to_radians(),
        value[1].to_radians(),
        value[2].to_radians(),
    )
    .normalize()
}

#[derive(Clone, Debug)]
pub struct VrmRuntimeInput {
    pub tracking: Option<VrmTrackingInput>,
    pub hand_calibration: HandTrackingCalibration,
    pub arm_ik_calibration: ArmIkCalibration,
    pub body_calibration: BodyTrackingCalibration,
    pub look_at: LookAtInput,
    pub expression_weights: HashMap<ExpressionKey, f32>,
    pub first_person: bool,
    pub delta_time: f32,
}

impl Default for VrmRuntimeInput {
    fn default() -> Self {
        Self {
            tracking: None,
            hand_calibration: HandTrackingCalibration::default(),
            arm_ik_calibration: ArmIkCalibration::default(),
            body_calibration: BodyTrackingCalibration::default(),
            look_at: LookAtInput::default(),
            expression_weights: HashMap::new(),
            first_person: true,
            delta_time: 0.0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn arm_ik_calibration_default_should_use_zero_offsets_and_twist_ratio() {
        let calibration = ArmIkCalibration::default();

        assert_eq!(calibration.left.wrist_offset_model, Vec3::ZERO);
        assert_eq!(calibration.right.wrist_offset_model, Vec3::ZERO);
        assert!((calibration.forearm_twist_ratio - 0.4).abs() < 1e-6);
    }

    #[test]
    fn vrm_runtime_input_default_should_include_default_arm_ik_calibration() {
        let input = VrmRuntimeInput::default();

        assert_eq!(
            input.hand_calibration.left.orientation_offset,
            Quat::IDENTITY
        );
        assert_eq!(
            input.hand_calibration.right.orientation_offset,
            Quat::IDENTITY
        );
        assert_eq!(input.arm_ik_calibration.left.wrist_offset_model, Vec3::ZERO);
        assert_eq!(
            input.arm_ik_calibration.right.wrist_offset_model,
            Vec3::ZERO
        );
        assert!((input.arm_ik_calibration.forearm_twist_ratio - 0.4).abs() < 1e-6);
    }

    #[test]
    fn hand_tracking_calibration_default_should_use_identity_rotation_offsets() {
        let calibration = HandTrackingCalibration::default();

        assert_eq!(calibration.left.position_offset, Vec3::ZERO);
        assert_eq!(calibration.right.position_offset, Vec3::ZERO);
        assert_eq!(calibration.left.orientation_offset, Quat::IDENTITY);
        assert_eq!(calibration.right.orientation_offset, Quat::IDENTITY);
    }
}
