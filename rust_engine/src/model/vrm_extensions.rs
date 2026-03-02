//! VRM 扩展 JSON 解析（humanoid / expressions / materials）

use std::collections::HashMap;
use gltf::json as gltf_json;
use serde_json::Value;
use crate::MmdError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VrmVersion {
    V0x,
    V1_0,
}

/// VRM 扩展统一数据结构
#[derive(Debug, Clone)]
pub struct VrmExtensions {
    pub version: VrmVersion,
    pub humanoid: HumanoidMapping,
    pub expressions: VrmExpressions,
    pub mtoon_materials: Vec<Option<MtoonParams>>,
    pub spring_bones: SpringBoneData,
}

/// node_index → VRM 骨骼名
#[derive(Debug, Clone, Default)]
pub struct HumanoidMapping {
    pub map: HashMap<usize, String>,
}

/// 表情名 → morph target 权重组合 (morph_target_index, weight)
#[derive(Debug, Clone, Default)]
pub struct VrmExpressions {
    pub map: HashMap<String, Vec<(usize, f32)>>,
}


/// 碰撞器形状
#[derive(Debug, Clone)]
pub enum ColliderShape {
    Sphere { offset: [f32; 3], radius: f32 },
    Capsule { offset: [f32; 3], tail: [f32; 3], radius: f32 },
}

/// 碰撞器（绑定到某个 node）
#[derive(Debug, Clone)]
pub struct SpringBoneCollider {
    pub node: usize,
    pub shape: ColliderShape,
}

/// 碰撞器组
#[derive(Debug, Clone)]
pub struct SpringBoneColliderGroup {
    pub colliders: Vec<usize>,
}

/// 弹簧关节参数
#[derive(Debug, Clone)]
pub struct SpringBoneJoint {
    pub node: usize,
    pub hit_radius: f32,
    pub stiffness: f32,
    pub gravity_power: f32,
    pub gravity_dir: [f32; 3],
    pub drag_force: f32,
}

/// 弹簧链
#[derive(Debug, Clone)]
pub struct SpringBoneSpring {
    pub joints: Vec<SpringBoneJoint>,
    pub collider_groups: Vec<usize>,
    pub center: Option<usize>,
}

/// Spring Bone 完整数据
#[derive(Debug, Clone, Default)]
pub struct SpringBoneData {
    pub springs: Vec<SpringBoneSpring>,
    pub colliders: Vec<SpringBoneCollider>,
    pub collider_groups: Vec<SpringBoneColliderGroup>,
}

/// MToon 着色器参数
#[derive(Debug, Clone)]
pub struct MtoonParams {
    pub shade_color_factor: [f32; 3],
    pub shading_shift_factor: f32,
    pub outline_color_factor: [f32; 3],
    pub outline_width_factor: f32,
}

impl Default for MtoonParams {
    fn default() -> Self {
        Self {
            shade_color_factor: [0.8, 0.8, 0.8],
            shading_shift_factor: 0.0,
            outline_color_factor: [0.0, 0.0, 0.0],
            outline_width_factor: 0.002,
        }
    }
}

/// 从 glTF Document 解析 VRM 扩展（自动检测版本）
pub fn parse_vrm_extensions(document: &gltf::Document) -> crate::Result<VrmExtensions> {
    let json_root = document.as_json();
    let extensions = json_root.extensions.as_ref()
        .ok_or_else(|| MmdError::VrmParse("缺少 glTF extensions 字段".into()))?;

    if extensions.others.contains_key("VRMC_vrm") {
        parse_vrm_1_0(&extensions.others, json_root)
    } else if extensions.others.contains_key("VRM") {
        parse_vrm_0x(&extensions.others, json_root)
    } else {
        Err(MmdError::VrmParse("未找到 VRM 或 VRMC_vrm 扩展".into()))
    }
}

/// VRM 0.x 解析
fn parse_vrm_0x(
    others: &serde_json::Map<String, Value>,
    json_root: &gltf_json::Root,
) -> crate::Result<VrmExtensions> {
    let vrm = others.get("VRM")
        .ok_or_else(|| MmdError::VrmParse("缺少 VRM 扩展字段".into()))?;

    let humanoid = parse_humanoid_0x(vrm)?;
    let expressions = parse_expressions_0x(vrm)?;
    let mtoon_materials = parse_mtoon_0x(vrm, json_root.materials.len());
    let spring_bones = parse_spring_bones_0x(vrm, json_root);

    Ok(VrmExtensions {
        version: VrmVersion::V0x,
        humanoid,
        expressions,
        mtoon_materials,
        spring_bones,
    })
}

