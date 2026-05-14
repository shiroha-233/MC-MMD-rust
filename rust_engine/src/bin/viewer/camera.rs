//! 相机控制

use super::input::InputState;
use glam::{Mat4, Quat, Vec3};

pub struct Camera {
    pub position: Vec3,
    pub yaw: f32,
    pub pitch: f32,
    pub fov: f32,
    pub aspect: f32,
    pub near: f32,
    pub far: f32,
    pub move_speed: f32,
    pub look_speed: f32,
    pub zoom_speed: f32,
    pub view_mode: ViewMode,
}

#[derive(Clone, Copy, PartialEq)]
pub enum ViewMode {
    Free,
    Front,
    Side,
    Top,
}

impl Camera {
    pub fn new() -> Self {
        Self {
            position: Vec3::new(0.0, 10.0, 25.0),
            yaw: 0.0,
            pitch: -0.2,
            fov: 45.0_f32.to_radians(),
            aspect: 16.0 / 9.0,
            near: 0.1,
            far: 1000.0,
            move_speed: 10.0,
            look_speed: 0.003,
            zoom_speed: 2.0,
            view_mode: ViewMode::Free,
        }
    }

    pub fn set_aspect(&mut self, aspect: f32) {
        self.aspect = aspect;
    }

    pub fn update(&mut self, input: &InputState, delta_time: f32) {
        // 鼠标右键拖动旋转视角
        if input.right_mouse_down && self.view_mode == ViewMode::Free {
            self.yaw -= input.mouse_delta.0 * self.look_speed;
            self.pitch -= input.mouse_delta.1 * self.look_speed;
            self.pitch = self.pitch.clamp(-1.5, 1.5);
        }

        // 滚轮缩放
        let zoom = input.scroll_delta * self.zoom_speed;
        let forward = self.forward();
        self.position += forward * zoom;

        // WASD 移动
        if self.view_mode == ViewMode::Free {
            let right = self.right();
            let up = Vec3::Y;

            let mut move_dir = Vec3::ZERO;
            if input.key_w {
                move_dir += forward;
            }
            if input.key_s {
                move_dir -= forward;
            }
            if input.key_a {
                move_dir -= right;
            }
            if input.key_d {
                move_dir += right;
            }
            if input.key_q {
                move_dir -= up;
            }
            if input.key_e {
                move_dir += up;
            }

            if move_dir.length_squared() > 0.0 {
                move_dir = move_dir.normalize();
                self.position += move_dir * self.move_speed * delta_time;
            }
        }
    }

    pub fn forward(&self) -> Vec3 {
        let rotation = Quat::from_euler(glam::EulerRot::YXZ, self.yaw, self.pitch, 0.0);
        rotation * Vec3::NEG_Z
    }

    pub fn right(&self) -> Vec3 {
        let rotation = Quat::from_euler(glam::EulerRot::YXZ, self.yaw, 0.0, 0.0);
        rotation * Vec3::X
    }

    pub fn view_matrix(&self) -> Mat4 {
        match self.view_mode {
            ViewMode::Free => {
                Mat4::look_at_rh(self.position, self.position + self.forward(), Vec3::Y)
            }
            ViewMode::Front => Mat4::look_at_rh(
                Vec3::new(0.0, 10.0, 30.0),
                Vec3::new(0.0, 10.0, 0.0),
                Vec3::Y,
            ),
            ViewMode::Side => Mat4::look_at_rh(
                Vec3::new(30.0, 10.0, 0.0),
                Vec3::new(0.0, 10.0, 0.0),
                Vec3::Y,
            ),
            ViewMode::Top => Mat4::look_at_rh(
                Vec3::new(0.0, 40.0, 0.01),
                Vec3::new(0.0, 0.0, 0.0),
                Vec3::NEG_Z,
            ),
        }
    }

    pub fn projection_matrix(&self) -> Mat4 {
        Mat4::perspective_rh_gl(self.fov, self.aspect, self.near, self.far)
    }

    pub fn set_view_front(&mut self) {
        self.view_mode = ViewMode::Front;
        println!("视图: 正面");
    }

    pub fn set_view_side(&mut self) {
        self.view_mode = ViewMode::Side;
        println!("视图: 侧面");
    }

    pub fn set_view_top(&mut self) {
        self.view_mode = ViewMode::Top;
        println!("视图: 顶部");
    }

    pub fn set_view_free(&mut self) {
        self.view_mode = ViewMode::Free;
        println!("视图: 自由");
    }
}

impl Default for Camera {
    fn default() -> Self {
        Self::new()
    }
}
