use std::path::PathBuf;
use std::time::{Duration, Instant};

use anyhow::{anyhow, Context, Result};
use rfd::FileDialog;
use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::{ElementState, KeyEvent, WindowEvent};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{Window, WindowAttributes, WindowId};

use crate::control_window::ControlWindow;
use crate::scene::{AvatarScene, TrackingFrame};
use crate::vulkan::VulkanRenderer;
use crate::xr::XrBootstrap;

const IDLE_REDRAW_INTERVAL: Duration = Duration::from_millis(200);

pub fn run() -> Result<()> {
    let model_path = resolve_model_path()?;
    let event_loop = EventLoop::new().context("failed to create winit event loop")?;
    let mut app = DemoApp::new(model_path);
    event_loop
        .run_app(&mut app)
        .context("failed to run VR PMX Demo")
}

fn resolve_model_path() -> Result<PathBuf> {
    let model_path = match parse_cli_model_path()? {
        Some(path) => path,
        None => pick_model_path_with_dialog()?,
    };
    validate_model_path(model_path)
}

fn parse_cli_model_path() -> Result<Option<PathBuf>> {
    let mut args = std::env::args_os().skip(1);
    let mut model_path = None;

    while let Some(arg) = args.next() {
        if let Some(flag) = arg.to_str() {
            if flag == "-m" || flag == "--model" {
                let value = args
                    .next()
                    .ok_or_else(|| anyhow!("`--model` requires a file path"))?;
                set_model_path(&mut model_path, PathBuf::from(value))?;
                continue;
            }

            if let Some(value) = flag.strip_prefix("--model=") {
                set_model_path(&mut model_path, PathBuf::from(value))?;
                continue;
            }
        }

        set_model_path(&mut model_path, PathBuf::from(arg))?;
    }

    Ok(model_path)
}

fn set_model_path(slot: &mut Option<PathBuf>, path: PathBuf) -> Result<()> {
    if let Some(existing) = slot {
        return Err(anyhow!(
            "multiple VRM model paths were provided: `{}` and `{}`",
            existing.display(),
            path.display()
        ));
    }

    *slot = Some(path);
    Ok(())
}

fn pick_model_path_with_dialog() -> Result<PathBuf> {
    let mut dialog = FileDialog::new()
        .set_title("Select a VRM model")
        .add_filter("VRM avatar", &["vrm"]);
    if let Ok(current_dir) = std::env::current_dir() {
        dialog = dialog.set_directory(current_dir);
    }

    dialog.pick_file().ok_or_else(|| {
        anyhow!("no VRM model selected; pass `--model <path>` or choose a `.vrm` file")
    })
}

fn validate_model_path(path: PathBuf) -> Result<PathBuf> {
    let path = if path.is_absolute() {
        path
    } else {
        std::env::current_dir()
            .context("failed to resolve the current directory for the VRM path")?
            .join(path)
    };
    let path = path.canonicalize().unwrap_or(path);

    if !path.is_file() {
        return Err(anyhow!("VRM file does not exist: {}", path.display()));
    }

    let is_vrm = path
        .extension()
        .and_then(|extension| extension.to_str())
        .is_some_and(|extension| extension.eq_ignore_ascii_case("vrm"));
    if !is_vrm {
        return Err(anyhow!(
            "selected file is not a `.vrm` model: {}",
            path.display()
        ));
    }

    Ok(path)
}

struct DemoApp {
    model_path: PathBuf,
    demo: Option<VrDemo>,
}

impl DemoApp {
    fn new(model_path: PathBuf) -> Self {
        Self {
            model_path,
            demo: None,
        }
    }

    fn update_control_flow(&mut self, event_loop: &ActiveEventLoop) {
        let Some(demo) = self.demo.as_mut() else {
            event_loop.set_control_flow(ControlFlow::Wait);
            return;
        };

        demo.schedule_next_wakeup(event_loop);
    }
}

