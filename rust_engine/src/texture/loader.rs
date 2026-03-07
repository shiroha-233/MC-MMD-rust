//! 纹理加载

use std::path::Path;
use image::{GenericImageView, DynamicImage};

use crate::{Result, MmdError};
use super::Texture;

/// 从文件加载纹理
pub fn load_texture<P: AsRef<Path>>(path: P) -> Result<Texture> {
    let img = image::open(path.as_ref())
        .map_err(|e| MmdError::Texture(format!("Failed to load texture: {}", e)))?;
    
    let (width, height) = img.dimensions();
    let has_alpha = has_alpha_channel(&img);
    
    // 与C++一致：垂直翻转图像，然后根据是否有alpha通道选择格式
    let data = if has_alpha {
        // RGBA格式（4字节/像素）
        let rgba = img.to_rgba8();
        let flipped = image::imageops::flip_vertical(&rgba);
        flipped.into_raw()
    } else {
        // RGB格式（3字节/像素）- 与C++的STBI_rgb一致
        let rgb = img.to_rgb8();
        let flipped = image::imageops::flip_vertical(&rgb);
        flipped.into_raw()
    };
    
    Ok(Texture::new(width, height, data, has_alpha))
}

/// 检查图片是否有透明通道
/// 与C++一致：comp == 4 时返回true
fn has_alpha_channel(img: &DynamicImage) -> bool {
    match img {
        DynamicImage::ImageRgba8(_) |
        DynamicImage::ImageRgba16(_) |
        DynamicImage::ImageRgba32F(_) |
        DynamicImage::ImageLumaA8(_) |
        DynamicImage::ImageLumaA16(_) => true,
        _ => false,
    }
}

/// 从内存加载纹理
#[allow(dead_code)]
pub fn load_texture_from_memory(data: &[u8]) -> Result<Texture> {
    let img = image::load_from_memory(data)
        .map_err(|e| MmdError::Texture(format!("Failed to load texture from memory: {}", e)))?;
    
    let (width, height) = img.dimensions();
    let has_alpha = has_alpha_channel(&img);
    
    // 与C++一致：垂直翻转图像，然后根据是否有alpha通道选择格式
    let pixel_data = if has_alpha {
        let rgba = img.to_rgba8();
        let flipped = image::imageops::flip_vertical(&rgba);
        flipped.into_raw()
    } else {
        let rgb = img.to_rgb8();
        let flipped = image::imageops::flip_vertical(&rgb);
        flipped.into_raw()
    };
    
    Ok(Texture::new(width, height, pixel_data, has_alpha))
}
