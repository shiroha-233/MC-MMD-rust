//! 骨骼集合 - 参考 nphysics Multibody 设计

use glam::{Vec3, Quat, Mat4};
use std::collections::{HashMap, HashSet};

use super::{BoneLink, IkSolver};


/// 骨骼集合 - 类似 nphysics Multibody
///
/// 设计原则（参考 nphysics）：
/// - 所有骨骼存储在连续数组中
/// - 使用排序索引确保父骨骼先于子骨骼更新
/// - 变换传播：local_to_world = parent.local_to_world * local_to_parent
pub struct BoneSet {
    /// 骨骼数组
    links: Vec<BoneLink>,
    
    /// 名称到索引的映射
    name_to_index: HashMap<String, usize>,
    
    /// 按变换层级排序的索引
    sorted_indices: Vec<usize>,
    
    /// IK 求解器列表
    ik_solvers: Vec<IkSolver>,
    
    /// 蒙皮矩阵缓存
    skinning_matrices: Vec<Mat4>,
    
    /// 物理驱动的骨骼索引集合
    physics_bone_indices: HashSet<usize>,
    
    /// 子骨骼缓存（parent_index -> children_indices）
    children_cache: Vec<Vec<usize>>,
    
    /// 更新标志
    needs_hierarchy_update: bool,
    
    /// VRM 标志（VMD 旋转/平移需要额外坐标系转换）
    is_vrm: bool,
}

impl BoneSet {
    /// 创建空的骨骼集合
    pub fn new() -> Self {
        Self {
            links: Vec::new(),
            name_to_index: HashMap::new(),
            sorted_indices: Vec::new(),
            ik_solvers: Vec::new(),
            skinning_matrices: Vec::new(),
            physics_bone_indices: HashSet::new(),
            children_cache: Vec::new(),
            needs_hierarchy_update: true,
            is_vrm: false,
        }
    }
    
    // ========================================
    // 骨骼添加（类似 nphysics add_link）
    // ========================================
    
    /// 添加骨骼
    pub fn add_bone(&mut self, mut bone: BoneLink) {
        let index = self.links.len();
        bone.internal_id = index;
        self.name_to_index.insert(bone.name.clone(), index);
        self.links.push(bone);
        self.needs_hierarchy_update = true;
    }
    
    /// 添加骨骼（别名）
    #[inline]
    pub fn add_link(&mut self, bone: BoneLink) {
        self.add_bone(bone);
    }
    
    // ========================================
    // 层次结构构建
    // ========================================
    
    /// 构建骨骼层次结构
    ///
    /// 参考 nphysics Multibody 的初始化流程：
    /// 1. 计算排序索引
    /// 2. 计算 body_shift（相对于父骨骼的偏移）
    /// 3. 计算初始全局变换
    /// 4. 计算逆绑定矩阵
    /// 5. 构建子骨骼缓存
    /// 6. 创建 IK 求解器
    pub fn build_hierarchy(&mut self) {
        let bone_count = self.links.len();
        if bone_count == 0 {
            return;
        }
        
        // 1. 按变换层级排序
        self.sorted_indices = (0..bone_count).collect();
        self.sorted_indices.sort_by(|&a, &b| {
            self.links[a].transform_level.cmp(&self.links[b].transform_level)
        });
        
        // 2. 计算 body_shift 和初始变换
        for i in 0..bone_count {
            let pos = self.links[i].initial_position;
            let parent_idx = self.links[i].parent_index;
            
            // body_shift = 相对于父骨骼的偏移
            let body_shift = if parent_idx >= 0 && (parent_idx as usize) < bone_count {
                let parent_pos = self.links[parent_idx as usize].initial_position;
                pos - parent_pos
            } else {
                pos
            };
            self.links[i].body_shift = body_shift;
            
            // 初始全局变换
            let init_global = Mat4::from_translation(pos);
            self.links[i].local_to_world = init_global;
            
            // 逆绑定矩阵 = inverse(初始全局变换)
            self.links[i].inverse_init = init_global.inverse();
            
            // 本地变换
            self.links[i].local_to_parent = Mat4::from_translation(body_shift);
        }
        
        // 3. 构建子骨骼缓存
        self.build_children_cache();
        
        // 4. 创建 IK 求解器
        self.ik_solvers.clear();
        for (i, bone) in self.links.iter().enumerate() {
            if let Some(ref ik_config) = bone.ik_config {
                self.ik_solvers.push(IkSolver::new(i, ik_config.clone()));
            }
        }
        
        // 5. 初始化蒙皮矩阵缓存
        self.skinning_matrices = vec![Mat4::IDENTITY; bone_count];
        
        // 6. 计算初始蒙皮矩阵
        for i in 0..bone_count {
            self.skinning_matrices[i] = self.links[i].get_skinning_matrix();
        }
        
        self.needs_hierarchy_update = false;
    }
    
