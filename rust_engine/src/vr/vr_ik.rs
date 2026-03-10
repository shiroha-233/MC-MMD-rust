//! VR IK 求解器

use glam::{Mat4, Quat, Vec3};
use crate::skeleton::BoneManager;

const TRACK_POINT_SIZE: usize = 7;

// MMD 标准骨骼名：「腕」=上臂, 「ひじ」=肘, 「手首」=手腕
const BONE_HEAD: &str = "頭";
const BONE_NECK: &str = "首";
const BONE_UPPER_BODY: &str = "上半身";
const BONE_LEFT_ARM: &str = "左腕";
const BONE_LEFT_ELBOW: &str = "左ひじ";
const BONE_LEFT_WRIST: &str = "左手首";
const BONE_RIGHT_ARM: &str = "右腕";
const BONE_RIGHT_ELBOW: &str = "右ひじ";
const BONE_RIGHT_WRIST: &str = "右手首";

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

/// 缓存骨骼索引
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
            head: None, neck: None, upper_body: None,
            left_arm: None, left_elbow: None, left_wrist: None,
            right_arm: None, right_elbow: None, right_wrist: None,
            initialized: false,
        }
    }
}

pub struct VrIkSolver {
    cache: BoneCache,
}

impl VrIkSolver {
    pub fn new() -> Self {
        Self { cache: BoneCache::default() }
    }

    fn ensure_cache(&mut self, bones: &BoneManager) {
        if self.cache.initialized { return; }
        self.cache.initialized = true;
        self.cache.head = bones.find_bone_by_name(BONE_HEAD);
        self.cache.neck = bones.find_bone_by_name(BONE_NECK);
        self.cache.upper_body = bones.find_bone_by_name(BONE_UPPER_BODY);

        // MMD 骨骼命名：「腕」= upper arm, 「ひじ」= elbow, 「手首」= wrist
        self.cache.left_arm = bones.find_bone_by_name(BONE_LEFT_ARM);
        self.cache.left_elbow = bones.find_bone_by_name(BONE_LEFT_ELBOW);
        self.cache.left_wrist = bones.find_bone_by_name(BONE_LEFT_WRIST);
        self.cache.right_arm = bones.find_bone_by_name(BONE_RIGHT_ARM);
        self.cache.right_elbow = bones.find_bone_by_name(BONE_RIGHT_ELBOW);
        self.cache.right_wrist = bones.find_bone_by_name(BONE_RIGHT_WRIST);

        log::info!(
            "VR IK 骨骼缓存: head={:?} neck={:?} L_arm={:?} L_elbow={:?} L_wrist={:?} R_arm={:?} R_elbow={:?} R_wrist={:?}",
            self.cache.head, self.cache.neck,
            self.cache.left_arm, self.cache.left_elbow, self.cache.left_wrist,
            self.cache.right_arm, self.cache.right_elbow, self.cache.right_wrist,
        );
    }

    /// 在 update_node_animation(false) 之后调用
    pub fn solve(&mut self, bones: &mut BoneManager, tracking_data: &[f32; 21], strength: f32) {
        if strength <= 0.0 { return; }
        self.ensure_cache(bones);

        let head = TrackPoint::from_slice(&tracking_data[0..TRACK_POINT_SIZE]);
        let main_hand = TrackPoint::from_slice(&tracking_data[TRACK_POINT_SIZE..TRACK_POINT_SIZE * 2]);
        let off_hand = TrackPoint::from_slice(&tracking_data[TRACK_POINT_SIZE * 2..TRACK_POINT_SIZE * 3]);

        // 头部追踪（全局旋转 → 局部旋转）
        self.apply_head_tracking(bones, &head, strength);

        // 右手臂 IK（主手控制器）
        self.solve_arm_ik(
            bones,
            self.cache.right_arm, self.cache.right_elbow, self.cache.right_wrist,
            &main_hand, strength, false,
        );

        // 左手臂 IK（副手控制器）
        self.solve_arm_ik(
            bones,
            self.cache.left_arm, self.cache.left_elbow, self.cache.left_wrist,
            &off_hand, strength, true,
        );
    }

    /// 头部追踪：将模型空间全局旋转转换为相对于父骨骼的局部旋转
    fn apply_head_tracking(&self, bones: &mut BoneManager, head: &TrackPoint, strength: f32) {
        let head_idx = match self.cache.head {
            Some(idx) => idx,
            None => return,
        };

        // 获取父骨骼（首/neck）的全局旋转
        let parent_global_rot = self.cache.neck
            .map(|idx| mat4_rotation(bones.get_global_transform(idx)))
            .unwrap_or(Quat::IDENTITY);

        // 模型空间全局旋转 → 相对于父骨骼的局部旋转
        let local_rot = parent_global_rot.inverse() * head.rotation;

        if strength >= 1.0 {
            bones.set_bone_rotation(head_idx, local_rot);
        } else if let Some(bone) = bones.get_bone(head_idx) {
            let blended = bone.animation_rotate.slerp(local_rot, strength);
            bones.set_bone_rotation(head_idx, blended);
        }

        // 更新头部全局变换（子骨骼依赖）
        bones.update_single_bone_global(head_idx);
    }