/// VRM 1.0 解析
fn parse_vrm_1_0(
    others: &serde_json::Map<String, Value>,
    json_root: &gltf_json::Root,
) -> crate::Result<VrmExtensions> {
    let vrmc = others.get("VRMC_vrm")
        .ok_or_else(|| MmdError::VrmParse("缺少 VRMC_vrm 扩展字段".into()))?;

    let humanoid = parse_humanoid_1_0(vrmc)?;
    let expressions = parse_expressions_1_0(vrmc)?;
    let mtoon_materials = parse_mtoon_1_0(json_root);
    let spring_bones = parse_spring_bones_1_0(others, json_root);

    Ok(VrmExtensions {
        version: VrmVersion::V1_0,
        humanoid,
        expressions,
        mtoon_materials,
        spring_bones,
    })
}

// ── VRM 0.x humanoid 解析 ──

fn parse_humanoid_0x(vrm: &Value) -> crate::Result<HumanoidMapping> {
    let mut map = HashMap::new();
    let bones = vrm.pointer("/humanoid/humanBones")
        .and_then(|v| v.as_array());

    if let Some(bones) = bones {
        for bone in bones {
            let name = bone.get("bone").and_then(|v| v.as_str());
            let node = bone.get("node").and_then(|v| v.as_u64());
            if let (Some(name), Some(node)) = (name, node) {
                map.insert(node as usize, name.to_string());
            }
        }
    }

    Ok(HumanoidMapping { map })
}

// ── VRM 0.x 表情解析 ──

fn parse_expressions_0x(vrm: &Value) -> crate::Result<VrmExpressions> {
    let mut map = HashMap::new();
    let groups = vrm.pointer("/blendShapeMaster/blendShapeGroups")
        .and_then(|v| v.as_array());

    if let Some(groups) = groups {
        for group in groups {
            // 优先 presetName（标准化名称），回退到 name（显示名）
            let preset = group.get("presetName")
                .and_then(|v| v.as_str())
                .filter(|s| !s.is_empty() && *s != "unknown");
            let display = group.get("name").and_then(|v| v.as_str());
            let name = match preset.or(display) {
                Some(n) => n.to_lowercase(),
                None => continue,
            };
            let binds = group.get("binds").and_then(|v| v.as_array());
            let mut weights = Vec::new();
            if let Some(binds) = binds {
                for bind in binds {
                    let index = bind.get("index").and_then(|v| v.as_u64());
                    let weight = bind.get("weight").and_then(|v| v.as_f64());
                    if let (Some(idx), Some(w)) = (index, weight) {
                        // VRM 0.x 权重范围 0-100，归一化到 0-1
                        weights.push((idx as usize, (w / 100.0) as f32));
                    }
                }
            }
            if !weights.is_empty() {
                map.insert(name, weights);
            }
        }
    }

    Ok(VrmExpressions { map })
}

// ── VRM 0.x MToon 解析（从 materialProperties）──

fn parse_mtoon_0x(vrm: &Value, material_count: usize) -> Vec<Option<MtoonParams>> {
    let mut result = vec![None; material_count];
    let props = vrm.get("materialProperties").and_then(|v| v.as_array());

    if let Some(props) = props {
        for (i, prop) in props.iter().enumerate() {
            if i >= material_count { break; }
            let shader = prop.get("shader").and_then(|v| v.as_str()).unwrap_or("");
            if !shader.contains("MToon") { continue; }

            let vf = prop.get("vectorProperties");
            let ff = prop.get("floatProperties");

            let shade_color = extract_vec3_from_props(vf, "_ShadeColor", [0.8, 0.8, 0.8]);
            let outline_color = extract_vec3_from_props(vf, "_OutlineColor", [0.0, 0.0, 0.0]);
            let shading_shift = extract_f32_from_props(ff, "_ShadeShift", 0.0);
            let outline_width = extract_f32_from_props(ff, "_OutlineWidth", 0.002);

            result[i] = Some(MtoonParams {
                shade_color_factor: shade_color,
                shading_shift_factor: shading_shift,
                outline_color_factor: outline_color,
                outline_width_factor: outline_width,
            });
        }
    }

    result
}

