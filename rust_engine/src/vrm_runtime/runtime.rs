//! Public runtime wrapper adapted from UniVRM's `Vrm10Runtime` API shape (MIT).

use std::collections::HashMap;
use std::path::Path;

use glam::{Quat, Vec3};

use crate::model::{load_vrm_with_extensions, LoadedVrmModel, MmdMaterial, MmdModel, SubMesh};
use crate::Result;

use super::render_state::{VrmRenderState, VrmView};
use super::{ExpressionKey, EyeDirection, VrmRuntimeInput};

#[derive(Clone)]
pub struct RuntimeAssets {
    pub name: String,
    pub vertex_count: usize,
    pub indices: Vec<u32>,
    pub materials: Vec<MmdMaterial>,
    pub submeshes: Vec<SubMesh>,
    pub texture_paths: Vec<String>,
}

#[derive(Clone, Debug, Default)]
pub struct VrmRuntimeOutput {
    pub input_weights: HashMap<ExpressionKey, f32>,
    pub actual_weights: HashMap<ExpressionKey, f32>,
    pub blink_override_rate: f32,
    pub look_at_override_rate: f32,
    pub mouth_override_rate: f32,
    pub eye_direction: EyeDirection,
    pub hmd_visible_materials: Vec<bool>,
    pub mirror_visible_materials: Vec<bool>,
    pub view_anchor_model: Vec3,
    pub head_rotation: Quat,
    pub first_person_source: String,
    pub head_local_model: Vec3,
    pub body_anchor_model: Vec3,
    pub left_palm_target_model: Vec3,
    pub right_palm_target_model: Vec3,
    pub left_wrist_solved_model: Vec3,
    pub right_wrist_solved_model: Vec3,
    pub left_wrist_error_cm: f32,
    pub right_wrist_error_cm: f32,
}

pub struct VrmRuntime {
    model: MmdModel,
    assets: RuntimeAssets,
}

impl VrmRuntime {
    pub fn load(path: impl AsRef<Path>) -> Result<Self> {
        let loaded = load_vrm_with_extensions(path)?;
        Ok(Self::from_loaded(loaded))
    }

    pub fn from_loaded(loaded: LoadedVrmModel) -> Self {
        let LoadedVrmModel { model, .. } = loaded;
        let assets = RuntimeAssets {
            name: model.name.clone(),
            vertex_count: model.vertex_count(),
            indices: model.indices.clone(),
            materials: model.materials.clone(),
            submeshes: model.submeshes.clone(),
            texture_paths: model.texture_paths.clone(),
        };

        Self { model, assets }
    }

    pub fn assets(&self) -> &RuntimeAssets {
        &self.assets
    }

    pub fn head_rest_y(&mut self) -> f32 {
        self.model.get_head_bone_rest_position_y()
    }

    pub fn output(&self) -> &VrmRuntimeOutput {
        self.model
            .vrm_runtime_output()
            .expect("VRM runtime output should exist for loaded VRM models")
    }

    pub fn right_hand_matrix(&self) -> glam::Mat4 {
        self.model.get_right_hand_matrix()
    }

    pub fn left_hand_matrix(&self) -> glam::Mat4 {
        self.model.get_left_hand_matrix()
    }

    pub fn render_state(&self, view: VrmView) -> VrmRenderState<'_> {
        let output = self.output();
        let visible_materials = match view {
            VrmView::Hmd => &output.hmd_visible_materials,
            VrmView::Mirror => &output.mirror_visible_materials,
        };
        VrmRenderState {
            positions: &self.model.update_positions_raw,
            normals: &self.model.update_normals_raw,
            uvs: &self.model.update_uvs_raw,
            visible_materials,
        }
    }

    pub fn process(&mut self, mut input: VrmRuntimeInput) {
        input.delta_time = input.delta_time.clamp(0.0, 0.1);
        self.model.set_vrm_runtime_input(input.clone());
        self.model.tick_animation(input.delta_time);
    }
}
