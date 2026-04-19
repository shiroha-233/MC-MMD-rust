//! VR IK solver.

use glam::{Mat3, Mat4, Quat, Vec3};

use crate::skeleton::BoneManager;
use crate::vrm_runtime::{ArmIkCalibration, BodyTrackingCalibration};

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
    pub(crate) arm_ik_calibration: ArmIkCalibration,
    pub(crate) body_calibration: BodyTrackingCalibration,
}

impl VrTrackingFrame {
    pub(crate) fn from_tracking_packet(
        tracking_data: &[f32; 21],
        arm_ik_calibration: ArmIkCalibration,
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
            arm_ik_calibration,
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
    pub(crate) left_auto_wrist_offset_model: Vec3,
    pub(crate) right_auto_wrist_offset_model: Vec3,
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
    auto_wrist_offset_model: Vec3,
}

#[derive(Clone, Copy, Debug)]
struct HandBindPose {
    rotation_offset: Quat,
    position_offset: Vec3,
    wrist_from_palm_model: Vec3,
}

#[derive(Clone, Copy, Debug, Default)]
struct BodySolveState {
    body_anchor_model: Vec3,
    head_residual_model: Vec3,
}

#[derive(Clone, Copy, Debug, Default)]
struct ArmSolveResult {
    auto_wrist_offset_model: Vec3,
    wrist_target_model: Vec3,
    solved_wrist_model: Vec3,
    wrist_error_cm: f32,
}

#[derive(Clone, Debug)]
struct LowerArmChain {
    indices: Vec<usize>,
    old_positions: Vec<Vec3>,
    segment_lengths: Vec<f32>,
    total_length: f32,
}

impl LowerArmChain {
    fn from_bones(bones: &BoneManager, elbow_idx: usize, wrist_idx: usize) -> Self {
        let indices = collect_parent_chain(bones, elbow_idx, wrist_idx)
            .filter(|chain| chain.len() >= 2)
            .unwrap_or_else(|| vec![elbow_idx, wrist_idx]);
        let old_positions = indices
            .iter()
            .map(|&index| mat4_translation(bones.get_global_transform(index)))
            .collect::<Vec<_>>();
        let segment_lengths = old_positions
            .windows(2)
            .map(|pair| (pair[1] - pair[0]).length())
            .collect::<Vec<_>>();
        let total_length = segment_lengths.iter().copied().sum();
        Self {
            indices,
            old_positions,
            segment_lengths,
            total_length,
        }
    }

    fn target_positions(&self, elbow_target: Vec3, wrist_target: Vec3) -> Vec<Vec3> {
        let direction = (wrist_target - elbow_target).normalize_or_zero();
        let mut positions = Vec::with_capacity(self.indices.len());
        positions.push(elbow_target);

        let mut traveled = 0.0;
        for length in &self.segment_lengths {
            traveled += *length;
            positions.push(elbow_target + direction * traveled);
        }
        if let Some(last) = positions.last_mut() {
            *last = wrist_target;
        }
        positions
    }