fn extract_vec3_from_props(props: Option<&Value>, key: &str, default: [f32; 3]) -> [f32; 3] {
    props
        .and_then(|p| p.get(key))
        .and_then(|v| v.as_array())
        .map(|arr| {
            let r = arr.first().and_then(|v| v.as_f64()).unwrap_or(default[0] as f64) as f32;
            let g = arr.get(1).and_then(|v| v.as_f64()).unwrap_or(default[1] as f64) as f32;
            let b = arr.get(2).and_then(|v| v.as_f64()).unwrap_or(default[2] as f64) as f32;
            [r, g, b]
        })
        .unwrap_or(default)
}

fn extract_f32_from_props(props: Option<&Value>, key: &str, default: f32) -> f32 {
    props
        .and_then(|p| p.get(key))
        .and_then(|v| v.as_f64())
        .map(|v| v as f32)
        .unwrap_or(default)
}

// ── VRM 1.0 humanoid 解析 ──

fn parse_humanoid_1_0(vrmc: &Value) -> crate::Result<HumanoidMapping> {
    let mut map = HashMap::new();
    let bones = vrmc.pointer("/humanoid/humanBones")
        .and_then(|v| v.as_object());

    if let Some(bones) = bones {
        for (bone_name, bone_data) in bones {
            if let Some(node) = bone_data.get("node").and_then(|v| v.as_u64()) {
                map.insert(node as usize, bone_name.clone());
            }
        }
    }

    Ok(HumanoidMapping { map })
}

// ── VRM 1.0 表情解析 ──

fn parse_expressions_1_0(vrmc: &Value) -> crate::Result<VrmExpressions> {
    let mut map = HashMap::new();

    let expressions = vrmc.get("expressions");
    if let Some(expressions) = expressions {
        parse_expression_group(expressions.get("preset"), &mut map);
        parse_expression_group(expressions.get("custom"), &mut map);
    }

    Ok(VrmExpressions { map })
}

fn parse_expression_group(group: Option<&Value>, map: &mut HashMap<String, Vec<(usize, f32)>>) {
    let group = match group.and_then(|v| v.as_object()) {
        Some(g) => g,
        None => return,
    };

    for (name, expr_data) in group {
        let binds = expr_data.get("morphTargetBinds").and_then(|v| v.as_array());
        let mut weights = Vec::new();
        if let Some(binds) = binds {
            for bind in binds {
                let index = bind.get("index").and_then(|v| v.as_u64());
                let weight = bind.get("weight").and_then(|v| v.as_f64());
                if let (Some(idx), Some(w)) = (index, weight) {
                    weights.push((idx as usize, w as f32));
                }
            }
        }
        if !weights.is_empty() {
            map.insert(name.clone(), weights);
        }
    }
}

// ── VRM 1.0 MToon 解析（从材质扩展）──

fn parse_mtoon_1_0(json_root: &gltf_json::Root) -> Vec<Option<MtoonParams>> {
    json_root.materials.iter().map(|mat| {
        let ext = mat.extensions.as_ref()?;
        let mtoon = ext.others.get("VRMC_materials_mtoon")?;

        Some(MtoonParams {
            shade_color_factor: parse_f32_array3(
                mtoon.get("shadeColorFactor"), [0.8, 0.8, 0.8]
            ),
            shading_shift_factor: mtoon.get("shadingShiftFactor")
                .and_then(|v: &Value| v.as_f64()).unwrap_or(0.0) as f32,
            outline_color_factor: parse_f32_array3(
                mtoon.get("outlineColorFactor"), [0.0, 0.0, 0.0]
            ),
            outline_width_factor: mtoon.get("outlineWidthFactor")
                .and_then(|v: &Value| v.as_f64()).unwrap_or(0.002) as f32,
        })
    }).collect()
}

fn parse_f32_array3(val: Option<&Value>, default: [f32; 3]) -> [f32; 3] {
    val.and_then(|v| v.as_array())
        .map(|arr| {
            let r = arr.first().and_then(|v| v.as_f64()).unwrap_or(default[0] as f64) as f32;
            let g = arr.get(1).and_then(|v| v.as_f64()).unwrap_or(default[1] as f64) as f32;
            let b = arr.get(2).and_then(|v| v.as_f64()).unwrap_or(default[2] as f64) as f32;
            [r, g, b]
        })
        .unwrap_or(default)
}

