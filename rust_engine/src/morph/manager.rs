//! Morph 管理器
//!
//! 实现 MMD Morph 系统：Vertex / Bone / Group / Flip / Material / UV Morph。
//! 支持 Group/Flip Morph 递归展开，并提供 GPU 蒙皮路径的有效权重计算。

use glam::{Vec2, Vec3, Vec4};
use std::collections::HashMap;

use super::{MaterialMorphOffset, Morph, MorphType};
use crate::skeleton::BoneManager;

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

    /// 乘算：lerp(1.0, offset, weight) 累乘
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

    /// 加算
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

/// 材质 Morph 计算结果（渲染时：final = base * mul + add）
#[derive(Clone, Debug)]
pub struct MaterialMorphResult {
    pub mul: MaterialMorphValues,
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
    pub fn reset(&mut self) {
        *self = Self::default();
    }
}

#[derive(Default)]
struct VertexMorphSoaCache {
    indices: Vec<u32>,
    dx: Vec<f32>,
    dy: Vec<f32>,
    dz: Vec<f32>,
}

impl VertexMorphSoaCache {
    fn from_offsets(offsets: &[super::VertexMorphOffset]) -> Option<Self> {
        if offsets.is_empty() {
            return None;
        }

        let mut cache = Self {
            indices: Vec::with_capacity(offsets.len()),
            dx: Vec::with_capacity(offsets.len()),
            dy: Vec::with_capacity(offsets.len()),
            dz: Vec::with_capacity(offsets.len()),
        };

        for offset in offsets {
            cache.indices.push(offset.vertex_index);
            cache.dx.push(offset.offset.x);
            cache.dy.push(offset.offset.y);
            cache.dz.push(offset.offset.z);
        }

        Some(cache)
    }

    fn memory_usage(&self) -> u64 {
        use std::mem::size_of;
        (self.indices.capacity() * size_of::<u32>()) as u64
            + (self.dx.capacity() * size_of::<f32>()) as u64
            + (self.dy.capacity() * size_of::<f32>()) as u64
            + (self.dz.capacity() * size_of::<f32>()) as u64
    }
}

#[derive(Default)]
struct UvMorphSoaCache {
    indices: Vec<u32>,
    du: Vec<f32>,
    dv: Vec<f32>,
}

impl UvMorphSoaCache {
    fn from_offsets(offsets: &[super::UvMorphOffset]) -> Option<Self> {
        if offsets.is_empty() {
            return None;
        }

        let mut cache = Self {
            indices: Vec::with_capacity(offsets.len()),
            du: Vec::with_capacity(offsets.len()),
            dv: Vec::with_capacity(offsets.len()),
        };

        for offset in offsets {
            cache.indices.push(offset.vertex_index);
            cache.du.push(offset.offset.x);
            cache.dv.push(offset.offset.y);
        }

        Some(cache)
    }

    fn memory_usage(&self) -> u64 {
        use std::mem::size_of;
        (self.indices.capacity() * size_of::<u32>()) as u64
            + (self.du.capacity() * size_of::<f32>()) as u64
            + (self.dv.capacity() * size_of::<f32>()) as u64
    }
}

#[derive(Default)]
struct MorphRuntimeCache {
    vertex: Option<VertexMorphSoaCache>,
    uv: Option<UvMorphSoaCache>,
}

impl MorphRuntimeCache {
    fn from_morph(morph: &Morph) -> Self {
        Self {
            vertex: VertexMorphSoaCache::from_offsets(&morph.vertex_offsets),
            uv: UvMorphSoaCache::from_offsets(&morph.uv_offsets),
        }
    }

    fn memory_usage(&self) -> u64 {
        self.vertex.as_ref().map_or(0, VertexMorphSoaCache::memory_usage)
            + self.uv.as_ref().map_or(0, UvMorphSoaCache::memory_usage)
    }
}

#[derive(Clone, Copy, Debug)]
struct PreparedLeafMorph {
    morph_idx: usize,
    effective_weight: f32,
}

#[derive(Clone, Copy, Debug)]
struct PendingMorph {
    morph_idx: usize,
    effective_weight: f32,
    depth: u32,
}

