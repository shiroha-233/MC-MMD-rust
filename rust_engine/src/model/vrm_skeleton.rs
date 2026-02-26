//! glTF skin/nodes → BoneSet 骨骼系统转换（含 MMD 虚拟骨骼注入）

use std::collections::HashMap;
use glam::{Mat4, Vec3};

use crate::skeleton::{BoneLink, BoneFlags, BoneSet};
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
/// 在 glTF 骨骼前插入 3 个 MMD 虚拟骨骼：
/// [0] 全ての親  (parent=-1, pos=origin)
/// [1] センター  (parent=0, pos=hips)
/// [2] グルーブ  (parent=1, pos=hips)
/// [3..] glTF joints（索引偏移 VIRTUAL_BONE_COUNT）
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

    // 找到 hips 骨骼的世界位置（用于虚拟骨骼定位）
    let hips_pos = find_hips_world_pos(humanoid_mapping, &joint_nodes, &ibms, &node_world_transforms);

    let mut bone_set = BoneSet::new();

    // 插入 MMD 虚拟骨骼
    inject_virtual_bones(&mut bone_set, hips_pos);

    // 转换 glTF joints（索引偏移 VIRTUAL_BONE_COUNT）
    let hips_joint_idx = find_hips_joint_index(humanoid_mapping, &joint_nodes);

    for (joint_idx, joint_node) in skin.joints().enumerate() {
        let node_idx = joint_node.index();

        let name = match humanoid_mapping.map.get(&node_idx) {
            Some(vrm_name) => bone_mapping::vrm_to_mmd_bone_name(vrm_name).to_string(),
            None => joint_node.name().unwrap_or("unknown").to_string(),
        };

        let mut bone = BoneLink::new(name);

        let world_pos = if joint_idx < ibms.len() {
            let bind_matrix = ibms[joint_idx].inverse();
            Vec3::new(bind_matrix.col(3).x, bind_matrix.col(3).y, -bind_matrix.col(3).z)
        } else {
            let wt = node_world_transforms.get(&node_idx).copied().unwrap_or(Mat4::IDENTITY);
            Vec3::new(wt.col(3).x, wt.col(3).y, -wt.col(3).z)
        };

        bone.initial_position = world_pos * VRM_TO_PMX_SCALE;

        // hips → parent 指向 グルーブ(idx=2)，其余骨骼 parent 偏移 VIRTUAL_BONE_COUNT
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

    bone_set.build_hierarchy();

    Ok(bone_set)
}

/// 插入 MMD 虚拟骨骼（全ての親、センター、グルーブ）
fn inject_virtual_bones(bone_set: &mut BoneSet, hips_pos: Vec3) {
    // 全ての親：原点
    let mut master = BoneLink::new("全ての親".to_string());
    master.initial_position = Vec3::ZERO;
    master.parent_index = -1;
    master.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(master);

    // センター：hips 位置
    let mut center = BoneLink::new("センター".to_string());
    center.initial_position = hips_pos;
    center.parent_index = 0;
    center.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(center);

    // グルーブ：hips 位置
    let mut groove = BoneLink::new("グルーブ".to_string());
    groove.initial_position = hips_pos;
    groove.parent_index = 1;
    groove.flags = BoneFlags::ROTATABLE | BoneFlags::MOVABLE;
    bone_set.add_bone(groove);
}

/// 查找 hips 骨骼的世界空间位置（已缩放）
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
                    Vec3::new(bind_matrix.col(3).x, bind_matrix.col(3).y, -bind_matrix.col(3).z)
                } else {
                    let wt = node_world_transforms.get(&node_idx).copied().unwrap_or(Mat4::IDENTITY);
                    Vec3::new(wt.col(3).x, wt.col(3).y, -wt.col(3).z)
                };
                return world_pos * VRM_TO_PMX_SCALE;
            }
        }
    }
    Vec3::ZERO
}

/// 查找 hips 在 joint 数组中的索引
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
