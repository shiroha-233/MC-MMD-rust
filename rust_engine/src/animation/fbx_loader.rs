//! FBX 动画加载器
//!
//! 从 FBX 文件中提取骨骼/Morph 动画数据，转换为 VmdAnimation。
//! 支持 Mixamo 骨骼名自动映射到 PMX 标准骨骼名。

use std::collections::{HashMap, BTreeSet};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use glam::{Quat, Vec3};

use crate::{MmdError, Result};
use super::fbx_parser::{parse_fbx, FbxNode};
use super::fbx_bone_mapping::map_fbx_bone_name;
use super::keyframe::{BoneKeyframe, IkKeyframe};
use super::motion::Motion;
use super::vmd_loader::VmdAnimation;

/// FBX 时间单位：1秒 = 46186158000
const FBX_TICKS_PER_SECOND: f64 = 46_186_158_000.0;
const VMD_FPS: f64 = 30.0;

/// FBX 模型节点（骨骼）信息
struct FbxModel {
    name: String,
    default_translation: Vec3,
    default_rotation: Vec3,
    pre_rotation: Vec3,
    rotation_order: u8,
    is_bone: bool,
}

/// FBX 动画曲线数据
struct FbxCurve {
    key_times: Vec<i64>,
    key_values: Vec<f32>,
}

/// 连接属性类型
enum ConnProperty {
    Translation,
    Rotation,
}

/// FBX 文件缓存（避免重复解析大文件）
pub struct FbxCache {
    nodes: Vec<FbxNode>,
    stack_names: Vec<String>,
}

impl FbxCache {
    /// 从文件加载并缓存解析结果
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let file = File::open(path.as_ref())
            .map_err(|e| MmdError::FbxParse(format!("打开文件失败: {}", e)))?;
        let mut reader = BufReader::new(file);
        let nodes = parse_fbx(&mut reader)?;

        let stack_names = extract_stack_names(&nodes);

        Ok(Self { nodes, stack_names })
    }

    /// 获取所有 AnimationStack 名称
    pub fn stack_names(&self) -> &[String] {
        &self.stack_names
    }

    /// 从缓存中加载指定 Stack 的动画
    pub fn load_animation(&self, stack_name: Option<&str>) -> Result<VmdAnimation> {
        let motion = extract_animation(&self.nodes, stack_name)?;
        Ok(VmdAnimation::from_motion(motion))
    }
}

/// 从 FBX 文件加载动画（无缓存，适用于 JNI 单次调用）
pub fn load_fbx_animation<P: AsRef<Path>>(path: P, stack_name: Option<&str>) -> Result<VmdAnimation> {
    let file = File::open(path.as_ref())
        .map_err(|e| MmdError::FbxParse(format!("打开文件失败: {}", e)))?;
    let mut reader = BufReader::new(file);
    let nodes = parse_fbx(&mut reader)?;

    let motion = extract_animation(&nodes, stack_name)?;
    Ok(VmdAnimation::from_motion(motion))
}

