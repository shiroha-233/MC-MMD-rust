use glam::{Mat4, Quat, Vec2, Vec3};
use mmd_engine::vrm_runtime::{TrackedPose as RuntimePose, VrmTrackingInput};

use crate::avatar_rig::{DemoHandTrackingMode, MODEL_TO_WORLD_SCALE};
use crate::room::{ROOM_FLOOR_Y, ROOM_HALF_EXTENT, ROOM_MIRROR_SURFACE_Z};
use crate::xr::{ActionButtons, TrackedPose};

const MIRROR_CAMERA_FOV_Y: f32 = 36.0_f32.to_radians();
const DESKTOP_FIRST_PERSON_FOV_Y: f32 = 72.0_f32.to_radians();
const MIRROR_CAMERA_NEAR: f32 = 0.05;
const MIRROR_CAMERA_FAR: f32 = 50.0;
const MODEL_FORWARD_CORRECTION: f32 = std::f32::consts::PI;
const SNAP_TURN_ANGLE_RAD: f32 = std::f32::consts::FRAC_PI_4;
const MIRROR_CAMERA_EYE_OFFSET_AVATAR: Vec3 = Vec3::new(-0.35, 0.18, -2.65);
const MOVE_SPEED_METERS_PER_SECOND: f32 = 1.8;
const ROOM_HEAD_MARGIN: f32 = 0.12;

#[derive(Clone, Copy, Debug, Default)]
pub struct TrackingFrame {
    pub head: TrackedPose,
    pub left_grip: TrackedPose,
    pub left_aim: TrackedPose,
    pub left_hand: TrackedPose,
    pub right_grip: TrackedPose,
    pub right_aim: TrackedPose,
    pub right_hand: TrackedPose,
    pub buttons: ActionButtons,
}

#[derive(Clone, Copy, Debug)]
pub struct MirrorCamera {
    pub view_proj: Mat4,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct TeleportTarget {
    pub position: Vec3,
    pub valid: bool,
}

#[derive(Clone, Copy, Debug)]
struct Calibration {
    tracking_space_origin_world: Vec3,
    tracking_space_yaw_world: Quat,
    tracking_root_origin_world: Vec3,
    render_root_origin_world: Vec3,
    avatar_body_yaw_world: Quat,
    tracking_anchor_model: Vec3,
    view_anchor_model: Vec3,
    mirror_root_world: Vec3,
    calibrated: bool,
}

impl Default for Calibration {
    fn default() -> Self {
        Self {
            tracking_space_origin_world: Vec3::ZERO,
            tracking_space_yaw_world: Quat::IDENTITY,
            tracking_root_origin_world: Vec3::ZERO,
            render_root_origin_world: Vec3::ZERO,
            avatar_body_yaw_world: Quat::IDENTITY,
            tracking_anchor_model: Vec3::ZERO,
            view_anchor_model: Vec3::ZERO,
            mirror_root_world: Vec3::ZERO,
            calibrated: false,
        }
    }
}

pub struct SpaceState {
    calibration: Calibration,
    last_raw_tracking: TrackingFrame,
    teleport_target: TeleportTarget,
}

impl Default for SpaceState {
    fn default() -> Self {
        Self {
            calibration: Calibration::default(),
            last_raw_tracking: TrackingFrame::default(),
            teleport_target: TeleportTarget::default(),
        }
    }
}

impl SpaceState {
    pub fn observe_raw_tracking(&mut self, tracking: TrackingFrame) {
        self.last_raw_tracking = tracking;
    }

    pub fn update_locomotion(&mut self, tracking: &TrackingFrame, delta_time: f32) {
        if tracking.buttons.snap_turn_left {
            self.apply_snap_turn(tracking.head, SNAP_TURN_ANGLE_RAD);
        } else if tracking.buttons.snap_turn_right {
            self.apply_snap_turn(tracking.head, -SNAP_TURN_ANGLE_RAD);
        }

        if tracking.buttons.teleport_aim_active {
            self.teleport_target = self.resolve_teleport_target(*tracking).unwrap_or_default();
            return;
        }

        if tracking.buttons.teleport_confirm && self.teleport_target.valid {
            self.apply_teleport(tracking.head, self.teleport_target.position);
        }
        self.teleport_target = TeleportTarget::default();

        self.apply_smooth_locomotion(tracking.head, tracking.buttons.move_axis, delta_time);
    }

    pub fn sync_tracking_from_head(&mut self, head: TrackedPose, current_view_anchor_model: Vec3) {
        let head_world = self.pose_in_world(head);
        if !head_world.valid {
            return;
        }
        if !self.calibration.calibrated {
            self.recenter_from_head(head_world, current_view_anchor_model);
            return;
        }
        self.calibration.tracking_anchor_model = current_view_anchor_model;
        self.update_tracking_root_from_head(head_world);
    }

    pub fn sync_render_from_head(&mut self, head: TrackedPose, current_view_anchor_model: Vec3) {
        let head_world = self.pose_in_world(head);
        if !head_world.valid {
            return;
        }
        if !self.calibration.calibrated {
            self.recenter_from_head(head_world, current_view_anchor_model);
            return;
        }
        self.update_render_root_from_head(head_world, current_view_anchor_model);
    }

