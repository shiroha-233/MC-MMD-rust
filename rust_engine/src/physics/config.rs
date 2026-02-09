//! MMD 物理配置
//!
//! 所有参数扁平化，直接在代码中修改默认值即可。

use once_cell::sync::Lazy;
use std::sync::RwLock;

/// 物理配置（扁平化，不嵌套）
#[derive(Debug, Clone)]
pub struct PhysicsConfig {
    // ========== 重力 ==========
    /// 重力 Y 分量（负数向下），默认 -98.0（MMD 标准）
    pub gravity_y: f32,

    // ========== 模拟参数 ==========
    /// 物理 FPS，默认 120.0
    pub physics_fps: f32,
    /// 每帧最大子步数，默认 10
    pub max_substep_count: i32,
    /// 求解器迭代次数，默认 8
    pub solver_iterations: usize,
    /// 内部 PGS 迭代次数，默认 2
    pub pgs_iterations: usize,
    /// 最大修正速度，默认 5.0
    pub max_corrective_velocity: f32,

    // ========== 刚体阻尼（让头发更柔顺的关键！）==========
    /// 线性阻尼缩放（乘以 PMX 原值），默认 1.0
    /// 增大此值让头发移动更慢、更柔顺
    pub linear_damping_scale: f32,
    /// 角速度阻尼缩放（乘以 PMX 原值），默认 1.0
    /// 增大此值让头发旋转更慢、更柔顺
    pub angular_damping_scale: f32,

    // ========== 质量 ==========
    /// 质量缩放（乘以 PMX 原值），默认 1.0
    /// 增大质量让头发更"重"，惯性更大
    pub mass_scale: f32,

    // ========== 弹簧刚度（头发弹性的核心！）==========
    /// 线性弹簧刚度缩放（乘以 PMX 原值），默认 1.0
    /// **减小此值让头发不那么弹**
    pub linear_spring_stiffness_scale: f32,
    /// 角度弹簧刚度缩放（乘以 PMX 原值），默认 1.0
    /// **减小此值让头发不那么弹**
    pub angular_spring_stiffness_scale: f32,

    // ========== 弹簧阻尼（减少弹跳的关键！）==========
    /// 线性弹簧阻尼系数，默认 0.1
    /// 公式：damping = sqrt(stiffness * 此值)
    /// **增大此值减少头发弹跳**
    pub linear_spring_damping_factor: f32,
    /// 角度弹簧阻尼系数，默认 0.1
    /// 公式：damping = sqrt(stiffness * 此值)
    /// **增大此值减少头发弹跳**
    pub angular_spring_damping_factor: f32,

    // ========== 惯性效果 ==========
    /// 惯性效果强度，默认 1.0
    /// 控制人物移动时头发被拖拽的程度
    /// 0.0 = 无惯性，1.0 = 正常，2.0 = 双倍效果
    pub inertia_strength: f32,

    // ========== 速度限制 ==========
    /// 最大线速度 (m/s)，默认 50.0
    pub max_linear_velocity: f32,
    /// 最大角速度 (rad/s)，默认 20.0
    pub max_angular_velocity: f32,

    // ========== 胸部物理专用参数 ==========
    // 胸部刚体通过名称自动识别（おっぱ、乳、胸、bust、Bust 等），
    // 使用独立的参数组，避免与头发物理互相干扰。

    /// 胸部物理是否启用，默认 true
    pub bust_physics_enabled: bool,
    /// 胸部线性阻尼缩放（乘以 PMX 原值），默认 1.0
    pub bust_linear_damping_scale: f32,
    /// 胸部角速度阻尼缩放（乘以 PMX 原值），默认 1.0
    pub bust_angular_damping_scale: f32,
    /// 胸部质量缩放（乘以 PMX 原值），默认 1.0
    pub bust_mass_scale: f32,
    /// 胸部线性弹簧刚度缩放，默认 1.0
    pub bust_linear_spring_stiffness_scale: f32,
    /// 胸部角度弹簧刚度缩放，默认 1.0
    pub bust_angular_spring_stiffness_scale: f32,
    /// 胸部线性弹簧阻尼系数，默认 1.0
    pub bust_linear_spring_damping_factor: f32,
    /// 胸部角度弹簧阻尼系数，默认 1.0
    pub bust_angular_spring_damping_factor: f32,

    // ========== 胸部防凹陷 ==========
    /// 是否启用胸部防凹陷修正，默认 true
    /// 开启后，胸部骨骼向身体内部旋转时会被钳位回动画姿态，
    /// 但向外弹跳不受影响。
    pub bust_clamp_inward: bool,

    // ========== 调试 ==========
    /// 是否启用关节，默认 true
    pub joints_enabled: bool,
    /// 是否输出调试日志，默认 false
    pub debug_log: bool,
}

