//! Control-rig entry flow adapted from UniVRM's runtime pipeline ordering.

use glam::{Quat, Vec3};

use crate::model::MmdModel;
use crate::vr::{VrTrackedPose, VrTrackingFrame, XR_TO_MODEL_SCALE};

use super::tracking::{
    BodyTrackingCalibration, HandGripOffset, HandTrackingCalibration, TrackedPose, VrmTrackingInput,
};

const BONE_HEAD_NAMES: &[&str] = &["頭", "head", "Head"];
const BONE_BODY_ANCHOR_NAMES: &[&str] = &[
    "センター",
    "center",
    "Center",
    "グルーブ",
    "groove",
    "Groove",
    "下半身",
    "hips",
    "Hips",
    "pelvis",
    "Pelvis",
    "全ての親",
    "root",
    "Root",
    "上半身3",
    "upperChest",
    "UpperChest",
    "上半身2",
    "chest",
    "Chest",
    "上半身",
    "spine",
    "Spine",
];
const BONE_LEFT_SHOULDER_NAMES: &[&str] = &[
    "左肩",
    "leftShoulder",
    "LeftShoulder",
    "left_shoulder",
    "clavicle_l",
    "左腕",
    "leftUpperArm",
    "LeftUpperArm",
];
const BONE_RIGHT_SHOULDER_NAMES: &[&str] = &[
    "右肩",
    "rightShoulder",
    "RightShoulder",
    "right_shoulder",
    "clavicle_r",
    "右腕",
    "rightUpperArm",
    "RightUpperArm",
];

pub struct ControlRigRuntime {
    default_body_calibration: BodyTrackingCalibration,
}

impl ControlRigRuntime {
    pub fn new(model: &mut MmdModel) -> Self {
        Self {
            default_body_calibration: derive_default_body_calibration(model),
        }
    }

    pub fn apply_tracking(
        &self,
        model: &mut MmdModel,
        tracking: Option<VrmTrackingInput>,
        hand_calibration: HandTrackingCalibration,
        body_calibration: BodyTrackingCalibration,
    ) {
        let Some(tracking) = tracking else {
            model.set_vr_enabled(false);
            model.set_vr_tracking_frame(None);
            return;
        };

        let resolved_body = body_calibration.resolve_with_defaults(self.default_body_calibration);
        let frame = build_tracking_frame(tracking, hand_calibration, resolved_body);

        model.set_vr_enabled(true);
        model.set_vr_ik_strength(1.0);
        model.set_vr_tracking_frame(Some(frame));
    }
}

fn build_tracking_frame(
    tracking: VrmTrackingInput,
    hand_calibration: HandTrackingCalibration,
    body_calibration: BodyTrackingCalibration,
) -> VrTrackingFrame {
    VrTrackingFrame {
        head: convert_pose(tracking.head),
        right_palm: convert_pose(apply_hand_grip_offset(
            tracking.right_hand,
            hand_calibration.right,
        )),
        left_palm: convert_pose(apply_hand_grip_offset(
            tracking.left_hand,
            hand_calibration.left,
        )),
        body_calibration,
    }
}

fn apply_hand_grip_offset(pose: TrackedPose, hand_offset: HandGripOffset) -> TrackedPose {
    if !pose.valid || hand_offset.position_offset == Vec3::ZERO {
        return pose;
    }

    TrackedPose {
        position: pose.position + pose.orientation * hand_offset.position_offset,
        ..pose
    }
}

fn convert_pose(raw: TrackedPose) -> VrTrackedPose {
    if !raw.valid {
        return VrTrackedPose::default();
    }

    let basis = Quat::from_rotation_y(std::f32::consts::PI);
    VrTrackedPose {
        position: (basis * raw.position) * XR_TO_MODEL_SCALE,
        rotation: (basis * raw.orientation * basis).normalize(),
        valid: true,
    }
}

