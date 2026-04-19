//! Control-rig entry flow adapted from UniVRM's runtime pipeline ordering.

use glam::{Quat, Vec3};

use crate::model::MmdModel;
use crate::vr::{VrTrackedPose, VrTrackingFrame, XR_TO_MODEL_SCALE};

use super::tracking::{
    ArmIkCalibration, BodyTrackingCalibration, HandGripOffset, HandTrackingCalibration,
    TrackedPose, VrmTrackingInput,
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
        arm_ik_calibration: ArmIkCalibration,
        body_calibration: BodyTrackingCalibration,
    ) {
        let Some(tracking) = tracking else {
            model.set_vr_enabled(false);
            model.set_vr_tracking_frame(None);
            return;
        };
        let resolved_body = body_calibration.resolve_with_defaults(self.default_body_calibration);
        let frame = build_tracking_frame(
            tracking,
            hand_calibration,
            arm_ik_calibration,
            resolved_body,
        );

        model.set_vr_enabled(true);
        model.set_vr_tracking_frame(Some(frame));
    }
}

pub(crate) fn resolve_tracking_frame_for_model(
    model: &mut MmdModel,
    tracking: Option<VrmTrackingInput>,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
    body_calibration: BodyTrackingCalibration,
) -> Option<VrTrackingFrame> {
    let tracking = tracking?;
    let default_body_calibration = derive_default_body_calibration(model);
    let resolved_body = body_calibration.resolve_with_defaults(default_body_calibration);
    Some(build_tracking_frame(
        tracking,
        hand_calibration,
        arm_ik_calibration,
        resolved_body,
    ))
}

pub(crate) fn resolve_java_tracking_frame_for_model(
    model: &mut MmdModel,
    tracking: Option<VrmTrackingInput>,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
    body_calibration: BodyTrackingCalibration,
) -> Option<VrTrackingFrame> {
    let tracking = tracking?;
    let default_body_calibration = derive_default_body_calibration(model);
    let resolved_body = body_calibration.resolve_with_defaults(default_body_calibration);
    let mut frame = build_tracking_frame_with_converter(
        tracking,
        hand_calibration,
        arm_ik_calibration,
        resolved_body,
        convert_java_pose,
    );
    frame.arm_ik_calibration.hand_face_flip = false;
    if !model.is_vrm() {
        frame.arm_ik_calibration.left.wrist_rotation_offset_model =
            Quat::from_rotation_z(-std::f32::consts::FRAC_PI_2);
        frame.arm_ik_calibration.right.wrist_rotation_offset_model =
            Quat::from_rotation_z(std::f32::consts::FRAC_PI_2);
    }
    Some(frame)
}

fn build_tracking_frame(
    tracking: VrmTrackingInput,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
    body_calibration: BodyTrackingCalibration,
) -> VrTrackingFrame {
    build_tracking_frame_with_converter(
        tracking,
        hand_calibration,
        arm_ik_calibration,
        body_calibration,
        convert_pose,
    )
}

fn build_tracking_frame_with_converter(
    tracking: VrmTrackingInput,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
    body_calibration: BodyTrackingCalibration,
    convert: fn(TrackedPose) -> VrTrackedPose,
) -> VrTrackingFrame {
    VrTrackingFrame {
        head: convert(tracking.head),
        right_palm: convert(apply_hand_grip_offset(
            tracking.right_hand,
            hand_calibration.right,
        )),
        left_palm: convert(apply_hand_grip_offset(
            tracking.left_hand,
            hand_calibration.left,
        )),
        arm_ik_calibration,
        body_calibration,
    }
}

