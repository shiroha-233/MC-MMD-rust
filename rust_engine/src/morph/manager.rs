//! Morph 管理器
//!
//! 完整实现 MMD Morph 系统：
//! - Vertex Morph: 顶点位置偏移
//! - Bone Morph: 骨骼平移/旋转偏移
//! - Group Morph: 递归组合多个 Morph（含循环引用保护）
//! - Material Morph: 材质参数变形（乘算/加算）
//! - UV Morph: 纹理坐标偏移

use std::collections::HashMap;
use glam::{Vec2, Vec3, Vec4};

use crate::skeleton::BoneManager;
use super::{Morph, MorphType, MaterialMorphOffset};

/// Morph 权重阈值，低于此值视为不活跃
const MORPH_WEIGHT_EPSILON: f32 = 0.001;
/// Group Morph 最大递归深度（防止循环引用导致无限递归）
const MAX_GROUP_MORPH_DEPTH: u32 = 16;

/// 材质变形参数值（乘算或加算共用的字段集合）
#[derive(Clone, Debug)]
pub struct MaterialMorphValues {
    pub diffuse: Vec4,
    pub specular: Vec3,
    pub specular_strength: f32,
    pub ambient: Vec3,
    pub edge_color: Vec4,
    pub edge_size: f32,
    pub texture_tint: Vec4,
    pub environment_tint: Vec4,
    pub toon_tint: Vec4,
}

impl MaterialMorphValues {
    /// 乘算单位值（所有字段 = 1.0，乘上去无变化）
    pub fn mul_identity() -> Self {
        Self {
            diffuse: Vec4::ONE,
            specular: Vec3::ONE,
            specular_strength: 1.0,
            ambient: Vec3::ONE,
            edge_color: Vec4::ONE,
            edge_size: 1.0,
            texture_tint: Vec4::ONE,
            environment_tint: Vec4::ONE,
            toon_tint: Vec4::ONE,
        }
    }
    
    /// 加算单位值（所有字段 = 0.0，加上去无变化）
    pub fn add_identity() -> Self {
        Self {
            diffuse: Vec4::ZERO,
            specular: Vec3::ZERO,
            specular_strength: 0.0,
            ambient: Vec3::ZERO,
            edge_color: Vec4::ZERO,
            edge_size: 0.0,
            texture_tint: Vec4::ZERO,
            environment_tint: Vec4::ZERO,
            toon_tint: Vec4::ZERO,
        }
    }
    
    /// 应用一个乘算偏移（operation=0）
    /// 将 lerp(1.0, offset, weight) 乘到现有累积值上，确保多个乘算 Morph 正确复合
    fn apply_multiply(&mut self, offset: &MaterialMorphOffset, weight: f32) {
        let w = weight;
        let inv_w = 1.0 - w;
        self.diffuse *= Vec4::ONE * inv_w + offset.diffuse * w;
        self.specular *= Vec3::ONE * inv_w + offset.specular * w;
        self.specular_strength *= inv_w + offset.specular_strength * w;
        self.ambient *= Vec3::ONE * inv_w + offset.ambient * w;
        self.edge_color *= Vec4::ONE * inv_w + offset.edge_color * w;
        self.edge_size *= inv_w + offset.edge_size * w;
        self.texture_tint *= Vec4::ONE * inv_w + offset.texture_tint * w;
        self.environment_tint *= Vec4::ONE * inv_w + offset.environment_tint * w;
        self.toon_tint *= Vec4::ONE * inv_w + offset.toon_tint * w;
    }
    
    /// 应用一个加算偏移（operation=1）
    fn apply_additive(&mut self, offset: &MaterialMorphOffset, weight: f32) {
        self.diffuse += offset.diffuse * weight;
        self.specular += offset.specular * weight;
        self.specular_strength += offset.specular_strength * weight;
        self.ambient += offset.ambient * weight;
        self.edge_color += offset.edge_color * weight;
        self.edge_size += offset.edge_size * weight;
        self.texture_tint += offset.texture_tint * weight;
        self.environment_tint += offset.environment_tint * weight;
        self.toon_tint += offset.toon_tint * weight;
    }
}

/// 单个材质的 Morph 计算结果
///
/// 渲染时：final = base * mul + add
/// 乘算累积基准为 1.0（无变化），加算累积基准为 0.0。
#[derive(Clone, Debug)]
pub struct MaterialMorphResult {
    /// 乘算累积
    pub mul: MaterialMorphValues,
    /// 加算累积
    pub add: MaterialMorphValues,
}

impl Default for MaterialMorphResult {
    fn default() -> Self {
        Self {
            mul: MaterialMorphValues::mul_identity(),
            add: MaterialMorphValues::add_identity(),
        }
    }
}

impl MaterialMorphResult {
    /// 重置为初始状态（乘算=1, 加算=0）
    pub fn reset(&mut self) {
        *self = Self::default();
    }
}

/// Morph 管理器
pub struct MorphManager {
    morphs: Vec<Morph>,
    name_to_index: HashMap<String, usize>,
    
