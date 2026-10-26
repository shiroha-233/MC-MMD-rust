//! 布娃娃身体规格 —— 移植自 ragdoll-lab.html（参数见 D:\mcmmd\ragdoll-params.md）
//!
//! 16 段解剖学刚体划分（pelvis/chest/upperChest/head + 左右臂×3 + 左右腿×3），
//! MMD 日文骨名匹配（标准骨/变形骨D 双套名按顶点主权重选骨 + 位置守卫），
//! Dempster 解剖学质量分布、半径表、球/铰链关节限位。

use glam::{Mat4, Quat, Vec3};

use crate::model::VertexWeight;
use crate::skeleton::BoneManager;

/// 单位换算：1 模型单位 ≈ 0.09 米（Java 侧 MODEL_SCALE = 0.09）
pub const UNIT_TO_M: f32 = 0.09;

// ============================================================================
// 骨骼槽位表（slot → 候选日文名，自动追加 +D 变体）
// ============================================================================

/// 16 段刚体引用的槽位（顺序即 BODY_PARTS 中 a/b 引用的下标）
pub mod slot {
    pub const HIPS: usize = 0;
    pub const SPINE: usize = 1;
    pub const CHEST: usize = 2;
    pub const NECK: usize = 3;
    pub const HEAD: usize = 4;
    pub const L_UPPER_ARM: usize = 5;
    pub const L_LOWER_ARM: usize = 6;
    pub const L_HAND: usize = 7;
    pub const R_UPPER_ARM: usize = 8;
    pub const R_LOWER_ARM: usize = 9;
    pub const R_HAND: usize = 10;
    pub const L_UPPER_LEG: usize = 11;
    pub const L_LOWER_LEG: usize = 12;
    pub const L_FOOT: usize = 13;
    pub const L_TOES: usize = 14;
    pub const R_UPPER_LEG: usize = 15;
    pub const R_LOWER_LEG: usize = 16;
    pub const R_FOOT: usize = 17;
    pub const R_TOES: usize = 18;
    pub const COUNT: usize = 19;
}

/// 槽位候选骨名（lab 的 MMD_BONES 表，MMD_ALT 变体在匹配时自动展开）
const SLOT_CANDIDATES: [&[&str]; slot::COUNT] = [
    &["センター", "腰", "下半身"],            // hips
    &["上半身"],                              // spine
    &["上半身2", "上半身3"],                  // chest
    &["首"],                                  // neck
    &["頭"],                                  // head
    &["左腕"],                                // leftUpperArm
    &["左ひじ", "左肘"],                      // leftLowerArm
    &["左手首"],                              // leftHand
    &["右腕"],                                // rightUpperArm
    &["右ひじ", "右肘"],                      // rightLowerArm
    &["右手首"],                              // rightHand
    &["左足"],                                // leftUpperLeg
    &["左ひざ", "左膝"],                      // leftLowerLeg
    &["左足首"],                              // leftFoot
    &["左つま先", "左足先EX", "左足先"],      // leftToes
    &["右足"],                                // rightUpperLeg
    &["右ひざ", "右膝"],                      // rightLowerLeg
    &["右足首"],                              // rightFoot
    &["右つま先", "右足先EX", "右足先"],      // rightToes
];

/// PMD 旧名变体（lab 的 MMD_ALT）
const NAME_ALTS: &[(&str, &str)] = &[
    ("左ひじ", "左肘"),
    ("右ひじ", "右肘"),
    ("左ひざ", "左膝"),
    ("右ひざ", "右膝"),
    ("左つま先", "左足先"),
    ("右つま先", "右足先"),
];

// ============================================================================
// 质量分布 / 半径（lab 原表，米制，按身高比例缩放后换算为模型单位）
// ============================================================================

/// Dempster 人体质量分布（占总质量比例）
pub const MASS_FRAC: [f32; 16] = [
    0.142, // pelvis
    0.200, // chest
    0.160, // upperChest
    0.081, // head
    0.028, 0.016, 0.006, // lUpperArm, lLowerArm, lHand
    0.028, 0.016, 0.006, // rUpperArm, rLowerArm, rHand
    0.100, 0.0465, 0.0145, // lUpperLeg, lLowerLeg, lFoot
    0.100, 0.0465, 0.0145, // rUpperLeg, rLowerLeg, rFoot
];

/// 解剖学默认半径（米，无实测值时 fallback）
pub const RADIUS_M: [f32; 16] = [
    0.105, // pelvis
    0.115, // chest
    0.098, // upperChest
    0.088, // head
    0.045, 0.038, 0.032, // arms
    0.045, 0.038, 0.032,
    0.078, 0.055, 0.042, // legs
    0.078, 0.055, 0.042,
];

// ============================================================================
// 16 段刚体定义
// ============================================================================

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum JointKind {
    None,
    Spherical,
    Revolute,
}

