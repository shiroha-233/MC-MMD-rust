//! PMX 模型加载器

use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use glam::{Vec2, Vec3, Vec4};
use mmd::pmx::types::DefaultConfig;
use mmd::pmx::weight_deform::WeightDeform;
use mmd::reader::{DisplayFrameReader, JointReader, RigidBodyReader};
use mmd::{
    BoneReader, HeaderReader, MaterialReader, MorphReader, SurfaceReader, TextureReader,
    VertexReader,
};

use crate::skeleton::Bone;
use crate::{MmdError, Result};

use super::{MmdMaterial, MmdModel, RuntimeVertex, SubMesh, VertexWeight};

/// 从 PMX 文件加载模型
pub fn load_pmx<P: AsRef<Path>>(path: P) -> Result<MmdModel> {
    let file = File::open(path.as_ref()).map_err(|e| MmdError::Io(e))?;
    let mut reader = BufReader::new(file);

    // 获取模型所在目录（用于组合纹理路径）
    let model_dir = path
        .as_ref()
        .parent()
        .map(|p| p.to_path_buf())
        .unwrap_or_default();

    // 读取头部
    let header_reader = HeaderReader::new(&mut reader)
        .map_err(|e| MmdError::PmxParse(format!("Header error: {:?}", e)))?;

    let model_name = header_reader.model_local_name.clone();

    // 读取顶点
    let mut vertex_reader = VertexReader::new(header_reader)
        .map_err(|e| MmdError::PmxParse(format!("Vertex reader error: {:?}", e)))?;

    let mut vertices = Vec::new();
    let mut weights = Vec::new();

    while let Some(v) = vertex_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Vertex error: {:?}", e)))?
    {
        // MMD使用左手坐标系，翻转Z轴转换为右手坐标系
        // UV坐标：与C++一致 glm::vec2 uv = glm::vec2(v.m_uv.x, 1.0f - v.m_uv.y)
        vertices.push(RuntimeVertex {
            position: Vec3::new(v.position[0], v.position[1], -v.position[2]),
            normal: Vec3::new(v.normal[0], v.normal[1], -v.normal[2]),
            uv: Vec2::new(v.uv[0], 1.0 - v.uv[1]),
        });

        weights.push(convert_weight_deform(v.weight_deform));
    }

    // 读取面
    let mut surface_reader = SurfaceReader::new(vertex_reader)
        .map_err(|e| MmdError::PmxParse(format!("Surface reader error: {:?}", e)))?;

    let mut indices: Vec<u32> = Vec::new();
    while let Some(triangle) = surface_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Surface error: {:?}", e)))?
    {
        // 与C++一致：face.m_vertices[3 - i - 1] 即 [2,1,0] 顺序
        // 翻转Z轴后需要反转三角形顺序以保持正确的面朝向
        indices.push(triangle[2] as u32);
        indices.push(triangle[1] as u32);
        indices.push(triangle[0] as u32);
    }

    // 读取纹理（与C++版本一致，组合为完整路径）
    let mut texture_reader = TextureReader::new(surface_reader)
        .map_err(|e| MmdError::PmxParse(format!("Texture reader error: {:?}", e)))?;

    let mut texture_paths = Vec::new();
    while let Some(tex_path) = texture_reader
        .next()
        .map_err(|e| MmdError::PmxParse(format!("Texture error: {:?}", e)))?
    {
        // 将相对路径与模型目录组合，并规范化路径分隔符
        let full_path = model_dir.join(&tex_path);
        let normalized = normalize_path(&full_path);
        texture_paths.push(normalized);
    }

    // 读取材质
    let mut material_reader = MaterialReader::new(texture_reader)
        .map_err(|e| MmdError::PmxParse(format!("Material reader error: {:?}", e)))?;

    let mut materials = Vec::new();
    let mut submeshes = Vec::new();
    let mut index_offset = 0u32;

    while let Some(mat) = material_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Material error: {:?}", e)))?
    {
        let surface_count = mat.surface_count as u32;

        submeshes.push(SubMesh {
            begin_index: index_offset,
            index_count: surface_count,
            material_id: materials.len() as i32,
        });

        materials.push(MmdMaterial {
            name: mat.local_name.clone(),
            diffuse: vec4_from_arr(mat.diffuse_color),
            specular: vec3_from_arr(mat.specular_color),
            specular_strength: mat.specular_strength,
            ambient: vec3_from_arr(mat.ambient_color),
            edge_color: vec4_from_arr(mat.edge_color),
            edge_scale: mat.edge_scale,
            texture_index: mat.texture_index,
            environment_index: mat.environment_index,
            toon_index: match mat.toon {
                mmd::pmx::material::Toon::Texture(idx) => idx,
                mmd::pmx::material::Toon::Internal(idx) => -(idx as i32) - 1,
            },
            draw_flags: mat.draw_flags.bits(),
        });

        index_offset += surface_count;
    }

    // 读取骨骼
    let mut bone_reader = BoneReader::new(material_reader)
        .map_err(|e| MmdError::PmxParse(format!("Bone reader error: {:?}", e)))?;

    let mut bone_manager = crate::skeleton::BoneManager::new();

    while let Some(bone) = bone_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Bone error: {:?}", e)))?
    {
        bone_manager.add_bone(Bone::from_pmx_bone(&bone));
    }

    bone_manager.build_hierarchy();

    // 读取变形
    let mut morph_reader = MorphReader::new(bone_reader)
        .map_err(|e| MmdError::PmxParse(format!("Morph reader error: {:?}", e)))?;
    
    let mut morph_manager = crate::morph::MorphManager::new();
    
    while let Some(pmx_morph) = morph_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Morph error: {:?}", e)))?
    {
        let morph = convert_pmx_morph(&pmx_morph);
        morph_manager.add_morph(morph);
    }
    
    log::info!("加载 {} 个 Morph", morph_manager.morph_count());

    // 读取显示帧（跳过）
    let mut display_frame_reader = DisplayFrameReader::new(morph_reader)
        .map_err(|e| MmdError::PmxParse(format!("DisplayFrame reader error: {:?}", e)))?;
    while display_frame_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("DisplayFrame error: {:?}", e)))?
        .is_some()
    {}

    // 读取刚体
    let mut rigid_body_reader = RigidBodyReader::new(display_frame_reader)
        .map_err(|e| MmdError::PmxParse(format!("RigidBody reader error: {:?}", e)))?;
    let mut rigid_bodies = Vec::new();
    while let Some(rb) = rigid_body_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("RigidBody error: {:?}", e)))?
    {
        rigid_bodies.push(rb);
    }

    // 读取关节
    let mut joint_reader = JointReader::new(rigid_body_reader)
        .map_err(|e| MmdError::PmxParse(format!("Joint reader error: {:?}", e)))?;
    let mut joints = Vec::new();
    while let Some(j) = joint_reader
        .next::<DefaultConfig>()
        .map_err(|e| MmdError::PmxParse(format!("Joint error: {:?}", e)))?
    {
        joints.push(j);
    }

    // 初始化更新缓冲区
    let update_positions: Vec<Vec3> = vertices.iter().map(|v| v.position).collect();
    let update_normals: Vec<Vec3> = vertices.iter().map(|v| v.normal).collect();
    let update_uvs: Vec<Vec2> = vertices.iter().map(|v| v.uv).collect();

    let mut model = MmdModel::new();
    model.name = model_name;
    model.vertices = vertices;
    model.indices = indices;
    model.weights = weights;
    model.materials = materials;
    model.submeshes = submeshes;
    model.texture_paths = texture_paths;
    model.rigid_bodies = rigid_bodies;
    model.joints = joints;
    model.update_positions = update_positions;
    model.update_normals = update_normals;
    model.update_uvs = update_uvs;
    model.bone_manager = bone_manager;
    model.morph_manager = morph_manager;
    
    // 初始化 Morph 系统缓冲区（材质 Morph 结果 + UV Morph 偏移）
    model.morph_manager.set_material_count(model.materials.len());
    model.morph_manager.set_vertex_count(model.vertices.len());
    
    // 初始化材质可见性（默认全部可见）
    model.init_material_visibility();

    // 初始化后立即计算一次蒙皮，确保顶点位置正确
    model.update();

    Ok(model)
}