impl ApplicationHandler for DemoApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.demo.is_some() {
            self.update_control_flow(event_loop);
            return;
        }

        match VrDemo::new(event_loop, &self.model_path) {
            Ok(demo) => {
                self.demo = Some(demo);
                self.update_control_flow(event_loop);
            }
            Err(err) => {
                log::error!("failed to initialize VR PMX Demo: {err:#}");
                event_loop.exit();
            }
        }
    }

    fn window_event(
        &mut self,
        event_loop: &ActiveEventLoop,
        window_id: WindowId,
        event: WindowEvent,
    ) {
        let Some(demo) = self.demo.as_mut() else {
            return;
        };
        if window_id == demo.window.id() {
            match event {
                WindowEvent::CloseRequested => event_loop.exit(),
                WindowEvent::Resized(_) => {
                    if let Err(err) = demo.renderer.resize_mirror(&demo.window) {
                        log::error!("failed to resize the mirror surface: {err:#}");
                        event_loop.exit();
                        return;
                    }
                    demo.request_redraw_soon();
                }
                WindowEvent::RedrawRequested => match demo.render_frame() {
                    Ok(should_exit) => {
                        if should_exit {
                            event_loop.exit();
                            return;
                        }
                    }
                    Err(err) => {
                        log::error!("frame rendering failed: {err:#}");
                        event_loop.exit();
                        return;
                    }
                },
                WindowEvent::KeyboardInput { event, .. } => {
                    demo.handle_keyboard(event_loop, &event);
                }
                _ => {}
            }

            self.update_control_flow(event_loop);
            return;
        }

        if window_id == demo.control.window_id() {
            match event {
                WindowEvent::CloseRequested => {
                    event_loop.exit();
                    return;
                }
                WindowEvent::RedrawRequested => {
                    if let Err(err) = demo.render_control_window() {
                        log::error!("control window rendering failed: {err:#}");
                        event_loop.exit();
                        return;
                    }
                }
                _ => {
                    demo.control.handle_window_event(&event);
                }
            }

            self.update_control_flow(event_loop);
            return;
        }

        self.update_control_flow(event_loop);
    }

    fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
        self.update_control_flow(event_loop);
    }
}

struct VrDemo {
    window: Window,
    control: ControlWindow,
    renderer: VulkanRenderer,
    xr: crate::xr::XrRuntime,
    scene: AvatarScene,
    last_frame_instant: Instant,
    last_title_update: Instant,
    next_idle_redraw: Instant,
}

impl VrDemo {
    fn new(event_loop: &ActiveEventLoop, model_path: &PathBuf) -> Result<Self> {
        log::info!("VR Demo: creating mirror window");
        let window = event_loop
            .create_window(window_attributes())
            .context("failed to create the mirror window")?;

        log::info!("VR Demo: initializing OpenXR");
        let bootstrap = XrBootstrap::new()?;
        if !bootstrap
            .runtime_name()
            .to_ascii_lowercase()
            .contains("steam")
        {
            log::warn!(
                "current OpenXR runtime is not SteamVR: {}",
                bootstrap.runtime_name()
            );
        }

        log::info!("VR Demo: loading scene from {}", model_path.display());
        let scene = AvatarScene::new(model_path)?;
        log::info!("VR Demo: creating arm IK control window");
        let control = ControlWindow::new(
            event_loop,
            scene.hand_calibration(),
            scene.arm_ik_calibration(),
        )?;

        log::info!("VR Demo: initializing Vulkan renderer");
        let mut renderer =
            VulkanRenderer::new(&window, &bootstrap, scene.assets(), scene.room_meshes())?;

        log::info!("VR Demo: creating OpenXR session");
        let xr = bootstrap.create_runtime(renderer.session_info())?;

        log::info!("VR Demo: initializing OpenXR render targets");
        renderer.initialize_xr_targets(&xr)?;

        let now = Instant::now();
        let mut demo = Self {
            window,
            control,
            renderer,
            xr,
            scene,
            last_frame_instant: now,
            last_title_update: now,
            next_idle_redraw: now,
        };
        demo.refresh_title();
        log::info!("{}", demo.scene.summary_text(&demo.xr.runtime_label()));
        Ok(demo)
    }