/// 16 段刚体定义（lab 的 BODY_DEFS；a/b 为槽位下标，p 为父刚体下标）
pub struct BodyPartDef {
    /// 起始骨槽位
    pub a: usize,
    /// 终止骨槽位（None = 末端骨，沿父方向延续 lead 长度）
    pub b: Option<usize>,
    /// 父刚体下标（None = 根 pelvis）
    pub parent: Option<usize>,
    /// 关节类型
    pub joint: JointKind,
    /// 末端骨延伸长度（米）
    pub lead_m: f32,
    /// 是否腿部（决定自碰撞组）
    pub is_leg: bool,
}

pub const PELVIS: usize = 0;
pub const CHEST: usize = 1;
pub const UPPER_CHEST: usize = 2;
pub const HEAD: usize = 3;
pub const L_UPPER_ARM: usize = 4;
pub const L_LOWER_ARM: usize = 5;
pub const L_HAND: usize = 6;
pub const R_UPPER_ARM: usize = 7;
pub const R_LOWER_ARM: usize = 8;
pub const R_HAND: usize = 9;
pub const L_UPPER_LEG: usize = 10;
pub const L_LOWER_LEG: usize = 11;
pub const L_FOOT: usize = 12;
pub const R_UPPER_LEG: usize = 13;
pub const R_LOWER_LEG: usize = 14;
pub const R_FOOT: usize = 15;
pub const BODY_COUNT: usize = 16;

/// 躯干+头的最大段下标（apply_velocity_delta 的 upper_body_only 用）
pub const UPPER_BODY_MAX_PART: usize = HEAD;

pub const BODY_PARTS: [BodyPartDef; BODY_COUNT] = [
    BodyPartDef { a: slot::HIPS, b: Some(slot::SPINE), parent: None, joint: JointKind::None, lead_m: 0.16, is_leg: false },
    BodyPartDef { a: slot::SPINE, b: Some(slot::CHEST), parent: Some(PELVIS), joint: JointKind::Spherical, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::CHEST, b: Some(slot::NECK), parent: Some(CHEST), joint: JointKind::Spherical, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::HEAD, b: None, parent: Some(UPPER_CHEST), joint: JointKind::Spherical, lead_m: 0.20, is_leg: false },
    BodyPartDef { a: slot::L_UPPER_ARM, b: Some(slot::L_LOWER_ARM), parent: Some(UPPER_CHEST), joint: JointKind::Spherical, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::L_LOWER_ARM, b: Some(slot::L_HAND), parent: Some(L_UPPER_ARM), joint: JointKind::Revolute, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::L_HAND, b: None, parent: Some(L_LOWER_ARM), joint: JointKind::Spherical, lead_m: 0.10, is_leg: false },
    BodyPartDef { a: slot::R_UPPER_ARM, b: Some(slot::R_LOWER_ARM), parent: Some(UPPER_CHEST), joint: JointKind::Spherical, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::R_LOWER_ARM, b: Some(slot::R_HAND), parent: Some(R_UPPER_ARM), joint: JointKind::Revolute, lead_m: 0.0, is_leg: false },
    BodyPartDef { a: slot::R_HAND, b: None, parent: Some(R_LOWER_ARM), joint: JointKind::Spherical, lead_m: 0.10, is_leg: false },
    BodyPartDef { a: slot::L_UPPER_LEG, b: Some(slot::L_LOWER_LEG), parent: Some(PELVIS), joint: JointKind::Spherical, lead_m: 0.0, is_leg: true },
    BodyPartDef { a: slot::L_LOWER_LEG, b: Some(slot::L_FOOT), parent: Some(L_UPPER_LEG), joint: JointKind::Revolute, lead_m: 0.0, is_leg: true },
    BodyPartDef { a: slot::L_FOOT, b: Some(slot::L_TOES), parent: Some(L_LOWER_LEG), joint: JointKind::Spherical, lead_m: 0.0, is_leg: true },
    BodyPartDef { a: slot::R_UPPER_LEG, b: Some(slot::R_LOWER_LEG), parent: Some(PELVIS), joint: JointKind::Spherical, lead_m: 0.0, is_leg: true },
    BodyPartDef { a: slot::R_LOWER_LEG, b: Some(slot::R_FOOT), parent: Some(R_UPPER_LEG), joint: JointKind::Revolute, lead_m: 0.0, is_leg: true },
    BodyPartDef { a: slot::R_FOOT, b: Some(slot::R_TOES), parent: Some(R_LOWER_LEG), joint: JointKind::Spherical, lead_m: 0.0, is_leg: true },
];

