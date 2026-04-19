use glam::{Mat4, Vec3};

pub const ROOM_FLOOR_Y: f32 = 0.0;
pub const ROOM_HALF_EXTENT: f32 = 3.2;
pub const ROOM_HEIGHT: f32 = 2.8;
pub const ROOM_MIRROR_WIDTH: f32 = 1.2;
pub const ROOM_MIRROR_HEIGHT: f32 = 1.6;
pub const ROOM_MIRROR_ASPECT: f32 = ROOM_MIRROR_WIDTH / ROOM_MIRROR_HEIGHT;

const ROOM_MIRROR_CENTER_Y: f32 = 1.5;
const ROOM_MIRROR_FRAME_THICKNESS: f32 = 0.08;
const ROOM_MIRROR_FRAME_Z: f32 = -ROOM_HALF_EXTENT + 0.016;
pub const ROOM_MIRROR_SURFACE_Z: f32 = -ROOM_HALF_EXTENT + 0.018;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RoomTextureKind {
    Flat,
    Mirror,
}

#[derive(Clone)]
pub struct RoomMesh {
    pub positions: Vec<f32>,
    pub normals: Vec<f32>,
    pub uvs: Vec<f32>,
    pub indices: Vec<u32>,
    pub color: [f32; 4],
    pub model_matrix: Mat4,
    pub texture_kind: RoomTextureKind,
}

pub struct DemoRoom {
    meshes: Vec<RoomMesh>,
}

impl DemoRoom {
    pub fn new() -> Self {
        Self {
            meshes: build_demo_room_meshes(),
        }
    }

    pub fn meshes(&self) -> &[RoomMesh] {
        &self.meshes
    }
}

impl Default for DemoRoom {
    fn default() -> Self {
        Self::new()
    }
}

fn build_demo_room_meshes() -> Vec<RoomMesh> {
    let half = ROOM_HALF_EXTENT;
    let height = ROOM_HEIGHT;
    vec![
        quad(
            [
                Vec3::new(-half, ROOM_FLOOR_Y, -half),
                Vec3::new(half, ROOM_FLOOR_Y, -half),
                Vec3::new(half, ROOM_FLOOR_Y, half),
                Vec3::new(-half, ROOM_FLOOR_Y, half),
            ],
            Vec3::Y,
            [0.22, 0.24, 0.27, 1.0],
        ),
        quad(
            [
                Vec3::new(-half, ROOM_FLOOR_Y, -half),
                Vec3::new(-half, height, -half),
                Vec3::new(half, height, -half),
                Vec3::new(half, ROOM_FLOOR_Y, -half),
            ],
            Vec3::Z,
            [0.18, 0.21, 0.25, 1.0],
        ),
        quad(
            [
                Vec3::new(half, ROOM_FLOOR_Y, half),
                Vec3::new(half, height, half),
                Vec3::new(-half, height, half),
                Vec3::new(-half, ROOM_FLOOR_Y, half),
            ],
            Vec3::NEG_Z,
            [0.16, 0.18, 0.22, 1.0],
        ),
        quad(
            [
                Vec3::new(-half, ROOM_FLOOR_Y, half),
                Vec3::new(-half, height, half),
                Vec3::new(-half, height, -half),
                Vec3::new(-half, ROOM_FLOOR_Y, -half),
            ],
            Vec3::X,
            [0.17, 0.19, 0.23, 1.0],
        ),
        quad(
            [
                Vec3::new(half, ROOM_FLOOR_Y, -half),
                Vec3::new(half, height, -half),
                Vec3::new(half, height, half),
                Vec3::new(half, ROOM_FLOOR_Y, half),
            ],
            Vec3::NEG_X,
            [0.17, 0.19, 0.23, 1.0],
        ),
        quad(
            [
                Vec3::new(-half, height, half),
                Vec3::new(half, height, half),
                Vec3::new(half, height, -half),
                Vec3::new(-half, height, -half),
            ],
            Vec3::NEG_Y,
            [0.14, 0.16, 0.2, 1.0],
        ),
        front_wall_panel(
            ROOM_MIRROR_WIDTH + ROOM_MIRROR_FRAME_THICKNESS * 2.0,
            ROOM_MIRROR_HEIGHT + ROOM_MIRROR_FRAME_THICKNESS * 2.0,
            ROOM_MIRROR_FRAME_Z,
            [0.08, 0.09, 0.10, 1.0],
            RoomTextureKind::Flat,
        ),
        front_wall_panel(
            ROOM_MIRROR_WIDTH,
            ROOM_MIRROR_HEIGHT,
            ROOM_MIRROR_SURFACE_Z,
            [1.0, 1.0, 1.0, 1.0],
            RoomTextureKind::Mirror,
        ),
    ]
}

fn quad(corners: [Vec3; 4], normal: Vec3, color: [f32; 4]) -> RoomMesh {
    let mut positions = Vec::with_capacity(12);
    let mut normals = Vec::with_capacity(12);
    let mut uvs = Vec::with_capacity(8);
    for (index, corner) in corners.into_iter().enumerate() {
        positions.extend_from_slice(&corner.to_array());
        normals.extend_from_slice(&normal.to_array());
        let uv = match index {
            0 => [0.0, 0.0],
            1 => [1.0, 0.0],
            2 => [1.0, 1.0],
            _ => [0.0, 1.0],
        };
        uvs.extend_from_slice(&uv);
    }

    RoomMesh {
        positions,
        normals,
        uvs,
        indices: vec![0, 1, 2, 0, 2, 3],
        color,
        model_matrix: Mat4::IDENTITY,
        texture_kind: RoomTextureKind::Flat,
    }
}

fn front_wall_panel(
    width: f32,
    height: f32,
    z: f32,
    color: [f32; 4],
    texture_kind: RoomTextureKind,
) -> RoomMesh {
    let half_width = width * 0.5;
    let half_height = height * 0.5;
    let center_y = ROOM_MIRROR_CENTER_Y;
    let mut mesh = quad(
        [
            Vec3::new(-half_width, center_y - half_height, z),
            Vec3::new(half_width, center_y - half_height, z),
            Vec3::new(half_width, center_y + half_height, z),
            Vec3::new(-half_width, center_y + half_height, z),
        ],
        Vec3::Z,
        color,
    );
    mesh.texture_kind = texture_kind;
    mesh
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn demo_room_should_build_floor_five_bounds_and_mirror_panel() {
        let room = DemoRoom::default();

        assert_eq!(room.meshes().len(), 8);
    }

    #[test]
    fn room_meshes_should_have_matching_vertex_stream_lengths() {
        let room = DemoRoom::default();

        for mesh in room.meshes() {
            let vertex_count = mesh.positions.len() / 3;
            assert_eq!(mesh.normals.len() / 3, vertex_count);
            assert_eq!(mesh.uvs.len() / 2, vertex_count);
            assert_eq!(mesh.indices.len(), 6);
        }
    }

    #[test]
    fn demo_room_should_include_single_mirror_surface_mesh() {
        let room = DemoRoom::default();

        assert_eq!(
            room.meshes()
                .iter()
                .filter(|mesh| mesh.texture_kind == RoomTextureKind::Mirror)
                .count(),
            1
        );
    }
}
