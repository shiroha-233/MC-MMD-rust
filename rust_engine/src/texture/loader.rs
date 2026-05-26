//! 纹理加载

use image::{DynamicImage, GenericImageView};
use std::path::Path;

use super::Texture;
use crate::{MmdError, Result};

/// 从文件加载纹理
pub fn load_texture<P: AsRef<Path>>(path: P) -> Result<Texture> {
    let img = image::open(path.as_ref())
        .map_err(|e| MmdError::Texture(format!("Failed to load texture: {}", e)))?;

    Ok(texture_from_image(img))
}

fn texture_from_image(img: DynamicImage) -> Texture {
    let (width, height) = img.dimensions();
    let has_alpha = has_alpha_channel(&img);

    let w = width as usize;
    let h = height as usize;

    let data = if has_alpha {
        let raw = img.to_rgba8().into_raw();
        let row_bytes = w * 4;
        // 单次分配 + 垂直翻转（避免 to_rgba8 + flip_vertical 双分配）
        let mut flipped = vec![0u8; raw.len()];
        for src_row in 0..h {
            let dst_row = h - 1 - src_row;
            let src = src_row * row_bytes;
            let dst = dst_row * row_bytes;
            flipped[dst..dst + row_bytes].copy_from_slice(&raw[src..src + row_bytes]);
        }
        flipped
    } else {
        let raw = img.to_rgb8().into_raw();
        let row_bytes = w * 3;
        let mut flipped = vec![0u8; raw.len()];
        for src_row in 0..h {
            let dst_row = h - 1 - src_row;
            let src = src_row * row_bytes;
            let dst = dst_row * row_bytes;
            flipped[dst..dst + row_bytes].copy_from_slice(&raw[src..src + row_bytes]);
        }
        flipped
    };

    Texture::new(width, height, data, has_alpha)
}

/// 检查图片是否有透明通道
/// 与C++一致：comp == 4 时返回true
fn has_alpha_channel(img: &DynamicImage) -> bool {
    match img {
        DynamicImage::ImageRgba8(_)
        | DynamicImage::ImageRgba16(_)
        | DynamicImage::ImageRgba32F(_)
        | DynamicImage::ImageLumaA8(_)
        | DynamicImage::ImageLumaA16(_) => true,
        _ => false,
    }
}

/// 从内存加载纹理
#[allow(dead_code)]
pub fn load_texture_from_memory(data: &[u8]) -> Result<Texture> {
    let img = image::load_from_memory(data)
        .map_err(|e| MmdError::Texture(format!("Failed to load texture from memory: {}", e)))?;

    Ok(texture_from_image(img))
}

#[cfg(test)]
mod tests {
    use super::{load_texture, load_texture_from_memory};
    use std::fs;
    use std::path::PathBuf;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn load_texture_should_decode_dxt1_dds_file() {
        let bytes = dds_texture("DXT1", 8, &[0x00, 0xF8, 0x00, 0x00, 0, 0, 0, 0]);
        let path = write_temp_dds(&bytes);

        let texture = load_texture(&path).expect("DXT1 DDS should load");
        let _ = fs::remove_file(&path);

        assert_eq!(texture.width, 4);
        assert_eq!(texture.height, 4);
        assert!(!texture.has_alpha);
        assert_eq!(texture.byte_count(), 4 * 4 * 3);
        assert!(texture.data.chunks_exact(3).all(|px| px == [255, 0, 0]));
    }

    #[test]
    fn load_texture_from_memory_should_decode_dxt5_dds() {
        let bytes = dds_texture(
            "DXT5",
            16,
            &[255, 0, 0, 0, 0, 0, 0, 0, 0x00, 0xF8, 0x00, 0x00, 0, 0, 0, 0],
        );

        let texture = load_texture_from_memory(&bytes).expect("DXT5 DDS should load");

        assert_eq!(texture.width, 4);
        assert_eq!(texture.height, 4);
        assert!(texture.has_alpha);
        assert_eq!(texture.byte_count(), 4 * 4 * 4);
        assert!(texture
            .data
            .chunks_exact(4)
            .all(|px| px == [255, 0, 0, 255]));
    }

    fn dds_texture(fourcc: &str, linear_size: u32, block: &[u8]) -> Vec<u8> {
        let mut bytes = Vec::with_capacity(128 + block.len());
        bytes.extend_from_slice(b"DDS ");
        push_u32(&mut bytes, 124);
        push_u32(&mut bytes, 0x1007);
        push_u32(&mut bytes, 4);
        push_u32(&mut bytes, 4);
        push_u32(&mut bytes, linear_size);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        for _ in 0..11 {
            push_u32(&mut bytes, 0);
        }
        push_u32(&mut bytes, 32);
        push_u32(&mut bytes, 0x4);
        bytes.extend_from_slice(fourcc.as_bytes());
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0x1000);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        push_u32(&mut bytes, 0);
        bytes.extend_from_slice(block);
        bytes
    }

    fn push_u32(bytes: &mut Vec<u8>, value: u32) {
        bytes.extend_from_slice(&value.to_le_bytes());
    }

    fn write_temp_dds(bytes: &[u8]) -> PathBuf {
        let mut path = std::env::temp_dir();
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        path.push(format!(
            "mmd_engine_dds_test_{}_{}.dds",
            std::process::id(),
            nanos
        ));
        fs::write(&path, bytes).unwrap();
        path
    }
}