/// 从解析后的 FBX 节点树中提取动画
/// stack_name 为 None 时取第一个 Stack，为 Some 时按名称模糊匹配
fn extract_animation(nodes: &[FbxNode], stack_name: Option<&str>) -> Result<Motion> {
    let objects = nodes.iter().find(|n| n.name == "Objects")
        .ok_or_else(|| MmdError::FbxParse("未找到 Objects 节点".into()))?;
    let connections = nodes.iter().find(|n| n.name == "Connections")
        .ok_or_else(|| MmdError::FbxParse("未找到 Connections 节点".into()))?;

    // 收集各类对象
    let mut models: HashMap<i64, FbxModel> = HashMap::new();
    let mut curves: HashMap<i64, FbxCurve> = HashMap::new();
    let mut all_curve_node_ids: BTreeSet<i64> = BTreeSet::new();
    let mut anim_stacks: Vec<(i64, String)> = Vec::new();
    let mut anim_layer_ids: BTreeSet<i64> = BTreeSet::new();
    let mut model_parent: HashMap<i64, i64> = HashMap::new();

    // 解析 GlobalSettings 坐标系（UpAxis=2 表示 Z-up，如 Blender 导出）
    let is_z_up = nodes.iter()
        .find(|n| n.name == "GlobalSettings")
        .and_then(|gs| gs.find_child("Properties70"))
        .map(|p70| {
            p70.children.iter().any(|p| {
                p.name == "P" && p.properties.len() >= 5
                    && p.properties[0].as_string() == Some("UpAxis")
                    && p.properties[4].as_f64().unwrap_or(1.0) as i32 == 2
            })
        })
        .unwrap_or(false);

    for child in &objects.children {
        match child.name.as_str() {
            "Model" => {
                if let Some(model) = parse_model_node(child) {
                    models.insert(model.0, model.1);
                }
            }
            "AnimationCurve" => {
                if let Some((id, curve)) = parse_animation_curve(child) {
                    curves.insert(id, curve);
                }
            }
            "AnimationCurveNode" => {
                if let Some(id) = child.properties.first().and_then(|p| p.as_i64()) {
                    all_curve_node_ids.insert(id);
                }
            }
            "AnimationStack" => {
                if let Some(id) = child.properties.first().and_then(|p| p.as_i64()) {
                    let name = child.properties.get(1)
                        .and_then(|p| p.as_string())
                        .map(|s| clean_fbx_name(s))
                        .unwrap_or_default();
                    anim_stacks.push((id, name));
                }
            }
            "AnimationLayer" => {
                if let Some(id) = child.properties.first().and_then(|p| p.as_i64()) {
                    anim_layer_ids.insert(id);
                }
            }
            _ => {}
        }
    }

    // 第一遍连接扫描：构建 Stack→Layer→CurveNode 层级，筛选第一个 Stack 的 CurveNode
    let mut layer_to_stack: HashMap<i64, i64> = HashMap::new();
    let mut cn_to_layer: HashMap<i64, i64> = HashMap::new();

    for conn in &connections.children {
        if conn.name != "C" { continue; }
        let props = &conn.properties;
        if props.len() < 3 { continue; }
        let conn_type = match props[0].as_string() { Some(s) => s, None => continue };
        if conn_type != "OO" { continue; }
        let child_id = match props[1].as_i64() { Some(v) => v, None => continue };
        let parent_id = match props[2].as_i64() { Some(v) => v, None => continue };

        if anim_layer_ids.contains(&child_id) && anim_stacks.iter().any(|(id, _)| *id == parent_id) {
            layer_to_stack.insert(child_id, parent_id);
        }
        if all_curve_node_ids.contains(&child_id) && anim_layer_ids.contains(&parent_id) {
            cn_to_layer.insert(child_id, parent_id);
        }
        if models.contains_key(&child_id) && models.contains_key(&parent_id) {
            if models[&parent_id].is_bone {
                model_parent.insert(child_id, parent_id);
            }
        }
    }

    // 确定目标 AnimationStack
    let target_stack_id = if let Some(name) = stack_name {
        // 按名称模糊匹配（忽略大小写，支持部分匹配）
        let lower = name.to_ascii_lowercase();
        anim_stacks.iter()
            .find(|(_, sn)| sn.to_ascii_lowercase().contains(&lower))
            .map(|(id, _)| *id)
            .ok_or_else(|| {
                let available: Vec<&str> = anim_stacks.iter().map(|(_, n)| n.as_str()).collect();
                MmdError::FbxParse(format!(
                    "未找到名为 '{}' 的 AnimationStack，可用: {:?}", name, available
                ))
            })?
    } else {
        anim_stacks.first()
            .ok_or_else(|| MmdError::FbxParse("FBX 文件不包含 AnimationStack".into()))?
            .0
    };

    // 确定有效 CurveNode 集合（属于目标 Stack 的 Layer）
    let curve_node_ids: BTreeSet<i64> = if anim_stacks.len() > 1 {
        let valid_layers: BTreeSet<i64> = layer_to_stack.iter()
            .filter(|(_, &stack)| stack == target_stack_id)
            .map(|(&layer, _)| layer)
            .collect();
        cn_to_layer.iter()
            .filter(|(_, layer)| valid_layers.contains(layer))
            .map(|(&cn, _)| cn)
            .collect()
    } else {
        all_curve_node_ids
    };

    // 骨骼世界静息旋转（world_rest = 祖先链 pre*default 累积）
    let world_rest_rot: HashMap<i64, Quat> = {
        let mut result: HashMap<i64, Quat> = HashMap::new();
        let mut remaining: Vec<i64> = models.keys().copied().collect();
        let mut max_iter = remaining.len() + 1;
        while !remaining.is_empty() && max_iter > 0 {
            max_iter -= 1;
            let mut next = Vec::new();
            for &mid in &remaining {
                let pid = model_parent.get(&mid).copied();
                if let Some(p) = pid {
                    if models.contains_key(&p) && !result.contains_key(&p) {
                        next.push(mid);
                        continue;
                    }
                }
                let m = &models[&mid];
                let q_pre = euler_to_quat(
                    m.pre_rotation.x, m.pre_rotation.y, m.pre_rotation.z,
                    0, // PreRotation 固定 XYZ 顺序
                );
                let q_rest = euler_to_quat(
                    m.default_rotation.x, m.default_rotation.y, m.default_rotation.z,
                    m.rotation_order,
                );
                let parent_rest = pid
                    .and_then(|p| result.get(&p))
                    .copied()
                    .unwrap_or(Quat::IDENTITY);
                result.insert(mid, parent_rest * q_pre * q_rest);
            }
            remaining = next;
        }
        result
    };

    // 构建 CurveNode→Model 和 Curve→CurveNode 映射
    let mut cn_to_model: HashMap<i64, (i64, ConnProperty)> = HashMap::new();
    let mut curve_to_cn: HashMap<i64, (i64, u8)> = HashMap::new();

    for conn in &connections.children {
        if conn.name != "C" { continue; }
        let props = &conn.properties;
        if props.len() < 3 { continue; }

        let conn_type = match props[0].as_string() { Some(s) => s, None => continue };
        let child_id = match props[1].as_i64() { Some(v) => v, None => continue };
        let parent_id = match props[2].as_i64() { Some(v) => v, None => continue };

        if conn_type == "OP" && props.len() >= 4 {
            if let Some(prop_name) = props[3].as_string() {
                if curve_node_ids.contains(&child_id) && models.contains_key(&parent_id) {
                    let cp = match prop_name {
                        "Lcl Translation" => ConnProperty::Translation,
                        "Lcl Rotation" => ConnProperty::Rotation,
                        _ => continue,
                    };
                    cn_to_model.insert(child_id, (parent_id, cp));
                }
                if curves.contains_key(&child_id) && curve_node_ids.contains(&parent_id) {
                    let axis = match prop_name {
                        "d|X" => 0u8,
                        "d|Y" => 1u8,
                        "d|Z" => 2u8,
                        _ => continue,
                    };
                    curve_to_cn.insert(child_id, (parent_id, axis));
                }
            }
        } else if conn_type == "OO" {
            if curves.contains_key(&child_id) && curve_node_ids.contains(&parent_id) {
                if !curve_to_cn.contains_key(&child_id) {
                    let count = curve_to_cn.values().filter(|(cn, _)| *cn == parent_id).count();
                    if count < 3 {
                        curve_to_cn.insert(child_id, (parent_id, count as u8));
                    }
                }
            }
        }
    }

    // 骨骼动画曲线索引：model_id → [X/Y/Z 轴曲线 ID]
    let mut bone_trans: HashMap<i64, [Option<i64>; 3]> = HashMap::new();
    let mut bone_rot: HashMap<i64, [Option<i64>; 3]> = HashMap::new();

    for (&curve_id, &(cn_id, axis)) in &curve_to_cn {
        if let Some(&(model_id, ref prop)) = cn_to_model.get(&cn_id) {
            let idx = axis as usize;
            if idx >= 3 { continue; }
            match prop {
                ConnProperty::Translation => {
                    bone_trans.entry(model_id).or_insert([None; 3])[idx] = Some(curve_id);
                }
                ConnProperty::Rotation => {
                    bone_rot.entry(model_id).or_insert([None; 3])[idx] = Some(curve_id);
                }
            }
        }
    }

    let mut motion = Motion::new();
    let all_model_ids: BTreeSet<i64> = bone_trans.keys().chain(bone_rot.keys()).copied().collect();

    for model_id in &all_model_ids {
        let model = match models.get(model_id) { Some(m) => m, None => continue };
        let bone_name = map_fbx_bone_name(&model.name);
        log::debug!("FBX bone: '{}' → MMD: '{}'", model.name, bone_name);

        let trans_curves = bone_trans.get(model_id);
        let rot_curves = bone_rot.get(model_id);

        // 世界静息旋转（旋转重定向用）和父骨骼世界静息旋转（平移转换用）
        let world_rest = world_rest_rot.get(model_id).copied().unwrap_or(Quat::IDENTITY);
        let parent_rest = model_parent.get(model_id)
            .and_then(|pid| world_rest_rot.get(pid))
            .copied()
            .unwrap_or(Quat::IDENTITY);
        let q_default = euler_to_quat(
            model.default_rotation.x,
            model.default_rotation.y,
            model.default_rotation.z,
            model.rotation_order,
        );

        let mut all_times: BTreeSet<i64> = BTreeSet::new();
        collect_curve_times(trans_curves, &curves, &mut all_times);
        collect_curve_times(rot_curves, &curves, &mut all_times);

        if all_times.is_empty() { continue; }

        for &time in &all_times {
            let frame_index = fbx_time_to_frame(time);

            let tx = sample_axis(trans_curves, 0, time, &curves, model.default_translation.x);
            let ty = sample_axis(trans_curves, 1, time, &curves, model.default_translation.y);
            let tz = sample_axis(trans_curves, 2, time, &curves, model.default_translation.z);
            let rel_t = Vec3::new(
                tx - model.default_translation.x,
                ty - model.default_translation.y,
                tz - model.default_translation.z,
            );

            let rx = sample_axis(rot_curves, 0, time, &curves, model.default_rotation.x);
            let ry = sample_axis(rot_curves, 1, time, &curves, model.default_rotation.y);
            let rz = sample_axis(rot_curves, 2, time, &curves, model.default_rotation.z);
            let q_anim = euler_to_quat(rx, ry, rz, model.rotation_order);
            let delta = q_default.inverse() * q_anim;

            let orientation = world_rest * delta * world_rest.conjugate();
            let rel_t = parent_rest * rel_t;

            let (translation, orientation) = if is_z_up {
                let t = Vec3::new(rel_t.x, rel_t.z, -rel_t.y);
                let q = Quat::from_xyzw(
                    orientation.x, orientation.z, -orientation.y, orientation.w,
                ).normalize();
                (t, q)
            } else {
                (rel_t, orientation.normalize())
            };

            let keyframe = BoneKeyframe::with_transform(frame_index, translation, orientation);
            motion.insert_bone_keyframe(&bone_name, keyframe);
        }
    }

    // 禁用 IK（FBX 为纯 FK 数据）
    const MMD_IK_BONES: &[&str] = &[
        "左足ＩＫ", "右足ＩＫ", "左つま先ＩＫ", "右つま先ＩＫ",
    ];
    for &ik_name in MMD_IK_BONES {
        motion.insert_ik_keyframe(ik_name, IkKeyframe::new(0, ik_name.to_string(), false));
    }

    if motion.bone_tracks.is_empty() {
        log::warn!("FBX 文件未包含可识别的骨骼动画数据");
    }

    Ok(motion)
}

