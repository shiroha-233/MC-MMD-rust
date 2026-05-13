//! Expression runtime flow adapted from UniVRM's `Vrm10RuntimeExpression`.

use std::collections::HashMap;

use crate::model::{LookAtType, MmdModel, VrmExpressionClip, VrmExpressions};

use super::look_at::EyeDirection;

#[derive(Clone, Debug, Hash, PartialEq, Eq)]
pub struct ExpressionKey(String);

impl ExpressionKey {
    pub fn new(name: impl Into<String>) -> Self {
        Self(name.into().trim().to_ascii_lowercase())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl From<&str> for ExpressionKey {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ExpressionPreset {
    Blink,
    BlinkLeft,
    BlinkRight,
    Aa,
    Ih,
    Ou,
    Ee,
    Oh,
    Happy,
    Angry,
    Sad,
    Relaxed,
    Surprised,
    LookUp,
    LookDown,
    LookLeft,
    LookRight,
}

impl ExpressionPreset {
    pub fn as_key(self) -> ExpressionKey {
        let key = match self {
            Self::Blink => "blink",
            Self::BlinkLeft => "blinkleft",
            Self::BlinkRight => "blinkright",
            Self::Aa => "aa",
            Self::Ih => "ih",
            Self::Ou => "ou",
            Self::Ee => "ee",
            Self::Oh => "oh",
            Self::Happy => "happy",
            Self::Angry => "angry",
            Self::Sad => "sad",
            Self::Relaxed => "relaxed",
            Self::Surprised => "surprised",
            Self::LookUp => "lookup",
            Self::LookDown => "lookdown",
            Self::LookLeft => "lookleft",
            Self::LookRight => "lookright",
        };
        ExpressionKey::new(key)
    }
}

impl From<ExpressionPreset> for ExpressionKey {
    fn from(value: ExpressionPreset) -> Self {
        value.as_key()
    }
}

#[derive(Clone, Debug, Default)]
pub struct ExpressionRuntimeState {
    pub input_weights: HashMap<ExpressionKey, f32>,
    pub actual_weights: HashMap<ExpressionKey, f32>,
    pub blink_override_rate: f32,
    pub look_at_override_rate: f32,
    pub mouth_override_rate: f32,
}

pub struct ExpressionRuntime {
    clips: HashMap<ExpressionKey, VrmExpressionClip>,
    morph_names: HashMap<ExpressionKey, String>,
}

impl ExpressionRuntime {
    pub fn new(expressions: &VrmExpressions) -> Self {
        let mut clips = HashMap::new();
        let mut morph_names = HashMap::new();

        for (name, clip) in &expressions.map {
            let key = ExpressionKey::new(name);
            clips.insert(key.clone(), clip.clone());
            morph_names.insert(
                key.clone(),
                crate::model::vrm_expression_to_mmd(key.as_str()).to_string(),
            );
        }

        Self { clips, morph_names }
    }

    pub fn process(
        &self,
        model: &mut MmdModel,
        requested: &HashMap<ExpressionKey, f32>,
        eye_direction: EyeDirection,
        look_at_type: Option<LookAtType>,
    ) -> ExpressionRuntimeState {
        let mut merged = requested.clone();
        if matches!(look_at_type, Some(LookAtType::Expression)) {
            merged.extend(synthesized_look_at_weights(eye_direction));
        }

        let state = self.compute_state(&merged);
        self.apply_to_model(model, &state.actual_weights);
        state
    }

    fn compute_state(&self, requested: &HashMap<ExpressionKey, f32>) -> ExpressionRuntimeState {
        let mut state = ExpressionRuntimeState::default();
        state.input_weights = requested.clone();

        for (key, clip) in &self.clips {
            let mut weight = requested.get(key).copied().unwrap_or(0.0).clamp(0.0, 1.0);
            if clip.is_binary {
                weight = if weight >= 0.5 { 1.0 } else { 0.0 };
            }
            if weight <= f32::EPSILON {
                continue;
            }

            state.actual_weights.insert(key.clone(), weight);
            if clip.override_blink != crate::model::ExpressionOverride::None
                || is_blink_key(key.as_str())
            {
                state.blink_override_rate = state.blink_override_rate.max(weight);
            }
            if clip.override_look_at != crate::model::ExpressionOverride::None
                || is_look_at_key(key.as_str())
            {
                state.look_at_override_rate = state.look_at_override_rate.max(weight);
            }
            if clip.override_mouth != crate::model::ExpressionOverride::None
                || is_mouth_key(key.as_str())
            {
                state.mouth_override_rate = state.mouth_override_rate.max(weight);
            }
        }

        state
    }

    fn apply_to_model(&self, model: &mut MmdModel, actual_weights: &HashMap<ExpressionKey, f32>) {
        for (key, morph_name) in &self.morph_names {
            if let Some(index) = model.morph_manager.find_morph_by_name(morph_name) {
                let weight = actual_weights.get(key).copied().unwrap_or(0.0);
                model.morph_manager.set_morph_weight(index, weight);
            }
        }
    }
}

fn synthesized_look_at_weights(direction: EyeDirection) -> HashMap<ExpressionKey, f32> {
    let mut weights = HashMap::new();
    let horizontal = (direction.yaw / 30.0).clamp(-1.0, 1.0);
    let vertical = (direction.pitch / 20.0).clamp(-1.0, 1.0);

    if horizontal < 0.0 {
        weights.insert(ExpressionPreset::LookLeft.into(), horizontal.abs());
    } else if horizontal > 0.0 {
        weights.insert(ExpressionPreset::LookRight.into(), horizontal);
    }

    if vertical < 0.0 {
        weights.insert(ExpressionPreset::LookDown.into(), vertical.abs());
    } else if vertical > 0.0 {
        weights.insert(ExpressionPreset::LookUp.into(), vertical);
    }

    weights
}

fn is_blink_key(key: &str) -> bool {
    matches!(key, "blink" | "blinkleft" | "blinkright")
}

fn is_look_at_key(key: &str) -> bool {
    matches!(key, "lookleft" | "lookright" | "lookup" | "lookdown")
}

fn is_mouth_key(key: &str) -> bool {
    matches!(
        key,
        "aa" | "a" | "ih" | "i" | "ou" | "u" | "ee" | "e" | "oh" | "o"
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{ExpressionOverride, VrmExpressionClip, VrmExpressions};

    fn runtime() -> ExpressionRuntime {
        let mut map = HashMap::new();
        map.insert(
            "blink".to_string(),
            VrmExpressionClip {
                is_binary: true,
                override_blink: ExpressionOverride::Block,
                ..VrmExpressionClip::default()
            },
        );
        map.insert(
            "aa".to_string(),
            VrmExpressionClip {
                override_mouth: ExpressionOverride::Blend,
                ..VrmExpressionClip::default()
            },
        );
        ExpressionRuntime::new(&VrmExpressions { map })
    }

    #[test]
    fn compute_state_should_apply_binary_threshold() {
        let runtime = runtime();
        let mut requested = HashMap::new();
        requested.insert(ExpressionPreset::Blink.into(), 0.49);
        let state = runtime.compute_state(&requested);
        assert_eq!(
            state.actual_weights.get(&ExpressionPreset::Blink.into()),
            None
        );

        requested.insert(ExpressionPreset::Blink.into(), 0.5);
        let state = runtime.compute_state(&requested);
        assert_eq!(
            state.actual_weights.get(&ExpressionPreset::Blink.into()),
            Some(&1.0)
        );
    }

    #[test]
    fn compute_state_should_track_override_rates() {
        let runtime = runtime();
        let mut requested = HashMap::new();
        requested.insert(ExpressionPreset::Blink.into(), 0.7);
        requested.insert(ExpressionPreset::Aa.into(), 0.4);
        let state = runtime.compute_state(&requested);
        assert_eq!(state.blink_override_rate, 1.0);
        assert_eq!(state.mouth_override_rate, 0.4);
    }

    #[test]
    fn synthesized_look_at_weights_should_choose_signed_channels() {
        let weights = synthesized_look_at_weights(EyeDirection {
            yaw: -30.0,
            pitch: 10.0,
        });
        assert_eq!(weights.get(&ExpressionPreset::LookLeft.into()), Some(&1.0));
        assert_eq!(weights.get(&ExpressionPreset::LookUp.into()), Some(&0.5));
        assert!(!weights.contains_key(&ExpressionPreset::LookRight.into()));
    }
}
