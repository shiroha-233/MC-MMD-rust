use super::{effective_collision_mask, ActivePhysicsDebugConfig, PhysicsDebugTelemetry};
use crate::physics::config::PhysicsConfig;
use glam::Vec3;

#[test]
fn collision_switch_disables_all_contact_pairs() {
    assert_eq!(effective_collision_mask(false, 0xFFFE), 0);
    assert_eq!(effective_collision_mask(true, 0xFFFE), 0xFFFE);
}

#[test]
fn telemetry_keeps_velocity_and_position_from_the_same_peak_sample() {
    let mut telemetry = PhysicsDebugTelemetry::default();
    telemetry.observe_body(
        3,
        Vec3::new(2.0, 0.0, 0.0),
        Vec3::new(0.0, 4.0, 0.0),
        Vec3::new(1.0, 2.0, 3.0),
        Some(Vec3::new(1.0, 2.0, 2.5)),
    );
    telemetry.observe_body(
        7,
        Vec3::X,
        Vec3::Y,
        Vec3::splat(9.0),
        Some(Vec3::splat(9.0)),
    );

    assert_eq!(telemetry.peak_linear_body_index, Some(3));
    assert_eq!(telemetry.peak_linear_velocity, Vec3::new(2.0, 0.0, 0.0));
    assert_eq!(telemetry.peak_linear_position, Vec3::new(1.0, 2.0, 3.0));
    assert!((telemetry.peak_linear_body_target_error - 0.5).abs() < 1e-6);
    assert_eq!(telemetry.peak_angular_body_index, Some(3));
    assert_eq!(telemetry.peak_angular_velocity, Vec3::new(0.0, 4.0, 0.0));
}

#[test]
fn body_target_error_preserves_delta_direction() {
    let mut telemetry = PhysicsDebugTelemetry::default();
    telemetry.observe_body(
        2,
        Vec3::ZERO,
        Vec3::ZERO,
        Vec3::new(3.0, 2.0, 1.0),
        Some(Vec3::ONE),
    );
    assert_eq!(telemetry.peak_body_target_index, Some(2));
    assert_eq!(telemetry.max_body_target_delta, Vec3::new(2.0, 1.0, 0.0));
    assert!((telemetry.max_body_target_error - 5.0_f32.sqrt()).abs() < 1e-6);
}

#[test]
fn active_debug_config_is_a_build_time_snapshot() {
    let mut config = PhysicsConfig::default();
    config.collision_enabled = true;
    config.kinematic_filter = false;
    let snapshot = ActivePhysicsDebugConfig::from_config(&config);
    config.collision_enabled = false;
    config.kinematic_filter = true;
    assert!(!config.collision_enabled);
    assert!(config.kinematic_filter);
    assert!(snapshot.collision_enabled);
    assert!(!snapshot.kinematic_filter);
}

#[test]
fn telemetry_counts_filtered_kinematic_noise() {
    let mut telemetry = PhysicsDebugTelemetry::default();
    telemetry.observe_kinematic_suppression(0.0007, 0.0004);
    telemetry.observe_kinematic_suppression(0.0012, 0.0009);

    assert_eq!(telemetry.kinematic_suppressed_count, 2);
    assert!((telemetry.max_suppressed_translation_error - 0.0012).abs() < 1e-7);
    assert!((telemetry.max_suppressed_rotation_error - 0.0009).abs() < 1e-7);
}

#[test]
fn skirt_name_detection_supports_english_and_japanese_models() {
    assert!(super::is_skirt_body_name("Sp_Hi_MSkirt0_B_00_skirt_anchor"));
    assert!(super::is_skirt_body_name("裙_0_0"));
    assert!(!super::is_skirt_body_name("Sp_He_Hair4_L_00_anchor"));
}
