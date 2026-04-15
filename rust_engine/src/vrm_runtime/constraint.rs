//! Constraint runtime adapted from UniVRM's `Vrm10Constraint*` runtime ordering
//! and formulas (MIT).

use glam::{Mat4, Quat, Vec3};

use crate::model::{ConstraintAxis, NodeConstraint, NodeConstraintKind};
use crate::skeleton::BoneManager;

#[derive(Clone, Debug)]
struct ConstraintNodeState {
    target: usize,
    target_init_local_rotation: Quat,
    source: usize,
    source_init_local_rotation: Quat,
    kind: NodeConstraintKind,
}

pub struct ConstraintRuntime {
    constraints: Vec<ConstraintNodeState>,
}

impl ConstraintRuntime {
    pub fn new(constraints: Vec<NodeConstraint>, bones: &BoneManager) -> Self {
        let constraints = constraints
            .into_iter()
            .filter_map(|constraint| resolve_constraint(constraint, bones))
            .collect();
        Self { constraints }
    }

    pub fn process(&self, bones: &mut BoneManager) {
        for constraint in &self.constraints {
            apply_constraint(bones, constraint);
        }
    }

    #[cfg(test)]
    #[allow(dead_code)]
    pub fn len(&self) -> usize {
        self.constraints.len()
    }
}

fn resolve_constraint(
    constraint: NodeConstraint,
    bones: &BoneManager,
) -> Option<ConstraintNodeState> {
    let target = bones.get_bone(constraint.target)?;
    let (source_index, kind) = match constraint.kind {
        NodeConstraintKind::Roll {
            source,
            roll_axis,
            weight,
        } => (
            source,
            NodeConstraintKind::Roll {
                source,
                roll_axis,
                weight: weight.clamp(0.0, 1.0),
            },
        ),
        NodeConstraintKind::Aim {
            source,
            aim_axis,
            weight,
        } => (
            source,
            NodeConstraintKind::Aim {
                source,
                aim_axis,
                weight: weight.clamp(0.0, 1.0),
            },
        ),
        NodeConstraintKind::Rotation { source, weight } => (
            source,
            NodeConstraintKind::Rotation {
                source,
                weight: weight.clamp(0.0, 1.0),
            },
        ),
    };
    let source = bones.get_bone(source_index)?;

    Some(ConstraintNodeState {
        target: constraint.target,
        target_init_local_rotation: local_rotation(target.local_transform()),
        source: source_index,
        source_init_local_rotation: local_rotation(source.local_transform()),
        kind,
    })
}

fn apply_constraint(bones: &mut BoneManager, state: &ConstraintNodeState) {
    match state.kind {
        NodeConstraintKind::Rotation { weight, .. } => {
            let src_current_local = current_local_rotation(bones, state.source);
            let src_delta_local = state.source_init_local_rotation.inverse() * src_current_local;
            let target_local = state
                .target_init_local_rotation
                .slerp(
                    (state.target_init_local_rotation * src_delta_local).normalize_or_identity(),
                    weight,
                )
                .normalize_or_identity();
            apply_local_rotation(bones, state.target, target_local);
        }
        NodeConstraintKind::Roll {
            roll_axis, weight, ..
        } => {
            let src_current_local = current_local_rotation(bones, state.source);
            let delta_src_quat = state.source_init_local_rotation.inverse() * src_current_local;
            let delta_src_quat_in_parent = state.source_init_local_rotation
                * delta_src_quat
                * state.source_init_local_rotation.inverse();
            let delta_src_quat_in_dst = state.target_init_local_rotation.inverse()
                * delta_src_quat_in_parent
                * state.target_init_local_rotation;

            let roll_axis = axis_vector(roll_axis);
            let to_vec = (delta_src_quat_in_dst * roll_axis).normalize_or_zero();
            if to_vec.length_squared() <= 1e-6 {
                return;
            }
            let from_to_quat = Quat::from_rotation_arc(roll_axis, to_vec);
            let desired_local =
                state.target_init_local_rotation * from_to_quat.inverse() * delta_src_quat_in_dst;
            let target_local = state
                .target_init_local_rotation
                .slerp(desired_local, weight)
                .normalize_or_identity();
            apply_local_rotation(bones, state.target, target_local);
        }
        NodeConstraintKind::Aim {
            aim_axis, weight, ..
        } => {
            let target = match bones.get_bone(state.target) {
                Some(target) => target,
                None => return,
            };
            let source = match bones.get_bone(state.source) {
                Some(source) => source,
                None => return,
            };
            let dst_parent_world_rot = target
                .parent_id()
                .and_then(|index| bones.get_bone(index))
                .map(|parent| parent.rotation())
                .unwrap_or(Quat::IDENTITY);
            let from_vec =
                (dst_parent_world_rot * state.target_init_local_rotation) * axis_vector(aim_axis);
            let to_vec = (source.position() - target.position()).normalize_or_zero();
            if from_vec.length_squared() <= 1e-6 || to_vec.length_squared() <= 1e-6 {
                return;
            }
            let from_to_quat = Quat::from_rotation_arc(from_vec.normalize(), to_vec);
            let desired_local = dst_parent_world_rot.inverse()
                * from_to_quat
                * dst_parent_world_rot
                * state.target_init_local_rotation;
            let target_local = state
                .target_init_local_rotation
                .slerp(desired_local, weight)
                .normalize_or_identity();
            apply_local_rotation(bones, state.target, target_local);
        }
    }
}

