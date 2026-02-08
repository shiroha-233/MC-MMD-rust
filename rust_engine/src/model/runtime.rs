//! MMD 运行时模型

use crate::animation::{VmdAnimation, AnimationLayerManager};
use crate::morph::MorphManager;
use crate::physics::MMDPhysics;
use crate::skeleton::BoneManager;
use glam::{Mat4, Quat, Vec2, Vec3, Vec4};
use rayon::prelude::*;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use super::{MmdMaterial, RuntimeVertex, SubMesh, VertexWeight};

/// 简单的伪随机数生成（0.0 - 1.0）
fn rand_float() -> f32 {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .subsec_nanos();
    ((nanos % 10000) as f32) / 10000.0
}

/// MMD 运行时模型
pub struct MmdModel {
    // 静态数据
    pub name: String,
    pub vertices: Vec<RuntimeVertex>,
    pub indices: Vec<u32>,
    pub weights: Vec<VertexWeight>,
    pub materials: Vec<MmdMaterial>,
    pub submeshes: Vec<SubMesh>,
    pub texture_paths: Vec<String>,
    pub rigid_bodies: Vec<mmd::pmx::rigid_body::RigidBody>,
    pub joints: Vec<mmd::pmx::joint::Joint>,

    // 运行时数据
    pub update_positions: Vec<Vec3>,
    pub update_normals: Vec<Vec3>,
    pub update_uvs: Vec<Vec2>,
    /// JNI/渲染用平铺缓冲区（避免 Vec3/Vec2 内存对齐导致错位）
    pub update_positions_raw: Vec<f32>,
    pub update_normals_raw: Vec<f32>,
    pub update_uvs_raw: Vec<f32>,

    // 子系统
    pub bone_manager: BoneManager,
    pub morph_manager: MorphManager,

    // 动画层系统（支持多轨并行动画）
    animation_layer_manager: AnimationLayerManager,
    
    // 头部旋转
    head_angle_x: f32,
    head_angle_y: f32,
    head_angle_z: f32,
    
    // 眼球追踪（看向摄像头）
    eye_angle_x: f32,
    eye_angle_y: f32,
    eye_tracking_enabled: bool,
    eye_bone_left: Option<usize>,   // 缓存左眼骨骼索引
    eye_bone_right: Option<usize>,  // 缓存右眼骨骼索引
    eye_max_angle: f32,             // 最大眼球角度（弧度）
    
    // 自动眨眼
    auto_blink_enabled: bool,
    blink_timer: f32,           // 当前计时器
    blink_interval: f32,        // 眨眼间隔（秒）
    blink_duration: f32,        // 眨眼持续时间（秒）
    blink_phase: f32,           // 眨眼进度 0-1（0=督开, 0.5=闭眼, 1=督开）
    is_blinking: bool,          // 是否正在眨眼
    blink_morph_index: Option<usize>, // 缓存眨眼 Morph 索引
    
    debug_logged: bool,
    
    // 模型全局变换
    model_transform: Mat4,
    
    // 物理系统
    physics: Option<MMDPhysics>,
    physics_enabled: bool,
    
    // 材质可见性控制（用于脱外套等功能）
    material_visible: Vec<bool>,
    
    // GPU 蒙皮数据缓冲区
    /// 骨骼索引（ivec4 格式，每顶点 4 个索引）
    bone_indices: Vec<i32>,
    /// 骨骼权重（vec4 格式，每顶点 4 个权重）
    bone_weights: Vec<f32>,
    /// 原始顶点位置（未蒙皮，用于 GPU 蒙皮）
    original_positions: Vec<f32>,
    /// 原始法线（未蒙皮，用于 GPU 蒙皮）
    original_normals: Vec<f32>,
    
    // GPU Morph 数据缓冲区
    /// 顶点 Morph 偏移数据（密集格式：morph_count * vertex_count * 3）
    gpu_morph_offsets: Vec<f32>,
    /// Morph 权重数组（用于 GPU）
    gpu_morph_weights: Vec<f32>,
    /// 顶点 Morph 索引映射（GPU Morph 索引 -> MorphManager 索引）
    vertex_morph_indices: Vec<usize>,
    /// 顶点 Morph 数量
    vertex_morph_count: usize,
    /// GPU Morph 数据是否已初始化
    gpu_morph_initialized: bool,
    
    // VPD 骨骼姿势覆盖（骨骼索引 -> (位移, 旋转)）
    vpd_bone_overrides: HashMap<usize, (Vec3, Quat)>,
    
    // ======== 矩阵插值过渡 ========
    /// 缓存的蒙皮矩阵（过渡开始时的状态）
    transition_matrices: Vec<Mat4>,
    /// 过渡进度（0.0 - 1.0）
    transition_progress: f32,
    /// 过渡时长（秒）
    transition_duration: f32,
    /// 是否正在过渡
    is_transitioning: bool,
}

impl MmdModel {
    /// 创建空模型
    pub fn new() -> Self {
        Self {
            name: String::new(),
            vertices: Vec::new(),
            indices: Vec::new(),
            weights: Vec::new(),
            materials: Vec::new(),
            submeshes: Vec::new(),
            texture_paths: Vec::new(),
            rigid_bodies: Vec::new(),
            joints: Vec::new(),
            update_positions: Vec::new(),
            update_normals: Vec::new(),
            update_uvs: Vec::new(),
            update_positions_raw: Vec::new(),
            update_normals_raw: Vec::new(),
            update_uvs_raw: Vec::new(),
            bone_manager: BoneManager::new(),
            morph_manager: MorphManager::new(),
            animation_layer_manager: AnimationLayerManager::new(4), // 默认4层
            head_angle_x: 0.0,
            head_angle_y: 0.0,
            head_angle_z: 0.0,
            eye_angle_x: 0.0,
            eye_angle_y: 0.0,
            eye_tracking_enabled: false,
            eye_bone_left: None,
            eye_bone_right: None,
            eye_max_angle: 0.35,  // 默认约 20 度
            auto_blink_enabled: false,
            blink_timer: 0.0,
            blink_interval: 4.0,      // 默认 4 秒眨一次
            blink_duration: 0.15,     // 眨眼持续 0.15 秒
            blink_phase: 0.0,
            is_blinking: false,
            blink_morph_index: None,
            debug_logged: false,
            model_transform: Mat4::IDENTITY,
            physics: None,
            physics_enabled: false,
            material_visible: Vec::new(),
            bone_indices: Vec::new(),
            bone_weights: Vec::new(),
            original_positions: Vec::new(),
            original_normals: Vec::new(),
            gpu_morph_offsets: Vec::new(),
            gpu_morph_weights: Vec::new(),
            vertex_morph_indices: Vec::new(),
            vertex_morph_count: 0,
            gpu_morph_initialized: false,
            vpd_bone_overrides: HashMap::new(),
            transition_matrices: Vec::new(),
            transition_progress: 0.0,
            transition_duration: 0.0,
            is_transitioning: false,
        }
    }