impl Default for PhysicsConfig {
    fn default() -> Self {
        Self {
            // ====== 重力 ======
            // 重力加速度的 Y 分量（负数 = 向下）
            // MMD 标准约 -98.0，但游戏中通常用更小的值
            // 值越小（绝对值越大）→ 头发下垂越快、越重
            // 值越大（绝对值越小）→ 头发飘起来、更轻盈
            gravity_y: -3.8,

            // ====== 模拟参数 ======
            // 物理模拟的帧率（每秒计算多少次物理）
            // 越高 → 模拟越精确、越稳定，但 CPU 消耗越大
            // 建议范围: 30~120，60 是平衡点
            physics_fps: 60.0,
            
            // 每帧最多分成几个子步骤
            // 当游戏帧率低于 physics_fps 时会分步计算
            // 越大 → 防止卡顿时物理爆炸，但更耗性能
            max_substep_count: 4,
            
            // 约束求解器迭代次数
            // 越大 → 关节约束越精确（头发不会穿模），但更慢
            // 建议范围: 4~16
            solver_iterations: 4,
            
            // 内部 PGS（投影高斯-赛德尔）迭代次数
            // 配合 solver_iterations 使用，一般不用改
            pgs_iterations: 2,
            
            // 穿透后的最大修正速度
            // 当刚体卡进别的刚体时，修正的最大速度
            // 越大 → 修正越快但可能弹飞；越小 → 修正更平滑
            max_corrective_velocity: 0.1,

            // ====== 刚体阻尼（空气阻力）======
            // 线性阻尼 = 移动时的空气阻力
            // 这个值会乘以 PMX 模型里设置的原始阻尼
            // 越大 → 头发移动越慢、停止越快、更"稠"
            // 越小 → 头发移动越自由、惯性越大
            linear_damping_scale: 0.3,
            
            // 角速度阻尼 = 旋转时的空气阻力
            // 越大 → 头发旋转越慢、更稳定
            // 越小 → 头发旋转越自由、更飘逸
            angular_damping_scale: 0.2,

            // ====== 质量 ======
            // 质量缩放，乘以 PMX 模型里的原始质量
            // 越大 → 头发越"重"，惯性越大，不容易被带动
            // 越小 → 头发越"轻"，跟随性好，但可能太飘
            mass_scale: 2.0,

            // ====== 弹簧刚度（头发弹性！）======
            // 线性弹簧刚度 = 头发被拉伸后回弹的力度
            // 越大 → 回弹越猛、越"弹"、橡皮筋感
            // 越小 → 回弹越慢、越"软"、更自然
            linear_spring_stiffness_scale: 0.01,
            
            // 角度弹簧刚度 = 头发被扭转后回弹的力度
            // 原理同上
            angular_spring_stiffness_scale: 0.01,

            // ====== 弹簧阻尼（减少弹跳！）======
            // 弹簧阻尼 = 弹簧振动时的能量损耗
            // 公式: 实际阻尼 = sqrt(刚度 * 此值)
            // 越大 → 弹跳次数越少，很快停下来
            // 越小 → 弹跳次数越多，像果冻一样晃
            linear_spring_damping_factor: 8.0,
            
            // 角度弹簧阻尼，原理同上
            angular_spring_damping_factor: 8.0,

            // ====== 惯性效果 ======
            // 惯性效果强度
            // 0.0 = 无惯性，1.0 = 正常，>1.0 = 更强的拖拽感
            inertia_strength: 1.0,

            // ====== 速度限制（防止爆炸）======
            // 最大线速度（米/秒）
            // 超过这个速度会被强制限制，防止物理爆炸
            // 注意：人物移动速度约 4-6 方块/秒，需要设置足够大
            max_linear_velocity: 1.0,
            
            // 最大角速度（弧度/秒）
            // 同上
            max_angular_velocity: 1.0,

            // ====== 胸部物理（独立参数组）======
            // 胸部刚体需要比头发更高的弹簧刚度来保持形状，
            // 同时需要较高的阻尼防止过度弹跳。
            bust_physics_enabled: true,
            bust_linear_damping_scale: 1.5,
            bust_angular_damping_scale: 1.5,
            bust_mass_scale: 1.0,
            bust_linear_spring_stiffness_scale: 10.0,
            bust_angular_spring_stiffness_scale: 10.0,
            bust_linear_spring_damping_factor: 3.0,
            bust_angular_spring_damping_factor: 3.0,

            // ====== 胸部防凹陷 ======
            bust_clamp_inward: true,

            // ====== 调试 ======
            // 是否启用关节约束
            // 关闭后头发会完全散开（用于调试）
            joints_enabled: true,
            
            // 是否输出物理调试日志
            debug_log: false,
        }
    }
}

/// 全局配置实例
static PHYSICS_CONFIG: Lazy<RwLock<PhysicsConfig>> = Lazy::new(|| {
    RwLock::new(PhysicsConfig::default())
});

/// 获取当前配置（只读）
pub fn get_config() -> PhysicsConfig {
    PHYSICS_CONFIG.read().unwrap().clone()
}

/// 手动设置配置（用于运行时调试）
pub fn set_config(config: PhysicsConfig) {
    *PHYSICS_CONFIG.write().unwrap() = config;
}

/// 重置为默认配置
pub fn reset_config() {
    *PHYSICS_CONFIG.write().unwrap() = PhysicsConfig::default();
}
