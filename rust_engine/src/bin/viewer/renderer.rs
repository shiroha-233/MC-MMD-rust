use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    sync::Arc,
};

use glam::{Mat4, Vec3};
use glium::{
    backend::Facade,
    index::{NoIndices, PrimitiveType},
    texture::{RawImage2d, SrgbTexture2d},
    uniform, DrawParameters, Frame, Surface,
};
use mmd_engine::{
    animation::{
        fbx_loader::{FbxCache, FbxRetargetOptions},
        VmdAnimation, VmdFile,
    },
    model::{load_pmx, MmdModel},
};

use super::camera::Camera;

const PRIMARY_ANIMATION_LAYER: usize = 0;
fn mat4_to_array(matrix: &Mat4) -> [[f32; 4]; 4] {
    [
        [
            matrix.x_axis.x,
            matrix.x_axis.y,
            matrix.x_axis.z,
            matrix.x_axis.w,
        ],
        [
            matrix.y_axis.x,
            matrix.y_axis.y,
            matrix.y_axis.z,
            matrix.y_axis.w,
        ],
        [
            matrix.z_axis.x,
            matrix.z_axis.y,
            matrix.z_axis.z,
            matrix.z_axis.w,
        ],
        [
            matrix.w_axis.x,
            matrix.w_axis.y,
            matrix.w_axis.z,
            matrix.w_axis.w,
        ],
    ]
}

fn vec3_to_array(vector: Vec3) -> [f32; 3] {
    [vector.x, vector.y, vector.z]
}

#[derive(Copy, Clone)]
struct Vertex {
    position: [f32; 3],
    normal: [f32; 3],
    uv: [f32; 2],
}

glium::implement_vertex!(Vertex, position, normal, uv);

#[derive(Copy, Clone)]
struct LineVertex {
    position: [f32; 3],
    color: [f32; 4],
}

glium::implement_vertex!(LineVertex, position, color);

pub struct Renderer {
    model: Option<MmdModel>,
    animation: Option<Arc<VmdAnimation>>,
    mesh_program: glium::Program,
    line_program: glium::Program,
    show_mesh: bool,
    show_bones: bool,
    show_axes: bool,
    wireframe: bool,
    playing: bool,
    current_frame: f32,
    grid_vbo: glium::VertexBuffer<LineVertex>,
    axes_vbo: glium::VertexBuffer<LineVertex>,
    textures: HashMap<i32, SrgbTexture2d>,
    default_texture: SrgbTexture2d,
    model_dir: Option<PathBuf>,
    fbx_caches: HashMap<String, FbxCache>,
}

impl Renderer {
    pub fn new(display: &impl Facade) -> Self {
        let mesh_program =
            glium::Program::from_source(display, MESH_VERTEX_SHADER, MESH_FRAGMENT_SHADER, None)
                .expect("Failed to compile mesh shader");
        let line_program =
            glium::Program::from_source(display, LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER, None)
                .expect("Failed to compile line shader");

        Self {
            model: None,
            animation: None,
            mesh_program,
            line_program,
            show_mesh: true,
            show_bones: true,
            show_axes: true,
            wireframe: false,
            playing: true,
            current_frame: 0.0,
            grid_vbo: Self::create_grid(display, 50.0, 50),
            axes_vbo: Self::create_axes(display, 5.0),
            textures: HashMap::new(),
            default_texture: Self::create_default_texture(display),
            model_dir: None,
            fbx_caches: HashMap::new(),
        }
    }

    pub fn has_model(&self) -> bool {
        self.model.is_some()
    }

    pub fn has_animation(&self) -> bool {
        self.animation.is_some()
    }

    pub fn is_playing(&self) -> bool {
        self.playing
    }

    pub fn current_frame(&self) -> f32 {
        self.current_frame
    }

