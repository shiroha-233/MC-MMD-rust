use std::time::Instant;

extern crate mmd_engine;

use egui::{FontData, FontDefinitions, FontFamily, ViewportId};
use glium::{
    backend::glutin::SimpleWindowBuilder,
    glutin::surface::WindowSurface,
    winit::{
        application::ApplicationHandler,
        event::{ElementState, WindowEvent},
        event_loop::{ActiveEventLoop, EventLoop},
        keyboard::{KeyCode, PhysicalKey},
        window::{Window, WindowId},
    },
    Surface,
};

mod camera;
mod gui;
mod input;
mod renderer;
mod state;

use camera::Camera;
use gui::{ViewerAction, ViewerGuiState, ViewerSnapshot};
use input::InputState;
use renderer::Renderer;
use state::ViewerPersistedState;

fn main() {
    env_logger::init();

    let args: Vec<String> = std::env::args().collect();
    let persisted_state = ViewerPersistedState::load();
    let startup_model = args.get(1).cloned().or_else(|| {
        persisted_state
            .as_ref()
            .map(|state| state.model_path.clone())
            .filter(|path| !path.is_empty())
    });
    let startup_animation = args.get(2).cloned().or_else(|| {
        persisted_state
            .as_ref()
            .map(|state| state.animation_path.clone())
            .filter(|path| !path.is_empty())
    });
    let startup_fbx_stack = persisted_state
        .as_ref()
        .and_then(|state| state.selected_fbx_stack.clone());

    println!("=== MMD Model Viewer ===");
    println!("用法: viewer <pmx文件> [vmd/fbx文件]");
    println!("GUI: 左侧面板可自由选择 PMX 模型和 VMD/FBX 动作");
    println!("快捷键: WASD/QE 移动, 右键旋转, 滚轮缩放, Space 播放/暂停, R 重置");

    let event_loop = EventLoop::new().expect("Failed to create event loop");
    let (window, display) = create_display(&event_loop);
    let egui = egui_glium::EguiGlium::new(ViewportId::ROOT, &display, &window, &event_loop);
    configure_chinese_font(egui.egui_ctx());

    let mut app = ViewerApp::new(
        window,
        display,
        egui,
        startup_model,
        startup_animation,
        startup_fbx_stack,
    );
    if let Err(error) = event_loop.run_app(&mut app) {
        eprintln!("viewer 退出失败: {}", error);
    }
}

struct ViewerApp {
    window: Window,
    display: glium::Display<WindowSurface>,
    egui: egui_glium::EguiGlium,
    renderer: Renderer,
    camera: Camera,
    input: InputState,
    gui: ViewerGuiState,
    last_frame: Instant,
}

impl ViewerApp {
    fn new(
        window: Window,
        display: glium::Display<WindowSurface>,
        egui: egui_glium::EguiGlium,
        startup_model: Option<String>,
        startup_animation: Option<String>,
        startup_fbx_stack: Option<String>,
    ) -> Self {
        let mut renderer = Renderer::new(&display);
        let mut gui = ViewerGuiState::new(startup_model.clone(), startup_animation.clone());

        if let Some(model_path) = startup_model {
            match renderer.load_model(&display, &model_path) {
                Ok(()) => gui.set_info(format!("已加载模型: {}", model_path)),
                Err(error) => gui.set_error(format!("模型加载失败: {}", error)),
            }
        }

        if let Some(animation_arg) = startup_animation {
            let (animation_path, stack_name) = parse_animation_argument(&animation_arg);
            gui.set_animation_path(animation_path.to_string());

            if animation_path.to_ascii_lowercase().ends_with(".fbx") {
                match renderer.list_fbx_stacks(animation_path) {
                    Ok(stacks) => {
                        gui.set_fbx_stacks(stacks);
                        gui.restore_selected_fbx_stack(
                            startup_fbx_stack.as_deref().or(stack_name.as_deref()),
                        );
                    }
                    Err(error) => gui.set_error(format!("读取 FBX 动作列表失败: {}", error)),
                }
            }

            let load_stack_name = gui.selected_stack_name();
            match renderer.load_animation(animation_path, load_stack_name.as_deref()) {
                Ok(()) => gui.set_info(format!("已加载动作: {}", animation_arg)),
                Err(error) => gui.set_error(format!("动作加载失败: {}", error)),
            }
        }

        Self {
            window,
            display,
            egui,
            renderer,
            camera: Camera::new(),
            input: InputState::new(),
            gui,
            last_frame: Instant::now(),
        }
    }

