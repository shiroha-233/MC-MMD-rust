use glium::winit::{
    event::{ElementState, KeyEvent, MouseButton, MouseScrollDelta},
    keyboard::{KeyCode, PhysicalKey},
};

pub struct InputState {
    pub key_w: bool,
    pub key_a: bool,
    pub key_s: bool,
    pub key_d: bool,
    pub key_q: bool,
    pub key_e: bool,
    pub left_mouse_down: bool,
    pub right_mouse_down: bool,
    pub mouse_pos: (f32, f32),
    pub mouse_delta: (f32, f32),
    pub scroll_delta: f32,
    last_mouse_pos: Option<(f32, f32)>,
}

impl InputState {
    pub fn new() -> Self {
        Self {
            key_w: false,
            key_a: false,
            key_s: false,
            key_d: false,
            key_q: false,
            key_e: false,
            left_mouse_down: false,
            right_mouse_down: false,
            mouse_pos: (0.0, 0.0),
            mouse_delta: (0.0, 0.0),
            scroll_delta: 0.0,
            last_mouse_pos: None,
        }
    }

    pub fn handle_keyboard(&mut self, event: &KeyEvent) {
        let pressed = event.state == ElementState::Pressed;

        if let PhysicalKey::Code(keycode) = event.physical_key {
            match keycode {
                KeyCode::KeyW => self.key_w = pressed,
                KeyCode::KeyA => self.key_a = pressed,
                KeyCode::KeyS => self.key_s = pressed,
                KeyCode::KeyD => self.key_d = pressed,
                KeyCode::KeyQ => self.key_q = pressed,
                KeyCode::KeyE => self.key_e = pressed,
                _ => {}
            }
        }
    }

    pub fn handle_mouse_button(&mut self, button: MouseButton, state: ElementState) {
        let pressed = state == ElementState::Pressed;
        match button {
            MouseButton::Left => self.left_mouse_down = pressed,
            MouseButton::Right => self.right_mouse_down = pressed,
            _ => {}
        }
    }

    pub fn handle_mouse_move(&mut self, x: f32, y: f32) {
        self.mouse_pos = (x, y);
        if let Some(last) = self.last_mouse_pos {
            self.mouse_delta = (x - last.0, y - last.1);
        }
        self.last_mouse_pos = Some((x, y));
    }

    pub fn handle_mouse_wheel(&mut self, delta: MouseScrollDelta) {
        self.scroll_delta = match delta {
            MouseScrollDelta::LineDelta(_, y) => y,
            MouseScrollDelta::PixelDelta(position) => position.y as f32 / 100.0,
        };
    }

    pub fn clear_mouse_delta(&mut self) {
        self.mouse_delta = (0.0, 0.0);
    }

    pub fn end_frame(&mut self) {
        self.mouse_delta = (0.0, 0.0);
        self.scroll_delta = 0.0;
    }

    pub fn reset(&mut self) {
        self.key_w = false;
        self.key_a = false;
        self.key_s = false;
        self.key_d = false;
        self.key_q = false;
        self.key_e = false;
        self.left_mouse_down = false;
        self.right_mouse_down = false;
        self.mouse_delta = (0.0, 0.0);
        self.scroll_delta = 0.0;
        self.last_mouse_pos = None;
    }
}

impl Default for InputState {
    fn default() -> Self {
        Self::new()
    }
}
