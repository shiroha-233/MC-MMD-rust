//! 输出指定 PMX 模型中与裙摆碰撞有关的原始物理数据。

use std::env;
use std::process::ExitCode;

use glam::{Mat4, Quat, Vec3};
use mmd::pmx::rigid_body::{RigidBody, RigidBodyMode, RigidBodyShape};
use mmd_engine::{
    model::{load_pmx, VertexWeight},
    physics::body_collider_scale_flags,
};

const TARGET_NAMES: &[&str] = &[
    // TohsakaRin 奔跑时反复出现接触和限位异常的刚体。
    "左袖1",
    "左袖2",
    "左袖3",
    "左袖4",
    "右袖1",
    "右袖2",
    "右袖3",
    "右袖4",
    "M-M-M-下半身",
    "頭",
    "左髮飾3",
    "左髮飾4",
    "右髮飾3",
    "右髮飾4",
    // TohsakaRin 裙摆主链及日志中持续分离的横向关节端点。
    "裙_4_0",
    "裙_5_0",
    "裙_5_1",
    "裙_5_2",
    "裙_6_0",
    "裙_7_16",
    "裙_8_1",
    "左衣帶_8_1",
    "右衣帶_8_1",
    "左馬尾1-9",
    "右馬尾1-9",
    "Sp_Hi_Tail0_B_00_body_blocker",
    "left_thigh_skirt_collider",
    "right_thigh_skirt_collider",
    "left_shin_skirt_collider",
    "right_shin_skirt_collider",
    "Sp_Hi_MSkirt0_F_00_skirt_physics",
    "Sp_Hi_MSkirt0_FL_00_skirt_physics",
    "Sp_Hi_MSkirt0_FR_00_skirt_physics",
    // Teio 后裙摆与尾巴链：用于对应运行日志中的持续接触和横向关节限位异常。
    "Sp_Hi_MSkirt0_L_00_skirt_anchor",
    "Sp_Hi_MSkirt0_L_00_skirt_physics",
    "Sp_Hi_MSkirt0_BL_00_skirt_anchor",
    "Sp_Hi_MSkirt0_BL_00_skirt_physics",
    "Sp_Hi_MSkirt0_B_00_skirt_anchor",
    "Sp_Hi_MSkirt0_B_00_skirt_physics",
    "Sp_Hi_MSkirt0_BR_00_skirt_anchor",
    "Sp_Hi_MSkirt0_BR_00_skirt_physics",
    "Sp_Hi_MSkirt0_R_00_skirt_anchor",
    "Sp_Hi_MSkirt0_R_00_skirt_physics",
    "Sp_Hi_Tail0_B_00_anchor",
    "Sp_Hi_Tail0_B_00_physics",
    "Sp_Hi_Tail0_B_01_physics",
    "Sp_Hi_Tail0_B_02_physics",
];

fn is_target(name: &str) -> bool {
    TARGET_NAMES.iter().any(|target| name == *target)
}

fn is_skirt_body(body: &RigidBody) -> bool {
    const NAMES: &[&str] = &["裙", "スカート", "skirt", "petticoat", "下装", "下衣", "裾"];
    let local = body.local_name.to_lowercase();
    let universal = body.universal_name.to_lowercase();
    NAMES
        .iter()
        .any(|name| local.contains(name) || universal.contains(name))
}

/// 判断刚体是否属于截图中同类的下装、衣带或外套动态链。
fn is_lower_garment_body(body: &RigidBody) -> bool {
    const NAMES: &[&str] = &[
        "裙",
        "スカート",
        "skirt",
        "petticoat",
        "下装",
        "下衣",
        "裾",
        "摆",
        "衣摆",
        "后摆",
        "下摆",
        "衣帶",
        "衣带",
        "外套",
        "风衣",
        "coat",
        "cloak",
        "cape",
        "flap",
    ];
    let local = body.local_name.to_lowercase();
    let universal = body.universal_name.to_lowercase();
    NAMES
        .iter()
        .any(|name| local.contains(name) || universal.contains(name))
}

