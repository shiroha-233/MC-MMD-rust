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

#[derive(Clone, Debug)]
pub struct VrmRuntimeInput {
    pub tracking: Option<VrmTrackingInput>,
    pub look_at: LookAtInput,
    pub expression_weights: HashMap<ExpressionKey, f32>,
    pub first_person: bool,
    pub delta_time: f32,
}

impl Default for VrmRuntimeInput {
    fn default() -> Self {
        Self {
            tracking: None,
            look_at: LookAtInput::default(),
            expression_weights: HashMap::new(),
            first_person: true,
            delta_time: 0.0,
        }
    }
}