/// 球关节限位（度）：[swing(侧摆), fwd(前摆), back(后摆), twist(绕骨轴扭转)]
/// 下标与 BODY_PARTS 一致；f64 精度供查表，f32 运行
pub const SPH_LIMITS_DEG: [(f32, f32, f32, f32); BODY_COUNT] = [
    (0.0, 0.0, 0.0, 0.0),        // pelvis（无关节）
    (32.0, 32.0, 25.0, 32.0),    // chest
    (26.0, 26.0, 22.0, 26.0),    // upperChest
    (42.0, 42.0, 38.0, 52.0),    // head
    (78.0, 78.0, 46.0, 38.0),    // lUpperArm
    (0.0, 0.0, 0.0, 0.0),        // lLowerArm（铰链）
    (58.0, 58.0, 58.0, 20.0),    // lHand
    (78.0, 78.0, 46.0, 38.0),    // rUpperArm
    (0.0, 0.0, 0.0, 0.0),        // rLowerArm（铰链）
    (58.0, 58.0, 58.0, 20.0),    // rHand
    (105.0, 105.0, 28.0, 40.0),  // lUpperLeg
    (0.0, 0.0, 0.0, 0.0),        // lLowerLeg（铰链）
    (22.0, 18.0, 20.0, 9.0),     // lFoot
    (105.0, 105.0, 28.0, 40.0),  // rUpperLeg
    (0.0, 0.0, 0.0, 0.0),        // rLowerLeg（铰链）
    (22.0, 18.0, 20.0, 9.0),     // rFoot
];

/// 铰链限位（度，[屈曲负向超出, 屈曲正向最大]），下标与 BODY_PARTS 一致
pub const HINGE_LIMITS_DEG: [(f32, f32); BODY_COUNT] = [
    (0.0, 0.0),   // pelvis
    (0.0, 0.0),   // chest
    (0.0, 0.0),   // upperChest
    (0.0, 0.0),   // head
    (0.0, 0.0),   // lUpperArm
    (-2.0, 146.0), // lLowerArm（肘，屈曲向前）
    (0.0, 0.0),   // lHand
    (0.0, 0.0),   // rUpperArm
    (-2.0, 146.0), // rLowerArm
    (0.0, 0.0),   // rHand
    (0.0, 0.0),   // lUpperLeg
    (-4.0, 150.0), // lLowerLeg（膝，屈曲向后）
    (0.0, 0.0),   // lFoot
    (0.0, 0.0),   // rUpperLeg
    (-4.0, 150.0), // rLowerLeg
    (0.0, 0.0),   // rFoot
];

/// 手臂/手关节限位整体乘数（lab 的 armLimit = 0.8）
pub const ARM_LIMIT: f32 = 0.8;

// ============================================================================
// 骨骼匹配
// ============================================================================

/// 展开候选名：原名 + PMD 别名 + (+D 变体)
fn expand_candidates(names: &[&str]) -> Vec<String> {
    let mut out = Vec::new();
    for name in names {
        out.push(name.to_string());
        for (from, to) in NAME_ALTS {
            if name == from {
                out.push(to.to_string());
            }
        }
    }
    let mut with_d = Vec::new();
    for name in &out {
        if !name.ends_with('D') && !name.ends_with("EX") {
            with_d.push(format!("{}D", name));
        }
    }
    out.extend(with_d);
    out
}

/// 顶点主权重骨统计：返回 [骨索引 -> 以该骨为主导权重的顶点数]
///
/// lab 语义：花火 左足 0 顶点 vs 左足D 3492 顶点 —— 用支配顶点数选变形骨
pub fn count_dominant_vertices(
    weights: &[VertexWeight],
    bone_count: usize,
) -> Vec<u32> {
    let mut counts = vec![0u32; bone_count];
    for w in weights {
        match w {
            VertexWeight::Bdef1 { bone } => {
                if *bone >= 0 && (*bone as usize) < bone_count {
                    counts[*bone as usize] += 1;
                }
            }
            VertexWeight::Bdef2 { bones, weight } => {
                let idx = if *weight >= 0.5 { 0 } else { 1 };
                let bone = bones[idx];
                if bone >= 0 && (bone as usize) < bone_count {
                    counts[bone as usize] += 1;
                }
            }
            VertexWeight::Sdef { bones, weight, .. } => {
                let idx = if *weight >= 0.5 { 0 } else { 1 };
                let bone = bones[idx];
                if bone >= 0 && (bone as usize) < bone_count {
                    counts[bone as usize] += 1;
                }
            }
            VertexWeight::Bdef4 { bones, weights } | VertexWeight::Qdef { bones, weights } => {
                let mut best = -1i32;
                let mut best_w = 0.0f32;
                for k in 0..4 {
                    if weights[k] > best_w {
                        best_w = weights[k];
                        best = bones[k];
                    }
                }
                if best >= 0 && (best as usize) < bone_count {
                    counts[best as usize] += 1;
                }
            }
        }
    }
    counts
}

/// 匹配结果：槽位 -> 骨骼索引
pub struct BoneMatch {
    pub slots: [Option<usize>; slot::COUNT],
}