    pub fn recenter(&mut self, current_view_anchor_model: Vec3) {
        if self.last_raw_tracking.head.valid {
            self.recenter_from_head(
                self.pose_in_world(self.last_raw_tracking.head),
                current_view_anchor_model,
            );
        }
    }

    fn recenter_from_head(&mut self, head_world: TrackedPose, current_view_anchor_model: Vec3) {
        let viewer_forward = planar_forward(head_world.orientation);
        let mirror_head_world = head_world.position + viewer_forward * 1.35;
        let avatar_body_yaw_world = yaw_from_head(head_world.orientation);

        self.calibration.avatar_body_yaw_world = avatar_body_yaw_world;
        self.calibration.tracking_anchor_model = current_view_anchor_model;
        self.calibration.view_anchor_model = current_view_anchor_model;
        self.calibration.tracking_root_origin_world = resolve_avatar_root_origin_world(
            head_world.position,
            self.calibration.avatar_body_yaw_world,
            self.calibration.tracking_anchor_model,
        );
        self.calibration.render_root_origin_world = resolve_avatar_root_origin_world(
            head_world.position,
            self.calibration.avatar_body_yaw_world,
            self.calibration.view_anchor_model,
        );
        self.calibration.mirror_root_world =
            Vec3::new(mirror_head_world.x, ROOM_FLOOR_Y, mirror_head_world.z);
        self.calibration.calibrated = true;
        log::info!(
            "VR 角色已重定中心 head=({:.2},{:.2},{:.2}) tracking_root=({:.2},{:.2},{:.2}) render_root=({:.2},{:.2},{:.2}) mirror_root=({:.2},{:.2},{:.2})",
            head_world.position.x,
            head_world.position.y,
            head_world.position.z,
            self.calibration.tracking_root_origin_world.x,
            self.calibration.tracking_root_origin_world.y,
            self.calibration.tracking_root_origin_world.z,
            self.calibration.render_root_origin_world.x,
            self.calibration.render_root_origin_world.y,
            self.calibration.render_root_origin_world.z,
            self.calibration.mirror_root_world.x,
            self.calibration.mirror_root_world.y,
            self.calibration.mirror_root_world.z,
        );
    }

    fn update_tracking_root_from_head(&mut self, head_world: TrackedPose) {
        self.calibration.avatar_body_yaw_world = yaw_from_head(head_world.orientation);
        self.calibration.tracking_root_origin_world = resolve_avatar_root_origin_world(
            head_world.position,
            self.calibration.avatar_body_yaw_world,
            self.calibration.tracking_anchor_model,
        );
    }

    fn update_render_root_from_head(
        &mut self,
        head_world: TrackedPose,
        current_view_anchor_model: Vec3,
    ) {
        self.calibration.avatar_body_yaw_world = yaw_from_head(head_world.orientation);
        self.calibration.view_anchor_model = current_view_anchor_model;
        self.calibration.render_root_origin_world = resolve_avatar_root_origin_world(
            head_world.position,
            self.calibration.avatar_body_yaw_world,
            self.calibration.view_anchor_model,
        );
    }

    pub fn xr_model_matrix(&self) -> Mat4 {
        compose_tracking_root_matrix(
            self.calibration.render_root_origin_world,
            self.calibration.avatar_body_yaw_world,
        )
    }

    pub fn room_world_matrix(&self) -> Mat4 {
        Mat4::from_translation(self.calibration.tracking_space_origin_world)
            * Mat4::from_quat(self.calibration.tracking_space_yaw_world)
    }

    pub fn mirror_model_matrix(&self) -> Mat4 {
        compose_tracking_root_matrix(
            self.calibration.mirror_root_world,
            self.calibration.avatar_body_yaw_world,
        )
    }

