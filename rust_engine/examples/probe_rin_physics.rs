//! Runs a fixed-step Bullet probe for either Rin or Grass Wonder PMX physics.
use std::env;
use std::process::ExitCode;

use glam::{Mat4, Vec3};
use mmd_engine::{
    model::load_pmx,
    physics::config::{reset_config, set_config, PhysicsConfig},
};

const SAMPLE_FRAMES: &[usize] = &[0, 1, 10, 60, 300];

struct ProbeProfile {
    name: &'static str,
    bones: &'static [&'static str],
    joint_needles: &'static [&'static str],
}

const RIN_PROFILE: ProbeProfile = ProbeProfile {
    name: "Rin ribbons",
    bones: &[
        "\u{88d9}_1_8",
        "\u{88d9}_1_10",
        "\u{5de6}\u{8863}\u{5e36}_0_1",
        "\u{5de6}\u{8863}\u{5e36}_4_1",
        "\u{5de6}\u{8863}\u{5e36}_8_1",
        "\u{53f3}\u{8863}\u{5e36}_0_1",
        "\u{53f3}\u{8863}\u{5e36}_4_1",
        "\u{53f3}\u{8863}\u{5e36}_8_1",
    ],
    joint_needles: &["\u{5de6}\u{8863}\u{5e36}", "\u{53f3}\u{8863}\u{5e36}"],
};

const GRASS_WONDER_PROFILE: ProbeProfile = ProbeProfile {
    name: "Grass Wonder skirt",
    bones: &[
        "Sp_Hi_MSkirt0_B_00",
        "Sp_Hi_MSkirt0_B_01",
        "Sp_Hi_MSkirt0_F_00",
        "Sp_Hi_MSkirt0_F_01",
        "Sp_Hi_MSkirt0_L_00",
        "Sp_Hi_MSkirt0_L_01",
    ],
    joint_needles: &[
        "Sp_Hi_MSkirt0_B_00_skirt_vertical_joint",
        "Sp_Hi_MSkirt0_F_00_skirt_vertical_joint",
        "Sp_Hi_MSkirt0_L_00_skirt_vertical_joint",
    ],
};

fn profile_for_path(path: &str) -> &'static ProbeProfile {
    if path.to_ascii_lowercase().contains("grasswonder") {
        &GRASS_WONDER_PROFILE
    } else {
        &RIN_PROFILE
    }
}

fn transform_error(bind: Mat4, current: Mat4) -> (f32, f32, Vec3) {
    let (_, bind_rotation, bind_position) = bind.to_scale_rotation_translation();
    let (_, current_rotation, current_position) = current.to_scale_rotation_translation();
    let delta = current_position - bind_position;
    let rotation = bind_rotation.inverse() * current_rotation;
    let angle = 2.0 * rotation.w.clamp(-1.0, 1.0).acos();
    (
        delta.length(),
        angle.min(std::f32::consts::TAU - angle),
        delta,
    )
}

// Prints constraint state from the live Bullet world instead of inferring it from bones.
fn print_joint_snapshots(model: &mmd_engine::model::MmdModel, profile: &ProbeProfile) {
    for needle in profile.joint_needles {
        for snapshot in model.physics_joint_snapshots_matching(needle) {
            let diagnostic = snapshot.diagnostic;
            println!(
                "  joint '{}' anchor_error={:.5} linear_violation=({:.5},{:.5},{:.5}) angular_violation=({:.5},{:.5},{:.5}) spring={:?}",
                snapshot.name,
                snapshot.anchor_error,
                diagnostic.linear_violation.x,
                diagnostic.linear_violation.y,
                diagnostic.linear_violation.z,
                diagnostic.angular_violation.x,
                diagnostic.angular_violation.y,
                diagnostic.angular_violation.z,
                diagnostic.spring_enabled,
            );
        }
    }
}

fn run_case(
    path: &str,
    profile: &ProbeProfile,
    label: &str,
    collision_enabled: bool,
) -> Result<(), String> {
    let mut config = PhysicsConfig::default();
    config.collision_enabled = collision_enabled;
    config.joints_enabled = true;
    config.gravity_y = -98.0;
    config.physics_fps = 60.0;
    config.debug_log = false;
    set_config(config);

    let mut model = load_pmx(path).map_err(|error| error.to_string())?;
    model.update_node_animation(false);
    let watched: Vec<_> = profile
        .bones
        .iter()
        .map(|name| {
            model
                .bone_manager
                .find_bone_by_name(name)
                .map(|index| (*name, index))
                .ok_or_else(|| format!("missing watched bone '{name}'"))
        })
        .collect::<Result<_, _>>()?;
    let bind: Vec<_> = watched
        .iter()
        .map(|(_, index)| model.bone_manager.get_global_transform(*index))
        .collect();

    if !model.init_physics() {
        return Err("physics initialization failed".to_owned());
    }

    println!(
        "\n=== profile={} case={label} collision_enabled={collision_enabled} ===",
        profile.name
    );
    for frame in 0..=SAMPLE_FRAMES.last().copied().unwrap_or_default() {
        if frame > 0 {
            // Static animation isolates gravity, constraints, and collision response.
            model.update_node_animation(false);
            model.update_physics(1.0 / 60.0);
        }
        if !SAMPLE_FRAMES.contains(&frame) {
            continue;
        }
        println!("frame={frame}");
        for ((name, index), bind_transform) in watched.iter().zip(&bind) {
            let current = model.bone_manager.get_global_transform(*index);
            let (distance, angle, delta) = transform_error(*bind_transform, current);
            println!(
                "  {name}: distance={distance:.5} angle={angle:.5} delta=({:.5},{:.5},{:.5})",
                delta.x, delta.y, delta.z
            );
        }
        print_joint_snapshots(&model, profile);
    }
    Ok(())
}

fn main() -> ExitCode {
    let Some(path) = env::args().nth(1) else {
        eprintln!("usage: cargo run --example probe_rin_physics -- <model.pmx>");
        return ExitCode::FAILURE;
    };
    let profile = profile_for_path(&path);
    let result = run_case(&path, profile, "default", true)
        .and_then(|_| run_case(&path, profile, "collision-off", false));
    reset_config();
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("probe failed: {error}");
            ExitCode::FAILURE
        }
    }
}