    fn snapshot(&self) -> ViewerSnapshot {
        ViewerSnapshot {
            has_model: self.renderer.has_model(),
            has_animation: self.renderer.has_animation(),
            playing: self.renderer.is_playing(),
            current_frame: self.renderer.current_frame(),
        }
    }

    fn redraw(&mut self) {
        let now = Instant::now();
        let delta_time = (now - self.last_frame).as_secs_f32();
        self.last_frame = now;

        let snapshot = self.snapshot();
        let gui = &mut self.gui;
        let mut actions = Vec::new();
        self.egui.run(&self.window, |ctx| {
            actions = gui.show(ctx, snapshot);
        });

        self.process_actions(actions);

        self.camera.update(&self.input, delta_time);
        self.input.end_frame();
        self.renderer.update(delta_time);

        let mut target = self.display.draw();
        target.clear_color_and_depth((0.85, 0.87, 0.9, 1.0), 1.0);
        self.renderer
            .render(&self.display, &mut target, &self.camera);
        self.egui.paint(&self.display, &mut target);

        if let Err(error) = target.finish() {
            self.gui.set_error(format!("提交渲染帧失败: {}", error));
        }

        self.window.request_redraw();
    }

    fn process_actions(&mut self, actions: Vec<ViewerAction>) {
        let mut should_save_state = false;

        for action in actions {
            match action {
                ViewerAction::LoadModel(path) => {
                    match self.renderer.load_model(&self.display, &path) {
                        Ok(()) => self.gui.set_info(format!("模型加载成功: {}", path)),
                        Err(error) => self.gui.set_error(format!("模型加载失败: {}", error)),
                    }
                    should_save_state = true;
                }
                ViewerAction::RefreshFbxStacks(path) => {
                    match self.renderer.list_fbx_stacks(&path) {
                        Ok(stacks) => {
                            let count = stacks.len();
                            self.gui.set_fbx_stacks(stacks);
                            self.gui
                                .set_info(format!("已读取 {} 个 FBX AnimationStack", count));
                        }
                        Err(error) => self
                            .gui
                            .set_error(format!("读取 FBX 动作列表失败: {}", error)),
                    }
                    should_save_state = true;
                }
                ViewerAction::LoadAnimation { path, stack_name } => {
                    match self.renderer.load_animation(&path, stack_name.as_deref()) {
                        Ok(()) => {
                            let suffix = stack_name
                                .map(|name| format!(" (Stack: {})", name))
                                .unwrap_or_default();
                            self.gui
                                .set_info(format!("动作加载成功: {}{}", path, suffix));
                        }
                        Err(error) => self.gui.set_error(format!("动作加载失败: {}", error)),
                    }
                    should_save_state = true;
                }
                ViewerAction::ClearAnimation => {
                    self.renderer.clear_animation();
                    self.gui.set_info("已清除当前动作");
                    should_save_state = true;
                }
                ViewerAction::ToggleAnimation => {
                    self.renderer.toggle_animation();
                }
                ViewerAction::ResetAnimation => {
                    self.renderer.reset_animation();
                    self.gui.set_info("动画已重置到第 0 帧");
                }
                ViewerAction::PersistState => {
                    should_save_state = true;
                }
            }
        }

        if should_save_state {
            self.save_state();
        }
    }

    fn save_state(&mut self) {
        let state = ViewerPersistedState {
            model_path: self.gui.model_path().to_string(),
            animation_path: self.gui.animation_path().to_string(),
            selected_fbx_stack: self.gui.selected_stack_name(),
        };

        if let Err(error) = state.save() {
            self.gui.set_error(error);
        }
    }