    pub fn mirror_camera(&self, aspect: f32, current_view_anchor_model: Vec3) -> MirrorCamera {
        let target = self.mirror_head_target_world(current_view_anchor_model);
        let eye = target + self.mirror_camera_eye_offset_world();
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

    pub fn first_person_camera(&self, aspect: f32) -> Option<MirrorCamera> {
        if !self.calibration.calibrated {
            return None;
        }

        let head_world = self.pose_in_world(self.last_raw_tracking.head);
        if !head_world.valid {
            return None;
        }

        let view =
            Mat4::from_rotation_translation(head_world.orientation, head_world.position).inverse();
        let projection = Mat4::perspective_rh(
            DESKTOP_FIRST_PERSON_FOV_Y,
            aspect.max(0.1),
            MIRROR_CAMERA_NEAR,
            MIRROR_CAMERA_FAR,
        );
        Some(MirrorCamera {
            view_proj: projection * view,
        })
    }

    pub fn room_mirror_camera(&self, aspect: f32, current_view_anchor_model: Vec3) -> MirrorCamera {
        if let Some((eye, forward, up)) = self.room_mirror_view_pose() {
            let view = Mat4::look_at_rh(eye, eye + forward, up);
            let projection = Mat4::perspective_rh(
                MIRROR_CAMERA_FOV_Y,
                aspect.max(0.1),
                MIRROR_CAMERA_NEAR,
                MIRROR_CAMERA_FAR,
            );
            MirrorCamera {
                view_proj: projection * view,
            }
        } else {
            self.mirror_camera(aspect, current_view_anchor_model)
        }
    }

    fn mirror_head_target_world(&self, current_view_anchor_model: Vec3) -> Vec3 {
        self.mirror_model_matrix()
            .transform_point3(current_view_anchor_model)
    }

    fn mirror_camera_eye_offset_world(&self) -> Vec3 {
        let root_rotation = (self.calibration.avatar_body_yaw_world
            * Quat::from_rotation_y(MODEL_FORWARD_CORRECTION))
        .normalize();
        root_rotation * MIRROR_CAMERA_EYE_OFFSET_AVATAR
    }

    fn room_mirror_view_pose(&self) -> Option<(Vec3, Vec3, Vec3)> {
        if !self.calibration.calibrated || !self.last_raw_tracking.head.valid {
            return None;
        }

        let head_world = self.pose_in_world(self.last_raw_tracking.head);
        if !head_world.valid {
            return None;
        }

        let (plane_point, plane_normal) = self.room_mirror_plane_world();
        let reflected_eye =
            reflect_point_across_plane(head_world.position, plane_point, plane_normal);
        let reflected_forward =
            reflect_direction_across_plane(head_world.orientation * Vec3::NEG_Z, plane_normal);
        let reflected_up =
            reflect_direction_across_plane(head_world.orientation * Vec3::Y, plane_normal);
        let forward = reflected_forward.normalize_or_zero();
        let up = reflected_up.normalize_or_zero();
        let resolved_forward = if forward.length_squared() > 1e-6 {
            forward
        } else {
            plane_normal
        };
        let resolved_up = if up.length_squared() > 1e-6 {
            up
        } else {
            Vec3::Y
        };

        Some((reflected_eye, resolved_forward, resolved_up))
    }

    fn room_mirror_plane_world(&self) -> (Vec3, Vec3) {
        let room_world = self.room_world_matrix();
        let plane_point = room_world.transform_point3(Vec3::new(0.0, 0.0, ROOM_MIRROR_SURFACE_Z));
        let plane_normal = room_world.transform_vector3(Vec3::Z).normalize_or_zero();
        let resolved_normal = if plane_normal.length_squared() > 1e-6 {
            plane_normal
        } else {
            Vec3::Z
        };
        (plane_point, resolved_normal)
    }

    pub fn runtime_tracking_with_mode(
        &self,
        tracking: &TrackingFrame,
        last_tracking: VrmTrackingInput,
        hand_tracking_mode: DemoHandTrackingMode,
    ) -> VrmTrackingInput {
        let head = self.resolve_head_pose(self.anchor_pose(tracking.head), last_tracking.head);
        let (left_hand_pose, right_hand_pose) = match hand_tracking_mode {
            DemoHandTrackingMode::VrmLegacyGripAim => (
                preferred_hand_pose(tracking.left_grip, tracking.left_aim),
                preferred_hand_pose(tracking.right_grip, tracking.right_aim),
            ),
            DemoHandTrackingMode::PmxControllerPose => (tracking.left_hand, tracking.right_hand),
        };
        let right = self.resolve_hand_pose(self.anchor_pose(right_hand_pose), head, 1.0);
        let left = self.resolve_hand_pose(self.anchor_pose(left_hand_pose), head, -1.0);
        VrmTrackingInput {
            head,
            left_hand: left,
            right_hand: right,
        }
    }

    pub fn last_raw_tracking(&self) -> TrackingFrame {
        self.last_raw_tracking
    }

    pub fn teleport_target(&self) -> TeleportTarget {
        self.teleport_target
    }

    fn anchor_pose(&self, pose: TrackedPose) -> RuntimePose {
        let world_pose = self.pose_in_world(pose);
        if !world_pose.valid || !self.calibration.calibrated {
            return runtime_pose_from_xr(world_pose);
        }

        let world_to_avatar_yaw = self.calibration.avatar_body_yaw_world.inverse();

        RuntimePose {
            position: world_to_avatar_yaw
                * (world_pose.position - self.calibration.tracking_root_origin_world),
            orientation: (world_to_avatar_yaw * world_pose.orientation).normalize(),
            valid: true,
        }
    }

    fn pose_in_world(&self, pose: TrackedPose) -> TrackedPose {
        if !pose.valid {
            return pose;
        }

        TrackedPose {
            position: self.calibration.tracking_space_origin_world
                + self.calibration.tracking_space_yaw_world * pose.position,
            orientation: (self.calibration.tracking_space_yaw_world * pose.orientation).normalize(),
            valid: true,
        }
    }

    fn resolve_teleport_target(&self, tracking: TrackingFrame) -> Option<TeleportTarget> {
        let aim = self.pose_in_world(tracking.right_aim);
        if !aim.valid {
            return None;
        }

        let direction = aim.orientation * Vec3::NEG_Z;
        if direction.length_squared() <= 1e-6 || direction.y.abs() <= 1e-4 {
            return None;
        }

        let t = (ROOM_FLOOR_Y - aim.position.y) / direction.y;
        if t <= 0.0 {
            return None;
        }

        let hit = aim.position + direction * t;
        if hit.x.abs() > ROOM_HALF_EXTENT || hit.z.abs() > ROOM_HALF_EXTENT {
            return None;
        }

        Some(TeleportTarget {
            position: Vec3::new(hit.x, ROOM_FLOOR_Y, hit.z),
            valid: true,
        })
    }

    fn apply_teleport(&mut self, head: TrackedPose, target: Vec3) {
        if !head.valid {
            return;
        }

        let rotated_head = self.calibration.tracking_space_yaw_world * head.position;
        self.calibration.tracking_space_origin_world.x = target.x - rotated_head.x;
        self.calibration.tracking_space_origin_world.y = ROOM_FLOOR_Y;
        self.calibration.tracking_space_origin_world.z = target.z - rotated_head.z;
    }

    fn apply_snap_turn(&mut self, head: TrackedPose, delta_radians: f32) {
        if !head.valid {
            return;
        }

        let current_head_world = self.pose_in_world(head).position;
        let delta = Quat::from_rotation_y(delta_radians);
        self.calibration.tracking_space_yaw_world =
            (delta * self.calibration.tracking_space_yaw_world).normalize();
        let rotated_head = self.calibration.tracking_space_yaw_world * head.position;
        self.calibration.tracking_space_origin_world = current_head_world - rotated_head;
    }

    fn apply_smooth_locomotion(&mut self, head: TrackedPose, move_axis: Vec2, delta_time: f32) {
        if !head.valid || move_axis == Vec2::ZERO || delta_time <= 0.0 {
            return;
        }

        let head_world = self.pose_in_world(head);
        let forward = planar_forward(head_world.orientation);
        let right = Vec3::new(-forward.z, 0.0, forward.x).normalize_or_zero();
        let movement_direction = right * move_axis.x + forward * move_axis.y;
        if movement_direction.length_squared() <= 1e-6 {
            return;
        }

        let movement = movement_direction.normalize() * MOVE_SPEED_METERS_PER_SECOND * delta_time;
        let rotated_head = self.calibration.tracking_space_yaw_world * head.position;
        let next_head_world = head_world.position + movement;
        let clamped_head_world = Vec3::new(
            next_head_world.x.clamp(
                -ROOM_HALF_EXTENT + ROOM_HEAD_MARGIN,
                ROOM_HALF_EXTENT - ROOM_HEAD_MARGIN,
            ),
            next_head_world.y,
            next_head_world.z.clamp(
                -ROOM_HALF_EXTENT + ROOM_HEAD_MARGIN,
                ROOM_HALF_EXTENT - ROOM_HEAD_MARGIN,
            ),
        );
        self.calibration.tracking_space_origin_world = clamped_head_world - rotated_head;
    }

    fn resolve_head_pose(&self, head: RuntimePose, fallback: RuntimePose) -> RuntimePose {
        if head.valid {
            head
        } else if fallback.valid {
            fallback
        } else {
            RuntimePose::identity()
        }
    }

    fn resolve_hand_pose(&self, hand: RuntimePose, head: RuntimePose, side: f32) -> RuntimePose {
        if hand.valid {
            hand
        } else if head.valid {
            let fallback_offset = Vec3::new(0.23 * side, -0.28, -0.34);
            RuntimePose {
                position: head.position + head.orientation * fallback_offset,
                orientation: head.orientation,
                valid: true,
            }
        } else {
            RuntimePose::identity()
        }
    }
}

fn runtime_pose_from_xr(pose: TrackedPose) -> RuntimePose {
    RuntimePose {
        position: pose.position,
        orientation: pose.orientation,
        valid: pose.valid,
    }
}

fn yaw_from_head(orientation: Quat) -> Quat {
    let forward = planar_forward(orientation);
    let yaw = (-forward.x).atan2(-forward.z);
    Quat::from_rotation_y(yaw)
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

fn resolve_avatar_root_origin_world(
    head_world: Vec3,
    avatar_body_yaw_world: Quat,
    view_anchor_model: Vec3,
) -> Vec3 {
    let root_rotation =
        (avatar_body_yaw_world * Quat::from_rotation_y(MODEL_FORWARD_CORRECTION)).normalize();
    head_world - root_rotation * (view_anchor_model * MODEL_TO_WORLD_SCALE)
}

fn reflect_point_across_plane(point: Vec3, plane_point: Vec3, plane_normal: Vec3) -> Vec3 {
    let normal = plane_normal.normalize_or_zero();
    if normal.length_squared() <= 1e-6 {
        return point;
    }
    point - 2.0 * (point - plane_point).dot(normal) * normal
}

fn reflect_direction_across_plane(direction: Vec3, plane_normal: Vec3) -> Vec3 {
    let normal = plane_normal.normalize_or_zero();
    if normal.length_squared() <= 1e-6 {
        return direction;
    }
    direction - 2.0 * direction.dot(normal) * normal
}

fn preferred_hand_pose(primary: TrackedPose, fallback: TrackedPose) -> TrackedPose {
    if primary.valid {
        primary
    } else if fallback.valid {
        fallback
    } else {
        TrackedPose::identity()
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
    fn yaw_from_head_should_leave_identity_unchanged() {
        let yaw = yaw_from_head(Quat::IDENTITY);
        let forward = yaw * Vec3::NEG_Z;

        assert_vec3_near(forward, Vec3::NEG_Z, 1e-5);
    }

    #[test]
    fn resolve_avatar_root_origin_world_should_place_view_anchor_at_head_position() {
        let head_world = Vec3::new(2.0, 1.6, -0.5);
        let yaw = Quat::from_rotation_y(0.35);
        let view_anchor_model = Vec3::new(0.0, 18.0, 1.5);
        let origin = resolve_avatar_root_origin_world(head_world, yaw, view_anchor_model);
        let matrix = compose_tracking_root_matrix(origin, yaw);
        let solved_head_world = matrix.transform_point3(view_anchor_model);

        assert_vec3_near(solved_head_world, head_world, 1e-5);
    }

    #[test]
    fn observe_raw_tracking_should_preserve_expanded_xr_fields() {
        let mut state = SpaceState::default();
        let tracking = sample_tracking_frame();

        state.observe_raw_tracking(tracking);
        let raw = state.last_raw_tracking();

        assert_vec3_near(raw.left_grip.position, tracking.left_grip.position, 1e-5);
        assert_vec3_near(raw.left_aim.position, tracking.left_aim.position, 1e-5);
        assert_vec3_near(raw.right_grip.position, tracking.right_grip.position, 1e-5);
        assert_vec3_near(raw.right_aim.position, tracking.right_aim.position, 1e-5);
        assert!(raw.buttons.teleport_aim_active);
        assert!(raw.buttons.teleport_confirm);
        assert!(raw.buttons.snap_turn_left);
        assert!(!raw.buttons.snap_turn_right);
    }

    #[test]
    fn runtime_tracking_should_keep_left_and_right_hand_semantics() {
        let state = SpaceState::default();
        let tracking = sample_tracking_frame();

        let runtime_tracking = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );

        assert_vec3_near(
            runtime_tracking.left_hand.position,
            tracking.left_hand.position,
            1e-5,
        );
        assert_vec3_near(
            runtime_tracking.right_hand.position,
            tracking.right_hand.position,
            1e-5,
        );
    }

    #[test]
    fn runtime_tracking_should_keep_synthesized_hand_fallback_valid() {
        let state = SpaceState::default();
        let head =
            tracked_pose_with_orientation(Vec3::new(0.1, 1.65, -0.2), Quat::from_rotation_y(0.45));
        let tracking = TrackingFrame {
            head,
            right_hand: TrackedPose {
                valid: false,
                ..TrackedPose::default()
            },
            ..sample_tracking_frame()
        };

        let runtime_tracking = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );
        let expected_offset = head.orientation * Vec3::new(0.23, -0.28, -0.34);

        assert!(runtime_tracking.right_hand.valid);
        assert_vec3_near(
            runtime_tracking.right_hand.position,
            head.position + expected_offset,
            1e-5,
        );
        assert_quat_near(
            runtime_tracking.right_hand.orientation,
            head.orientation,
            1e-5,
        );
    }

    #[test]
    fn runtime_tracking_should_support_vrm_legacy_grip_pose_source() {
        let state = SpaceState::default();
        let tracking = TrackingFrame {
            left_grip: tracked_pose_with_orientation(
                Vec3::new(-0.4, 1.1, -0.2),
                Quat::from_rotation_y(0.35),
            ),
            left_aim: tracked_pose_with_orientation(
                Vec3::new(-0.1, 1.3, 0.5),
                Quat::from_rotation_x(-0.8),
            ),
            right_grip: tracked_pose_with_orientation(
                Vec3::new(0.4, 1.1, -0.2),
                Quat::from_rotation_y(-0.45),
            ),
            right_aim: tracked_pose_with_orientation(
                Vec3::new(0.2, 1.4, 0.6),
                Quat::from_rotation_z(0.7),
            ),
            left_hand: tracked_pose_with_orientation(
                Vec3::new(-9.0, -9.0, -9.0),
                Quat::from_rotation_x(1.0),
            ),
            right_hand: tracked_pose_with_orientation(
                Vec3::new(9.0, 9.0, 9.0),
                Quat::from_rotation_z(-1.0),
            ),
            ..sample_tracking_frame()
        };

        let runtime_tracking = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::VrmLegacyGripAim,
        );

        assert_vec3_near(
            runtime_tracking.left_hand.position,
            tracking.left_grip.position,
            1e-5,
        );
        assert_quat_near(
            runtime_tracking.left_hand.orientation,
            tracking.left_grip.orientation,
            1e-5,
        );
        assert_vec3_near(
            runtime_tracking.right_hand.position,
            tracking.right_grip.position,
            1e-5,
        );
        assert_quat_near(
            runtime_tracking.right_hand.orientation,
            tracking.right_grip.orientation,
            1e-5,
        );
    }

