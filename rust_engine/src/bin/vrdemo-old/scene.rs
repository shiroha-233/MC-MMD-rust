use std::path::Path;

use anyhow::Result;
use glam::Mat4;
use mmd_engine::vrm_runtime::{ArmIkCalibration, HandTrackingCalibration};

use crate::avatar_rig::AvatarRig;
pub use crate::avatar_rig::{ArmIkDebugSnapshot, ModelRenderData, SceneAssets};
use crate::room::DemoRoom;
pub use crate::room::RoomMesh;
use crate::space::SpaceState;
pub use crate::space::{MirrorCamera, TrackingFrame};

pub struct AvatarScene {
    avatar: AvatarRig,
    room: DemoRoom,
    space: SpaceState,
}

impl AvatarScene {
    pub fn new(path: impl AsRef<Path>) -> Result<Self> {
        Ok(Self {
            avatar: AvatarRig::new(path)?,
            room: DemoRoom::default(),
            space: SpaceState::default(),
        })
    }

    pub fn assets(&self) -> &SceneAssets {
        self.avatar.assets()
    }

    pub fn room_meshes(&self) -> &[RoomMesh] {
        self.room.meshes()
    }

    pub fn xr_model_matrix(&self) -> Mat4 {
        self.space.xr_model_matrix()
    }

    pub fn xr_room_matrix(&self) -> Mat4 {
        self.space.room_world_matrix()
    }

    pub fn mirror_model_matrix(&self) -> Mat4 {
        self.space.mirror_model_matrix()
    }

    pub fn mirror_room_matrix(&self) -> Mat4 {
        self.space.room_world_matrix()
    }

    pub fn update(&mut self, tracking: &TrackingFrame, delta_time: f32) {
        self.space.observe_raw_tracking(*tracking);
        self.space.update_locomotion(tracking, delta_time);
        self.space
            .sync_tracking_from_head(tracking.head, self.avatar.current_view_anchor_model());
        let runtime_tracking = self.space.runtime_tracking_with_mode(
            tracking,
            self.avatar.last_tracking(),
            self.avatar.hand_tracking_mode(),
        );
        self.avatar.process(runtime_tracking, delta_time);
        self.space
            .sync_render_from_head(tracking.head, self.avatar.current_view_anchor_model());
    }

    pub fn recenter(&mut self) {
        self.space.recenter(self.avatar.current_view_anchor_model());
    }

    pub fn hmd_render_data(&self) -> ModelRenderData<'_> {
        self.avatar.hmd_render_data()
    }

    pub fn mirror_render_data(&self) -> ModelRenderData<'_> {
        self.avatar.mirror_render_data()
    }

    pub fn mirror_camera(&self, aspect: f32) -> MirrorCamera {
        self.space
            .mirror_camera(aspect, self.avatar.current_view_anchor_model())
    }

    pub fn first_person_camera(&self, aspect: f32) -> Option<MirrorCamera> {
        self.space.first_person_camera(aspect)
    }

    pub fn room_mirror_camera(&self, aspect: f32) -> MirrorCamera {
        self.space
            .room_mirror_camera(aspect, self.avatar.current_view_anchor_model())
    }

    pub fn status_line(&self, runtime_label: &str) -> String {
        let raw = self.space.last_raw_tracking();
        let mut line = self.avatar.status_line(
            runtime_label,
            raw.left_hand.position,
            raw.right_hand.position,
        );
        let teleport = self.space.teleport_target();
        if teleport.valid {
            line.push_str(&format!(
                " teleport=({:.2},{:.2},{:.2})",
                teleport.position.x, teleport.position.y, teleport.position.z
            ));
        }
        line
    }

    pub fn summary_text(&self, runtime_label: &str) -> String {
        self.avatar.summary_text(runtime_label)
    }

    pub fn arm_ik_calibration(&self) -> ArmIkCalibration {
        self.avatar.arm_ik_calibration()
    }

    pub fn hand_calibration(&self) -> HandTrackingCalibration {
        self.avatar.hand_calibration()
    }

    pub fn set_hand_calibration(&mut self, calibration: HandTrackingCalibration) {
        self.avatar.set_hand_calibration(calibration);
    }

    pub fn set_arm_ik_calibration(&mut self, calibration: ArmIkCalibration) {
        self.avatar.set_arm_ik_calibration(calibration);
    }

    pub fn arm_ik_debug_snapshot(&self) -> ArmIkDebugSnapshot {
        self.avatar.arm_ik_debug_snapshot()
    }
}