    /// 获取顶点数量
    pub fn vertex_count(&self) -> usize {
        self.vertices.len()
    }

    /// 获取索引数量
    pub fn index_count(&self) -> usize {
        self.indices.len()
    }

    /// 获取材质数量
    pub fn material_count(&self) -> usize {
        self.materials.len()
    }

    /// 获取子网格数量
    pub fn submesh_count(&self) -> usize {
        self.submeshes.len()
    }
    
    // ========== 材质可见性控制 ==========
    
    /// 初始化材质可见性（默认全部可见）
    pub fn init_material_visibility(&mut self) {
        self.material_visible = vec![true; self.materials.len()];
    }
    
    /// 获取材质是否可见
    pub fn is_material_visible(&self, index: usize) -> bool {
        self.material_visible.get(index).copied().unwrap_or(true)
    }
    
    /// 设置材质可见性
    pub fn set_material_visible(&mut self, index: usize, visible: bool) {
        if index < self.material_visible.len() {
            self.material_visible[index] = visible;
        }
    }
    
    /// 根据材质名称设置可见性（支持部分匹配）
    pub fn set_material_visible_by_name(&mut self, name: &str, visible: bool) -> usize {
        let mut count = 0;
        for (i, mat) in self.materials.iter().enumerate() {
            if mat.name.contains(name) {
                if i < self.material_visible.len() {
                    self.material_visible[i] = visible;
                    count += 1;
                }
            }
        }
        count
    }
    
    /// 设置所有材质可见性
    pub fn set_all_materials_visible(&mut self, visible: bool) {
        for v in &mut self.material_visible {
            *v = visible;
        }
    }
    
    /// 获取材质名称
    pub fn get_material_name(&self, index: usize) -> Option<&str> {
        self.materials.get(index).map(|m| m.name.as_str())
    }
    
    /// 获取所有材质名称列表
    pub fn get_material_names(&self) -> Vec<String> {
        self.materials.iter().map(|m| m.name.clone()).collect()
    }

    /// 初始化动画状态
    pub fn initialize_animation(&mut self) {
        self.bone_manager.reset_all_transforms();
        self.morph_manager.reset_all_weights();
    }

    /// 开始动画帧
    pub fn begin_animation(&mut self) {
        self.bone_manager.begin_update();
    }

    /// 结束动画帧
    pub fn end_animation(&mut self) {
        self.bone_manager.end_update();
    }

    /// 更新 Morph 动画
    pub fn update_morph_animation(&mut self) {
        // 先将 update_positions 重置为原始顶点位置（因为 apply_morphs 是累加操作）
        for (i, vertex) in self.vertices.iter().enumerate() {
            if i < self.update_positions.len() {
                self.update_positions[i] = vertex.position;
            }
        }
        // 然后应用 Morph 变形
        self.morph_manager
            .apply_morphs(&mut self.bone_manager, &mut self.update_positions);
    }

    /// 更新骨骼动画（物理前/后）
    pub fn update_node_animation(&mut self, after_physics: bool) {
        self.bone_manager.update_transforms(after_physics);
    }

    /// 更新顶点（蒙皮计算）- 使用 rayon 并行加速
    pub fn update(&mut self) {
        let bone_matrices = self.bone_manager.get_skinning_matrices();
        let vertex_count = self.vertices.len();
        let raw_len = vertex_count * 3;

        if self.update_positions_raw.len() != raw_len {
            self.update_positions_raw.resize(raw_len, 0.0);
        }
        if self.update_normals_raw.len() != raw_len {
            self.update_normals_raw.resize(raw_len, 0.0);
        }
        if self.update_uvs_raw.len() != self.update_uvs.len() * 2 {
            self.update_uvs_raw.resize(self.update_uvs.len() * 2, 0.0);
        }
        
        // UV 拷贝（并行）
        self.update_uvs_raw
            .par_chunks_mut(2)
            .zip(self.update_uvs.par_iter())
            .for_each(|(chunk, uv)| {
                chunk[0] = uv.x;
                chunk[1] = uv.y;
            });

        // 并行蒙皮计算
        let vertices = &self.vertices;
        let weights = &self.weights;
        
        // 将输出切片分块，每个顶点对应 3 个 f32
        let pos_raw = &mut self.update_positions_raw;
        let norm_raw = &mut self.update_normals_raw;
        let positions = &mut self.update_positions;
        let normals = &mut self.update_normals;
        
        // 并行计算所有顶点（使用已应用 Morph 的 update_positions）
        positions
            .par_iter_mut()
            .zip(normals.par_iter_mut())
            .zip(pos_raw.par_chunks_mut(3))
            .zip(norm_raw.par_chunks_mut(3))
            .zip(vertices.par_iter())
            .zip(weights.par_iter())
            .for_each(|(((((pos_out, norm_out), pos_chunk), norm_chunk), vertex), weight)| {
                // 使用 pos_out（即 update_positions，已应用 Morph）作为蒙皮输入
                let morph_position = *pos_out;
                let (pos, norm) = compute_vertex_skinning(
                    morph_position,  // 使用已应用 Morph 的位置
                    vertex.normal,
                    weight,
                    &bone_matrices,
                );
                
                *pos_out = pos;
                *norm_out = norm;
                
                pos_chunk[0] = pos.x;
                pos_chunk[1] = pos.y;
                pos_chunk[2] = pos.z;
                norm_chunk[0] = norm.x;
                norm_chunk[1] = norm.y;
                norm_chunk[2] = norm.z;
            });

        // 调试日志（只在首次执行）
        if !self.debug_logged {
            self.debug_logged = true;
            log::info!(
                "MMD Debug: vertex_count={}, pos_raw_len={}, uv_raw_len={} (rayon并行蒙皮)",
                vertex_count,
                self.update_positions_raw.len(),
                self.update_uvs_raw.len(),
            );
        }
    }