/// 解析 Model 节点，提取 ID、名称、默认变换
fn parse_model_node(node: &FbxNode) -> Option<(i64, FbxModel)> {
    let id = node.properties.first()?.as_i64()?;
    let raw_name = node.properties.get(1).and_then(|p| p.as_string()).unwrap_or("");
    let name = clean_fbx_name(raw_name);

    let subtype = node.properties.get(2)
        .and_then(|p| p.as_string())
        .unwrap_or("");
    let is_bone = matches!(subtype, "LimbNode" | "Root");

    let mut default_t = Vec3::ZERO;
    let mut default_r = Vec3::ZERO;
    let mut pre_r = Vec3::ZERO;
    let mut rot_order: u8 = 0;

    if let Some(p70) = node.find_child("Properties70") {
        for p in &p70.children {
            if p.name != "P" || p.properties.len() < 5 { continue; }
            let prop_name = match p.properties[0].as_string() { Some(s) => s, None => continue };
            match prop_name {
                "Lcl Translation" => {
                    if p.properties.len() >= 7 {
                        default_t.x = p.properties[4].as_f64().unwrap_or(0.0) as f32;
                        default_t.y = p.properties[5].as_f64().unwrap_or(0.0) as f32;
                        default_t.z = p.properties[6].as_f64().unwrap_or(0.0) as f32;
                    }
                }
                "Lcl Rotation" => {
                    if p.properties.len() >= 7 {
                        default_r.x = p.properties[4].as_f64().unwrap_or(0.0) as f32;
                        default_r.y = p.properties[5].as_f64().unwrap_or(0.0) as f32;
                        default_r.z = p.properties[6].as_f64().unwrap_or(0.0) as f32;
                    }
                }
                "PreRotation" => {
                    if p.properties.len() >= 7 {
                        pre_r.x = p.properties[4].as_f64().unwrap_or(0.0) as f32;
                        pre_r.y = p.properties[5].as_f64().unwrap_or(0.0) as f32;
                        pre_r.z = p.properties[6].as_f64().unwrap_or(0.0) as f32;
                    }
                }
                "RotationOrder" => {
                    rot_order = p.properties[4].as_f64().unwrap_or(0.0) as u8;
                }
                _ => {}
            }
        }
    }

    Some((id, FbxModel {
        name,
        default_translation: default_t,
        default_rotation: default_r,
        pre_rotation: pre_r,
        rotation_order: rot_order,
        is_bone,
    }))
}