// ── VRM 0.x Spring Bone 解析 ──

/// VRM 0.x secondaryAnimation → SpringBoneData
///
/// 0.x 格式：boneGroups 定义根骨骼列表 + 统一参数，
/// 需要遍历 glTF 节点层次找出完整骨骼链。
fn parse_spring_bones_0x(vrm: &Value, json_root: &gltf_json::Root) -> SpringBoneData {
    let mut data = SpringBoneData::default();
    let secondary = match vrm.get("secondaryAnimation") {
        Some(v) => v,
        None => return data,
    };

    // 解析碰撞器组（0.x 中 colliderGroups 同时定义 group 和 collider）
    if let Some(groups) = secondary.get("colliderGroups").and_then(|v| v.as_array()) {
        for group_val in groups {
            let node = group_val.get("node").and_then(|v| v.as_u64()).unwrap_or(0) as usize;
            let mut collider_indices = Vec::new();

            if let Some(colliders) = group_val.get("colliders").and_then(|v| v.as_array()) {
                for c in colliders {
                    let offset = parse_vec3_object(c.get("offset"), [0.0, 0.0, 0.0]);
                    let radius = c.get("radius").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                    let idx = data.colliders.len();
                    data.colliders.push(SpringBoneCollider {
                        node,
                        shape: ColliderShape::Sphere { offset, radius },
                    });
                    collider_indices.push(idx);
                }
            }
            data.collider_groups.push(SpringBoneColliderGroup { colliders: collider_indices });
        }
    }

    // 解析 boneGroups → 展开为 spring chains
    if let Some(bone_groups) = secondary.get("boneGroups").and_then(|v| v.as_array()) {
        // 构建节点子级映射
        let children_map = build_node_children_map(json_root);

        for group in bone_groups {
            let stiffness = group.get("stiffiness") // VRM 0.x 规范拼写
                .or_else(|| group.get("stiffness"))
                .and_then(|v| v.as_f64()).unwrap_or(1.0) as f32;
            let gravity_power = group.get("gravityPower").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
            let gravity_dir = parse_vec3_object(group.get("gravityDir"), [0.0, -1.0, 0.0]);
            let drag_force = group.get("dragForce").and_then(|v| v.as_f64()).unwrap_or(0.5) as f32;
            let hit_radius = group.get("hitRadius").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
            let center = group.get("center").and_then(|v| v.as_i64())
                .and_then(|v| if v >= 0 { Some(v as usize) } else { None });

            let collider_groups: Vec<usize> = group.get("colliderGroups")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|v| v.as_u64().map(|i| i as usize)).collect())
                .unwrap_or_default();

            let root_bones: Vec<usize> = group.get("bones")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|v| v.as_u64().map(|i| i as usize)).collect())
                .unwrap_or_default();

            // 每个根骨骼展开为一条独立的 spring chain
            for &root_node in &root_bones {
                let chain = collect_bone_chain(root_node, &children_map);
                if chain.is_empty() { continue; }

                let joints: Vec<SpringBoneJoint> = chain.iter().map(|&node| {
                    SpringBoneJoint {
                        node,
                        hit_radius,
                        stiffness,
                        gravity_power,
                        gravity_dir,
                        drag_force,
                    }
                }).collect();

                data.springs.push(SpringBoneSpring {
                    joints,
                    collider_groups: collider_groups.clone(),
                    center,
                });
            }
        }
    }

    log::info!(
        "VRM 0.x Spring Bone: {} 弹簧链, {} 碰撞器, {} 碰撞器组",
        data.springs.len(), data.colliders.len(), data.collider_groups.len()
    );
    data
}

// ── VRM 1.0 Spring Bone 解析 ──

