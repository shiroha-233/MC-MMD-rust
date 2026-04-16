use std::collections::HashMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use glam::{Mat4, Quat, Vec3};
use mmd_engine::model::{MmdMaterial, SubMesh};
use mmd_engine::vrm_runtime::{
    BodyTrackingCalibration, HandGripOffset, HandTrackingCalibration, LookAtInput,
    TrackedPose as RuntimePose, VrmRenderState, VrmRuntime, VrmRuntimeInput, VrmTrackingInput,
    VrmView,
};

use crate::xr::TrackedPose;

pub const MODEL_TO_WORLD_SCALE: f32 = 1.0 / 12.5;

const MIRROR_CAMERA_FOV_Y: f32 = 36.0_f32.to_radians();
const MIRROR_CAMERA_NEAR: f32 = 0.05;
const MIRROR_CAMERA_FAR: f32 = 50.0;
const MODEL_FORWARD_CORRECTION: f32 = std::f32::consts::PI;
// OpenXR grip pose is already close to the palm center on most controllers,
// so keep only a small hardware bias here and let the model bind pose provide
// the palm-to-wrist offset.
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
    tracking_origin_world: Vec3,
    avatar_root_world_yaw: Quat,
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
        compose_tracking_root_matrix(
            self.calibration.tracking_origin_world,
            self.calibration.avatar_root_world_yaw,
        )
    }

    pub fn mirror_model_matrix(&self) -> Mat4 {
        compose_tracking_root_matrix(
            self.calibration.mirror_root_world,
            self.calibration.avatar_root_world_yaw,
        )
    }

    pub fn update(&mut self, tracking: &TrackingFrame, delta_time: f32) {
        self.last_raw_tracking = *tracking;

        if tracking.head.valid && !self.calibration.calibrated {
            self.recenter_from_head(tracking.head);
        }
        if tracking.head.valid && self.calibration.calibrated {
            self.calibration.tracking_origin_world = resolve_tracking_origin_world(
                tracking.head.position,
                self.calibration.avatar_root_world_yaw,
                self.current_view_anchor_model,
            );
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
        let hand_calibration = demo_hand_calibration();
        self.runtime.process(VrmRuntimeInput {
            tracking: Some(runtime_tracking),
            hand_calibration,
            body_calibration: BodyTrackingCalibration::default(),
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
        self.calibration.avatar_root_world_yaw = self.calibration.world_to_avatar_yaw.inverse();
        self.calibration.tracking_origin_world = resolve_tracking_origin_world(
            head.position,
            self.calibration.avatar_root_world_yaw,
            self.current_view_anchor_model,
        );
        self.calibration.mirror_root_world =
            Vec3::new(mirror_head_world.x, 0.0, mirror_head_world.z);
        self.calibration.calibrated = true;
        log::info!(
            "VR 角色已重定中心 head=({:.2},{:.2},{:.2}) tracking_root=({:.2},{:.2},{:.2}) mirror_root=({:.2},{:.2},{:.2})",
            head.position.x,
            head.position.y,
            head.position.z,
            self.calibration.tracking_origin_world.x,
            self.calibration.tracking_origin_world.y,
            self.calibration.tracking_origin_world.z,
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
                * (pose.position - self.calibration.tracking_origin_world),
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
                * (pose.position - self.calibration.tracking_origin_world),
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
        let output = self.runtime.output();
        let head_local = output.head_local_model * MODEL_TO_WORLD_SCALE;
        let body_anchor = output.body_anchor_model * MODEL_TO_WORLD_SCALE;
        let left = output.left_palm_target_model * MODEL_TO_WORLD_SCALE;
        let right = output.right_palm_target_model * MODEL_TO_WORLD_SCALE;
        let raw_left = self.last_raw_tracking.left_hand.position;
        let raw_right = self.last_raw_tracking.right_hand.position;
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

fn compose_tracking_root_matrix(origin_world: Vec3, avatar_root_world_yaw: Quat) -> Mat4 {
    let root_rotation =
        (avatar_root_world_yaw * Quat::from_rotation_y(MODEL_FORWARD_CORRECTION)).normalize();
    Mat4::from_translation(origin_world)
        * Mat4::from_quat(root_rotation)
        * Mat4::from_scale(Vec3::splat(MODEL_TO_WORLD_SCALE))
}

fn resolve_tracking_origin_world(
    head_world: Vec3,
    avatar_root_world_yaw: Quat,
    view_anchor_model: Vec3,
) -> Vec3 {
    let root_rotation =
        (avatar_root_world_yaw * Quat::from_rotation_y(MODEL_FORWARD_CORRECTION)).normalize();
    head_world - root_rotation * (view_anchor_model * MODEL_TO_WORLD_SCALE)
}

fn demo_hand_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: LEFT_GRIP_TO_PALM_XR,
        },
        right: HandGripOffset {
            position_offset: RIGHT_GRIP_TO_PALM_XR,
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compose_tracking_root_matrix_should_keep_recenter_origin_stable() {
        let origin = Vec3::new(1.5, 0.0, -2.0);
        let yaw = Quat::from_rotation_y(0.75);
        let matrix = compose_tracking_root_matrix(origin, yaw);

        assert_vec3_near(matrix.w_axis.truncate(), origin, 1e-5);
    }

    #[test]
    fn inverse_yaw_from_head_should_leave_identity_unchanged() {
        let yaw = inverse_yaw_from_head(Quat::IDENTITY);
        let forward = yaw * Vec3::NEG_Z;

        assert_vec3_near(forward, Vec3::NEG_Z, 1e-5);
    }

    #[test]
    fn resolve_tracking_origin_world_should_place_view_anchor_at_head_position() {
        let head_world = Vec3::new(2.0, 1.6, -0.5);
        let yaw = Quat::from_rotation_y(0.35);
        let view_anchor_model = Vec3::new(0.0, 18.0, 1.5);
        let origin = resolve_tracking_origin_world(head_world, yaw, view_anchor_model);
        let matrix = compose_tracking_root_matrix(origin, yaw);
        let solved_head_world = matrix.transform_point3(view_anchor_model);

        assert_vec3_near(solved_head_world, head_world, 1e-5);
    }

    fn assert_vec3_near(actual: Vec3, expected: Vec3, tolerance: f32) {
        let delta = actual - expected;
        assert!(
            delta.length() <= tolerance,
            "vec mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }
}
