use std::collections::HashMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use glam::{Mat4, Quat, Vec3};
use mmd_engine::model::{MmdMaterial, SubMesh};
use mmd_engine::vrm_runtime::{
    LookAtInput, TrackedPose as RuntimePose, VrmRenderState, VrmRuntime, VrmRuntimeInput,
    VrmTrackingInput, VrmView,
};

use crate::xr::TrackedPose;

pub const MODEL_TO_WORLD_SCALE: f32 = 1.0 / 12.5;

const MIRROR_CAMERA_FOV_Y: f32 = 36.0_f32.to_radians();
const MIRROR_CAMERA_NEAR: f32 = 0.05;
const MIRROR_CAMERA_FAR: f32 = 50.0;
const MODEL_FORWARD_CORRECTION: f32 = std::f32::consts::PI;

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
pub struct TrackingFrame {
    pub head: TrackedPose,
    pub left_hand: TrackedPose,
    pub right_hand: TrackedPose,
}

#[derive(Clone, Copy, Debug)]
pub struct MirrorCamera {
    pub view_proj: Mat4,
}

#[derive(Clone, Copy, Debug, Default)]
struct Calibration {
    root_origin_xr: Vec3,
    world_to_avatar_yaw: Quat,
    mirror_root_world: Vec3,
    calibrated: bool,
}

pub struct AvatarScene {
    model_path: PathBuf,
    assets: SceneAssets,
    runtime: VrmRuntime,
    calibration: Calibration,
    current_view_anchor_model: Vec3,
    last_raw_tracking: TrackingFrame,
    last_tracking: VrmTrackingInput,
}