/// 解析 AnimationCurve 节点
fn parse_animation_curve(node: &FbxNode) -> Option<(i64, FbxCurve)> {
    let id = node.properties.first()?.as_i64()?;

    let key_times: Vec<i64> = node.find_child("KeyTime")
        .and_then(|n| n.properties.first())
        .and_then(|p| p.as_i64_array())
        .map(|a| a.to_vec())
        .unwrap_or_default();

    let key_values: Vec<f32> = node.find_child("KeyValueFloat")
        .and_then(|n| n.properties.first())
        .and_then(|p| {
            if let Some(f32s) = p.as_f32_array() {
                Some(f32s.to_vec())
            } else if let Some(f64s) = p.as_f64_array() {
                Some(f64s.iter().map(|&v| v as f32).collect())
            } else {
                None
            }
        })
        .unwrap_or_default();

    if key_times.is_empty() || key_times.len() != key_values.len() {
        return None;
    }

    Some((id, FbxCurve { key_times, key_values }))
}

/// 收集曲线组中所有时间点
fn collect_curve_times(
    curve_refs: Option<&[Option<i64>; 3]>,
    curves: &HashMap<i64, FbxCurve>,
    out: &mut BTreeSet<i64>,
) {
    if let Some(refs) = curve_refs {
        for cid in refs.iter().flatten() {
            if let Some(curve) = curves.get(cid) {
                out.extend(&curve.key_times);
            }
        }
    }
}