/// VRM 1.0 VRMC_springBone → SpringBoneData
fn parse_spring_bones_1_0(
    others: &serde_json::Map<String, Value>,
    _json_root: &gltf_json::Root,
) -> SpringBoneData {
    let mut data = SpringBoneData::default();
    let sb = match others.get("VRMC_springBone") {
        Some(v) => v,
        None => return data,
    };

    // 碰撞器
    if let Some(colliders) = sb.get("colliders").and_then(|v| v.as_array()) {
        for c in colliders {
            let node = c.get("node").and_then(|v| v.as_u64()).unwrap_or(0) as usize;
            let shape = if let Some(sphere) = c.pointer("/shape/sphere") {
                let offset = parse_f32_array3(sphere.get("offset"), [0.0, 0.0, 0.0]);
                let radius = sphere.get("radius").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                ColliderShape::Sphere { offset, radius }
            } else if let Some(capsule) = c.pointer("/shape/capsule") {
                let offset = parse_f32_array3(capsule.get("offset"), [0.0, 0.0, 0.0]);
                let tail = parse_f32_array3(capsule.get("tail"), [0.0, 0.0, 0.0]);
                let radius = capsule.get("radius").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                ColliderShape::Capsule { offset, tail, radius }
            } else {
                continue;
            };
            data.colliders.push(SpringBoneCollider { node, shape });
        }
    }

    // 碰撞器组
    if let Some(groups) = sb.get("colliderGroups").and_then(|v| v.as_array()) {
        for g in groups {
            let colliders = g.get("colliders")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|v| v.as_u64().map(|i| i as usize)).collect())
                .unwrap_or_default();
            data.collider_groups.push(SpringBoneColliderGroup { colliders });
        }
    }

    // 弹簧链
    if let Some(springs) = sb.get("springs").and_then(|v| v.as_array()) {
        for spring in springs {
            let center = spring.get("center").and_then(|v| v.as_u64()).map(|v| v as usize);
            let collider_groups: Vec<usize> = spring.get("colliderGroups")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|v| v.as_u64().map(|i| i as usize)).collect())
                .unwrap_or_default();

            let joints: Vec<SpringBoneJoint> = spring.get("joints")
                .and_then(|v| v.as_array())
                .map(|arr| arr.iter().filter_map(|j| {
                    let node = j.get("node").and_then(|v| v.as_u64())? as usize;
                    Some(SpringBoneJoint {
                        node,
                        hit_radius: j.get("hitRadius").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32,
                        stiffness: j.get("stiffness").and_then(|v| v.as_f64()).unwrap_or(1.0) as f32,
                        gravity_power: j.get("gravityPower").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32,
                        gravity_dir: parse_f32_array3(j.get("gravityDir"), [0.0, -1.0, 0.0]),
                        drag_force: j.get("dragForce").and_then(|v| v.as_f64()).unwrap_or(0.5) as f32,
                    })
                }).collect())
                .unwrap_or_default();

            if !joints.is_empty() {
                data.springs.push(SpringBoneSpring { joints, collider_groups, center });
            }
        }
    }

    log::info!(
        "VRM 1.0 Spring Bone: {} 弹簧链, {} 碰撞器, {} 碰撞器组",
        data.springs.len(), data.colliders.len(), data.collider_groups.len()
    );
    data
}

// ── 辅助函数 ──

/// 解析 VRM 0.x 的 {x, y, z} 对象格式
fn parse_vec3_object(val: Option<&Value>, default: [f32; 3]) -> [f32; 3] {
    match val {
        Some(v) => [
            v.get("x").and_then(|v| v.as_f64()).unwrap_or(default[0] as f64) as f32,
            v.get("y").and_then(|v| v.as_f64()).unwrap_or(default[1] as f64) as f32,
            v.get("z").and_then(|v| v.as_f64()).unwrap_or(default[2] as f64) as f32,
        ],
        None => default,
    }
}

/// 构建 glTF 节点子级映射 (parent → children)
fn build_node_children_map(json_root: &gltf_json::Root) -> HashMap<usize, Vec<usize>> {
    let mut map: HashMap<usize, Vec<usize>> = HashMap::new();
    for (i, node) in json_root.nodes.iter().enumerate() {
        if let Some(ref children) = node.children {
            let child_indices: Vec<usize> = children.iter().map(|c| c.value()).collect();
            map.insert(i, child_indices);
        }
    }
    map
}

/// 从根节点沿第一子节点链收集骨骼链（VRM 0.x 每个根骨骼为一条链）
fn collect_bone_chain(root: usize, children_map: &HashMap<usize, Vec<usize>>) -> Vec<usize> {
    let mut chain = vec![root];
    let mut current = root;
    loop {
        match children_map.get(&current) {
            Some(children) if !children.is_empty() => {
                current = children[0];
                chain.push(current);
            }
            _ => break,
        }
    }
    chain
}