    /// 构建子骨骼缓存
    fn build_children_cache(&mut self) {
        let bone_count = self.links.len();
        self.children_cache = vec![Vec::new(); bone_count];
        
        for i in 0..bone_count {
            let parent_idx = self.links[i].parent_index;
            if parent_idx >= 0 && (parent_idx as usize) < bone_count {
                self.children_cache[parent_idx as usize].push(i);
                self.links[parent_idx as usize].is_leaf = false;
            }
        }
    }
    
    // ========================================
    // 访问器（类似 nphysics Multibody）
    // ========================================
    
    /// 骨骼数量
    #[inline]
    pub fn num_links(&self) -> usize {
        self.links.len()
    }
    
    /// 骨骼数量
    #[inline]
    pub fn bone_count(&self) -> usize {
        self.links.len()
    }
    
    /// 获取骨骼引用
    #[inline]
    pub fn link(&self, id: usize) -> Option<&BoneLink> {
        self.links.get(id)
    }
    
    /// 获取骨骼可变引用
    #[inline]
    pub fn link_mut(&mut self, id: usize) -> Option<&mut BoneLink> {
        self.links.get_mut(id)
    }
    
    /// 获取骨骼
    #[inline]
    pub fn get_bone(&self, index: usize) -> Option<&BoneLink> {
        self.links.get(index)
    }
    
    /// 获取骨骼可变引用
    #[inline]
    pub fn get_bone_mut(&mut self, index: usize) -> Option<&mut BoneLink> {
        self.links.get_mut(index)
    }
    
    /// 通过名称查找骨骼
    #[inline]
    pub fn find_bone_by_name(&self, name: &str) -> Option<usize> {
        self.name_to_index.get(name).copied()
    }
    