/// 在指定时间采样某轴曲线值（线性插值），无曲线时返回默认值
fn sample_axis(
    curve_refs: Option<&[Option<i64>; 3]>,
    axis: usize,
    time: i64,
    curves: &HashMap<i64, FbxCurve>,
    default_value: f32,
) -> f32 {
    let cid = curve_refs
        .and_then(|r| r.get(axis))
        .and_then(|o| o.as_ref());
    let curve = match cid.and_then(|id| curves.get(id)) {
        Some(c) => c,
        None => return default_value,
    };
    interpolate_curve(curve, time)
}

/// 线性插值曲线在指定时间的值
fn interpolate_curve(curve: &FbxCurve, time: i64) -> f32 {
    if curve.key_times.is_empty() { return 0.0; }
    if time <= curve.key_times[0] { return curve.key_values[0]; }
    let last = curve.key_times.len() - 1;
    if time >= curve.key_times[last] { return curve.key_values[last]; }

    let mut lo = 0usize;
    let mut hi = last;
    while lo + 1 < hi {
        let mid = (lo + hi) / 2;
        if curve.key_times[mid] <= time { lo = mid; } else { hi = mid; }
    }

    let t0 = curve.key_times[lo];
    let t1 = curve.key_times[hi];
    if t1 == t0 { return curve.key_values[lo]; }
    let alpha = (time - t0) as f32 / (t1 - t0) as f32;
    curve.key_values[lo] + (curve.key_values[hi] - curve.key_values[lo]) * alpha
}