/// Morph 管理器
pub struct MorphManager {
    morphs: Vec<Morph>,
    name_to_index: HashMap<String, usize>,
    material_morph_results: Vec<MaterialMorphResult>,
    material_count: usize,
    uv_morph_deltas: Vec<Vec2>,
    has_active_uv_morphs: bool,
    has_active_vertex_morphs: bool,
    vertex_count: usize,
    runtime_caches: Vec<MorphRuntimeCache>,
    runtime_caches_dirty: bool,
    prepared_leaf_morphs: Vec<PreparedLeafMorph>,
    traversal_stack: Vec<PendingMorph>,
}

impl MorphManager {
    pub fn new() -> Self {
        Self {
            morphs: Vec::new(),
            name_to_index: HashMap::new(),
            material_morph_results: Vec::new(),
            material_count: 0,
            uv_morph_deltas: Vec::new(),
            has_active_uv_morphs: false,
            has_active_vertex_morphs: false,
            vertex_count: 0,
            runtime_caches: Vec::new(),
            runtime_caches_dirty: false,
            prepared_leaf_morphs: Vec::new(),
            traversal_stack: Vec::new(),
        }
    }

    pub fn set_material_count(&mut self, count: usize) {
        self.material_count = count;
        self.material_morph_results = vec![MaterialMorphResult::default(); count];
    }

    pub fn set_vertex_count(&mut self, count: usize) {
        self.vertex_count = count;
        self.uv_morph_deltas = vec![Vec2::ZERO; count];
    }

    pub fn add_morph(&mut self, morph: Morph) {
        let index = self.morphs.len();
        self.name_to_index.insert(morph.name.clone(), index);
        self.morphs.push(morph);
        self.runtime_caches_dirty = true;
    }

    pub fn find_morph_by_name(&self, name: &str) -> Option<usize> {
        self.name_to_index.get(name).copied()
    }

    pub fn morph_count(&self) -> usize {
        self.morphs.len()
    }

    pub fn get_morph(&self, index: usize) -> Option<&Morph> {
        self.morphs.get(index)
    }

    pub fn get_morph_mut(&mut self, index: usize) -> Option<&mut Morph> {
        self.runtime_caches_dirty = true;
        self.morphs.get_mut(index)
    }

    pub fn set_morph_weight(&mut self, index: usize, weight: f32) {
        if let Some(morph) = self.morphs.get_mut(index) {
            morph.set_weight(weight);
        }
    }

    pub fn get_morph_weight(&self, index: usize) -> f32 {
        self.morphs.get(index).map(|m| m.weight).unwrap_or(0.0)
    }

    pub fn reset_all_weights(&mut self) {
        for morph in &mut self.morphs {
            morph.reset();
        }
    }

    pub fn get_material_morph_result(&self, material_index: usize) -> Option<&MaterialMorphResult> {
        self.material_morph_results.get(material_index)
    }

    pub fn get_material_morph_results(&self) -> &[MaterialMorphResult] {
        &self.material_morph_results
    }

    pub fn get_uv_morph_deltas(&self) -> &[Vec2] {
        &self.uv_morph_deltas
    }

    pub fn has_active_uv_morphs(&self) -> bool {
        self.has_active_uv_morphs
    }

    pub fn has_active_vertex_morphs(&self) -> bool {
        self.has_active_vertex_morphs
    }

    /// 应用已线性化的叶子 Morph。
    ///
    /// 调用方应先通过 [`MorphManager::compute_effective_weights_into()`](rust_engine/src/morph/manager.rs:349)
    /// 准备当前帧的有效权重与叶子列表，以复用同一份 Group/Flip 展开结果。
    pub fn apply_prepared_morphs(&mut self, bone_manager: &mut BoneManager, positions: &mut [Vec3]) {
        self.ensure_runtime_caches();
        self.reset_runtime_results();

        let morphs = &self.morphs;
        let runtime_caches = &self.runtime_caches;
        let prepared_leaf_morphs = &self.prepared_leaf_morphs;
        let material_morph_results = &mut self.material_morph_results;
        let uv_morph_deltas = &mut self.uv_morph_deltas;

        for prepared in prepared_leaf_morphs.iter().copied() {
            apply_leaf_morph(
                morphs,
                runtime_caches,
                material_morph_results,
                uv_morph_deltas,
                prepared.morph_idx,
                prepared.effective_weight,
                bone_manager,
                positions,
            );
        }
    }