fn derive_default_body_calibration(model: &mut MmdModel) -> BodyTrackingCalibration {
    model.init_head_detection();

    let head_anchor = model
        .tracked_head_bone_index()
        .and_then(|index| model.bone_manager.get_bone(index))
        .map(|bone| bone.initial_position)
        .or_else(|| first_existing_initial_position(model, BONE_HEAD_NAMES))
        .unwrap_or(Vec3::new(0.0, model.get_head_bone_rest_position_y(), 0.0));
    let body_anchor = first_existing_initial_position(model, BONE_BODY_ANCHOR_NAMES)
        .unwrap_or(Vec3::new(0.0, head_anchor.y * 0.55, 0.0));
    let left_shoulder = first_existing_initial_position(model, BONE_LEFT_SHOULDER_NAMES)
        .unwrap_or(body_anchor + Vec3::new(-XR_TO_MODEL_SCALE * 0.15, XR_TO_MODEL_SCALE * 0.05, 0.0));
    let right_shoulder = first_existing_initial_position(model, BONE_RIGHT_SHOULDER_NAMES)
        .unwrap_or(body_anchor + Vec3::new(XR_TO_MODEL_SCALE * 0.15, XR_TO_MODEL_SCALE * 0.05, 0.0));

    let shoulder_width_model = (right_shoulder - left_shoulder)
        .length()
        .max(XR_TO_MODEL_SCALE * 0.28);
    let shoulder_depth_model = ((left_shoulder.z - body_anchor.z).abs()
        + (right_shoulder.z - body_anchor.z).abs())
        * 0.5_f32
        .max(XR_TO_MODEL_SCALE * 0.08);

    BodyTrackingCalibration {
        head_rest_anchor_model: head_anchor,
        shoulder_width_model,
        shoulder_depth_model,
        body_yaw_follow_gain: 0.65,
        body_translation_clamp_model: (shoulder_width_model * 0.6).max(XR_TO_MODEL_SCALE * 0.12),
        shoulder_follow_gain: 0.45,
    }
}

fn first_existing_initial_position(model: &MmdModel, names: &[&str]) -> Option<Vec3> {
    names.iter().find_map(|name| {
        model
            .bone_manager
            .find_bone_by_name(name)
            .and_then(|index| model.bone_manager.get_bone(index))
            .map(|bone| bone.initial_position)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_tracking_frame_should_apply_hand_offset_before_coordinate_conversion() {
        let right_hand = TrackedPose {
            position: Vec3::new(1.0, 2.0, 3.0),
            orientation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
            valid: true,
        };
        let tracking = VrmTrackingInput {
            head: TrackedPose::identity(),
            left_hand: TrackedPose::identity(),
            right_hand,
        };
        let hand_calibration = HandTrackingCalibration {
            right: HandGripOffset {
                position_offset: Vec3::new(0.0, -0.05, -0.07),
            },
            ..HandTrackingCalibration::default()
        };

        let frame = build_tracking_frame(
            tracking,
            hand_calibration,
            BodyTrackingCalibration::default(),
        );
        let basis = Quat::from_rotation_y(std::f32::consts::PI);
        let expected_position = (basis
            * (right_hand.position + right_hand.orientation * hand_calibration.right.position_offset))
            * XR_TO_MODEL_SCALE;

        assert_vec3_eq(frame.right_palm.position, expected_position);
    }

    #[test]
    fn body_calibration_should_use_runtime_defaults_for_zero_fields() {
        let defaults = BodyTrackingCalibration {
            head_rest_anchor_model: Vec3::new(1.0, 2.0, 3.0),
            shoulder_width_model: 4.0,
            shoulder_depth_model: 5.0,
            body_yaw_follow_gain: 0.6,
            body_translation_clamp_model: 7.0,
            shoulder_follow_gain: 0.4,
        };
        let resolved = BodyTrackingCalibration::default().resolve_with_defaults(defaults);

        assert_eq!(resolved.head_rest_anchor_model, defaults.head_rest_anchor_model);
        assert_eq!(resolved.shoulder_width_model, defaults.shoulder_width_model);
        assert_eq!(resolved.shoulder_depth_model, defaults.shoulder_depth_model);
        assert_eq!(resolved.body_yaw_follow_gain, defaults.body_yaw_follow_gain);
        assert_eq!(
            resolved.body_translation_clamp_model,
            defaults.body_translation_clamp_model
        );
        assert_eq!(resolved.shoulder_follow_gain, defaults.shoulder_follow_gain);
    }

    fn assert_vec3_eq(actual: Vec3, expected: Vec3) {
        let delta = actual - expected;
        assert!(
            delta.length() < 1e-5,
            "vec mismatch: actual={actual:?} expected={expected:?}",
        );
    }
}