/// MMD 骨名匹配（lab 的 matchMMDBones）
///
/// - 候选按「主导顶点数」选胜者（变形骨 D 通常挂了全部权重）
/// - 位置守卫：非首选候选与首选世界位置偏离 > 模型高 6% 直接淘汰
/// - chest 缺失时回落到 neck
pub fn match_mmd_bones(
    bones: &BoneManager,
    vertex_counts: &[u32],
    bbox_height: f32,
) -> BoneMatch {
    let guard = if bbox_height > 0.0 { bbox_height * 0.06 } else { 0.0 };
    let mut slots: [Option<usize>; slot::COUNT] = [None; slot::COUNT];
    let mut used: std::collections::HashSet<usize> = std::collections::HashSet::new();

    for (s, candidates) in SLOT_CANDIDATES.iter().enumerate() {
        let mut ref_pos: Option<Vec3> = None;
        let mut best: Option<(usize, u32)> = None;
        let mut first: Option<usize> = None;

        for name in expand_candidates(candidates) {
            let Some(idx) = bones.find_bone_by_name(&name) else {
                continue;
            };
            if used.contains(&idx) {
                continue;
            }
            let pos = bones.get_bone(idx).map(|b| b.initial_position).unwrap_or(Vec3::ZERO);
            if first.is_none() {
                first = Some(idx);
            }
            if ref_pos.is_none() {
                ref_pos = Some(pos);
            }
            // 位置守卫：变形骨应与标准骨重合，差太远的淘汰
            if guard > 0.0 {
                if let Some(rp) = ref_pos {
                    if pos.distance(rp) > guard {
                        continue;
                    }
                }
            }
            let n = vertex_counts.get(idx).copied().unwrap_or(0);
            if best.map_or(true, |(_, bn)| n > bn) {
                best = Some((idx, n));
            }
        }

        let chosen = best.map(|(i, _)| i).or(first);
        if let Some(idx) = chosen {
            slots[s] = Some(idx);
            used.insert(idx);
        }
    }

    // MMD 老模型没有 上半身2：chest 落到 neck
    if slots[slot::CHEST].is_none() {
        slots[slot::CHEST] = slots[slot::NECK];
    }

    BoneMatch { slots }
}

// ============================================================================
// 规格构建
// ============================================================================

/// 关节帧与限位（模型单位）
pub struct JointSpec {
    /// 约束帧旋转（刚体局部 → 世界对齐：X=侧摆轴, Y=front 基准, Z=骨轴）
    pub frame_rot: Quat,
    /// 角限位（弧度，帧局部 XYZ）
    pub ang_lower: Vec3,
    pub ang_upper: Vec3,
}

/// 单段刚体规格（模型单位、绑定姿态世界空间）
pub struct BodySpec {
    pub part_index: usize,
    /// 驱动骨骼索引
    pub bone_index: usize,
    /// 子骨索引（定方向/长度用，可无）
    pub child_bone_index: Option<usize>,
    /// 父刚体下标
    pub parent: Option<usize>,
    pub joint: JointKind,
    pub is_leg: bool,
    /// 骨骼绑定全局变换（右手模型空间）
    pub bone_bind: Mat4,
    /// 骨段全长（a→b 距离，模型单位；末端骨为 lead）
    pub seg_len: f32,
    /// 胶囊：中心相对骨骼原点的偏移 + 旋转（世界方向）
    pub col_pos: Vec3,
    pub col_rot: Quat,
    pub capsule_half_height: f32,
    pub capsule_radius: f32,
    /// 盒体（pelvis 退化时使用，相对骨骼原点无偏移）
    pub box_half_extents: Option<Vec3>,
    pub mass_kg: f32,
    /// 关节规格（joint != None 时存在）
    pub joint_spec: Option<JointSpec>,
}

