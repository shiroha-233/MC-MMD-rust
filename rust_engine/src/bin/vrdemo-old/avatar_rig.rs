use std::collections::HashMap;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use glam::{EulerRot, Quat, Vec3};
use mmd_engine::model::{load_pmx, MmdMaterial, MmdModel, SubMesh};
use mmd_engine::vrm_runtime::{
    ArmIkCalibration, ArmIkHandCalibration, BodyTrackingCalibration, HandGripOffset,
    HandTrackingCalibration, LookAtInput, VrmRenderState, VrmRuntime, VrmRuntimeInput,
    VrmRuntimeOutput, VrmTrackingInput, VrmView,
};

pub const MODEL_TO_WORLD_SCALE: f32 = 1.0 / 12.5;

const LEFT_GRIP_TO_PALM_XR: Vec3 = Vec3::new(0.0, -0.012, -0.018);
const RIGHT_GRIP_TO_PALM_XR: Vec3 = Vec3::new(0.0, -0.012, -0.018);
pub(crate) const VRM_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR: [f32; 3] =
    [0.0, 0.0, 0.0];
pub(crate) const VRM_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR: [f32; 3] =
    [0.0, 0.0, 0.0];
pub(crate) const PMX_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR: [f32; 3] =
    [90.0, 0.0, 90.0];
pub(crate) const PMX_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR: [f32; 3] =
    [90.0, 0.0, -90.0];
const HMD_VIEW_ANCHOR_RIGHT_OFFSET_METERS: f32 = 0.0;
const HMD_VIEW_ANCHOR_UP_OFFSET_METERS: f32 = 0.015;
const HMD_VIEW_ANCHOR_FORWARD_OFFSET_METERS: f32 = 0.045;
const MIN_VALID_VIEW_ANCHOR_LENGTH_SQUARED: f32 = 1e-6;

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

#[derive(Clone, Copy, Debug, Default)]
struct RuntimeTelemetry {
    view_anchor_model: Vec3,
    head_rotation: Quat,
    head_local_model: Vec3,
    body_anchor_model: Vec3,
    left_palm_target_model: Vec3,
    right_palm_target_model: Vec3,
    left_auto_wrist_offset_model: Vec3,
    right_auto_wrist_offset_model: Vec3,
    left_wrist_solved_model: Vec3,
    right_wrist_solved_model: Vec3,
    left_wrist_error_cm: f32,
    right_wrist_error_cm: f32,
}

impl RuntimeTelemetry {
    fn from_vrm_output(output: &VrmRuntimeOutput) -> Self {
        Self {
            view_anchor_model: output.view_anchor_model,
            head_rotation: output.head_rotation,
            head_local_model: output.head_local_model,
            body_anchor_model: output.body_anchor_model,
            left_palm_target_model: output.left_palm_target_model,
            right_palm_target_model: output.right_palm_target_model,
            left_auto_wrist_offset_model: output.left_auto_wrist_offset_model,
            right_auto_wrist_offset_model: output.right_auto_wrist_offset_model,
            left_wrist_solved_model: output.left_wrist_solved_model,
            right_wrist_solved_model: output.right_wrist_solved_model,
            left_wrist_error_cm: output.left_wrist_error_cm,
            right_wrist_error_cm: output.right_wrist_error_cm,
        }
    }

