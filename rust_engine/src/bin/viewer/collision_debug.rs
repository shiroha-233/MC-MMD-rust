use std::f32::consts::{PI, TAU};

use glam::{Mat4, Quat, Vec3};
use mmd::pmx::rigid_body::{RigidBody, RigidBodyMode, RigidBodyShape};
use mmd_engine::model::MmdModel;
use mmd_engine::physics::{
    body_collider_scale_flags, effective_collision_shape_size_with_static_scale,
};

const CIRCLE_SEGMENTS: usize = 32;

pub struct CollisionLine {
    pub start: Vec3,
    pub end: Vec3,
    pub color: [f32; 4],
}

pub fn build_collision_lines(model: &MmdModel, static_collider_scale: f32) -> Vec<CollisionLine> {
    let mut lines = Vec::new();
    let body_collider_flags = body_collider_scale_flags(&model.rigid_bodies, &model.joints);
    for (body_index, body) in model.rigid_bodies.iter().enumerate() {
        let transform = body_render_transform(model, body);
        let color = body_color(body.mode);
        // viewer 倍率只缩放绿色人体碰撞体，保持动态附件的 PMX 原始尺寸。
        let size = effective_collision_shape_size_with_static_scale(
            body,
            static_collider_scale,
            body_collider_flags[body_index],
        );
        match body.shape {
            RigidBodyShape::Sphere => append_sphere(&mut lines, transform, size[0], color),
            RigidBodyShape::Box => append_box(&mut lines, transform, Vec3::from_array(size), color),
            RigidBodyShape::Capsule => {
                append_capsule(&mut lines, transform, size[0], size[1], color)
            }
        }
    }
    lines
}

fn body_render_transform(model: &MmdModel, body: &RigidBody) -> Mat4 {
    let rotation = normalized_rotation(body.rotation);
    let initial_left = Mat4::from_rotation_translation(
        Quat::from_rotation_z(rotation[2])
            * Quat::from_rotation_y(rotation[1])
            * Quat::from_rotation_x(rotation[0]),
        Vec3::from_array(body.position),
    );

    let current_left = usize::try_from(body.bone_index)
        .ok()
        .and_then(|index| model.bone_manager.get_bone(index))
        .map(|bone| {
            // 骨骼存于右手模型空间，刚体偏移则在 PMX/Bullet 左手空间计算。
            let bind_left = mirror_z(Mat4::from_translation(bone.initial_position));
            mirror_z(bone.global_transform()) * bind_left.inverse() * initial_left
        })
        .unwrap_or(initial_left);

    mirror_z(current_left)
}

fn normalized_rotation(rotation: [f32; 3]) -> [f32; 3] {
    if rotation
        .iter()
        .any(|value| value.is_finite() && value.abs() > TAU)
    {
        let radians_per_degree = PI / 180.0;
        rotation.map(|value| value * radians_per_degree * radians_per_degree)
    } else {
        rotation
    }
}

fn mirror_z(matrix: Mat4) -> Mat4 {
    let mirror = Mat4::from_scale(Vec3::new(1.0, 1.0, -1.0));
    mirror * matrix * mirror
}

fn body_color(mode: RigidBodyMode) -> [f32; 4] {
    match mode {
        RigidBodyMode::Static => [0.1, 0.9, 0.25, 0.85],
        RigidBodyMode::Dynamic => [1.0, 0.15, 0.1, 0.85],
        RigidBodyMode::DynamicWithBonePosition => [0.1, 0.55, 1.0, 0.85],
    }
}

fn append_line(
    lines: &mut Vec<CollisionLine>,
    transform: Mat4,
    start: Vec3,
    end: Vec3,
    color: [f32; 4],
) {
    lines.push(CollisionLine {
        start: transform.transform_point3(start),
        end: transform.transform_point3(end),
        color,
    });
}

