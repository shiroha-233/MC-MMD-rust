//! VR IK solver.

use glam::{Mat3, Mat4, Quat, Vec3};

use crate::skeleton::BoneManager;

const TRACK_POINT_SIZE: usize = 7;
const BONE_HEAD_NAMES: &[&str] = &["頭", "head", "Head"];
const BONE_NECK_NAMES: &[&str] = &["首", "neck", "Neck"];
const BONE_UPPER_BODY_NAMES: &[&str] = &["上半身", "spine", "Spine", "chest", "Chest"];
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
const LEFT_INDEX_ROOT_NAMES: &[&str] = &[
    "左人指１",
    "leftIndexProximal",
    "leftIndex1",
];
const RIGHT_INDEX_ROOT_NAMES: &[&str] = &[
    "右人指１",
    "rightIndexProximal",
    "rightIndex1",
];
const LEFT_MIDDLE_ROOT_NAMES: &[&str] = &[
    "左中指１",
    "leftMiddleProximal",
    "leftMiddle1",
];
const RIGHT_MIDDLE_ROOT_NAMES: &[&str] = &[
    "右中指１",
    "rightMiddleProximal",
    "rightMiddle1",
];
const LEFT_RING_ROOT_NAMES: &[&str] = &[
    "左薬指１",
    "leftRingProximal",
    "leftRing1",
];
const RIGHT_RING_ROOT_NAMES: &[&str] = &[
    "右薬指１",
    "rightRingProximal",
    "rightRing1",
];
const LEFT_LITTLE_ROOT_NAMES: &[&str] = &[
    "左小指１",
    "leftLittleProximal",
    "leftLittle1",
];
const RIGHT_LITTLE_ROOT_NAMES: &[&str] = &[
    "右小指１",
    "rightLittleProximal",
    "rightLittle1",
];

#[derive(Clone, Copy, Debug)]
struct TrackPoint {
    position: Vec3,
    rotation: Quat,
}

impl TrackPoint {
    fn from_slice(data: &[f32]) -> Self {
        Self {
            position: Vec3::new(data[0], data[1], data[2]),
            rotation: Quat::from_xyzw(data[3], data[4], data[5], data[6]).normalize(),
        }
    }
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

struct BoneCache {
    head: Option<usize>,
    neck: Option<usize>,
    upper_body: Option<usize>,
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
            head: None,
            neck: None,
            upper_body: None,
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
        self.cache.head = find_first_existing(bones, BONE_HEAD_NAMES);
        self.cache.neck = find_first_existing(bones, BONE_NECK_NAMES);
        self.cache.upper_body = find_first_existing(bones, BONE_UPPER_BODY_NAMES);
        self.cache.left_arm = find_first_existing(bones, BONE_LEFT_ARM_NAMES);
        self.cache.left_elbow = find_first_existing(bones, BONE_LEFT_ELBOW_NAMES);
        self.cache.left_wrist = find_first_existing(bones, BONE_LEFT_WRIST_NAMES);
        self.cache.right_arm = find_first_existing(bones, BONE_RIGHT_ARM_NAMES);
        self.cache.right_elbow = find_first_existing(bones, BONE_RIGHT_ELBOW_NAMES);
        self.cache.right_wrist = find_first_existing(bones, BONE_RIGHT_WRIST_NAMES);

        log::info!(
            "VR IK bone cache: head={:?} neck={:?} L_arm={:?} L_elbow={:?} L_wrist={:?} R_arm={:?} R_elbow={:?} R_wrist={:?}",
            self.cache.head,
            self.cache.neck,
            self.cache.left_arm,
            self.cache.left_elbow,
            self.cache.left_wrist,
            self.cache.right_arm,
            self.cache.right_elbow,
            self.cache.right_wrist,
        );
    }

    pub fn solve(&mut self, bones: &mut BoneManager, tracking_data: &[f32; 21], strength: f32) {
        if strength <= 0.0 {
            return;
        }
        self.ensure_cache(bones);

        let head = TrackPoint::from_slice(&tracking_data[0..TRACK_POINT_SIZE]);
        let main_hand =
            TrackPoint::from_slice(&tracking_data[TRACK_POINT_SIZE..TRACK_POINT_SIZE * 2]);
        let off_hand =
            TrackPoint::from_slice(&tracking_data[TRACK_POINT_SIZE * 2..TRACK_POINT_SIZE * 3]);

        self.apply_head_tracking(bones, &head, strength);
        self.solve_arm_ik(
            bones,
            self.cache.right_arm,
            self.cache.right_elbow,
            self.cache.right_wrist,
            &main_hand,
            strength,
            false,
        );
        self.solve_arm_ik(
            bones,
            self.cache.left_arm,
            self.cache.left_elbow,
            self.cache.left_wrist,
            &off_hand,
            strength,
            true,
        );
    }