    /// 计算所有 Morph 的有效权重，并线性化当前帧叶子 Morph 顺序。
    ///
    /// `out` 保存按 Morph 索引聚合后的有效权重，供 GPU 顶点/UV Morph 同步复用；
    /// `prepared_leaf_morphs` 保存保持原有深度优先语义的线性叶子列表，供 CPU 路径直接顺序应用。
    pub fn compute_effective_weights_into(&mut self, out: &mut [f32]) {
        self.ensure_runtime_caches();
        out.fill(0.0);
        self.prepared_leaf_morphs.clear();
        self.traversal_stack.clear();
        self.has_active_uv_morphs = false;
        self.has_active_vertex_morphs = false;

        let morph_count = self.morphs.len();
        for morph_idx in (0..morph_count).rev() {
            let weight = self.morphs[morph_idx].weight;
            if weight.abs() > MORPH_WEIGHT_EPSILON {
                self.traversal_stack.push(PendingMorph {
                    morph_idx,
                    effective_weight: weight,
                    depth: 0,
                });
            }
        }

        while let Some(pending) = self.traversal_stack.pop() {
            if pending.depth > MAX_GROUP_MORPH_DEPTH
                || pending.effective_weight.abs() < MORPH_WEIGHT_EPSILON
            {
                continue;
            }

            let morph = match self.morphs.get(pending.morph_idx) {
                Some(morph) => morph,
                None => continue,
            };

            match morph.morph_type {
                MorphType::Group => {
                    for sub in morph.group_offsets.iter().rev() {
                        let sub_idx = sub.morph_index as usize;
                        if sub_idx < morph_count && sub_idx != pending.morph_idx {
                            self.traversal_stack.push(PendingMorph {
                                morph_idx: sub_idx,
                                effective_weight: pending.effective_weight * sub.influence,
                                depth: pending.depth + 1,
                            });
                        }
                    }
                }
                MorphType::Flip => {
                    let count = morph.group_offsets.len();
                    if count > 0 {
                        let w = pending.effective_weight.clamp(0.0, 1.0);
                        let index = ((w * count as f32).floor() as usize).min(count - 1);
                        let sub = &morph.group_offsets[index];
                        let sub_idx = sub.morph_index as usize;
                        if sub_idx < morph_count && sub_idx != pending.morph_idx {
                            self.traversal_stack.push(PendingMorph {
                                morph_idx: sub_idx,
                                effective_weight: sub.influence,
                                depth: pending.depth + 1,
                            });
                        }
                    }
                }
                MorphType::Vertex => {
                    if !morph.vertex_offsets.is_empty() {
                        self.has_active_vertex_morphs = true;
                    }
                    if pending.morph_idx < out.len() {
                        out[pending.morph_idx] += pending.effective_weight;
                    }
                    self.prepared_leaf_morphs.push(PreparedLeafMorph {
                        morph_idx: pending.morph_idx,
                        effective_weight: pending.effective_weight,
                    });
                }
                MorphType::Uv | MorphType::AdditionalUv1 => {
                    if !morph.uv_offsets.is_empty() {
                        self.has_active_uv_morphs = true;
                    }
                    if pending.morph_idx < out.len() {
                        out[pending.morph_idx] += pending.effective_weight;
                    }
                    self.prepared_leaf_morphs.push(PreparedLeafMorph {
                        morph_idx: pending.morph_idx,
                        effective_weight: pending.effective_weight,
                    });
                }
                _ => {
                    if pending.morph_idx < out.len() {
                        out[pending.morph_idx] += pending.effective_weight;
                    }
                    self.prepared_leaf_morphs.push(PreparedLeafMorph {
                        morph_idx: pending.morph_idx,
                        effective_weight: pending.effective_weight,
                    });
                }
            }
        }
    }

    pub fn memory_usage(&self) -> u64 {
        use std::mem::size_of;
        let mut total: u64 = 0;
        total += (self.morphs.capacity() * size_of::<Morph>()) as u64;
        total +=
            (self.name_to_index.capacity() * (size_of::<String>() + size_of::<usize>())) as u64;
        total += (self.material_morph_results.capacity() * size_of::<MaterialMorphResult>()) as u64;
        total += (self.uv_morph_deltas.capacity() * size_of::<Vec2>()) as u64;
        total += (self.runtime_caches.capacity() * size_of::<MorphRuntimeCache>()) as u64;
        total += (self.prepared_leaf_morphs.capacity() * size_of::<PreparedLeafMorph>()) as u64;
        total += (self.traversal_stack.capacity() * size_of::<PendingMorph>()) as u64;
        for cache in &self.runtime_caches {
            total += cache.memory_usage();
        }
        total
    }