    pub fn load_model(&mut self, display: &impl Facade, path: &str) -> Result<(), String> {
        let mut model = load_pmx(path).map_err(|error| error.to_string())?;
        model.reset_to_rest_pose();

        self.model_dir = Path::new(path).parent().map(Path::to_path_buf);
        self.model = Some(model);
        self.animation = None;
        self.current_frame = 0.0;
        self.textures.clear();
        self.load_textures(display);

        Ok(())
    }

    pub fn list_fbx_stacks(&mut self, path: &str) -> Result<Vec<String>, String> {
        if !Self::is_fbx_file(path) {
            return Err("当前文件不是 FBX 动作".to_string());
        }

        let cache = self.get_or_load_fbx_cache(path)?;
        Ok(cache.stack_names().to_vec())
    }

    pub fn load_animation(&mut self, path: &str, stack_name: Option<&str>) -> Result<(), String> {
        let animation = if Self::is_fbx_file(path) {
            let arm_positions = self
                .model
                .as_ref()
                .map(Self::collect_retarget_bone_positions);
            let animation = {
                let cache = self.get_or_load_fbx_cache(path)?;
                cache
                    .load_animation_with_options(FbxRetargetOptions {
                        stack_name,
                        target_bone_positions: arm_positions.as_ref(),
                        ..Default::default()
                    })
                    .map_err(|error| error.to_string())?
            };
            Arc::new(animation)
        } else {
            let vmd = VmdFile::load(path).map_err(|error| error.to_string())?;
            Arc::new(VmdAnimation::from_vmd_file(vmd))
        };

        let max_frame = animation.max_frame();

        if let Some(model) = self.model.as_mut() {
            model.set_layer_animation(PRIMARY_ANIMATION_LAYER, Some(animation.clone()));
            model.set_layer_loop(PRIMARY_ANIMATION_LAYER, true);
            model.seek_layer(PRIMARY_ANIMATION_LAYER, 0.0);
            if self.playing {
                model.play_layer(PRIMARY_ANIMATION_LAYER);
            } else {
                model.pause_layer(PRIMARY_ANIMATION_LAYER);
            }
            model.tick_animation(0.0);
        }

        self.animation = Some(animation);
        self.current_frame = 0.0;
        println!("动作加载成功，最大帧数: {}", max_frame);

        Ok(())
    }

    pub fn clear_animation(&mut self) {
        self.animation = None;
        self.current_frame = 0.0;

        if let Some(model) = self.model.as_mut() {
            model.stop_layer(PRIMARY_ANIMATION_LAYER);
            model.set_layer_animation(PRIMARY_ANIMATION_LAYER, None);
            model.reset_to_rest_pose();
        }
    }

    pub fn update(&mut self, delta_time: f32) {
        let Some(animation) = &self.animation else {
            return;
        };
        if !self.playing {
            return;
        }

        if let Some(model) = self.model.as_mut() {
            model.tick_animation(delta_time);
            self.current_frame += delta_time * 30.0;
            let max_frame = animation.max_frame() as f32;
            if self.current_frame > max_frame {
                self.current_frame = 0.0;
            }
        }
    }

    pub fn render(&self, display: &impl Facade, target: &mut Frame, camera: &Camera) {
        let view = camera.view_matrix();
        let projection = camera.projection_matrix();
        let vp = projection * view;

        let draw_params = DrawParameters {
            depth: glium::Depth {
                test: glium::DepthTest::IfLess,
                write: true,
                ..Default::default()
            },
            backface_culling: glium::BackfaceCullingMode::CullClockwise,
            polygon_mode: if self.wireframe {
                glium::PolygonMode::Line
            } else {
                glium::PolygonMode::Fill
            },
            ..Default::default()
        };

        let line_params = DrawParameters {
            depth: glium::Depth {
                test: glium::DepthTest::IfLess,
                write: false,
                ..Default::default()
            },
            line_width: Some(1.0),
            ..Default::default()
        };

        self.render_grid(target, &vp, &line_params);

        if self.show_axes {
            self.render_axes(target, &vp);
        }

        if self.show_mesh {
            if let Some(model) = &self.model {
                self.render_model(display, target, model, &vp, &draw_params);
            }
        }

        if self.show_bones {
            if let Some(model) = &self.model {
                self.render_bones(display, target, model, &vp);
            }
        }
    }