/// 从匹配结果构建 16 段规格（全部模型单位）
///
/// `radius_scale` = 模型身高 / 1.7m 的比例（lab 参数按 1.7m 标定）
/// `measured_radii_m` = measureRadii 的实测结果（米制，优先使用）
/// `arm_limit_scale` = 手臂关节限位乘数（lab 默认 0.8）
pub fn build_spec(
    bones: &BoneManager,
    matched: &BoneMatch,
    radius_scale: f32,
    measured_radii_m: &[f32; BODY_COUNT],
    arm_limit_scale: f32,
) -> Vec<Option<BodySpec>> {
    const DEG: f32 = std::f32::consts::PI / 180.0;

    // 槽位骨骼的绑定姿态位置/旋转（右手模型空间）
    let slot_pos = |s: usize| -> Option<Vec3> {
        matched.slots[s].and_then(|i| bones.get_bone(i)).map(|b| b.initial_position)
    };
    let slot_bone = |s: usize| -> Option<usize> { matched.slots[s] };

    // 身体基准方向（lab 的 basis()）：side=左右轴, front=面朝方向, up=(0,1,0)
    let up = Vec3::Y;
    let side = match (slot_pos(slot::L_UPPER_ARM), slot_pos(slot::R_UPPER_ARM)) {
        (Some(l), Some(r)) => {
            let v = l - r;
            let flat = Vec3::new(v.x, 0.0, v.z);
            if flat.length_squared() > 1e-10 { flat.normalize() } else { Vec3::X }
        }
        _ => Vec3::X,
    };
    let mut front = Vec3::Z;
    if let (Some(toes), Some(foot)) = (slot_pos(slot::L_TOES), slot_pos(slot::L_FOOT)) {
        let v = Vec3::new(toes.x - foot.x, 0.0, toes.z - foot.z);
        if v.length_squared() > 1e-10 {
            front = v.normalize();
        } else if let (Some(rtoes), Some(rfoot)) = (slot_pos(slot::R_TOES), slot_pos(slot::R_FOOT)) {
            let v = Vec3::new(rtoes.x - rfoot.x, 0.0, rtoes.z - rfoot.z);
            if v.length_squared() > 1e-10 {
                front = v.normalize();
            }
        }
    } else {
        front = side.cross(up).normalize();
    }

    let mut out: Vec<Option<BodySpec>> = Vec::with_capacity(BODY_COUNT);

    for (pi, def) in BODY_PARTS.iter().enumerate() {
        let Some(a_bone) = slot_bone(def.a) else {
            out.push(None);
            continue;
        };
        let Some(a_pos) = slot_pos(def.a) else {
            out.push(None);
            continue;
        };
        let b_pos = def.b.and_then(slot_pos);

        // 方向与长度
        let (len, dir) = if let Some(bp) = b_pos {
            let v = bp - a_pos;
            let l = v.length();
            if l > 1e-6 {
                (l, v.normalize())
            } else {
                (l, Vec3::Y)
            }
        } else {
            // 末端骨：沿父骨（父刚体起始骨）方向延续，长度用解剖学固定值
            let dir = match def.parent {
                Some(p) => out[p].as_ref().and_then(|parent| {
                    let pa = parent.bone_bind.w_axis.truncate();
                    let v = a_pos - pa;
                    if v.length_squared() > 1e-10 { Some(v.normalize()) } else { None }
                }),
                None => None,
            };
            let lead = def.lead_m * radius_scale / UNIT_TO_M;
            (lead, dir.unwrap_or(Vec3::Y))
        };
        let seg_len = len;

        // 半径（米）：实测 > 解剖学表；脚部 ×0.62
        let mut r_m = if measured_radii_m[pi] > 0.0 {
            measured_radii_m[pi]
        } else {
            RADIUS_M[pi]
        };
        if def.is_leg && matches!(def.a, slot::L_FOOT | slot::R_FOOT) {
            r_m *= 0.62;
        }
        let r = (r_m * radius_scale / UNIT_TO_M).max(0.004);

        // 碰撞体（模型单位）
        let (col_pos, col_rot, half_height, box_half) = if len < 0.03 * radius_scale / UNIT_TO_M
            && pi == PELVIS
        {
            // 退化骨段（hips→spine 过短）：改用盒体
            let gap = match (slot_pos(slot::L_UPPER_LEG), slot_pos(slot::R_UPPER_LEG)) {
                (Some(l), Some(rt)) => l.distance(rt),
                _ => 0.22 * radius_scale / UNIT_TO_M,
            };
            (
                Vec3::ZERO,
                Quat::IDENTITY,
                0.0,
                Some(Vec3::new(
                    (0.06 * radius_scale / UNIT_TO_M).max(gap * 0.62),
                    0.07 * radius_scale / UNIT_TO_M,
                    0.09 * radius_scale / UNIT_TO_M,
                )),
            )
        } else {
            let half = (len / 2.0 - r).max(0.004);
            let rot = Quat::from_rotation_arc(Vec3::Y, dir);
            (dir * (len / 2.0), rot, half, None)
        };

        // 关节规格
        let joint_spec = if def.joint != JointKind::None {
            if def.parent.is_some() {
                // 骨轴（a→b）与 front 的垂直分量
                let bone_axis = if let Some(bp) = b_pos {
                    let v = bp - a_pos;
                    if v.length_squared() > 1e-10 { v.normalize() } else { dir }
                } else {
                    dir
                };
                let front_perp = {
                    let v = front - bone_axis * front.dot(bone_axis);
                    if v.length_squared() > 1e-8 {
                        v.normalize()
                    } else {
                        // front 与骨轴平行（罕见）：用 side 兜底
                        let v2 = side - bone_axis * side.dot(bone_axis);
                        if v2.length_squared() > 1e-8 {
                            v2.normalize()
                        } else {
                            Vec3::Y
                        }
                    }
                };

                // 约束帧：Z=骨轴, Y=±front_perp, X=Y×Z
                //  - 铰链屈曲向前（肘）：Y=-front_perp → 屈曲 = 绕 X 正转
                //  - 铰链屈曲向后（膝）：Y=+front_perp → 屈曲 = 绕 X 正转
                //  - 球关节：Y=+front_perp → 绕 X 负转 = 前摆（fwd），正转 = 后摆（back）
                let y_sign = match def.joint {
                    JointKind::Revolute => {
                        if matches!(def.a, slot::L_LOWER_ARM | slot::R_LOWER_ARM) {
                            -1.0
                        } else {
                            1.0
                        }
                    }
                    _ => 1.0,
                };
                let y_axis = front_perp * y_sign;
                let x_axis = y_axis.cross(bone_axis).normalize();
                let z_axis = x_axis.cross(y_axis).normalize();
                let frame_rot = Quat::from_mat3(&glam::Mat3::from_cols(x_axis, y_axis, z_axis));

                let (ang_lower, ang_upper) = match def.joint {
                    JointKind::Revolute => {
                        let (lo, hi) = HINGE_LIMITS_DEG[pi];
                        (
                            Vec3::new(lo * DEG, -10.0 * DEG, -10.0 * DEG),
                            Vec3::new(hi * DEG, 10.0 * DEG, 10.0 * DEG),
                        )
                    }
                    _ => {
                        let (swing, fwd, back, twist) = SPH_LIMITS_DEG[pi];
                        let mut scale = 1.0;
                        if matches!(
                            pi,
                            L_UPPER_ARM | R_UPPER_ARM | L_HAND | R_HAND
                        ) {
                            scale = arm_limit_scale;
                        }
                        (
                            Vec3::new(
                                -fwd * DEG * scale,
                                -swing * DEG * scale,
                                -twist * DEG * scale,
                            ),
                            Vec3::new(
                                back * DEG * scale,
                                swing * DEG * scale,
                                twist * DEG * scale,
                            ),
                        )
                    }
                };

                Some(JointSpec { frame_rot, ang_lower, ang_upper })
            } else {
                None
            }
        } else {
            None
        };

        out.push(Some(BodySpec {
            part_index: pi,
            bone_index: a_bone,
            child_bone_index: def.b.and_then(slot_bone),
            parent: def.parent,
            joint: def.joint,
            is_leg: def.is_leg,
            bone_bind: bones.get_global_transform(a_bone),
            seg_len,
            col_pos,
            col_rot,
            capsule_half_height: half_height,
            capsule_radius: r,
            box_half_extents: box_half,
            mass_kg: MASS_FRAC[pi] * 50.0,
            joint_spec,
        }));
    }

    out
}

