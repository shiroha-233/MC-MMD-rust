//! 模型句柄

use crate::model::MmdModel;
use std::sync::{Arc, Mutex};

/// 模型句柄包装
pub struct ModelHandle {
    pub id: i64,
    pub model: Arc<Mutex<MmdModel>>,
}

impl ModelHandle {
    pub fn new(id: i64, model: MmdModel) -> Self {
        Self {
            id,
            model: Arc::new(Mutex::new(model)),
        }
    }
}