    /// 通过名称查找骨骼（类似 nphysics links_with_name）
    pub fn links_with_name<'a>(&'a self, name: &'a str) -> impl Iterator<Item = (usize, &'a BoneLink)> {
        self.links
            .iter()
            .enumerate()
            .filter(move |(_, l)| l.name == name)
    }
    
    /// 迭代所有骨骼
    #[inline]
    pub fn links(&self) -> impl Iterator<Item = &BoneLink> {
        self.links.iter()
    }
    
    /// 可变迭代所有骨骼
    #[inline]
    pub fn links_mut(&mut self) -> impl Iterator<Item = &mut BoneLink> {
        self.links.iter_mut()
    }
    
    /// 获取根骨骼
    #[inline]
    pub fn root(&self) -> Option<&BoneLink> {
        self.links.first()
    }
    
    /// 获取排序后的索引
    #[inline]
    pub fn sorted_indices(&self) -> &[usize] {
        &self.sorted_indices
    }
    
    // ========================================
    // 物理骨骼管理
    // ========================================
    
    /// 设置物理骨骼索引集合（接受引用，内部 clone）
    pub fn set_physics_bone_indices(&mut self, indices: &HashSet<usize>) {
        self.physics_bone_indices.clone_from(indices);
    }
    
    /// 清除物理骨骼索引集合
    pub fn clear_physics_bone_indices(&mut self) {
        self.physics_bone_indices.clear();
    }
    
    /// 检查是否为物理骨骼
    #[inline]
    pub fn is_physics_bone(&self, index: usize) -> bool {
        self.physics_bone_indices.contains(&index)
    }
    
    // ========================================
    // 变换更新（核心功能）
    // ========================================
    
    /// 开始更新（重置所有动画状态）
    pub fn begin_update(&mut self) {
        for bone in &mut self.links {
            bone.reset_animation();
        }
    }
    
    /// 结束更新（计算蒙皮矩阵）
    pub fn end_update(&mut self) {
        for i in 0..self.links.len() {
            self.skinning_matrices[i] = self.links[i].get_skinning_matrix();
        }
    }
    
    /// 重置所有骨骼变换
    pub fn reset_all_transforms(&mut self) {
        for bone in &mut self.links {
            bone.reset_animation();
        }
    }
    
    /// 更新骨骼变换
    ///
    /// 参考 nphysics Multibody::update_kinematics
    pub fn update_transforms(&mut self, after_physics: bool) {
        // 1. 更新本地变换（跳过物理骨骼）
        for &idx in &self.sorted_indices.clone() {
            if self.links[idx].deform_after_physics() != after_physics {
                continue;
            }
            if self.physics_bone_indices.contains(&idx) {
                continue;
            }
            self.links[idx].compute_local_transform();
        }
        
        // 2. 从根骨骼递归更新全局变换
        for &idx in &self.sorted_indices.clone() {
            if self.links[idx].deform_after_physics() != after_physics {
                continue;
            }
            if self.links[idx].is_root() {
                self.update_global_transform_recursive(idx);
            }
        }
        
        // 3. 处理附加变换和 IK
        for &idx in &self.sorted_indices.clone() {
            if self.links[idx].deform_after_physics() != after_physics {
                continue;
            }
            
            let needs_append = self.links[idx].is_append_rotate() || self.links[idx].is_append_translate();
            let is_ik = self.links[idx].is_ik();
            
            if needs_append {
                self.apply_append_transform(idx);
                self.update_global_transform_recursive(idx);
            }
            
            if is_ik {
                self.solve_ik(idx);
            }
        }
        
        // 4. 最终更新全局变换
        for &idx in &self.sorted_indices.clone() {
            if self.links[idx].deform_after_physics() != after_physics {
                continue;
            }
            if self.links[idx].is_root() {
                self.update_global_transform_recursive(idx);
            }
        }
    }
    
    /// 递归更新全局变换
    ///
    /// 类似 nphysics Multibody::update_kinematics
    fn update_global_transform_recursive(&mut self, index: usize) {
        // 跳过物理骨骼（它们的变换由物理系统设置）
        if self.physics_bone_indices.contains(&index) {
            // 仍需递归更新子骨骼
            let children = self.children_cache[index].clone();
            for child_idx in children {
                self.update_global_transform_recursive(child_idx);
            }
            return;
        }
        
        // 计算全局变换：local_to_world = parent.local_to_world * local_to_parent
        let parent_idx = self.links[index].parent_index;
        if parent_idx >= 0 && (parent_idx as usize) < self.links.len() {
            let parent_global = self.links[parent_idx as usize].local_to_world;
            self.links[index].parent_to_world = parent_global;
            self.links[index].local_to_world = parent_global * self.links[index].local_to_parent;
        } else {
            self.links[index].parent_to_world = Mat4::IDENTITY;
            self.links[index].local_to_world = self.links[index].local_to_parent;
        }
        
        // 递归更新子骨骼
        let children = self.children_cache[index].clone();
        for child_idx in children {
            self.update_global_transform_recursive(child_idx);
        }
    }
    
    /// 应用附加变换
    fn apply_append_transform(&mut self, index: usize) {
        let append_config = match &self.links[index].append_config {
            Some(config) => config.clone(),
            None => return,
        };
        
        let parent_idx = append_config.parent as usize;
        if parent_idx >= self.links.len() {
            return;
        }
        
        let rate = append_config.rate;
        let is_append_local = self.links[index].is_append_local();
        
        // 附加旋转
        if self.links[index].is_append_rotate() {
            let parent_has_append = self.links[parent_idx].append_config.is_some();
            
            let append_rotate = if is_append_local {
                self.links[parent_idx].animation_rotate
            } else if parent_has_append {
                self.links[parent_idx].append_rotate
            } else {
                self.links[parent_idx].animation_rotate
            };
            
            let append_rotate = if self.links[parent_idx].enable_ik() {
                self.links[parent_idx].ik_rotate * append_rotate
            } else {
                append_rotate
            };
            
            self.links[index].append_rotate = Quat::IDENTITY.slerp(append_rotate, rate);
        }
        
        // 附加平移
        if self.links[index].is_append_translate() {
            let parent_has_append = self.links[parent_idx].append_config.is_some();
            
            let append_translate = if is_append_local {
                self.links[parent_idx].animation_translate
            } else if parent_has_append {
                self.links[parent_idx].append_translate
            } else {
                self.links[parent_idx].animation_translate
            };
            
            self.links[index].append_translate = append_translate * rate;
        }
        
        self.links[index].compute_local_transform();
    }
    
    /// IK 求解
    fn solve_ik(&mut self, bone_index: usize) {
        let solver_idx = self.ik_solvers.iter().position(|s| s.bone_index == bone_index);
        if let Some(idx) = solver_idx {
            let solver = self.ik_solvers[idx].clone();
            solver.solve(&mut self.links, &self.children_cache);
            self.update_global_transform_recursive(bone_index);
        }
    }
    
    // ========================================
    // 动画控制
    // ========================================
    
    /// 设置骨骼平移
    pub fn set_bone_translation(&mut self, index: usize, translation: Vec3) {
        if let Some(bone) = self.links.get_mut(index) {
            bone.animation_translate = translation;
        }
    }
    
    /// 设置骨骼旋转
    pub fn set_bone_rotation(&mut self, index: usize, rotation: Quat) {
        if let Some(bone) = self.links.get_mut(index) {
            bone.animation_rotate = rotation;
        }
    }
    
    /// 添加骨骼旋转（头部/眼球追踪等非 VMD 数据，不做坐标系转换）
    pub fn add_bone_rotation(&mut self, index: usize, rotation: Quat) {
        if let Some(bone) = self.links.get_mut(index) {
            bone.animation_rotate = bone.animation_rotate * rotation;
        }
    }
    
    /// 按名称设置 IK 启用状态
    pub fn set_ik_enabled_by_name(&mut self, ik_name: &str, enabled: bool) {
        // 先找到匹配的 IK 解算器索引
        let solver_idx = self.ik_solvers.iter().position(|solver| {
            self.links.get(solver.bone_index)
                .map(|bone| bone.name == ik_name)
                .unwrap_or(false)
        });
        
        if let Some(idx) = solver_idx {
            self.ik_solvers[idx].enabled = enabled;
        }
    }
    
    /// 设置 IK 启用状态（按索引）
    pub fn set_ik_enabled(&mut self, solver_index: usize, enabled: bool) {
        if let Some(solver) = self.ik_solvers.get_mut(solver_index) {
            solver.enabled = enabled;
        }
    }
    
    // ========================================
    // 变换访问
    // ========================================
    
    /// 获取全局变换
    #[inline]
    pub fn get_global_transform(&self, index: usize) -> Mat4 {
        self.links.get(index).map(|b| b.local_to_world).unwrap_or(Mat4::IDENTITY)
    }
    
    /// 设置全局变换（用于物理系统）
    pub fn set_global_transform(&mut self, index: usize, transform: Mat4) {
        if index >= self.links.len() {
            return;
        }
        
        self.links[index].local_to_world = transform;
        
        let parent_idx = self.links[index].parent_index;
        let local_transform = if parent_idx >= 0 && (parent_idx as usize) < self.links.len() {
            let parent_global = self.links[parent_idx as usize].local_to_world;
            parent_global.inverse() * transform
        } else {
            transform
        };
        self.links[index].local_to_parent = local_transform;
        
        let (_, rotation, translation) = local_transform.to_scale_rotation_translation();
        let p = self.links[index].parent_rest_rotation;
        self.links[index].animation_rotate = p * rotation * self.links[index].rest_rotation.inverse() * p.inverse();
        self.links[index].animation_translate = translation - self.links[index].body_shift;
        
        self.update_children_global_transform(index);
    }
    
    /// 设置全局变换（物理专用，不递归更新子骨骼）
    pub fn set_global_transform_physics(&mut self, index: usize, transform: Mat4) {
        if index >= self.links.len() {
            return;
        }
        
        self.links[index].local_to_world = transform;
        
        let parent_idx = self.links[index].parent_index;
        let local_transform = if parent_idx >= 0 && (parent_idx as usize) < self.links.len() {
            let parent_global = self.links[parent_idx as usize].local_to_world;
            parent_global.inverse() * transform
        } else {
            transform
        };
        self.links[index].local_to_parent = local_transform;
        
        let (_, rotation, translation) = local_transform.to_scale_rotation_translation();
        let p = self.links[index].parent_rest_rotation;
        self.links[index].animation_rotate = p * rotation * self.links[index].rest_rotation.inverse() * p.inverse();
        self.links[index].animation_translate = translation - self.links[index].body_shift;
    }
    
    /// 更新单个骨骼的全局变换（VR IK 用：头部旋转后刷新子骨骼链）
    pub fn update_single_bone_global(&mut self, index: usize) {
        if index >= self.links.len() { return; }
        self.links[index].compute_local_transform();
        let parent_idx = self.links[index].parent_index;
        if parent_idx >= 0 && (parent_idx as usize) < self.links.len() {
            self.links[index].local_to_world =
                self.links[parent_idx as usize].local_to_world * self.links[index].local_to_parent;
        } else {
            self.links[index].local_to_world = self.links[index].local_to_parent;
        }
        self.update_children_global_transform(index);
    }

    /// 递归更新子骨骼全局变换
    fn update_children_global_transform(&mut self, parent_index: usize) {
        let parent_global = self.links[parent_index].local_to_world;
        let children = self.children_cache[parent_index].clone();
        
        for child_idx in children {
            self.links[child_idx].local_to_world = parent_global * self.links[child_idx].local_to_parent;
            self.update_children_global_transform(child_idx);
        }
    }
    
    /// 收集指定骨骼及其所有子孙的索引集合
    pub fn collect_descendants(&self, root_index: usize) -> HashSet<usize> {
        let mut result = HashSet::new();
        self.collect_descendants_recursive(root_index, &mut result);
        result
    }

    fn collect_descendants_recursive(&self, index: usize, result: &mut HashSet<usize>) {
        result.insert(index);
        if index < self.children_cache.len() {
            for &child in &self.children_cache[index] {
                self.collect_descendants_recursive(child, result);
            }
        }
    }

    /// 批量更新物理骨骼后，更新非物理骨骼
    pub fn update_non_physics_children(&mut self, physics_bone_indices: &HashSet<usize>) {
        for &idx in &self.sorted_indices.clone() {
            if physics_bone_indices.contains(&idx) {
                continue;
            }
            
            let parent_idx = self.links[idx].parent_index;
            if parent_idx >= 0 {
                let parent_global = self.links[parent_idx as usize].local_to_world;
                self.links[idx].local_to_world = parent_global * self.links[idx].local_to_parent;
            }
        }
    }
    
    /// 设置骨骼的物理变换
    pub fn set_bone_physics_transform(&mut self, index: usize, position: Vec3, rotation: Quat) {
        if index >= self.links.len() {
            return;
        }
        let transform = Mat4::from_rotation_translation(rotation, position);
        self.set_global_transform(index, transform);
    }
    
    // ========================================
    // 蒙皮矩阵
    // ========================================
    
    /// 获取蒙皮矩阵数组
    #[inline]
    pub fn get_skinning_matrices(&self) -> &[Mat4] {
        &self.skinning_matrices
    }
    
    /// 设置指定骨骼的蒙皮矩阵（用于过渡插值）
    #[inline]
    pub fn set_skinning_matrix(&mut self, index: usize, matrix: Mat4) {
        if index < self.skinning_matrices.len() {
            self.skinning_matrices[index] = matrix;
        }
    }
    
    /// 覆盖指定骨骼的逆绑定矩阵（VRM 加载时用 glTF IBM 替换纯平移版本）
    #[inline]
    pub fn set_inverse_init(&mut self, index: usize, matrix: Mat4) {
        if let Some(bone) = self.links.get_mut(index) {
            bone.inverse_init = matrix;
        }
    }
    
    /// 标记为 VRM 骨骼系统（VMD 动画旋转/平移自动做 Y 轴 180° 坐标系转换）
    #[inline]
    pub fn set_vrm(&mut self, is_vrm: bool) {
        self.is_vrm = is_vrm;
    }
    
    #[inline]
    pub fn is_vrm(&self) -> bool {
        self.is_vrm
    }
    
    /// VMD→VRM 旋转转换
    ///
    /// VRM 加载时已做 Z-flip，与 PMX 在同一坐标空间，无需额外转换
    #[inline]
    pub fn convert_vmd_rotation(&self, q: Quat) -> Quat {
        q
    }
    
    /// VMD→VRM 平移转换
    #[inline]
    pub fn convert_vmd_translation(&self, v: Vec3) -> Vec3 {
        v
    }
    
    /// 更新蒙皮矩阵
    pub fn update_skinning_matrices(&mut self) {
        for i in 0..self.links.len() {
            self.skinning_matrices[i] = self.links[i].get_skinning_matrix();
        }
    }
    
    /// 计算 BoneSet 的堆内存占用（字节）
    pub fn memory_usage(&self) -> u64 {
        use std::mem::size_of;
        let mut total: u64 = 0;
        total += (self.links.capacity() * size_of::<BoneLink>()) as u64;
        // name_to_index HashMap 估算
        total += (self.name_to_index.capacity() * (size_of::<String>() + size_of::<usize>())) as u64;
        total += (self.sorted_indices.capacity() * size_of::<usize>()) as u64;
        total += (self.ik_solvers.capacity() * size_of::<IkSolver>()) as u64;
        total += (self.skinning_matrices.capacity() * size_of::<Mat4>()) as u64;
        total += (self.physics_bone_indices.capacity() * size_of::<usize>()) as u64;
        // children_cache: Vec<Vec<usize>>
        for children in &self.children_cache {
            total += (children.capacity() * size_of::<usize>()) as u64;
        }
        total += (self.children_cache.capacity() * size_of::<Vec<usize>>()) as u64;
        total
    }
}

impl Default for BoneSet {
    fn default() -> Self {
        Self::new()
    }
}

// ============================================================================