    fn reset_runtime_results(&mut self) {
        for result in &mut self.material_morph_results {
            result.reset();
        }
        for delta in &mut self.uv_morph_deltas {
            *delta = Vec2::ZERO;
        }
    }

    fn ensure_runtime_caches(&mut self) {
        if !self.runtime_caches_dirty && self.runtime_caches.len() == self.morphs.len() {
            return;
        }

        self.runtime_caches = self
            .morphs
            .iter()
            .map(MorphRuntimeCache::from_morph)
            .collect();
        self.runtime_caches_dirty = false;
    }
}

fn apply_leaf_morph(
    morphs: &[Morph],
    runtime_caches: &[MorphRuntimeCache],
    material_morph_results: &mut [MaterialMorphResult],
    uv_morph_deltas: &mut [Vec2],
    morph_idx: usize,
    effective_weight: f32,
    bone_manager: &mut BoneManager,
    positions: &mut [Vec3],
) {
    if effective_weight.abs() < MORPH_WEIGHT_EPSILON {
        return;
    }

    let morph = match morphs.get(morph_idx) {
        Some(m) => m,
        None => return,
    };

    match morph.morph_type {
        MorphType::Vertex => {
            if let Some(cache) = runtime_caches.get(morph_idx).and_then(|cache| cache.vertex.as_ref()) {
                apply_vertex_morph_cached(cache, effective_weight, positions);
            } else {
                apply_vertex_morph(&morph.vertex_offsets, effective_weight, positions);
            }
        }
        MorphType::Bone => {
            apply_bone_morph(&morph.bone_offsets, effective_weight, bone_manager);
        }
        MorphType::Material => {
            apply_material_morph(
                &morph.material_offsets,
                effective_weight,
                material_morph_results,
            );
        }
        MorphType::Uv | MorphType::AdditionalUv1 => {
            if let Some(cache) = runtime_caches.get(morph_idx).and_then(|cache| cache.uv.as_ref()) {
                apply_uv_morph_cached(cache, effective_weight, uv_morph_deltas);
            } else {
                apply_uv_morph(&morph.uv_offsets, effective_weight, uv_morph_deltas);
            }
        }
        _ => {
            // AdditionalUv2/3/4, Impulse 暂不处理
        }
    }
}

fn apply_vertex_morph(offsets: &[super::VertexMorphOffset], weight: f32, positions: &mut [Vec3]) {
    for offset in offsets {
        let idx = offset.vertex_index as usize;
        if idx < positions.len() {
            positions[idx] += offset.offset * weight;
        }
    }
}

#[inline]
fn apply_vertex_morph_cached(cache: &VertexMorphSoaCache, weight: f32, positions: &mut [Vec3]) {
    let positions_len = positions.len();
    let mut i = 0;
    let len = cache.indices.len();

    while i + 3 < len {
        apply_vertex_morph_cached_one(
            cache.indices[i] as usize,
            cache.dx[i],
            cache.dy[i],
            cache.dz[i],
            weight,
            positions,
            positions_len,
        );
        apply_vertex_morph_cached_one(
            cache.indices[i + 1] as usize,
            cache.dx[i + 1],
            cache.dy[i + 1],
            cache.dz[i + 1],
            weight,
            positions,
            positions_len,
        );
        apply_vertex_morph_cached_one(
            cache.indices[i + 2] as usize,
            cache.dx[i + 2],
            cache.dy[i + 2],
            cache.dz[i + 2],
            weight,
            positions,
            positions_len,
        );
        apply_vertex_morph_cached_one(
            cache.indices[i + 3] as usize,
            cache.dx[i + 3],
            cache.dy[i + 3],
            cache.dz[i + 3],
            weight,
            positions,
            positions_len,
        );
        i += 4;
    }

    while i < len {
        apply_vertex_morph_cached_one(
            cache.indices[i] as usize,
            cache.dx[i],
            cache.dy[i],
            cache.dz[i],
            weight,
            positions,
            positions_len,
        );
        i += 1;
    }
}