    fn segment_count(&self) -> usize {
        self.indices.len().saturating_sub(1)
    }
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
    left_zero_wrist_offset_warned: bool,
    right_zero_wrist_offset_warned: bool,
}

impl VrIkSolver {
    pub fn new() -> Self {
        Self {
            cache: BoneCache::default(),
            left_zero_wrist_offset_warned: false,
            right_zero_wrist_offset_warned: false,
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
        let arm_ik_calibration = ArmIkCalibration::default();
        let body_calibration = BodyTrackingCalibration::default();
        let frame = VrTrackingFrame::from_tracking_packet(
            tracking_data,
            arm_ik_calibration,
            body_calibration,
        );
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
            &frame.arm_ik_calibration,
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
            &frame.arm_ik_calibration,
            &frame.body_calibration,
            body_state.head_residual_model,
            strength,
            true,
        );

        // Re-run the standard before-physics transform pass so append/twist helper bones
        // can react to the updated arm/head/body animation values we just wrote.
        bones.update_transforms(false);

        let left_wrist_solved_model = self
            .cache
            .left_wrist
            .map(|index| mat4_translation(bones.get_global_transform(index)))
            .unwrap_or(left_result.solved_wrist_model);
        let right_wrist_solved_model = self
            .cache
            .right_wrist
            .map(|index| mat4_translation(bones.get_global_transform(index)))
            .unwrap_or(right_result.solved_wrist_model);

        VrDebugState {
            head_local_model: frame.head.position,
            body_anchor_model: self
                .body_anchor_index()
                .map(|index| mat4_translation(bones.get_global_transform(index)))
                .unwrap_or(body_state.body_anchor_model),
            left_palm_target_model: frame.left_palm.position,
            right_palm_target_model: frame.right_palm.position,
            left_auto_wrist_offset_model: left_result.auto_wrist_offset_model,
            right_auto_wrist_offset_model: right_result.auto_wrist_offset_model,
            left_wrist_solved_model,
            right_wrist_solved_model,
            left_wrist_error_cm: self
                .cache
                .left_wrist
                .map(|_| wrist_error_cm(left_wrist_solved_model, left_result.wrist_target_model))
                .unwrap_or(left_result.wrist_error_cm),
            right_wrist_error_cm: self
                .cache
                .right_wrist
                .map(|_| wrist_error_cm(right_wrist_solved_model, right_result.wrist_target_model))
                .unwrap_or(right_result.wrist_error_cm),
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
        let bind_position = bone_bind_position(bones, body_idx).unwrap_or(current_position);
        let bind_rotation = bone_bind_global_rotation(bones, body_idx);
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
        let body_target_position = bind_position + body_translation;
        let body_yaw = if frame.head.valid {
            extract_yaw_rotation(frame.head.rotation)
        } else {
            Quat::IDENTITY
        };
        let yaw_follow = Quat::IDENTITY.slerp(body_yaw, calibration.body_yaw_follow_gain);
        let body_target_rotation = (yaw_follow * bind_rotation).normalize();

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
        &mut self,
        bones: &mut BoneManager,
        shoulder_idx: Option<usize>,
        arm_idx: Option<usize>,
        elbow_idx: Option<usize>,
        wrist_idx: Option<usize>,
        target: &VrTrackedPose,
        arm_ik_calibration: &ArmIkCalibration,
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
        let lower_chain = LowerArmChain::from_bones(bones, elbow_idx, wrist_idx);
        let elbow_pos = lower_chain
            .old_positions
            .first()
            .copied()
            .unwrap_or_else(|| mat4_translation(bones.get_global_transform(elbow_idx)));
        let upper_len = (elbow_pos - arm_pos).length();
        let lower_len = lower_chain.total_length;
        if upper_len < 1e-4 || lower_len < 1e-4 {
            return ArmSolveResult::default();
        }

        let side = HandSide::from_left(is_left);
        let wrist_target = self.resolve_wrist_target(
            bones,
            wrist_idx,
            target,
            side,
            manual_wrist_offset(arm_ik_calibration, side),
            arm_ik_calibration.hand_face_flip,
            manual_wrist_rotation_offset(arm_ik_calibration, side),
        );
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
        let lower_chain_targets = lower_chain.target_positions(elbow_new, wrist_new);

        let solved_wrist_model = self.apply_arm_result(
            bones,
            arm_idx,
            arm_origin,
            elbow_new,
            &lower_chain,
            &lower_chain_targets,
            &wrist_target,
            arm_ik_calibration.forearm_twist_ratio.clamp(0.0, 1.0),
            strength,
        );

        ArmSolveResult {
            auto_wrist_offset_model: wrist_target.auto_wrist_offset_model,
            wrist_target_model: wrist_target.position,
            solved_wrist_model,
            wrist_error_cm: wrist_error_cm(solved_wrist_model, wrist_target.position),
        }
    }

    fn apply_arm_result(
        &self,
        bones: &mut BoneManager,
        arm_idx: usize,
        arm_origin: Vec3,
        new_elbow: Vec3,
        lower_chain: &LowerArmChain,
        lower_chain_targets: &[Vec3],
        wrist_target: &WristTarget,
        forearm_twist_ratio: f32,
        strength: f32,
    ) -> Vec3 {
        let arm_transform = bones.get_global_transform(arm_idx);
        let arm_rot = mat4_rotation(arm_transform);
        let arm_position = mat4_translation(arm_transform);
        let current_elbow = lower_chain
            .indices
            .first()
            .map(|&index| mat4_translation(bones.get_global_transform(index)))
            .unwrap_or(new_elbow);
        let new_arm_rot = rotate_bone_direction(arm_position, current_elbow, new_elbow, arm_rot);
        let lower_segment_count = lower_chain.segment_count().max(1) as f32;

        bones.set_global_transform(
            arm_idx,
            Mat4::from_rotation_translation(
                arm_rot.slerp(new_arm_rot, strength.min(1.0)),
                if strength >= 1.0 {
                    arm_origin
                } else {
                    arm_position.lerp(arm_origin, strength)
                },
            ),
        );

        for (segment_index, &bone_idx) in lower_chain.indices.iter().enumerate() {
            let current_transform = bones.get_global_transform(bone_idx);
            let current_position = mat4_translation(current_transform);
            let current_rotation = mat4_rotation(current_transform);
            let target_rotation = if segment_index + 1 < lower_chain.indices.len() {
                let child_idx = lower_chain.indices[segment_index + 1];
                let current_child_position =
                    mat4_translation(bones.get_global_transform(child_idx));
                let target_direction = (lower_chain_targets[segment_index + 1]
                    - lower_chain_targets[segment_index])
                    .normalize_or_zero();
                let current_segment_length = (current_child_position - current_position).length();
                let desired_child_position =
                    current_position + target_direction * current_segment_length;
                let solved_rotation = rotate_bone_direction(
                    current_position,
                    current_child_position,
                    desired_child_position,
                    current_rotation,
                );
                let forearm_axis_world =
                    (desired_child_position - current_position).normalize_or_zero();
                let twist_share = forearm_twist_ratio
                    * ((segment_index + 1) as f32 / lower_segment_count).clamp(0.0, 1.0);
                distribute_forearm_twist(
                    solved_rotation,
                    wrist_target.rotation,
                    forearm_axis_world,
                    twist_share,
                )
            } else {
                wrist_target.rotation
            };

            bones.set_global_transform(
                bone_idx,
                Mat4::from_rotation_translation(
                    current_rotation.slerp(target_rotation, strength.min(1.0)),
                    current_position,
                ),
            );
        }

        lower_chain
            .indices
            .last()
            .map(|&index| mat4_translation(bones.get_global_transform(index)))
            .unwrap_or(new_elbow)
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
        let fallback_origin = bone_bind_position(bones, arm_idx)
            .unwrap_or_else(|| mat4_translation(bones.get_global_transform(arm_idx)));
        let mut origin = shoulder_idx
            .and_then(|index| bone_bind_position(bones, index))
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

    fn resolve_wrist_target(
        &mut self,
        bones: &BoneManager,
        wrist_idx: usize,
        target: &VrTrackedPose,
        side: HandSide,
        manual_wrist_offset_model: Vec3,
        hand_face_flip: bool,
        manual_wrist_rotation_offset_model: Quat,
    ) -> WristTarget {
        let bind_pose = self.derive_hand_bind_pose(bones, wrist_idx, side);
        let mut rotation_offset =
            (bind_pose.rotation_offset * controller_mount_rotation(side)).normalize();
        if hand_face_flip {
            rotation_offset = (rotation_offset * Quat::from_rotation_y(std::f32::consts::PI))
                .normalize();
        }
        rotation_offset = (rotation_offset * manual_wrist_rotation_offset_model).normalize();
        WristTarget {
            position: target.position
                + target.rotation * bind_pose.position_offset
                + manual_wrist_offset_model,
            rotation: (target.rotation * rotation_offset).normalize(),
            auto_wrist_offset_model: bind_pose.wrist_from_palm_model,
        }
    }

    fn derive_hand_bind_pose(
        &mut self,
        bones: &BoneManager,
        wrist_idx: usize,
        side: HandSide,
    ) -> HandBindPose {
        if let Some(bind_pose) = derive_hand_bind_pose_from_finger_axes(bones, wrist_idx, side) {
            return bind_pose;
        }

        if let Some(bind_pose) = derive_hand_bind_pose_from_wrist_children(bones, wrist_idx, side) {
            return bind_pose;
        }

        self.warn_zero_wrist_offset_once(side);
        fallback_hand_bind_pose(side)
    }

    fn warn_zero_wrist_offset_once(&mut self, side: HandSide) {
        let warned = match side {
            HandSide::Left => &mut self.left_zero_wrist_offset_warned,
            HandSide::Right => &mut self.right_zero_wrist_offset_warned,
        };
        if *warned {
            return;
        }

        *warned = true;
        log::warn!(
            "VR IK could not derive a wrist offset for {:?} hand; falling back to zero palm-to-wrist offset",
            side
        );
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

fn bone_bind_position(bones: &BoneManager, index: usize) -> Option<Vec3> {
    bones.get_bone(index).map(|bone| bone.initial_position)
}

fn bone_bind_global_rotation(bones: &BoneManager, index: usize) -> Quat {
    bones
        .get_bone(index)
        .map(|bone| (bone.parent_rest_rotation * bone.rest_rotation).normalize())
        .unwrap_or(Quat::IDENTITY)
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

fn wrist_error_cm(actual_wrist_model: Vec3, wrist_target_model: Vec3) -> f32 {
    (actual_wrist_model - wrist_target_model).length() * MODEL_TO_WORLD_SCALE * 100.0
}

fn manual_wrist_offset(calibration: &ArmIkCalibration, side: HandSide) -> Vec3 {
    match side {
        HandSide::Left => calibration.left.wrist_offset_model,
        HandSide::Right => calibration.right.wrist_offset_model,
    }
}

fn manual_wrist_rotation_offset(calibration: &ArmIkCalibration, side: HandSide) -> Quat {
    match side {
        HandSide::Left => calibration.left.wrist_rotation_offset_model,
        HandSide::Right => calibration.right.wrist_rotation_offset_model,
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

fn distribute_forearm_twist(
    lower_arm_rotation: Quat,
    wrist_rotation: Quat,
    forearm_axis_world: Vec3,
    forearm_twist_ratio: f32,
) -> Quat {
    if forearm_twist_ratio <= 1e-6 || forearm_axis_world.length_squared() <= 1e-6 {
        return lower_arm_rotation;
    }

    let relative_to_wrist = (lower_arm_rotation.inverse() * wrist_rotation).normalize();
    let forearm_axis_local =
        (lower_arm_rotation.inverse() * forearm_axis_world).normalize_or_zero();
    if forearm_axis_local.length_squared() <= 1e-6 {
        return lower_arm_rotation;
    }

    let twist_local = extract_twist(relative_to_wrist, forearm_axis_local);
    let partial_twist_local =
        Quat::IDENTITY.slerp(twist_local, forearm_twist_ratio.clamp(0.0, 1.0));
    (lower_arm_rotation * partial_twist_local).normalize()
}

fn extract_twist(rotation: Quat, axis_local: Vec3) -> Quat {
    let axis_local = axis_local.normalize_or_zero();
    if axis_local.length_squared() <= 1e-6 {
        return Quat::IDENTITY;
    }

    let imag = Vec3::new(rotation.x, rotation.y, rotation.z);
    let projected = axis_local * imag.dot(axis_local);
    let twist = Quat::from_xyzw(projected.x, projected.y, projected.z, rotation.w);
    if twist.length_squared() <= 1e-6 {
        Quat::IDENTITY
    } else {
        twist.normalize()
    }
}

fn find_first_existing(bones: &BoneManager, names: &[&str]) -> Option<usize> {
    names.iter().find_map(|name| bones.find_bone_by_name(name))
}

fn controller_mount_rotation(side: HandSide) -> Quat {
    match side {
        HandSide::Left | HandSide::Right => Quat::from_rotation_y(std::f32::consts::PI),
    }
}

fn derive_hand_bind_pose_from_finger_axes(
    bones: &BoneManager,
    wrist_idx: usize,
    side: HandSide,
) -> Option<HandBindPose> {
    let wrist_pos = bones
        .get_bone(wrist_idx)
        .map(|bone| bone.initial_position)
        .unwrap_or(Vec3::ZERO);
    let finger_roots = hand_finger_root_positions(bones, side);
    if finger_roots.is_empty() {
        return None;
    }
    let thumb_pos = hand_thumb_root_position(bones, side);
    let little_pos = hand_little_root_position(bones, side);

    let knuckle_center = average_points(&finger_roots)?;
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
        Some(HandBindPose {
            rotation_offset,
            position_offset,
            wrist_from_palm_model: wrist_from_palm,
        })
    } else {
        None
    }
}

fn derive_hand_bind_pose_from_wrist_children(
    bones: &BoneManager,
    wrist_idx: usize,
    side: HandSide,
) -> Option<HandBindPose> {
    let wrist_pos = bones
        .get_bone(wrist_idx)
        .map(|bone| bone.initial_position)
        .unwrap_or(Vec3::ZERO);
    let child_positions = filtered_wrist_child_positions(bones, wrist_idx);
    let palm_anchor = average_points(&child_positions)?;
    let wrist_from_palm = wrist_pos - palm_anchor;
    let fallback = fallback_hand_bind_pose(side);
    let position_offset = controller_mount_rotation(side).inverse()
        * fallback.rotation_offset.inverse()
        * wrist_from_palm;

    Some(HandBindPose {
        position_offset,
        wrist_from_palm_model: wrist_from_palm,
        ..fallback
    })
}

fn filtered_wrist_child_positions(bones: &BoneManager, wrist_idx: usize) -> Vec<Vec3> {
    let mut preferred = Vec::new();
    let mut fallback = Vec::new();

    for &child_idx in bones.children_of(wrist_idx) {
        let Some(child) = bones.get_bone(child_idx) else {
            continue;
        };
        if should_ignore_wrist_child(child) {
            continue;
        }
        let offset = child.initial_position
            - bones
                .get_bone(wrist_idx)
                .map(|bone| bone.initial_position)
                .unwrap_or(Vec3::ZERO);
        if offset.length_squared() <= 1e-6 {
            continue;
        }

        fallback.push(child.initial_position);
        if looks_like_palm_child_name(&child.name) {
            preferred.push(child.initial_position);
        }
    }

    if preferred.is_empty() {
        fallback
    } else {
        preferred
    }
}

fn should_ignore_wrist_child(bone: &crate::skeleton::BoneLink) -> bool {
    if bone.append_config.is_some() || bone.is_append_rotate() || bone.is_append_translate() {
        return true;
    }

    let lower_name = bone.name.to_lowercase();
    [
        "捩",
        "捻",
        "twist",
        "helper",
        "dummy",
        "attachment",
        "attach",
        "slot",
        "rigid",
        "physics",
        "cloth",
        "skirt",
        "hair",
        "髪",
        "袖",
        "補助",
        "アクセ",
        "weapon",
        "武器",
    ]
    .iter()
    .any(|token| lower_name.contains(token))
}

fn looks_like_palm_child_name(name: &str) -> bool {
    let lower_name = name.to_lowercase();
    [
        "palm",
        "thumb",
        "index",
        "middle",
        "ring",
        "little",
        "finger",
        "metacarpal",
        "proximal",
        "親指",
        "人指",
        "中指",
        "薬指",
        "小指",
        "指",
        "掌",
    ]
    .iter()
    .any(|token| lower_name.contains(token))
}

fn collect_parent_chain(
    bones: &BoneManager,
    ancestor_idx: usize,
    descendant_idx: usize,
) -> Option<Vec<usize>> {
    let mut reversed = Vec::new();
    let mut current = descendant_idx;

    for _ in 0..bones.bone_count() {
        reversed.push(current);
        if current == ancestor_idx {
            reversed.reverse();
            return Some(reversed);
        }
        current = bones.get_bone(current)?.parent_id()?;
    }

    None
}

fn fallback_hand_bind_pose(side: HandSide) -> HandBindPose {
    let side_sign = side.side_sign();
    HandBindPose {
        rotation_offset: Quat::from_rotation_z(-side_sign * std::f32::consts::FRAC_PI_2),
        position_offset: Vec3::ZERO,
        wrist_from_palm_model: Vec3::ZERO,
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
    use crate::skeleton::{AppendConfig, BoneFlags, BoneLink, BoneManager};

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
        let bind_pose =
            derive_hand_bind_pose_from_finger_axes(&bones, wrist_idx, HandSide::Right).unwrap();

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

        let bind_pose =
            derive_hand_bind_pose_from_finger_axes(&bones, wrist_idx, HandSide::Right).unwrap();
        let mut solver = VrIkSolver::new();
        let wrist_target = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            Vec3::ZERO,
            false,
            Quat::IDENTITY,
        );
        let expected_rotation = target.rotation
            * bind_pose.rotation_offset
            * controller_mount_rotation(HandSide::Right);
        let expected_position = target.position + target.rotation * bind_pose.position_offset;

        assert_vec3_eq(wrist_target.position, expected_position);
        assert_quat_eq(wrist_target.rotation, expected_rotation);
        assert_vec3_eq(
            wrist_target.auto_wrist_offset_model,
            bind_pose.wrist_from_palm_model,
        );
    }

    #[test]
    fn resolve_wrist_target_should_add_manual_wrist_offset_after_geometry_offset() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let mut solver = VrIkSolver::new();
        let manual_offset = Vec3::new(0.25, -0.1, 0.05);
        let target = VrTrackedPose {
            position: Vec3::new(4.0, 5.0, 6.0),
            rotation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
            valid: true,
        };

        let bind_pose =
            derive_hand_bind_pose_from_finger_axes(&bones, wrist_idx, HandSide::Right).unwrap();
        let wrist_target = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            manual_offset,
            false,
            Quat::IDENTITY,
        );

        assert_vec3_eq(
            wrist_target.position,
            target.position + target.rotation * bind_pose.position_offset + manual_offset,
        );
        assert_vec3_eq(
            wrist_target.auto_wrist_offset_model,
            bind_pose.wrist_from_palm_model,
        );
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
    fn resolve_wrist_target_should_flip_palm_normal_after_bind_pose_when_requested() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let target = VrTrackedPose {
            position: Vec3::ZERO,
            rotation: Quat::from_rotation_z(std::f32::consts::FRAC_PI_4),
            valid: true,
        };
        let mut solver = VrIkSolver::new();
        let normal = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            Vec3::ZERO,
            false,
            Quat::IDENTITY,
        );
        let flipped = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            Vec3::ZERO,
            true,
            Quat::IDENTITY,
        );

        let normal_finger = normal.rotation * Vec3::Y;
        let flipped_finger = flipped.rotation * Vec3::Y;
        let normal_palm = normal.rotation * Vec3::X;
        let flipped_palm = flipped.rotation * Vec3::X;

        assert_vec3_eq(flipped_finger, normal_finger);
        assert_vec3_eq(flipped_palm, -normal_palm);
    }

    #[test]
    fn resolve_wrist_target_should_apply_manual_rotation_offset_after_bind_pose() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let target = VrTrackedPose {
            position: Vec3::ZERO,
            rotation: Quat::from_rotation_z(std::f32::consts::FRAC_PI_4),
            valid: true,
        };
        let adjust = Quat::from_rotation_z(std::f32::consts::FRAC_PI_2);
        let mut solver = VrIkSolver::new();
        let base = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            Vec3::ZERO,
            false,
            Quat::IDENTITY,
        );
        let adjusted = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &target,
            HandSide::Right,
            Vec3::ZERO,
            false,
            adjust,
        );