fn append_circle<F>(lines: &mut Vec<CollisionLine>, transform: Mat4, color: [f32; 4], point: F)
where
    F: Fn(f32) -> Vec3,
{
    for index in 0..CIRCLE_SEGMENTS {
        let a = TAU * index as f32 / CIRCLE_SEGMENTS as f32;
        let b = TAU * (index + 1) as f32 / CIRCLE_SEGMENTS as f32;
        append_line(lines, transform, point(a), point(b), color);
    }
}

fn append_sphere(lines: &mut Vec<CollisionLine>, transform: Mat4, radius: f32, color: [f32; 4]) {
    append_circle(lines, transform, color, |angle| {
        Vec3::new(radius * angle.cos(), radius * angle.sin(), 0.0)
    });
    append_circle(lines, transform, color, |angle| {
        Vec3::new(radius * angle.cos(), 0.0, radius * angle.sin())
    });
    append_circle(lines, transform, color, |angle| {
        Vec3::new(0.0, radius * angle.cos(), radius * angle.sin())
    });
}

fn append_box(lines: &mut Vec<CollisionLine>, transform: Mat4, half: Vec3, color: [f32; 4]) {
    let corners = [
        Vec3::new(-half.x, -half.y, -half.z),
        Vec3::new(half.x, -half.y, -half.z),
        Vec3::new(half.x, half.y, -half.z),
        Vec3::new(-half.x, half.y, -half.z),
        Vec3::new(-half.x, -half.y, half.z),
        Vec3::new(half.x, -half.y, half.z),
        Vec3::new(half.x, half.y, half.z),
        Vec3::new(-half.x, half.y, half.z),
    ];
    for (start, end) in [
        (0, 1),
        (1, 2),
        (2, 3),
        (3, 0),
        (4, 5),
        (5, 6),
        (6, 7),
        (7, 4),
        (0, 4),
        (1, 5),
        (2, 6),
        (3, 7),
    ] {
        append_line(lines, transform, corners[start], corners[end], color);
    }
}

fn append_capsule(
    lines: &mut Vec<CollisionLine>,
    transform: Mat4,
    radius: f32,
    cylinder_height: f32,
    color: [f32; 4],
) {
    let half_height = cylinder_height * 0.5;
    for y in [-half_height, half_height] {
        append_circle(lines, transform, color, |angle| {
            Vec3::new(radius * angle.cos(), y, radius * angle.sin())
        });
    }

    for radial in [
        Vec3::X * radius,
        Vec3::NEG_X * radius,
        Vec3::Z * radius,
        Vec3::NEG_Z * radius,
    ] {
        append_line(
            lines,
            transform,
            radial - Vec3::Y * half_height,
            radial + Vec3::Y * half_height,
            color,
        );
    }

    // 两个正交平面的半圆共同画出胶囊端帽。
    for axis in [Vec3::X, Vec3::Z] {
        for index in 0..CIRCLE_SEGMENTS / 2 {
            let a = PI * index as f32 / (CIRCLE_SEGMENTS / 2) as f32;
            let b = PI * (index + 1) as f32 / (CIRCLE_SEGMENTS / 2) as f32;
            let top = |angle: f32| {
                axis * (radius * angle.cos()) + Vec3::Y * (half_height + radius * angle.sin())
            };
            let bottom = |angle: f32| {
                axis * (radius * angle.cos()) - Vec3::Y * (half_height + radius * angle.sin())
            };
            append_line(lines, transform, top(a), top(b), color);
            append_line(lines, transform, bottom(a), bottom(b), color);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::mirror_z;
    use glam::{Mat4, Quat, Vec3};

    #[test]
    fn mirror_z_round_trip_restores_transform() {
        let transform = Mat4::from_rotation_translation(
            Quat::from_euler(glam::EulerRot::ZYX, 0.3, -0.5, 0.7),
            Vec3::new(1.0, 2.0, 3.0),
        );
        assert!(mirror_z(mirror_z(transform)).abs_diff_eq(transform, 1e-6));
    }
}
