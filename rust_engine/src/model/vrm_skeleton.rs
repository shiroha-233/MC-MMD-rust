//! glTF skin/nodes → BoneSet 骨骼系统转换（含 MMD 虚拟骨骼 + IK 骨骼注入）

use std::collections::HashMap;
use glam::{Mat4, Quat, Vec3};

use crate::skeleton::{BoneLink, BoneFlags, BoneSet, IkLink, IkConfig};
use super::vrm_extensions::HumanoidMapping;
use super::bone_mapping;

/// VRM→PMX 单位缩放（1m → 12.5 PMX 单位）
const VRM_TO_PMX_SCALE: f32 = 12.5;

/// MMD 虚拟骨骼数量（全ての親 + センター + グルーブ）
pub(crate) const VIRTUAL_BONE_COUNT: usize = 3;


fn build_parent_map(document: &gltf::Document) -> HashMap<usize, usize> {
    let mut parent_map = HashMap::new();
    for node in document.nodes() {
        for child in node.children() {
            parent_map.insert(child.index(), node.index());
        }
    }
    parent_map
}

fn compute_node_world_transforms(document: &gltf::Document) -> HashMap<usize, Mat4> {
    let mut world_transforms = HashMap::new();
    let parent_map = build_parent_map(document);
    let nodes: Vec<_> = document.nodes().collect();
    for node in &nodes {
        compute_world_transform_recursive(node.index(), &nodes, &parent_map, &mut world_transforms);
    }
    world_transforms
}

fn compute_world_transform_recursive(
    node_idx: usize,
    nodes: &[gltf::Node],
    parent_map: &HashMap<usize, usize>,
    cache: &mut HashMap<usize, Mat4>,
) -> Mat4 {
    if let Some(&cached) = cache.get(&node_idx) {
        return cached;
    }
    let local = Mat4::from_cols_array_2d(&nodes[node_idx].transform().matrix());
    let world = match parent_map.get(&node_idx) {
        Some(&parent_idx) => {
            let parent_world = compute_world_transform_recursive(parent_idx, nodes, parent_map, cache);
            parent_world * local
        }
        None => local,
    };
    cache.insert(node_idx, world);
    world
}

/// 从 glTF skin + node hierarchy 构建 BoneSet
///
/// 骨骼布局：
/// [0] 全ての親  [1] センター  [2] グルーブ
/// [3..3+N] glTF joints
/// [3+N..3+N+4] IK 骨骼（左足ＩＫ, 右足ＩＫ, 左つま先ＩＫ, 右つま先ＩＫ）
pub(crate) fn build_skeleton(
    document: &gltf::Document,
    skin: &gltf::Skin,
    buffers: &[gltf::buffer::Data],
    humanoid_mapping: &HumanoidMapping,
) -> crate::Result<BoneSet> {
    let parent_map = build_parent_map(document);

    let joint_nodes: Vec<usize> = skin.joints().map(|n| n.index()).collect();
    let node_to_joint: HashMap<usize, usize> = joint_nodes.iter().enumerate()
        .map(|(joint_idx, &node_idx)| (node_idx, joint_idx))
        .collect();

    let node_world_transforms = compute_node_world_transforms(document);

    let reader = skin.reader(|buffer| Some(&buffers[buffer.index()]));
    let ibms: Vec<Mat4> = reader.read_inverse_bind_matrices()
        .map(|iter| iter.map(|m| Mat4::from_cols_array_2d(&m)).collect())
        .unwrap_or_default();

    let hips_pos = find_hips_world_pos(humanoid_mapping, &joint_nodes, &ibms, &node_world_transforms);

    let mut bone_set = BoneSet::new();
    bone_set.set_vrm(true);

    inject_virtual_bones(&mut bone_set, hips_pos);

    let hips_joint_idx = find_hips_joint_index(humanoid_mapping, &joint_nodes);

    for (joint_idx, joint_node) in skin.joints().enumerate() {
        let node_idx = joint_node.index();

        let name = match humanoid_mapping.map.get(&node_idx) {
            Some(vrm_name) => bone_mapping::vrm_to_mmd_bone_name(vrm_name).to_string(),
            None => joint_node.name().unwrap_or("unknown").to_string(),
        };

        let mut bone = BoneLink::new(name);

        // X-flip + Z-flip 与 PMX 统一坐标空间
        let world_pos = if joint_idx < ibms.len() {
            let bind_matrix = ibms[joint_idx].inverse();
            Vec3::new(-bind_matrix.col(3).x, bind_matrix.col(3).y, -bind_matrix.col(3).z)
        } else {
            let wt = node_world_transforms.get(&node_idx).copied().unwrap_or(Mat4::IDENTITY);
            Vec3::new(-wt.col(3).x, wt.col(3).y, -wt.col(3).z)
        };

        bone.initial_position = world_pos * VRM_TO_PMX_SCALE;

        if Some(joint_idx) == hips_joint_idx {
            bone.parent_index = 2; // グルーブ
        } else {
            bone.parent_index = parent_map.get(&node_idx)
                .and_then(|&parent_node| node_to_joint.get(&parent_node))
                .map(|&idx| (idx + VIRTUAL_BONE_COUNT) as i32)
                .unwrap_or(-1);
        }

        bone.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
        bone_set.add_bone(bone);
    }

    // 注入 IK 骨骼（在所有 glTF 骨骼之后）
    inject_ik_bones(&mut bone_set);

    bone_set.build_hierarchy();

    // T-pose → A-pose 手臂静息旋转修正
    apply_arm_rest_rotations(&mut bone_set);

    Ok(bone_set)
}

