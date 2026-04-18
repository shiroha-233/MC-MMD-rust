use std::num::NonZeroU32;
use std::sync::Arc;

use anyhow::{Context, Result};
use egui::{Slider, ViewportId};
use egui_wgpu::WgpuConfiguration;
use egui_winit::State as EguiWinitState;
use glam::{EulerRot, Quat, Vec3};
use mmd_engine::vrm_runtime::{
    ArmIkCalibration, ArmIkHandCalibration, HandGripOffset, HandTrackingCalibration,
};
use pollster::block_on;
use winit::dpi::LogicalSize;
use winit::event::WindowEvent;
use winit::event_loop::ActiveEventLoop;
use winit::window::{Window, WindowAttributes, WindowId};

use crate::avatar_rig::{ArmIkDebugSnapshot, MODEL_TO_WORLD_SCALE};

const OFFSET_RANGE_METERS: std::ops::RangeInclusive<f32> = -0.15..=0.15;
const ROTATION_RANGE_DEGREES: std::ops::RangeInclusive<f32> = -180.0..=180.0;
const TWIST_RANGE: std::ops::RangeInclusive<f32> = 0.0..=1.0;
const CLEAR_COLOR: [f32; 4] = [0.08, 0.08, 0.1, 1.0];

#[derive(Clone, Copy, Debug)]
pub struct ControlCalibrationUpdate {
    pub hand_calibration: HandTrackingCalibration,
    pub arm_ik_calibration: ArmIkCalibration,
}

pub struct ControlWindow {
    window: Arc<Window>,
    egui_ctx: egui::Context,
    egui_state: EguiWinitState,
    painter: egui_wgpu::winit::Painter,
    calibration_ui: ArmIkCalibrationUiState,
}

impl ControlWindow {
    pub fn new(
        event_loop: &ActiveEventLoop,
        initial_hand_calibration: HandTrackingCalibration,
        initial_calibration: ArmIkCalibration,
    ) -> Result<Self> {
        let window = Arc::new(
            event_loop
                .create_window(control_window_attributes())
                .context("failed to create the arm IK control window")?,
        );

        let egui_ctx = egui::Context::default();
        let mut painter =
            egui_wgpu::winit::Painter::new(WgpuConfiguration::default(), 1, None, false, false);
        block_on(painter.set_window(ViewportId::ROOT, Some(window.clone())))
            .context("failed to initialize the arm IK control surface")?;

        let egui_state = EguiWinitState::new(
            egui_ctx.clone(),
            ViewportId::ROOT,
            window.as_ref(),
            Some(window.scale_factor() as f32),
            window.theme(),
            painter.max_texture_side(),
        );

        Ok(Self {
            window,
            egui_ctx,
            egui_state,
            painter,
            calibration_ui: ArmIkCalibrationUiState::from_model(
                initial_hand_calibration,
                initial_calibration,
            ),
        })
    }

    pub fn window_id(&self) -> WindowId {
        self.window.id()
    }

    pub fn request_redraw(&self) {
        self.window.request_redraw();
    }

    pub fn handle_window_event(&mut self, event: &WindowEvent) -> bool {
        let response = self.egui_state.on_window_event(self.window.as_ref(), event);
        match event {
            WindowEvent::Resized(size) => {
                self.resize(size.width, size.height);
            }
            WindowEvent::ScaleFactorChanged { .. } => {
                let size = self.window.inner_size();
                self.resize(size.width, size.height);
            }
            _ => {}
        }

        if response.repaint {
            self.window.request_redraw();
        }
        response.repaint
    }

