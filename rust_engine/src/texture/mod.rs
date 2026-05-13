//! 纹理加载和管理

mod loader;

pub use loader::load_texture;

/// 纹理数据
#[derive(Clone)]
pub struct Texture {
    pub width: u32,
    pub height: u32,
    pub data: Vec<u8>,
    pub has_alpha: bool,
}

impl Texture {
    pub fn new(width: u32, height: u32, data: Vec<u8>, has_alpha: bool) -> Self {
        Self {
            width,
            height,
            data,
            has_alpha,
        }
    }

    /// 获取纹理字节数
    pub fn byte_count(&self) -> usize {
        self.data.len()
    }

    /// 检查是否包含透明通道
    pub fn has_transparency(&self) -> bool {
        self.has_alpha
    }
}
