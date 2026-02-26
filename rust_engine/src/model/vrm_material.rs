//! glTF/MToon 材质 → MmdMaterial 转换

use glam::{Vec3, Vec4};

use super::MmdMaterial;
use crate::model::vrm_extensions::{MtoonParams, VrmExtensions};

/// 将 glTF 材质转换为 MmdMaterial，同时构建纹理路径数组
pub(crate) fn convert_materials(
    document: &gltf::Document,
    vrm_extensions: &VrmExtensions,
) -> (Vec<MmdMaterial>, Vec<String>) {
    let texture_paths: Vec<String> = document
        .images()
        .enumerate()
        .map(|(i, _)| format!(".vrm_tex_{}.png", i))
        .collect();

    let texture_to_image: Vec<usize> = document
        .textures()
        .map(|tex| tex.source().index())
        .collect();

    let materials = document
        .materials()
        .enumerate()
        .map(|(idx, mat)| {
            convert_single_material(&mat, vrm_extensions, idx, &texture_to_image)
        })
        .collect();

    (materials, texture_paths)
}

/// 转换单个 glTF 材质
fn convert_single_material(
    mat: &gltf::Material,
    vrm_extensions: &VrmExtensions,
    material_index: usize,
    texture_to_image: &[usize],
) -> MmdMaterial {
    let name = mat.name().unwrap_or("").to_string();
    let pbr = mat.pbr_metallic_roughness();
    let base_color = pbr.base_color_factor();

    let texture_index = resolve_texture_index(&pbr, texture_to_image);
    let mtoon = vrm_extensions
        .mtoon_materials
        .get(material_index)
        .and_then(|m| m.as_ref());

    let (diffuse, specular, specular_strength, ambient, edge_color, edge_scale) =
        if let Some(mtoon) = mtoon {
            convert_mtoon(base_color, mtoon)
        } else if mat.unlit() {
            convert_unlit(base_color)
        } else {
            convert_pbr(base_color)
        };

    let draw_flags = build_draw_flags(mat, mtoon);
    let alpha = compute_alpha(mat, base_color[3]);

    MmdMaterial {
        name,
        diffuse: Vec4::new(diffuse.x, diffuse.y, diffuse.z, alpha),
        specular,
        specular_strength,
        ambient,
        edge_color,
        edge_scale,
        texture_index,
        environment_index: -1,
        toon_index: -1,
        draw_flags,
    }
}

/// 解析 glTF 纹理索引 → 图片索引
fn resolve_texture_index(
    pbr: &gltf::material::PbrMetallicRoughness,
    texture_to_image: &[usize],
) -> i32 {
    pbr.base_color_texture()
        .map(|info| {
            let tex_idx = info.texture().index();
            texture_to_image
                .get(tex_idx)
                .map(|&img_idx| img_idx as i32)
                .unwrap_or(-1)
        })
        .unwrap_or(-1)
}

/// MToon 材质：shadeFactor → ambient, outlineColor → edge_color
fn convert_mtoon(
    base_color: [f32; 4],
    mtoon: &MtoonParams,
) -> (Vec4, Vec3, f32, Vec3, Vec4, f32) {
    let diffuse = Vec4::from(base_color);
    let specular = Vec3::ZERO;
    let specular_strength = 0.0;
    let ambient = Vec3::from(mtoon.shade_color_factor);
    let oc = mtoon.outline_color_factor;
    let edge_color = Vec4::new(oc[0], oc[1], oc[2], 1.0);
    let edge_scale = mtoon.outline_width_factor;

    (diffuse, specular, specular_strength, ambient, edge_color, edge_scale)
}

/// Unlit 材质：baseColorFactor → diffuse, specular = 0
fn convert_unlit(base_color: [f32; 4]) -> (Vec4, Vec3, f32, Vec3, Vec4, f32) {
    let diffuse = Vec4::from(base_color);
    let specular = Vec3::ZERO;
    let specular_strength = 0.0;
    let ambient = Vec3::new(
        base_color[0] * 0.5,
        base_color[1] * 0.5,
        base_color[2] * 0.5,
    );
    let edge_color = Vec4::new(0.0, 0.0, 0.0, 1.0);
    let edge_scale = 1.0;

    (diffuse, specular, specular_strength, ambient, edge_color, edge_scale)
}

/// PBR 材质：baseColorFactor → diffuse（忽略 metallic/roughness）
fn convert_pbr(base_color: [f32; 4]) -> (Vec4, Vec3, f32, Vec3, Vec4, f32) {
    let diffuse = Vec4::from(base_color);
    let specular = Vec3::ZERO;
    let specular_strength = 0.0;
    let ambient = Vec3::new(
        base_color[0] * 0.5,
        base_color[1] * 0.5,
        base_color[2] * 0.5,
    );
    let edge_color = Vec4::new(0.0, 0.0, 0.0, 1.0);
    let edge_scale = 1.0;

    (diffuse, specular, specular_strength, ambient, edge_color, edge_scale)
}

/// 构建 draw_flags：bit 0 = 双面渲染, bit 4 = 边缘（MToon outline）
fn build_draw_flags(mat: &gltf::Material, mtoon: Option<&MtoonParams>) -> u8 {
    let mut flags: u8 = 0;
    if mat.double_sided() {
        flags |= 0x01;
    }
    if let Some(m) = mtoon {
        if m.outline_width_factor > 0.0 {
            flags |= 0x10;
        }
    }
    flags
}

/// 根据 alphaMode 计算最终 alpha 值
fn compute_alpha(mat: &gltf::Material, base_alpha: f32) -> f32 {
    match mat.alpha_mode() {
        gltf::material::AlphaMode::Opaque => 1.0,
        gltf::material::AlphaMode::Mask => {
            let cutoff = mat.alpha_cutoff().unwrap_or(0.5);
            if base_alpha >= cutoff { 1.0 } else { 0.0 }
        }
        gltf::material::AlphaMode::Blend => base_alpha,
    }
}
