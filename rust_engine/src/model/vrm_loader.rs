//! VRM 模型加载入口

use glam::{Vec2, Vec3};
use std::collections::HashMap;
use std::path::Path;

use super::runtime::MmdModel;
use super::{vrm_extensions, vrm_material, vrm_mesh, vrm_morph, vrm_skeleton};
use crate::{MmdError, Result};

pub struct LoadedVrmModel {
    pub model: MmdModel,
    pub extensions: vrm_extensions::VrmExtensions,
}

fn remap_extensions_to_runtime_bones(
    mut extensions: vrm_extensions::VrmExtensions,
    skin: &gltf::Skin,
) -> vrm_extensions::VrmExtensions {
    let node_to_bone: HashMap<usize, usize> = skin
        .joints()
        .enumerate()
        .map(|(joint_index, node)| (node.index(), joint_index + vrm_skeleton::VIRTUAL_BONE_COUNT))
        .collect();

    extensions.first_person.bone = extensions
        .first_person
        .bone
        .and_then(|node_index| node_to_bone.get(&node_index).copied());

    extensions.node_constraints = extensions
        .node_constraints
        .into_iter()
        .filter_map(|constraint| {
            let target = node_to_bone.get(&constraint.target).copied();
            let source = match &constraint.kind {
                vrm_extensions::NodeConstraintKind::Roll { source, .. }
                | vrm_extensions::NodeConstraintKind::Aim { source, .. }
                | vrm_extensions::NodeConstraintKind::Rotation { source, .. } => {
                    node_to_bone.get(source).copied()
                }
            };

            match (target, source) {
                (Some(target), Some(source)) => Some(vrm_extensions::NodeConstraint {
                    target,
                    kind: match constraint.kind {
                        vrm_extensions::NodeConstraintKind::Roll {
                            roll_axis, weight, ..
                        } => vrm_extensions::NodeConstraintKind::Roll {
                            source,
                            roll_axis,
                            weight,
                        },
                        vrm_extensions::NodeConstraintKind::Aim {
                            aim_axis, weight, ..
                        } => vrm_extensions::NodeConstraintKind::Aim {
                            source,
                            aim_axis,
                            weight,
                        },
                        vrm_extensions::NodeConstraintKind::Rotation { weight, .. } => {
                            vrm_extensions::NodeConstraintKind::Rotation { source, weight }
                        }
                    },
                }),
                _ => {
                    log::warn!(
                        "VRM constraint dropped because source/target node is not part of the skin"
                    );
                    None
                }
            }
        })
        .collect();

    let mut old_to_new_collider = HashMap::new();
    let mut remapped_colliders = Vec::new();
    for (old_index, collider) in extensions.spring_bones.colliders.into_iter().enumerate() {
        if let Some(node) = node_to_bone.get(&collider.node).copied() {
            old_to_new_collider.insert(old_index, remapped_colliders.len());
            remapped_colliders.push(vrm_extensions::SpringBoneCollider { node, ..collider });
        } else {
            log::warn!(
                "VRM spring-bone collider dropped because collider node is not part of the skin"
            );
        }
    }

    let remapped_groups = extensions
        .spring_bones
        .collider_groups
        .into_iter()
        .map(|group| vrm_extensions::SpringBoneColliderGroup {
            colliders: group
                .colliders
                .into_iter()
                .filter_map(|index| old_to_new_collider.get(&index).copied())
                .collect(),
        })
        .collect::<Vec<_>>();

    let remapped_springs = extensions
        .spring_bones
        .springs
        .into_iter()
        .filter_map(|spring| {
            let joints = spring
                .joints
                .into_iter()
                .filter_map(|joint| {
                    node_to_bone
                        .get(&joint.node)
                        .copied()
                        .map(|node| vrm_extensions::SpringBoneJoint { node, ..joint })
                })
                .collect::<Vec<_>>();

            if joints.is_empty() {
                log::warn!(
                    "VRM spring-bone chain dropped because no joints could be mapped onto the skin"
                );
                return None;
            }

            Some(vrm_extensions::SpringBoneSpring {
                joints,
                collider_groups: spring
                    .collider_groups
                    .into_iter()
                    .filter(|index| *index < remapped_groups.len())
                    .collect(),
                center: spring
                    .center
                    .and_then(|center| node_to_bone.get(&center).copied()),
            })
        })
        .collect();

    extensions.spring_bones = vrm_extensions::SpringBoneData {
        springs: remapped_springs,
        colliders: remapped_colliders,
        collider_groups: remapped_groups,
    };

    extensions
}

