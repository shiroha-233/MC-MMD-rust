//! 贝塞尔曲线 - 复刻 mdanceio 实现
//!
//! 用于 VMD 动画的非线性插值

use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use glam::Vec2;

/// 曲线 trait
pub trait Curve {
    fn value(&self, v: f32) -> f32;
}

/// 三次贝塞尔曲线
#[derive(Debug, Clone, PartialEq)]
pub struct BezierCurve {
    /// 预计算的曲线采样点
    points: Vec<Vec2>,
    /// 控制点1
    c0: Vec2,
    /// 控制点2
    c1: Vec2,
    /// 采样间隔数
    interval: u32,
}

impl BezierCurve {
    const P0: Vec2 = Vec2::ZERO;
    const P1: Vec2 = Vec2::ONE;

    /// 创建新的贝塞尔曲线
    /// 
    /// # 参数
    /// - `c0`: 控制点1 (归一化到 0-1 范围)
    /// - `c1`: 控制点2 (归一化到 0-1 范围)
    /// - `interval`: 采样间隔数
    pub fn new(c0: Vec2, c1: Vec2, interval: u32) -> Self {
        let interval = interval.max(1);
        let mut points = Vec::with_capacity((interval + 1) as usize);
        let interval_f = interval as f32;
        
        for i in 0..=interval {
            let t = i as f32 / interval_f;
            let it = 1.0 - t;
            // 三次贝塞尔曲线公式: B(t) = (1-t)³P₀ + 3(1-t)²tP₁ + 3(1-t)t²P₂ + t³P₃
            let point = Self::P0 * it.powi(3)
                + c0 * 3.0 * it.powi(2) * t
                + c1 * 3.0 * it * t.powi(2)
                + Self::P1 * t.powi(3);
            points.push(point);
        }
        
        // 按 X 排序以便查找
        points.sort_unstable_by(|a, b| a.x.partial_cmp(&b.x).unwrap());
        
        Self {
            points,
            c0,
            c1,
            interval,
        }
    }

    /// 从 VMD 参数创建贝塞尔曲线
    /// 
    /// VMD 使用 [0, 127] 范围的控制点参数
    pub fn from_parameters(parameters: [u8; 4], interval: u32) -> Self {
        let c0 = Vec2::new(
            parameters[0] as f32 / 127.0,
            parameters[1] as f32 / 127.0,
        );
        let c1 = Vec2::new(
            parameters[2] as f32 / 127.0,
            parameters[3] as f32 / 127.0,
        );
        Self::new(c0, c1, interval)
    }

    /// 导出为 VMD 参数格式
    pub fn to_parameters(&self) -> [u8; 4] {
        [
            (self.c0.x * 127.0) as u8,
            (self.c0.y * 127.0) as u8,
            (self.c1.x * 127.0) as u8,
            (self.c1.y * 127.0) as u8,
        ]
    }

    /// 分割贝塞尔曲线
    pub fn split(&self, t: f32) -> (Self, Self) {
        let t = t.clamp(0.0, 1.0);
        let points = vec![Self::P0, self.c0, self.c1, Self::P1];
        let mut left = Vec::new();
        let mut right = Vec::new();
        Self::split_bezier_curve(&points, t, &mut left, &mut right);
        
        let left_interval = (self.interval as f32 * t) as u32;
        let right_interval = (self.interval as f32 * (1.0 - t)) as u32;
        
        // left: P0(0,0) -> split_point，控制点需归一化到 [0,1]
        let split_point = left[3];
        let left_c0 = if split_point.x.abs() > f32::EPSILON && split_point.y.abs() > f32::EPSILON {
            Vec2::new(left[1].x / split_point.x, left[1].y / split_point.y)
        } else {
            Vec2::new(0.25, 0.25)
        };
        let left_c1 = if split_point.x.abs() > f32::EPSILON && split_point.y.abs() > f32::EPSILON {
            Vec2::new(left[2].x / split_point.x, left[2].y / split_point.y)
        } else {
            Vec2::new(0.75, 0.75)
        };
        
        // right: split_point -> P1(1,1)，right 存储顺序为 [P1, C, E, F]
        // 反转后子曲线为 [F, E, C, P1]，控制点 c0=E, c1=C
        let range = Self::P1 - split_point;
        let right_c0 = if range.x.abs() > f32::EPSILON && range.y.abs() > f32::EPSILON {
            Vec2::new(
                (right[2].x - split_point.x) / range.x,
                (right[2].y - split_point.y) / range.y,
            )
        } else {
            Vec2::new(0.25, 0.25)
        };
        let right_c1 = if range.x.abs() > f32::EPSILON && range.y.abs() > f32::EPSILON {
            Vec2::new(
                (right[1].x - split_point.x) / range.x,
                (right[1].y - split_point.y) / range.y,
            )
        } else {
            Vec2::new(0.75, 0.75)
        };
        
        (
            Self::new(left_c0, left_c1, left_interval.max(1)),
            Self::new(right_c0, right_c1, right_interval.max(1)),
        )
    }

