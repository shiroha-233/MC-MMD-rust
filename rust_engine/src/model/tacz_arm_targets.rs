//! TaCZ 的瞬态双臂模型局部空间目标。
//!
//! 此模块不持久化 VPD 覆盖；调用方每帧取走目标后必须立即应用，取走即清除。
//! 主运行时唯一接线点位于普通骨骼与物理更新完成后、蒙皮矩阵生成前，
//! 以保证结果参与同帧渲染且不会被物理阶段再次覆盖。

use glam::{Mat4, Quat, Vec3};
use std::sync::atomic::{AtomicUsize, Ordering};

use crate::model::hand_attachment::find_hand_attachment;
use crate::skeleton::BoneManager;

const EPSILON: f32 = 1.0e-4;
pub const DIAGNOSTIC_FLOAT_COUNT: usize = 24;
const SIDE_DIAGNOSTIC_STRIDE: usize = 12;
const DIAGNOSTIC_LIMIT: usize = 16;
static DIAGNOSTIC_COUNT: AtomicUsize = AtomicUsize::new(0);
const LEFT_ARM_NAMES: &[&str] = &[
    "左腕",
    "leftUpperArm",
    "LeftUpperArm",
    "left_arm",
    "LeftArm",
];
const LEFT_ELBOW_NAMES: &[&str] = &[
    "左ひじ",
    "leftLowerArm",
    "LeftLowerArm",
    "left_elbow",
    "LeftElbow",
];
const LEFT_WRIST_NAMES: &[&str] = &["左手首", "leftHand", "LeftHand", "left_wrist", "LeftWrist"];
const RIGHT_ARM_NAMES: &[&str] = &[
    "右腕",
    "rightUpperArm",
    "RightUpperArm",
    "right_arm",
    "RightArm",
];
const RIGHT_ELBOW_NAMES: &[&str] = &[
    "右ひじ",
    "rightLowerArm",
    "RightLowerArm",
    "right_elbow",
    "RightElbow",
];
const RIGHT_WRIST_NAMES: &[&str] = &[
    "右手首",
    "rightHand",
    "RightHand",
    "right_wrist",
    "RightWrist",
];

/// 左右手目标矩阵。矩阵在模型局部空间，列主序，与 `glam::Mat4` 和 JNI float[16] 一致。
#[derive(Clone, Copy, Debug)]
pub struct TaczArmTargets {
    pub left: Option<Mat4>,
    pub right: Option<Mat4>,
}

/// 同一帧双臂求解结果。诊断数组左右各占 12 项：
/// 目标挂点、最终手首、最终挂点、挂点误差、目标距离、最大臂长、是否钳制。
#[derive(Clone, Copy, Debug)]
pub struct TaczArmApplyOutcome {
    pub applied_mask: u8,
    pub diagnostics: [f32; DIAGNOSTIC_FLOAT_COUNT],
}

#[derive(Clone, Copy, Debug, Default)]
struct ArmChain {
    arm: Option<usize>,
    elbow: Option<usize>,
    wrist: Option<usize>,
    attachment: Option<usize>,
}

/// 按模型实例缓存 TaCZ 所需腕链，避免在动画热路径逐帧扫描骨名。
#[derive(Debug, Default)]
pub struct TaczArmSolverCache {
    left: ArmChain,
    right: ArmChain,
    resolved: bool,
}

impl TaczArmSolverCache {
    fn resolve(&mut self, bones: &BoneManager) {
        if self.resolved {
            return;
        }
        self.left = resolve_chain(
            bones,
            LEFT_ARM_NAMES,
            LEFT_ELBOW_NAMES,
            LEFT_WRIST_NAMES,
            "Hand_Attach_L",
            'L',
        );
        self.right = resolve_chain(
            bones,
            RIGHT_ARM_NAMES,
            RIGHT_ELBOW_NAMES,
            RIGHT_WRIST_NAMES,
            "Hand_Attach_R",
            'R',
        );
        self.resolved = true;
    }
}