#[inline(always)]
fn apply_vertex_morph_cached_one(
    idx: usize,
    dx: f32,
    dy: f32,
    dz: f32,
    weight: f32,
    positions: &mut [Vec3],
    positions_len: usize,
) {
    if idx >= positions_len {
        return;
    }

    let position = &mut positions[idx];
    position.x = dx.mul_add(weight, position.x);
    position.y = dy.mul_add(weight, position.y);
    position.z = dz.mul_add(weight, position.z);
}

fn apply_bone_morph(
    offsets: &[super::BoneMorphOffset],
    weight: f32,
    bone_manager: &mut BoneManager,
) {
    for offset in offsets {
        let idx = offset.bone_index as usize;
        let translation = offset.translation * weight;
        // 四元数短弧插值：dot < 0 时取反
        let (ox, oy, oz, ow) = if offset.rotation.w < 0.0 {
            (
                -offset.rotation.x,
                -offset.rotation.y,
                -offset.rotation.z,
                -offset.rotation.w,
            )
        } else {
            (
                offset.rotation.x,
                offset.rotation.y,
                offset.rotation.z,
                offset.rotation.w,
            )
        };
        let rotation = glam::Quat::from_xyzw(
            ox * weight,
            oy * weight,
            oz * weight,
            1.0 - (1.0 - ow) * weight,
        )
        .normalize();

        if let Some(bone) = bone_manager.get_bone_mut(idx) {
            bone.animation_translate += translation;
            bone.animation_rotate = bone.animation_rotate * rotation;
        }
    }
}

/// material_index == -1 表示应用到所有材质
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

fn apply_uv_morph(offsets: &[super::UvMorphOffset], weight: f32, uv_morph_deltas: &mut [Vec2]) {
    for offset in offsets {
        let idx = offset.vertex_index as usize;
        if idx < uv_morph_deltas.len() {
            uv_morph_deltas[idx] += Vec2::new(offset.offset.x, offset.offset.y) * weight;
        }
    }
}

#[inline]
fn apply_uv_morph_cached(cache: &UvMorphSoaCache, weight: f32, uv_morph_deltas: &mut [Vec2]) {
    let uv_len = uv_morph_deltas.len();
    let mut i = 0;
    let len = cache.indices.len();

    while i + 3 < len {
        apply_uv_morph_cached_one(
            cache.indices[i] as usize,
            cache.du[i],
            cache.dv[i],
            weight,
            uv_morph_deltas,
            uv_len,
        );
        apply_uv_morph_cached_one(
            cache.indices[i + 1] as usize,
            cache.du[i + 1],
            cache.dv[i + 1],
            weight,
            uv_morph_deltas,
            uv_len,
        );
        apply_uv_morph_cached_one(
            cache.indices[i + 2] as usize,
            cache.du[i + 2],
            cache.dv[i + 2],
            weight,
            uv_morph_deltas,
            uv_len,
        );
        apply_uv_morph_cached_one(
            cache.indices[i + 3] as usize,
            cache.du[i + 3],
            cache.dv[i + 3],
            weight,
            uv_morph_deltas,
            uv_len,
        );
        i += 4;
    }

    while i < len {
        apply_uv_morph_cached_one(
            cache.indices[i] as usize,
            cache.du[i],
            cache.dv[i],
            weight,
            uv_morph_deltas,
            uv_len,
        );
        i += 1;
    }
}