    pub fn render(
        &mut self,
        debug_snapshot: ArmIkDebugSnapshot,
    ) -> Result<Option<ControlCalibrationUpdate>> {
        let size = self.window.inner_size();
        if size.width == 0 || size.height == 0 {
            return Ok(None);
        }

        let raw_input = self.egui_state.take_egui_input(self.window.as_ref());
        let mut calibration_changed = false;
        let full_output = self.egui_ctx.run(raw_input, |ctx| {
            egui::CentralPanel::default().show(ctx, |ui| {
                ui.heading("Hand / Arm IK Calibration");
                ui.label(
                    "Tune grip alignment, wrist seam alignment, and forearm twist distribution.",
                );
                ui.separator();

                ui.label("Left grip -> palm rotation (deg)");
                calibration_changed |=
                    rotation_sliders(ui, "left_rotation", &mut self.calibration_ui.left_rotation);

                ui.separator();
                ui.label("Right grip -> palm rotation (deg)");
                calibration_changed |= rotation_sliders(
                    ui,
                    "right_rotation",
                    &mut self.calibration_ui.right_rotation,
                );

                ui.separator();
                ui.label("Left wrist offset (m)");
                calibration_changed |= offset_sliders(ui, "left", &mut self.calibration_ui.left);

                ui.separator();
                ui.label("Right wrist offset (m)");
                calibration_changed |= offset_sliders(ui, "right", &mut self.calibration_ui.right);

                ui.separator();
                calibration_changed |= ui
                    .add(
                        Slider::new(&mut self.calibration_ui.forearm_twist_ratio, TWIST_RANGE)
                            .text("Forearm twist ratio"),
                    )
                    .changed();

                ui.separator();
                ui.monospace(format!(
                    "Auto left wrist offset:  ({:.3}, {:.3}, {:.3}) m",
                    debug_snapshot.left_auto_wrist_offset_model.x * MODEL_TO_WORLD_SCALE,
                    debug_snapshot.left_auto_wrist_offset_model.y * MODEL_TO_WORLD_SCALE,
                    debug_snapshot.left_auto_wrist_offset_model.z * MODEL_TO_WORLD_SCALE,
                ));
                ui.monospace(format!(
                    "Auto right wrist offset: ({:.3}, {:.3}, {:.3}) m",
                    debug_snapshot.right_auto_wrist_offset_model.x * MODEL_TO_WORLD_SCALE,
                    debug_snapshot.right_auto_wrist_offset_model.y * MODEL_TO_WORLD_SCALE,
                    debug_snapshot.right_auto_wrist_offset_model.z * MODEL_TO_WORLD_SCALE,
                ));
                ui.monospace(format!(
                    "Wrist error cm: left {:.2} | right {:.2}",
                    debug_snapshot.left_wrist_error_cm, debug_snapshot.right_wrist_error_cm
                ));
            });
        });

        self.egui_state
            .handle_platform_output(self.window.as_ref(), full_output.platform_output.clone());

        let clipped_primitives = self
            .egui_ctx
            .tessellate(full_output.shapes, full_output.pixels_per_point);
        let _ = self.painter.paint_and_update_textures(
            ViewportId::ROOT,
            full_output.pixels_per_point,
            CLEAR_COLOR,
            &clipped_primitives,
            &full_output.textures_delta,
            false,
        );

        Ok(calibration_changed.then(|| self.calibration_ui.to_model()))
    }

    fn resize(&mut self, width: u32, height: u32) {
        let Some(width) = NonZeroU32::new(width) else {
            return;
        };
        let Some(height) = NonZeroU32::new(height) else {
            return;
        };

        self.painter
            .on_window_resized(ViewportId::ROOT, width, height);
    }
}

#[derive(Clone, Copy, Debug)]
struct ArmIkCalibrationUiState {
    left_grip_position_offset: Vec3,
    right_grip_position_offset: Vec3,
    left_rotation: [f32; 3],
    right_rotation: [f32; 3],
    left: [f32; 3],
    right: [f32; 3],
    forearm_twist_ratio: f32,
}