impl Default for TaczArmApplyOutcome {
    fn default() -> Self {
        Self {
            applied_mask: 0,
            diagnostics: [f32::NAN; DIAGNOSTIC_FLOAT_COUNT],
        }
    }
}

impl TaczArmTargets {
    /// 从 JNI 的连续 32 个 float 构造目标：前 16 个为左手，后 16 个为右手。
    pub fn from_column_major_slice(values: &[f32], valid_mask: u8) -> Option<Self> {
        if values.len() != 32 || valid_mask == 0 || valid_mask & !0b11 != 0 {
            return None;
        }
        let left = Mat4::from_cols_array(&values[..16].try_into().ok()?);
        let right = Mat4::from_cols_array(&values[16..].try_into().ok()?);
        if valid_mask & 0b01 != 0 && !is_valid_target_matrix(left) {
            return None;
        }
        if valid_mask & 0b10 != 0 && !is_valid_target_matrix(right) {
            return None;
        }
        Some(Self {
            left: (valid_mask & 0b01 != 0).then_some(left),
            right: (valid_mask & 0b10 != 0).then_some(right),
        })
    }

    pub fn valid_mask(self) -> u8 {
        u8::from(self.left.is_some()) | (u8::from(self.right.is_some()) << 1)
    }
}

/// 校验目标是有限的仿射刚体姿态；不接受缩放、透视或零长度四元数。
pub fn is_valid_target_matrix(matrix: Mat4) -> bool {
    if !matrix.to_cols_array().iter().all(|value| value.is_finite()) {
        return false;
    }
    if matrix.x_axis.w.abs() > EPSILON
        || matrix.y_axis.w.abs() > EPSILON
        || matrix.z_axis.w.abs() > EPSILON
        || (matrix.w_axis.w - 1.0).abs() > EPSILON
    {
        return false;
    }
    let (scale, rotation, position) = matrix.to_scale_rotation_translation();
    position.is_finite()
        && rotation.is_finite()
        && rotation.length_squared() > EPSILON
        && (scale - Vec3::ONE).abs().max_element() <= 1.0e-3
}

/// 对已完成动画评估的骨骼应用双臂目标。
///
/// 每一侧独立求解；缺少任一腕链骨骼或几何退化时，该侧保持动画原样。
/// 求解器只写入上臂、肘部和手首，不会移动模型根、躯干、腿或头。
pub fn apply_tacz_arm_targets(
    bones: &mut BoneManager,
    cache: &mut TaczArmSolverCache,
    targets: TaczArmTargets,
) -> TaczArmApplyOutcome {
    cache.resolve(bones);
    let mut outcome = TaczArmApplyOutcome::default();
    if let Some(left) = targets.left {
        if let Some(diagnostic) = apply_side(bones, left, "左", cache.left) {
            outcome.applied_mask |= 0b01;
            write_side_diagnostic(&mut outcome.diagnostics, 0, diagnostic);
        }
    }
    if let Some(right) = targets.right {
        if let Some(diagnostic) = apply_side(bones, right, "右", cache.right) {
            outcome.applied_mask |= 0b10;
            write_side_diagnostic(&mut outcome.diagnostics, SIDE_DIAGNOSTIC_STRIDE, diagnostic);
        }
    }
    outcome
}

#[derive(Clone, Copy, Debug)]
struct SideDiagnostic {
    attachment_target: Vec3,
    final_wrist: Vec3,
    final_attachment: Vec3,
    attachment_error: f32,
    target_distance: f32,
    max_reach: f32,
    clamped: bool,
}

