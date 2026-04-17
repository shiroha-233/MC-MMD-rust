//! 动画句柄

use crate::animation::VmdAnimation;
use std::sync::Arc;

/// 动画句柄包装
pub struct AnimationHandle {
    pub id: i64,
    pub animation: Arc<VmdAnimation>,
}

impl AnimationHandle {
    pub fn new(id: i64, animation: VmdAnimation) -> Self {
        Self {
            id,
            animation: Arc::new(animation),
        }
    }
}
