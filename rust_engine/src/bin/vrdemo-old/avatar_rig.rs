use std::collections::HashMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use glam::{Quat, Vec3};
use mmd_engine::model::{MmdMaterial, SubMesh};
use mmd_engine::vrm_runtime::{
    ArmIkCalibration, ArmIkHandCalibration, BodyTrackingCalibration, HandGripOffset,
    HandTrackingCalibration, LookAtInput, VrmRenderState, VrmRuntime, VrmRuntimeInput,
    VrmTrackingInput, VrmView,
};

pub const MODEL_TO_WORLD_SCALE: f32 = 1.0 / 12.5;

const LEFT_GRIP_TO_PALM_XR: Vec3 = Vec3::new(0.0, -0.012, -0.018);
const RIGHT_GRIP_TO_PALM_XR: Vec3 = Vec3::new(0.0, -0.012, -0.018);

#[derive(Clone)]
pub struct SceneAssets {
    pub name: String,
    pub vertex_count: usize,
    pub indices: Vec<u32>,
    pub materials: Vec<MmdMaterial>,
    pub submeshes: Vec<SubMesh>,
    pub texture_paths: Vec<String>,
}

pub struct ModelRenderData<'a> {
    pub positions: &'a [f32],
    pub normals: &'a [f32],
    pub uvs: &'a [f32],
    pub visible_materials: Vec<bool>,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct ArmIkDebugSnapshot {
    pub left_auto_wrist_offset_model: Vec3,
    pub right_auto_wrist_offset_model: Vec3,
    pub left_wrist_error_cm: f32,
    pub right_wrist_error_cm: f32,
}

pub struct AvatarRig {
    model_path: PathBuf,
    assets: SceneAssets,
    runtime: VrmRuntime,
    current_view_anchor_model: Vec3,
    last_tracking: VrmTrackingInput,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
}

impl AvatarRig {
    pub fn new(path: impl AsRef<Path>) -> Result<Self> {
        let model_path = path.as_ref().to_path_buf();
        let mut runtime = VrmRuntime::load(&model_path)
            .with_context(|| format!("加载 VRM 失败: {}", model_path.display()))?;
        let assets = SceneAssets {
            name: runtime.assets().name.clone(),
            vertex_count: runtime.assets().vertex_count,
            indices: runtime.assets().indices.clone(),
            materials: runtime.assets().materials.clone(),
            submeshes: runtime.assets().submeshes.clone(),
            texture_paths: runtime.assets().texture_paths.clone(),
        };
        let current_view_anchor_model = {
            let anchor = runtime.output().view_anchor_model;
            if anchor.length_squared() > 1e-6 {
                anchor
            } else {
                Vec3::new(0.0, runtime.head_rest_y(), 0.0)
            }
        };

        log::info!(
            "已加载 {} (顶点 {} / 材质 {})",
            assets.name,
            assets.vertex_count,
            assets.materials.len()
        );

        Ok(Self {
            model_path,
            assets,
            runtime,
            current_view_anchor_model,
            last_tracking: VrmTrackingInput::default(),
            hand_calibration: demo_hand_calibration(),
            arm_ik_calibration: demo_arm_ik_calibration(),
        })
    }

    pub fn assets(&self) -> &SceneAssets {
        &self.assets
    }

    pub fn current_view_anchor_model(&self) -> Vec3 {
        self.current_view_anchor_model
    }

    pub fn last_tracking(&self) -> VrmTrackingInput {
        self.last_tracking
    }

    pub fn hand_calibration(&self) -> HandTrackingCalibration {
        self.hand_calibration
    }

    pub fn set_hand_calibration(&mut self, calibration: HandTrackingCalibration) {
        self.hand_calibration = calibration;
    }

    pub fn arm_ik_calibration(&self) -> ArmIkCalibration {
        self.arm_ik_calibration
    }

    pub fn set_arm_ik_calibration(&mut self, calibration: ArmIkCalibration) {
        self.arm_ik_calibration = calibration;
    }

    pub fn arm_ik_debug_snapshot(&self) -> ArmIkDebugSnapshot {
        let output = self.runtime.output();
        ArmIkDebugSnapshot {
            left_auto_wrist_offset_model: output.left_auto_wrist_offset_model,
            right_auto_wrist_offset_model: output.right_auto_wrist_offset_model,
            left_wrist_error_cm: output.left_wrist_error_cm,
            right_wrist_error_cm: output.right_wrist_error_cm,
        }
    }