    pub fn toggle_animation(&mut self) {
        if self.animation.is_none() {
            return;
        }

        self.playing = !self.playing;
        if let Some(model) = self.model.as_mut() {
            if self.playing {
                model.resume_layer(PRIMARY_ANIMATION_LAYER);
            } else {
                model.pause_layer(PRIMARY_ANIMATION_LAYER);
            }
        }
    }

    pub fn reset_animation(&mut self) {
        self.current_frame = 0.0;

        if let Some(model) = self.model.as_mut() {
            if self.animation.is_some() {
                model.seek_layer(PRIMARY_ANIMATION_LAYER, 0.0);
                model.tick_animation(0.0);
                if self.playing {
                    model.resume_layer(PRIMARY_ANIMATION_LAYER);
                } else {
                    model.pause_layer(PRIMARY_ANIMATION_LAYER);
                }
            } else {
                model.reset_to_rest_pose();
            }
        }
    }

    pub fn toggle_bones(&mut self) {
        self.show_bones = !self.show_bones;
    }

    pub fn toggle_mesh(&mut self) {
        self.show_mesh = !self.show_mesh;
    }

    pub fn toggle_axes(&mut self) {
        self.show_axes = !self.show_axes;
    }

    pub fn toggle_wireframe(&mut self) {
        self.wireframe = !self.wireframe;
    }

    fn create_default_texture(display: &impl Facade) -> SrgbTexture2d {
        let raw = RawImage2d::from_raw_rgba(vec![255_u8; 4], (1, 1));
        SrgbTexture2d::new(display, raw).expect("Failed to create default texture")
    }

    fn create_grid(
        display: &impl Facade,
        size: f32,
        divisions: i32,
    ) -> glium::VertexBuffer<LineVertex> {
        let mut vertices = Vec::new();
        let step = size * 2.0 / divisions as f32;
        let color = [0.4, 0.4, 0.4, 1.0];

        for i in 0..=divisions {
            let pos = -size + i as f32 * step;
            vertices.push(LineVertex {
                position: [-size, 0.0, pos],
                color,
            });
            vertices.push(LineVertex {
                position: [size, 0.0, pos],
                color,
            });
            vertices.push(LineVertex {
                position: [pos, 0.0, -size],
                color,
            });
            vertices.push(LineVertex {
                position: [pos, 0.0, size],
                color,
            });
        }

        glium::VertexBuffer::new(display, &vertices).expect("Failed to create grid buffer")
    }

    fn create_axes(display: &impl Facade, size: f32) -> glium::VertexBuffer<LineVertex> {
        let vertices = vec![
            LineVertex {
                position: [0.0, 0.0, 0.0],
                color: [1.0, 0.0, 0.0, 1.0],
            },
            LineVertex {
                position: [size, 0.0, 0.0],
                color: [1.0, 0.0, 0.0, 1.0],
            },
            LineVertex {
                position: [0.0, 0.0, 0.0],
                color: [0.0, 1.0, 0.0, 1.0],
            },
            LineVertex {
                position: [0.0, size, 0.0],
                color: [0.0, 1.0, 0.0, 1.0],
            },
            LineVertex {
                position: [0.0, 0.0, 0.0],
                color: [0.0, 0.0, 1.0, 1.0],
            },
            LineVertex {
                position: [0.0, 0.0, size],
                color: [0.0, 0.0, 1.0, 1.0],
            },
        ];

        glium::VertexBuffer::new(display, &vertices).expect("Failed to create axes buffer")
    }