/// FBX 时间 → 30fps 帧号
fn fbx_time_to_frame(time: i64) -> u32 {
    let seconds = time as f64 / FBX_TICKS_PER_SECOND;
    (seconds * VMD_FPS).round().max(0.0) as u32
}

/// 欧拉角（角度制）→ 四元数，按指定旋转顺序
fn euler_to_quat(ex: f32, ey: f32, ez: f32, rotation_order: u8) -> Quat {
    let rx = ex.to_radians();
    let ry = ey.to_radians();
    let rz = ez.to_radians();

    let qx = Quat::from_rotation_x(rx);
    let qy = Quat::from_rotation_y(ry);
    let qz = Quat::from_rotation_z(rz);

    match rotation_order {
        0 => qz * qy * qx, // XYZ
        1 => qy * qz * qx, // XZY
        2 => qx * qz * qy, // YZX
        3 => qz * qx * qy, // YXZ
        4 => qy * qx * qz, // ZXY
        5 => qx * qy * qz, // ZYX
        _ => qz * qy * qx,
    }
}

/// 列出 FBX 文件中所有 AnimationStack 名称（无缓存版本）
pub fn list_fbx_stacks<P: AsRef<Path>>(path: P) -> Result<Vec<String>> {
    let file = File::open(path.as_ref())
        .map_err(|e| MmdError::FbxParse(format!("打开文件失败: {}", e)))?;
    let mut reader = BufReader::new(file);
    let nodes = parse_fbx(&mut reader)?;
    Ok(extract_stack_names(&nodes))
}

/// 从已解析的节点树提取 AnimationStack 名称
fn extract_stack_names(nodes: &[FbxNode]) -> Vec<String> {
    let objects = match nodes.iter().find(|n| n.name == "Objects") {
        Some(o) => o,
        None => return Vec::new(),
    };
    let mut names = Vec::new();
    for child in &objects.children {
        if child.name == "AnimationStack" {
            let name = child.properties.get(1)
                .and_then(|p| p.as_string())
                .map(|s| clean_fbx_name(s))
                .unwrap_or_default();
            if !name.is_empty() {
                names.push(name);
            }
        }
    }
    names
}

/// 清理 FBX 节点名称（去掉 "Model::" 前缀和 NUL 后缀）
fn clean_fbx_name(raw: &str) -> String {
    let s = raw.split('\0').next().unwrap_or(raw);
    let s = s.strip_prefix("Model::").unwrap_or(s);
    s.to_string()
}