    /// 完整动画更新流程
    pub fn update_all_animation(&mut self, vmd: Option<&VmdAnimation>, frame: f32, _elapsed: f32) {
        self.begin_animation();

        if let Some(animation) = vmd {
            animation.evaluate(frame, &mut self.bone_manager, &mut self.morph_manager);
        }

        // 应用头部旋转
        self.apply_head_rotation();

        self.update_morph_animation();
        self.update_node_animation(false);
        self.update_node_animation(true);

        self.end_animation();
        self.update();
    }

    /// 设置指定层的动画（新版多动画层接口）
    pub fn set_layer_animation(&mut self, layer_id: usize, animation: Option<Arc<VmdAnimation>>) {
        // 如果这是第一个层且设置了动画，先评估第一帧
        // 避免骨骼瞬间回到 T-pose 导致物理系统异常
        if layer_id == 0 {
            if let Some(ref anim) = animation {
                self.initialize_animation();
                anim.evaluate(0.0, &mut self.bone_manager, &mut self.morph_manager);
                self.begin_animation();
                self.update_node_animation(false);
                self.update_node_animation(true);
                self.end_animation();
            }
        }
        
        self.animation_layer_manager.set_layer_animation(layer_id, animation);
    }

    /// 播放指定层的动画
    pub fn play_layer(&mut self, layer_id: usize) {
        self.animation_layer_manager.play_layer(layer_id);
    }

    /// 停止指定层的动画
    pub fn stop_layer(&mut self, layer_id: usize) {
        self.animation_layer_manager.stop_layer(layer_id);
    }

    /// 暂停指定层的动画
    pub fn pause_layer(&mut self, layer_id: usize) {
        self.animation_layer_manager.pause_layer(layer_id);
    }

    /// 恢复指定层的动画
    pub fn resume_layer(&mut self, layer_id: usize) {
        self.animation_layer_manager.resume_layer(layer_id);
    }

    /// 设置层权重
    pub fn set_layer_weight(&mut self, layer_id: usize, weight: f32) {
        self.animation_layer_manager.set_layer_weight(layer_id, weight);
    }

    /// 设置层播放速度
    pub fn set_layer_speed(&mut self, layer_id: usize, speed: f32) {
        self.animation_layer_manager.set_layer_speed(layer_id, speed);
    }

    /// 跳转到指定帧
    pub fn seek_layer(&mut self, layer_id: usize, frame: f32) {
        self.animation_layer_manager.seek_layer(layer_id, frame);
    }

    /// 设置层淡入淡出时间
    pub fn set_layer_fade_times(&mut self, layer_id: usize, fade_in: f32, fade_out: f32) {
        self.animation_layer_manager.set_layer_fade_times(layer_id, fade_in, fade_out);
    }
    
    /// 带过渡地切换层动画（矩阵插值过渡）
    /// 
    /// 从当前骨骼姿态平滑过渡到新动画的第一帧，避免动作切换时的突兀感。
    /// 
    /// # 参数
    /// - `layer_id`: 层 ID
    /// - `animation`: 新动画
    /// - `transition_time`: 过渡时间（秒），推荐 0.2 ~ 0.5 秒
    pub fn transition_layer_to(&mut self, layer_id: usize, animation: Option<Arc<VmdAnimation>>, transition_time: f32) {
        if transition_time > 0.0 {
            self.transition_matrices = self.bone_manager.get_skinning_matrices().to_vec();
            self.transition_duration = transition_time;
            self.transition_progress = 0.0;
            self.is_transitioning = true;
        }
        
        self.animation_layer_manager.set_layer_animation(layer_id, animation);
        self.animation_layer_manager.play_layer(layer_id);
    }
    
    /// 获取指定层的最大帧数
    pub fn get_layer_max_frame(&self, layer_id: usize) -> u32 {
        self.animation_layer_manager
            .get_layer(layer_id)
            .map(|l| l.max_frame())
            .unwrap_or(0)
    }

    /// 获取所有活跃层中的最大帧数
    pub fn get_max_frame(&self) -> u32 {
        (0..self.animation_layer_manager.layer_count())
            .map(|i| self.get_layer_max_frame(i))
            .max()
            .unwrap_or(0)
    }

    /// 更新动画（每帧调用）- 多动画层版本（CPU蒙皮模式）
    pub fn tick_animation(&mut self, elapsed: f32) {
        // 更新所有动画层
        self.animation_layer_manager.update(elapsed);

        // 执行动画更新
        self.begin_animation();

        // 评估所有层并混合结果
        self.animation_layer_manager.evaluate_normalized(
            &mut self.bone_manager,
            &mut self.morph_manager,
        );

        // 应用 VPD 骨骼姿势覆盖（在动画评估后）
        self.apply_vpd_bone_overrides();
        
        // 自动眨眼
        self.update_auto_blink(elapsed);

        self.apply_head_rotation();
        self.update_morph_animation();
        
        // 骨骼更新（物理前）
        self.update_node_animation(false);
        
        // 物理更新
        self.update_physics(elapsed);
        
        // 骨骼更新（物理后）
        self.update_node_animation(true);
        
        // 清除物理骨骼保护（允许下一帧重新计算）
        self.end_physics_update();

        self.end_animation();
        
        // 应用矩阵插值过渡
        self.apply_transition_blend(elapsed);
        
        self.update();
    }
    
    /// 应用矩阵插值过渡
    fn apply_transition_blend(&mut self, elapsed: f32) {
        if !self.is_transitioning {
            return;
        }
        
        // 检查过渡矩阵是否有效
        if self.transition_matrices.is_empty() {
            self.is_transitioning = false;
            return;
        }
        
        // 更新过渡进度
        self.transition_progress += elapsed / self.transition_duration;
        
        if self.transition_progress >= 1.0 {
            // 过渡完成，不再修改矩阵
            self.transition_progress = 1.0;
            self.is_transitioning = false;
            self.transition_matrices.clear();
            return;
        }
        
        // 平滑过渡曲线 (smoothstep)
        let t = self.transition_progress;
        let smooth_t = t * t * (3.0 - 2.0 * t);
        
        // 先复制当前蒙皮矩阵（避免借用冲突）
        let new_matrices: Vec<Mat4> = self.bone_manager.get_skinning_matrices().to_vec();
        let bone_count = self.transition_matrices.len().min(new_matrices.len());
        
        for i in 0..bone_count {
            let old_mat = self.transition_matrices[i];
            let new_mat = new_matrices[i];
            
            // 简单的矩阵线性插值（LERP），避免分解失败导致的拉扯
            // 对于蒙皮矩阵，直接插值通常比分解更稳定
            let blended_mat = Self::lerp_matrix(old_mat, new_mat, smooth_t);
            self.bone_manager.set_skinning_matrix(i, blended_mat);
        }
    }
    