/// 注入 MMD 足 IK 骨骼
///
/// VMD 动画通过 IK 骨骼驱动脚部运动：
/// - 左足ＩＫ: IK 链 = [左ひざ(角度限制), 左足] → 目标 = 左足首
/// - 右足ＩＫ: IK 链 = [右ひざ(角度限制), 右足] → 目标 = 右足首
/// - 左つま先ＩＫ: IK 链 = [左足首] → 目标 = 左つま先
/// - 右つま先ＩＫ: IK 链 = [右足首] → 目標 = 右つま先
fn inject_ik_bones(bone_set: &mut BoneSet) {
    let ik_defs = [
        ("左足ＩＫ", "左足首", &["左ひざ", "左足"][..], "全ての親"),
        ("右足ＩＫ", "右足首", &["右ひざ", "右足"][..], "全ての親"),
        ("左つま先ＩＫ", "左つま先", &["左足首"][..], "左足ＩＫ"),
        ("右つま先ＩＫ", "右つま先", &["右足首"][..], "右足ＩＫ"),
    ];

    for (ik_name, target_name, chain_names, parent_name) in &ik_defs {
        let target_idx = match bone_set.find_bone_by_name(target_name) {
            Some(idx) => idx,
            None => {
                log::warn!("IK 注入跳过 {}: 目标骨骼 {} 未找到", ik_name, target_name);
                continue;
            }
        };

        let target_pos = bone_set.get_bone(target_idx)
            .map(|b| b.initial_position)
            .unwrap_or(Vec3::ZERO);

        let parent_idx = bone_set.find_bone_by_name(parent_name)
            .map(|idx| idx as i32)
            .unwrap_or(0);

        let links: Vec<IkLink> = chain_names.iter().filter_map(|&name| {
            bone_set.find_bone_by_name(name).map(|idx| {
                let is_knee = name.contains("ひざ");
                IkLink {
                    bone_index: idx as i32,
                    has_limits: is_knee,
                    // X+Z flip 后膝盖弯曲方向为 X 轴正方向
                    limit_min: if is_knee { Vec3::new(0.008726646, 0.0, 0.0) } else { Vec3::ZERO },
                    limit_max: if is_knee { Vec3::new(std::f32::consts::PI, 0.0, 0.0) } else { Vec3::ZERO },
                }
            })
        }).collect();

        if links.is_empty() {
            log::warn!("IK 注入跳过 {}: IK 链骨骼未找到", ik_name);
            continue;
        }

        let mut ik_bone = BoneLink::new(ik_name.to_string());
        ik_bone.initial_position = target_pos;
        ik_bone.parent_index = parent_idx;
        ik_bone.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE | BoneFlags::IK;
        ik_bone.ik_config = Some(IkConfig {
            target_bone: target_idx as i32,
            iterations: 40,
            limit_angle: 2.0_f32.to_radians(),
            links,
        });

        // 标记 IK 链骨骼启用 IK
        for name in *chain_names {
            if let Some(idx) = bone_set.find_bone_by_name(name) {
                if let Some(bone) = bone_set.get_bone_mut(idx) {
                    bone.flags.insert(BoneFlags::IK_ENABLED);
                }
            }
        }

        bone_set.add_bone(ik_bone);
        log::info!("IK 注入: {} → 目标={} 链长={}", ik_name, target_name, chain_names.len());
    }
}