        assert_quat_eq(adjusted.rotation, (base.rotation * adjust).normalize());
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
    fn derive_hand_bind_pose_should_fallback_to_wrist_children_when_finger_roots_are_missing() {
        let (bones, wrist_idx, palm_a, palm_b) = make_test_wrist_children_pose(HandSide::Right);
        let bind_pose =
            derive_hand_bind_pose_from_wrist_children(&bones, wrist_idx, HandSide::Right).unwrap();
        let expected_anchor = (palm_a + palm_b) * 0.5;

        assert_vec3_eq(
            bind_pose.wrist_from_palm_model,
            Vec3::ZERO - expected_anchor,
        );
        assert!(
            bind_pose.position_offset.length_squared() > 1e-6,
            "expected non-zero fallback position offset"
        );
    }

    #[test]
    fn derive_hand_bind_pose_should_ignore_obvious_helper_wrist_children() {
        let (bones, wrist_idx, palm_a, palm_b) =
            make_test_wrist_children_pose_with_helper(HandSide::Right);
        let bind_pose =
            derive_hand_bind_pose_from_wrist_children(&bones, wrist_idx, HandSide::Right).unwrap();
        let expected_anchor = (palm_a + palm_b) * 0.5;

        assert_vec3_eq(
            bind_pose.wrist_from_palm_model,
            Vec3::ZERO - expected_anchor,
        );
    }