    /// 矩阵线性插值
    fn lerp_matrix(a: Mat4, b: Mat4, t: f32) -> Mat4 {
        // 分量线性插值
        let cols_a = a.to_cols_array();
        let cols_b = b.to_cols_array();
        let mut result = [0.0f32; 16];
        for i in 0..16 {
            result[i] = cols_a[i] * (1.0 - t) + cols_b[i] * t;
        }
        Mat4::from_cols_array(&result)
    }

    /// 设置头部角度
    pub fn set_head_angle(&mut self, x: f32, y: f32, z: f32) {
        self.head_angle_x = x;
        self.head_angle_y = y;
        self.head_angle_z = z;
    }

    /// 应用头部旋转到骨骼
    fn apply_head_rotation(&mut self) {
        // 查找头部骨骼（常见名称）
        let head_names = ["頭", "head", "Head", "あたま"];

        for name in &head_names {
            if let Some(bone_idx) = self.bone_manager.find_bone_by_name(name) {
                let rotation = glam::Quat::from_euler(
                    glam::EulerRot::XYZ,
                    self.head_angle_x,
                    self.head_angle_y,
                    self.head_angle_z,
                );
                self.bone_manager.add_bone_rotation(bone_idx, rotation);
                break;
            }
        }
        
        // 应用眼球追踪
        self.apply_eye_rotation();
    }
    
    /// 设置眼球追踪角度（会自动限制在最大角度内）
    pub fn set_eye_angle(&mut self, x: f32, y: f32) {
        // 限制在最大角度范围内
        self.eye_angle_x = x.clamp(-self.eye_max_angle, self.eye_max_angle);
        self.eye_angle_y = y.clamp(-self.eye_max_angle, self.eye_max_angle);
    }
    
    /// 设置眼球最大转动角度（弧度）
    pub fn set_eye_max_angle(&mut self, max_angle: f32) {
        self.eye_max_angle = max_angle.clamp(0.1, 1.0); // 约 5.7° - 57°
    }
    
    /// 启用/禁用眼球追踪
    pub fn set_eye_tracking_enabled(&mut self, enabled: bool) {
        self.eye_tracking_enabled = enabled;
        if enabled && self.eye_bone_left.is_none() {
            // 首次启用时查找眼睛骨骼
            self.find_eye_bones();
        }
    }
    
    /// 获取眼球追踪是否启用
    pub fn is_eye_tracking_enabled(&self) -> bool {
        self.eye_tracking_enabled
    }
    
    /// 查找眼睛骨骼并缓存索引
    fn find_eye_bones(&mut self) {
        // 扩展的眼睛骨骼名称列表
        let left_eye_names = [
            "左目", "eye_L", "Eye_L", "LeftEye", "left_eye", "Left_Eye",
            "eyeL", "EyeL", "左眼", "L_Eye", "eye.L", "Eye.L"
        ];
        let right_eye_names = [
            "右目", "eye_R", "Eye_R", "RightEye", "right_eye", "Right_Eye",
            "eyeR", "EyeR", "右眼", "R_Eye", "eye.R", "Eye.R"
        ];
        
        // 查找左眼
        self.eye_bone_left = None;
        for name in &left_eye_names {
            if let Some(idx) = self.bone_manager.find_bone_by_name(name) {
                self.eye_bone_left = Some(idx);
                break;
            }
        }
        
        // 查找右眼
        self.eye_bone_right = None;
        for name in &right_eye_names {
            if let Some(idx) = self.bone_manager.find_bone_by_name(name) {
                self.eye_bone_right = Some(idx);
                break;
            }
        }
    }
    
    /// 应用眼球旋转到骨骼（左目、右目）
    fn apply_eye_rotation(&mut self) {
        if !self.eye_tracking_enabled {
            return;
        }
        
        // 使用缓存的骨骼索引
        let left_idx = self.eye_bone_left;
        let right_idx = self.eye_bone_right;
        
        if left_idx.is_none() && right_idx.is_none() {
            return;
        }
        
        // 眼球旋转（上下左右看）
        // 直接使用眼球追踪角度覆盖动画旋转，确保实时响应
        let rotation = glam::Quat::from_euler(
            glam::EulerRot::XYZ,
            self.eye_angle_x,
            self.eye_angle_y,
            0.0,
        );
        
        // 应用到左眼（直接设置而不是累加，确保每帧都准确）
        if let Some(idx) = left_idx {
            self.bone_manager.set_bone_rotation(idx, rotation);
        }
        
        // 应用到右眼
        if let Some(idx) = right_idx {
            self.bone_manager.set_bone_rotation(idx, rotation);
        }
    }
    
    // ========== 自动眨眼 ==========
    
    /// 启用/禁用自动眨眼
    pub fn set_auto_blink_enabled(&mut self, enabled: bool) {
        self.auto_blink_enabled = enabled;
        if enabled {
            // 初始化眨眼 Morph 索引缓存
            self.find_blink_morph();
            // 随机初始计时器，避免所有模型同时眨眼
            self.blink_timer = rand_float() * self.blink_interval;
        }
    }
    
    /// 获取自动眨眼是否启用
    pub fn is_auto_blink_enabled(&self) -> bool {
        self.auto_blink_enabled
    }
    
    /// 设置眨眼参数
    pub fn set_blink_params(&mut self, interval: f32, duration: f32) {
        self.blink_interval = interval.max(0.5); // 最小 0.5 秒间隔
        self.blink_duration = duration.clamp(0.05, 0.5); // 0.05-0.5 秒
    }
    
    /// 查找眨眼 Morph 索引
    fn find_blink_morph(&mut self) {
        // 常见眨眼 Morph 名称
        let blink_names = ["まばたき", "眨眼", "blink", "Blink", "まばたき両目", "ウィンク", "wink"];
        
        for name in &blink_names {
            if let Some(idx) = self.morph_manager.find_morph_by_name(name) {
                self.blink_morph_index = Some(idx);
                return;
            }
        }
        self.blink_morph_index = None;
    }
    