fn apply_local_rotation(bones: &mut BoneManager, target_index: usize, target_local_rotation: Quat) {
    let Some(target) = bones.get_bone(target_index) else {
        return;
    };
    let (_, _, local_translation) = target.local_transform().to_scale_rotation_translation();
    let parent_world = target
        .parent_id()
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.global_transform())
        .unwrap_or(Mat4::IDENTITY);
    let world_transform =
        parent_world * Mat4::from_rotation_translation(target_local_rotation, local_translation);
    bones.set_global_transform(target_index, world_transform);
}

fn current_local_rotation(bones: &BoneManager, index: usize) -> Quat {
    bones
        .get_bone(index)
        .map(|bone| local_rotation(bone.local_transform()))
        .unwrap_or(Quat::IDENTITY)
}

fn local_rotation(transform: Mat4) -> Quat {
    transform.to_scale_rotation_translation().1.normalize()
}

fn axis_vector(axis: ConstraintAxis) -> Vec3 {
    match axis {
        ConstraintAxis::X => Vec3::X,
        ConstraintAxis::Y => Vec3::Y,
        ConstraintAxis::Z => Vec3::Z,
        ConstraintAxis::NegativeX => -Vec3::X,
        ConstraintAxis::NegativeY => -Vec3::Y,
        ConstraintAxis::NegativeZ => -Vec3::Z,
    }
}

trait NormalizeOrIdentity {
    fn normalize_or_identity(self) -> Self;
}

impl NormalizeOrIdentity for Quat {
    fn normalize_or_identity(self) -> Self {
        if self.length_squared() <= 1e-6 {
            Quat::IDENTITY
        } else {
            self.normalize()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::skeleton::{BoneLink, BoneManager};

    fn build_simple_bones() -> BoneManager {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut source = BoneLink::new("source".to_string());
        source.initial_position = Vec3::new(0.0, 1.0, 0.0);
        source.parent_index = 0;
        bones.add_bone(source);

        let mut target = BoneLink::new("target".to_string());
        target.initial_position = Vec3::new(1.0, 1.0, 0.0);
        target.parent_index = 0;
        bones.add_bone(target);

        bones.build_hierarchy();
        bones
    }

    #[test]
    fn rotation_constraint_should_copy_source_local_rotation() {
        let mut bones = build_simple_bones();
        let runtime = ConstraintRuntime::new(
            vec![NodeConstraint {
                target: 2,
                kind: NodeConstraintKind::Rotation {
                    source: 1,
                    weight: 1.0,
                },
            }],
            &bones,
        );
        let source_rotation = Quat::from_rotation_y(0.5);
        bones.set_global_transform(
            1,
            Mat4::from_rotation_translation(source_rotation, Vec3::new(0.0, 1.0, 0.0)),
        );
        runtime.process(&mut bones);

        let target_local = local_rotation(bones.get_bone(2).unwrap().local_transform());
        assert!((target_local.dot(source_rotation).abs() - 1.0).abs() < 1e-4);
    }

    #[test]
    fn roll_constraint_should_rotate_only_around_requested_axis() {
        let mut bones = build_simple_bones();
        let runtime = ConstraintRuntime::new(
            vec![NodeConstraint {
                target: 2,
                kind: NodeConstraintKind::Roll {
                    source: 1,
                    roll_axis: ConstraintAxis::X,
                    weight: 1.0,
                },
            }],
            &bones,
        );
        let source_rotation = Quat::from_rotation_x(0.6);
        bones.set_global_transform(
            1,
            Mat4::from_rotation_translation(source_rotation, Vec3::new(0.0, 1.0, 0.0)),
        );
        runtime.process(&mut bones);

        let target_local = local_rotation(bones.get_bone(2).unwrap().local_transform());
        let rotated_up = target_local * Vec3::Y;
        assert!(rotated_up.z.abs() > 0.0);
    }

    #[test]
    fn aim_constraint_should_turn_towards_source() {
        let mut bones = build_simple_bones();
        bones.set_global_transform(
            1,
            Mat4::from_rotation_translation(Quat::IDENTITY, Vec3::new(2.0, 1.0, -1.0)),
        );

        let runtime = ConstraintRuntime::new(
            vec![NodeConstraint {
                target: 2,
                kind: NodeConstraintKind::Aim {
                    source: 1,
                    aim_axis: ConstraintAxis::NegativeZ,
                    weight: 1.0,
                },
            }],
            &bones,
        );
        runtime.process(&mut bones);

        let target_rotation = bones.get_bone(2).unwrap().rotation();
        let facing = target_rotation * -Vec3::Z;
        let to_source = (bones.get_bone(1).unwrap().position()
            - bones.get_bone(2).unwrap().position())
        .normalize();
        assert!(facing.dot(to_source) > 0.99);
    }
}