    /// 每个材质的 Morph 计算结果（材质索引 -> 结果）
    material_morph_results: Vec<MaterialMorphResult>,
    /// 材质数量（由外部设置）
    material_count: usize,
    
    /// UV Morph 偏移结果（每顶点的 UV 偏移，在 apply_morphs 时累积）
    uv_morph_deltas: Vec<Vec2>,
    /// 顶点数量（由外部设置）
    vertex_count: usize,
}

impl MorphManager {
    pub fn new() -> Self {
        Self {
            morphs: Vec::new(),
            name_to_index: HashMap::new(),
            material_morph_results: Vec::new(),
            material_count: 0,
            uv_morph_deltas: Vec::new(),
            vertex_count: 0,
        }
    }
    
    /// 设置材质数量（模型加载后调用）
    pub fn set_material_count(&mut self, count: usize) {
        self.material_count = count;
        self.material_morph_results = vec![MaterialMorphResult::default(); count];
    }
    
    /// 设置顶点数量（模型加载后调用）
    pub fn set_vertex_count(&mut self, count: usize) {
        self.vertex_count = count;
        self.uv_morph_deltas = vec![Vec2::ZERO; count];
    }
    
    /// 添加 Morph
    pub fn add_morph(&mut self, morph: Morph) {
        let index = self.morphs.len();
        self.name_to_index.insert(morph.name.clone(), index);
        self.morphs.push(morph);
    }
    
    /// 通过名称查找 Morph
    pub fn find_morph_by_name(&self, name: &str) -> Option<usize> {
        self.name_to_index.get(name).copied()
    }
    
    /// 获取 Morph 数量
    pub fn morph_count(&self) -> usize {
        self.morphs.len()
    }
    
    /// 获取 Morph
    pub fn get_morph(&self, index: usize) -> Option<&Morph> {
        self.morphs.get(index)
    }
    
    /// 获取可变 Morph 引用
    pub fn get_morph_mut(&mut self, index: usize) -> Option<&mut Morph> {
        self.morphs.get_mut(index)
    }
    
    /// 设置 Morph 权重
    pub fn set_morph_weight(&mut self, index: usize, weight: f32) {
        if let Some(morph) = self.morphs.get_mut(index) {
            morph.set_weight(weight);
        }
    }
    
    /// 获取 Morph 权重
    pub fn get_morph_weight(&self, index: usize) -> f32 {
        self.morphs.get(index).map(|m| m.weight).unwrap_or(0.0)
    }
    
    /// 重置所有 Morph 权重
    pub fn reset_all_weights(&mut self) {
        for morph in &mut self.morphs {
            morph.reset();
        }
    }
    
    /// 获取材质 Morph 结果
    pub fn get_material_morph_result(&self, material_index: usize) -> Option<&MaterialMorphResult> {
        self.material_morph_results.get(material_index)
    }
    
    /// 获取所有材质 Morph 结果
    pub fn get_material_morph_results(&self) -> &[MaterialMorphResult] {
        &self.material_morph_results
    }
    
    /// 获取 UV Morph 偏移结果
    pub fn get_uv_morph_deltas(&self) -> &[Vec2] {
        &self.uv_morph_deltas
    }
    
    /// 应用所有 Morph（完整流水线）
    ///
    /// 处理顺序遵循 MMD 规范：
    /// 1. 重置材质/UV 累积缓冲区
    /// 2. 遍历所有 Morph，按类型分发处理
    /// 3. Group Morph 递归展开子项
    pub fn apply_morphs(&mut self, bone_manager: &mut BoneManager, positions: &mut [Vec3]) {
        // 重置材质 Morph 结果
        for result in &mut self.material_morph_results {
            result.reset();
        }
        // 重置 UV 偏移
        for delta in &mut self.uv_morph_deltas {
            *delta = Vec2::ZERO;
        }
        
        // 收集需要处理的 morph 索引和权重
        let active_morphs: Vec<(usize, f32)> = self.morphs.iter().enumerate()
            .filter(|(_, m)| m.weight.abs() > MORPH_WEIGHT_EPSILON)
            .map(|(i, m)| (i, m.weight))
            .collect();
        
        for (morph_idx, weight) in active_morphs {
            // 拆分借用：morphs 只读，material_morph_results / uv_morph_deltas 可写
            apply_single_morph(
                &self.morphs,
                &mut self.material_morph_results,
                &mut self.uv_morph_deltas,
                morph_idx,
                weight,
                bone_manager,
                positions,
                0,
            );
        }
    }
}