    /// 更新自动眨眼（每帧调用）
    /// 返回是否需要同步 GPU Morph 权重
    fn update_auto_blink(&mut self, delta_time: f32) -> bool {
        if !self.auto_blink_enabled {
            return false;
        }
        
        let morph_idx = match self.blink_morph_index {
            Some(idx) => idx,
            None => return false,
        };
        
        let mut needs_sync = false;
        
        if self.is_blinking {
            // 正在眨眼，更新进度
            self.blink_phase += delta_time / self.blink_duration;
            
            if self.blink_phase >= 1.0 {
                // 眨眼结束
                self.is_blinking = false;
                self.blink_phase = 0.0;
                self.morph_manager.set_morph_weight(morph_idx, 0.0);
                // 添加随机变化到下次眨眼间隔
                self.blink_timer = self.blink_interval * (0.7 + rand_float() * 0.6);
                needs_sync = true;
            } else {
                // 计算眨眼权重：0 -> 1 -> 0 (使用 sin 曲线)
                let weight = (self.blink_phase * std::f32::consts::PI).sin();
                self.morph_manager.set_morph_weight(morph_idx, weight);
                needs_sync = true;
            }
        } else {
            // 等待下次眨眼
            self.blink_timer -= delta_time;
            
            if self.blink_timer <= 0.0 {
                // 开始眨眼
                self.is_blinking = true;
                self.blink_phase = 0.0;
            }
        }
        
        needs_sync
    }
    
    /// 设置模型全局变换
    pub fn set_model_transform(&mut self, transform: Mat4) {
        self.model_transform = transform;
    }
    
    /// 设置模型位置和朝向（用于惯性计算）
    /// 位置用于计算速度，yaw 用于将世界速度转换到模型局部空间
    pub fn set_model_position_and_yaw(&mut self, x: f32, y: f32, z: f32, yaw: f32) {
        // 构建带旋转的变换矩阵
        let cos_y = yaw.cos();
        let sin_y = yaw.sin();
        // Y轴旋转矩阵 + 平移
        self.model_transform = Mat4::from_cols(
            Vec4::new(cos_y, 0.0, sin_y, 0.0),
            Vec4::new(0.0, 1.0, 0.0, 0.0),
            Vec4::new(-sin_y, 0.0, cos_y, 0.0),
            Vec4::new(x, y, z, 1.0),
        );
    }
    
    /// 获取模型全局变换
    pub fn model_transform(&self) -> Mat4 {
        self.model_transform
    }

    /// 获取右手矩阵
    pub fn get_right_hand_matrix(&self) -> Mat4 {
        let names = ["右手首", "右腕", "right_hand", "RightHand"];
        for name in &names {
            if let Some(idx) = self.bone_manager.find_bone_by_name(name) {
                return self.bone_manager.get_global_transform(idx);
            }
        }
        Mat4::IDENTITY
    }

    /// 获取左手矩阵
    pub fn get_left_hand_matrix(&self) -> Mat4 {
        let names = ["左手首", "左腕", "left_hand", "LeftHand"];
        for name in &names {
            if let Some(idx) = self.bone_manager.find_bone_by_name(name) {
                return self.bone_manager.get_global_transform(idx);
            }
        }
        Mat4::IDENTITY
    }

    /// 获取更新后的顶点位置数据指针
    pub fn get_positions_ptr(&self) -> *const f32 {
        self.update_positions_raw.as_ptr()
    }

    /// 获取更新后的法线数据指针
    pub fn get_normals_ptr(&self) -> *const f32 {
        self.update_normals_raw.as_ptr()
    }

    /// 获取 UV 数据指针
    pub fn get_uvs_ptr(&self) -> *const f32 {
        self.update_uvs_raw.as_ptr()
    }

    /// 获取索引数据指针
    pub fn get_indices_ptr(&self) -> *const u32 {
        self.indices.as_ptr()
    }
    
    // ========== GPU 蒙皮相关方法 ==========
    
    /// 初始化 GPU 蒙皮数据（模型加载后调用）
    pub fn init_gpu_skinning_data(&mut self) {
        let vertex_count = self.vertices.len();
        
        // 初始化骨骼索引和权重缓冲区（每顶点 4 个）
        self.bone_indices = vec![-1; vertex_count * 4];
        self.bone_weights = vec![0.0; vertex_count * 4];
        
        // 从权重数据填充
        for (i, weight) in self.weights.iter().enumerate() {
            let base = i * 4;
            match weight {
                VertexWeight::Bdef1 { bone } => {
                    self.bone_indices[base] = *bone;
                    self.bone_weights[base] = 1.0;
                }
                VertexWeight::Bdef2 { bones, weight } => {
                    self.bone_indices[base] = bones[0];
                    self.bone_indices[base + 1] = bones[1];
                    self.bone_weights[base] = *weight;
                    self.bone_weights[base + 1] = 1.0 - *weight;
                }
                VertexWeight::Bdef4 { bones, weights } => {
                    for j in 0..4 {
                        self.bone_indices[base + j] = bones[j];
                        self.bone_weights[base + j] = weights[j];
                    }
                }
                VertexWeight::Sdef { bones, weight, .. } => {
                    // SDEF 退化为 BDEF2
                    self.bone_indices[base] = bones[0];
                    self.bone_indices[base + 1] = bones[1];
                    self.bone_weights[base] = *weight;
                    self.bone_weights[base + 1] = 1.0 - *weight;
                }
                VertexWeight::Qdef { bones, weights } => {
                    for j in 0..4 {
                        self.bone_indices[base + j] = bones[j];
                        self.bone_weights[base + j] = weights[j];
                    }
                }
            }
        }
        
        // 初始化原始顶点数据（未蒙皮）
        self.original_positions = Vec::with_capacity(vertex_count * 3);
        self.original_normals = Vec::with_capacity(vertex_count * 3);
        
        for vertex in &self.vertices {
            self.original_positions.push(vertex.position.x);
            self.original_positions.push(vertex.position.y);
            self.original_positions.push(vertex.position.z);
            self.original_normals.push(vertex.normal.x);
            self.original_normals.push(vertex.normal.y);
            self.original_normals.push(vertex.normal.z);
        }
        
        // 调试：检查骨骼索引范围和权重
        let bone_count = self.bone_manager.bone_count();
        let mut max_bone_idx = -1i32;
        let mut invalid_idx_count = 0usize;
        let mut zero_weight_count = 0usize;
        
        for i in 0..vertex_count {
            let base = i * 4;
            let mut total_weight = 0.0f32;
            let mut valid_bones = 0;
            
            for j in 0..4 {
                let idx = self.bone_indices[base + j];
                let weight = self.bone_weights[base + j];
                
                if idx > max_bone_idx {
                    max_bone_idx = idx;
                }
                if idx >= 0 && idx < bone_count as i32 {
                    valid_bones += 1;
                    total_weight += weight;
                } else if idx >= bone_count as i32 {
                    invalid_idx_count += 1;
                }
            }
            
            if valid_bones > 0 && total_weight < 0.001 {
                zero_weight_count += 1;
            }
        }
        
        if invalid_idx_count > 0 {
            log::warn!("GPU 蒙皮: 发现 {} 个无效骨骼索引 (>= {})", invalid_idx_count, bone_count);
        }
        if zero_weight_count > 0 {
            log::warn!("GPU 蒙皮: 发现 {} 个顶点权重为0", zero_weight_count);
        }
        
        log::info!("GPU 蒙皮数据初始化完成: {} 顶点, {} 骨骼, 最大骨骼索引: {}", 
            vertex_count, bone_count, max_bone_idx);
    }
    
