//! First-person visibility flow adapted from UniVRM's first-person runtime
//! (`RendererFirstPersonFlags` / `Vrm10FirstPersonLayerSettings`, MIT).

use glam::Vec3;

use crate::model::{FirstPersonConfig, FirstPersonType, LookAtConfig, MmdModel};

pub struct FirstPersonSnapshot {
    pub hmd_visible_materials: Vec<bool>,
    pub mirror_visible_materials: Vec<bool>,
    pub source: &'static str,
}

pub struct FirstPersonRuntime {
    config: FirstPersonConfig,
    look_at: Option<LookAtConfig>,
}

impl FirstPersonRuntime {
    pub fn new(config: FirstPersonConfig, look_at: Option<LookAtConfig>) -> Self {
        Self { config, look_at }
    }

    pub fn capture(
        &self,
        model: &mut MmdModel,
        baseline_visible: &[bool],
        first_person_enabled: bool,
    ) -> FirstPersonSnapshot {
        if let Some(snapshot) =
            self.capture_from_annotations(model, baseline_visible, first_person_enabled)
        {
            return snapshot;
        }

        let (mut hmd_visible_materials, mirror_visible_materials) =
            model.build_first_person_heuristic_masks(baseline_visible);
        if !first_person_enabled {
            hmd_visible_materials.clone_from(&mirror_visible_materials);
        }

        FirstPersonSnapshot {
            hmd_visible_materials,
            mirror_visible_materials,
            source: "heuristic",
        }
    }

    pub fn resolve_view_anchor_model(&self, model: &mut MmdModel) -> Vec3 {
        let eye_anchor = model.get_eye_bone_animated_position();
        if eye_anchor.length_squared() > 1e-6 {
            return eye_anchor;
        }

        if let Some(anchor) = self
            .config
            .bone
            .and_then(|bone| resolve_bone_anchor(model, bone, self.config.bone_offset))
        {
            return anchor;
        }

        if let Some(look_at) = &self.look_at {
            let look_at_anchor = self
                .config
                .bone
                .and_then(|bone| resolve_bone_anchor(model, bone, look_at.offset_from_head_bone))
                .or_else(|| resolve_named_head_anchor(model, look_at.offset_from_head_bone));
            if let Some(anchor) = look_at_anchor {
                return anchor;
            }
        }

        if let Some(anchor) = resolve_named_head_anchor(model, [0.0, 0.0, 0.0]) {
            return anchor;
        }

        Vec3::new(0.0, model.get_head_bone_rest_position_y(), 0.0)
    }

    fn capture_from_annotations(
        &self,
        model: &mut MmdModel,
        baseline_visible: &[bool],
        first_person_enabled: bool,
    ) -> Option<FirstPersonSnapshot> {
        if self.config.mesh_annotations.is_empty() {
            return None;
        }

        let material_count = model.material_count();
        let mut hmd_visible_materials = baseline_visible.to_vec();
        let mut mirror_visible_materials = baseline_visible.to_vec();
        if hmd_visible_materials.len() != material_count {
            hmd_visible_materials = vec![true; material_count];
        }
        if mirror_visible_materials.len() != material_count {
            mirror_visible_materials = vec![true; material_count];
        }

        let (head_materials, body_materials) = model.classify_head_materials();

        for annotation in &self.config.mesh_annotations {
            for &material_id in &annotation.material_ids {
                if material_id >= material_count {
                    continue;
                }
                match annotation.first_person_type {
                    FirstPersonType::Auto => {
                        mirror_visible_materials[material_id] = true;
                        if head_materials.contains(&material_id)
                            && !body_materials.contains(&material_id)
                        {
                            hmd_visible_materials[material_id] = false;
                        } else {
                            hmd_visible_materials[material_id] = true;
                        }
                    }
                    FirstPersonType::Both => {
                        hmd_visible_materials[material_id] = true;
                        mirror_visible_materials[material_id] = true;
                    }
                    FirstPersonType::ThirdPersonOnly => {
                        hmd_visible_materials[material_id] = false;
                        mirror_visible_materials[material_id] = true;
                    }
                    FirstPersonType::FirstPersonOnly => {
                        hmd_visible_materials[material_id] = true;
                        mirror_visible_materials[material_id] = false;
                    }
                }
            }
        }

        if !first_person_enabled {
            hmd_visible_materials.clone_from(&mirror_visible_materials);
        }

        Some(FirstPersonSnapshot {
            hmd_visible_materials,
            mirror_visible_materials,
            source: "annotation",
        })
    }
}

fn resolve_bone_anchor(model: &MmdModel, bone_index: usize, offset: [f32; 3]) -> Option<Vec3> {
    model
        .bone_manager
        .get_bone(bone_index)
        .map(|_| {
            model
                .bone_manager
                .get_global_transform(bone_index)
                .w_axis
                .truncate()
        })
        .map(|position| position + Vec3::from_array(offset))
}