    #[test]
    fn distribute_forearm_twist_should_respect_ratio_bounds() {
        let lower_arm_rotation = Quat::IDENTITY;
        let wrist_rotation = Quat::from_rotation_y(0.8);
        let axis_world = Vec3::Y;

        let no_twist =
            distribute_forearm_twist(lower_arm_rotation, wrist_rotation, axis_world, 0.0);
        let full_twist =
            distribute_forearm_twist(lower_arm_rotation, wrist_rotation, axis_world, 1.0);

        assert_quat_eq(no_twist, lower_arm_rotation);
        assert_quat_eq(full_twist, wrist_rotation);
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

    #[test]
    fn solve_tracking_frame_should_refresh_append_twist_children_after_arm_ik() {
        let (mut bones, calibration, elbow_idx, append_idx) =
            make_tracking_test_skeleton_with_append_twist();
        let mut solver = VrIkSolver::new();
        let frame = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(6.2, 11.9, 0.5),
            calibration,
        );

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);

        let elbow_rotation = mat4_rotation(bones.get_global_transform(elbow_idx));
        let append_rotation = mat4_rotation(bones.get_global_transform(append_idx));
        let relative = (elbow_rotation.inverse() * append_rotation).normalize();
        let similarity = relative.dot(Quat::IDENTITY).abs();