    /// 获取骨骼索引数据指针
    pub fn get_bone_indices_ptr(&self) -> *const i32 {
        self.bone_indices.as_ptr()
    }
    
    /// 获取骨骼索引数据引用
    pub fn get_bone_indices(&self) -> &[i32] {
        &self.bone_indices
    }
    
    /// 获取骨骼权重数据指针
    pub fn get_bone_weights_ptr(&self) -> *const f32 {
        self.bone_weights.as_ptr()
    }
    
    /// 获取骨骼权重数据引用
    pub fn get_bone_weights(&self) -> &[f32] {
        &self.bone_weights
    }
    
    /// 获取物理系统动态骨骼数量
    pub fn get_dynamic_bone_count(&self) -> usize {
        if let Some(ref physics) = self.physics {
            physics.get_dynamic_bone_indices().len()
        } else {
            0
        }
    }
    
    /// 获取原始顶点位置数据指针
    pub fn get_original_positions_ptr(&self) -> *const f32 {
        self.original_positions.as_ptr()
    }
    
    /// 获取原始法线数据指针
    pub fn get_original_normals_ptr(&self) -> *const f32 {
        self.original_normals.as_ptr()
    }
    
    // ========== GPU Morph 相关方法 ==========
    
    /// 初始化 GPU Morph 数据
    /// 将稀疏的顶点 Morph 偏移转换为密集格式，供 GPU Compute Shader 使用
    pub fn init_gpu_morph_data(&mut self) {
        if self.gpu_morph_initialized {
            return;
        }
        
        let vertex_count = self.vertices.len();
        
        // 收集所有顶点类型的 Morph 索引
        self.vertex_morph_indices = (0..self.morph_manager.morph_count())
            .filter_map(|i| {
                let morph = self.morph_manager.get_morph(i)?;
                if morph.morph_type == crate::morph::MorphType::Vertex && !morph.vertex_offsets.is_empty() {
                    Some(i)
                } else {
                    None
                }
            })
            .collect();
        
        self.vertex_morph_count = self.vertex_morph_indices.len();
        
        if self.vertex_morph_count == 0 {
            log::info!("模型没有顶点 Morph，跳过 GPU Morph 初始化");
            self.gpu_morph_initialized = true;
            return;
        }
        
        // 分配密集格式的偏移数据：morph_count * vertex_count * 3 (xyz)
        let total_floats = self.vertex_morph_count * vertex_count * 3;
        self.gpu_morph_offsets = vec![0.0f32; total_floats];
        self.gpu_morph_weights = vec![0.0f32; self.vertex_morph_count];
        
        // 填充稀疏数据到密集格式
        for (morph_idx, &global_morph_idx) in self.vertex_morph_indices.iter().enumerate() {
            if let Some(morph) = self.morph_manager.get_morph(global_morph_idx) {
                let base_offset = morph_idx * vertex_count * 3;
                for offset in &morph.vertex_offsets {
                    let vid = offset.vertex_index as usize;
                    if vid < vertex_count {
                        let idx = base_offset + vid * 3;
                        self.gpu_morph_offsets[idx] = offset.offset.x;
                        self.gpu_morph_offsets[idx + 1] = offset.offset.y;
                        self.gpu_morph_offsets[idx + 2] = offset.offset.z;
                    }
                }
            }
        }
        
        self.gpu_morph_initialized = true;
        log::info!(
            "GPU Morph 数据初始化完成: {} 个顶点 Morph, 数据大小 {:.2} MB",
            self.vertex_morph_count,
            (total_floats * 4) as f64 / 1024.0 / 1024.0
        );
    }
    
    /// 更新 GPU Morph 权重数组（从 MorphManager 同步）
    pub fn sync_gpu_morph_weights(&mut self) {
        if !self.gpu_morph_initialized || self.vertex_morph_count == 0 {
            return;
        }
        
        // 使用保存的索引映射同步权重
        for (gpu_idx, &morph_idx) in self.vertex_morph_indices.iter().enumerate() {
            if gpu_idx < self.gpu_morph_weights.len() {
                if let Some(morph) = self.morph_manager.get_morph(morph_idx) {
                    self.gpu_morph_weights[gpu_idx] = morph.weight;
                }
            }
        }
    }
    
    /// 获取顶点 Morph 数量
    pub fn get_vertex_morph_count(&self) -> usize {
        self.vertex_morph_count
    }
    
    /// 获取 GPU Morph 偏移数据指针
    pub fn get_gpu_morph_offsets_ptr(&self) -> *const f32 {
        self.gpu_morph_offsets.as_ptr()
    }
    
    /// 获取 GPU Morph 偏移数据大小（字节）
    pub fn get_gpu_morph_offsets_size(&self) -> usize {
        self.gpu_morph_offsets.len() * 4
    }
    
    /// 获取 GPU Morph 权重数据指针
    pub fn get_gpu_morph_weights_ptr(&self) -> *const f32 {
        self.gpu_morph_weights.as_ptr()
    }
    
    /// 获取 GPU Morph 是否已初始化
    pub fn is_gpu_morph_initialized(&self) -> bool {
        self.gpu_morph_initialized
    }
    
    // ========== VPD 骨骼姿势覆盖 ==========
    
    /// 设置 VPD 骨骼姿势覆盖
    pub fn set_vpd_bone_override(&mut self, bone_index: usize, translation: Vec3, rotation: Quat) {
        self.vpd_bone_overrides.insert(bone_index, (translation, rotation));
    }
    
    /// 清除所有 VPD 骨骼姿势覆盖
    pub fn clear_vpd_bone_overrides(&mut self) {
        self.vpd_bone_overrides.clear();
    }
    
    /// 应用 VPD 骨骼姿势覆盖到 BoneManager
    fn apply_vpd_bone_overrides(&mut self) {
        for (&bone_index, &(translation, rotation)) in &self.vpd_bone_overrides {
            self.bone_manager.set_bone_translation(bone_index, translation);
            self.bone_manager.set_bone_rotation(bone_index, rotation);
        }
    }
    