    fn load_textures(&mut self, display: &impl Facade) {
        self.textures.clear();

        let Some(model) = &self.model else {
            return;
        };

        for (index, texture_path) in model.texture_paths.iter().enumerate() {
            let resolved = Self::resolve_texture_path(self.model_dir.as_deref(), texture_path);
            match Self::load_texture_file(display, &resolved) {
                Ok(texture) => {
                    self.textures.insert(index as i32, texture);
                }
                Err(error) => {
                    eprintln!(
                        "贴图加载失败 [{}]: {} ({})",
                        index,
                        resolved.display(),
                        error
                    );
                }
            }
        }
    }

    fn load_texture_file(display: &impl Facade, path: &Path) -> Result<SrgbTexture2d, String> {
        let image = image::open(path)
            .map_err(|error| format!("打开图片失败: {}", error))?
            .to_rgba8();
        let dimensions = image.dimensions();
        let raw = RawImage2d::from_raw_rgba_reversed(&image.into_raw(), dimensions);
        SrgbTexture2d::new(display, raw).map_err(|error| format!("创建 OpenGL 贴图失败: {}", error))
    }

    fn resolve_texture_path(model_dir: Option<&Path>, raw_path: &str) -> PathBuf {
        let path = Path::new(raw_path);
        if path.is_absolute() {
            return path.to_path_buf();
        }

        if let Some(model_dir) = model_dir {
            let resolved = model_dir.join(path);
            if resolved.exists() {
                return resolved;
            }
        }

        path.to_path_buf()
    }

    fn get_or_load_fbx_cache(&mut self, path: &str) -> Result<&FbxCache, String> {
        if !self.fbx_caches.contains_key(path) {
            let cache = FbxCache::load(path).map_err(|error| error.to_string())?;
            self.fbx_caches.insert(path.to_string(), cache);
        }

        self.fbx_caches
            .get(path)
            .ok_or_else(|| "FBX 缓存建立失败".to_string())
    }

    fn is_fbx_file(path: &str) -> bool {
        path.to_ascii_lowercase().ends_with(".fbx")
    }

    fn collect_retarget_bone_positions(model: &MmdModel) -> HashMap<String, Vec3> {
        let mut positions = HashMap::new();
        for name in [
            "左肩",
            "左腕",
            "左ひじ",
            "左手首",
            "右肩",
            "右腕",
            "右ひじ",
            "右手首",
        ] {
            if let Some(index) = model.bone_manager.find_bone_by_name(name) {
                if let Some(bone) = model.bone_manager.get_bone(index) {
                    positions.insert(name.to_string(), bone.initial_position);
                }
            }
        }
        positions
    }

    fn render_grid(&self, target: &mut Frame, vp: &Mat4, params: &DrawParameters<'_>) {
        let uniforms = uniform! {
            vp: mat4_to_array(vp),
        };

        let _ = target.draw(
            &self.grid_vbo,
            NoIndices(PrimitiveType::LinesList),
            &self.line_program,
            &uniforms,
            params,
        );
    }

    fn render_axes(&self, target: &mut Frame, vp: &Mat4) {
        let params = DrawParameters {
            depth: glium::Depth {
                test: glium::DepthTest::IfLess,
                write: false,
                ..Default::default()
            },
            line_width: Some(3.0),
            ..Default::default()
        };

        let uniforms = uniform! {
            vp: mat4_to_array(vp),
        };

        let _ = target.draw(
            &self.axes_vbo,
            NoIndices(PrimitiveType::LinesList),
            &self.line_program,
            &uniforms,
            &params,
        );
    }