    fn from_pmx_model(model: &mut MmdModel) -> Self {
        let debug = model.vr_debug_snapshot();
        Self {
            view_anchor_model: resolve_model_view_anchor(model),
            head_rotation: model.current_head_rotation(),
            head_local_model: debug.head_local_model,
            body_anchor_model: debug.body_anchor_model,
            left_palm_target_model: debug.left_palm_target_model,
            right_palm_target_model: debug.right_palm_target_model,
            left_auto_wrist_offset_model: debug.left_auto_wrist_offset_model,
            right_auto_wrist_offset_model: debug.right_auto_wrist_offset_model,
            left_wrist_solved_model: debug.left_wrist_solved_model,
            right_wrist_solved_model: debug.right_wrist_solved_model,
            left_wrist_error_cm: debug.left_wrist_error_cm,
            right_wrist_error_cm: debug.right_wrist_error_cm,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum DemoModelFormat {
    Vrm,
    Pmx,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DemoHandTrackingMode {
    VrmLegacyGripAim,
    PmxControllerPose,
}

impl DemoModelFormat {
    fn from_path(path: &Path) -> Result<Self> {
        let Some(extension) = path.extension().and_then(|extension| extension.to_str()) else {
            return Err(anyhow!(
                "unsupported avatar file without extension: {}",
                path.display()
            ));
        };

        match extension.to_ascii_lowercase().as_str() {
            "vrm" => Ok(Self::Vrm),
            "pmx" => Ok(Self::Pmx),
            _ => Err(anyhow!(
                "unsupported avatar format `{}` for {}",
                extension,
                path.display()
            )),
        }
    }
}

enum DemoModelRuntime {
    Vrm(VrmRuntime),
    Pmx(PmxRuntime),
}

impl DemoModelRuntime {
    fn load(path: &Path) -> Result<(Self, SceneAssets, RuntimeTelemetry)> {
        match DemoModelFormat::from_path(path)? {
            DemoModelFormat::Vrm => {
                let mut runtime = VrmRuntime::load(path)
                    .with_context(|| format!("加载 VRM 失败: {}", path.display()))?;
                let mut telemetry = RuntimeTelemetry::from_vrm_output(runtime.output());
                if telemetry.view_anchor_model.length_squared()
                    <= MIN_VALID_VIEW_ANCHOR_LENGTH_SQUARED
                {
                    telemetry.view_anchor_model = Vec3::new(0.0, runtime.head_rest_y(), 0.0);
                }
                let assets = scene_assets_from_vrm(&runtime);
                Ok((Self::Vrm(runtime), assets, telemetry))
            }
            DemoModelFormat::Pmx => {
                let mut runtime = PmxRuntime::load(path)?;
                let telemetry = RuntimeTelemetry::from_pmx_model(&mut runtime.model);
                let assets = scene_assets_from_model(&runtime.model);
                Ok((Self::Pmx(runtime), assets, telemetry))
            }
        }
    }

    fn process(
        &mut self,
        runtime_tracking: VrmTrackingInput,
        hand_calibration: HandTrackingCalibration,
        arm_ik_calibration: ArmIkCalibration,
        delta_time: f32,
    ) -> RuntimeTelemetry {
        let delta_time = delta_time.clamp(0.0, 0.1);
        match self {
            Self::Vrm(runtime) => {
                runtime.process(VrmRuntimeInput {
                    tracking: Some(runtime_tracking),
                    hand_calibration,
                    arm_ik_calibration,
                    body_calibration: demo_body_calibration(),
                    look_at: LookAtInput::default(),
                    expression_weights: HashMap::new(),
                    first_person: true,
                    delta_time,
                });
                RuntimeTelemetry::from_vrm_output(runtime.output())
            }
            Self::Pmx(runtime) => runtime.process(
                runtime_tracking,
                hand_calibration,
                arm_ik_calibration,
                delta_time,
            ),
        }
    }

    fn hmd_render_data(&self) -> ModelRenderData<'_> {
        match self {
            Self::Vrm(runtime) => render_data(runtime.render_state(VrmView::Hmd)),
            Self::Pmx(runtime) => runtime.hmd_render_data(),
        }
    }

    fn mirror_render_data(&self) -> ModelRenderData<'_> {
        match self {
            Self::Vrm(runtime) => render_data(runtime.render_state(VrmView::Mirror)),
            Self::Pmx(runtime) => runtime.mirror_render_data(),
        }
    }

    fn hand_tracking_mode(&self) -> DemoHandTrackingMode {
        match self {
            Self::Vrm(_) => DemoHandTrackingMode::VrmLegacyGripAim,
            Self::Pmx(_) => DemoHandTrackingMode::PmxControllerPose,
        }
    }

    fn default_hand_calibration(&self) -> HandTrackingCalibration {
        match self {
            Self::Vrm(_) => vrm_demo_hand_calibration(),
            Self::Pmx(_) => pmx_demo_hand_calibration(),
        }
    }
}

struct PmxRuntime {
    model: MmdModel,
    hmd_visible_materials: Vec<bool>,
    mirror_visible_materials: Vec<bool>,
}

impl PmxRuntime {
    fn load(path: &Path) -> Result<Self> {
        let mut model =
            load_pmx(path).with_context(|| format!("加载 PMX 失败: {}", path.display()))?;
        model.initialize_animation();
        model.tick_animation(0.0);

        let mirror_visible_materials = collect_visible_materials(&model);
        model.set_first_person_mode(true);
        let hmd_visible_materials = collect_visible_materials(&model);

        Ok(Self {
            model,
            hmd_visible_materials,
            mirror_visible_materials,
        })
    }

    fn process(
        &mut self,
        runtime_tracking: VrmTrackingInput,
        hand_calibration: HandTrackingCalibration,
        arm_ik_calibration: ArmIkCalibration,
        delta_time: f32,
    ) -> RuntimeTelemetry {
        self.model.apply_vr_tracking_input(
            Some(runtime_tracking),
            hand_calibration,
            arm_ik_calibration,
            demo_body_calibration(),
        );
        self.model.tick_animation(delta_time);
        RuntimeTelemetry::from_pmx_model(&mut self.model)
    }

    fn hmd_render_data(&self) -> ModelRenderData<'_> {
        self.render_data(&self.hmd_visible_materials)
    }

    fn mirror_render_data(&self) -> ModelRenderData<'_> {
        self.render_data(&self.mirror_visible_materials)
    }