impl AvatarScene {
    pub fn new(path: impl AsRef<Path>) -> Result<Self> {
        let model_path = path.as_ref().to_path_buf();
        let runtime = VrmRuntime::load(&model_path)
            .with_context(|| format!("加载 VRM 失败: {}", model_path.display()))?;
        let assets = SceneAssets {
            name: runtime.assets().name.clone(),
            vertex_count: runtime.assets().vertex_count,
            indices: runtime.assets().indices.clone(),
            materials: runtime.assets().materials.clone(),
            submeshes: runtime.assets().submeshes.clone(),
            texture_paths: runtime.assets().texture_paths.clone(),
        };
        let current_view_anchor_model = runtime.output().view_anchor_model;

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
            calibration: Calibration::default(),
            current_view_anchor_model,
            last_raw_tracking: TrackingFrame::default(),
            last_tracking: VrmTrackingInput::default(),
        })
    }

    pub fn assets(&self) -> &SceneAssets {
        &self.assets
    }

    pub fn xr_model_matrix(&self) -> Mat4 {
        let head_pose = self.last_raw_tracking.head;
        if !head_pose.valid {
            return self.mirror_model_matrix();
        }

        let head_model_rotation = (self.runtime.output().head_rotation
            * Quat::from_rotation_y(MODEL_FORWARD_CORRECTION))
        .normalize();
        let root_rotation = (head_pose.orientation * head_model_rotation.inverse()).normalize();
        let view_anchor_world =
            root_rotation * (self.current_view_anchor_model * MODEL_TO_WORLD_SCALE);
        let root_translation = head_pose.position - view_anchor_world;

        Mat4::from_translation(root_translation)
            * Mat4::from_quat(root_rotation)
            * Mat4::from_scale(Vec3::splat(MODEL_TO_WORLD_SCALE))
    }

    pub fn mirror_model_matrix(&self) -> Mat4 {
        Mat4::from_translation(self.calibration.mirror_root_world)
            * Mat4::from_quat(Quat::from_rotation_y(MODEL_FORWARD_CORRECTION))
            * Mat4::from_scale(Vec3::splat(MODEL_TO_WORLD_SCALE))
    }

    pub fn update(&mut self, tracking: &TrackingFrame, delta_time: f32) {
        self.last_raw_tracking = *tracking;

        if tracking.head.valid && !self.calibration.calibrated {
            self.recenter_from_head(tracking.head);
        }

        let head = self.resolve_head_pose(self.anchor_head_pose(tracking.head));
        let right = self.resolve_hand_pose(
            self.anchor_hand_pose(tracking.right_hand, tracking.head),
            head,
            1.0,
        );
        let left = self.resolve_hand_pose(
            self.anchor_hand_pose(tracking.left_hand, tracking.head),
            head,
            -1.0,
        );

        let runtime_tracking = VrmTrackingInput {
            head,
            left_hand: left,
            right_hand: right,
        };
        self.runtime.process(VrmRuntimeInput {
            tracking: Some(runtime_tracking),
            look_at: LookAtInput::default(),
            expression_weights: HashMap::new(),
            first_person: true,
            delta_time: delta_time.clamp(0.0, 0.1),
        });

        self.current_view_anchor_model = self.runtime.output().view_anchor_model;
        self.last_tracking = runtime_tracking;
    }

    pub fn recenter(&mut self) {
        if self.last_raw_tracking.head.valid {
            self.recenter_from_head(self.last_raw_tracking.head);
        }
    }

    fn recenter_from_head(&mut self, head: TrackedPose) {
        let viewer_forward = planar_forward(head.orientation);
        let mirror_head_world = head.position + viewer_forward * 1.35;

        self.calibration.root_origin_xr = Vec3::new(head.position.x, 0.0, head.position.z);
        self.calibration.world_to_avatar_yaw = inverse_yaw_from_head(head.orientation);
        self.calibration.mirror_root_world =
            Vec3::new(mirror_head_world.x, 0.0, mirror_head_world.z);
        self.calibration.calibrated = true;
        log::info!(
            "VR 角色已重定中心 head=({:.2},{:.2},{:.2}) mirror_root=({:.2},{:.2},{:.2})",
            head.position.x,
            head.position.y,
            head.position.z,
            self.calibration.mirror_root_world.x,
            self.calibration.mirror_root_world.y,
            self.calibration.mirror_root_world.z,
        );
    }

    fn anchor_head_pose(&self, pose: TrackedPose) -> RuntimePose {
        if !pose.valid || !self.calibration.calibrated {
            return runtime_pose_from_xr(pose);
        }

        RuntimePose {
            position: self.calibration.world_to_avatar_yaw
                * (pose.position - self.calibration.root_origin_xr),
            orientation: (self.calibration.world_to_avatar_yaw * pose.orientation).normalize(),
            valid: true,
        }
    }

    fn anchor_hand_pose(&self, pose: TrackedPose, head: TrackedPose) -> RuntimePose {
        if !pose.valid || !self.calibration.calibrated || !head.valid {
            return runtime_pose_from_xr(pose);
        }

        RuntimePose {
            position: self.calibration.world_to_avatar_yaw
                * (pose.position - self.calibration.root_origin_xr),
            orientation: (self.calibration.world_to_avatar_yaw * pose.orientation).normalize(),
            valid: true,
        }
    }

    fn resolve_head_pose(&self, head: RuntimePose) -> RuntimePose {
        if head.valid {
            head
        } else if self.last_tracking.head.valid {
            self.last_tracking.head
        } else {
            RuntimePose::identity()
        }
    }

    fn resolve_hand_pose(&self, hand: RuntimePose, head: RuntimePose, side: f32) -> RuntimePose {
        if hand.valid {
            hand
        } else {
            let fallback_offset = Vec3::new(0.23 * side, -0.28, -0.34);
            RuntimePose {
                position: head.position + head.orientation * fallback_offset,
                orientation: head.orientation,
                valid: false,
            }
        }
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

    pub fn mirror_camera(&self, aspect: f32) -> MirrorCamera {
        let view_height = (self.current_view_anchor_model.y * MODEL_TO_WORLD_SCALE).max(1.35);
        let target =
            self.calibration.mirror_root_world + Vec3::new(0.0, (view_height - 0.32).max(0.9), 0.0);
        let eye = target + Vec3::new(0.35, 0.18, 2.65);
        let view = Mat4::look_at_rh(eye, target, Vec3::Y);
        let projection = Mat4::perspective_rh(
            MIRROR_CAMERA_FOV_Y,
            aspect.max(0.1),
            MIRROR_CAMERA_NEAR,
            MIRROR_CAMERA_FAR,
        );
        MirrorCamera {
            view_proj: projection * view,
        }
    }

    pub fn status_line(&self, runtime_label: &str) -> String {
        let head = self.last_tracking.head.position;
        let left = self.last_tracking.left_hand.position;
        let right = self.last_tracking.right_hand.position;
        let raw_left = self.last_raw_tracking.left_hand.position;
        let raw_right = self.last_raw_tracking.right_hand.position;
        let left_wrist = self.runtime.left_hand_matrix().w_axis.truncate() * MODEL_TO_WORLD_SCALE;
        let right_wrist = self.runtime.right_hand_matrix().w_axis.truncate() * MODEL_TO_WORLD_SCALE;
        format!(
            "mode={} runtime={} head=({:.1},{:.1},{:.1}) lh=({:.1},{:.1},{:.1}) rh=({:.1},{:.1},{:.1}) raw_l=({:.1},{:.1},{:.1}) raw_r=({:.1},{:.1},{:.1}) wrist_l=({:.2},{:.2},{:.2}) wrist_r=({:.2},{:.2},{:.2})",
            self.assets.name,
            runtime_label,
            head.x,
            head.y,
            head.z,
            left.x,
            left.y,
            left.z,
            right.x,
            right.y,
            right.z,
            raw_left.x,
            raw_left.y,
            raw_left.z,
            raw_right.x,
            raw_right.y,
            raw_right.z,
            left_wrist.x,
            left_wrist.y,
            left_wrist.z,
            right_wrist.x,
            right_wrist.y,
            right_wrist.z,
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

fn runtime_pose_from_xr(pose: TrackedPose) -> RuntimePose {
    RuntimePose {
        position: pose.position,
        orientation: pose.orientation,
        valid: pose.valid,
    }
}

fn inverse_yaw_from_head(orientation: Quat) -> Quat {
    let forward = planar_forward(orientation);
    let yaw = forward.x.atan2(-forward.z);
    Quat::from_rotation_y(-yaw)
}

fn planar_forward(orientation: Quat) -> Vec3 {
    let forward = orientation * Vec3::NEG_Z;
    let planar = Vec3::new(forward.x, 0.0, forward.z);
    if planar.length_squared() <= 1e-6 {
        Vec3::NEG_Z
    } else {
        planar.normalize()
    }
}