    pub fn process(&mut self, runtime_tracking: VrmTrackingInput, delta_time: f32) {
        self.runtime.process(VrmRuntimeInput {
            tracking: Some(runtime_tracking),
            hand_calibration: self.hand_calibration,
            arm_ik_calibration: self.arm_ik_calibration,
            body_calibration: demo_body_calibration(),
            look_at: LookAtInput::default(),
            expression_weights: HashMap::new(),
            first_person: true,
            delta_time: delta_time.clamp(0.0, 0.1),
        });

        let anchor = self.runtime.output().view_anchor_model;
        if anchor.length_squared() > 1e-6 {
            self.current_view_anchor_model = anchor;
        }
        self.last_tracking = runtime_tracking;
    }

    pub fn hmd_render_data(&self) -> ModelRenderData<'_> {
        Self::render_data(self.runtime.render_state(VrmView::Hmd))
    }

    pub fn mirror_render_data(&self) -> ModelRenderData<'_> {
        Self::render_data(self.runtime.render_state(VrmView::Mirror))
    }

    fn render_data(state: VrmRenderState<'_>) -> ModelRenderData<'_> {
        ModelRenderData {
            positions: state.positions,
            normals: state.normals,
            uvs: state.uvs,
            visible_materials: state.visible_materials.to_vec(),
        }
    }

    pub fn status_line(
        &self,
        runtime_label: &str,
        last_raw_left: Vec3,
        last_raw_right: Vec3,
    ) -> String {
        let output = self.runtime.output();
        let head_local = output.head_local_model * MODEL_TO_WORLD_SCALE;
        let body_anchor = output.body_anchor_model * MODEL_TO_WORLD_SCALE;
        let left = output.left_palm_target_model * MODEL_TO_WORLD_SCALE;
        let right = output.right_palm_target_model * MODEL_TO_WORLD_SCALE;
        let left_wrist = output.left_wrist_solved_model * MODEL_TO_WORLD_SCALE;
        let right_wrist = output.right_wrist_solved_model * MODEL_TO_WORLD_SCALE;
        format!(
            "mode={} runtime={} head_local=({:.2},{:.2},{:.2}) body_anchor=({:.2},{:.2},{:.2}) palm_l=({:.2},{:.2},{:.2}) palm_r=({:.2},{:.2},{:.2}) raw_l=({:.2},{:.2},{:.2}) raw_r=({:.2},{:.2},{:.2}) wrist_l=({:.2},{:.2},{:.2}) wrist_r=({:.2},{:.2},{:.2}) wrist_err_cm=({:.1},{:.1})",
            self.assets.name,
            runtime_label,
            head_local.x,
            head_local.y,
            head_local.z,
            body_anchor.x,
            body_anchor.y,
            body_anchor.z,
            left.x,
            left.y,
            left.z,
            right.x,
            right.y,
            right.z,
            last_raw_left.x,
            last_raw_left.y,
            last_raw_left.z,
            last_raw_right.x,
            last_raw_right.y,
            last_raw_right.z,
            left_wrist.x,
            left_wrist.y,
            left_wrist.z,
            right_wrist.x,
            right_wrist.y,
            right_wrist.z,
            output.left_wrist_error_cm,
            output.right_wrist_error_cm,
        )
    }

    pub fn summary_text(&self, runtime_label: &str) -> String {
        format!(
            "已加载 {} (顶点 {} / 材质 {}) | {} | {}",
            self.assets.name,
            self.assets.vertex_count,
            self.assets.materials.len(),
            runtime_label,
            self.model_path.display(),
        )
    }
}

fn demo_hand_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: LEFT_GRIP_TO_PALM_XR,
            orientation_offset: Quat::IDENTITY,
        },
        right: HandGripOffset {
            position_offset: RIGHT_GRIP_TO_PALM_XR,
            orientation_offset: Quat::IDENTITY,
        },
    }
}

fn demo_body_calibration() -> BodyTrackingCalibration {
    BodyTrackingCalibration {
        horizontal_translation_follow_gain: 1.0,
        vertical_translation_follow_gain: 1.0,
        body_translation_clamp_model: -1.0,
        ..BodyTrackingCalibration::default()
    }
}

fn demo_arm_ik_calibration() -> ArmIkCalibration {
    ArmIkCalibration {
        left: ArmIkHandCalibration::default(),
        right: ArmIkHandCalibration::default(),
        forearm_twist_ratio: 0.4,
    }
}