    fn render_data(&self, visible_materials: &[bool]) -> ModelRenderData<'_> {
        ModelRenderData {
            positions: &self.model.update_positions_raw,
            normals: &self.model.update_normals_raw,
            uvs: &self.model.update_uvs_raw,
            visible_materials: visible_materials.to_vec(),
        }
    }
}

pub struct AvatarRig {
    model_path: PathBuf,
    assets: SceneAssets,
    runtime: DemoModelRuntime,
    telemetry: RuntimeTelemetry,
    current_raw_view_anchor_model: Vec3,
    current_view_anchor_model: Vec3,
    last_tracking: VrmTrackingInput,
    hand_calibration: HandTrackingCalibration,
    arm_ik_calibration: ArmIkCalibration,
}

impl AvatarRig {
    pub fn new(path: impl AsRef<Path>) -> Result<Self> {
        let model_path = path.as_ref().to_path_buf();
        let (runtime, assets, telemetry) = DemoModelRuntime::load(&model_path)?;
        let hand_calibration = runtime.default_hand_calibration();
        let current_raw_view_anchor_model = telemetry.view_anchor_model;
        let current_view_anchor_model =
            adjust_hmd_view_anchor_model(current_raw_view_anchor_model, telemetry.head_rotation);

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
            telemetry,
            current_raw_view_anchor_model,
            current_view_anchor_model,
            last_tracking: VrmTrackingInput::default(),
            hand_calibration,
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

    pub fn hand_tracking_mode(&self) -> DemoHandTrackingMode {
        self.runtime.hand_tracking_mode()
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
        ArmIkDebugSnapshot {
            left_auto_wrist_offset_model: self.telemetry.left_auto_wrist_offset_model,
            right_auto_wrist_offset_model: self.telemetry.right_auto_wrist_offset_model,
            left_wrist_error_cm: self.telemetry.left_wrist_error_cm,
            right_wrist_error_cm: self.telemetry.right_wrist_error_cm,
        }
    }

    pub fn process(&mut self, runtime_tracking: VrmTrackingInput, delta_time: f32) {
        let telemetry = self.runtime.process(
            runtime_tracking,
            self.hand_calibration,
            self.arm_ik_calibration,
            delta_time,
        );
        let (raw_anchor, adjusted_anchor) = resolve_demo_view_anchor(
            self.current_raw_view_anchor_model,
            self.current_view_anchor_model,
            telemetry.view_anchor_model,
            telemetry.head_rotation,
        );
        self.telemetry = telemetry;
        self.current_raw_view_anchor_model = raw_anchor;
        self.current_view_anchor_model = adjusted_anchor;
        self.last_tracking = runtime_tracking;
    }

    pub fn hmd_render_data(&self) -> ModelRenderData<'_> {
        self.runtime.hmd_render_data()
    }

