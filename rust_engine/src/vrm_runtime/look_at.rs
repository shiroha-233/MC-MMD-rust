//! Look-at runtime flow adapted from UniVRM's `Vrm10RuntimeLookAt`.

use glam::Vec3;

use crate::model::{LookAtConfig, LookAtType, MmdModel};

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct EyeDirection {
    pub yaw: f32,
    pub pitch: f32,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct LookAtInput {
    pub yaw_pitch: Option<EyeDirection>,
    pub world_position: Option<Vec3>,
}

pub struct LookAtRuntime {
    config: Option<LookAtConfig>,
    last_eye_direction: EyeDirection,
}

impl LookAtRuntime {
    pub fn new(config: Option<LookAtConfig>) -> Self {
        Self {
            config,
            last_eye_direction: EyeDirection::default(),
        }
    }

    pub fn look_at_type(&self) -> Option<LookAtType> {
        self.config.as_ref().map(|config| config.look_at_type)
    }

    pub fn resolve(&mut self, input: LookAtInput, head_position: Option<Vec3>) -> EyeDirection {
        let resolved = if let Some(direction) = input.yaw_pitch {
            direction
        } else if let (Some(world_position), Some(head_position)) =
            (input.world_position, head_position)
        {
            calculate_yaw_pitch(world_position - head_position)
        } else {
            EyeDirection::default()
        };
        self.last_eye_direction = resolved;
        resolved
    }

    pub fn apply_to_model(&self, model: &mut MmdModel, direction: EyeDirection) {
        let Some(config) = &self.config else {
            model.set_eye_tracking_enabled(false);
            return;
        };

        if direction == EyeDirection::default() {
            model.set_eye_tracking_enabled(false);
            return;
        }

        let mapped_yaw = map_horizontal(direction.yaw, config);
        let mapped_pitch = map_vertical(direction.pitch, config);
        model.set_eye_tracking_enabled(true);
        model.set_eye_angle(mapped_pitch, mapped_yaw);
    }
}

fn calculate_yaw_pitch(direction: Vec3) -> EyeDirection {
    let normalized = direction.normalize_or_zero();
    if normalized.length_squared() <= f32::EPSILON {
        return EyeDirection::default();
    }

    let yaw = normalized.x.atan2(-normalized.z).to_degrees();
    let pitch = normalized.y.asin().to_degrees();
    EyeDirection { yaw, pitch }
}

fn map_horizontal(yaw_deg: f32, config: &LookAtConfig) -> f32 {
    let range = if yaw_deg < 0.0 {
        config.range_map_horizontal_inner
    } else {
        config.range_map_horizontal_outer
    };
    map_axis(
        yaw_deg,
        range.input_max_value,
        range.output_scale,
        config.look_at_type,
    )
}

fn map_vertical(pitch_deg: f32, config: &LookAtConfig) -> f32 {
    let range = if pitch_deg < 0.0 {
        config.range_map_vertical_down
    } else {
        config.range_map_vertical_up
    };
    map_axis(
        pitch_deg,
        range.input_max_value,
        range.output_scale,
        config.look_at_type,
    )
}

fn map_axis(input_deg: f32, input_max: f32, output_scale: f32, look_at_type: LookAtType) -> f32 {
    let input_max = input_max.abs().max(1.0);
    let normalized = (input_deg / input_max).clamp(-1.0, 1.0);
    match look_at_type {
        LookAtType::Bone => (normalized * output_scale).to_radians(),
        LookAtType::Expression => normalized * output_scale,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{LookAtRangeMap, LookAtType};

    #[test]
    fn resolve_should_prefer_direct_yaw_pitch() {
        let mut runtime = LookAtRuntime::new(None);
        let direction = runtime.resolve(
            LookAtInput {
                yaw_pitch: Some(EyeDirection {
                    yaw: 10.0,
                    pitch: -5.0,
                }),
                world_position: Some(Vec3::new(10.0, 0.0, 0.0)),
            },
            Some(Vec3::ZERO),
        );
        assert_eq!(
            direction,
            EyeDirection {
                yaw: 10.0,
                pitch: -5.0
            }
        );
    }

    #[test]
    fn resolve_should_compute_world_position_yaw_pitch() {
        let mut runtime = LookAtRuntime::new(None);
        let direction = runtime.resolve(
            LookAtInput {
                yaw_pitch: None,
                world_position: Some(Vec3::new(1.0, 1.0, -1.0)),
            },
            Some(Vec3::ZERO),
        );
        assert!(direction.yaw > 0.0);
        assert!(direction.pitch > 0.0);
    }

    #[test]
    fn mapping_should_convert_bone_output_to_radians() {
        let config = LookAtConfig {
            look_at_type: LookAtType::Bone,
            range_map_horizontal_outer: LookAtRangeMap {
                input_max_value: 45.0,
                output_scale: 30.0,
            },
            ..LookAtConfig::default()
        };
        let mapped = map_horizontal(45.0, &config);
        assert!((mapped - 30.0_f32.to_radians()).abs() < 1e-5);
    }
}