        assert!(
            (1.0 - similarity) > 1e-3,
            "expected append twist child to receive a non-identity relative rotation after VR IK"
        );
    }

    #[test]
    fn solve_tracking_frame_should_distribute_real_lower_arm_chain_helpers() {
        let (mut bones, calibration, elbow_idx, helper_idx, wrist_idx, helper_ratio) =
            make_tracking_test_skeleton_with_lower_arm_helper();
        let mut solver = VrIkSolver::new();
        let frame = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(6.6, 11.7, 0.5),
            calibration,
        );

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);

        let elbow = mat4_translation(bones.get_global_transform(elbow_idx));
        let helper = mat4_translation(bones.get_global_transform(helper_idx));
        let wrist = mat4_translation(bones.get_global_transform(wrist_idx));
        let expected_helper = elbow.lerp(wrist, helper_ratio);

        assert_vec3_near(helper, expected_helper, 2e-4);
        assert!(
            (helper - elbow).length() > 1e-4,
            "expected real helper bone to move away from elbow after IK"
        );
    }

    #[test]
    fn solve_tracking_frame_should_preserve_lower_arm_local_translations_when_helper_chain_exists()
    {
        let (mut bones, calibration, elbow_idx, helper_idx, wrist_idx, _) =
            make_tracking_test_skeleton_with_lower_arm_helper();
        let mut solver = VrIkSolver::new();
        let frame = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(6.6, 11.7, 0.5),
            calibration,
        );
        let initial_wrist = mat4_translation(bones.get_global_transform(wrist_idx));
        let initial_local_translations = [elbow_idx, helper_idx, wrist_idx].map(|index| {
            bones
                .get_bone(index)
                .map(|bone| bone.animation_translate)
                .unwrap()
        });

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);

        for (bone_idx, expected_translation) in [elbow_idx, helper_idx, wrist_idx]
            .into_iter()
            .zip(initial_local_translations)
        {
            let actual_translation = bones
                .get_bone(bone_idx)
                .map(|bone| bone.animation_translate)
                .unwrap();
            assert_vec3_near(actual_translation, expected_translation, 1e-5);
        }

        let solved_wrist = mat4_translation(bones.get_global_transform(wrist_idx));
        assert!(
            (solved_wrist - initial_wrist).length() > 1e-2,
            "expected wrist to move even though local translations stay unchanged"
        );
    }

    #[test]
    fn solve_tracking_frame_should_report_actual_wrist_error_after_rotation_only_update() {
        let (mut bones, calibration, _, _, wrist_idx, _) =
            make_tracking_test_skeleton_with_lower_arm_helper();
        let mut solver = VrIkSolver::new();
        let frame = make_tracking_frame(
            calibration.head_rest_anchor_model,
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(6.6, 11.7, 0.5),
            calibration,
        );
        let wrist_target = solver.resolve_wrist_target(
            &bones,
            wrist_idx,
            &frame.right_palm,
            HandSide::Right,
            Vec3::ZERO,
            false,
            Quat::IDENTITY,
        );

        let debug = solver.solve_tracking_frame(&mut bones, &frame, 1.0);
        let expected_error = wrist_error_cm(debug.right_wrist_solved_model, wrist_target.position);

        assert!(
            (debug.right_wrist_error_cm - expected_error).abs() <= 1e-4,
            "expected reported wrist error to match actual solved wrist position"
        );
    }

    #[test]
    fn solve_tracking_frame_should_not_accumulate_body_anchor_translation_for_identical_frames() {
        let (mut bones, calibration) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();
        let frame = make_tracking_frame(
            calibration.head_rest_anchor_model + Vec3::new(1.25, 0.8, -0.65),
            Vec3::new(-4.8, 11.7, 1.8),
            Vec3::new(6.6, 11.7, 0.5),
            calibration,
        );

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);
        let body_idx = solver.body_anchor_index().expect("body anchor");
        let first_position = mat4_translation(bones.get_global_transform(body_idx));

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);
        let second_position = mat4_translation(bones.get_global_transform(body_idx));

        assert_vec3_near(second_position, first_position, 1e-5);
    }

    #[test]
    fn solve_tracking_frame_should_not_accumulate_body_yaw_for_identical_frames() {
        let (mut bones, calibration) = make_tracking_test_skeleton();
        let mut solver = VrIkSolver::new();
        let frame = VrTrackingFrame {
            head: VrTrackedPose {
                position: calibration.head_rest_anchor_model,
                rotation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
                valid: true,
            },
            left_palm: VrTrackedPose {
                position: Vec3::new(-4.8, 11.7, 1.8),
                rotation: Quat::IDENTITY,
                valid: true,
            },
            right_palm: VrTrackedPose {
                position: Vec3::new(6.6, 11.7, 0.5),
                rotation: Quat::IDENTITY,
                valid: true,
            },
            arm_ik_calibration: ArmIkCalibration::default(),
            body_calibration: BodyTrackingCalibration {
                body_yaw_follow_gain: 1.0,
                ..calibration
            },
        };

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);
        let body_idx = solver.body_anchor_index().expect("body anchor");
        let first_rotation = mat4_rotation(bones.get_global_transform(body_idx));

        solver.solve_tracking_frame(&mut bones, &frame, 1.0);
        let second_rotation = mat4_rotation(bones.get_global_transform(body_idx));

        assert_quat_eq(second_rotation, first_rotation);
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

    fn make_test_wrist_children_pose(side: HandSide) -> (BoneManager, usize, Vec3, Vec3) {
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

        let palm_a = Vec3::new(0.2, 0.9, 0.15);
        let palm_b = Vec3::new(-0.2, 0.85, 0.2);
        for (index, position) in [palm_a, palm_b].into_iter().enumerate() {
            let mut child = BoneLink::new(format!("palm_child_{index}"));
            child.initial_position = position;
            child.parent_index = 1;
            bones.add_bone(child);
        }

        bones.build_hierarchy();
        (bones, 1, palm_a, palm_b)
    }

    fn make_test_wrist_children_pose_with_helper(
        side: HandSide,
    ) -> (BoneManager, usize, Vec3, Vec3) {
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

        let helper = Vec3::new(0.0, 0.1, -0.7);
        let palm_a = Vec3::new(0.2, 0.9, 0.15);
        let palm_b = Vec3::new(-0.2, 0.85, 0.2);
        let helper_name = match side {
            HandSide::Left => "左手捩",
            HandSide::Right => "右手捩",
        };
        for (name, position) in [
            (helper_name.to_string(), helper),
            ("palm_child_0".to_string(), palm_a),
            ("rightIndexProximal".to_string(), palm_b),
        ] {
            let mut child = BoneLink::new(name);
            child.initial_position = position;
            child.parent_index = 1;
            bones.add_bone(child);
        }

        bones.build_hierarchy();
        (bones, 1, palm_a, palm_b)
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

    fn make_tracking_test_skeleton_with_append_twist(
    ) -> (BoneManager, BodyTrackingCalibration, usize, usize) {
        let (mut bones, calibration) = make_tracking_test_skeleton();
        let elbow_idx = bones.find_bone_by_name("右ひじ").expect("right elbow");

        let mut append_bone = BoneLink::new("右手捩".to_string());
        append_bone.initial_position = Vec3::new(4.35, 11.85, 1.25);
        append_bone.parent_index = elbow_idx as i32;
        append_bone.flags = BoneFlags::ROTATABLE | BoneFlags::APPEND_ROTATE;
        append_bone.append_config = Some(AppendConfig {
            parent: elbow_idx as i32,
            rate: 0.5,
        });
        bones.add_bone(append_bone);
        bones.build_hierarchy();

        let append_idx = bones
            .find_bone_by_name("右手捩")
            .expect("append twist bone");
        (bones, calibration, elbow_idx, append_idx)
    }

    fn make_tracking_test_skeleton_with_lower_arm_helper() -> (
        BoneManager,
        BodyTrackingCalibration,
        usize,
        usize,
        usize,
        f32,
    ) {
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
        add_tracking_arm_with_lower_arm_helper(&mut bones, 2, 1.0);
        bones.build_hierarchy();

        let elbow_idx = bones.find_bone_by_name("右ひじ").expect("right elbow");
        let helper_idx = bones.find_bone_by_name("右手捩").expect("right helper");
        let wrist_idx = bones.find_bone_by_name("右手首").expect("right wrist");
        let elbow_pos = bones
            .get_bone(elbow_idx)
            .map(|bone| bone.initial_position)
            .unwrap();
        let helper_pos = bones
            .get_bone(helper_idx)
            .map(|bone| bone.initial_position)
            .unwrap();
        let wrist_pos = bones
            .get_bone(wrist_idx)
            .map(|bone| bone.initial_position)
            .unwrap();
        let helper_ratio = (helper_pos - elbow_pos).length() / (wrist_pos - elbow_pos).length();

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
            elbow_idx,
            helper_idx,
            wrist_idx,
            helper_ratio,
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

    fn add_tracking_arm_with_lower_arm_helper(
        bones: &mut BoneManager,
        parent_index: i32,
        side_sign: f32,
    ) {
        let mut shoulder = BoneLink::new("右肩".to_string());
        shoulder.initial_position = Vec3::new(1.6 * side_sign, 12.5, 0.0);
        shoulder.parent_index = parent_index;
        bones.add_bone(shoulder);
        let shoulder_index = bones.bone_count() as i32 - 1;

        let mut arm = BoneLink::new("右腕".to_string());
        arm.initial_position = Vec3::new(2.7 * side_sign, 12.4, 0.1);
        arm.parent_index = shoulder_index;
        bones.add_bone(arm);
        let arm_index = bones.bone_count() as i32 - 1;

        let mut elbow = BoneLink::new("右ひじ".to_string());
        elbow.initial_position = Vec3::new(3.9 * side_sign, 12.0, 0.7);
        elbow.parent_index = arm_index;
        bones.add_bone(elbow);
        let elbow_index = bones.bone_count() as i32 - 1;

        let mut helper = BoneLink::new("右手捩".to_string());
        helper.initial_position = Vec3::new(4.35 * side_sign, 11.85, 1.15);
        helper.parent_index = elbow_index;
        bones.add_bone(helper);
        let helper_index = bones.bone_count() as i32 - 1;

        let mut wrist = BoneLink::new("右手首".to_string());
        wrist.initial_position = Vec3::new(4.9 * side_sign, 11.7, 1.7);
        wrist.parent_index = helper_index;
        bones.add_bone(wrist);
        let wrist_index = bones.bone_count() as i32 - 1;

        for (name, position) in [
            (
                RIGHT_THUMB_ROOT_NAMES[0],
                Vec3::new(4.5 * side_sign, 11.45, 1.95),
            ),
            (
                RIGHT_INDEX_ROOT_NAMES[0],
                Vec3::new(5.15 * side_sign, 11.95, 2.30),
            ),
            (
                RIGHT_MIDDLE_ROOT_NAMES[0],
                Vec3::new(4.95 * side_sign, 12.00, 2.40),
            ),
            (
                RIGHT_RING_ROOT_NAMES[0],
                Vec3::new(4.75 * side_sign, 11.95, 2.30),
            ),
            (
                RIGHT_LITTLE_ROOT_NAMES[0],
                Vec3::new(4.55 * side_sign, 11.88, 2.18),
            ),
        ] {
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
            arm_ik_calibration: ArmIkCalibration::default(),
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