    fn apply_head_tracking(&self, bones: &mut BoneManager, head: &TrackPoint, strength: f32) {
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

    fn solve_arm_ik(
        &self,
        bones: &mut BoneManager,
        arm_idx: Option<usize>,
        elbow_idx: Option<usize>,
        wrist_idx: Option<usize>,
        target: &TrackPoint,
        strength: f32,
        is_left: bool,
    ) {
        let (arm_idx, elbow_idx, wrist_idx) = match (arm_idx, elbow_idx, wrist_idx) {
            (Some(arm), Some(elbow), Some(wrist)) => (arm, elbow, wrist),
            _ => return,
        };

        let arm_pos = mat4_translation(bones.get_global_transform(arm_idx));
        let elbow_pos = mat4_translation(bones.get_global_transform(elbow_idx));
        let wrist_pos = mat4_translation(bones.get_global_transform(wrist_idx));

        let upper_len = (elbow_pos - arm_pos).length();
        let lower_len = (wrist_pos - elbow_pos).length();
        if upper_len < 1e-4 || lower_len < 1e-4 {
            return;
        }

        let side = HandSide::from_left(is_left);
        let wrist_target = resolve_wrist_target(bones, wrist_idx, target, side, lower_len);
        let target_pos = wrist_target.position;
        let to_target = target_pos - arm_pos;
        let chain_len = upper_len + lower_len;
        let target_dist = to_target.length().min(chain_len * 0.999);
        if target_dist < 1e-4 {
            return;
        }

        let forward = to_target / to_target.length();
        let elbow_offset = elbow_pos - arm_pos;
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

        let elbow_new = arm_pos
            + forward * (angle_a.cos() * upper_len)
            + hint_dir * (angle_a.sin() * upper_len);
        let elbow_to_target = (target_pos - elbow_new).normalize_or_zero();
        let wrist_new = elbow_new + elbow_to_target * lower_len;

        self.apply_arm_result(
            bones,
            arm_idx,
            elbow_idx,
            wrist_idx,
            arm_pos,
            elbow_pos,
            wrist_pos,
            elbow_new,
            wrist_new,
            &wrist_target,
            strength,
        );
    }

    fn apply_arm_result(
        &self,
        bones: &mut BoneManager,
        arm_idx: usize,
        elbow_idx: usize,
        wrist_idx: usize,
        arm_pos: Vec3,
        old_elbow: Vec3,
        old_wrist: Vec3,
        new_elbow: Vec3,
        new_wrist: Vec3,
        wrist_target: &WristTarget,
        strength: f32,
    ) {
        let arm_rot = mat4_rotation(bones.get_global_transform(arm_idx));
        let new_arm_rot = rotate_bone_direction(arm_pos, old_elbow, new_elbow, arm_rot);

        let elbow_rot = mat4_rotation(bones.get_global_transform(elbow_idx));
        let new_elbow_rot = rotate_bone_direction(old_elbow, old_wrist, new_wrist, elbow_rot);

        if strength >= 1.0 {
            bones.set_global_transform(
                arm_idx,
                Mat4::from_rotation_translation(new_arm_rot, arm_pos),
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

            bones.set_global_transform(arm_idx, blend(arm_rot, new_arm_rot, arm_pos, arm_pos));
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
}

#[inline]
fn mat4_translation(m: Mat4) -> Vec3 {
    m.w_axis.truncate()
}

#[inline]
fn mat4_rotation(m: Mat4) -> Quat {
    Quat::from_mat4(&m)
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
    target: &TrackPoint,
    side: HandSide,
    lower_len: f32,
) -> WristTarget {
    let bind_pose = derive_hand_bind_pose(bones, wrist_idx, side, lower_len);
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

fn derive_hand_bind_pose(
    bones: &BoneManager,
    wrist_idx: usize,
    side: HandSide,
    lower_len: f32,
) -> HandBindPose {
    let wrist_pos = bones
        .get_bone(wrist_idx)
        .map(|bone| bone.initial_position)
        .unwrap_or(Vec3::ZERO);
    let finger_roots = hand_finger_root_positions(bones, side);
    let thumb_pos = hand_thumb_root_position(bones, side);
    let little_pos = hand_little_root_position(bones, side);

    let knuckle_center = average_points(&finger_roots).unwrap_or_else(|| {
        let fallback = wrist_pos + Vec3::Y * fallback_grip_depth(bones, wrist_idx);
        fallback
    });
    let finger_dir = normalize_with_fallback(
        knuckle_center - wrist_pos,
        Vec3::Y,
    );
    let little_to_thumb = derive_little_to_thumb_axis(
        wrist_pos,
        thumb_pos,
        little_pos,
        finger_dir,
        side,
    );
    let z_axis = (-little_to_thumb).normalize_or_zero();
    let x_axis = finger_dir.cross(z_axis).normalize_or_zero();
    let y_axis = z_axis.cross(x_axis).normalize_or_zero();

    if x_axis.length_squared() > 1e-6
        && y_axis.length_squared() > 1e-6
        && z_axis.length_squared() > 1e-6
    {
        let rotation_offset =
            Quat::from_mat3(&Mat3::from_cols(x_axis, y_axis, z_axis)).normalize();
        let palm_anchor = derive_palm_anchor(wrist_pos, knuckle_center, thumb_pos, little_pos);
        let position_offset = rotation_offset.inverse() * (wrist_pos - palm_anchor);
        HandBindPose {
            rotation_offset,
            position_offset,
        }
    } else {
        fallback_hand_bind_pose(side, lower_len)
    }
}

fn fallback_hand_bind_pose(side: HandSide, lower_len: f32) -> HandBindPose {
    let side_sign = side.side_sign();
    HandBindPose {
        rotation_offset: Quat::from_rotation_z(-side_sign * std::f32::consts::FRAC_PI_2),
        position_offset: Vec3::new(
            0.0,
            -lower_len * 0.42,
            side_sign * lower_len * 0.06,
        ),
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

fn fallback_grip_depth(bones: &BoneManager, wrist_idx: usize) -> f32 {
    bones
        .get_bone(wrist_idx)
        .map(|bone| bone.body_shift.length().max(1.0))
        .unwrap_or(1.0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::skeleton::{BoneLink, BoneManager};

    #[test]
    fn derive_hand_bind_pose_should_use_finger_axes_for_rotation_and_position() {
        let (bones, wrist_idx) = make_test_hand_bind_pose(
            HandSide::Right,
            Vec3::new(0.55, 0.8, 0.0),
            Vec3::new(0.3, 1.0, 0.0),
            Vec3::new(0.1, 1.0, 0.0),
            Vec3::new(-0.1, 1.0, 0.0),
            Vec3::new(-0.3, 1.0, 0.0),
        );
        let bind_pose = derive_hand_bind_pose(&bones, wrist_idx, HandSide::Right, 2.0);

        let x_axis = bind_pose.rotation_offset * Vec3::X;
        let y_axis = bind_pose.rotation_offset * Vec3::Y;
        let z_axis = bind_pose.rotation_offset * Vec3::Z;
        assert_vec3_eq(y_axis, Vec3::new(0.0, 1.0, 0.0));
        assert_vec3_eq(z_axis, Vec3::new(-1.0, 0.0, 0.0));
        assert_vec3_eq(x_axis, Vec3::new(0.0, 0.0, 1.0));
        assert!(bind_pose.position_offset.y < -0.3);
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
        let target = TrackPoint {
            position: Vec3::new(4.0, 5.0, 6.0),
            rotation: Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
        };

        let bind_pose = derive_hand_bind_pose(&bones, wrist_idx, HandSide::Right, 2.0);
        let wrist_target = resolve_wrist_target(&bones, wrist_idx, &target, HandSide::Right, 2.0);
        let expected_rotation =
            target.rotation * bind_pose.rotation_offset * controller_mount_rotation(HandSide::Right);

        assert_vec3_eq(
            wrist_target.position,
            target.position + target.rotation * bind_pose.position_offset,
        );
        assert_quat_eq(wrist_target.rotation, expected_rotation);
    }

    #[test]
    fn controller_mount_rotation_should_flip_palm_normal_without_changing_finger_axis() {
        let bind_pose = fallback_hand_bind_pose(HandSide::Right, 2.0);
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
    fn fallback_bind_pose_should_use_spec_space_wrist_offset() {
        let bind_pose = fallback_hand_bind_pose(HandSide::Left, 2.0);
        assert!(bind_pose.position_offset.y < -0.7);
        assert!(bind_pose.position_offset.z < 0.0);
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
}
