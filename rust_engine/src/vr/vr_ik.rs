//! VR IK solver.

use glam::{Mat3, Mat4, Quat, Vec3};

use crate::skeleton::BoneManager;
use crate::vrm_runtime::BodyTrackingCalibration;

pub(crate) const XR_TO_MODEL_SCALE: f32 = 12.5;
const MODEL_TO_WORLD_SCALE: f32 = 1.0 / XR_TO_MODEL_SCALE;
const TRACK_POINT_SIZE: usize = 7;
const BONE_HEAD_NAMES: &[&str] = &["頭", "head", "Head"];
const BONE_NECK_NAMES: &[&str] = &["首", "neck", "Neck"];
const BONE_ROOT_NAMES: &[&str] = &["全ての親", "root", "Root"];
const BONE_CENTER_NAMES: &[&str] = &["センター", "center", "Center"];
const BONE_GROOVE_NAMES: &[&str] = &["グルーブ", "groove", "Groove"];
const BONE_HIPS_NAMES: &[&str] = &["下半身", "hips", "Hips", "pelvis", "Pelvis"];
const BONE_UPPER_BODY_NAMES: &[&str] = &["上半身", "spine", "Spine", "chest", "Chest"];
const BONE_CHEST_NAMES: &[&str] = &["上半身2", "chest", "Chest"];
const BONE_UPPER_CHEST_NAMES: &[&str] = &["上半身3", "upperChest", "UpperChest"];
const BONE_LEFT_SHOULDER_NAMES: &[&str] = &[
    "左肩",
    "leftShoulder",
    "LeftShoulder",
    "left_shoulder",
    "clavicle_l",
];
const BONE_RIGHT_SHOULDER_NAMES: &[&str] = &[
    "右肩",
    "rightShoulder",
    "RightShoulder",
    "right_shoulder",
    "clavicle_r",
];
const BONE_LEFT_ARM_NAMES: &[&str] = &[
    "左腕",
    "leftUpperArm",
    "LeftUpperArm",
    "left_arm",
    "LeftArm",
];
const BONE_LEFT_ELBOW_NAMES: &[&str] = &[
    "左ひじ",
    "leftLowerArm",
    "LeftLowerArm",
    "left_elbow",
    "LeftElbow",
];
const BONE_LEFT_WRIST_NAMES: &[&str] =
    &["左手首", "leftHand", "LeftHand", "left_wrist", "LeftWrist"];
const BONE_RIGHT_ARM_NAMES: &[&str] = &[
    "右腕",
    "rightUpperArm",
    "RightUpperArm",
    "right_arm",
    "RightArm",
];
const BONE_RIGHT_ELBOW_NAMES: &[&str] = &[
    "右ひじ",
    "rightLowerArm",
    "RightLowerArm",
    "right_elbow",
    "RightElbow",
];
const BONE_RIGHT_WRIST_NAMES: &[&str] = &[
    "右手首",
    "rightHand",
    "RightHand",
    "right_wrist",
    "RightWrist",
];
const LEFT_THUMB_ROOT_NAMES: &[&str] = &[
    "左親指０",
    "左親指１",
    "leftThumbMetacarpal",
    "leftThumbProximal",
    "leftThumb1",
];
const RIGHT_THUMB_ROOT_NAMES: &[&str] = &[
    "右親指０",
    "右親指１",
    "rightThumbMetacarpal",
    "rightThumbProximal",
    "rightThumb1",
];
const LEFT_INDEX_ROOT_NAMES: &[&str] = &["左人指１", "leftIndexProximal", "leftIndex1"];
const RIGHT_INDEX_ROOT_NAMES: &[&str] = &["右人指１", "rightIndexProximal", "rightIndex1"];
const LEFT_MIDDLE_ROOT_NAMES: &[&str] = &["左中指１", "leftMiddleProximal", "leftMiddle1"];
const RIGHT_MIDDLE_ROOT_NAMES: &[&str] = &["右中指１", "rightMiddleProximal", "rightMiddle1"];
const LEFT_RING_ROOT_NAMES: &[&str] = &["左薬指１", "leftRingProximal", "leftRing1"];
const RIGHT_RING_ROOT_NAMES: &[&str] = &["右薬指１", "rightRingProximal", "rightRing1"];
const LEFT_LITTLE_ROOT_NAMES: &[&str] = &["左小指１", "leftLittleProximal", "leftLittle1"];
const RIGHT_LITTLE_ROOT_NAMES: &[&str] = &["右小指１", "rightLittleProximal", "rightLittle1"];

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct VrTrackedPose {
    pub(crate) position: Vec3,
    pub(crate) rotation: Quat,
    pub(crate) valid: bool,
}

impl VrTrackedPose {
    fn from_slice(data: &[f32]) -> Self {
        let raw_rotation = Quat::from_xyzw(data[3], data[4], data[5], data[6]);
        let valid = raw_rotation.length_squared() > 1e-6;
        Self {
            position: Vec3::new(data[0], data[1], data[2]),
            rotation: if valid {
                raw_rotation.normalize()
            } else {
                Quat::IDENTITY
            },
            valid,
        }
    }