    #[test]
    fn teleport_confirm_should_move_head_projection_to_target() {
        let mut state = SpaceState::default();
        let mut tracking = sample_tracking_frame();
        tracking.head = tracked_pose(Vec3::new(0.2, 1.6, 0.1));
        tracking.right_aim =
            tracked_pose_with_orientation(Vec3::new(0.1, 1.4, -0.2), Quat::from_rotation_x(-0.9));
        tracking.buttons.teleport_aim_active = true;
        tracking.buttons.teleport_confirm = false;

        state.update_locomotion(&tracking, 0.016);
        let target = state.teleport_target();
        assert!(target.valid);

        tracking.buttons.teleport_aim_active = false;
        tracking.buttons.teleport_confirm = true;
        state.update_locomotion(&tracking, 0.016);

        let head_world = state.pose_in_world(tracking.head).position;
        assert_vec3_near(
            Vec3::new(head_world.x, ROOM_FLOOR_Y, head_world.z),
            target.position,
            1e-5,
        );
    }

    #[test]
    fn snap_turn_should_preserve_head_world_position() {
        let mut state = SpaceState::default();
        let tracking = sample_tracking_frame();
        let before = state.pose_in_world(tracking.head).position;

        state.apply_snap_turn(tracking.head, SNAP_TURN_ANGLE_RAD);
        let after = state.pose_in_world(tracking.head).position;

        assert_vec3_near(after, before, 1e-5);
    }