    pub fn mirror_render_data(&self) -> ModelRenderData<'_> {
        self.runtime.mirror_render_data()
    }

    pub fn status_line(
        &self,
        runtime_label: &str,
        last_raw_left: Vec3,
        last_raw_right: Vec3,
    ) -> String {
        let head_local = self.telemetry.head_local_model * MODEL_TO_WORLD_SCALE;
        let body_anchor = self.telemetry.body_anchor_model * MODEL_TO_WORLD_SCALE;
        let left = self.telemetry.left_palm_target_model * MODEL_TO_WORLD_SCALE;
        let right = self.telemetry.right_palm_target_model * MODEL_TO_WORLD_SCALE;
        let left_wrist = self.telemetry.left_wrist_solved_model * MODEL_TO_WORLD_SCALE;
        let right_wrist = self.telemetry.right_wrist_solved_model * MODEL_TO_WORLD_SCALE;
        let raw_anchor = self.current_raw_view_anchor_model * MODEL_TO_WORLD_SCALE;
        let adjusted_anchor = self.current_view_anchor_model * MODEL_TO_WORLD_SCALE;
        format!(
            "mode={} runtime={} head_local=({:.2},{:.2},{:.2}) body_anchor=({:.2},{:.2},{:.2}) anchor_raw=({:.2},{:.2},{:.2}) anchor_adj=({:.2},{:.2},{:.2}) palm_l=({:.2},{:.2},{:.2}) palm_r=({:.2},{:.2},{:.2}) raw_l=({:.2},{:.2},{:.2}) raw_r=({:.2},{:.2},{:.2}) wrist_l=({:.2},{:.2},{:.2}) wrist_r=({:.2},{:.2},{:.2}) wrist_err_cm=({:.1},{:.1})",
            self.assets.name,
            runtime_label,
            head_local.x,
            head_local.y,
            head_local.z,
            body_anchor.x,
            body_anchor.y,
            body_anchor.z,
            raw_anchor.x,
            raw_anchor.y,
            raw_anchor.z,
            adjusted_anchor.x,
            adjusted_anchor.y,
            adjusted_anchor.z,
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
            self.telemetry.left_wrist_error_cm,
            self.telemetry.right_wrist_error_cm,
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

fn vrm_demo_hand_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: LEFT_GRIP_TO_PALM_XR,
            orientation_offset: rotation_degrees_to_quat(
                VRM_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR,
            ),
        },
        right: HandGripOffset {
            position_offset: RIGHT_GRIP_TO_PALM_XR,
            orientation_offset: rotation_degrees_to_quat(
                VRM_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR,
            ),
        },
    }
}

