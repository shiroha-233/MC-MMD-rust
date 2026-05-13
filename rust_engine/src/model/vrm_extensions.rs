//! VRM extension parsing for runtime-facing metadata.
//!
//! The runtime metadata shape is intentionally modeled after UniVRM's runtime
//! semantics:
//! - `Packages/VRM10/Runtime/Components/Vrm10Runtime/*.cs`
//! - `Packages/VRM10/Runtime/Components/FirstPerson/*.cs`
//! - `Packages/VRM10/Runtime/Components/LookAt/*.cs`
//! - `Packages/VRM10/Runtime/Components/Constraint/*.cs`
//! - `Packages/VRM10/Runtime/Components/Expression/*.cs`
//! The original UniVRM implementation is MIT licensed.

use std::collections::{HashMap, HashSet};

use gltf::json as gltf_json;
use serde_json::{Map, Value};

use crate::MmdError;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VrmVersion {
    V0x,
    V1_0,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpressionOverride {
    None,
    Block,
    Blend,
}

impl ExpressionOverride {
    fn parse(value: Option<&Value>) -> Self {
        match value
            .and_then(Value::as_str)
            .unwrap_or("none")
            .to_ascii_lowercase()
            .as_str()
        {
            "block" => Self::Block,
            "blend" => Self::Blend,
            _ => Self::None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct VrmExpressionClip {
    pub morph_target_binds: Vec<(usize, f32)>,
    pub is_binary: bool,
    pub override_blink: ExpressionOverride,
    pub override_look_at: ExpressionOverride,
    pub override_mouth: ExpressionOverride,
}

impl Default for VrmExpressionClip {
    fn default() -> Self {
        Self {
            morph_target_binds: Vec::new(),
            is_binary: false,
            override_blink: ExpressionOverride::None,
            override_look_at: ExpressionOverride::None,
            override_mouth: ExpressionOverride::None,
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct VrmExpressions {
    pub map: HashMap<String, VrmExpressionClip>,
}

#[derive(Debug, Clone, Default)]
pub struct HumanoidMapping {
    pub map: HashMap<usize, String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FirstPersonType {
    Auto,
    Both,
    ThirdPersonOnly,
    FirstPersonOnly,
}

impl FirstPersonType {
    fn parse(value: Option<&Value>) -> Self {
        match value
            .and_then(Value::as_str)
            .unwrap_or("auto")
            .to_ascii_lowercase()
            .as_str()
        {
            "both" => Self::Both,
            "thirdpersononly" | "third_person_only" => Self::ThirdPersonOnly,
            "firstpersononly" | "first_person_only" => Self::FirstPersonOnly,
            _ => Self::Auto,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FirstPersonMeshAnnotation {
    pub material_ids: Vec<usize>,
    pub first_person_type: FirstPersonType,
}

#[derive(Debug, Clone, Default)]
pub struct FirstPersonConfig {
    pub bone: Option<usize>,
    pub bone_offset: [f32; 3],
    pub mesh_annotations: Vec<FirstPersonMeshAnnotation>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LookAtType {
    Bone,
    Expression,
}

impl LookAtType {
    fn parse(value: Option<&Value>) -> Self {
        match value
            .and_then(Value::as_str)
            .unwrap_or("bone")
            .to_ascii_lowercase()
            .as_str()
        {
            "expression" | "expressions" => Self::Expression,
            _ => Self::Bone,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LookAtRangeMap {
    pub input_max_value: f32,
    pub output_scale: f32,
}

impl Default for LookAtRangeMap {
    fn default() -> Self {
        Self {
            input_max_value: 90.0,
            output_scale: 1.0,
        }
    }
}

#[derive(Debug, Clone)]
pub struct LookAtConfig {
    pub offset_from_head_bone: [f32; 3],
    pub look_at_type: LookAtType,
    pub range_map_horizontal_inner: LookAtRangeMap,
    pub range_map_horizontal_outer: LookAtRangeMap,
    pub range_map_vertical_down: LookAtRangeMap,
    pub range_map_vertical_up: LookAtRangeMap,
}

impl Default for LookAtConfig {
    fn default() -> Self {
        Self {
            offset_from_head_bone: [0.0, 0.0, 0.0],
            look_at_type: LookAtType::Bone,
            range_map_horizontal_inner: LookAtRangeMap::default(),
            range_map_horizontal_outer: LookAtRangeMap::default(),
            range_map_vertical_down: LookAtRangeMap::default(),
            range_map_vertical_up: LookAtRangeMap::default(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConstraintAxis {
    X,
    Y,
    Z,
    NegativeX,
    NegativeY,
    NegativeZ,
}

impl ConstraintAxis {
    fn parse(value: Option<&Value>) -> Self {
        match value
            .and_then(Value::as_str)
            .unwrap_or("X")
            .to_ascii_lowercase()
            .as_str()
        {
            "y" => Self::Y,
            "z" => Self::Z,
            "negativex" | "-x" => Self::NegativeX,
            "negativey" | "-y" => Self::NegativeY,
            "negativez" | "-z" => Self::NegativeZ,
            _ => Self::X,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum NodeConstraintKind {
    Roll {
        source: usize,
        roll_axis: ConstraintAxis,
        weight: f32,
    },
    Aim {
        source: usize,
        aim_axis: ConstraintAxis,
        weight: f32,
    },
    Rotation {
        source: usize,
        weight: f32,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct NodeConstraint {
    pub target: usize,
    pub kind: NodeConstraintKind,
}

#[derive(Debug, Clone)]
pub enum ColliderShape {
    Sphere {
        offset: [f32; 3],
        radius: f32,
    },
    Capsule {
        offset: [f32; 3],
        tail: [f32; 3],
        radius: f32,
    },
}

#[derive(Debug, Clone)]
pub struct SpringBoneCollider {
    pub node: usize,
    pub shape: ColliderShape,
}

#[derive(Debug, Clone)]
pub struct SpringBoneColliderGroup {
    pub colliders: Vec<usize>,
}

#[derive(Debug, Clone)]
pub struct SpringBoneJoint {
    pub node: usize,
    pub hit_radius: f32,
    pub stiffness: f32,
    pub gravity_power: f32,
    pub gravity_dir: [f32; 3],
    pub drag_force: f32,
}

#[derive(Debug, Clone)]
pub struct SpringBoneSpring {
    pub joints: Vec<SpringBoneJoint>,
    pub collider_groups: Vec<usize>,
    pub center: Option<usize>,
}

#[derive(Debug, Clone, Default)]
pub struct SpringBoneData {
    pub springs: Vec<SpringBoneSpring>,
    pub colliders: Vec<SpringBoneCollider>,
    pub collider_groups: Vec<SpringBoneColliderGroup>,
}

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

#[derive(Debug, Clone)]
pub struct VrmExtensions {
    pub version: VrmVersion,
    pub humanoid: HumanoidMapping,
    pub expressions: VrmExpressions,
    pub mtoon_materials: Vec<Option<MtoonParams>>,
    pub spring_bones: SpringBoneData,
    pub first_person: FirstPersonConfig,
    pub look_at: Option<LookAtConfig>,
    pub node_constraints: Vec<NodeConstraint>,
}

pub fn parse_vrm_extensions(document: &gltf::Document) -> crate::Result<VrmExtensions> {
    let json_root = document.as_json();
    let extensions = json_root
        .extensions
        .as_ref()
        .ok_or_else(|| MmdError::VrmParse("missing glTF extensions".into()))?;

    if extensions.others.contains_key("VRMC_vrm") {
        parse_vrm_1_0(&extensions.others, json_root)
    } else if extensions.others.contains_key("VRM") {
        parse_vrm_0x(&extensions.others, json_root)
    } else {
        Err(MmdError::VrmParse(
            "missing VRM or VRMC_vrm extension".into(),
        ))
    }
}

fn parse_vrm_0x(
    others: &Map<String, Value>,
    json_root: &gltf_json::Root,
) -> crate::Result<VrmExtensions> {
    let vrm = others
        .get("VRM")
        .ok_or_else(|| MmdError::VrmParse("missing VRM extension object".into()))?;

    let humanoid = parse_humanoid_0x(vrm)?;
    let expressions = parse_expressions_0x(vrm);
    let mtoon_materials = parse_mtoon_0x(vrm, json_root.materials.len());
    let spring_bones = parse_spring_bones_0x(vrm, json_root);
    let first_person = parse_first_person_0x(vrm, json_root);
    let look_at = parse_look_at_0x(vrm);

    Ok(VrmExtensions {
        version: VrmVersion::V0x,
        humanoid,
        expressions,
        mtoon_materials,
        spring_bones,
        first_person,
        look_at,
        node_constraints: Vec::new(),
    })
}

fn parse_vrm_1_0(
    others: &Map<String, Value>,
    json_root: &gltf_json::Root,
) -> crate::Result<VrmExtensions> {
    let vrmc = others
        .get("VRMC_vrm")
        .ok_or_else(|| MmdError::VrmParse("missing VRMC_vrm extension object".into()))?;

    let humanoid = parse_humanoid_1_0(vrmc)?;
    let expressions = parse_expressions_1_0(vrmc);
    let mtoon_materials = parse_mtoon_1_0(json_root);
    let spring_bones = parse_spring_bones_1_0(others);
    let first_person = parse_first_person_1_0(vrmc, json_root);
    let look_at = parse_look_at_1_0(vrmc);
    let node_constraints = parse_node_constraints_1_0(json_root);

    Ok(VrmExtensions {
        version: VrmVersion::V1_0,
        humanoid,
        expressions,
        mtoon_materials,
        spring_bones,
        first_person,
        look_at,
        node_constraints,
    })
}

fn parse_humanoid_0x(vrm: &Value) -> crate::Result<HumanoidMapping> {
    let mut map = HashMap::new();
    if let Some(bones) = vrm
        .pointer("/humanoid/humanBones")
        .and_then(Value::as_array)
    {
        for bone in bones {
            let name = bone.get("bone").and_then(Value::as_str);
            let node = bone.get("node").and_then(Value::as_u64);
            if let (Some(name), Some(node)) = (name, node) {
                map.insert(node as usize, name.to_string());
            }
        }
    }
    Ok(HumanoidMapping { map })
}

fn parse_humanoid_1_0(vrmc: &Value) -> crate::Result<HumanoidMapping> {
    let mut map = HashMap::new();
    if let Some(bones) = vrmc
        .pointer("/humanoid/humanBones")
        .and_then(Value::as_object)
    {
        for (bone_name, bone_data) in bones {
            if let Some(node) = bone_data.get("node").and_then(Value::as_u64) {
                map.insert(node as usize, bone_name.clone());
            }
        }
    }
    Ok(HumanoidMapping { map })
}

fn parse_expressions_0x(vrm: &Value) -> VrmExpressions {
    let mut map = HashMap::new();
    if let Some(groups) = vrm
        .pointer("/blendShapeMaster/blendShapeGroups")
        .and_then(Value::as_array)
    {
        for group in groups {
            let preset_name = group
                .get("presetName")
                .and_then(Value::as_str)
                .filter(|name| !name.is_empty() && !name.eq_ignore_ascii_case("unknown"));
            let display_name = group.get("name").and_then(Value::as_str);
            let Some(key) = preset_name.or(display_name).map(normalize_expression_name) else {
                continue;
            };
            let mut clip = VrmExpressionClip {
                is_binary: group
                    .get("isBinary")
                    .and_then(Value::as_bool)
                    .unwrap_or(false),
                ..VrmExpressionClip::default()
            };
            if let Some(binds) = group.get("binds").and_then(Value::as_array) {
                for bind in binds {
                    let index = bind.get("index").and_then(Value::as_u64);
                    let weight = bind.get("weight").and_then(Value::as_f64);
                    if let (Some(index), Some(weight)) = (index, weight) {
                        clip.morph_target_binds
                            .push((index as usize, (weight / 100.0) as f32));
                    }
                }
            }
            map.insert(key, clip);
        }
    }
    VrmExpressions { map }
}

fn parse_expressions_1_0(vrmc: &Value) -> VrmExpressions {
    let mut map = HashMap::new();
    if let Some(expressions) = vrmc.get("expressions") {
        parse_expression_group(expressions.get("preset"), &mut map);
        parse_expression_group(expressions.get("custom"), &mut map);
    }
    VrmExpressions { map }
}

fn parse_expression_group(group: Option<&Value>, map: &mut HashMap<String, VrmExpressionClip>) {
    let Some(group) = group.and_then(Value::as_object) else {
        return;
    };

    for (name, expr_data) in group {
        let mut clip = VrmExpressionClip {
            is_binary: expr_data
                .get("isBinary")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            override_blink: ExpressionOverride::parse(expr_data.get("overrideBlink")),
            override_look_at: ExpressionOverride::parse(expr_data.get("overrideLookAt")),
            override_mouth: ExpressionOverride::parse(expr_data.get("overrideMouth")),
            ..VrmExpressionClip::default()
        };
        if let Some(binds) = expr_data.get("morphTargetBinds").and_then(Value::as_array) {
            for bind in binds {
                let index = bind.get("index").and_then(Value::as_u64);
                let weight = bind.get("weight").and_then(Value::as_f64);
                if let (Some(index), Some(weight)) = (index, weight) {
                    clip.morph_target_binds
                        .push((index as usize, weight as f32));
                }
            }
        }
        map.insert(normalize_expression_name(name), clip);
    }
}

fn parse_mtoon_0x(vrm: &Value, material_count: usize) -> Vec<Option<MtoonParams>> {
    let mut result = vec![None; material_count];
    if let Some(props) = vrm.get("materialProperties").and_then(Value::as_array) {
        for (index, prop) in props.iter().enumerate() {
            if index >= material_count {
                break;
            }
            let shader = prop
                .get("shader")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if !shader.contains("MToon") {
                continue;
            }
            let vector_props = prop.get("vectorProperties");
            let float_props = prop.get("floatProperties");
            result[index] = Some(MtoonParams {
                shade_color_factor: extract_vec3_from_props(
                    vector_props,
                    "_ShadeColor",
                    [0.8, 0.8, 0.8],
                ),
                shading_shift_factor: extract_f32_from_props(float_props, "_ShadeShift", 0.0),
                outline_color_factor: extract_vec3_from_props(
                    vector_props,
                    "_OutlineColor",
                    [0.0, 0.0, 0.0],
                ),
                outline_width_factor: extract_f32_from_props(float_props, "_OutlineWidth", 0.002),
            });
        }
    }
    result
}

fn parse_mtoon_1_0(json_root: &gltf_json::Root) -> Vec<Option<MtoonParams>> {
    json_root
        .materials
        .iter()
        .map(|material| {
            let ext = material.extensions.as_ref()?;
            let mtoon = ext.others.get("VRMC_materials_mtoon")?;
            Some(MtoonParams {
                shade_color_factor: parse_f32_array3(
                    mtoon.get("shadeColorFactor"),
                    [0.8, 0.8, 0.8],
                ),
                shading_shift_factor: mtoon
                    .get("shadingShiftFactor")
                    .and_then(Value::as_f64)
                    .unwrap_or(0.0) as f32,
                outline_color_factor: parse_f32_array3(
                    mtoon.get("outlineColorFactor"),
                    [0.0, 0.0, 0.0],
                ),
                outline_width_factor: mtoon
                    .get("outlineWidthFactor")
                    .and_then(Value::as_f64)
                    .unwrap_or(0.002) as f32,
            })
        })
        .collect()
}

fn parse_first_person_0x(vrm: &Value, json_root: &gltf_json::Root) -> FirstPersonConfig {
    let Some(first_person) = vrm.get("firstPerson") else {
        return FirstPersonConfig::default();
    };

    let mut config = FirstPersonConfig {
        bone: first_person
            .get("firstPersonBone")
            .and_then(Value::as_i64)
            .and_then(|value| (value >= 0).then_some(value as usize)),
        bone_offset: parse_vec3_object(first_person.get("firstPersonBoneOffset"), [0.0, 0.0, 0.0]),
        ..FirstPersonConfig::default()
    };

    if let Some(annotations) = first_person
        .get("meshAnnotations")
        .and_then(Value::as_array)
    {
        for annotation in annotations {
            let mesh_index = annotation
                .get("mesh")
                .and_then(Value::as_u64)
                .map(|value| value as usize);
            let Some(mesh_index) = mesh_index else {
                continue;
            };
            let material_ids = material_ids_for_mesh(json_root, mesh_index);
            if material_ids.is_empty() {
                continue;
            }
            config.mesh_annotations.push(FirstPersonMeshAnnotation {
                material_ids,
                first_person_type: FirstPersonType::parse(annotation.get("firstPersonFlag")),
            });
        }
    }

    config
}

fn parse_first_person_1_0(vrmc: &Value, json_root: &gltf_json::Root) -> FirstPersonConfig {
    let Some(first_person) = vrmc.get("firstPerson") else {
        return FirstPersonConfig::default();
    };

    let mut config = FirstPersonConfig::default();
    if let Some(annotations) = first_person
        .get("meshAnnotations")
        .and_then(Value::as_array)
    {
        for annotation in annotations {
            let node_index = annotation
                .get("node")
                .and_then(Value::as_u64)
                .map(|value| value as usize);
            let Some(node_index) = node_index else {
                continue;
            };
            let material_ids = material_ids_for_node(json_root, node_index);
            if material_ids.is_empty() {
                continue;
            }
            config.mesh_annotations.push(FirstPersonMeshAnnotation {
                material_ids,
                first_person_type: FirstPersonType::parse(annotation.get("type")),
            });
        }
    }
    config
}

fn parse_look_at_0x(vrm: &Value) -> Option<LookAtConfig> {
    let first_person = vrm.get("firstPerson")?;
    Some(LookAtConfig {
        offset_from_head_bone: parse_vec3_object(
            first_person.get("firstPersonBoneOffset"),
            [0.0, 0.0, 0.0],
        ),
        look_at_type: LookAtType::parse(first_person.get("lookAtTypeName")),
        range_map_horizontal_inner: parse_degree_map_0x(first_person.get("lookAtHorizontalInner")),
        range_map_horizontal_outer: parse_degree_map_0x(first_person.get("lookAtHorizontalOuter")),
        range_map_vertical_down: parse_degree_map_0x(first_person.get("lookAtVerticalDown")),
        range_map_vertical_up: parse_degree_map_0x(first_person.get("lookAtVerticalUp")),
    })
}

fn parse_look_at_1_0(vrmc: &Value) -> Option<LookAtConfig> {
    let look_at = vrmc.get("lookAt")?;
    Some(LookAtConfig {
        offset_from_head_bone: parse_f32_array3(look_at.get("offsetFromHeadBone"), [0.0, 0.0, 0.0]),
        look_at_type: LookAtType::parse(look_at.get("type")),
        range_map_horizontal_inner: parse_degree_map_1_0(look_at.get("rangeMapHorizontalInner")),
        range_map_horizontal_outer: parse_degree_map_1_0(look_at.get("rangeMapHorizontalOuter")),
        range_map_vertical_down: parse_degree_map_1_0(look_at.get("rangeMapVerticalDown")),
        range_map_vertical_up: parse_degree_map_1_0(look_at.get("rangeMapVerticalUp")),
    })
}

fn parse_node_constraints_1_0(json_root: &gltf_json::Root) -> Vec<NodeConstraint> {
    json_root
        .nodes
        .iter()
        .enumerate()
        .filter_map(|(target, node)| {
            let ext = node.extensions.as_ref()?;
            let constraint_ext = ext.others.get("VRMC_node_constraint")?;
            parse_single_node_constraint(target, constraint_ext)
        })
        .collect()
}

fn parse_single_node_constraint(target: usize, constraint_ext: &Value) -> Option<NodeConstraint> {
    let constraint = constraint_ext.get("constraint")?;
    if let Some(roll) = constraint.get("roll") {
        let source = roll.get("source")?.as_u64()? as usize;
        return Some(NodeConstraint {
            target,
            kind: NodeConstraintKind::Roll {
                source,
                roll_axis: ConstraintAxis::parse(roll.get("rollAxis")),
                weight: roll.get("weight").and_then(Value::as_f64).unwrap_or(1.0) as f32,
            },
        });
    }
    if let Some(aim) = constraint.get("aim") {
        let source = aim.get("source")?.as_u64()? as usize;
        return Some(NodeConstraint {
            target,
            kind: NodeConstraintKind::Aim {
                source,
                aim_axis: ConstraintAxis::parse(aim.get("aimAxis")),
                weight: aim.get("weight").and_then(Value::as_f64).unwrap_or(1.0) as f32,
            },
        });
    }
    if let Some(rotation) = constraint.get("rotation") {
        let source = rotation.get("source")?.as_u64()? as usize;
        return Some(NodeConstraint {
            target,
            kind: NodeConstraintKind::Rotation {
                source,
                weight: rotation
                    .get("weight")
                    .and_then(Value::as_f64)
                    .unwrap_or(1.0) as f32,
            },
        });
    }
    None
}

fn parse_spring_bones_0x(vrm: &Value, json_root: &gltf_json::Root) -> SpringBoneData {
    let mut data = SpringBoneData::default();
    let Some(secondary) = vrm.get("secondaryAnimation") else {
        return data;
    };

    if let Some(groups) = secondary.get("colliderGroups").and_then(Value::as_array) {
        for group_value in groups {
            let node = group_value.get("node").and_then(Value::as_u64).unwrap_or(0) as usize;
            let mut collider_indices = Vec::new();
            if let Some(colliders) = group_value.get("colliders").and_then(Value::as_array) {
                for collider in colliders {
                    let offset = parse_vec3_object(collider.get("offset"), [0.0, 0.0, 0.0]);
                    let radius = collider
                        .get("radius")
                        .and_then(Value::as_f64)
                        .unwrap_or(0.0) as f32;
                    let index = data.colliders.len();
                    data.colliders.push(SpringBoneCollider {
                        node,
                        shape: ColliderShape::Sphere { offset, radius },
                    });
                    collider_indices.push(index);
                }
            }
            data.collider_groups.push(SpringBoneColliderGroup {
                colliders: collider_indices,
            });
        }
    }

    if let Some(bone_groups) = secondary.get("boneGroups").and_then(Value::as_array) {
        let children_map = build_node_children_map(json_root);
        for group in bone_groups {
            let stiffness = group
                .get("stiffiness")
                .or_else(|| group.get("stiffness"))
                .and_then(Value::as_f64)
                .unwrap_or(1.0) as f32;
            let gravity_power = group
                .get("gravityPower")
                .and_then(Value::as_f64)
                .unwrap_or(0.0) as f32;
            let gravity_dir = parse_vec3_object(group.get("gravityDir"), [0.0, -1.0, 0.0]);
            let drag_force = group
                .get("dragForce")
                .and_then(Value::as_f64)
                .unwrap_or(0.5) as f32;
            let hit_radius = group
                .get("hitRadius")
                .and_then(Value::as_f64)
                .unwrap_or(0.0) as f32;
            let center = group
                .get("center")
                .and_then(Value::as_i64)
                .and_then(|value| (value >= 0).then_some(value as usize));
            let collider_groups = group
                .get("colliderGroups")
                .and_then(Value::as_array)
                .map(|array| {
                    array
                        .iter()
                        .filter_map(|value| value.as_u64().map(|index| index as usize))
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            let root_bones = group
                .get("bones")
                .and_then(Value::as_array)
                .map(|array| {
                    array
                        .iter()
                        .filter_map(|value| value.as_u64().map(|index| index as usize))
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();

            for root_node in root_bones {
                let chain = collect_bone_chain(root_node, &children_map);
                if chain.is_empty() {
                    continue;
                }
                let joints = chain
                    .into_iter()
                    .map(|node| SpringBoneJoint {
                        node,
                        hit_radius,
                        stiffness,
                        gravity_power,
                        gravity_dir,
                        drag_force,
                    })
                    .collect::<Vec<_>>();
                data.springs.push(SpringBoneSpring {
                    joints,
                    collider_groups: collider_groups.clone(),
                    center,
                });
            }
        }
    }

    data
}

fn parse_spring_bones_1_0(others: &Map<String, Value>) -> SpringBoneData {
    let mut data = SpringBoneData::default();
    let Some(sb) = others.get("VRMC_springBone") else {
        return data;
    };

    if let Some(colliders) = sb.get("colliders").and_then(Value::as_array) {
        for collider in colliders {
            let node = collider.get("node").and_then(Value::as_u64).unwrap_or(0) as usize;
            let shape = if let Some(sphere) = collider.pointer("/shape/sphere") {
                ColliderShape::Sphere {
                    offset: parse_f32_array3(sphere.get("offset"), [0.0, 0.0, 0.0]),
                    radius: sphere.get("radius").and_then(Value::as_f64).unwrap_or(0.0) as f32,
                }
            } else if let Some(capsule) = collider.pointer("/shape/capsule") {
                ColliderShape::Capsule {
                    offset: parse_f32_array3(capsule.get("offset"), [0.0, 0.0, 0.0]),
                    tail: parse_f32_array3(capsule.get("tail"), [0.0, 0.0, 0.0]),
                    radius: capsule.get("radius").and_then(Value::as_f64).unwrap_or(0.0) as f32,
                }
            } else {
                continue;
            };
            data.colliders.push(SpringBoneCollider { node, shape });
        }
    }

    if let Some(groups) = sb.get("colliderGroups").and_then(Value::as_array) {
        for group in groups {
            let colliders = group
                .get("colliders")
                .and_then(Value::as_array)
                .map(|array| {
                    array
                        .iter()
                        .filter_map(|value| value.as_u64().map(|index| index as usize))
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            data.collider_groups
                .push(SpringBoneColliderGroup { colliders });
        }
    }

    if let Some(springs) = sb.get("springs").and_then(Value::as_array) {
        for spring in springs {
            let center = spring
                .get("center")
                .and_then(Value::as_u64)
                .map(|value| value as usize);
            let collider_groups = spring
                .get("colliderGroups")
                .and_then(Value::as_array)
                .map(|array| {
                    array
                        .iter()
                        .filter_map(|value| value.as_u64().map(|index| index as usize))
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            let joints = spring
                .get("joints")
                .and_then(Value::as_array)
                .map(|array| {
                    array
                        .iter()
                        .filter_map(|joint| {
                            let node = joint.get("node").and_then(Value::as_u64)? as usize;
                            Some(SpringBoneJoint {
                                node,
                                hit_radius: joint
                                    .get("hitRadius")
                                    .and_then(Value::as_f64)
                                    .unwrap_or(0.0)
                                    as f32,
                                stiffness: joint
                                    .get("stiffness")
                                    .and_then(Value::as_f64)
                                    .unwrap_or(1.0)
                                    as f32,
                                gravity_power: joint
                                    .get("gravityPower")
                                    .and_then(Value::as_f64)
                                    .unwrap_or(0.0)
                                    as f32,
                                gravity_dir: parse_f32_array3(
                                    joint.get("gravityDir"),
                                    [0.0, -1.0, 0.0],
                                ),
                                drag_force: joint
                                    .get("dragForce")
                                    .and_then(Value::as_f64)
                                    .unwrap_or(0.5)
                                    as f32,
                            })
                        })
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            if !joints.is_empty() {
                data.springs.push(SpringBoneSpring {
                    joints,
                    collider_groups,
                    center,
                });
            }
        }
    }

    data
}

fn normalize_expression_name(name: &str) -> String {
    name.trim().to_ascii_lowercase()
}

fn extract_vec3_from_props(props: Option<&Value>, key: &str, default: [f32; 3]) -> [f32; 3] {
    props
        .and_then(|value| value.get(key))
        .and_then(Value::as_array)
        .map(|array| {
            [
                array
                    .first()
                    .and_then(Value::as_f64)
                    .unwrap_or(default[0] as f64) as f32,
                array
                    .get(1)
                    .and_then(Value::as_f64)
                    .unwrap_or(default[1] as f64) as f32,
                array
                    .get(2)
                    .and_then(Value::as_f64)
                    .unwrap_or(default[2] as f64) as f32,
            ]
        })
        .unwrap_or(default)
}

fn extract_f32_from_props(props: Option<&Value>, key: &str, default: f32) -> f32 {
    props
        .and_then(|value| value.get(key))
        .and_then(Value::as_f64)
        .map(|value| value as f32)
        .unwrap_or(default)
}

fn parse_degree_map_0x(value: Option<&Value>) -> LookAtRangeMap {
    let Some(value) = value else {
        return LookAtRangeMap::default();
    };
    LookAtRangeMap {
        input_max_value: value.get("xRange").and_then(Value::as_f64).unwrap_or(90.0) as f32,
        output_scale: value.get("yRange").and_then(Value::as_f64).unwrap_or(1.0) as f32,
    }
}

fn parse_degree_map_1_0(value: Option<&Value>) -> LookAtRangeMap {
    let Some(value) = value else {
        return LookAtRangeMap::default();
    };
    LookAtRangeMap {
        input_max_value: value
            .get("inputMaxValue")
            .and_then(Value::as_f64)
            .unwrap_or(90.0) as f32,
        output_scale: value
            .get("outputScale")
            .and_then(Value::as_f64)
            .unwrap_or(1.0) as f32,
    }
}

fn parse_vec3_object(value: Option<&Value>, default: [f32; 3]) -> [f32; 3] {
    match value {
        Some(value) => [
            value
                .get("x")
                .and_then(Value::as_f64)
                .unwrap_or(default[0] as f64) as f32,
            value
                .get("y")
                .and_then(Value::as_f64)
                .unwrap_or(default[1] as f64) as f32,
            value
                .get("z")
                .and_then(Value::as_f64)
                .unwrap_or(default[2] as f64) as f32,
        ],
        None => default,
    }
}

fn parse_f32_array3(value: Option<&Value>, default: [f32; 3]) -> [f32; 3] {
    value
        .and_then(Value::as_array)
        .map(|array| {
            [
                array
                    .first()
                    .and_then(Value::as_f64)
                    .unwrap_or(default[0] as f64) as f32,
                array
                    .get(1)
                    .and_then(Value::as_f64)
                    .unwrap_or(default[1] as f64) as f32,
                array
                    .get(2)
                    .and_then(Value::as_f64)
                    .unwrap_or(default[2] as f64) as f32,
            ]
        })
        .unwrap_or(default)
}

fn material_ids_for_node(json_root: &gltf_json::Root, node_index: usize) -> Vec<usize> {
    let Some(node) = json_root.nodes.get(node_index) else {
        return Vec::new();
    };
    let Some(mesh_index) = node.mesh.as_ref().map(|index| index.value()) else {
        return Vec::new();
    };
    material_ids_for_mesh(json_root, mesh_index)
}

fn material_ids_for_mesh(json_root: &gltf_json::Root, mesh_index: usize) -> Vec<usize> {
    let Some(mesh) = json_root.meshes.get(mesh_index) else {
        return Vec::new();
    };
    let mut seen = HashSet::new();
    let mut material_ids = Vec::new();
    for primitive in &mesh.primitives {
        if let Some(material) = primitive.material.as_ref().map(|index| index.value()) {
            if seen.insert(material) {
                material_ids.push(material);
            }
        }
    }
    material_ids
}

fn build_node_children_map(json_root: &gltf_json::Root) -> HashMap<usize, Vec<usize>> {
    let mut map = HashMap::new();
    for (index, node) in json_root.nodes.iter().enumerate() {
        if let Some(children) = node.children.as_ref() {
            map.insert(index, children.iter().map(|child| child.value()).collect());
        }
    }
    map
}

fn collect_bone_chain(root: usize, children_map: &HashMap<usize, Vec<usize>>) -> Vec<usize> {
    let mut chain = vec![root];
    let mut current = root;
    loop {
        let Some(children) = children_map.get(&current) else {
            break;
        };
        let Some(next) = children.first().copied() else {
            break;
        };
        chain.push(next);
        current = next;
    }
    chain
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn test_root() -> gltf_json::Root {
        serde_json::from_value(json!({
            "asset": { "version": "2.0" },
            "nodes": [
                { "mesh": 0 },
                { "mesh": 1 }
            ],
            "meshes": [
                { "primitives": [{ "attributes": {}, "material": 0 }, { "attributes": {}, "material": 1 }] },
                { "primitives": [{ "attributes": {}, "material": 2 }] }
            ],
            "materials": [{}, {}, {}]
        }))
        .expect("valid glTF json root")
    }

    #[test]
    fn parse_first_person_0x_should_map_mesh_annotations_to_materials() {
        let root = test_root();
        let vrm = json!({
            "firstPerson": {
                "firstPersonBone": 5,
                "firstPersonBoneOffset": { "x": 0.1, "y": 0.2, "z": 0.3 },
                "meshAnnotations": [
                    { "mesh": 0, "firstPersonFlag": "ThirdPersonOnly" },
                    { "mesh": 1, "firstPersonFlag": "FirstPersonOnly" }
                ]
            }
        });

        let config = parse_first_person_0x(&vrm, &root);

        assert_eq!(config.bone, Some(5));
        assert_eq!(config.bone_offset, [0.1, 0.2, 0.3]);
        assert_eq!(config.mesh_annotations.len(), 2);
        assert_eq!(config.mesh_annotations[0].material_ids, vec![0, 1]);
        assert_eq!(
            config.mesh_annotations[0].first_person_type,
            FirstPersonType::ThirdPersonOnly
        );
        assert_eq!(config.mesh_annotations[1].material_ids, vec![2]);
        assert_eq!(
            config.mesh_annotations[1].first_person_type,
            FirstPersonType::FirstPersonOnly
        );
    }

    #[test]
    fn parse_first_person_1_0_should_map_node_annotations_to_materials() {
        let root = test_root();
        let vrmc = json!({
            "firstPerson": {
                "meshAnnotations": [
                    { "node": 1, "type": "firstPersonOnly" }
                ]
            }
        });

        let config = parse_first_person_1_0(&vrmc, &root);

        assert_eq!(config.mesh_annotations.len(), 1);
        assert_eq!(config.mesh_annotations[0].material_ids, vec![2]);
        assert_eq!(
            config.mesh_annotations[0].first_person_type,
            FirstPersonType::FirstPersonOnly
        );
    }

    #[test]
    fn parse_look_at_configs_should_capture_linear_ranges() {
        let vrm = json!({
            "firstPerson": {
                "firstPersonBoneOffset": { "x": 0.0, "y": 1.0, "z": 2.0 },
                "lookAtTypeName": "Expression",
                "lookAtHorizontalOuter": { "xRange": 45.0, "yRange": 0.7 }
            }
        });
        let vrmc = json!({
            "lookAt": {
                "offsetFromHeadBone": [0.0, 0.4, 0.1],
                "type": "bone",
                "rangeMapHorizontalOuter": { "inputMaxValue": 35.0, "outputScale": 22.0 }
            }
        });

        let v0 = parse_look_at_0x(&vrm).expect("0.x look-at");
        let v1 = parse_look_at_1_0(&vrmc).expect("1.0 look-at");

        assert_eq!(v0.look_at_type, LookAtType::Expression);
        assert_eq!(v0.offset_from_head_bone, [0.0, 1.0, 2.0]);
        assert_eq!(v0.range_map_horizontal_outer.input_max_value, 45.0);
        assert_eq!(v0.range_map_horizontal_outer.output_scale, 0.7);

        assert_eq!(v1.look_at_type, LookAtType::Bone);
        assert_eq!(v1.offset_from_head_bone, [0.0, 0.4, 0.1]);
        assert_eq!(v1.range_map_horizontal_outer.input_max_value, 35.0);
        assert_eq!(v1.range_map_horizontal_outer.output_scale, 22.0);
    }

    #[test]
    fn parse_expression_group_should_normalize_names_and_binary_flags() {
        let mut map = HashMap::new();
        let group = json!({
            "BlinkLeft": {
                "isBinary": true,
                "overrideBlink": "block",
                "morphTargetBinds": [{ "index": 3, "weight": 0.9 }]
            }
        });

        parse_expression_group(Some(&group), &mut map);

        let clip = map.get("blinkleft").expect("normalized key");
        assert!(clip.is_binary);
        assert_eq!(clip.override_blink, ExpressionOverride::Block);
        assert_eq!(clip.morph_target_binds, vec![(3, 0.9)]);
    }

    #[test]
    fn parse_node_constraints_should_capture_roll_aim_and_rotation() {
        let root: gltf_json::Root = serde_json::from_value(json!({
            "asset": { "version": "2.0" },
            "nodes": [
                {
                    "extensions": {
                        "VRMC_node_constraint": {
                            "constraint": {
                                "roll": { "source": 2, "rollAxis": "Y", "weight": 0.5 }
                            }
                        }
                    }
                },
                {
                    "extensions": {
                        "VRMC_node_constraint": {
                            "constraint": {
                                "aim": { "source": 3, "aimAxis": "NegativeZ", "weight": 0.75 }
                            }
                        }
                    }
                },
                {
                    "extensions": {
                        "VRMC_node_constraint": {
                            "constraint": {
                                "rotation": { "source": 4, "weight": 1.0 }
                            }
                        }
                    }
                }
            ]
        }))
        .expect("valid glTF root");

        let constraints = parse_node_constraints_1_0(&root);

        assert_eq!(constraints.len(), 3);
        assert_eq!(
            constraints[0],
            NodeConstraint {
                target: 0,
                kind: NodeConstraintKind::Roll {
                    source: 2,
                    roll_axis: ConstraintAxis::Y,
                    weight: 0.5,
                },
            }
        );
        assert_eq!(
            constraints[1],
            NodeConstraint {
                target: 1,
                kind: NodeConstraintKind::Aim {
                    source: 3,
                    aim_axis: ConstraintAxis::NegativeZ,
                    weight: 0.75,
                },
            }
        );
        assert_eq!(
            constraints[2],
            NodeConstraint {
                target: 2,
                kind: NodeConstraintKind::Rotation {
                    source: 4,
                    weight: 1.0,
                },
            }
        );
    }
}
