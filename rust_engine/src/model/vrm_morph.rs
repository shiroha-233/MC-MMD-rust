//! glTF morph targets → MorphManager 转换

use glam::Vec3;

use super::vrm_extensions::VrmExpressions;
use super::vrm_mesh::MorphTargetData;
use crate::morph::{Morph, MorphManager, MorphType, VertexMorphOffset};

/// 近零阈值，过滤无效偏移节省内存
const EPSILON: f32 = 1e-6;

/// VRM 表情名 → MMD 日文表情名
fn vrm_expression_to_mmd(vrm_name: &str) -> &str {
    match vrm_name {
        "blink" => "まばたき",
        "blinkleft" => "ウィンク",
        "blinkright" => "ウィンク右",
        "happy" => "にこり",
        "angry" => "怒り",
        "sad" => "悲しい",
        "surprised" => "びっくり",
        "aa" => "あ",
        "ih" => "い",
        "ou" => "う",
        "ee" => "え",
        "oh" => "お",
        other => other,
    }
}

/// 从 morph target 数据中提取非零顶点偏移
fn collect_vertex_offsets(target: &MorphTargetData) -> Vec<VertexMorphOffset> {
    target.position_offsets.iter().enumerate()
        .filter(|(_, offset)| offset.length_squared() > EPSILON * EPSILON)
        .map(|(i, &offset)| VertexMorphOffset {
            vertex_index: i as u32,
            offset,
        })
        .collect()
}

/// 将 glTF morph targets + VRM expressions 转换为 MorphManager
pub(crate) fn convert_morph_targets(
    morph_targets: &[MorphTargetData],
    vrm_expressions: &VrmExpressions,
    vertex_count: usize,
    material_count: usize,
) -> MorphManager {
    let mut manager = MorphManager::new();
    manager.set_vertex_count(vertex_count);
    manager.set_material_count(material_count);

    // 阶段1：将每个 morph target 注册为原始 Vertex Morph
    for (idx, target) in morph_targets.iter().enumerate() {
        let mut morph = Morph::new(format!("morph_{}", idx), MorphType::Vertex);
        morph.vertex_offsets = collect_vertex_offsets(target);
        manager.add_morph(morph);
    }

    // 阶段2：将 VRM expressions 烘焙为组合 Vertex Morph（MMD 日文名）
    for (expr_name, binds) in &vrm_expressions.map {
        let mmd_name = vrm_expression_to_mmd(expr_name).to_string();

        if manager.find_morph_by_name(&mmd_name).is_some() {
            continue;
        }

        let combined_offsets = bake_expression_offsets(morph_targets, binds, vertex_count);
        if combined_offsets.is_empty() {
            continue;
        }

        let mut morph = Morph::new(mmd_name, MorphType::Vertex);
        morph.vertex_offsets = combined_offsets;
        manager.add_morph(morph);
    }

    manager
}

/// 将表情的多个 morph target 权重组合烘焙为单一顶点偏移数组
fn bake_expression_offsets(
    morph_targets: &[MorphTargetData],
    binds: &[(usize, f32)],
    vertex_count: usize,
) -> Vec<VertexMorphOffset> {
    let mut combined = vec![Vec3::ZERO; vertex_count];

    for &(target_idx, weight) in binds {
        if let Some(target) = morph_targets.get(target_idx) {
            for (i, &offset) in target.position_offsets.iter().enumerate() {
                if i < vertex_count {
                    combined[i] += offset * weight;
                }
            }
        }
    }

    combined.iter().enumerate()
        .filter(|(_, v)| v.length_squared() > EPSILON * EPSILON)
        .map(|(i, &offset)| VertexMorphOffset {
            vertex_index: i as u32,
            offset,
        })
        .collect()
}