// ============================================================================
// 实测半径（lab 的 measureRadii：顶点到骨轴垂距 0.85 分位数）
// ============================================================================

/// 从蒙皮顶点实测各段半径（米制输出）
///
/// `vertices`/`weights` 为模型原始数据（引擎右手空间，垂距与镜像无关）。
/// 只统计主导骨命中 16 段骨骼（或 twist/_jnt 类辅助骨沿父链上溯命中）的顶点。
pub fn measure_radii(
    bones: &BoneManager,
    matched: &BoneMatch,
    vertices: &[crate::model::RuntimeVertex],
    weights: &[VertexWeight],
    radius_scale: f32,
) -> [f32; BODY_COUNT] {
    let mut result = [0.0f32; BODY_COUNT];

    // 段骨骼 -> 刚体下标
    let mut bone_to_body: std::collections::HashMap<usize, usize> = std::collections::HashMap::new();
    for (pi, def) in BODY_PARTS.iter().enumerate() {
        if let Some(idx) = matched.slots[def.a] {
            bone_to_body.insert(idx, pi);
        }
    }

    // 每段骨轴（原点 + 单位方向，绑定姿态）
    let mut axes: [(Vec3, Vec3); BODY_COUNT] = [(Vec3::ZERO, Vec3::Y); BODY_COUNT];
    for (pi, def) in BODY_PARTS.iter().enumerate() {
        let Some(a_idx) = matched.slots[def.a] else { continue };
        let Some(a) = bones.get_bone(a_idx) else { continue };
        let origin = a.initial_position;
        let dir = def
            .b
            .and_then(|s| matched.slots[s])
            .and_then(|bi| bones.get_bone(bi))
            .map(|b| b.initial_position - origin)
            .filter(|v| v.length_squared() > 1e-10)
            .unwrap_or(Vec3::Y)
            .normalize();
        axes[pi] = (origin, dir);
    }

    // 顶点主导骨 -> 到骨轴垂距
    let mut dists: Vec<Vec<f32>> = vec![Vec::new(); BODY_COUNT];
    let bone_count = bones.bone_count();

    // 辅助骨上溯缓存（twist/_jnt 类）
    let mut deform_owner_cache: std::collections::HashMap<usize, Option<usize>> =
        std::collections::HashMap::new();
    for idx in 0..bone_count {
        if bone_to_body.contains_key(&idx) {
            deform_owner_cache.insert(idx, bone_to_body.get(&idx).copied());
        }
    }

    for (vi, w) in weights.iter().enumerate() {
        let (dominant, _) = match w {
            VertexWeight::Bdef1 { bone } => (*bone, 1.0),
            VertexWeight::Bdef2 { bones: bs, weight } => {
                if *weight >= 0.5 {
                    (bs[0], *weight)
                } else {
                    (bs[1], 1.0 - *weight)
                }
            }
            VertexWeight::Sdef { bones: bs, weight, .. } => {
                if *weight >= 0.5 {
                    (bs[0], *weight)
                } else {
                    (bs[1], 1.0 - *weight)
                }
            }
            VertexWeight::Bdef4 { bones: bs, weights: ws }
            | VertexWeight::Qdef { bones: bs, weights: ws } => {
                let mut best = -1i32;
                let mut bw = 0.0f32;
                for k in 0..4 {
                    if ws[k] > bw {
                        bw = ws[k];
                        best = bs[k];
                    }
                }
                (best, bw)
            }
        };
        if dominant < 0 || (dominant as usize) >= bone_count {
            continue;
        }
        let db = dominant as usize;

        // 命中判定：直接命中，或 twist/_jnt 类辅助骨沿父链上溯
        let owner = if let Some(o) = deform_owner_cache.get(&db) {
            *o
        } else {
            let name = bones.get_bone(db).map(|b| b.name.as_str()).unwrap_or("");
            let resolved = if is_auxiliary_bone(name) {
                let mut cur = bones.get_bone(db).and_then(|b| {
                    if b.parent_index >= 0 { Some(b.parent_index as usize) } else { None }
                });
                let mut hops = 0;
                let mut found = None;
                while let Some(ci) = cur {
                    if hops > 32 {
                        break;
                    }
                    if let Some(o) = bone_to_body.get(&ci) {
                        found = Some(*o);
                        break;
                    }
                    cur = bones.get_bone(ci).and_then(|b| {
                        if b.parent_index >= 0 { Some(b.parent_index as usize) } else { None }
                    });
                    hops += 1;
                }
                found
            } else {
                None
            };
            deform_owner_cache.insert(db, resolved);
            resolved
        };

        let Some(pi) = owner else { continue };
        let (origin, dir) = axes[pi];
        let rel = vertices[vi].position - origin;
        let along = rel.dot(dir);
        let perp = (rel.length_squared() - along * along).max(0.0).sqrt();
        dists[pi].push(perp);
    }

    // 0.85 分位，clamp [0.018, 0.2] 米（换算回米制比较）
    let pct = 0.85;
    for (pi, arr) in dists.iter().enumerate() {
        if arr.is_empty() {
            continue;
        }
        let mut sorted = arr.clone();
        sorted.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        let idx = ((sorted.len() as f32) * pct) as usize;
        let idx = idx.min(sorted.len() - 1);
        let r_units = sorted[idx];
        let r_m = r_units * UNIT_TO_M / radius_scale;
        result[pi] = r_m.clamp(0.018, 0.2);
    }

    result
}