#[inline(always)]
fn apply_uv_morph_cached_one(
    idx: usize,
    du: f32,
    dv: f32,
    weight: f32,
    uv_morph_deltas: &mut [Vec2],
    uv_len: usize,
) {
    if idx >= uv_len {
        return;
    }

    let delta = &mut uv_morph_deltas[idx];
    delta.x = du.mul_add(weight, delta.x);
    delta.y = dv.mul_add(weight, delta.y);
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::morph::{
        BoneMorphOffset, GroupMorphOffset, MaterialMorphOffset, UvMorphOffset,
        VertexMorphOffset,
    };
    use crate::skeleton::BoneLink;

    #[test]
    fn prepared_morph_pipeline_should_match_legacy_recursive_semantics() {
        let mut manager = MorphManager::new();
        manager.set_vertex_count(3);
        manager.set_material_count(2);

        let mut vertex = Morph::new("vertex".to_string(), MorphType::Vertex);
        vertex.vertex_offsets.push(VertexMorphOffset {
            vertex_index: 0,
            offset: Vec3::new(0.12, -0.04, 0.08),
        });
        vertex.vertex_offsets.push(VertexMorphOffset {
            vertex_index: 2,
            offset: Vec3::new(-0.03, 0.05, -0.01),
        });
        vertex.set_weight(0.2);
        manager.add_morph(vertex);

        let mut uv = Morph::new("uv".to_string(), MorphType::Uv);
        uv.uv_offsets.push(UvMorphOffset {
            vertex_index: 1,
            offset: Vec4::new(0.03, -0.02, 0.0, 0.0),
        });
        manager.add_morph(uv);

        let mut material = Morph::new("material".to_string(), MorphType::Material);
        material.material_offsets.push(MaterialMorphOffset {
            material_index: -1,
            operation: 0,
            diffuse: Vec4::new(1.2, 0.8, 1.1, 1.0),
            specular: Vec3::new(0.9, 1.1, 1.05),
            specular_strength: 1.25,
            ambient: Vec3::new(1.1, 0.95, 1.0),
            edge_color: Vec4::new(1.0, 1.15, 0.85, 1.0),
            edge_size: 0.9,
            texture_tint: Vec4::new(1.05, 0.95, 1.1, 1.0),
            environment_tint: Vec4::new(0.9, 1.08, 1.02, 1.0),
            toon_tint: Vec4::new(1.0, 0.88, 1.12, 1.0),
        });
        material.set_weight(0.35);
        manager.add_morph(material);

        let mut bone = Morph::new("bone".to_string(), MorphType::Bone);
        let rotation = glam::Quat::from_rotation_z(0.35);
        bone.bone_offsets.push(BoneMorphOffset {
            bone_index: 0,
            translation: Vec3::new(0.2, -0.1, 0.05),
            rotation: Vec4::new(rotation.x, rotation.y, rotation.z, rotation.w),
        });
        bone.set_weight(0.25);
        manager.add_morph(bone);

        let mut group = Morph::new("group".to_string(), MorphType::Group);
        group.group_offsets.extend([
            GroupMorphOffset {
                morph_index: 0,
                influence: 0.5,
            },
            GroupMorphOffset {
                morph_index: 1,
                influence: 1.0,
            },
            GroupMorphOffset {
                morph_index: 2,
                influence: 0.4,
            },
            GroupMorphOffset {
                morph_index: 3,
                influence: -0.25,
            },
        ]);
        group.set_weight(0.8);
        manager.add_morph(group);

        let mut flip = Morph::new("flip".to_string(), MorphType::Flip);
        flip.group_offsets.extend([
            GroupMorphOffset {
                morph_index: 0,
                influence: 0.2,
            },
            GroupMorphOffset {
                morph_index: 2,
                influence: 0.75,
            },
            GroupMorphOffset {
                morph_index: 3,
                influence: 0.6,
            },
        ]);
        flip.set_weight(0.66);
        manager.add_morph(flip);

        let mut nested = Morph::new("nested".to_string(), MorphType::Group);
        nested.group_offsets.extend([
            GroupMorphOffset {
                morph_index: 4,
                influence: 0.5,
            },
            GroupMorphOffset {
                morph_index: 5,
                influence: 1.0,
            },
        ]);
        nested.set_weight(0.5);
        manager.add_morph(nested);

        let base_positions = vec![
            Vec3::new(0.0, 0.0, 0.0),
            Vec3::new(1.0, -1.0, 0.5),
            Vec3::new(-0.5, 0.25, 1.5),
        ];

        let mut actual_positions = base_positions.clone();
        let mut actual_bones = make_test_bone_manager();
        let mut actual_weights = vec![0.0; manager.morph_count()];
        manager.compute_effective_weights_into(&mut actual_weights);
        manager.apply_prepared_morphs(&mut actual_bones, &mut actual_positions);

        let actual_uv = manager.get_uv_morph_deltas().to_vec();
        let actual_material = manager.get_material_morph_results().to_vec();

        let mut expected_positions = base_positions.clone();
        let mut expected_bones = make_test_bone_manager();
        let mut expected_weights = vec![0.0; manager.morph_count()];
        legacy_compute_effective_weights(&manager.morphs, &mut expected_weights);

        let mut expected_material = vec![MaterialMorphResult::default(); 2];
        let mut expected_uv = vec![Vec2::ZERO; base_positions.len()];
        let mut expected_has_active_uv = false;
        legacy_apply_all_morphs(
            &manager.morphs,
            &mut expected_material,
            &mut expected_uv,
            &mut expected_has_active_uv,
            &mut expected_bones,
            &mut expected_positions,
        );

        assert_eq!(actual_weights, expected_weights);
        assert_eq!(manager.has_active_uv_morphs(), expected_has_active_uv);
        assert!(manager.has_active_vertex_morphs());

        for (actual, expected) in actual_positions.iter().zip(expected_positions.iter()) {
            assert!(actual.abs_diff_eq(*expected, 1e-6));
        }
        for (actual, expected) in actual_uv.iter().zip(expected_uv.iter()) {
            assert!(actual.abs_diff_eq(*expected, 1e-6));
        }
        for (actual, expected) in actual_material.iter().zip(expected_material.iter()) {
            assert_material_result_eq(actual, expected);
        }

        let actual_bone = actual_bones.get_bone(0).expect("actual bone");
        let expected_bone = expected_bones.get_bone(0).expect("expected bone");
        assert!(actual_bone
            .animation_translate
            .abs_diff_eq(expected_bone.animation_translate, 1e-6));
        assert!(actual_bone
            .animation_rotate
            .dot(expected_bone.animation_rotate)
            .abs()
            > 1.0 - 1e-6);
    }

    fn make_test_bone_manager() -> BoneManager {
        let mut bone_manager = BoneManager::new();
        bone_manager.add_bone(BoneLink::new("root".to_string()));
        bone_manager.build_hierarchy();
        bone_manager
    }

    fn legacy_compute_effective_weights(morphs: &[Morph], out: &mut [f32]) {
        out.fill(0.0);
        for (morph_idx, morph) in morphs.iter().enumerate() {
            if morph.weight.abs() > MORPH_WEIGHT_EPSILON {
                legacy_accumulate_effective_weight(morphs, morph_idx, morph.weight, out, 0);
            }
        }
    }

    fn legacy_accumulate_effective_weight(
        morphs: &[Morph],
        morph_idx: usize,
        effective_weight: f32,
        out: &mut [f32],
        depth: u32,
    ) {
        if depth > MAX_GROUP_MORPH_DEPTH || effective_weight.abs() < MORPH_WEIGHT_EPSILON {
            return;
        }

        let morph = match morphs.get(morph_idx) {
            Some(morph) => morph,
            None => return,
        };

        match morph.morph_type {
            MorphType::Group => {
                for sub in &morph.group_offsets {
                    let sub_idx = sub.morph_index as usize;
                    if sub_idx < morphs.len() && sub_idx != morph_idx {
                        legacy_accumulate_effective_weight(
                            morphs,
                            sub_idx,
                            effective_weight * sub.influence,
                            out,
                            depth + 1,
                        );
                    }
                }
            }
            MorphType::Flip => {
                let count = morph.group_offsets.len();
                if count > 0 {
                    let w = effective_weight.clamp(0.0, 1.0);
                    let index = ((w * count as f32).floor() as usize).min(count - 1);
                    let sub = &morph.group_offsets[index];
                    let sub_idx = sub.morph_index as usize;
                    if sub_idx < morphs.len() && sub_idx != morph_idx {
                        legacy_accumulate_effective_weight(
                            morphs,
                            sub_idx,
                            sub.influence,
                            out,
                            depth + 1,
                        );
                    }
                }
            }
            _ => {
                if morph_idx < out.len() {
                    out[morph_idx] += effective_weight;
                }
            }
        }
    }

    fn legacy_apply_all_morphs(
        morphs: &[Morph],
        material_morph_results: &mut [MaterialMorphResult],
        uv_morph_deltas: &mut [Vec2],
        has_active_uv_morphs: &mut bool,
        bone_manager: &mut BoneManager,
        positions: &mut [Vec3],
    ) {
        for result in material_morph_results.iter_mut() {
            result.reset();
        }
        for delta in uv_morph_deltas.iter_mut() {
            *delta = Vec2::ZERO;
        }
        *has_active_uv_morphs = false;

        for (morph_idx, morph) in morphs.iter().enumerate() {
            if morph.weight.abs() > MORPH_WEIGHT_EPSILON {
                legacy_apply_single_morph(
                    morphs,
                    material_morph_results,
                    uv_morph_deltas,
                    has_active_uv_morphs,
                    morph_idx,
                    morph.weight,
                    bone_manager,
                    positions,
                    0,
                );
            }
        }
    }

    fn legacy_apply_single_morph(
        morphs: &[Morph],
        material_morph_results: &mut [MaterialMorphResult],
        uv_morph_deltas: &mut [Vec2],
        has_active_uv_morphs: &mut bool,
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
            Some(morph) => morph,
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
                for sub in &morph.group_offsets {
                    let sub_idx = sub.morph_index as usize;
                    if sub_idx < morphs.len() && sub_idx != morph_idx {
                        legacy_apply_single_morph(
                            morphs,
                            material_morph_results,
                            uv_morph_deltas,
                            has_active_uv_morphs,
                            sub_idx,
                            effective_weight * sub.influence,
                            bone_manager,
                            positions,
                            depth + 1,
                        );
                    }
                }
            }
            MorphType::Flip => {
                let count = morph.group_offsets.len();
                if count > 0 {
                    let w = effective_weight.clamp(0.0, 1.0);
                    let index = ((w * count as f32).floor() as usize).min(count - 1);
                    let sub = &morph.group_offsets[index];
                    let sub_idx = sub.morph_index as usize;
                    if sub_idx < morphs.len() && sub_idx != morph_idx {
                        legacy_apply_single_morph(
                            morphs,
                            material_morph_results,
                            uv_morph_deltas,
                            has_active_uv_morphs,
                            sub_idx,
                            sub.influence,
                            bone_manager,
                            positions,
                            depth + 1,
                        );
                    }
                }
            }
            MorphType::Material => {
                apply_material_morph(
                    &morph.material_offsets,
                    effective_weight,
                    material_morph_results,
                );
            }
            MorphType::Uv | MorphType::AdditionalUv1 => {
                if !morph.uv_offsets.is_empty() {
                    *has_active_uv_morphs = true;
                }
                apply_uv_morph(&morph.uv_offsets, effective_weight, uv_morph_deltas);
            }
            _ => {}
        }
    }

    fn assert_material_result_eq(actual: &MaterialMorphResult, expected: &MaterialMorphResult) {
        assert!(actual.mul.diffuse.abs_diff_eq(expected.mul.diffuse, 1e-6));
        assert!(actual.mul.specular.abs_diff_eq(expected.mul.specular, 1e-6));
        assert!((actual.mul.specular_strength - expected.mul.specular_strength).abs() < 1e-6);
        assert!(actual.mul.ambient.abs_diff_eq(expected.mul.ambient, 1e-6));
        assert!(actual.mul.edge_color.abs_diff_eq(expected.mul.edge_color, 1e-6));
        assert!((actual.mul.edge_size - expected.mul.edge_size).abs() < 1e-6);
        assert!(actual.mul.texture_tint.abs_diff_eq(expected.mul.texture_tint, 1e-6));
        assert!(actual
            .mul
            .environment_tint
            .abs_diff_eq(expected.mul.environment_tint, 1e-6));
        assert!(actual.mul.toon_tint.abs_diff_eq(expected.mul.toon_tint, 1e-6));

        assert!(actual.add.diffuse.abs_diff_eq(expected.add.diffuse, 1e-6));
        assert!(actual.add.specular.abs_diff_eq(expected.add.specular, 1e-6));
        assert!((actual.add.specular_strength - expected.add.specular_strength).abs() < 1e-6);
        assert!(actual.add.ambient.abs_diff_eq(expected.add.ambient, 1e-6));
        assert!(actual.add.edge_color.abs_diff_eq(expected.add.edge_color, 1e-6));
        assert!((actual.add.edge_size - expected.add.edge_size).abs() < 1e-6);
        assert!(actual.add.texture_tint.abs_diff_eq(expected.add.texture_tint, 1e-6));
        assert!(actual
            .add
            .environment_tint
            .abs_diff_eq(expected.add.environment_tint, 1e-6));
        assert!(actual.add.toon_tint.abs_diff_eq(expected.add.toon_tint, 1e-6));
    }
}

impl Default for MorphManager {
    fn default() -> Self {
        Self::new()
    }
}