fn inject_virtual_bones(bone_set: &mut BoneSet, hips_pos: Vec3) {
    let mut master = BoneLink::new("全ての親".to_string());
    master.initial_position = Vec3::ZERO;
    master.parent_index = -1;
    master.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(master);

    let mut center = BoneLink::new("センター".to_string());
    center.initial_position = hips_pos;
    center.parent_index = 0;
    center.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(center);

    let mut groove = BoneLink::new("グルーブ".to_string());
    groove.initial_position = hips_pos;
    groove.parent_index = 1;
    groove.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(groove);
}

fn find_hips_world_pos(
    humanoid_mapping: &HumanoidMapping,
    joint_nodes: &[usize],
    ibms: &[Mat4],
    node_world_transforms: &HashMap<usize, Mat4>,
) -> Vec3 {
    for (&node_idx, vrm_name) in &humanoid_mapping.map {
        if vrm_name == "hips" {
            if let Some(joint_idx) = joint_nodes.iter().position(|&n| n == node_idx) {
                let world_pos = if joint_idx < ibms.len() {
                    let bind_matrix = ibms[joint_idx].inverse();
                    Vec3::new(-bind_matrix.col(3).x, bind_matrix.col(3).y, -bind_matrix.col(3).z)
                } else {
                    let wt = node_world_transforms.get(&node_idx).copied().unwrap_or(Mat4::IDENTITY);
                    Vec3::new(-wt.col(3).x, wt.col(3).y, -wt.col(3).z)
                };
                return world_pos * VRM_TO_PMX_SCALE;
            }
        }
    }
    Vec3::ZERO
}

const ARM_REST_ANGLE_DEG: f32 = 35.0;

fn apply_arm_rest_rotations(bone_set: &mut BoneSet) {
    apply_single_arm_rest(bone_set, "左腕", "左ひじ", true);
    apply_single_arm_rest(bone_set, "右腕", "右ひじ", false);
    propagate_parent_rest_rotation(bone_set);
}

fn apply_single_arm_rest(bone_set: &mut BoneSet, upper_name: &str, lower_name: &str, is_left: bool) {
    let upper_idx = match bone_set.find_bone_by_name(upper_name) { Some(i) => i, None => return };
    let lower_idx = match bone_set.find_bone_by_name(lower_name) { Some(i) => i, None => return };

    let upper_pos = bone_set.get_bone(upper_idx).unwrap().initial_position;
    let lower_pos = bone_set.get_bone(lower_idx).unwrap().initial_position;

    let diff = lower_pos - upper_pos;
    let horizontal_len = (diff.x * diff.x + diff.z * diff.z).sqrt();

    if horizontal_len < 0.001 {
        return;
    }

    let current_angle = diff.y.atan2(horizontal_len);
    let target_angle = -ARM_REST_ANGLE_DEG.to_radians();
    let correction = target_angle - current_angle;

    if correction.abs() < 3.0_f32.to_radians() {
        return;
    }

    let z_angle = if is_left { correction } else { -correction };
    let rotation = Quat::from_axis_angle(Vec3::Z, z_angle);

    if let Some(bone) = bone_set.get_bone_mut(upper_idx) {
        bone.rest_rotation = rotation;
    }

    log::info!(
        "VRM 手臂修正: {} 当前={:.1}° 目标={:.1}° 修正={:.1}°",
        upper_name, current_angle.to_degrees(), target_angle.to_degrees(), correction.to_degrees()
    );
}

fn propagate_parent_rest_rotation(bone_set: &mut BoneSet) {
    let bone_count = bone_set.bone_count();

    for i in 0..bone_count {
        let parent_idx = match bone_set.get_bone(i) {
            Some(b) => b.parent_index,
            None => continue,
        };
        if parent_idx < 0 || parent_idx as usize >= bone_count {
            continue;
        }
        let pi = parent_idx as usize;
        let parent_accumulated = bone_set.get_bone(pi).unwrap().parent_rest_rotation;
        let parent_rest = bone_set.get_bone(pi).unwrap().rest_rotation;
        let accumulated = parent_accumulated * parent_rest;

        if accumulated != Quat::IDENTITY {
            if let Some(bone) = bone_set.get_bone_mut(i) {
                bone.parent_rest_rotation = accumulated;
            }
        }
    }
}

fn find_hips_joint_index(
    humanoid_mapping: &HumanoidMapping,
    joint_nodes: &[usize],
) -> Option<usize> {
    for (&node_idx, vrm_name) in &humanoid_mapping.map {
        if vrm_name == "hips" {
            return joint_nodes.iter().position(|&n| n == node_idx);
        }
    }
    None
}