fn apply_hand_grip_offset(pose: TrackedPose, hand_offset: HandGripOffset) -> TrackedPose {
    if !pose.valid {
        return pose;
    }

    TrackedPose {
        position: pose.position + pose.orientation * hand_offset.position_offset,
        orientation: (pose.orientation * hand_offset.orientation_offset).normalize(),
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

fn convert_java_pose(raw: TrackedPose) -> VrTrackedPose {
    if !raw.valid {
        return VrTrackedPose::default();
    }

    VrTrackedPose {
        position: raw.position * XR_TO_MODEL_SCALE,
        // Java/Vivecraft packets are already converted into player-local space on the Java side.
        // Applying the old demo Y-180 basis here flips the handedness of X/Z-axis rotations,
        // which makes controller roll/pitch drive the model in the opposite direction.
        rotation: raw.orientation.normalize(),
        valid: true,
    }
}

fn derive_default_body_calibration(model: &mut MmdModel) -> BodyTrackingCalibration {
    model.init_head_detection();

    let head_anchor = {
        let eye_anchor = model.get_eye_bone_animated_position();
        if eye_anchor.length_squared() > 1e-6 {
            eye_anchor
        } else {
            model
                .tracked_head_bone_index()
                .and_then(|index| model.bone_manager.get_bone(index))
                .map(|bone| bone.initial_position)
                .or_else(|| first_existing_initial_position(model, BONE_HEAD_NAMES))
                .unwrap_or(Vec3::new(0.0, model.get_head_bone_rest_position_y(), 0.0))
        }
    };
    let body_anchor = first_existing_initial_position(model, BONE_BODY_ANCHOR_NAMES)
        .unwrap_or(Vec3::new(0.0, head_anchor.y * 0.55, 0.0));
    let left_shoulder = first_existing_initial_position(model, BONE_LEFT_SHOULDER_NAMES).unwrap_or(
        body_anchor + Vec3::new(-XR_TO_MODEL_SCALE * 0.15, XR_TO_MODEL_SCALE * 0.05, 0.0),
    );
    let right_shoulder = first_existing_initial_position(model, BONE_RIGHT_SHOULDER_NAMES)
        .unwrap_or(
            body_anchor + Vec3::new(XR_TO_MODEL_SCALE * 0.15, XR_TO_MODEL_SCALE * 0.05, 0.0),
        );

    let shoulder_width_model = (right_shoulder - left_shoulder)
        .length()
        .max(XR_TO_MODEL_SCALE * 0.28);
    let shoulder_depth_model = ((left_shoulder.z - body_anchor.z).abs()
        + (right_shoulder.z - body_anchor.z).abs())
        * 0.5_f32.max(XR_TO_MODEL_SCALE * 0.08);

    BodyTrackingCalibration {
        head_rest_anchor_model: head_anchor,
        shoulder_width_model,
        shoulder_depth_model,
        body_yaw_follow_gain: 0.65,
        horizontal_translation_follow_gain: 0.9,
        vertical_translation_follow_gain: 0.95,
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
    use crate::skeleton::{BoneLink, BoneManager};

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
                ..HandGripOffset::default()
            },
            ..HandTrackingCalibration::default()
        };

        let frame = build_tracking_frame(
            tracking,
            hand_calibration,
            ArmIkCalibration::default(),
            BodyTrackingCalibration::default(),
        );
        let basis = Quat::from_rotation_y(std::f32::consts::PI);
        let expected_position = (basis
            * (right_hand.position
                + right_hand.orientation * hand_calibration.right.position_offset))
            * XR_TO_MODEL_SCALE;

        assert_vec3_eq(frame.right_palm.position, expected_position);
    }

    #[test]
    fn build_tracking_frame_should_apply_hand_rotation_before_coordinate_conversion() {
        let right_hand = TrackedPose {
            position: Vec3::new(1.0, 2.0, 3.0),
            orientation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
            valid: true,
        };
        let hand_calibration = HandTrackingCalibration {
            right: HandGripOffset {
                orientation_offset: Quat::from_rotation_x(std::f32::consts::FRAC_PI_4),
                ..HandGripOffset::default()
            },
            ..HandTrackingCalibration::default()
        };

        let frame = build_tracking_frame(
            VrmTrackingInput {
                head: TrackedPose::identity(),
                left_hand: TrackedPose::identity(),
                right_hand,
            },
            hand_calibration,
            ArmIkCalibration::default(),
            BodyTrackingCalibration::default(),
        );
        let basis = Quat::from_rotation_y(std::f32::consts::PI);
        let expected_rotation =
            (basis * (right_hand.orientation * hand_calibration.right.orientation_offset) * basis)
                .normalize();

        assert_quat_eq(frame.right_palm.rotation, expected_rotation);
    }

    #[test]
    fn java_tracking_frame_should_preserve_player_local_position() {
        let mut model = MmdModel::new();
        model.bone_manager = make_calibration_test_bones(true, true);

        let frame = resolve_java_tracking_frame_for_model(
            &mut model,
            Some(VrmTrackingInput {
                head: TrackedPose {
                    position: Vec3::new(1.0, 2.0, 3.0),
                    orientation: Quat::IDENTITY,
                    valid: true,
                },
                ..VrmTrackingInput::default()
            }),
            HandTrackingCalibration::default(),
            ArmIkCalibration::default(),
            BodyTrackingCalibration::default(),
        )
        .expect("java frame");

        assert_vec3_eq(
            frame.head.position,
            Vec3::new(1.0, 2.0, 3.0) * XR_TO_MODEL_SCALE,
        );
    }

    #[test]
    fn java_tracking_frame_should_preserve_rotation_direction() {
        let mut model = MmdModel::new();
        model.bone_manager = make_calibration_test_bones(true, true);
        let raw_rotation = Quat::from_rotation_z(std::f32::consts::FRAC_PI_4);

        let frame = resolve_java_tracking_frame_for_model(
            &mut model,
            Some(VrmTrackingInput {
                head: TrackedPose {
                    position: Vec3::ZERO,
                    orientation: raw_rotation,
                    valid: true,
                },
                ..VrmTrackingInput::default()
            }),
            HandTrackingCalibration::default(),
            ArmIkCalibration::default(),
            BodyTrackingCalibration::default(),
        )
        .expect("java frame");

        assert_quat_eq(frame.head.rotation, raw_rotation.normalize());
    }

    #[test]
    fn java_tracking_frame_should_preserve_hand_rotation_direction() {
        let mut model = MmdModel::new();
        model.bone_manager = make_calibration_test_bones(true, true);
        let raw_rotation = Quat::from_rotation_z(std::f32::consts::FRAC_PI_4);

        let frame = resolve_java_tracking_frame_for_model(
            &mut model,
            Some(VrmTrackingInput {
                right_hand: TrackedPose {
                    position: Vec3::ZERO,
                    orientation: raw_rotation,
                    valid: true,
                },
                ..VrmTrackingInput::default()
            }),
            HandTrackingCalibration::default(),
            ArmIkCalibration::default(),
            BodyTrackingCalibration::default(),
        )
        .expect("java frame");

        assert_quat_eq(frame.right_palm.rotation, raw_rotation.normalize());
        assert!(!frame.arm_ik_calibration.hand_face_flip);
        assert_quat_eq(
            frame.arm_ik_calibration.left.wrist_rotation_offset_model,
            Quat::from_rotation_z(-std::f32::consts::FRAC_PI_2),
        );
        assert_quat_eq(
            frame.arm_ik_calibration.right.wrist_rotation_offset_model,
            Quat::from_rotation_z(std::f32::consts::FRAC_PI_2),
        );
    }

    #[test]
    fn body_calibration_should_use_runtime_defaults_for_zero_fields() {
        let defaults = BodyTrackingCalibration {
            head_rest_anchor_model: Vec3::new(1.0, 2.0, 3.0),
            shoulder_width_model: 4.0,
            shoulder_depth_model: 5.0,
            body_yaw_follow_gain: 0.6,
            horizontal_translation_follow_gain: 0.9,
            vertical_translation_follow_gain: 0.95,
            body_translation_clamp_model: 7.0,
            shoulder_follow_gain: 0.4,
        };
        let resolved = BodyTrackingCalibration::default().resolve_with_defaults(defaults);

        assert_eq!(
            resolved.head_rest_anchor_model,
            defaults.head_rest_anchor_model
        );
        assert_eq!(resolved.shoulder_width_model, defaults.shoulder_width_model);
        assert_eq!(resolved.shoulder_depth_model, defaults.shoulder_depth_model);
        assert_eq!(resolved.body_yaw_follow_gain, defaults.body_yaw_follow_gain);
        assert_eq!(
            resolved.horizontal_translation_follow_gain,
            defaults.horizontal_translation_follow_gain
        );
        assert_eq!(
            resolved.vertical_translation_follow_gain,
            defaults.vertical_translation_follow_gain
        );
        assert_eq!(
            resolved.body_translation_clamp_model,
            defaults.body_translation_clamp_model
        );
        assert_eq!(resolved.shoulder_follow_gain, defaults.shoulder_follow_gain);
    }

    #[test]
    fn build_tracking_frame_should_pass_arm_ik_calibration_through_unchanged() {
        let arm_ik_calibration = ArmIkCalibration {
            left: super::super::tracking::ArmIkHandCalibration {
                wrist_offset_model: Vec3::new(1.0, 2.0, 3.0),
                wrist_rotation_offset_model: Quat::from_rotation_z(0.1),
            },
            right: super::super::tracking::ArmIkHandCalibration {
                wrist_offset_model: Vec3::new(-1.0, -2.0, -3.0),
                wrist_rotation_offset_model: Quat::from_rotation_z(-0.2),
            },
            forearm_twist_ratio: 0.75,
            hand_face_flip: false,
        };

        let frame = build_tracking_frame(
            VrmTrackingInput::default(),
            HandTrackingCalibration::default(),
            arm_ik_calibration,
            BodyTrackingCalibration::default(),
        );

        assert_eq!(
            frame.arm_ik_calibration.left.wrist_offset_model,
            arm_ik_calibration.left.wrist_offset_model
        );
        assert_eq!(
            frame.arm_ik_calibration.right.wrist_offset_model,
            arm_ik_calibration.right.wrist_offset_model
        );
        assert_quat_eq(
            frame.arm_ik_calibration.left.wrist_rotation_offset_model,
            arm_ik_calibration.left.wrist_rotation_offset_model,
        );
        assert_quat_eq(
            frame.arm_ik_calibration.right.wrist_rotation_offset_model,
            arm_ik_calibration.right.wrist_rotation_offset_model,
        );
        assert_eq!(
            frame.arm_ik_calibration.forearm_twist_ratio,
            arm_ik_calibration.forearm_twist_ratio
        );
    }

    #[test]
    fn body_calibration_should_preserve_negative_clamp_override() {
        let defaults = BodyTrackingCalibration {
            body_translation_clamp_model: 7.0,
            ..BodyTrackingCalibration::default()
        };
        let override_calibration = BodyTrackingCalibration {
            body_translation_clamp_model: -1.0,
            ..BodyTrackingCalibration::default()
        };

        let resolved = override_calibration.resolve_with_defaults(defaults);

        assert_eq!(resolved.body_translation_clamp_model, -1.0);
    }

    #[test]
    fn body_calibration_should_preserve_zero_clamp_override() {
        let defaults = BodyTrackingCalibration {
            body_translation_clamp_model: 7.0,
            ..BodyTrackingCalibration::default()
        };
        let override_calibration = BodyTrackingCalibration {
            body_translation_clamp_model: 0.0,
            ..BodyTrackingCalibration::default()
        };

        let resolved = override_calibration.resolve_with_defaults(defaults);

        assert_eq!(resolved.body_translation_clamp_model, 0.0);
    }

    #[test]
    fn derive_default_body_calibration_should_prefer_eye_anchor_when_available() {
        let mut model = MmdModel::new();
        model.bone_manager = make_calibration_test_bones(true, true);

        let calibration = derive_default_body_calibration(&mut model);

        assert_eq!(
            calibration.head_rest_anchor_model,
            Vec3::new(0.0, 17.0, 0.2)
        );
    }

    fn make_calibration_test_bones(has_left_eye: bool, has_right_eye: bool) -> BoneManager {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut neck = BoneLink::new("Neck".to_string());
        neck.initial_position = Vec3::new(0.0, 15.0, 0.0);
        neck.parent_index = 0;
        bones.add_bone(neck);

        let mut head = BoneLink::new("Head".to_string());
        head.initial_position = Vec3::new(0.0, 17.0, 0.0);
        head.parent_index = 1;
        bones.add_bone(head);

        if has_left_eye {
            let mut left_eye = BoneLink::new("LeftEye".to_string());
            left_eye.initial_position = Vec3::new(-0.3, 17.0, 0.2);
            left_eye.parent_index = 2;
            bones.add_bone(left_eye);
        }

        if has_right_eye {
            let mut right_eye = BoneLink::new("RightEye".to_string());
            right_eye.initial_position = Vec3::new(0.3, 17.0, 0.2);
            right_eye.parent_index = 2;
            bones.add_bone(right_eye);
        }

        bones.build_hierarchy();
        bones
    }

    fn assert_vec3_eq(actual: Vec3, expected: Vec3) {
        let delta = actual - expected;
        assert!(
            delta.length() < 1e-5,
            "vec mismatch: actual={actual:?} expected={expected:?}",
        );
    }

    fn assert_quat_eq(actual: Quat, expected: Quat) {
        let similarity = actual.dot(expected).abs();
        assert!(
            similarity > 1.0 - 1e-5,
            "quat mismatch: actual={actual:?} expected={expected:?}",
        );
    }
}
