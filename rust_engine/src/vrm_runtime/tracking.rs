use std::collections::HashMap;

use glam::{Quat, Vec3};

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

#[derive(Clone, Copy, Debug, Default)]
pub struct HandGripOffset {
    pub position_offset: Vec3,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct HandTrackingCalibration {
    pub left: HandGripOffset,
    pub right: HandGripOffset,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct BodyTrackingCalibration {
    pub head_rest_anchor_model: Vec3,
    pub shoulder_width_model: f32,
    pub shoulder_depth_model: f32,
    pub body_yaw_follow_gain: f32,
    pub body_translation_clamp_model: f32,
    pub shoulder_follow_gain: f32,
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
            body_translation_clamp_model: if self.body_translation_clamp_model > 1e-6 {
                self.body_translation_clamp_model
            } else {
                defaults.body_translation_clamp_model
            },
            shoulder_follow_gain: if self.shoulder_follow_gain > 1e-6 {
                self.shoulder_follow_gain
            } else {
                defaults.shoulder_follow_gain
            },
        }
    }
}

#[derive(Clone, Debug)]
pub struct VrmRuntimeInput {
    pub tracking: Option<VrmTrackingInput>,
    pub hand_calibration: HandTrackingCalibration,
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
            body_calibration: BodyTrackingCalibration::default(),
            look_at: LookAtInput::default(),
            expression_weights: HashMap::new(),
            first_person: true,
            delta_time: 0.0,
        }
    }
}