    fn handle_hotkey(&mut self, event_loop: &ActiveEventLoop, keycode: KeyCode) {
        match keycode {
            KeyCode::Space => self.renderer.toggle_animation(),
            KeyCode::KeyR => self.renderer.reset_animation(),
            KeyCode::KeyB => self.renderer.toggle_bones(),
            KeyCode::KeyG => self.renderer.toggle_mesh(),
            KeyCode::KeyX => self.renderer.toggle_axes(),
            KeyCode::KeyF => self.renderer.toggle_wireframe(),
            KeyCode::Digit1 => self.camera.set_view_front(),
            KeyCode::Digit2 => self.camera.set_view_side(),
            KeyCode::Digit3 => self.camera.set_view_top(),
            KeyCode::Digit4 => self.camera.set_view_free(),
            KeyCode::Escape => event_loop.exit(),
            _ => {}
        }
    }
}

impl ApplicationHandler for ViewerApp {
    fn resumed(&mut self, _event_loop: &ActiveEventLoop) {}

    fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
        self.window.request_redraw();
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        let event_response = self.egui.on_event(&self.window, &event);

        match &event {
            WindowEvent::CloseRequested | WindowEvent::Destroyed => {
                event_loop.exit();
                return;
            }
            WindowEvent::Focused(false) => {
                self.input.reset();
            }
            WindowEvent::Resized(new_size) => {
                self.display.resize((*new_size).into());
                if new_size.height > 0 {
                    self.camera
                        .set_aspect(new_size.width as f32 / new_size.height as f32);
                }
            }
            WindowEvent::KeyboardInput {
                event: key_event, ..
            } => {
                if !event_response.consumed || key_event.state == ElementState::Released {
                    self.input.handle_keyboard(key_event);
                }

                if !event_response.consumed
                    && key_event.state == ElementState::Pressed
                    && !key_event.repeat
                {
                    if let PhysicalKey::Code(keycode) = key_event.physical_key {
                        self.handle_hotkey(event_loop, keycode);
                    }
                }
            }
            WindowEvent::MouseInput { state, button, .. } => {
                if !event_response.consumed || *state == ElementState::Released {
                    self.input.handle_mouse_button(*button, *state);
                }
            }
            WindowEvent::CursorMoved { position, .. } => {
                self.input
                    .handle_mouse_move(position.x as f32, position.y as f32);
                if event_response.consumed {
                    self.input.clear_mouse_delta();
                }
            }
            WindowEvent::MouseWheel { delta, .. } => {
                if !event_response.consumed {
                    self.input.handle_mouse_wheel(*delta);
                }
            }
            WindowEvent::RedrawRequested => {
                self.redraw();
            }
            _ => {}
        }

        if event_response.repaint {
            self.window.request_redraw();
        }
    }
}

fn create_display(event_loop: &EventLoop<()>) -> (Window, glium::Display<WindowSurface>) {
    SimpleWindowBuilder::new()
        .set_window_builder(Window::default_attributes().with_resizable(true))
        .with_inner_size(1280, 720)
        .with_title("MMD Model Viewer")
        .build(event_loop)
}

fn parse_animation_argument(arg: &str) -> (&str, Option<String>) {
    if let Some(pos) = arg.rfind('#') {
        let file_path = &arg[..pos];
        if file_path.to_ascii_lowercase().ends_with(".fbx") {
            let stack_name = arg[pos + 1..].trim();
            if !stack_name.is_empty() {
                return (file_path, Some(stack_name.to_string()));
            }
        }
    }

    (arg, None)
}

fn configure_chinese_font(ctx: &egui::Context) {
    let font_paths = [
        "C:\\Windows\\Fonts\\simhei.ttf",
        "C:\\Windows\\Fonts\\msyh.ttf",
        "C:\\Windows\\Fonts\\simsun.ttc",
    ];

    for path in font_paths {
        let Ok(bytes) = std::fs::read(path) else {
            continue;
        };

        let mut fonts = FontDefinitions::default();
        fonts
            .font_data
            .insert("viewer_cjk".to_string(), FontData::from_owned(bytes).into());

        if let Some(family) = fonts.families.get_mut(&FontFamily::Proportional) {
            family.insert(0, "viewer_cjk".to_string());
        }
        if let Some(family) = fonts.families.get_mut(&FontFamily::Monospace) {
            family.insert(0, "viewer_cjk".to_string());
        }

        ctx.set_fonts(fonts);
        return;
    }
}