#[allow(dead_code)]
fn vec2_from_arr(v: [f32; 2]) -> Vec2 {
    Vec2::new(v[0], v[1])
}

fn vec3_from_arr(v: [f32; 3]) -> Vec3 {
    Vec3::new(v[0], v[1], v[2])
}

fn vec4_from_arr(v: [f32; 4]) -> Vec4 {
    Vec4::new(v[0], v[1], v[2], v[3])
}

/// 规范化路径（统一使用正斜杠，与C++版本PathUtil::Normalize一致）
fn normalize_path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

/// 转换 PMX Morph 到内部格式
fn convert_pmx_morph(pmx_morph: &mmd::pmx::morph::Morph<DefaultConfig>) -> crate::morph::Morph {
    use mmd::pmx::morph::Offsets;
    use crate::morph::{
        Morph, MorphType, VertexMorphOffset, BoneMorphOffset,
        MaterialMorphOffset, UvMorphOffset, GroupMorphOffset,
    };
    
    let morph = match &pmx_morph.offsets {
        Offsets::Vertex(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Vertex);
            m.vertex_offsets = offsets.iter().map(|o| {
                VertexMorphOffset {
                    vertex_index: o.vertex as u32,
                    offset: Vec3::new(o.offset[0], o.offset[1], -o.offset[2]),
                }
            }).collect();
            m
        }
        Offsets::Bone(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Bone);
            m.bone_offsets = offsets.iter().map(|o| {
                BoneMorphOffset {
                    bone_index: o.bone as u32,
                    translation: Vec3::new(o.translation[0], o.translation[1], -o.translation[2]),
                    rotation: Vec4::new(o.rotation[0], o.rotation[1], -o.rotation[2], -o.rotation[3]),
                }
            }).collect();
            m
        }
        Offsets::Group(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Group);
            m.group_offsets = offsets.iter().map(|o| {
                GroupMorphOffset {
                    morph_index: o.morph as u32,
                    influence: o.influence,
                }
            }).collect();
            m
        }
        Offsets::Material(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Material);
            m.material_offsets = offsets.iter().map(|o| {
                MaterialMorphOffset {
                    material_index: o.material,
                    operation: o.method as u8,
                    diffuse: vec4_from_arr(o.diffuse_color),
                    specular: vec3_from_arr(o.specular_color),
                    specular_strength: o.specular_strength,
                    ambient: vec3_from_arr(o.ambient_color),
                    edge_color: vec4_from_arr(o.edge_color),
                    edge_size: o.edge_scale,
                    texture_tint: vec4_from_arr(o.texture_tint),
                    environment_tint: vec4_from_arr(o.environment_tint),
                    toon_tint: vec4_from_arr(o.toon_tint),
                }
            }).collect();
            m
        }
        Offsets::UV(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Uv);
            m.uv_offsets = offsets.iter().map(|o| {
                UvMorphOffset {
                    vertex_index: o.vertex as u32,
                    offset: vec4_from_arr(o.offset),
                }
            }).collect();
            m
        }
        Offsets::AdditionalUV1(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::AdditionalUv1);
            m.uv_offsets = offsets.iter().map(|o| {
                UvMorphOffset {
                    vertex_index: o.vertex as u32,
                    offset: vec4_from_arr(o.offset),
                }
            }).collect();
            m
        }
        Offsets::AdditionalUV2(_) => Morph::new(pmx_morph.local_name.clone(), MorphType::AdditionalUv2),
        Offsets::AdditionalUV3(_) => Morph::new(pmx_morph.local_name.clone(), MorphType::AdditionalUv3),
        Offsets::AdditionalUV4(_) => Morph::new(pmx_morph.local_name.clone(), MorphType::AdditionalUv4),
        Offsets::Flip(offsets) => {
            let mut m = Morph::new(pmx_morph.local_name.clone(), MorphType::Flip);
            m.group_offsets = offsets.iter().map(|o| {
                GroupMorphOffset {
                    morph_index: o.morph as u32,
                    influence: o.influence,
                }
            }).collect();
            m
        }
        Offsets::Impulse(_) => Morph::new(pmx_morph.local_name.clone(), MorphType::Impulse),
    };
    
    morph
}