fn apply_side(
    bones: &mut BoneManager,
    attachment_target: Mat4,
    side: &str,
    chain: ArmChain,
) -> Option<SideDiagnostic> {
    let arm = chain.arm;
    let elbow = chain.elbow;
    let wrist = chain.wrist;
    let (Some(arm), Some(elbow), Some(wrist)) = (arm, elbow, wrist) else {
        diagnose(|| {
            format!(
                "TaCZ {}臂 IK 跳过: 上臂={:?}, 肘={:?}, 手首={:?}",
                side, arm, elbow, wrist
            )
        });
        return None;
    };

    let arm_matrix = bones.get_global_transform(arm);
    let elbow_matrix = bones.get_global_transform(elbow);
    let wrist_matrix = bones.get_global_transform(wrist);
    let attachment = chain.attachment;
    let wrist_target = desired_wrist_target(bones, wrist_matrix, attachment_target, attachment);
    let target_distance = (translation(wrist_target) - translation(arm_matrix)).length();
    let max_reach = (translation(elbow_matrix) - translation(arm_matrix)).length()
        + (translation(wrist_matrix) - translation(elbow_matrix)).length();
    let clamped = target_distance > max_reach - EPSILON;
    let Some((new_elbow, new_wrist)) = solve_two_bone_positions(
        translation(arm_matrix),
        translation(elbow_matrix),
        translation(wrist_matrix),
        translation(wrist_target),
    ) else {
        diagnose(|| {
            format!(
                "TaCZ {}臂 IK 跳过: 几何退化, 上臂='{}', 肘='{}', 手首='{}', target={:?}",
                side,
                bone_name(bones, arm),
                bone_name(bones, elbow),
                bone_name(bones, wrist),
                translation(wrist_target)
            )
        });
        return None;
    };

    // 仅旋转上臂以指向新肘部，根和肩部位置严格保持动画评估结果。
    let new_arm_rotation = rotate_towards(
        translation(arm_matrix),
        translation(elbow_matrix),
        new_elbow,
        rotation(arm_matrix),
    );
    bones.set_global_transform(
        arm,
        Mat4::from_rotation_translation(new_arm_rotation, translation(arm_matrix)),
    );

    // 上臂写入会传播子树，因此在新姿态下重新取得肘部和手首的当前位置。
    let elbow_after_arm = bones.get_global_transform(elbow);
    let wrist_after_arm = bones.get_global_transform(wrist);
    let new_elbow_rotation = rotate_towards(
        translation(elbow_after_arm),
        translation(wrist_after_arm),
        new_wrist,
        rotation(elbow_after_arm),
    );
    bones.set_global_transform(
        elbow,
        Mat4::from_rotation_translation(new_elbow_rotation, new_elbow),
    );
    // TaCZ 捕获的是原版整条手臂的渲染姿态，并不提供 MMD 手腕 roll 语义。
    // 手首保留 VMD 评估后的方向，只让两骨链追踪本帧腕端位置。
    bones.set_global_transform(
        wrist,
        Mat4::from_rotation_translation(rotation(wrist_target), new_wrist),
    );
    // 写入后必须从骨骼树回读，不能把解析几何的中间值误报为最终结果。
    let final_wrist = translation(bones.get_global_transform(wrist));
    let final_attachment = attachment
        .map(|index| translation(bones.get_global_transform(index)))
        .unwrap_or(final_wrist);
    let attachment_target_position = translation(attachment_target);
    let attachment_error = (final_attachment - attachment_target_position).length();
    diagnose(|| {
        format!(
        "TaCZ {}臂 IK 已应用: 上臂='{}'({}), 肘='{}'({}), 手首='{}'({}), target={:?}, final_wrist={:?}, final_attachment={:?}, error={:.4}, target_distance={:.4}, max_reach={:.4}, clamped={}",
        side,
        bone_name(bones, arm), arm,
        bone_name(bones, elbow), elbow,
        bone_name(bones, wrist), wrist,
        attachment_target_position, final_wrist, final_attachment, attachment_error,
        target_distance, max_reach, clamped
    )
    });
    Some(SideDiagnostic {
        attachment_target: attachment_target_position,
        final_wrist,
        final_attachment,
        attachment_error,
        target_distance,
        max_reach,
        clamped,
    })
}