/// 应用单个 Morph（支持递归，depth 用于防止无限循环）
///
/// 独立函数，通过拆分字段借用避免 clone 偏移数组的开销
fn apply_single_morph(
    morphs: &[Morph],
    material_morph_results: &mut [MaterialMorphResult],
    uv_morph_deltas: &mut [Vec2],
    morph_idx: usize,
    effective_weight: f32,
    bone_manager: &mut BoneManager,
    positions: &mut [Vec3],
    depth: u32,
) {
    if depth > MAX_GROUP_MORPH_DEPTH || effective_weight.abs() < MORPH_WEIGHT_EPSILON {
        return;
    }
    
    let morph = match morphs.get(morph_idx) {
        Some(m) => m,
        None => return,
    };
    
    match morph.morph_type {
        MorphType::Vertex => {
            apply_vertex_morph(&morph.vertex_offsets, effective_weight, positions);
        }
        MorphType::Bone => {
            apply_bone_morph(&morph.bone_offsets, effective_weight, bone_manager);
        }
        MorphType::Group => {
            // 需要收集子项信息后释放 morph 借用再递归
            let subs: Vec<(usize, f32)> = morph.group_offsets.iter()
                .filter_map(|sub| {
                    let sub_idx = sub.morph_index as usize;
                    if sub_idx < morphs.len() && sub_idx != morph_idx {
                        Some((sub_idx, effective_weight * sub.influence))
                    } else {
                        None
                    }
                })
                .collect();
            for (sub_idx, sub_weight) in subs {
                apply_single_morph(
                    morphs, material_morph_results, uv_morph_deltas,
                    sub_idx, sub_weight, bone_manager, positions, depth + 1,
                );
            }
        }
        MorphType::Flip => {
            let count = morph.group_offsets.len();
            if count > 0 {
                let w = effective_weight.clamp(0.0, 1.0);
                let index = ((w * count as f32).floor() as usize).min(count - 1);
                let sub = &morph.group_offsets[index];
                let sub_idx = sub.morph_index as usize;
                let sub_influence = sub.influence;
                if sub_idx < morphs.len() && sub_idx != morph_idx {
                    apply_single_morph(
                        morphs, material_morph_results, uv_morph_deltas,
                        sub_idx, sub_influence, bone_manager, positions, depth + 1,
                    );
                }
            }
        }
        MorphType::Material => {
            apply_material_morph(&morph.material_offsets, effective_weight, material_morph_results);
        }
        MorphType::Uv | MorphType::AdditionalUv1 => {
            apply_uv_morph(&morph.uv_offsets, effective_weight, uv_morph_deltas);
        }
        _ => {
            // AdditionalUv2/3/4, Impulse 暂不处理
        }
    }
}

/// 应用顶点 Morph
fn apply_vertex_morph(
    offsets: &[super::VertexMorphOffset],
    weight: f32,
    positions: &mut [Vec3],
) {
    for offset in offsets {
        let idx = offset.vertex_index as usize;
        if idx < positions.len() {
            positions[idx] += offset.offset * weight;
        }
    }
}

/// 应用骨骼 Morph
fn apply_bone_morph(
    offsets: &[super::BoneMorphOffset],
    weight: f32,
    bone_manager: &mut BoneManager,
) {
    for offset in offsets {
        let idx = offset.bone_index as usize;
        let translation = offset.translation * weight;
        // 确保四元数插值走最短路径：
        // identity = (0,0,0,1)，dot(identity, offset) = offset.w
        // 当 dot < 0 时取反 offset 以走短弧
        let (ox, oy, oz, ow) = if offset.rotation.w < 0.0 {
            (-offset.rotation.x, -offset.rotation.y, -offset.rotation.z, -offset.rotation.w)
        } else {
            (offset.rotation.x, offset.rotation.y, offset.rotation.z, offset.rotation.w)
        };
        let rotation = glam::Quat::from_xyzw(
            ox * weight,
            oy * weight,
            oz * weight,
            1.0 - (1.0 - ow) * weight,
        ).normalize();
        
        if let Some(bone) = bone_manager.get_bone_mut(idx) {
            bone.animation_translate += translation;
            bone.animation_rotate = bone.animation_rotate * rotation;
        }
    }
}

/// 应用材质 Morph 偏移
///
/// material_index == -1 表示应用到所有材质。
/// operation: 0=乘算（累积到 mul）, 1=加算（累积到 add）
fn apply_material_morph(
    offsets: &[MaterialMorphOffset],
    weight: f32,
    material_morph_results: &mut [MaterialMorphResult],
) {
    for offset in offsets {
        if offset.material_index < 0 {
            for result in material_morph_results.iter_mut() {
                if offset.operation == 0 {
                    result.mul.apply_multiply(offset, weight);
                } else {
                    result.add.apply_additive(offset, weight);
                }
            }
        } else {
            let mat_idx = offset.material_index as usize;
            if let Some(result) = material_morph_results.get_mut(mat_idx) {
                if offset.operation == 0 {
                    result.mul.apply_multiply(offset, weight);
                } else {
                    result.add.apply_additive(offset, weight);
                }
            }
        }
    }
}

/// 应用 UV Morph 偏移
fn apply_uv_morph(
    offsets: &[super::UvMorphOffset],
    weight: f32,
    uv_morph_deltas: &mut [Vec2],
) {
    for offset in offsets {
        let idx = offset.vertex_index as usize;
        if idx < uv_morph_deltas.len() {
            // 只取 x, y 分量作为 UV 偏移
            uv_morph_deltas[idx] += Vec2::new(offset.offset.x, offset.offset.y) * weight;
        }
    }
}

impl Default for MorphManager {
    fn default() -> Self {
        Self::new()
    }
}