/// 返回顶点对指定骨骼的权重，便于确认哪一片网格会随物理骨骼移动。
fn weight_for_bone(weight: &VertexWeight, target: i32) -> f32 {
    match weight {
        VertexWeight::Bdef1 { bone } => f32::from(*bone == target),
        VertexWeight::Bdef2 { bones, weight } | VertexWeight::Sdef { bones, weight, .. } => {
            if bones[0] == target {
                *weight
            } else if bones[1] == target {
                1.0 - *weight
            } else {
                0.0
            }
        }
        VertexWeight::Bdef4 { bones, weights } | VertexWeight::Qdef { bones, weights } => bones
            .iter()
            .zip(weights)
            .filter_map(|(bone, weight)| (*bone == target).then_some(*weight))
            .sum(),
    }
}

fn collision_enabled(a: &RigidBody, b: &RigidBody) -> bool {
    ((!a.un_collision_group_flag) & (1u16 << b.group.min(15))) != 0
        && ((!b.un_collision_group_flag) & (1u16 << a.group.min(15))) != 0
}

/// 使用与运行时一致的形状尺寸和欧拉顺序计算绑定姿态保守 AABB。
fn bind_aabb(body: &RigidBody) -> Option<(Vec3, Vec3)> {
    let size = Vec3::from_array(match body.shape {
        RigidBodyShape::Sphere => [body.size[0], body.size[0], body.size[0]],
        RigidBodyShape::Box => body.size,
        RigidBodyShape::Capsule => [
            body.size[0],
            body.size[0] + body.size[1] * 0.5,
            body.size[0],
        ],
    });
    if !size.is_finite() || size.cmple(Vec3::ZERO).any() {
        return None;
    }

    let rotation = if body
        .rotation
        .iter()
        .any(|value| value.is_finite() && value.abs() > std::f32::consts::TAU)
    {
        let factor = (std::f32::consts::PI / 180.0).powi(2);
        body.rotation.map(|value| value * factor)
    } else {
        body.rotation
    };
    let transform = Mat4::from_rotation_translation(
        Quat::from_rotation_z(rotation[2])
            * Quat::from_rotation_y(rotation[1])
            * Quat::from_rotation_x(rotation[0]),
        Vec3::from_array(body.position),
    );
    let half_extents = transform.x_axis.truncate().abs() * size.x
        + transform.y_axis.truncate().abs() * size.y
        + transform.z_axis.truncate().abs() * size.z;
    Some((transform.w_axis.truncate(), half_extents))
}

fn aabb_overlaps(a: (Vec3, Vec3), b: (Vec3, Vec3)) -> bool {
    (a.0 - b.0).abs().cmplt(a.1 + b.1).all()
}

