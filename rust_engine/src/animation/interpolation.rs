//! 插值系统 - 复刻 mdanceio 实现
//!
//! 提供关键帧插值点和各种插值计算

use glam::Vec3;
use crate::animation::bezier_curve::{BezierCurveFactory, Curve};

/// 计算插值系数
/// 
/// # 参数
/// - `prev_frame_index`: 前一帧索引
/// - `next_frame_index`: 后一帧索引
/// - `frame_index`: 当前帧索引
/// 
/// # 返回
/// 归一化的插值系数 [0, 1]
#[inline]
pub fn coefficient(prev_frame_index: u32, next_frame_index: u32, frame_index: u32) -> f32 {
    if prev_frame_index >= next_frame_index {
        1.0
    } else {
        let interval = next_frame_index - prev_frame_index;
        let coef = frame_index.saturating_sub(prev_frame_index) as f32 / interval as f32;
        coef.clamp(0.0, 1.0)
    }
}

/// 关键帧插值点
/// 
/// 存储贝塞尔曲线的两个控制点，用于非线性插值
#[derive(Debug, Clone, Copy)]
pub struct KeyframeInterpolationPoint {
    /// 控制点1 (x, y)
    pub control_point1: [u8; 2],
    /// 控制点2 (x, y)
    pub control_point2: [u8; 2],
    /// 是否为线性插值
    pub is_linear: bool,
}

impl Default for KeyframeInterpolationPoint {
    fn default() -> Self {
        Self {
            control_point1: [20, 20],
            control_point2: [107, 107],
            is_linear: true,
        }
    }
}

impl From<[u8; 4]> for KeyframeInterpolationPoint {
    fn from(v: [u8; 4]) -> Self {
        Self::new(&v)
    }
}

impl KeyframeInterpolationPoint {
    /// 创建零（默认线性）插值点
    pub fn zero() -> Self {
        Self::default()
    }

    /// 检查是否为线性插值
    /// 
    /// 线性插值满足：c0.x == c0.y && c1.x == c1.y && c0.x + c1.x == c0.y + c1.y
    #[inline]
    pub fn is_linear_interpolation(interpolation: &[u8; 4]) -> bool {
        interpolation[0] == interpolation[1]
            && interpolation[2] == interpolation[3]
    }

    /// 从 VMD 插值参数创建
    /// 
    /// # 参数
    /// - `interpolation`: [c0.x, c0.y, c1.x, c1.y] 格式的插值参数
    pub fn new(interpolation: &[u8; 4]) -> Self {
        if Self::is_linear_interpolation(interpolation) {
            Self::default()
        } else {
            Self {
                control_point1: [interpolation[0], interpolation[1]],
                control_point2: [interpolation[2], interpolation[3]],
                is_linear: false,
            }
        }
    }

    /// 导出为 VMD 插值参数格式
    pub fn bezier_control_point(&self) -> [u8; 4] {
        [
            self.control_point1[0],
            self.control_point1[1],
            self.control_point2[0],
            self.control_point2[1],
        ]
    }

    /// 线性插值两个插值点
    pub fn lerp(&self, other: Self, amount: f32) -> Self {
        let lerp_u8 = |a: u8, b: u8, t: f32| -> u8 {
            let result = a as f32 * (1.0 - t) + b as f32 * t;
            result.clamp(0.0, 255.0) as u8
        };
        
        Self {
            control_point1: [
                lerp_u8(self.control_point1[0], other.control_point1[0], amount),
                lerp_u8(self.control_point1[1], other.control_point1[1], amount),
            ],
            control_point2: [
                lerp_u8(self.control_point2[0], other.control_point2[0], amount),
                lerp_u8(self.control_point2[1], other.control_point2[1], amount),
            ],
            is_linear: self.is_linear,
        }
    }

    /// 计算曲线插值值
    /// 
    /// # 参数
    /// - `interval`: 帧间隔
    /// - `amount`: 线性插值系数 [0, 1]
    /// - `bezier_factory`: 贝塞尔曲线工厂（用于缓存）
    /// 
    /// # 返回
    /// 经过贝塞尔曲线调整后的插值系数
    pub fn curve_value(
        &self,
        interval: u32,
        amount: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> f32 {
        if self.is_linear {
            amount
        } else {
            let curve = bezier_factory.get_or_new(
                self.control_point1,
                self.control_point2,
                interval,
            );
            curve.value(amount)
        }
    }
}

/// 骨骼关键帧插值
/// 
/// 包含平移的 X/Y/Z 分量和旋转的独立插值参数
#[derive(Debug, Clone, Copy)]
pub struct BoneKeyframeInterpolation {
    /// 平移插值 (X, Y, Z)
    pub translation: [KeyframeInterpolationPoint; 3],
    /// 旋转插值
    pub orientation: KeyframeInterpolationPoint,
}

impl Default for BoneKeyframeInterpolation {
    fn default() -> Self {
        Self {
            translation: [
                KeyframeInterpolationPoint::default(),
                KeyframeInterpolationPoint::default(),
                KeyframeInterpolationPoint::default(),
            ],
            orientation: KeyframeInterpolationPoint::default(),
        }
    }
}

impl BoneKeyframeInterpolation {
    /// 创建零（默认线性）插值
    pub fn zero() -> Self {
        Self::default()
    }

    /// 从 VMD 骨骼插值数据构建
    /// 
    /// # 参数
    /// - `translation_x`: X 平移插值参数
    /// - `translation_y`: Y 平移插值参数
    /// - `translation_z`: Z 平移插值参数
    /// - `orientation`: 旋转插值参数
    pub fn build(
        translation_x: &[u8; 4],
        translation_y: &[u8; 4],
        translation_z: &[u8; 4],
        orientation: &[u8; 4],
    ) -> Self {
        Self {
            translation: [
                KeyframeInterpolationPoint::new(translation_x),
                KeyframeInterpolationPoint::new(translation_y),
                KeyframeInterpolationPoint::new(translation_z),
            ],
            orientation: KeyframeInterpolationPoint::new(orientation),
        }
    }

    /// 线性插值两个骨骼插值
    pub fn lerp(&self, other: Self, amount: f32) -> Self {
        Self {
            translation: [
                self.translation[0].lerp(other.translation[0], amount),
                self.translation[1].lerp(other.translation[1], amount),
                self.translation[2].lerp(other.translation[2], amount),
            ],
            orientation: self.orientation.lerp(other.orientation, amount),
        }
    }
}

/// 按分量线性插值 Vec3
#[inline]
pub fn lerp_element_wise(a: Vec3, b: Vec3, amounts: Vec3) -> Vec3 {
    Vec3::new(
        a.x + (b.x - a.x) * amounts.x,
        a.y + (b.y - a.y) * amounts.y,
        a.z + (b.z - a.z) * amounts.z,
    )
}

/// 线性插值 f32
#[inline]
pub fn lerp_f32(a: f32, b: f32, t: f32) -> f32 {
    a + (b - a) * t
}