/// 从 gltf::import 已解码的图片数据写入 PNG 临时文件，返回绝对路径数组
fn extract_textures(model_dir: &Path, images: &[gltf::image::Data]) -> Vec<String> {
    let mut paths = Vec::new();

    for (i, img_data) in images.iter().enumerate() {
        let filename = format!(".vrm_tex_{}.png", i);
        let full_path = model_dir.join(&filename);

        let result = (|| -> std::result::Result<(), Box<dyn std::error::Error>> {
            let (color_type, pixels) = match img_data.format {
                gltf::image::Format::R8G8B8 => (image::ColorType::Rgb8, &img_data.pixels[..]),
                gltf::image::Format::R8G8B8A8 => (image::ColorType::Rgba8, &img_data.pixels[..]),
                gltf::image::Format::R8 => (image::ColorType::L8, &img_data.pixels[..]),
                gltf::image::Format::R8G8 => (image::ColorType::La8, &img_data.pixels[..]),
                _ => {
                    return Err(format!("不支持的图片格式: {:?}", img_data.format).into());
                }
            };
            image::save_buffer(
                &full_path,
                pixels,
                img_data.width,
                img_data.height,
                color_type,
            )?;
            Ok(())
        })();

        match result {
            Ok(_) => {
                log::debug!(
                    "纹理提取: {} ({}x{})",
                    filename,
                    img_data.width,
                    img_data.height
                );
                paths.push(full_path.to_string_lossy().replace('\\', "/"));
            }
            Err(e) => {
                log::warn!("纹理写入失败 {}: {}", filename, e);
                paths.push(String::new());
            }
        }
    }

    paths
}

/// 加载 VRM 模型，转换为 MmdModel 复用现有渲染管线
pub fn load_vrm<P: AsRef<Path>>(path: P) -> Result<MmdModel> {
    load_vrm_with_extensions(path).map(|loaded| loaded.model)
}

/// Loads a VRM model and returns both the runtime model and parsed VRM metadata.
pub fn load_vrm_with_extensions<P: AsRef<Path>>(path: P) -> Result<LoadedVrmModel> {
    let path = path.as_ref();
    let model_dir = path.parent().unwrap_or_else(|| Path::new("."));

    let (document, buffers, images) =
        gltf::import(path).map_err(|e| MmdError::VrmParse(format!("glTF 加载失败: {}", e)))?;

    let vrm_ext = vrm_extensions::parse_vrm_extensions(&document)?;
    let mut mesh = vrm_mesh::merge_meshes(&document, &buffers)?;

    // VRM 使用米为单位，PMX 使用 1 单位 ≈ 0.08 米，需要缩放到 PMX 单位空间
    const VRM_TO_PMX_SCALE: f32 = 12.5;
    for v in &mut mesh.vertices {
        v.position *= VRM_TO_PMX_SCALE;
    }
    for target in &mut mesh.morph_targets {
        for offset in &mut target.position_offsets {
            *offset *= VRM_TO_PMX_SCALE;
        }
    }

    let skin = document
        .skins()
        .next()
        .ok_or_else(|| MmdError::VrmParse("VRM 缺少 skin 数据".into()))?;
    let bone_manager = vrm_skeleton::build_skeleton(&document, &skin, &buffers, &vrm_ext.humanoid)?;
    let vrm_ext = remap_extensions_to_runtime_bones(vrm_ext, &skin);

    let (materials, _placeholder_paths) = vrm_material::convert_materials(&document, &vrm_ext);

    let texture_paths = extract_textures(model_dir, &images);

    let morph_manager = vrm_morph::convert_morph_targets(
        &mesh.morph_targets,
        &vrm_ext.expressions,
        mesh.vertices.len(),
        materials.len(),
    );

    let name = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("vrm_model")
        .to_string();

    let update_positions: Vec<Vec3> = mesh.vertices.iter().map(|v| v.position).collect();
    let update_normals: Vec<Vec3> = mesh.vertices.iter().map(|v| v.normal).collect();
    let update_uvs: Vec<Vec2> = mesh.vertices.iter().map(|v| v.uv).collect();

    let mut model = MmdModel::new();
    model.name = name;
    model.set_vrm(true);
    model.vertices = mesh.vertices;
    model.indices = mesh.indices;
    model.weights = mesh.weights;
    model.materials = materials;
    model.submeshes = mesh.submeshes;
    model.texture_paths = texture_paths;
    model.rigid_bodies = Vec::new();
    model.joints = Vec::new();
    model.update_positions = update_positions;
    model.update_normals = update_normals;
    model.update_uvs = update_uvs;
    model.bone_manager = bone_manager;
    model.morph_manager = morph_manager;

    model
        .morph_manager
        .set_material_count(model.materials.len());
    model.morph_manager.set_vertex_count(model.vertices.len());
    model.init_material_visibility();
    model.initialize_animation();
    model.tick_animation(0.0);
    model.initialize_vrm_runtime(vrm_ext.clone());

    log::info!(
        "VRM 加载完成: {} (顶点:{}, 面:{}, 材质:{}, 骨骼:{}, 纹理:{})",
        model.name,
        model.vertex_count(),
        model.index_count() / 3,
        model.material_count(),
        model.bone_manager.bone_count(),
        model.texture_paths.len(),
    );

    Ok(LoadedVrmModel {
        model,
        extensions: vrm_ext,
    })
}