fn main() -> ExitCode {
    let Some(path) = env::args().nth(1) else {
        eprintln!("用法: cargo run --example audit_pmx_physics -- <模型.pmx>");
        return ExitCode::FAILURE;
    };
    let print_all_static = env::args().any(|arg| arg == "--static");

    let model = match load_pmx(&path) {
        Ok(model) => model,
        Err(error) => {
            eprintln!("读取 PMX 失败: {error}");
            return ExitCode::FAILURE;
        }
    };

    println!("模型: {}", model.name);
    println!("刚体总数: {}", model.rigid_bodies.len());
    println!("关节总数: {}", model.joints.len());

    println!("\n将应用人体碰撞体缩放的刚体:");
    let body_scale_flags = body_collider_scale_flags(&model.rigid_bodies, &model.joints);
    for (index, should_scale) in body_scale_flags.into_iter().enumerate() {
        if should_scale {
            let body = &model.rigid_bodies[index];
            println!(
                "  #{index} {} group={} shape={:?} size={:?}",
                body.local_name, body.group, body.shape, body.size
            );
        }
    }

    println!("\n人体缩放候选 -> 绑定姿态重叠裙摆刚体:");
    for (index, should_scale) in body_collider_scale_flags(&model.rigid_bodies, &model.joints)
        .into_iter()
        .enumerate()
    {
        if !should_scale {
            continue;
        }
        let body = &model.rigid_bodies[index];
        let overlaps: Vec<_> = model
            .rigid_bodies
            .iter()
            .enumerate()
            .filter(|(_, dynamic)| {
                dynamic.mode != RigidBodyMode::Static
                    && is_skirt_body(dynamic)
                    && collision_enabled(body, dynamic)
                    && bind_aabb(body)
                        .zip(bind_aabb(dynamic))
                        .is_some_and(|(a, b)| aabb_overlaps(a, b))
            })
            .map(|(dynamic_index, dynamic)| format!("#{dynamic_index} {}", dynamic.local_name))
            .collect();
        println!(
            "  #{index} {} overlap_count={} [{}]",
            body.local_name,
            overlaps.len(),
            overlaps.join(", ")
        );
    }

    println!("\n旋转绝对值超过 2π 的刚体:");
    for (index, body) in model.rigid_bodies.iter().enumerate() {
        if body
            .rotation
            .iter()
            .any(|value| value.abs() > std::f32::consts::TAU)
        {
            println!(
                "  #{index} {} shape={:?} rotation={:?}",
                body.local_name, body.shape, body.rotation
            );
        }
    }

    let mut selected_indices = Vec::new();
    for (index, body) in model.rigid_bodies.iter().enumerate() {
        if is_target(&body.local_name)
            || (print_all_static && body.mode == mmd::pmx::rigid_body::RigidBodyMode::Static)
        {
            selected_indices.push(index);
            println!("\n刚体 #{index}: {}", body.local_name);
            let bone_name = usize::try_from(body.bone_index)
                .ok()
                .and_then(|bone_index| model.bone_manager.get_bone(bone_index))
                .map_or("<无绑定骨骼>", |bone| bone.name.as_str());
            println!("  bone_index={} bone={}", body.bone_index, bone_name);
            println!(
                "  group={} excluded=0x{:04X}",
                body.group, body.un_collision_group_flag
            );
            println!("  shape={:?} size={:?}", body.shape, body.size);
            println!(
                "  position={:?} rotation={:?}",
                body.position, body.rotation
            );
            println!(
                "  mass={} move_damping={} rotation_damping={} restitution={} friction={} mode={:?}",
                body.mass,
                body.move_attenuation,
                body.rotation_attenuation,
                body.repulsion,
                body.friction,
                body.mode
            );
        }
    }

    println!("\n关联关节:");
    for (index, joint) in model.joints.iter().enumerate() {
        let body_a = joint.rigid_body_a_index as usize;
        let body_b = joint.rigid_body_b_index as usize;
        if selected_indices.contains(&body_a) || selected_indices.contains(&body_b) {
            println!("\n关节 #{index}: {}", joint.local_name);
            println!(
                "  type={:?} rigid_body_a={} ({}) rigid_body_b={} ({})",
                joint.type_,
                joint.rigid_body_a_index,
                model
                    .rigid_bodies
                    .get(body_a)
                    .map_or("<invalid>", |body| body.local_name.as_str()),
                joint.rigid_body_b_index,
                model
                    .rigid_bodies
                    .get(body_b)
                    .map_or("<invalid>", |body| body.local_name.as_str())
            );
            println!(
                "  position={:?} rotation={:?}",
                joint.position, joint.rotation
            );
            println!(
                "  position_min={:?} position_max={:?}",
                joint.position_min, joint.position_max
            );
            println!(
                "  rotation_min={:?} rotation_max={:?}",
                joint.rotation_min, joint.rotation_max
            );
            println!(
                "  position_spring={:?} rotation_spring={:?}",
                joint.position_spring, joint.rotation_spring
            );
        }
    }

    println!("\n衣物动态链根关节:");
    for (index, joint) in model.joints.iter().enumerate() {
        let Ok(body_a_index) = usize::try_from(joint.rigid_body_a_index) else {
            continue;
        };
        let Ok(body_b_index) = usize::try_from(joint.rigid_body_b_index) else {
            continue;
        };
        let (Some(body_a), Some(body_b)) = (
            model.rigid_bodies.get(body_a_index),
            model.rigid_bodies.get(body_b_index),
        ) else {
            continue;
        };

        // 根边必须由运动学刚体连接到动态衣物刚体，避免把纵向链中段误判为根部。
        if body_a.mode == RigidBodyMode::Static
            && body_b.mode != RigidBodyMode::Static
            && is_lower_garment_body(body_b)
        {
            println!(
                "  #{index} {}: A=#{} {} B=#{} {} joint_pos={:?} joint_rot={:?} body_pos={:?} body_rot={:?} rot_min={:?} rot_max={:?}",
                joint.local_name,
                body_a_index,
                body_a.local_name,
                body_b_index,
                body_b.local_name,
                joint.position,
                joint.rotation,
                body_b.position,
                body_b.rotation,
                joint.rotation_min,
                joint.rotation_max,
            );
        }
    }

    println!("\n下装/衣带动态骨骼绑定:");
    for (body_index, body) in model.rigid_bodies.iter().enumerate() {
        if body.mode == RigidBodyMode::Static || !is_lower_garment_body(body) {
            continue;
        }
        let Ok(bone_index) = usize::try_from(body.bone_index) else {
            continue;
        };
        let Some(bone) = model.bone_manager.get_bone(bone_index) else {
            continue;
        };
        let parent_name = bone
            .parent_id()
            .and_then(|parent_index| model.bone_manager.get_bone(parent_index))
            .map_or("<根骨骼>", |parent| parent.name.as_str());
        let (vertex_count, weight_sum) = model
            .weights
            .iter()
            .map(|weight| weight_for_bone(weight, body.bone_index))
            .filter(|weight| *weight > 0.0)
            .fold((0usize, 0.0f32), |(count, sum), weight| {
                (count + 1, sum + weight)
            });
        let connected_joints = model
            .joints
            .iter()
            .filter(|joint| {
                joint.rigid_body_a_index == body_index as i32
                    || joint.rigid_body_b_index == body_index as i32
            })
            .count();
        println!(
            "  body=#{body_index} '{}' bone=#{bone_index} '{}' parent='{}' mode={:?} joints={} vertices={} weight_sum={:.1}",
            body.local_name,
            bone.name,
            parent_name,
            body.mode,
            connected_joints,
            vertex_count,
            weight_sum,
        );
    }

    println!("\n动态组 -> 可碰撞静态刚体:");
    let dynamic_groups: std::collections::BTreeSet<u8> = model
        .rigid_bodies
        .iter()
        .filter(|body| body.mode != mmd::pmx::rigid_body::RigidBodyMode::Static)
        .map(|body| body.group)
        .collect();
    for dynamic_group in dynamic_groups {
        println!("\n动态组 {dynamic_group}:");
        for (index, body) in model.rigid_bodies.iter().enumerate() {
            if body.mode != mmd::pmx::rigid_body::RigidBodyMode::Static {
                continue;
            }

            // Bullet 只有在双方掩码都允许对方组时才会生成接触。
            let static_allows_dynamic =
                (!body.un_collision_group_flag & (1u16 << dynamic_group.min(15))) != 0;
            let dynamic_allows_static = model.rigid_bodies.iter().any(|dynamic| {
                dynamic.mode != mmd::pmx::rigid_body::RigidBodyMode::Static
                    && dynamic.group == dynamic_group
                    && (!dynamic.un_collision_group_flag & (1u16 << body.group.min(15))) != 0
            });
            if static_allows_dynamic && dynamic_allows_static {
                let bone_name = usize::try_from(body.bone_index)
                    .ok()
                    .and_then(|bone_index| model.bone_manager.get_bone(bone_index))
                    .map_or("<无绑定骨骼>", |bone| bone.name.as_str());
                println!(
                    "  #{index} {} bone={} group={} shape={:?} size={:?}",
                    body.local_name, bone_name, body.group, body.shape, body.size
                );
            }
        }
    }

    ExitCode::SUCCESS
}