/// 辅助骨判定（lab 的 deformOwner 过滤）：只认扭转骨/关节骨，排除肌肉块等
fn is_auxiliary_bone(name: &str) -> bool {
    let lower = name.to_lowercase();
    if lower.contains("muscleoffset") || lower.contains("_offset") {
        return false;
    }
    name.contains("ツイスト") || lower.contains("twist") || lower.contains("_jnt")
        || name.contains("ｊｎｔ")
}

// ============================================================================
// 腿部自碰撞安全（lab 的 autoFitLegs / legsOverlapAtBind）
// ============================================================================

/// 绑定姿态下双腿胶囊是否重叠（lab 的 legsOverlapAtBind）
pub fn legs_overlap_at_bind(spec: &[Option<BodySpec>], margin: f32) -> bool {
    let seg = |pi: usize| -> Option<(Vec3, Vec3, f32)> {
        let b = spec[pi].as_ref()?;
        if let Some(box_h) = b.box_half_extents {
            let c = b.bone_bind.w_axis.truncate();
            let r = box_h.x.max(box_h.y).max(box_h.z);
            Some((c, c, r))
        } else {
            let axis = b.col_rot.mul_vec3(Vec3::Y);
            let c = b.bone_bind.w_axis.truncate() + b.col_pos;
            let a = c - axis * b.capsule_half_height;
            let bp = c + axis * b.capsule_half_height;
            Some((a, bp, b.capsule_radius))
        }
    };

    let pairs = [
        (L_UPPER_LEG, R_UPPER_LEG),
        (L_LOWER_LEG, R_LOWER_LEG),
        (L_UPPER_LEG, R_LOWER_LEG),
        (L_LOWER_LEG, R_UPPER_LEG),
    ];
    for (pa, pb) in pairs {
        let (Some((a1, b1, r1)), Some((a2, b2, r2))) = (seg(pa), seg(pb)) else {
            continue;
        };
        if seg_seg_distance(a1, b1, a2, b2) < r1 + r2 + margin {
            return true;
        }
    }
    false
}