    fn render_model(
        &self,
        display: &impl Facade,
        target: &mut Frame,
        model: &MmdModel,
        vp: &Mat4,
        base_params: &DrawParameters<'_>,
    ) {
        let vertex_count = model.vertex_count();
        if vertex_count == 0 {
            return;
        }

        let mut vertices = Vec::with_capacity(vertex_count);
        for index in 0..vertex_count {
            let position_index = index * 3;
            let uv_index = index * 2;
            vertices.push(Vertex {
                position: [
                    model
                        .update_positions_raw
                        .get(position_index)
                        .copied()
                        .unwrap_or(0.0),
                    model
                        .update_positions_raw
                        .get(position_index + 1)
                        .copied()
                        .unwrap_or(0.0),
                    model
                        .update_positions_raw
                        .get(position_index + 2)
                        .copied()
                        .unwrap_or(0.0),
                ],
                normal: [
                    model
                        .update_normals_raw
                        .get(position_index)
                        .copied()
                        .unwrap_or(0.0),
                    model
                        .update_normals_raw
                        .get(position_index + 1)
                        .copied()
                        .unwrap_or(0.0),
                    model
                        .update_normals_raw
                        .get(position_index + 2)
                        .copied()
                        .unwrap_or(1.0),
                ],
                uv: [
                    model.update_uvs_raw.get(uv_index).copied().unwrap_or(0.0),
                    model
                        .update_uvs_raw
                        .get(uv_index + 1)
                        .copied()
                        .unwrap_or(0.0),
                ],
            });
        }

        let Ok(vertex_buffer) = glium::VertexBuffer::new(display, &vertices) else {
            return;
        };

        let vp_array = mat4_to_array(vp);
        let model_matrix = mat4_to_array(&Mat4::IDENTITY);
        let light_dir = vec3_to_array(Vec3::new(0.5, 1.0, 0.3).normalize());

        for submesh in &model.submeshes {
            let material = model.materials.get(submesh.material_id as usize);
            let (diffuse, texture_index, double_sided) = match material {
                Some(material) => (
                    [
                        material.diffuse.x,
                        material.diffuse.y,
                        material.diffuse.z,
                        material.diffuse.w,
                    ],
                    material.texture_index,
                    material.is_double_sided(),
                ),
                None => ([1.0, 1.0, 1.0, 1.0], -1, false),
            };

            if diffuse[3] < 0.01 {
                continue;
            }

            let begin = submesh.begin_index as usize;
            let end = begin.saturating_add(submesh.index_count as usize);
            if end > model.indices.len() {
                continue;
            }

            let texture = self
                .textures
                .get(&texture_index)
                .unwrap_or(&self.default_texture);

            let params = DrawParameters {
                backface_culling: if double_sided {
                    glium::BackfaceCullingMode::CullingDisabled
                } else {
                    base_params.backface_culling
                },
                blend: glium::Blend::alpha_blending(),
                ..base_params.clone()
            };

            let Ok(index_buffer) = glium::IndexBuffer::new(
                display,
                PrimitiveType::TrianglesList,
                &model.indices[begin..end],
            ) else {
                continue;
            };

            let uniforms = uniform! {
                model_mat: model_matrix,
                vp: vp_array,
                light_dir: light_dir,
                diffuse_color: diffuse,
                tex: texture,
                has_texture: if texture_index >= 0 { 1_i32 } else { 0_i32 },
            };

            let _ = target.draw(
                &vertex_buffer,
                &index_buffer,
                &self.mesh_program,
                &uniforms,
                &params,
            );
        }
    }