    /// 仅更新动画（不执行 CPU 蒙皮，用于 GPU 蒙皮模式）
    pub fn tick_animation_no_skinning(&mut self, elapsed: f32) {
        self.animation_layer_manager.update(elapsed);
        self.begin_animation();
        
        self.animation_layer_manager.evaluate_normalized(
            &mut self.bone_manager,
            &mut self.morph_manager,
        );
        
        // 应用 VPD 骨骼姿势覆盖（在动画评估后）
        self.apply_vpd_bone_overrides();
        
        // 自动眨眼（如果眨眼中则同步 GPU Morph 权重）
        let blink_needs_sync = self.update_auto_blink(elapsed);
        if blink_needs_sync {
            self.sync_gpu_morph_weights();
        }
        
        self.apply_head_rotation();
        self.update_morph_animation();
        self.update_node_animation(false);
        
        // 记录物理更新前的动态骨骼数量
        let physics_enabled = self.physics_enabled && self.physics.is_some();
        
        self.update_physics(elapsed);
        self.update_node_animation(true);
        self.end_physics_update();
        self.end_animation();
        
        // 应用矩阵插值过渡（GPU蒙皮模式也需要）
        self.apply_transition_blend(elapsed);
        
        // 调试日志（仅首次）
        if !self.debug_logged && physics_enabled {
            self.debug_logged = true;
            if let Some(ref physics) = self.physics {
                let dynamic_count = physics.get_dynamic_bone_indices().len();
                log::info!("GPU蒙皮物理调试: 物理已启用, {} 个动态骨骼", dynamic_count);
            }
        }
        // 注意：不调用 self.update()，跳过 CPU 蒙皮
    }
    
    // ========== 物理系统方法 ==========
    
    /// 初始化物理系统
    pub fn init_physics(&mut self) -> bool {
        if self.rigid_bodies.is_empty() {
            log::debug!("模型没有刚体数据，跳过物理初始化");
            return false;
        }
        
        let mut physics = MMDPhysics::new();
        
        // 添加刚体
        for pmx_rb in &self.rigid_bodies {
            let bone_transform = if pmx_rb.bone_index >= 0 {
                Some(self.bone_manager.get_global_transform(pmx_rb.bone_index as usize))
            } else {
                None
            };
            physics.add_rigid_body(pmx_rb, bone_transform);
        }
        
        // 添加关节
        for pmx_joint in &self.joints {
            physics.add_joint(pmx_joint);
        }
        
        // 统计刚体类型
        use crate::physics::RigidBodyType;
        let kinematic_count = physics.mmd_rigid_bodies.iter()
            .filter(|rb| rb.body_type == RigidBodyType::Kinematic)
            .count();
        let dynamic_count = physics.mmd_rigid_bodies.iter()
            .filter(|rb| rb.body_type == RigidBodyType::Dynamic)
            .count();
        let dynamic_bone_count = physics.mmd_rigid_bodies.iter()
            .filter(|rb| rb.body_type == RigidBodyType::DynamicWithBonePosition)
            .count();
        
        log::info!(
            "物理系统初始化完成: {} 个刚体 ({}运动学 + {}动态 + {}动态跟骨), {} 个关节",
            physics.rigid_body_count(),
            kinematic_count, dynamic_count, dynamic_bone_count,
            physics.joint_count()
        );
        
        self.physics = Some(physics);
        self.physics_enabled = true;
        true
    }
    
    /// 重置物理系统
    pub fn reset_physics(&mut self) {
        if let Some(ref mut physics) = self.physics {
            physics.reset();
        }
    }
    
    /// 启用/禁用物理
    pub fn set_physics_enabled(&mut self, enabled: bool) {
        self.physics_enabled = enabled;
    }
    
    /// 获取物理是否启用
    pub fn is_physics_enabled(&self) -> bool {
        self.physics_enabled && self.physics.is_some()
    }
    
    /// 获取物理系统是否已初始化
    pub fn has_physics(&self) -> bool {
        self.physics.is_some()
    }
    
    /// 更新物理模拟
    pub fn update_physics(&mut self, delta_time: f32) {
        if !self.physics_enabled {
            return;
        }
        
        if let Some(ref mut physics) = self.physics {
            // 同步运动学刚体（跟随骨骼），传入 delta_time 用于计算速度
            // 注意：骨骼变换保持在模型局部空间，不乘以 model_transform
            // model_transform 的变化会通过 sync_kinematic_bodies 内部计算位置差来影响速度
            let bone_transforms: Vec<Mat4> = (0..self.bone_manager.bone_count())
                .map(|i| self.bone_manager.get_global_transform(i))
                .collect();
            
            // 传入 model_transform 用于计算模型整体移动的速度
            physics.sync_kinematic_bodies_with_model_velocity(&bone_transforms, delta_time, self.model_transform);
            
            // 更新物理模拟
            physics.update(delta_time);
            
            // 获取当前骨骼变换
            let current_bone_transforms: Vec<Mat4> = (0..self.bone_manager.bone_count())
                .map(|i| self.bone_manager.get_global_transform(i))
                .collect();
            
            // 获取动态刚体关联的骨骼变换和索引
            let dynamic_bone_transforms = physics.get_dynamic_bone_transforms(&current_bone_transforms);
            let physics_bone_indices = physics.get_dynamic_bone_indices();
            
            // 设置物理骨骼索引，防止后续骨骼更新覆盖物理变换
            self.bone_manager.set_physics_bone_indices(physics_bone_indices.clone());
            
            // 只更新被物理驱动的骨骼
            for (bone_idx, transform) in dynamic_bone_transforms {
                self.bone_manager.set_global_transform_physics(bone_idx, transform);
            }
            
            // 更新非物理骨骼的全局变换（它们可能是物理骨骼的子骨骼）
            self.bone_manager.update_non_physics_children(&physics_bone_indices);
        }
    }
    
    /// 结束物理更新，清除物理骨骼保护
    /// 在 tick_animation 结束时调用
    fn end_physics_update(&mut self) {
        self.bone_manager.clear_physics_bone_indices();
    }
    
