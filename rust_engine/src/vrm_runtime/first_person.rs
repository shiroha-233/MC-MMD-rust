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
        if let Some(anchor) = resolve_head_anchor(model, self.config.bone, self.config.bone_offset)
        {
            return anchor;
        }

        if let Some(look_at) = &self.look_at {
            if let Some(anchor) =
                resolve_head_anchor(model, self.config.bone, look_at.offset_from_head_bone)
            {
                return anchor;
            }
        }

        let eye_anchor = model.get_eye_bone_animated_position();
        if eye_anchor.length_squared() > 1e-6 {
            eye_anchor
        } else {
            Vec3::new(0.0, model.get_head_bone_rest_position_y(), 0.0)
        }
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

fn resolve_head_anchor(
    model: &MmdModel,
    preferred_bone: Option<usize>,
    offset: [f32; 3],
) -> Option<Vec3> {
    let head_index = preferred_bone.or_else(|| {
        ["頭", "head", "Head", "neck", "Neck"]
            .iter()
            .find_map(|name| model.bone_manager.find_bone_by_name(name))
    })?;
    Some(
        model
            .bone_manager
            .get_global_transform(head_index)
            .w_axis
            .truncate()
            + Vec3::from_array(offset),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{FirstPersonMeshAnnotation, FirstPersonType};

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
}