    fn render_bones(&self, display: &impl Facade, target: &mut Frame, model: &MmdModel, vp: &Mat4) {
        let bone_count = model.bone_manager.bone_count();
        if bone_count == 0 {
            return;
        }

        let mut vertices = Vec::new();
        for index in 0..bone_count {
            let Some(bone) = model.bone_manager.get_bone(index) else {
                continue;
            };

            let transform = bone.global_transform();
            let position = transform.col(3).truncate();
            let color = if bone.is_ik() {
                [1.0, 0.5, 0.0, 1.0]
            } else if bone.deform_after_physics() {
                [0.0, 1.0, 1.0, 1.0]
            } else {
                [1.0, 1.0, 0.0, 1.0]
            };

            if bone.parent_index >= 0 && (bone.parent_index as usize) < bone_count {
                if let Some(parent) = model.bone_manager.get_bone(bone.parent_index as usize) {
                    let parent_position = parent.global_transform().col(3).truncate();
                    vertices.push(LineVertex {
                        position: vec3_to_array(position),
                        color,
                    });
                    vertices.push(LineVertex {
                        position: vec3_to_array(parent_position),
                        color: [0.8, 0.8, 0.0, 1.0],
                    });
                }
            }

            let scale = 0.5;
            let x_axis = transform.col(0).truncate().normalize_or_zero() * scale;
            let y_axis = transform.col(1).truncate().normalize_or_zero() * scale;
            let z_axis = transform.col(2).truncate().normalize_or_zero() * scale;

            vertices.push(LineVertex {
                position: vec3_to_array(position),
                color: [1.0, 0.0, 0.0, 1.0],
            });
            vertices.push(LineVertex {
                position: vec3_to_array(position + x_axis),
                color: [1.0, 0.0, 0.0, 1.0],
            });
            vertices.push(LineVertex {
                position: vec3_to_array(position),
                color: [0.0, 1.0, 0.0, 1.0],
            });
            vertices.push(LineVertex {
                position: vec3_to_array(position + y_axis),
                color: [0.0, 1.0, 0.0, 1.0],
            });
            vertices.push(LineVertex {
                position: vec3_to_array(position),
                color: [0.0, 0.0, 1.0, 1.0],
            });
            vertices.push(LineVertex {
                position: vec3_to_array(position + z_axis),
                color: [0.0, 0.0, 1.0, 1.0],
            });
        }

        if vertices.is_empty() {
            return;
        }

        let Ok(vertex_buffer) = glium::VertexBuffer::new(display, &vertices) else {
            return;
        };

        let params = DrawParameters {
            depth: glium::Depth {
                test: glium::DepthTest::IfLess,
                write: false,
                ..Default::default()
            },
            line_width: Some(2.0),
            ..Default::default()
        };

        let uniforms = uniform! {
            vp: mat4_to_array(vp),
        };

        let _ = target.draw(
            &vertex_buffer,
            NoIndices(PrimitiveType::LinesList),
            &self.line_program,
            &uniforms,
            &params,
        );
    }
}

const MESH_VERTEX_SHADER: &str = r#"
#version 330 core

in vec3 position;
in vec3 normal;
in vec2 uv;

uniform mat4 model_mat;
uniform mat4 vp;

out vec3 v_normal;
out vec2 v_uv;
out vec3 v_position;

void main() {
    vec4 world_pos = model_mat * vec4(position, 1.0);
    gl_Position = vp * world_pos;
    v_normal = mat3(model_mat) * normal;
    v_uv = uv;
    v_position = world_pos.xyz;
}
"#;

const MESH_FRAGMENT_SHADER: &str = r#"
#version 330 core

in vec3 v_normal;
in vec2 v_uv;
in vec3 v_position;

uniform vec3 light_dir;
uniform vec4 diffuse_color;
uniform sampler2D tex;
uniform int has_texture;

out vec4 color;

void main() {
    vec3 normal = normalize(v_normal);

    float ndotl = dot(normal, light_dir);
    if (ndotl < 0.0) {
        ndotl = -ndotl * 0.5;
    }

    float ambient = 0.75;
    float diffuse = ndotl * 0.35;

    vec4 base_color;
    if (has_texture == 1) {
        base_color = texture(tex, v_uv) * diffuse_color;
    } else {
        base_color = diffuse_color;
    }

    if (base_color.a < 0.01) {
        discard;
    }

    vec3 final_color = base_color.rgb * (ambient + diffuse);
    color = vec4(final_color, base_color.a);
}
"#;

const LINE_VERTEX_SHADER: &str = r#"
#version 330 core

in vec3 position;
in vec4 color;

uniform mat4 vp;

out vec4 v_color;

void main() {
    gl_Position = vp * vec4(position, 1.0);
    v_color = color;
}
"#;

const LINE_FRAGMENT_SHADER: &str = r#"
#version 330 core

in vec4 v_color;

out vec4 color;

void main() {
    color = v_color;
}
"#;