fn convert_weight_deform(wd: WeightDeform<DefaultConfig>) -> VertexWeight {
    match wd {
        WeightDeform::Bdef1(bdef1) => VertexWeight::Bdef1 {
            bone: bdef1.bone_index,
        },
        WeightDeform::Bdef2(bdef2) => VertexWeight::Bdef2 {
            bones: [bdef2.bone_1_index, bdef2.bone_2_index],
            weight: bdef2.bone_1_weight,
        },
        WeightDeform::Bdef4(bdef4) => VertexWeight::Bdef4 {
            bones: [
                bdef4.bone_1_index,
                bdef4.bone_2_index,
                bdef4.bone_3_index,
                bdef4.bone_4_index,
            ],
            weights: [
                bdef4.bone_1_weight,
                bdef4.bone_2_weight,
                bdef4.bone_3_weight,
                bdef4.bone_4_weight,
            ],
        },
        WeightDeform::Sdef(sdef) => VertexWeight::Sdef {
            bones: [sdef.bone_1_index, sdef.bone_2_index],
            weight: sdef.bone_1_weight,
            c: vec3_from_arr(sdef.c),
            r0: vec3_from_arr(sdef.r0),
            r1: vec3_from_arr(sdef.r1),
        },
        WeightDeform::Qdef(qdef) => VertexWeight::Qdef {
            bones: [
                qdef.bone_1_index,
                qdef.bone_2_index,
                qdef.bone_3_index,
                qdef.bone_4_index,
            ],
            weights: [
                qdef.bone_1_weight,
                qdef.bone_2_weight,
                qdef.bone_3_weight,
                qdef.bone_4_weight,
            ],
        },
    }
}
