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

    Ok(VrmExtensions {
        version: VrmVersion::V0x,
        humanoid,
        expressions,
        mtoon_materials,
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

    Ok(VrmExtensions {
        version: VrmVersion::V1_0,
        humanoid,
        expressions,
        mtoon_materials,
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
            let name = match group.get("name").and_then(|v| v.as_str()) {
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
                let node = bind.get("node").and_then(|v| v.as_u64());
                let index = bind.get("index").and_then(|v| v.as_u64());
                let weight = bind.get("weight").and_then(|v| v.as_f64());
                if let (Some(_node), Some(idx), Some(w)) = (node, index, weight) {
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