    fn handle_keyboard(&mut self, event_loop: &ActiveEventLoop, event: &KeyEvent) {
        if event.state != ElementState::Pressed {
            return;
        }

        if let PhysicalKey::Code(code) = event.physical_key {
            match code {
                KeyCode::Escape => event_loop.exit(),
                KeyCode::KeyR => {
                    self.scene.recenter();
                    self.refresh_title();
                    self.request_redraw_soon();
                }
                _ => {}
            }
        }
    }

    fn request_redraw_soon(&mut self) {
        self.next_idle_redraw = Instant::now();
        self.window.request_redraw();
        self.control.request_redraw();
    }

    fn schedule_next_wakeup(&mut self, event_loop: &ActiveEventLoop) {
        if self.xr.is_session_active() {
            self.next_idle_redraw = Instant::now();
            event_loop.set_control_flow(ControlFlow::Poll);
            self.window.request_redraw();
            self.control.request_redraw();
            return;
        }

        let now = Instant::now();
        if now >= self.next_idle_redraw {
            self.window.request_redraw();
            self.control.request_redraw();
            self.next_idle_redraw = now + IDLE_REDRAW_INTERVAL;
        }
        event_loop.set_control_flow(ControlFlow::WaitUntil(self.next_idle_redraw));
    }

    fn render_frame(&mut self) -> Result<bool> {
        if self.xr.poll_events()? {
            return Ok(true);
        }

        let now = Instant::now();
        let delta_time = (now - self.last_frame_instant).as_secs_f32();
        self.last_frame_instant = now;

        if let Some(frame) = self.xr.begin_frame()? {
            let tracking = TrackingFrame {
                head: frame.head,
                left_grip: frame.left_grip,
                left_aim: frame.left_aim,
                left_hand: frame.left_hand,
                right_grip: frame.right_grip,
                right_aim: frame.right_aim,
                right_hand: frame.right_hand,
                buttons: frame.buttons,
            };
            self.scene.update(&tracking, delta_time);

            let xr_model_matrix = self.scene.xr_model_matrix();
            let xr_room_matrix = self.scene.xr_room_matrix();
            let mirror_model_matrix = self.scene.mirror_model_matrix();
            let mirror_room_matrix = self.scene.mirror_room_matrix();
            let mirror_camera = self.scene.mirror_camera(self.renderer.mirror_aspect());
            let hmd_data = self.scene.hmd_render_data();
            let mirror_data = self.scene.mirror_render_data();
            self.renderer.render_xr_and_mirror(
                &self.window,
                &mut self.xr,
                frame,
                self.scene.assets(),
                &hmd_data,
                &mirror_data,
                xr_model_matrix,
                xr_room_matrix,
                mirror_model_matrix,
                mirror_room_matrix,
                mirror_camera.view_proj,
            )?;
        } else {
            let model_matrix = self.scene.mirror_model_matrix();
            let room_matrix = self.scene.mirror_room_matrix();
            let mirror_camera = self.scene.mirror_camera(self.renderer.mirror_aspect());
            let mirror_data = self.scene.mirror_render_data();
            self.renderer.render_mirror_only(
                &self.window,
                self.scene.assets(),
                &mirror_data,
                model_matrix,
                room_matrix,
                mirror_camera.view_proj,
            )?;
        }

        if now.duration_since(self.last_title_update) >= Duration::from_millis(250) {
            self.refresh_title();
            self.last_title_update = now;
        }

        Ok(false)
    }

    fn render_control_window(&mut self) -> Result<()> {
        let debug_snapshot = self.scene.arm_ik_debug_snapshot();
        if let Some(calibration) = self.control.render(debug_snapshot)? {
            self.scene
                .set_hand_calibration(calibration.hand_calibration);
            self.scene
                .set_arm_ik_calibration(calibration.arm_ik_calibration);
            self.request_redraw_soon();
        }
        Ok(())
    }

    fn refresh_title(&mut self) {
        let runtime_label = self.xr.runtime_label();
        let title = format!(
            "VR PMX Demo | {} | {}",
            runtime_label,
            self.scene.status_line(&runtime_label)
        );
        self.window.set_title(&title);
    }
}

fn window_attributes() -> WindowAttributes {
    Window::default_attributes()
        .with_title("VR PMX Demo")
        .with_inner_size(LogicalSize::new(1280.0, 720.0))
        .with_resizable(true)
}