fn pmx_demo_hand_calibration() -> HandTrackingCalibration {
    HandTrackingCalibration {
        left: HandGripOffset {
            position_offset: LEFT_GRIP_TO_PALM_XR,
            orientation_offset: rotation_degrees_to_quat(
                PMX_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR,
            ),
        },
        right: HandGripOffset {
            position_offset: RIGHT_GRIP_TO_PALM_XR,
            orientation_offset: rotation_degrees_to_quat(
                PMX_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR,
            ),
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

fn scene_assets_from_vrm(runtime: &VrmRuntime) -> SceneAssets {
    SceneAssets {
        name: runtime.assets().name.clone(),
        vertex_count: runtime.assets().vertex_count,
        indices: runtime.assets().indices.clone(),
        materials: runtime.assets().materials.clone(),
        submeshes: runtime.assets().submeshes.clone(),
        texture_paths: runtime.assets().texture_paths.clone(),
    }
}

fn scene_assets_from_model(model: &MmdModel) -> SceneAssets {
    SceneAssets {
        name: model.name.clone(),
        vertex_count: model.vertex_count(),
        indices: model.indices.clone(),
        materials: model.materials.clone(),
        submeshes: model.submeshes.clone(),
        texture_paths: model.texture_paths.clone(),
    }
}

fn collect_visible_materials(model: &MmdModel) -> Vec<bool> {
    (0..model.material_count())
        .map(|index| model.is_material_visible(index))
        .collect()
}

fn resolve_model_view_anchor(model: &mut MmdModel) -> Vec3 {
    let eye_anchor = model.get_eye_bone_animated_position();
    if eye_anchor.length_squared() > MIN_VALID_VIEW_ANCHOR_LENGTH_SQUARED {
        eye_anchor
    } else {
        Vec3::new(0.0, model.get_head_bone_rest_position_y(), 0.0)
    }
}

fn render_data(state: VrmRenderState<'_>) -> ModelRenderData<'_> {
    ModelRenderData {
        positions: state.positions,
        normals: state.normals,
        uvs: state.uvs,
        visible_materials: state.visible_materials.to_vec(),
    }
}

fn resolve_demo_view_anchor(
    previous_raw_view_anchor_model: Vec3,
    previous_adjusted_view_anchor_model: Vec3,
    runtime_view_anchor_model: Vec3,
    head_rotation: Quat,
) -> (Vec3, Vec3) {
    if runtime_view_anchor_model.length_squared() <= MIN_VALID_VIEW_ANCHOR_LENGTH_SQUARED {
        return (
            previous_raw_view_anchor_model,
            previous_adjusted_view_anchor_model,
        );
    }

    (
        runtime_view_anchor_model,
        adjust_hmd_view_anchor_model(runtime_view_anchor_model, head_rotation),
    )
}

fn adjust_hmd_view_anchor_model(raw_view_anchor_model: Vec3, head_rotation: Quat) -> Vec3 {
    raw_view_anchor_model
        + normalize_or_identity(head_rotation) * hmd_view_anchor_local_offset_model()
}

fn hmd_view_anchor_local_offset_model() -> Vec3 {
    meters_to_model(Vec3::new(
        HMD_VIEW_ANCHOR_RIGHT_OFFSET_METERS,
        HMD_VIEW_ANCHOR_UP_OFFSET_METERS,
        HMD_VIEW_ANCHOR_FORWARD_OFFSET_METERS,
    ))
}

fn meters_to_model(value: Vec3) -> Vec3 {
    value / MODEL_TO_WORLD_SCALE
}

fn normalize_or_identity(value: Quat) -> Quat {
    if value.length_squared() > 1e-6 {
        value.normalize()
    } else {
        Quat::IDENTITY
    }
}

fn rotation_degrees_to_quat(value: [f32; 3]) -> Quat {
    Quat::from_euler(
        EulerRot::XYZ,
        value[0].to_radians(),
        value[1].to_radians(),
        value[2].to_radians(),
    )
    .normalize()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn demo_model_format_should_detect_vrm_extension_case_insensitively() {
        let format = DemoModelFormat::from_path(Path::new("avatar.VRM")).unwrap();

        assert_eq!(format, DemoModelFormat::Vrm);
    }

    #[test]
    fn demo_model_format_should_detect_pmx_extension_case_insensitively() {
        let format = DemoModelFormat::from_path(Path::new("avatar.PmX")).unwrap();

        assert_eq!(format, DemoModelFormat::Pmx);
    }

    #[test]
    fn vrm_demo_hand_calibration_should_keep_identity_rotation_defaults() {
        let calibration = vrm_demo_hand_calibration();

        assert_quat_near(
            calibration.left.orientation_offset,
            rotation_degrees_to_quat(VRM_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR),
            1e-6,
        );
        assert_quat_near(
            calibration.right.orientation_offset,
            rotation_degrees_to_quat(VRM_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR),
            1e-6,
        );
    }

    #[test]
    fn pmx_demo_hand_calibration_should_default_to_documented_controller_to_palm_rotations() {
        let calibration = pmx_demo_hand_calibration();

        assert_quat_near(
            calibration.left.orientation_offset,
            rotation_degrees_to_quat(PMX_DEFAULT_LEFT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR),
            1e-6,
        );
        assert_quat_near(
            calibration.right.orientation_offset,
            rotation_degrees_to_quat(PMX_DEFAULT_RIGHT_CONTROLLER_TO_PALM_ROTATION_DEGREES_XR),
            1e-6,
        );
    }

    #[test]
    fn adjust_hmd_view_anchor_model_should_apply_default_local_offset_when_head_rotation_identity()
    {
        let raw_anchor = Vec3::new(3.0, 7.0, 11.0);

        let adjusted_anchor = adjust_hmd_view_anchor_model(raw_anchor, Quat::IDENTITY);

        assert_vec3_near(
            adjusted_anchor,
            raw_anchor + hmd_view_anchor_local_offset_model(),
            1e-5,
        );
    }

    #[test]
    fn adjust_hmd_view_anchor_model_should_rotate_offset_with_head_yaw() {
        let raw_anchor = Vec3::new(1.0, 2.0, 3.0);
        let head_rotation = Quat::from_rotation_y(std::f32::consts::FRAC_PI_2);

        let adjusted_anchor = adjust_hmd_view_anchor_model(raw_anchor, head_rotation);

        assert_vec3_near(
            adjusted_anchor,
            raw_anchor + head_rotation * hmd_view_anchor_local_offset_model(),
            1e-5,
        );
    }

    #[test]
    fn adjust_hmd_view_anchor_model_should_rotate_offset_with_head_pitch() {
        let raw_anchor = Vec3::new(-4.0, 5.0, -6.0);
        let head_rotation = Quat::from_rotation_x(std::f32::consts::FRAC_PI_4);

        let adjusted_anchor = adjust_hmd_view_anchor_model(raw_anchor, head_rotation);

        assert_vec3_near(
            adjusted_anchor,
            raw_anchor + head_rotation * hmd_view_anchor_local_offset_model(),
            1e-5,
        );
    }

    #[test]
    fn resolve_demo_view_anchor_should_keep_previous_adjusted_anchor_when_runtime_anchor_is_zero() {
        let previous_raw_anchor = Vec3::new(10.0, 20.0, 30.0);
        let previous_adjusted_anchor = Vec3::new(11.0, 22.0, 33.0);

        let (raw_anchor, adjusted_anchor) = resolve_demo_view_anchor(
            previous_raw_anchor,
            previous_adjusted_anchor,
            Vec3::ZERO,
            Quat::from_rotation_y(std::f32::consts::FRAC_PI_2),
        );

        assert_vec3_near(raw_anchor, previous_raw_anchor, 1e-5);
        assert_vec3_near(adjusted_anchor, previous_adjusted_anchor, 1e-5);
    }

    fn assert_vec3_near(actual: Vec3, expected: Vec3, tolerance: f32) {
        let delta = actual - expected;
        assert!(
            delta.length() <= tolerance,
            "vec mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }

    fn assert_quat_near(actual: Quat, expected: Quat, tolerance: f32) {
        let similarity = actual.dot(expected).abs();
        assert!(
            1.0 - similarity <= tolerance,
            "quat mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }
}