    fn write_to_slice(self, output: &mut [f32]) {
        if self.valid {
            output[0] = self.position.x;
            output[1] = self.position.y;
            output[2] = self.position.z;
            output[3] = self.rotation.x;
            output[4] = self.rotation.y;
            output[5] = self.rotation.z;
            output[6] = self.rotation.w;
        } else {
            output.fill(0.0);
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct VrTrackingFrame {
    pub(crate) head: VrTrackedPose,
    pub(crate) right_palm: VrTrackedPose,
    pub(crate) left_palm: VrTrackedPose,
    pub(crate) body_calibration: BodyTrackingCalibration,
}

impl VrTrackingFrame {
    pub(crate) fn from_tracking_packet(
        tracking_data: &[f32; 21],
        body_calibration: BodyTrackingCalibration,
    ) -> Self {
        Self {
            head: VrTrackedPose::from_slice(&tracking_data[0..TRACK_POINT_SIZE]),
            right_palm: VrTrackedPose::from_slice(
                &tracking_data[TRACK_POINT_SIZE..TRACK_POINT_SIZE * 2],
            ),
            left_palm: VrTrackedPose::from_slice(
                &tracking_data[TRACK_POINT_SIZE * 2..TRACK_POINT_SIZE * 3],
            ),
            body_calibration,
        }
    }

    pub(crate) fn to_tracking_packet(self) -> [f32; 21] {
        let mut packet = [0.0f32; 21];
        self.head.write_to_slice(&mut packet[0..TRACK_POINT_SIZE]);
        self.right_palm
            .write_to_slice(&mut packet[TRACK_POINT_SIZE..TRACK_POINT_SIZE * 2]);
        self.left_palm
            .write_to_slice(&mut packet[TRACK_POINT_SIZE * 2..TRACK_POINT_SIZE * 3]);
        packet
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct VrDebugState {
    pub(crate) head_local_model: Vec3,
    pub(crate) body_anchor_model: Vec3,
    pub(crate) left_palm_target_model: Vec3,
    pub(crate) right_palm_target_model: Vec3,
    pub(crate) left_wrist_solved_model: Vec3,
    pub(crate) right_wrist_solved_model: Vec3,
    pub(crate) left_wrist_error_cm: f32,
    pub(crate) right_wrist_error_cm: f32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum HandSide {
    Left,
    Right,
}

impl HandSide {
    fn from_left(is_left: bool) -> Self {
        if is_left {
            Self::Left
        } else {
            Self::Right
        }
    }

    fn side_sign(self) -> f32 {
        match self {
            Self::Left => -1.0,
            Self::Right => 1.0,
        }
    }
}

#[derive(Clone, Copy, Debug)]
struct WristTarget {
    position: Vec3,
    rotation: Quat,
}

#[derive(Clone, Copy, Debug)]
struct HandBindPose {
    rotation_offset: Quat,
    position_offset: Vec3,
}

#[derive(Clone, Copy, Debug, Default)]
struct BodySolveState {
    body_anchor_model: Vec3,
    head_residual_model: Vec3,
}

#[derive(Clone, Copy, Debug, Default)]
struct ArmSolveResult {
    solved_wrist_model: Vec3,
    wrist_error_cm: f32,
}

struct BoneCache {
    root: Option<usize>,
    center: Option<usize>,
    groove: Option<usize>,
    hips: Option<usize>,
    head: Option<usize>,
    neck: Option<usize>,
    upper_body: Option<usize>,
    chest: Option<usize>,
    upper_chest: Option<usize>,
    left_shoulder: Option<usize>,
    right_shoulder: Option<usize>,
    left_arm: Option<usize>,
    left_elbow: Option<usize>,
    left_wrist: Option<usize>,
    right_arm: Option<usize>,
    right_elbow: Option<usize>,
    right_wrist: Option<usize>,
    initialized: bool,
}

impl Default for BoneCache {
    fn default() -> Self {
        Self {
            root: None,
            center: None,
            groove: None,
            hips: None,
            head: None,
            neck: None,
            upper_body: None,
            chest: None,
            upper_chest: None,
            left_shoulder: None,
            right_shoulder: None,
            left_arm: None,
            left_elbow: None,
            left_wrist: None,
            right_arm: None,
            right_elbow: None,
            right_wrist: None,
            initialized: false,
        }
    }
}

pub struct VrIkSolver {
    cache: BoneCache,
}

impl VrIkSolver {
    pub fn new() -> Self {
        Self {
            cache: BoneCache::default(),
        }
    }

    fn ensure_cache(&mut self, bones: &BoneManager) {
        if self.cache.initialized {
            return;
        }
        self.cache.initialized = true;
        self.cache.root = find_first_existing(bones, BONE_ROOT_NAMES);
        self.cache.center = find_first_existing(bones, BONE_CENTER_NAMES);
        self.cache.groove = find_first_existing(bones, BONE_GROOVE_NAMES);
        self.cache.hips = find_first_existing(bones, BONE_HIPS_NAMES);
        self.cache.head = find_first_existing(bones, BONE_HEAD_NAMES);
        self.cache.neck = find_first_existing(bones, BONE_NECK_NAMES);
        self.cache.upper_body = find_first_existing(bones, BONE_UPPER_BODY_NAMES);
        self.cache.chest = find_first_existing(bones, BONE_CHEST_NAMES);
        self.cache.upper_chest = find_first_existing(bones, BONE_UPPER_CHEST_NAMES);
        self.cache.left_shoulder = find_first_existing(bones, BONE_LEFT_SHOULDER_NAMES);
        self.cache.right_shoulder = find_first_existing(bones, BONE_RIGHT_SHOULDER_NAMES);
        self.cache.left_arm = find_first_existing(bones, BONE_LEFT_ARM_NAMES);
        self.cache.left_elbow = find_first_existing(bones, BONE_LEFT_ELBOW_NAMES);
        self.cache.left_wrist = find_first_existing(bones, BONE_LEFT_WRIST_NAMES);
        self.cache.right_arm = find_first_existing(bones, BONE_RIGHT_ARM_NAMES);
        self.cache.right_elbow = find_first_existing(bones, BONE_RIGHT_ELBOW_NAMES);
        self.cache.right_wrist = find_first_existing(bones, BONE_RIGHT_WRIST_NAMES);

        log::info!(
            "VR IK bone cache: root={:?}/{:?}/{:?}/{:?} head={:?} neck={:?} upper_body={:?}/{:?}/{:?} shoulders={:?}/{:?} L_chain={:?}/{:?}/{:?} R_chain={:?}/{:?}/{:?}",
            self.cache.root,
            self.cache.center,
            self.cache.groove,
            self.cache.hips,
            self.cache.head,
            self.cache.neck,
            self.cache.upper_body,
            self.cache.chest,
            self.cache.upper_chest,
            self.cache.left_shoulder,
            self.cache.right_shoulder,
            self.cache.left_arm,
            self.cache.left_elbow,
            self.cache.left_wrist,
            self.cache.right_arm,
            self.cache.right_elbow,
            self.cache.right_wrist,
        );
    }

    pub fn solve(&mut self, bones: &mut BoneManager, tracking_data: &[f32; 21], strength: f32) {
        let body_calibration = BodyTrackingCalibration::default();
        let frame = VrTrackingFrame::from_tracking_packet(tracking_data, body_calibration);
        let _ = self.solve_tracking_frame(bones, &frame, strength);
    }

    pub(crate) fn solve_tracking_frame(
        &mut self,
        bones: &mut BoneManager,
        frame: &VrTrackingFrame,
        strength: f32,
    ) -> VrDebugState {
        if strength <= 0.0 {
            return VrDebugState::default();
        }
        self.ensure_cache(bones);

        let body_state = self.apply_body_tracking(bones, frame, strength);
        self.apply_head_tracking(bones, &frame.head, strength);
        let right_result = self.solve_arm_ik(
            bones,
            self.cache.right_shoulder,
            self.cache.right_arm,
            self.cache.right_elbow,
            self.cache.right_wrist,
            &frame.right_palm,
            &frame.body_calibration,
            body_state.head_residual_model,
            strength,
            false,
        );
        let left_result = self.solve_arm_ik(
            bones,
            self.cache.left_shoulder,
            self.cache.left_arm,
            self.cache.left_elbow,
            self.cache.left_wrist,
            &frame.left_palm,
            &frame.body_calibration,
            body_state.head_residual_model,
            strength,
            true,
        );

        VrDebugState {
            head_local_model: frame.head.position,
            body_anchor_model: body_state.body_anchor_model,
            left_palm_target_model: frame.left_palm.position,
            right_palm_target_model: frame.right_palm.position,
            left_wrist_solved_model: left_result.solved_wrist_model,
            right_wrist_solved_model: right_result.solved_wrist_model,
            left_wrist_error_cm: left_result.wrist_error_cm,
            right_wrist_error_cm: right_result.wrist_error_cm,
        }
    }

    fn apply_head_tracking(&self, bones: &mut BoneManager, head: &VrTrackedPose, strength: f32) {
        if !head.valid {
            return;
        }
        let head_idx = match self.cache.head {
            Some(index) => index,
            None => return,
        };

        let parent_global_rot = self
            .cache
            .neck
            .map(|index| mat4_rotation(bones.get_global_transform(index)))
            .unwrap_or(Quat::IDENTITY);
        let local_rot = parent_global_rot.inverse() * head.rotation;

        if strength >= 1.0 {
            bones.set_bone_rotation(head_idx, local_rot);
        } else if let Some(bone) = bones.get_bone(head_idx) {
            bones.set_bone_rotation(head_idx, bone.animation_rotate.slerp(local_rot, strength));
        }

        bones.update_single_bone_global(head_idx);
    }

    fn apply_body_tracking(
        &self,
        bones: &mut BoneManager,
        frame: &VrTrackingFrame,
        strength: f32,
    ) -> BodySolveState {
        let calibration = frame.body_calibration;
        let body_idx = self.body_anchor_index();
        let Some(body_idx) = body_idx else {
            return BodySolveState::default();
        };

        let current_transform = bones.get_global_transform(body_idx);
        let current_position = mat4_translation(current_transform);
        let current_rotation = mat4_rotation(current_transform);
        let head_delta = if frame.head.valid {
            frame.head.position - calibration.head_rest_anchor_model
        } else {
            Vec3::ZERO
        };
        let body_translation = Vec3::new(
            clamp_scalar(
                head_delta.x * calibration.horizontal_translation_follow_gain,
                calibration.body_translation_clamp_model,
            ),
            head_delta.y * calibration.vertical_translation_follow_gain,
            clamp_scalar(
                head_delta.z * calibration.horizontal_translation_follow_gain,
                calibration.body_translation_clamp_model,
            ),
        );
        let head_residual_model = head_delta - body_translation;
        let body_target_position = current_position + body_translation;
        let body_yaw = if frame.head.valid {
            extract_yaw_rotation(frame.head.rotation)
        } else {
            Quat::IDENTITY
        };
        let yaw_follow = Quat::IDENTITY.slerp(body_yaw, calibration.body_yaw_follow_gain);
        let body_target_rotation = (yaw_follow * current_rotation).normalize();

        if strength >= 1.0 {
            bones.set_global_transform(
                body_idx,
                Mat4::from_rotation_translation(body_target_rotation, body_target_position),
            );
        } else {
            let blended = Mat4::from_rotation_translation(
                current_rotation.slerp(body_target_rotation, strength),
                current_position.lerp(body_target_position, strength),
            );
            bones.set_global_transform(body_idx, blended);
        }

        BodySolveState {
            body_anchor_model: mat4_translation(bones.get_global_transform(body_idx)),
            head_residual_model,
        }
    }

    fn solve_arm_ik(
        &self,
        bones: &mut BoneManager,
        shoulder_idx: Option<usize>,
        arm_idx: Option<usize>,
        elbow_idx: Option<usize>,
        wrist_idx: Option<usize>,
        target: &VrTrackedPose,
        calibration: &BodyTrackingCalibration,
        head_residual_model: Vec3,
        strength: f32,
        is_left: bool,
    ) -> ArmSolveResult {
        if !target.valid {
            return ArmSolveResult::default();
        }
        let (arm_idx, elbow_idx, wrist_idx) = match (arm_idx, elbow_idx, wrist_idx) {
            (Some(arm), Some(elbow), Some(wrist)) => (arm, elbow, wrist),
            _ => return ArmSolveResult::default(),
        };

        let arm_pos = mat4_translation(bones.get_global_transform(arm_idx));
        let elbow_pos = mat4_translation(bones.get_global_transform(elbow_idx));
        let wrist_pos = mat4_translation(bones.get_global_transform(wrist_idx));

        let upper_len = (elbow_pos - arm_pos).length();
        let lower_len = (wrist_pos - elbow_pos).length();
        if upper_len < 1e-4 || lower_len < 1e-4 {
            return ArmSolveResult::default();
        }

        let side = HandSide::from_left(is_left);
        let wrist_target = resolve_wrist_target(bones, wrist_idx, target, side);
        let arm_origin = self.resolve_arm_origin(
            bones,
            shoulder_idx,
            arm_idx,
            wrist_target.position,
            calibration,
            head_residual_model,
            upper_len + lower_len,
            strength,
            is_left,
        );
        let target_pos = wrist_target.position;
        let to_target = target_pos - arm_origin;
        let chain_len = upper_len + lower_len;
        let target_dist = to_target.length().min(chain_len * 0.999);
        if target_dist < 1e-4 {
            return ArmSolveResult::default();
        }

        let forward = to_target / to_target.length();
        let elbow_offset = elbow_pos - arm_origin;
        let elbow_proj = forward * elbow_offset.dot(forward);
        let hint_raw = elbow_offset - elbow_proj;
        let hint_dir = if hint_raw.length_squared() > 1e-4 {
            hint_raw.normalize()
        } else {
            let default_hint = if is_left {
                Vec3::new(-0.3, -0.5, -0.7)
            } else {
                Vec3::new(0.3, -0.5, -0.7)
            };
            let proj = forward * default_hint.dot(forward);
            (default_hint - proj).normalize_or_zero()
        };

        let cos_a = ((upper_len * upper_len + target_dist * target_dist - lower_len * lower_len)
            / (2.0 * upper_len * target_dist))
            .clamp(-1.0, 1.0);
        let angle_a = cos_a.acos();

        let elbow_new = arm_origin
            + forward * (angle_a.cos() * upper_len)
            + hint_dir * (angle_a.sin() * upper_len);
        let elbow_to_target = (target_pos - elbow_new).normalize_or_zero();
        let wrist_new = elbow_new + elbow_to_target * lower_len;

        self.apply_arm_result(
            bones,
            arm_idx,
            elbow_idx,
            wrist_idx,
            arm_origin,
            arm_pos,
            elbow_pos,
            wrist_pos,
            elbow_new,
            wrist_new,
            &wrist_target,
            strength,
        );

        ArmSolveResult {
            solved_wrist_model: wrist_new,
            wrist_error_cm: (wrist_new - wrist_target.position).length()
                * MODEL_TO_WORLD_SCALE
                * 100.0,
        }
    }

    fn apply_arm_result(
        &self,
        bones: &mut BoneManager,
        arm_idx: usize,
        elbow_idx: usize,
        wrist_idx: usize,
        arm_origin: Vec3,
        old_arm: Vec3,
        old_elbow: Vec3,
        old_wrist: Vec3,
        new_elbow: Vec3,
        new_wrist: Vec3,
        wrist_target: &WristTarget,
        strength: f32,
    ) {
        let arm_rot = mat4_rotation(bones.get_global_transform(arm_idx));
        let new_arm_rot = rotate_bone_direction(arm_origin, old_elbow, new_elbow, arm_rot);

        let elbow_rot = mat4_rotation(bones.get_global_transform(elbow_idx));
        let new_elbow_rot = rotate_bone_direction(old_elbow, old_wrist, new_wrist, elbow_rot);

        if strength >= 1.0 {
            bones.set_global_transform(
                arm_idx,
                Mat4::from_rotation_translation(new_arm_rot, arm_origin),
            );
            bones.set_global_transform(
                elbow_idx,
                Mat4::from_rotation_translation(new_elbow_rot, new_elbow),
            );
            bones.set_global_transform(
                wrist_idx,
                Mat4::from_rotation_translation(wrist_target.rotation, new_wrist),
            );
        } else {
            let blend = |cur_rot: Quat, new_rot: Quat, cur_pos: Vec3, new_pos: Vec3| -> Mat4 {
                Mat4::from_rotation_translation(
                    cur_rot.slerp(new_rot, strength),
                    cur_pos.lerp(new_pos, strength),
                )
            };

            bones.set_global_transform(arm_idx, blend(arm_rot, new_arm_rot, old_arm, arm_origin));
            bones.set_global_transform(
                elbow_idx,
                blend(elbow_rot, new_elbow_rot, old_elbow, new_elbow),
            );

            let cur_wrist_rot = mat4_rotation(bones.get_global_transform(wrist_idx));
            bones.set_global_transform(
                wrist_idx,
                blend(cur_wrist_rot, wrist_target.rotation, old_wrist, new_wrist),
            );
        }
    }

    fn resolve_arm_origin(
        &self,
        bones: &mut BoneManager,
        shoulder_idx: Option<usize>,
        arm_idx: usize,
        wrist_target_position: Vec3,
        calibration: &BodyTrackingCalibration,
        head_residual_model: Vec3,
        chain_len: f32,
        strength: f32,
        is_left: bool,
    ) -> Vec3 {
        let fallback_origin = mat4_translation(bones.get_global_transform(arm_idx));
        let mut origin = shoulder_idx
            .map(|index| mat4_translation(bones.get_global_transform(index)))
            .unwrap_or(fallback_origin);

        let shoulder_follow = Vec3::new(
            head_residual_model.x,
            head_residual_model.y * 0.5,
            head_residual_model.z,
        ) * calibration.shoulder_follow_gain;
        origin += shoulder_follow;

        let to_target = wrist_target_position - origin;
        let overflow = (to_target.length() - chain_len).max(0.0);
        if overflow > 1e-4 {
            let side_sign = if is_left { -1.0 } else { 1.0 };
            let lateral_bias = Vec3::new(
                side_sign * calibration.shoulder_width_model * 0.05,
                0.0,
                -calibration.shoulder_depth_model * 0.15,
            );
            origin += (to_target.normalize_or_zero() + lateral_bias).normalize_or_zero()
                * (overflow * calibration.shoulder_follow_gain)
                    .min(calibration.shoulder_depth_model);
        }

        if let Some(shoulder_idx) = shoulder_idx {
            let current_transform = bones.get_global_transform(shoulder_idx);
            let current_rotation = mat4_rotation(current_transform);
            let current_position = mat4_translation(current_transform);
            let target_transform = if strength >= 1.0 {
                Mat4::from_rotation_translation(current_rotation, origin)
            } else {
                Mat4::from_rotation_translation(
                    current_rotation,
                    current_position.lerp(origin, strength),
                )
            };
            bones.set_global_transform(shoulder_idx, target_transform);
            mat4_translation(bones.get_global_transform(arm_idx))
        } else {
            origin
        }
    }

    fn body_anchor_index(&self) -> Option<usize> {
        self.cache
            .center
            .or(self.cache.groove)
            .or(self.cache.hips)
            .or(self.cache.root)
            .or(self.cache.upper_body)
            .or(self.cache.chest)
            .or(self.cache.upper_chest)
    }
}

#[inline]
fn mat4_translation(m: Mat4) -> Vec3 {
    m.w_axis.truncate()
}

#[inline]
fn mat4_rotation(m: Mat4) -> Quat {
    Quat::from_mat4(&m)
}

fn extract_yaw_rotation(rotation: Quat) -> Quat {
    let forward = rotation * Vec3::NEG_Z;
    let planar = Vec3::new(forward.x, 0.0, forward.z);
    if planar.length_squared() <= 1e-6 {
        Quat::IDENTITY
    } else {
        let yaw = planar.x.atan2(-planar.z);
        Quat::from_rotation_y(yaw)
    }
}

fn clamp_scalar(value: f32, limit: f32) -> f32 {
    if limit <= 1e-6 {
        value
    } else {
        value.clamp(-limit, limit)
    }
}

fn rotate_bone_direction(from: Vec3, old_to: Vec3, new_to: Vec3, current_rot: Quat) -> Quat {
    let old_dir = (old_to - from).normalize_or_zero();
    let new_dir = (new_to - from).normalize_or_zero();
    if old_dir.length_squared() < 1e-6 || new_dir.length_squared() < 1e-6 {
        return current_rot;
    }
    let delta = Quat::from_rotation_arc(old_dir, new_dir);
    (delta * current_rot).normalize()
}

fn find_first_existing(bones: &BoneManager, names: &[&str]) -> Option<usize> {
    names.iter().find_map(|name| bones.find_bone_by_name(name))
}

fn resolve_wrist_target(
    bones: &BoneManager,
    wrist_idx: usize,
    target: &VrTrackedPose,
    side: HandSide,
) -> WristTarget {
    let bind_pose = derive_hand_bind_pose(bones, wrist_idx, side);
    let rotation_offset = (bind_pose.rotation_offset * controller_mount_rotation(side)).normalize();
    WristTarget {
        position: target.position + target.rotation * bind_pose.position_offset,
        rotation: (target.rotation * rotation_offset).normalize(),
    }
}

fn controller_mount_rotation(side: HandSide) -> Quat {
    match side {
        HandSide::Left | HandSide::Right => Quat::from_rotation_y(std::f32::consts::PI),
    }
}

fn derive_hand_bind_pose(bones: &BoneManager, wrist_idx: usize, side: HandSide) -> HandBindPose {
    let wrist_pos = bones
        .get_bone(wrist_idx)
        .map(|bone| bone.initial_position)
        .unwrap_or(Vec3::ZERO);
    let finger_roots = hand_finger_root_positions(bones, side);
    let thumb_pos = hand_thumb_root_position(bones, side);
    let little_pos = hand_little_root_position(bones, side);

    let knuckle_center = average_points(&finger_roots).unwrap_or(wrist_pos + Vec3::Y);
    let finger_dir = normalize_with_fallback(knuckle_center - wrist_pos, Vec3::Y);
    let little_to_thumb =
        derive_little_to_thumb_axis(wrist_pos, thumb_pos, little_pos, finger_dir, side);
    let z_axis = (-little_to_thumb).normalize_or_zero();
    let x_axis = finger_dir.cross(z_axis).normalize_or_zero();
    let y_axis = z_axis.cross(x_axis).normalize_or_zero();

    if x_axis.length_squared() > 1e-6
        && y_axis.length_squared() > 1e-6
        && z_axis.length_squared() > 1e-6
    {
        let rotation_offset = Quat::from_mat3(&Mat3::from_cols(x_axis, y_axis, z_axis)).normalize();
        let palm_anchor = derive_palm_anchor(wrist_pos, knuckle_center, thumb_pos, little_pos);
        let wrist_from_palm = wrist_pos - palm_anchor;
        let position_offset =
            controller_mount_rotation(side).inverse() * rotation_offset.inverse() * wrist_from_palm;
        HandBindPose {
            rotation_offset,
            position_offset,
        }
    } else {
        fallback_hand_bind_pose(side)
    }
}

fn fallback_hand_bind_pose(side: HandSide) -> HandBindPose {
    let side_sign = side.side_sign();
    HandBindPose {
        rotation_offset: Quat::from_rotation_z(-side_sign * std::f32::consts::FRAC_PI_2),
        position_offset: Vec3::ZERO,
    }
}

fn hand_thumb_root_position(bones: &BoneManager, side: HandSide) -> Option<Vec3> {
    let names = match side {
        HandSide::Left => LEFT_THUMB_ROOT_NAMES,
        HandSide::Right => RIGHT_THUMB_ROOT_NAMES,
    };
    first_existing_initial_position(bones, names)
}

fn hand_little_root_position(bones: &BoneManager, side: HandSide) -> Option<Vec3> {
    let names = match side {
        HandSide::Left => LEFT_LITTLE_ROOT_NAMES,
        HandSide::Right => RIGHT_LITTLE_ROOT_NAMES,
    };
    first_existing_initial_position(bones, names)
}

fn hand_finger_root_positions(bones: &BoneManager, side: HandSide) -> Vec<Vec3> {
    let finger_sets = match side {
        HandSide::Left => [
            LEFT_INDEX_ROOT_NAMES,
            LEFT_MIDDLE_ROOT_NAMES,
            LEFT_RING_ROOT_NAMES,
            LEFT_LITTLE_ROOT_NAMES,
        ],
        HandSide::Right => [
            RIGHT_INDEX_ROOT_NAMES,
            RIGHT_MIDDLE_ROOT_NAMES,
            RIGHT_RING_ROOT_NAMES,
            RIGHT_LITTLE_ROOT_NAMES,
        ],
    };

    finger_sets
        .into_iter()
        .filter_map(|names| first_existing_initial_position(bones, names))
        .collect()
}

fn first_existing_initial_position(bones: &BoneManager, names: &[&str]) -> Option<Vec3> {
    names.iter().find_map(|name| {
        bones
            .find_bone_by_name(name)
            .and_then(|index| bones.get_bone(index))
            .map(|bone| bone.initial_position)
    })
}

fn average_points(points: &[Vec3]) -> Option<Vec3> {
    if points.is_empty() {
        None
    } else {
        Some(points.iter().copied().sum::<Vec3>() / points.len() as f32)
    }
}

fn normalize_with_fallback(value: Vec3, fallback: Vec3) -> Vec3 {
    let normalized = value.normalize_or_zero();
    if normalized.length_squared() > 1e-6 {
        normalized
    } else {
        fallback
    }
}

fn derive_little_to_thumb_axis(
    wrist_pos: Vec3,
    thumb_pos: Option<Vec3>,
    little_pos: Option<Vec3>,
    finger_dir: Vec3,
    side: HandSide,
) -> Vec3 {
    let raw = match (thumb_pos, little_pos) {
        (Some(thumb), Some(little)) => thumb - little,
        (Some(thumb), None) => thumb - wrist_pos,
        (None, Some(little)) => wrist_pos - little,
        (None, None) => Vec3::new(side.side_sign(), 0.0, 0.0),
    };

    let projected = raw - finger_dir * raw.dot(finger_dir);
    normalize_with_fallback(projected, Vec3::new(side.side_sign(), 0.0, 0.0))
}

fn derive_palm_anchor(
    wrist_pos: Vec3,
    knuckle_center: Vec3,
    thumb_pos: Option<Vec3>,
    little_pos: Option<Vec3>,
) -> Vec3 {
    let knuckle_anchor = wrist_pos.lerp(knuckle_center, 0.42);
    match (thumb_pos, little_pos) {
        (Some(thumb), Some(little)) => {
            let span_center = (thumb + little) * 0.5;
            knuckle_anchor.lerp(span_center, 0.35)
        }
        _ => knuckle_anchor,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::skeleton::{BoneLink, BoneManager};

    #[test]
    fn derive_hand_bind_pose_should_use_finger_axes_for_rotation() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let bind_pose = derive_hand_bind_pose(&bones, wrist_idx, HandSide::Right);

        let x_axis = bind_pose.rotation_offset * Vec3::X;
        let y_axis = bind_pose.rotation_offset * Vec3::Y;
        let z_axis = bind_pose.rotation_offset * Vec3::Z;
        assert_vec3_eq(y_axis, Vec3::new(0.0, 1.0, 0.0));
        assert_vec3_eq(z_axis, Vec3::new(-1.0, 0.0, 0.0));
        assert_vec3_eq(x_axis, Vec3::new(0.0, 0.0, 1.0));
    }

    #[test]
    fn resolve_wrist_target_should_apply_geometry_derived_bind_pose() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let target = VrTrackedPose {
            position: Vec3::new(4.0, 5.0, 6.0),
            rotation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
            valid: true,
        };

        let bind_pose = derive_hand_bind_pose(&bones, wrist_idx, HandSide::Right);
        let wrist_target = resolve_wrist_target(&bones, wrist_idx, &target, HandSide::Right);
        let expected_rotation = target.rotation
            * bind_pose.rotation_offset
            * controller_mount_rotation(HandSide::Right);
        let expected_position = target.position + target.rotation * bind_pose.position_offset;

        assert_vec3_eq(wrist_target.position, expected_position);
        assert_quat_eq(wrist_target.rotation, expected_rotation);
    }

    #[test]
    fn controller_mount_rotation_should_flip_palm_normal_without_changing_finger_axis() {
        let bind_pose = fallback_hand_bind_pose(HandSide::Right);
        let corrected =
            (bind_pose.rotation_offset * controller_mount_rotation(HandSide::Right)).normalize();

        let raw_finger = bind_pose.rotation_offset * Vec3::Y;
        let corrected_finger = corrected * Vec3::Y;
        let raw_palm = bind_pose.rotation_offset * Vec3::X;
        let corrected_palm = corrected * Vec3::X;

        assert_vec3_eq(corrected_finger, raw_finger);
        assert_vec3_eq(corrected_palm, -raw_palm);
    }

    #[test]
    fn fallback_bind_pose_should_still_define_hand_forward_rotation() {
        let bind_pose = fallback_hand_bind_pose(HandSide::Left);
        assert_vec3_eq(
            bind_pose.rotation_offset * Vec3::Y,
            Vec3::new(-1.0, 0.0, 0.0),
        );
        assert_vec3_eq(bind_pose.position_offset, Vec3::ZERO);
    }

    #[test]
    fn solve_tracking_frame_should_move_wrist_one_to_one_when_head_and_hands_translate_together() {
        let (_, calibration) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();
        let rest = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(4.8, 11.7, 1.8),
            calibration,
        );
        let delta = Vec3::new(1.25, 0.5, -0.75);
        let moved = make_tracking_frame(
            calibration.head_rest_anchor_model + delta,
            rest.left_palm.position + delta,
            rest.right_palm.position + delta,
            calibration,
        );

        let (mut rest_bones, _) = make_tracking_test_skeleton();
        let rest_debug = solver.solve_tracking_frame(&mut rest_bones, &rest, 1.0);
        let (mut moved_bones, _) = make_tracking_test_skeleton();
        let moved_debug = solver.solve_tracking_frame(&mut moved_bones, &moved, 1.0);

        assert_vec3_near(
            moved_debug.left_wrist_solved_model - rest_debug.left_wrist_solved_model,
            delta,
            0.2,
        );
        assert_vec3_near(
            moved_debug.right_wrist_solved_model - rest_debug.right_wrist_solved_model,
            delta,
            0.2,
        );
    }

    #[test]
    fn solve_tracking_frame_should_limit_head_only_hand_drift() {
        let (_, calibration) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();
        let rest = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(4.8, 11.7, 1.8),
            calibration,
        );
        let moved_head = make_tracking_frame(
            calibration.head_rest_anchor_model + Vec3::new(0.8, 0.3, -0.6),
            rest.left_palm.position,
            rest.right_palm.position,
            calibration,
        );

        let (mut rest_bones, _) = make_tracking_test_skeleton();
        let rest_debug = solver.solve_tracking_frame(&mut rest_bones, &rest, 1.0);
        let (mut moved_bones, _) = make_tracking_test_skeleton();
        let moved_debug = solver.solve_tracking_frame(&mut moved_bones, &moved_head, 1.0);

        let left_drift =
            (moved_debug.left_wrist_solved_model - rest_debug.left_wrist_solved_model).length();
        let right_drift =
            (moved_debug.right_wrist_solved_model - rest_debug.right_wrist_solved_model).length();

        assert!(
            left_drift <= 0.25,
            "left wrist drift too large: {left_drift:?}"
        );
        assert!(
            right_drift <= 0.25,
            "right wrist drift too large: {right_drift:?}"
        );
    }

    #[test]
    fn solve_tracking_frame_should_keep_reachable_controller_motion_close_to_one_to_one() {
        let (_, calibration) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();
        let rest = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(4.8, 11.7, 1.8),
            calibration,
        );
        let delta = Vec3::new(0.0, 0.0, -1.25);
        let moved_right = make_tracking_frame(
            calibration.head_rest_anchor_model,
            rest.left_palm.position,
            rest.right_palm.position + delta,
            calibration,
        );

        let (mut rest_bones, _) = make_tracking_test_skeleton();
        let rest_debug = solver.solve_tracking_frame(&mut rest_bones, &rest, 1.0);
        let (mut moved_bones, _) = make_tracking_test_skeleton();
        let moved_debug = solver.solve_tracking_frame(&mut moved_bones, &moved_right, 1.0);

        assert_vec3_near(
            moved_debug.right_wrist_solved_model - rest_debug.right_wrist_solved_model,
            delta,
            0.2,
        );
    }

    #[test]
    fn apply_body_tracking_should_translate_one_to_one_when_follow_is_full_and_unclamped() {
        let (mut rest_bones, calibration) = make_tracking_test_skeleton();
        let (mut moved_bones, _) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();

        solver.ensure_cache(&rest_bones);
        solver.ensure_cache(&moved_bones);

        let strict_calibration = BodyTrackingCalibration {
            horizontal_translation_follow_gain: 1.0,
            vertical_translation_follow_gain: 1.0,
            body_translation_clamp_model: -1.0,
            ..calibration
        };
        let rest_frame = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(4.8, 11.7, 1.8),
            strict_calibration,
        );
        let moved_head_delta = Vec3::new(1.1, 0.6, -0.9);
        let moved_frame = make_tracking_frame(
            calibration.head_rest_anchor_model + moved_head_delta,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(4.8, 11.7, 1.8),
            strict_calibration,
        );

        let rest_state = solver.apply_body_tracking(&mut rest_bones, &rest_frame, 1.0);
        let moved_state = solver.apply_body_tracking(&mut moved_bones, &moved_frame, 1.0);

        assert_vec3_near(
            moved_state.body_anchor_model - rest_state.body_anchor_model,
            moved_head_delta,
            1e-5,
        );
        assert_vec3_near(moved_state.head_residual_model, Vec3::ZERO, 1e-5);
    }

    fn make_test_hand_bind_pose(
        side: HandSide,
        thumb: Vec3,
        index: Vec3,
        middle: Vec3,
        ring: Vec3,
        little: Vec3,
    ) -> (BoneManager, usize) {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let wrist_name = match side {
            HandSide::Left => "左手首",
            HandSide::Right => "右手首",
        };
        let mut wrist = BoneLink::new(wrist_name.to_string());
        wrist.initial_position = Vec3::ZERO;
        wrist.parent_index = 0;
        bones.add_bone(wrist);

        let finger_names = match side {
            HandSide::Left => [
                LEFT_THUMB_ROOT_NAMES[0],
                LEFT_INDEX_ROOT_NAMES[0],
                LEFT_MIDDLE_ROOT_NAMES[0],
                LEFT_RING_ROOT_NAMES[0],
                LEFT_LITTLE_ROOT_NAMES[0],
            ],
            HandSide::Right => [
                RIGHT_THUMB_ROOT_NAMES[0],
                RIGHT_INDEX_ROOT_NAMES[0],
                RIGHT_MIDDLE_ROOT_NAMES[0],
                RIGHT_RING_ROOT_NAMES[0],
                RIGHT_LITTLE_ROOT_NAMES[0],
            ],
        };
        for (name, position) in finger_names
            .into_iter()
            .zip([thumb, index, middle, ring, little])
        {
            let mut bone = BoneLink::new(name.to_string());
            bone.initial_position = position;
            bone.parent_index = 1;
            bones.add_bone(bone);
        }

        bones.build_hierarchy();
        (bones, 1)
    }

    fn make_tracking_test_skeleton() -> (BoneManager, BodyTrackingCalibration) {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut upper_body = BoneLink::new("上半身".to_string());
        upper_body.initial_position = Vec3::new(0.0, 10.0, 0.0);
        upper_body.parent_index = 0;
        bones.add_bone(upper_body);

        let mut chest = BoneLink::new("上半身2".to_string());
        chest.initial_position = Vec3::new(0.0, 12.0, 0.0);
        chest.parent_index = 1;
        bones.add_bone(chest);

        let mut neck = BoneLink::new("首".to_string());
        neck.initial_position = Vec3::new(0.0, 15.0, 0.0);
        neck.parent_index = 2;
        bones.add_bone(neck);

        let mut head = BoneLink::new("頭".to_string());
        head.initial_position = Vec3::new(0.0, 17.0, 0.0);
        head.parent_index = 3;
        bones.add_bone(head);

        add_tracking_arm(&mut bones, HandSide::Left, 2, -1.0);
        add_tracking_arm(&mut bones, HandSide::Right, 2, 1.0);
        bones.build_hierarchy();

        (
            bones,
            BodyTrackingCalibration {
                head_rest_anchor_model: Vec3::new(0.0, 17.0, 0.0),
                shoulder_width_model: 4.0,
                shoulder_depth_model: 0.9,
                body_yaw_follow_gain: 0.65,
                horizontal_translation_follow_gain: 0.9,
                vertical_translation_follow_gain: 0.95,
                body_translation_clamp_model: 2.0,
                shoulder_follow_gain: 0.45,
            },
        )
    }

    fn add_tracking_arm(
        bones: &mut BoneManager,
        side: HandSide,
        parent_index: i32,
        side_sign: f32,
    ) {
        let shoulder_name = match side {
            HandSide::Left => "左肩",
            HandSide::Right => "右肩",
        };
        let arm_name = match side {
            HandSide::Left => "左腕",
            HandSide::Right => "右腕",
        };
        let elbow_name = match side {
            HandSide::Left => "左ひじ",
            HandSide::Right => "右ひじ",
        };
        let wrist_name = match side {
            HandSide::Left => "左手首",
            HandSide::Right => "右手首",
        };

        let mut shoulder = BoneLink::new(shoulder_name.to_string());
        shoulder.initial_position = Vec3::new(1.6 * side_sign, 12.5, 0.0);
        shoulder.parent_index = parent_index;
        bones.add_bone(shoulder);
        let shoulder_index = bones.bone_count() as i32 - 1;

        let mut arm = BoneLink::new(arm_name.to_string());
        arm.initial_position = Vec3::new(2.7 * side_sign, 12.4, 0.1);
        arm.parent_index = shoulder_index;
        bones.add_bone(arm);
        let arm_index = bones.bone_count() as i32 - 1;

        let mut elbow = BoneLink::new(elbow_name.to_string());
        elbow.initial_position = Vec3::new(3.9 * side_sign, 12.0, 0.7);
        elbow.parent_index = arm_index;
        bones.add_bone(elbow);
        let elbow_index = bones.bone_count() as i32 - 1;

        let mut wrist = BoneLink::new(wrist_name.to_string());
        wrist.initial_position = Vec3::new(4.9 * side_sign, 11.7, 1.7);
        wrist.parent_index = elbow_index;
        bones.add_bone(wrist);
        let wrist_index = bones.bone_count() as i32 - 1;

        let finger_names = match side {
            HandSide::Left => [
                LEFT_THUMB_ROOT_NAMES[0],
                LEFT_INDEX_ROOT_NAMES[0],
                LEFT_MIDDLE_ROOT_NAMES[0],
                LEFT_RING_ROOT_NAMES[0],
                LEFT_LITTLE_ROOT_NAMES[0],
            ],
            HandSide::Right => [
                RIGHT_THUMB_ROOT_NAMES[0],
                RIGHT_INDEX_ROOT_NAMES[0],
                RIGHT_MIDDLE_ROOT_NAMES[0],
                RIGHT_RING_ROOT_NAMES[0],
                RIGHT_LITTLE_ROOT_NAMES[0],
            ],
        };
        let finger_positions = [
            Vec3::new(4.5 * side_sign, 11.45, 1.95),
            Vec3::new(5.15 * side_sign, 11.95, 2.30),
            Vec3::new(4.95 * side_sign, 12.00, 2.40),
            Vec3::new(4.75 * side_sign, 11.95, 2.30),
            Vec3::new(4.55 * side_sign, 11.88, 2.18),
        ];

        for (name, position) in finger_names.into_iter().zip(finger_positions) {
            let mut finger = BoneLink::new(name.to_string());
            finger.initial_position = position;
            finger.parent_index = wrist_index;
            bones.add_bone(finger);
        }
    }

    fn make_tracking_frame(
        head_position: Vec3,
        left_palm: Vec3,
        right_palm: Vec3,
        calibration: BodyTrackingCalibration,
    ) -> VrTrackingFrame {
        VrTrackingFrame {
            head: VrTrackedPose {
                position: head_position,
                rotation: Quat::IDENTITY,
                valid: true,
            },
            left_palm: VrTrackedPose {
                position: left_palm,
                rotation: Quat::IDENTITY,
                valid: true,
            },
            right_palm: VrTrackedPose {
                position: right_palm,
                rotation: Quat::IDENTITY,
                valid: true,
            },
            body_calibration: calibration,
        }
    }

    fn assert_quat_eq(actual: Quat, expected: Quat) {
        let similarity = actual.dot(expected).abs();
        assert!(
            (1.0 - similarity) < 1e-5,
            "quat mismatch: actual={actual:?} expected={expected:?}",
        );
    }

    fn assert_vec3_eq(actual: Vec3, expected: Vec3) {
        let delta = actual - expected;
        assert!(
            delta.length() < 1e-5,
            "vec mismatch: actual={actual:?} expected={expected:?}",
        );
    }

    fn assert_vec3_near(actual: Vec3, expected: Vec3, tolerance: f32) {
        let delta = actual - expected;
        assert!(
            delta.length() <= tolerance,
            "vec mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }
}