    fn split_bezier_curve(
        points: &[Vec2],
        t: f32,
        left: &mut Vec<Vec2>,
        right: &mut Vec<Vec2>,
    ) {
        if points.len() == 1 {
            left.push(points[0]);
            right.push(points[0]);
        } else {
            left.push(points[0]);
            right.push(points[points.len() - 1]);
            let mut new_points = Vec::new();
            for i in 0..points.len() - 1 {
                new_points.push(points[i] * (1.0 - t) + points[i + 1] * t);
            }
            Self::split_bezier_curve(&new_points, t, left, right);
        }
    }
}

impl Curve for BezierCurve {
    /// 根据输入值计算曲线输出值
    /// 
    /// 使用预计算的采样点进行线性插值查找
    fn value(&self, v: f32) -> f32 {
        let mut n = (self.points[0], self.points[1]);
        for point in &self.points[2..] {
            if n.1.x > v {
                break;
            }
            n = (n.1, *point);
        }
        if n.0.x == n.1.x {
            n.0.y
        } else {
            n.0.y + (v - n.0.x) * (n.1.y - n.0.y) / (n.1.x - n.0.x)
        }
    }
}

/// 贝塞尔曲线工厂 trait
pub trait BezierCurveFactory {
    fn get_or_new(&self, c0: [u8; 2], c1: [u8; 2], interval: u32) -> Arc<BezierCurve>;
}

/// 曲线缓存键
#[derive(Debug, Clone, Copy, Hash, PartialEq, Eq)]
struct CurveCacheKey {
    c0: [u8; 2],
    c1: [u8; 2],
}

/// 贝塞尔曲线缓存
/// 
/// 避免重复创建相同参数的曲线
#[derive(Debug)]
pub struct BezierCurveCache(RwLock<HashMap<CurveCacheKey, Arc<BezierCurve>>>);

impl BezierCurveCache {
    pub fn new() -> Self {
        Self(RwLock::new(HashMap::new()))
    }
}

impl Default for BezierCurveCache {
    fn default() -> Self {
        Self::new()
    }
}

impl BezierCurveFactory for BezierCurveCache {
    fn get_or_new(&self, c0: [u8; 2], c1: [u8; 2], interval: u32) -> Arc<BezierCurve> {
        let key = CurveCacheKey { c0, c1 };
        let c0_f = Vec2::new(c0[0] as f32 / 127.0, c0[1] as f32 / 127.0);
        let c1_f = Vec2::new(c1[0] as f32 / 127.0, c1[1] as f32 / 127.0);
        
        let build_new_curve = || Arc::new(BezierCurve::new(c0_f, c1_f, interval));
        
        // 尝试读取缓存
        match self.0.read() {
            Ok(map) => {
                if let Some(curve) = map.get(&key) {
                    if curve.interval >= interval {
                        return curve.clone();
                    }
                    // 精度不足，需要更新缓存
                }
            }
            Err(_) => return build_new_curve(),
        };
        
        // 写入缓存（新建或更新精度不足的条目）
        match self.0.write() {
            Ok(mut map) => {
                // Double-checked locking: 再次检查是否已被其他线程更新
                if let Some(existing) = map.get(&key) {
                    if existing.interval >= interval {
                        return existing.clone();
                    }
                }
                let curve = build_new_curve();
                map.insert(key, curve.clone());
                curve
            }
            Err(_) => build_new_curve(),
        }
    }
}

impl Clone for BezierCurveCache {
    fn clone(&self) -> Self {
        // 克隆时创建新的空缓存
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_linear_curve() {
        // 线性曲线 (对角线)
        let curve = BezierCurve::new(
            Vec2::new(0.25, 0.25),
            Vec2::new(0.75, 0.75),
            100,
        );
        
        // 线性曲线应该近似 y = x
        assert!((curve.value(0.0) - 0.0).abs() < 0.01);
        assert!((curve.value(0.5) - 0.5).abs() < 0.05);
        assert!((curve.value(1.0) - 1.0).abs() < 0.01);
    }

    #[test]
    fn test_ease_in_curve() {
        // Ease-in 曲线
        let curve = BezierCurve::new(
            Vec2::new(0.42, 0.0),
            Vec2::new(1.0, 1.0),
            100,
        );
        
        // Ease-in 在开始时较慢
        let v_quarter = curve.value(0.25);
        assert!(v_quarter < 0.25);
    }

    #[test]
    fn test_cache() {
        let cache = BezierCurveCache::new();
        
        let curve1 = cache.get_or_new([32, 32], [96, 96], 100);
        let curve2 = cache.get_or_new([32, 32], [96, 96], 100);
        
        // 应该返回相同的 Arc
        assert!(Arc::ptr_eq(&curve1, &curve2));
    }
}