    /// 获取物理调试信息（JSON 格式）
    pub fn get_physics_debug_info(&self) -> String {
        use crate::physics::RigidBodyType;
        
        if let Some(ref physics) = self.physics {
            let mut info = String::from("{\n");
            
            // 刚体信息
            info.push_str("  \"rigid_bodies\": [\n");
            for (i, rb) in physics.mmd_rigid_bodies.iter().enumerate() {
                let type_str = match rb.body_type {
                    RigidBodyType::Kinematic => "Kinematic",
                    RigidBodyType::Dynamic => "Dynamic",
                    RigidBodyType::DynamicWithBonePosition => "DynamicWithBonePosition",
                };
                info.push_str(&format!(
                    "    {{\"index\": {}, \"name\": \"{}\", \"type\": \"{}\", \"bone\": {}, \"mass\": {:.3}, \"is_bust\": {}}}",
                    i, rb.name, type_str, rb.bone_index, rb.mass, rb.is_bust
                ));
                if i < physics.mmd_rigid_bodies.len() - 1 {
                    info.push_str(",\n");
                } else {
                    info.push_str("\n");
                }
            }
            info.push_str("  ],\n");
            
            // 关节信息
            info.push_str("  \"joints\": [\n");
            for (i, joint) in physics.mmd_joints.iter().enumerate() {
                info.push_str(&format!(
                    "    {{\"index\": {}, \"name\": \"{}\", \"rb_a\": {}, \"rb_b\": {}, ",
                    i, joint.name, joint.rigid_body_a_index, joint.rigid_body_b_index
                ));
                info.push_str(&format!(
                    "\"lin_lower\": [{:.3},{:.3},{:.3}], \"lin_upper\": [{:.3},{:.3},{:.3}], ",
                    joint.linear_lower.x, joint.linear_lower.y, joint.linear_lower.z,
                    joint.linear_upper.x, joint.linear_upper.y, joint.linear_upper.z
                ));
                info.push_str(&format!(
                    "\"ang_lower\": [{:.3},{:.3},{:.3}], \"ang_upper\": [{:.3},{:.3},{:.3}], ",
                    joint.angular_lower.x, joint.angular_lower.y, joint.angular_lower.z,
                    joint.angular_upper.x, joint.angular_upper.y, joint.angular_upper.z
                ));
                info.push_str(&format!(
                    "\"lin_spring\": [{:.3},{:.3},{:.3}], \"ang_spring\": [{:.3},{:.3},{:.3}]}}",
                    joint.linear_spring.x, joint.linear_spring.y, joint.linear_spring.z,
                    joint.angular_spring.x, joint.angular_spring.y, joint.angular_spring.z
                ));
                if i < physics.mmd_joints.len() - 1 {
                    info.push_str(",\n");
                } else {
                    info.push_str("\n");
                }
            }
            info.push_str("  ],\n");
            
            // 统计信息
            let kinematic_count = physics.mmd_rigid_bodies.iter()
                .filter(|rb| rb.body_type == RigidBodyType::Kinematic).count();
            let dynamic_count = physics.mmd_rigid_bodies.iter()
                .filter(|rb| rb.body_type == RigidBodyType::Dynamic).count();
            let dynamic_bone_count = physics.mmd_rigid_bodies.iter()
                .filter(|rb| rb.body_type == RigidBodyType::DynamicWithBonePosition).count();
            
            info.push_str(&format!(
                "  \"stats\": {{\"total_rb\": {}, \"kinematic\": {}, \"dynamic\": {}, \"dynamic_bone\": {}, \"joints\": {}}}\n",
                physics.mmd_rigid_bodies.len(), kinematic_count, dynamic_count, dynamic_bone_count, physics.mmd_joints.len()
            ));
            
            info.push_str("}");
            info
        } else {
            String::from("{\"error\": \"no physics\"}")
        }
    }
}

impl Default for MmdModel {
    fn default() -> Self {
        Self::new()
    }
}

/// 计算单个顶点的蒙皮
fn compute_vertex_skinning(
    position: Vec3,
    normal: Vec3,
    weight: &VertexWeight,
    matrices: &[Mat4],
) -> (Vec3, Vec3) {
    match weight {
        VertexWeight::Bdef1 { bone } => {
            let m = matrices
                .get(*bone as usize)
                .copied()
                .unwrap_or(Mat4::IDENTITY);
            let pos = m.transform_point3(position);
            let norm = m.transform_vector3(normal).normalize();
            (pos, norm)
        }
        VertexWeight::Bdef2 { bones, weight } => {
            let m0 = matrices
                .get(bones[0] as usize)
                .copied()
                .unwrap_or(Mat4::IDENTITY);
            let m1 = matrices
                .get(bones[1] as usize)
                .copied()
                .unwrap_or(Mat4::IDENTITY);
            let w0 = *weight;
            let w1 = 1.0 - w0;

            let pos = m0.transform_point3(position) * w0 + m1.transform_point3(position) * w1;
            let norm =
                (m0.transform_vector3(normal) * w0 + m1.transform_vector3(normal) * w1).normalize();
            (pos, norm)
        }
        VertexWeight::Bdef4 { bones, weights } => {
            let mut pos = Vec3::ZERO;
            let mut norm = Vec3::ZERO;

            for i in 0..4 {
                let m = matrices
                    .get(bones[i] as usize)
                    .copied()
                    .unwrap_or(Mat4::IDENTITY);
                let w = weights[i];
                pos += m.transform_point3(position) * w;
                norm += m.transform_vector3(normal) * w;
            }

            (pos, norm.normalize())
        }
        VertexWeight::Sdef {
            bones,
            weight,
            c: _,
            r0: _,
            r1: _,
        } => {
            // SDEF 球面变形
            let m0 = matrices
                .get(bones[0] as usize)
                .copied()
                .unwrap_or(Mat4::IDENTITY);
            let m1 = matrices
                .get(bones[1] as usize)
                .copied()
                .unwrap_or(Mat4::IDENTITY);
            let w0 = *weight;
            let w1 = 1.0 - w0;

            // 简化实现：退化为 BDEF2
            let pos = m0.transform_point3(position) * w0 + m1.transform_point3(position) * w1;
            let norm =
                (m0.transform_vector3(normal) * w0 + m1.transform_vector3(normal) * w1).normalize();
            (pos, norm)
        }
        VertexWeight::Qdef { bones, weights } => {
            // QDEF 与 BDEF4 相同处理
            let mut pos = Vec3::ZERO;
            let mut norm = Vec3::ZERO;

            for i in 0..4 {
                let m = matrices
                    .get(bones[i] as usize)
                    .copied()
                    .unwrap_or(Mat4::IDENTITY);
                let w = weights[i];
                pos += m.transform_point3(position) * w;
                norm += m.transform_vector3(normal) * w;
            }

            (pos, norm.normalize())
        }
    }
}
