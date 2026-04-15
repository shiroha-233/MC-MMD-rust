//! Control-rig entry flow adapted from UniVRM's runtime pipeline ordering.

use glam::Quat;

use crate::model::MmdModel;

use super::tracking::{TrackedPose, VrmTrackingInput};

const XR_TO_MODEL_SCALE: f32 = 12.5;
pub struct ControlRigRuntime;

impl ControlRigRuntime {
    pub fn new(_head_anchor_model_y: f32) -> Self {
        Self
    }

    pub fn apply_tracking(&self, model: &mut MmdModel, tracking: Option<VrmTrackingInput>) {
        let Some(tracking) = tracking else {
            model.set_vr_enabled(false);
            model.set_vr_tracking_data(&[0.0; 21]);
            return;
        };

        let mut packet = [0.0f32; 21];
        write_pose(&mut packet[0..7], tracking.head, TrackRole::Head);
        write_pose(
            &mut packet[7..14],
            tracking.right_hand,
            TrackRole::RightHand,
        );
        write_pose(&mut packet[14..21], tracking.left_hand, TrackRole::LeftHand);

        model.set_vr_enabled(true);
        model.set_vr_ik_strength(1.0);
        model.set_vr_tracking_data(&packet);
    }
}

#[derive(Clone, Copy)]
enum TrackRole {
    Head,
    RightHand,
    LeftHand,
}

fn write_pose(output: &mut [f32], pose: TrackedPose, role: TrackRole) {
    let position = convert_position(pose.position);
    let rotation = convert_orientation(pose.orientation, role);
    output[0] = position.x;
    output[1] = position.y;
    output[2] = position.z;
    output[3] = rotation.x;
    output[4] = rotation.y;
    output[5] = rotation.z;
    output[6] = rotation.w;
}

fn convert_position(raw: glam::Vec3) -> glam::Vec3 {
    let basis = Quat::from_rotation_y(std::f32::consts::PI);
    (basis * raw) * XR_TO_MODEL_SCALE
}

fn convert_orientation(raw: Quat, role: TrackRole) -> Quat {
    let basis = Quat::from_rotation_y(std::f32::consts::PI);
    let mapped = (basis * raw * basis).normalize();
    match role {
        TrackRole::Head => mapped,
        TrackRole::RightHand | TrackRole::LeftHand => mapped,
    }
}