/// FBX T-pose → PMX A-pose 手臂姿态校正
///
/// 对上臂关键帧右乘校正四元数，将 A-pose 方向映射到 T-pose 后再应用动画。
/// FK 链自然传播校正到子骨骼，保持肘部角度一致。
pub fn apply_arm_retarget_correction(
    animation: &mut VmdAnimation,
    bone_positions: &HashMap<String, Vec3>,
) {
    let arm_bones: &[(&str, &str, Vec3)] = &[
        ("左腕", "左ひじ", Vec3::new(1.0, 0.0, 0.0)),
        ("右腕", "右ひじ", Vec3::new(-1.0, 0.0, 0.0)),
    ];

    for &(upper, child, tpose_dir) in arm_bones {
        let upper_pos = match bone_positions.get(upper) { Some(p) => *p, None => continue };
        let child_pos = match bone_positions.get(child) { Some(p) => *p, None => continue };

        let diff = child_pos - upper_pos;
        if diff.length_squared() < 1e-6 { continue; }
        let pmx_dir = diff.normalize();

        if pmx_dir.dot(tpose_dir) > 0.995 { continue; }

        let correction = Quat::from_rotation_arc(pmx_dir, tpose_dir);

        log::info!(
            "手臂姿态校正: {} pmx_dir=({:.3},{:.3},{:.3}) angle={:.1}°",
            upper, pmx_dir.x, pmx_dir.y, pmx_dir.z,
            correction.to_axis_angle().1.to_degrees()
        );

        if let Some(track) = animation.motion_mut().bone_tracks.get_mut(upper) {
            for kf in track.keyframes.values_mut() {
                kf.orientation = (kf.orientation * correction).normalize();
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_load_ual2_fbx() {
        let path = r"D:\GITHUB\work\MC-MMD-rust\docs\Universal Animation Library 2[Standard]\Unity\UAL2_Standard.fbx";
        if !std::path::Path::new(path).exists() {
            eprintln!("测试文件不存在，跳过: {}", path);
            return;
        }

        // 解析 FBX 节点树
        let file = File::open(path).expect("打开文件失败");
        let mut reader = BufReader::new(file);
        let nodes = parse_fbx(&mut reader).expect("FBX 解析失败");

        eprintln!("=== 顶层节点 ===");
        for n in &nodes {
            eprintln!("  {} (属性数: {}, 子节点数: {})", n.name, n.properties.len(), n.children.len());
        }

        // 查找 Objects
        let objects = nodes.iter().find(|n| n.name == "Objects").expect("无 Objects 节点");
        let mut model_count = 0;
        let mut curve_count = 0;
        let mut curve_node_count = 0;
        let mut anim_stack_count = 0;

        for child in &objects.children {
            match child.name.as_str() {
                "Model" => {
                    model_count += 1;
                    if let Some((_, m)) = parse_model_node(child) {
                        eprintln!("  Model: '{}' defT={:?} defR={:?} rotOrder={}", 
                            m.name, m.default_translation, m.default_rotation, m.rotation_order);
                    }
                }
                "AnimationCurve" => curve_count += 1,
                "AnimationCurveNode" => curve_node_count += 1,
                "AnimationStack" => anim_stack_count += 1,
                _ => {}
            }
        }

        eprintln!("\n=== 统计 ===");
        eprintln!("  Models: {}", model_count);
        eprintln!("  AnimationCurves: {}", curve_count);
        eprintln!("  AnimationCurveNodes: {}", curve_node_count);
        eprintln!("  AnimationStacks: {}", anim_stack_count);

        // 测试骨骼名映射
        eprintln!("\n=== 骨骼名映射测试 ===");
        let test_names = ["pelvis", "spine_01", "clavicle_l", "upperarm_l", "thigh_r", "foot_l", "index_01_l"];
        for name in &test_names {
            eprintln!("  {} → {}", name, map_fbx_bone_name(name));
        }

        // 列出所有 AnimationStack 名称
        eprintln!("\n=== AnimationStack 列表 ===");
        for child in &objects.children {
            if child.name == "AnimationStack" {
                let name = child.properties.get(1)
                    .and_then(|p| p.as_string())
                    .map(|s| clean_fbx_name(s))
                    .unwrap_or_default();
                eprintln!("  '{}'", name);
            }
        }

        // 加载第一个 Stack（默认）
        let anim = load_fbx_animation(path, None).expect("默认加载失败");
        eprintln!("\n=== 默认 Stack ===");
        eprintln!("  max_frame: {}, 骨骼轨道: {}", anim.max_frame(), anim.bone_track_names().len());

        // 按名称加载指定 Stack
        let anim2 = load_fbx_animation(path, Some("Idle"));
        match &anim2 {
            Ok(a) => eprintln!("=== Stack 'Idle' === max_frame: {}, 轨道: {}", a.max_frame(), a.bone_track_names().len()),
            Err(e) => eprintln!("=== Stack 'Idle' 加载失败: {}", e),
        }

        let anim3 = load_fbx_animation(path, Some("Walk"));
        match &anim3 {
            Ok(a) => eprintln!("=== Stack 'Walk' === max_frame: {}, 轨道: {}", a.max_frame(), a.bone_track_names().len()),
            Err(e) => eprintln!("=== Stack 'Walk' 加载失败: {}", e),
        }

        // 测试不存在的名称
        let anim4 = load_fbx_animation(path, Some("NotExist"));
        assert!(anim4.is_err(), "不存在的 Stack 应返回错误");
        eprintln!("=== 'NotExist' 正确返回错误 ===");
    }
}