fn write_side_diagnostic(
    output: &mut [f32; DIAGNOSTIC_FLOAT_COUNT],
    offset: usize,
    diagnostic: SideDiagnostic,
) {
    output[offset..offset + 3].copy_from_slice(&diagnostic.attachment_target.to_array());
    output[offset + 3..offset + 6].copy_from_slice(&diagnostic.final_wrist.to_array());
    output[offset + 6..offset + 9].copy_from_slice(&diagnostic.final_attachment.to_array());
    output[offset + 9] = diagnostic.attachment_error;
    output[offset + 10] = diagnostic.target_distance;
    output[offset + 11] = if diagnostic.clamped {
        -diagnostic.max_reach
    } else {
        diagnostic.max_reach
    };
}

fn bone_name(bones: &BoneManager, index: usize) -> &str {
    bones
        .get_bone(index)
        .map(|bone| bone.name.as_str())
        .unwrap_or("?")
}

/// 只记录启动后的少量求解结果，既建立运行时证据，也避免逐帧污染整合包日志。
fn diagnose(message: impl FnOnce() -> String) {
    if DIAGNOSTIC_COUNT.fetch_add(1, Ordering::Relaxed) < DIAGNOSTIC_LIMIT {
        log::info!("{}", message());
    }
}

fn desired_wrist_target(
    bones: &BoneManager,
    wrist_matrix: Mat4,
    attachment_target: Mat4,
    attachment: Option<usize>,
) -> Mat4 {
    // 作者提供挂点时，按动画后的手首方向反推腕点位置；目标旋转不覆盖 MMD 手腕方向。
    let offset = attachment
        .map(|index| wrist_matrix.inverse() * bones.get_global_transform(index))
        .unwrap_or(Mat4::IDENTITY);
    wrist_target_preserving_rotation(wrist_matrix, attachment_target, offset)
}

fn wrist_target_preserving_rotation(
    wrist_matrix: Mat4,
    attachment_target: Mat4,
    wrist_to_attachment: Mat4,
) -> Mat4 {
    let wrist_rotation = rotation(wrist_matrix);
    let wrist_position =
        translation(attachment_target) - wrist_rotation * translation(wrist_to_attachment);
    Mat4::from_rotation_translation(wrist_rotation, wrist_position)
}

fn find_first(bones: &BoneManager, names: &[&str]) -> Option<usize> {
    names.iter().find_map(|name| bones.find_bone_by_name(name))
}

fn resolve_chain(
    bones: &BoneManager,
    arm_names: &[&str],
    elbow_names: &[&str],
    wrist_names: &[&str],
    explicit_attachment_name: &str,
    dummy_side: char,
) -> ArmChain {
    ArmChain {
        arm: find_first(bones, arm_names),
        elbow: find_first(bones, elbow_names),
        wrist: find_first(bones, wrist_names),
        attachment: find_hand_attachment(bones, explicit_attachment_name, dummy_side),
    }
}

fn solve_two_bone_positions(
    root: Vec3,
    elbow: Vec3,
    wrist: Vec3,
    target: Vec3,
) -> Option<(Vec3, Vec3)> {
    let upper_len = (elbow - root).length();
    let lower_len = (wrist - elbow).length();
    let target_delta = target - root;
    let target_length = target_delta.length();
    if upper_len < EPSILON || lower_len < EPSILON || target_length < EPSILON {
        return None;
    }
    let direction = target_delta / target_length;
    let reach = (upper_len + lower_len - EPSILON).max(EPSILON);
    let distance = target_length.clamp((upper_len - lower_len).abs() + EPSILON, reach);
    let old_elbow_offset = elbow - root;
    let perpendicular = old_elbow_offset - direction * old_elbow_offset.dot(direction);
    let bend_direction = if perpendicular.length_squared() > EPSILON {
        perpendicular.normalize()
    } else {
        // 共线时选择稳定法向，避免数值抖动。
        let vertical_normal = direction.cross(Vec3::Y).normalize_or_zero();
        if vertical_normal.length_squared() > EPSILON {
            vertical_normal
        } else {
            direction.cross(Vec3::X).normalize_or_zero()
        }
    };
    let cos_root = ((upper_len * upper_len + distance * distance - lower_len * lower_len)
        / (2.0 * upper_len * distance))
        .clamp(-1.0, 1.0);
    let root_angle = cos_root.acos();
    let solved_elbow = root
        + direction * (root_angle.cos() * upper_len)
        + bend_direction * (root_angle.sin() * upper_len);
    let solved_wrist = root + direction * distance;
    Some((solved_elbow, solved_wrist))
}