    /// Two-Bone IK：肩(arm) → 肘(elbow) → 腕(wrist)
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
            (Some(a), Some(e), Some(w)) => (a, e, w),
            _ => return,
        };

        let arm_pos = mat4_translation(bones.get_global_transform(arm_idx));
        let elbow_pos = mat4_translation(bones.get_global_transform(elbow_idx));
        let wrist_pos = mat4_translation(bones.get_global_transform(wrist_idx));

        let upper_len = (elbow_pos - arm_pos).length();
        let lower_len = (wrist_pos - elbow_pos).length();
        if upper_len < 1e-4 || lower_len < 1e-4 { return; }

        let target_pos = target.position;
        let to_target = target_pos - arm_pos;
        let chain_len = upper_len + lower_len;
        // 限制目标距离不超过链长（留余量防止完全伸直）
        let target_dist = to_target.length().min(chain_len * 0.999);
        if target_dist < 1e-4 { return; }

        let forward = to_target / to_target.length();

        // 动态 hint：基于当前肘关节位置偏移方向
        let elbow_offset = elbow_pos - arm_pos;
        let elbow_proj = forward * elbow_offset.dot(forward);
        let hint_raw = elbow_offset - elbow_proj;
        let hint_dir = if hint_raw.length_squared() > 1e-4 {
            hint_raw.normalize()
        } else {
            // 肘关节在肩→目标直线上，使用默认 hint
            let default_hint = if is_left {
                Vec3::new(-0.3, -0.5, -0.7)
            } else {
                Vec3::new(0.3, -0.5, -0.7)
            };
            // 从 forward 中去除平行分量
            let proj = forward * default_hint.dot(forward);
            (default_hint - proj).normalize_or_zero()
        };

        // 余弦定理求肩关节角度
        let cos_a = ((upper_len * upper_len + target_dist * target_dist - lower_len * lower_len)
            / (2.0 * upper_len * target_dist)).clamp(-1.0, 1.0);
        let angle_a = cos_a.acos();

        // 计算新肘关节位置
        let elbow_new = arm_pos
            + forward * (angle_a.cos() * upper_len)
            + hint_dir * (angle_a.sin() * upper_len);

        // 计算新腕关节位置
        let elbow_to_target = (target_pos - elbow_new).normalize_or_zero();
        let wrist_new = elbow_new + elbow_to_target * lower_len;

        // 应用 IK 结果
        self.apply_arm_result(
            bones, arm_idx, elbow_idx, wrist_idx,
            arm_pos, elbow_pos, wrist_pos,
            elbow_new, wrist_new, target, strength,
        );
    }

    fn apply_arm_result(
        &self,
        bones: &mut BoneManager,
        arm_idx: usize, elbow_idx: usize, wrist_idx: usize,
        arm_pos: Vec3, old_elbow: Vec3, old_wrist: Vec3,
        new_elbow: Vec3, new_wrist: Vec3,
        target: &TrackPoint,
        strength: f32,
    ) {
        // 肩骨骼：旋转差量
        let arm_rot = mat4_rotation(bones.get_global_transform(arm_idx));
        let new_arm_rot = rotate_bone_direction(arm_pos, old_elbow, new_elbow, arm_rot);

        // 肘骨骼：旋转差量
        let elbow_rot = mat4_rotation(bones.get_global_transform(elbow_idx));
        let new_elbow_rot = rotate_bone_direction(old_elbow, old_wrist, new_wrist, elbow_rot);

        if strength >= 1.0 {
            bones.set_global_transform(arm_idx,
                Mat4::from_rotation_translation(new_arm_rot, arm_pos));
            bones.set_global_transform(elbow_idx,
                Mat4::from_rotation_translation(new_elbow_rot, new_elbow));
            bones.set_global_transform(wrist_idx,
                Mat4::from_rotation_translation(target.rotation, new_wrist));
        } else {
            // 混合
            let blend = |cur_rot: Quat, new_rot: Quat, cur_pos: Vec3, new_pos: Vec3| -> Mat4 {
                Mat4::from_rotation_translation(
                    cur_rot.slerp(new_rot, strength),
                    cur_pos.lerp(new_pos, strength),
                )
            };

            bones.set_global_transform(arm_idx,
                blend(arm_rot, new_arm_rot, arm_pos, arm_pos));
            bones.set_global_transform(elbow_idx,
                blend(elbow_rot, new_elbow_rot, old_elbow, new_elbow));

            let cur_wrist_rot = mat4_rotation(bones.get_global_transform(wrist_idx));
            bones.set_global_transform(wrist_idx,
                blend(cur_wrist_rot, target.rotation, old_wrist, new_wrist));
        }
    }
}

// ============================================================================
// 辅助函数
// ============================================================================

#[inline]
fn mat4_translation(m: Mat4) -> Vec3 {
    m.w_axis.truncate()
}

#[inline]
fn mat4_rotation(m: Mat4) -> Quat {
    Quat::from_mat4(&m)
}

/// 根据方向变化计算新旋转
fn rotate_bone_direction(from: Vec3, old_to: Vec3, new_to: Vec3, current_rot: Quat) -> Quat {
    let old_dir = (old_to - from).normalize_or_zero();
    let new_dir = (new_to - from).normalize_or_zero();
    if old_dir.length_squared() < 1e-6 || new_dir.length_squared() < 1e-6 {
        return current_rot;
    }
    let delta = Quat::from_rotation_arc(old_dir, new_dir);
    (delta * current_rot).normalize()
}