fn resolve_named_head_anchor(model: &MmdModel, offset: [f32; 3]) -> Option<Vec3> {
    ["頭", "head", "Head", "neck", "Neck"]
        .iter()
        .find_map(|name| model.bone_manager.find_bone_by_name(name))
        .and_then(|bone| resolve_bone_anchor(model, bone, offset))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{FirstPersonMeshAnnotation, FirstPersonType};
    use crate::skeleton::{BoneLink, BoneManager};

    #[test]
    fn capture_from_annotations_should_apply_view_masks() {
        let runtime = FirstPersonRuntime::new(
            FirstPersonConfig {
                mesh_annotations: vec![
                    FirstPersonMeshAnnotation {
                        material_ids: vec![0, 1],
                        first_person_type: FirstPersonType::ThirdPersonOnly,
                    },
                    FirstPersonMeshAnnotation {
                        material_ids: vec![2],
                        first_person_type: FirstPersonType::FirstPersonOnly,
                    },
                ],
                ..FirstPersonConfig::default()
            },
            None,
        );

        let mut model = MmdModel::new();
        model.materials = vec![Default::default(), Default::default(), Default::default()];
        model.submeshes = vec![];
        let snapshot = runtime
            .capture_from_annotations(&mut model, &[true, true, true], true)
            .expect("annotation snapshot");
        assert_eq!(snapshot.hmd_visible_materials, vec![false, false, true]);
        assert_eq!(snapshot.mirror_visible_materials, vec![true, true, false]);
    }

    #[test]
    fn resolve_view_anchor_model_should_prefer_eye_midpoint_over_head_metadata() {
        let runtime = FirstPersonRuntime::new(
            FirstPersonConfig {
                bone: Some(1),
                bone_offset: [0.0, 0.5, 0.0],
                ..FirstPersonConfig::default()
            },
            None,
        );
        let mut model = make_anchor_test_model(true, true);

        let anchor = runtime.resolve_view_anchor_model(&mut model);

        assert_eq!(anchor, Vec3::new(0.0, 17.0, 0.2));
    }

    #[test]
    fn resolve_view_anchor_model_should_use_single_eye_when_only_one_is_available() {
        let runtime = FirstPersonRuntime::new(FirstPersonConfig::default(), None);
        let mut model = make_anchor_test_model(true, false);

        let anchor = runtime.resolve_view_anchor_model(&mut model);

        assert_eq!(anchor, Vec3::new(-0.3, 17.0, 0.2));
    }

    #[test]
    fn resolve_view_anchor_model_should_fall_back_to_configured_bone_when_eyes_are_missing() {
        let runtime = FirstPersonRuntime::new(
            FirstPersonConfig {
                bone: Some(1),
                bone_offset: [0.0, 0.5, 0.0],
                ..FirstPersonConfig::default()
            },
            None,
        );
        let mut model = make_anchor_test_model(false, false);

        let anchor = runtime.resolve_view_anchor_model(&mut model);

        assert_eq!(anchor, Vec3::new(0.0, 17.5, 0.0));
    }

    #[test]
    fn resolve_view_anchor_model_should_fall_back_to_look_at_offset_then_head_anchor() {
        let runtime = FirstPersonRuntime::new(
            FirstPersonConfig::default(),
            Some(LookAtConfig {
                offset_from_head_bone: [0.0, 0.25, 0.1],
                ..LookAtConfig::default()
            }),
        );
        let mut model = make_anchor_test_model(false, false);

        let anchor = runtime.resolve_view_anchor_model(&mut model);

        assert_eq!(anchor, Vec3::new(0.0, 17.25, 0.1));
    }

    fn make_anchor_test_model(has_left_eye: bool, has_right_eye: bool) -> MmdModel {
        let mut model = MmdModel::new();
        model.bone_manager = make_anchor_test_bones(has_left_eye, has_right_eye);
        model
    }

    fn make_anchor_test_bones(has_left_eye: bool, has_right_eye: bool) -> BoneManager {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut head = BoneLink::new("Head".to_string());
        head.initial_position = Vec3::new(0.0, 17.0, 0.0);
        head.parent_index = 0;
        bones.add_bone(head);

        let mut neck = BoneLink::new("Neck".to_string());
        neck.initial_position = Vec3::new(0.0, 15.0, 0.0);
        neck.parent_index = 0;
        bones.add_bone(neck);

        if has_left_eye {
            let mut left_eye = BoneLink::new("LeftEye".to_string());
            left_eye.initial_position = Vec3::new(-0.3, 17.0, 0.2);
            left_eye.parent_index = 1;
            bones.add_bone(left_eye);
        }

        if has_right_eye {
            let mut right_eye = BoneLink::new("RightEye".to_string());
            right_eye.initial_position = Vec3::new(0.3, 17.0, 0.2);
            right_eye.parent_index = 1;
            bones.add_bone(right_eye);
        }

        bones.build_hierarchy();
        bones
    }
}