fn translation(matrix: Mat4) -> Vec3 {
    matrix.w_axis.truncate()
}
fn rotation(matrix: Mat4) -> Quat {
    Quat::from_mat4(&matrix).normalize()
}

fn rotate_towards(origin: Vec3, old_end: Vec3, new_end: Vec3, current: Quat) -> Quat {
    let old_direction = (old_end - origin).normalize_or_zero();
    let new_direction = (new_end - origin).normalize_or_zero();
    if old_direction.length_squared() < EPSILON || new_direction.length_squared() < EPSILON {
        return current;
    }
    (Quat::from_rotation_arc(old_direction, new_direction) * current).normalize()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_finite_rigid_column_major_targets() {
        let matrix =
            Mat4::from_rotation_translation(Quat::from_rotation_y(0.4), Vec3::new(1.0, 2.0, 3.0));
        let mut values = [0.0; 32];
        values[..16].copy_from_slice(&matrix.to_cols_array());
        values[16..].copy_from_slice(&matrix.to_cols_array());
        assert!(TaczArmTargets::from_column_major_slice(&values, 0b11).is_some());
        assert!(TaczArmTargets::from_column_major_slice(&values, 0b01)
            .unwrap()
            .right
            .is_none());
        assert!(TaczArmTargets::from_column_major_slice(&values, 0).is_none());
    }

    #[test]
    fn rejects_non_finite_or_scaled_targets() {
        assert!(!is_valid_target_matrix(Mat4::from_scale(Vec3::splat(2.0))));
        let mut values = Mat4::IDENTITY.to_cols_array();
        values[3] = f32::NAN;
        assert!(!is_valid_target_matrix(Mat4::from_cols_array(&values)));
    }

    #[test]
    fn two_bone_solution_reaches_a_reachable_target_without_moving_root() {
        let root = Vec3::ZERO;
        let target = Vec3::new(1.0, 1.0, 0.0);
        let (elbow, wrist) =
            solve_two_bone_positions(root, Vec3::X, Vec3::new(2.0, 0.0, 0.0), target).unwrap();
        assert!((wrist - target).length() < 1.0e-4);
        assert!(((elbow - root).length() - 1.0).abs() < 1.0e-4);
        assert!(((wrist - elbow).length() - 1.0).abs() < 1.0e-4);
    }

    #[test]
    fn wrist_target_keeps_animated_rotation_and_aligns_attachment_position() {
        let wrist_rotation = Quat::from_rotation_z(0.7);
        let wrist = Mat4::from_rotation_translation(wrist_rotation, Vec3::new(1.0, 2.0, 3.0));
        let offset = Mat4::from_translation(Vec3::new(0.5, -0.25, 0.75));
        let attachment_target =
            Mat4::from_rotation_translation(Quat::from_rotation_x(1.2), Vec3::new(8.0, 9.0, 10.0));

        let target = wrist_target_preserving_rotation(wrist, attachment_target, offset);
        let final_attachment = target * offset;

        assert!(rotation(target).dot(wrist_rotation).abs() > 1.0 - 1.0e-5);
        assert!((translation(final_attachment) - translation(attachment_target)).length() < 1.0e-5);
    }
}