impl ArmIkCalibrationUiState {
    fn from_model(
        hand_calibration: HandTrackingCalibration,
        calibration: ArmIkCalibration,
    ) -> Self {
        Self {
            left_grip_position_offset: hand_calibration.left.position_offset,
            right_grip_position_offset: hand_calibration.right.position_offset,
            left_rotation: quat_to_degrees(hand_calibration.left.orientation_offset),
            right_rotation: quat_to_degrees(hand_calibration.right.orientation_offset),
            left: vec3_to_meters(calibration.left.wrist_offset_model),
            right: vec3_to_meters(calibration.right.wrist_offset_model),
            forearm_twist_ratio: calibration.forearm_twist_ratio,
        }
    }

    fn to_model(self) -> ControlCalibrationUpdate {
        ControlCalibrationUpdate {
            hand_calibration: HandTrackingCalibration {
                left: HandGripOffset {
                    position_offset: self.left_grip_position_offset,
                    orientation_offset: degrees_to_quat(self.left_rotation),
                },
                right: HandGripOffset {
                    position_offset: self.right_grip_position_offset,
                    orientation_offset: degrees_to_quat(self.right_rotation),
                },
            },
            arm_ik_calibration: ArmIkCalibration {
                left: ArmIkHandCalibration {
                    wrist_offset_model: meters_to_model(self.left),
                },
                right: ArmIkHandCalibration {
                    wrist_offset_model: meters_to_model(self.right),
                },
                forearm_twist_ratio: self.forearm_twist_ratio.clamp(0.0, 1.0),
            },
        }
    }
}

fn offset_sliders(ui: &mut egui::Ui, id_prefix: &str, values: &mut [f32; 3]) -> bool {
    let mut changed = false;
    for (axis_label, value) in ["X", "Y", "Z"].into_iter().zip(values.iter_mut()) {
        changed |= ui
            .push_id(format!("{id_prefix}_{axis_label}"), |ui| {
                ui.add(
                    Slider::new(value, OFFSET_RANGE_METERS.clone())
                        .text(axis_label)
                        .step_by(0.001),
                )
                .changed()
            })
            .inner;
    }
    changed
}

fn rotation_sliders(ui: &mut egui::Ui, id_prefix: &str, values: &mut [f32; 3]) -> bool {
    let mut changed = false;
    for (axis_label, value) in ["X", "Y", "Z"].into_iter().zip(values.iter_mut()) {
        changed |= ui
            .push_id(format!("{id_prefix}_{axis_label}"), |ui| {
                ui.add(
                    Slider::new(value, ROTATION_RANGE_DEGREES.clone())
                        .text(axis_label)
                        .step_by(1.0),
                )
                .changed()
            })
            .inner;
    }
    changed
}

fn vec3_to_meters(value: glam::Vec3) -> [f32; 3] {
    [
        value.x * MODEL_TO_WORLD_SCALE,
        value.y * MODEL_TO_WORLD_SCALE,
        value.z * MODEL_TO_WORLD_SCALE,
    ]
}

fn meters_to_model(value: [f32; 3]) -> glam::Vec3 {
    glam::Vec3::new(
        value[0] / MODEL_TO_WORLD_SCALE,
        value[1] / MODEL_TO_WORLD_SCALE,
        value[2] / MODEL_TO_WORLD_SCALE,
    )
}

fn quat_to_degrees(value: Quat) -> [f32; 3] {
    let (x, y, z) = value.to_euler(EulerRot::XYZ);
    [x.to_degrees(), y.to_degrees(), z.to_degrees()]
}

fn degrees_to_quat(value: [f32; 3]) -> Quat {
    Quat::from_euler(
        EulerRot::XYZ,
        value[0].to_radians(),
        value[1].to_radians(),
        value[2].to_radians(),
    )
    .normalize()
}

fn control_window_attributes() -> WindowAttributes {
    Window::default_attributes()
        .with_title("Hand / Arm IK Controls")
        .with_inner_size(LogicalSize::new(420.0, 520.0))
        .with_resizable(true)
}