    #[test]
    fn runtime_tracking_should_remain_stable_under_snap_turn() {
        let mut state = SpaceState::default();
        let tracking = sample_tracking_frame();
        let anchor = Vec3::new(0.0, 18.0, 0.0);

        state.sync_tracking_from_head(tracking.head, anchor);

        let before = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );
        state.apply_snap_turn(tracking.head, SNAP_TURN_ANGLE_RAD);
        state.sync_tracking_from_head(tracking.head, anchor);
        let after = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );

        assert_vec3_near(before.head.position, after.head.position, 1e-5);
        assert_vec3_near(before.left_hand.position, after.left_hand.position, 1e-5);
        assert_vec3_near(before.right_hand.position, after.right_hand.position, 1e-5);
        assert_quat_near(before.head.orientation, after.head.orientation, 1e-5);
        assert_quat_near(
            before.left_hand.orientation,
            after.left_hand.orientation,
            1e-5,
        );
        assert_quat_near(
            before.right_hand.orientation,
            after.right_hand.orientation,
            1e-5,
        );
    }

    #[test]
    fn sync_render_from_head_should_only_realign_render_root_when_view_anchor_changes() {
        let mut state = SpaceState::default();
        let head = tracked_pose_with_orientation(Vec3::new(0.0, 1.6, 0.0), Quat::IDENTITY);
        let initial_anchor = Vec3::new(0.0, 18.0, 0.0);
        let changed_anchor = Vec3::new(0.35, 18.0, -0.25);

        state.sync_tracking_from_head(head, initial_anchor);
        let tracking_origin_before = state.calibration.tracking_root_origin_world;
        let render_origin_before = state.calibration.render_root_origin_world;

        state.sync_render_from_head(head, changed_anchor);
        let tracking_origin_after = state.calibration.tracking_root_origin_world;
        let render_origin_after = state.calibration.render_root_origin_world;

        assert!(
            (render_origin_after - render_origin_before).length() > 1e-5,
            "render root should be recomputed when the runtime anchor changes"
        );
        assert_vec3_near(tracking_origin_after, tracking_origin_before, 1e-5);
        assert_vec3_near(
            state.calibration.tracking_anchor_model,
            initial_anchor,
            1e-5,
        );
        assert_vec3_near(state.calibration.view_anchor_model, changed_anchor, 1e-5);
    }

    #[test]
    fn sync_tracking_from_head_should_move_tracking_root_with_tracked_head() {
        let mut state = SpaceState::default();
        let initial_head = tracked_pose_with_orientation(Vec3::new(0.0, 1.6, 0.0), Quat::IDENTITY);
        let moved_head =
            tracked_pose_with_orientation(Vec3::new(0.12, 1.6, -0.08), Quat::from_rotation_y(0.6));
        let anchor = Vec3::new(0.0, 18.0, 0.0);

        state.sync_tracking_from_head(initial_head, anchor);
        let origin_before = state.calibration.tracking_root_origin_world;

        state.sync_tracking_from_head(moved_head, anchor);
        let origin_after = state.calibration.tracking_root_origin_world;

        assert!(
            (origin_after - origin_before).length() > 1e-5,
            "tracking root should follow tracked head translation"
        );
    }

    #[test]
    fn sync_tracking_from_head_should_accept_latest_anchor_for_next_frame_input_root() {
        let mut state = SpaceState::default();
        let head = tracked_pose_with_orientation(Vec3::new(0.0, 1.6, 0.0), Quat::IDENTITY);
        let initial_anchor = Vec3::new(0.0, 18.0, 0.0);
        let changed_anchor = Vec3::new(0.35, 18.0, -0.25);

        state.sync_tracking_from_head(head, initial_anchor);
        let origin_before = state.calibration.tracking_root_origin_world;

        state.sync_tracking_from_head(head, changed_anchor);
        let origin_after = state.calibration.tracking_root_origin_world;

        assert!(
            (origin_after - origin_before).length() > 1e-5,
            "tracking root should update when the latest anchor from the previous frame changes"
        );
        assert_vec3_near(
            state.calibration.tracking_anchor_model,
            changed_anchor,
            1e-5,
        );
    }

    #[test]
    fn xr_model_matrix_should_keep_view_anchor_locked_to_head_world_position() {
        let mut state = SpaceState::default();
        let anchor = Vec3::new(0.15, 18.1, -0.2);
        let initial_head = tracked_pose_with_orientation(Vec3::new(0.0, 1.6, 0.0), Quat::IDENTITY);
        let moved_head =
            tracked_pose_with_orientation(Vec3::new(0.18, 1.72, -0.11), Quat::from_rotation_y(0.4));

        state.sync_tracking_from_head(initial_head, anchor);
        state.sync_render_from_head(initial_head, anchor);
        state.sync_tracking_from_head(moved_head, anchor);
        state.sync_render_from_head(moved_head, anchor);

        let locked_head_world = state.xr_model_matrix().transform_point3(anchor);
        let expected_head_world = state.pose_in_world(moved_head).position;

        assert_vec3_near(locked_head_world, expected_head_world, 1e-5);
    }

    #[test]
    fn xr_model_matrix_should_rotate_with_snap_turn() {
        let mut state = SpaceState::default();
        let tracking = sample_tracking_frame();
        let anchor = Vec3::new(0.0, 18.0, 0.0);

        state.sync_tracking_from_head(tracking.head, anchor);
        state.sync_render_from_head(tracking.head, anchor);
        state.apply_snap_turn(tracking.head, SNAP_TURN_ANGLE_RAD);
        state.sync_tracking_from_head(tracking.head, anchor);
        state.sync_render_from_head(tracking.head, anchor);
        let matrix = state.xr_model_matrix();
        let forward = matrix.transform_vector3(Vec3::NEG_Z).normalize();

        assert_vec3_near(
            forward,
            Quat::from_rotation_y(SNAP_TURN_ANGLE_RAD + MODEL_FORWARD_CORRECTION) * Vec3::NEG_Z,
            1e-5,
        );
    }

    #[test]
    fn mirror_head_target_should_follow_mirror_model_transform() {
        let mut state = SpaceState::default();
        state.calibration.calibrated = true;
        state.calibration.mirror_root_world = Vec3::new(1.5, 0.0, -2.0);
        state.calibration.avatar_body_yaw_world = Quat::from_rotation_y(0.5);
        let anchor = Vec3::new(0.0, 18.0, 1.5);

        let actual = state.mirror_head_target_world(anchor);
        let expected = state.mirror_model_matrix().transform_point3(anchor);

        assert_vec3_near(actual, expected, 1e-5);
    }

    #[test]
    fn mirror_camera_should_orbit_with_avatar_body_yaw() {
        let mut state = SpaceState::default();
        state.calibration.calibrated = true;
        state.calibration.mirror_root_world = Vec3::new(0.0, 0.0, 0.0);
        let anchor = Vec3::new(0.0, 18.0, 0.0);

        state.calibration.avatar_body_yaw_world = Quat::IDENTITY;
        let offset_before = state.mirror_camera_eye_offset_world();

        state.calibration.avatar_body_yaw_world =
            Quat::from_rotation_y(std::f32::consts::FRAC_PI_2);
        let offset_after = state.mirror_camera_eye_offset_world();

        assert!(
            (offset_after - offset_before).length() > 1e-5,
            "mirror camera eye offset should rotate with avatar yaw"
        );
        let target = state.mirror_head_target_world(anchor);
        let eye = target + offset_after;
        let expected_offset =
            (Quat::from_rotation_y(std::f32::consts::FRAC_PI_2 + MODEL_FORWARD_CORRECTION)
                * MIRROR_CAMERA_EYE_OFFSET_AVATAR)
                .normalize()
                * MIRROR_CAMERA_EYE_OFFSET_AVATAR.length();
        assert_vec3_near(eye - target, expected_offset, 1e-5);
    }

    #[test]
    fn room_mirror_view_pose_should_reflect_head_position_and_forward_across_front_wall() {
        let mut state = SpaceState::default();
        state.calibration.calibrated = true;
        state.last_raw_tracking.head =
            tracked_pose_with_orientation(Vec3::new(0.2, 1.6, 0.4), Quat::IDENTITY);

        let (eye, forward, up) = state
            .room_mirror_view_pose()
            .expect("room mirror pose should exist");
        let plane_z = ROOM_MIRROR_SURFACE_Z;

        assert_vec3_near(eye, Vec3::new(0.2, 1.6, 2.0 * plane_z - 0.4), 1e-5);
        assert_vec3_near(forward, Vec3::Z, 1e-5);
        assert_vec3_near(up, Vec3::Y, 1e-5);
    }

    #[test]
    fn room_mirror_view_pose_should_follow_tracking_space_yaw() {
        let mut state = SpaceState::default();
        state.calibration.calibrated = true;
        state.calibration.tracking_space_yaw_world =
            Quat::from_rotation_y(std::f32::consts::FRAC_PI_2);
        state.last_raw_tracking.head =
            tracked_pose_with_orientation(Vec3::new(0.2, 1.6, 0.0), Quat::IDENTITY);

        let (_, forward, _) = state
            .room_mirror_view_pose()
            .expect("room mirror pose should exist");

        assert_vec3_near(forward, Vec3::X, 1e-5);
    }

    #[test]
    fn first_person_camera_should_match_head_world_pose() {
        let mut state = SpaceState::default();
        state.calibration.calibrated = true;
        state.calibration.tracking_space_origin_world = Vec3::new(0.4, 0.2, -0.6);
        state.calibration.tracking_space_yaw_world = Quat::from_rotation_y(0.35);
        state.last_raw_tracking.head = tracked_pose_with_orientation(
            Vec3::new(0.25, 1.55, -0.15),
            Quat::from_rotation_y(-0.2),
        );

        let head_world = state.pose_in_world(state.last_raw_tracking.head);
        let camera = state
            .first_person_camera(16.0_f32 / 9.0_f32)
            .expect("first-person camera should exist");
        let projection = Mat4::perspective_rh(
            DESKTOP_FIRST_PERSON_FOV_Y,
            (16.0_f32 / 9.0_f32).max(0.1),
            MIRROR_CAMERA_NEAR,
            MIRROR_CAMERA_FAR,
        );
        let view = projection.inverse() * camera.view_proj;
        let camera_world = view.inverse();
        let eye = camera_world.transform_point3(Vec3::ZERO);
        let forward = camera_world.transform_vector3(Vec3::NEG_Z).normalize();

        assert_vec3_near(eye, head_world.position, 1e-5);
        assert_vec3_near(
            forward,
            (head_world.orientation * Vec3::NEG_Z).normalize(),
            1e-5,
        );
    }

    #[test]
    fn sync_render_from_head_should_rotate_avatar_root_with_current_head_yaw() {
        let mut state = SpaceState::default();
        let anchor = Vec3::new(0.0, 18.0, 0.0);
        let head =
            tracked_pose_with_orientation(Vec3::new(0.0, 1.6, 0.0), Quat::from_rotation_y(0.75));

        state.sync_render_from_head(head, anchor);
        let matrix = state.xr_model_matrix();
        let forward = matrix.transform_vector3(Vec3::NEG_Z).normalize();

        assert_vec3_near(
            forward,
            Quat::from_rotation_y(0.75 + MODEL_FORWARD_CORRECTION) * Vec3::NEG_Z,
            1e-5,
        );
    }

    #[test]
    fn runtime_tracking_should_remove_avatar_body_yaw_from_head_local_orientation() {
        let mut state = SpaceState::default();
        let tracking = TrackingFrame {
            head: tracked_pose_with_orientation(
                Vec3::new(0.0, 1.6, 0.0),
                Quat::from_rotation_y(0.6),
            ),
            ..sample_tracking_frame()
        };

        state.observe_raw_tracking(tracking);
        state.sync_tracking_from_head(tracking.head, Vec3::new(0.0, 18.0, 0.0));
        let runtime_tracking = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );
        let head_forward = runtime_tracking.head.orientation * Vec3::NEG_Z;

        assert_vec3_near(head_forward, Vec3::NEG_Z, 1e-5);
    }

    #[test]
    fn runtime_tracking_should_ignore_render_anchor_changes() {
        let mut state = SpaceState::default();
        let tracking = sample_tracking_frame();
        let initial_anchor = Vec3::new(0.0, 18.0, 0.0);
        let changed_render_anchor = Vec3::new(0.4, 18.1, -0.2);

        state.sync_tracking_from_head(tracking.head, initial_anchor);
        let before = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );

        state.sync_render_from_head(tracking.head, changed_render_anchor);
        let after = state.runtime_tracking_with_mode(
            &tracking,
            VrmTrackingInput::default(),
            DemoHandTrackingMode::PmxControllerPose,
        );

        assert_vec3_near(before.head.position, after.head.position, 1e-5);
        assert_quat_near(before.head.orientation, after.head.orientation, 1e-5);
    }

    #[test]
    fn teleport_target_should_reject_points_outside_room_bounds() {
        let state = SpaceState::default();
        let mut tracking = sample_tracking_frame();
        tracking.right_aim =
            tracked_pose_with_orientation(Vec3::new(10.0, 1.2, 10.0), Quat::from_rotation_x(-0.8));

        let target = state.resolve_teleport_target(tracking);
        assert!(target.is_none());
    }

    fn sample_tracking_frame() -> TrackingFrame {
        TrackingFrame {
            head: tracked_pose(Vec3::new(0.0, 1.6, 0.0)),
            left_grip: tracked_pose(Vec3::new(-0.4, 1.1, -0.2)),
            left_aim: tracked_pose(Vec3::new(-0.3, 1.2, -0.1)),
            left_hand: tracked_pose(Vec3::new(-0.2, 1.0, -0.4)),
            right_grip: tracked_pose(Vec3::new(0.4, 1.1, -0.2)),
            right_aim: tracked_pose(Vec3::new(0.3, 1.2, -0.1)),
            right_hand: tracked_pose(Vec3::new(0.2, 1.0, -0.4)),
            buttons: ActionButtons {
                teleport_aim_active: true,
                teleport_confirm: true,
                snap_turn_left: true,
                snap_turn_right: false,
                move_axis: Vec2::ZERO,
            },
        }
    }

    fn tracked_pose(position: Vec3) -> TrackedPose {
        tracked_pose_with_orientation(position, Quat::IDENTITY)
    }

    fn tracked_pose_with_orientation(position: Vec3, orientation: Quat) -> TrackedPose {
        TrackedPose {
            position,
            orientation,
            valid: true,
        }
    }

    fn assert_vec3_near(actual: Vec3, expected: Vec3, tolerance: f32) {
        let delta = actual - expected;
        assert!(
            delta.length() <= tolerance,
            "vec mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }

    fn assert_quat_near(actual: Quat, expected: Quat, tolerance: f32) {
        let align = actual.dot(expected).abs();
        assert!(
            (1.0 - align) <= tolerance,
            "quat mismatch: actual={actual:?} expected={expected:?} tolerance={tolerance}",
        );
    }
}
