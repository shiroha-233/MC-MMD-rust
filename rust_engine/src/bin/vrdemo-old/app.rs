use std::path::PathBuf;
use std::time::{Duration, Instant};

use anyhow::{Context, Result};
use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::{ElementState, KeyEvent, WindowEvent};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{Window, WindowAttributes, WindowId};

use crate::scene::{AvatarScene, TrackingFrame};
use crate::vulkan::VulkanRenderer;
use crate::xr::XrBootstrap;

const DEFAULT_MODEL_PATH: &str = "D:/GITHUB/work/MC-MMD-rust/moxing/玛丽/vrm/伊落玛丽.vrm";

pub fn run() -> Result<()> {
    let event_loop = EventLoop::new().context("创建 winit EventLoop 失败")?;
    let mut app = DemoApp::new(PathBuf::from(DEFAULT_MODEL_PATH));
    event_loop
        .run_app(&mut app)
        .context("运行 VR PMX Demo 失败")
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
}

impl ApplicationHandler for DemoApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        event_loop.set_control_flow(ControlFlow::Poll);
        if self.demo.is_some() {
            return;
        }

        match VrDemo::new(event_loop, &self.model_path) {
            Ok(demo) => self.demo = Some(demo),
            Err(err) => {
                log::error!("初始化 VR PMX Demo 失败: {err:#}");
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
        if window_id != demo.window.id() {
            return;
        }

        match event {
            WindowEvent::CloseRequested => event_loop.exit(),
            WindowEvent::Resized(_) => {
                if let Err(err) = demo.renderer.resize_mirror(&demo.window) {
                    log::error!("重建桌面镜像失败: {err:#}");
                    event_loop.exit();
                }
            }
            WindowEvent::RedrawRequested => match demo.render_frame() {
                Ok(should_exit) => {
                    if should_exit {
                        event_loop.exit();
                    }
                }
                Err(err) => {
                    log::error!("渲染失败: {err:#}");
                    event_loop.exit();
                }
            },
            WindowEvent::KeyboardInput { event, .. } => {
                demo.handle_keyboard(event_loop, &event);
            }
            _ => {}
        }
    }

    fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
        if let Some(demo) = self.demo.as_ref() {
            demo.window.request_redraw();
        }
    }
}

struct VrDemo {
    window: Window,
    renderer: VulkanRenderer,
    xr: crate::xr::XrRuntime,
    scene: AvatarScene,
    last_frame_instant: Instant,
    last_title_update: Instant,
}

impl VrDemo {
    fn new(event_loop: &ActiveEventLoop, model_path: &PathBuf) -> Result<Self> {
        log::info!("VR Demo: 创建桌面窗口");
        let window = event_loop
            .create_window(window_attributes())
            .context("创建桌面镜像窗口失败")?;

        log::info!("VR Demo: 初始化 OpenXR");
        let bootstrap = XrBootstrap::new()?;
        if !bootstrap
            .runtime_name()
            .to_ascii_lowercase()
            .contains("steam")
        {
            log::warn!(
                "当前 OpenXR runtime 不是 SteamVR: {}，但仍继续启动",
                bootstrap.runtime_name()
            );
        }

        log::info!("VR Demo: 加载场景 {}", model_path.display());
        let scene = AvatarScene::new(model_path)?;
        log::info!("VR Demo: 初始化 Vulkan 渲染器");
        let mut renderer = VulkanRenderer::new(&window, &bootstrap, scene.assets())?;
        log::info!("VR Demo: 创建 OpenXR Session");
        let xr = bootstrap.create_runtime(renderer.session_info())?;
        log::info!("VR Demo: 初始化 OpenXR 渲染目标");
        renderer.initialize_xr_targets(&xr)?;
        log::info!("VR Demo: 初始化完成，进入主循环");

        let mut demo = Self {
            window,
            renderer,
            xr,
            scene,
            last_frame_instant: Instant::now(),
            last_title_update: Instant::now(),
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
                }
                _ => {}
            }
        }
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
                left_hand: frame.left_hand,
                right_hand: frame.right_hand,
            };
            self.scene.update(&tracking, delta_time);

            let xr_model_matrix = self.scene.xr_model_matrix();
            let mirror_model_matrix = self.scene.mirror_model_matrix();
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
                mirror_model_matrix,
                mirror_camera.view_proj,
            )?;
        } else {
            let model_matrix = self.scene.mirror_model_matrix();
            let mirror_camera = self.scene.mirror_camera(self.renderer.mirror_aspect());
            let mirror_data = self.scene.mirror_render_data();
            self.renderer.render_mirror_only(
                &self.window,
                self.scene.assets(),
                &mirror_data,
                model_matrix,
                mirror_camera.view_proj,
            )?;
        }

        if now.duration_since(self.last_title_update) >= Duration::from_millis(250) {
            self.refresh_title();
            self.last_title_update = now;
        }

        Ok(false)
    }

    fn refresh_title(&mut self) {
        let title = format!(
            "VR PMX Demo | {} | {}",
            self.xr.runtime_label(),
            self.scene.status_line(&self.xr.runtime_label())
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