/// 线段-线段最短距离（lab 的 segSegDist）
pub fn seg_seg_distance(p1: Vec3, q1: Vec3, p2: Vec3, q2: Vec3) -> f32 {
    let d1 = q1 - p1;
    let d2 = q2 - p2;
    let r = p1 - p2;
    let a = d1.dot(d1);
    let e = d2.dot(d2);
    let f = d2.dot(r);
    let c = d1.dot(r);
    let b = d1.dot(d2);
    let den = a * e - b * b;

    let (s, t) = if den > 1e-9 {
        let s = ((b * f - c * e) / den).clamp(0.0, 1.0);
        let t = (b * s + f) / e;
        (s, t)
    } else {
        (0.0, if e > 1e-9 { f / e } else { 0.0 })
    };
    let (s, t) = if t < 0.0 {
        (if a > 1e-9 { (-c / a).clamp(0.0, 1.0) } else { 0.0 }, 0.0)
    } else if t > 1.0 {
        let t = 1.0;
        let s = if a > 1e-9 { ((b - c) / a).clamp(0.0, 1.0) } else { 0.0 };
        (s, t)
    } else {
        (s, t)
    };

    (p1 + d1 * s).distance(p2 + d2 * t)
}

/// 绑定姿态腿就重叠时自动收缩腿部碰撞体（lab 的 autoFitLegs）
///
/// 只缩大腿/小腿（不动脚），每轮 ×0.94，最多 18 轮，下限 0.45× 基准
pub fn auto_fit_legs(spec: &mut [Option<BodySpec>], min_scale: f32) -> bool {
    let leg_parts = [L_UPPER_LEG, L_LOWER_LEG, R_UPPER_LEG, R_LOWER_LEG];
    let mut base = [0.0f32; 4];
    for (k, &pi) in leg_parts.iter().enumerate() {
        if let Some(Some(b)) = spec.get(pi) {
            if b.box_half_extents.is_none() {
                base[k] = b.capsule_radius;
            }
        }
    }

    for _ in 0..18 {
        if !legs_overlap_at_bind(spec, 0.004) {
            return true;
        }
        let mut any = false;
        for (k, &pi) in leg_parts.iter().enumerate() {
            if base[k] <= 0.0 {
                continue;
            }
            if let Some(Some(b)) = spec.get_mut(pi) {
                if b.capsule_radius > base[k] * min_scale {
                    b.capsule_radius *= 0.94;
                    any = true;
                }
                b.capsule_half_height = (b.seg_len / 2.0 - b.capsule_radius).max(0.004);
            }
        }
        if !any {
            break;
        }
    }
    !legs_overlap_at_bind(spec, 0.004)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 铰链帧方向：肘（屈曲向前）绕 X 正转应使骨轴朝 front 偏转
    #[test]
    fn hinge_frame_flexion_direction() {
        // 站姿手臂：骨轴向下(-Y)，front=+Z，side=+X
        let bone_axis = -Vec3::Y;
        let front = Vec3::Z;
        let front_perp = (front - bone_axis * front.dot(bone_axis)).normalize();
        let y_axis = front_perp * -1.0; // 肘
        let x_axis = y_axis.cross(bone_axis).normalize();
        let z_axis = x_axis.cross(y_axis).normalize();

        // 绕 X 正转 90°：骨轴应从 -Y 转向 +Z(front)
        let rot = Mat4::from_axis_angle(x_axis, std::f32::consts::FRAC_PI_2);
        let rotated = rot.transform_vector3(bone_axis);
        assert!(rotated.dot(front) > 0.9, "肘屈曲应朝 front: {:?}", rotated);
    }

    /// 球关节帧：绕 X 负转（lower 限位方向）应使骨轴朝 front 偏转（前摆 fwd）
    #[test]
    fn spherical_frame_swing_direction() {
        let bone_axis = -Vec3::Y; // 大腿向下
        let front = Vec3::Z;
        let front_perp = (front - bone_axis * front.dot(bone_axis)).normalize();
        let y_axis = front_perp;
        let x_axis = y_axis.cross(bone_axis).normalize();
        let z_axis = x_axis.cross(y_axis).normalize();

        let rot = Mat4::from_axis_angle(x_axis, -std::f32::consts::FRAC_PI_4);
        let rotated = rot.transform_vector3(bone_axis);
        assert!(
            rotated.dot(front) > 0.5,
            "球关节负向 X 旋转应为前摆: {:?}",
            rotated
        );
    }

    #[test]
    fn seg_seg_distance_basic() {
        assert!((seg_seg_distance(Vec3::ZERO, Vec3::X, Vec3::Y, Vec3::Y + Vec3::X) - 1.0).abs() < 1e-5);
        assert!(seg_seg_distance(Vec3::ZERO, Vec3::X, Vec3::ZERO, Vec3::ZERO) < 1e-6);
    }
}
