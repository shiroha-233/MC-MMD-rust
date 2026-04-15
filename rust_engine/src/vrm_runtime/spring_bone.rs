//! Spring-bone runtime adapted from UniVRM's spring-bone logic (MIT).

use glam::{Mat4, Quat, Vec3};

use crate::model::{
    ColliderShape, SpringBoneCollider, SpringBoneData, SpringBoneJoint, SpringBoneSpring,
};
use crate::skeleton::BoneManager;

#[derive(Clone, Copy, Debug)]
enum TailSpace {
    World,
    Center(usize),
}

#[derive(Clone, Copy, Debug)]
struct TailState {
    current_tail: Vec3,
    prev_tail: Vec3,
    space: TailSpace,
}

impl TailState {
    fn new(
        space: TailSpace,
        current_tail_world: Vec3,
        prev_tail_world: Vec3,
        bones: &BoneManager,
    ) -> Self {
        let current_tail = to_tail_space(space, current_tail_world, bones);
        let prev_tail = to_tail_space(space, prev_tail_world, bones);
        Self {
            current_tail,
            prev_tail,
            space,
        }
    }

    fn current_world(self, bones: &BoneManager) -> Vec3 {
        from_tail_space(self.space, self.current_tail, bones)
    }

    fn prev_world(self, bones: &BoneManager) -> Vec3 {
        from_tail_space(self.space, self.prev_tail, bones)
    }

    fn advance(&mut self, current_tail_world: Vec3, next_tail_world: Vec3, bones: &BoneManager) {
        self.prev_tail = to_tail_space(self.space, current_tail_world, bones);
        self.current_tail = to_tail_space(self.space, next_tail_world, bones);
    }
}

#[derive(Clone, Copy, Debug)]
struct SpringJointRuntime {
    bone_index: usize,
    bone_axis: Vec3,
    rest_length: f32,
    init_local_rotation: Quat,
    hit_radius: f32,
    stiffness: f32,
    gravity_power: f32,
    gravity_dir: Vec3,
    drag_force: f32,
    state: TailState,
}

#[derive(Clone, Debug)]
struct SpringChainRuntime {
    joints: Vec<SpringJointRuntime>,
    collider_indices: Vec<usize>,
}

#[derive(Clone, Debug)]
struct ResolvedCollider {
    node: usize,
    shape: ColliderShape,
}

pub struct SpringBoneRuntime {
    chains: Vec<SpringChainRuntime>,
    colliders: Vec<ResolvedCollider>,
}

impl SpringBoneRuntime {
    pub fn new(spring_bones: SpringBoneData, bones: &BoneManager) -> Self {
        let SpringBoneData {
            springs,
            colliders,
            collider_groups,
        } = spring_bones;

        let colliders = colliders
            .into_iter()
            .filter_map(|collider| resolve_collider(collider, bones))
            .collect::<Vec<_>>();

        let spring_meta = SpringBoneData {
            springs: Vec::new(),
            colliders: Vec::new(),
            collider_groups,
        };

        let chains = springs
            .into_iter()
            .filter_map(|spring| resolve_chain(spring, &spring_meta, &colliders, bones))
            .collect();

        Self { chains, colliders }
    }

    pub fn process(&mut self, bones: &mut BoneManager, delta_time: f32) {
        if self.chains.is_empty() || delta_time <= 0.0 {
            return;
        }

        for chain in &mut self.chains {
            for joint in &mut chain.joints {
                let Some(transform) = bones.get_bone(joint.bone_index) else {
                    continue;
                };
                let current_tail = joint.state.current_world(bones);
                let prev_tail = joint.state.prev_world(bones);
                let bone_position = transform.position();
                let parent_rotation = transform
                    .parent_id()
                    .and_then(|index| bones.get_bone(index))
                    .map(|parent| parent.rotation())
                    .unwrap_or(Quat::IDENTITY);

                let mut next_tail = current_tail
                    + (current_tail - prev_tail) * (1.0 - joint.drag_force)
                    + (parent_rotation * joint.init_local_rotation * joint.bone_axis)
                        * (joint.stiffness * delta_time)
                    + joint.gravity_dir * (joint.gravity_power * delta_time);

                let direction = (next_tail - bone_position).normalize_or_zero();
                if direction.length_squared() <= 1e-6 {
                    next_tail = bone_position
                        + (parent_rotation * joint.init_local_rotation * joint.bone_axis)
                            * joint.rest_length;
                } else {
                    next_tail = bone_position + direction * joint.rest_length;
                }

                for &collider_index in &chain.collider_indices {
                    let Some(collider) = self.colliders.get(collider_index) else {
                        continue;
                    };
                    if let Some(pushed_tail) =
                        collide_tail(bones, collider, joint.hit_radius, next_tail)
                    {
                        let corrected_dir = (pushed_tail - bone_position).normalize_or_zero();
                        if corrected_dir.length_squared() > 1e-6 {
                            next_tail = bone_position + corrected_dir * joint.rest_length;
                        }
                    }
                }

                let world_rotation = world_rotation_from_tail(
                    bones,
                    joint.bone_index,
                    joint.init_local_rotation,
                    joint.bone_axis,
                    next_tail,
                );
                bones.set_global_transform(
                    joint.bone_index,
                    Mat4::from_rotation_translation(world_rotation, bone_position),
                );
                joint.state.advance(current_tail, next_tail, bones);
            }
        }
    }

    #[cfg(test)]
    pub fn len(&self) -> usize {
        self.chains.len()
    }
}

fn resolve_collider(collider: SpringBoneCollider, bones: &BoneManager) -> Option<ResolvedCollider> {
    bones.get_bone(collider.node)?;
    Some(ResolvedCollider {
        node: collider.node,
        shape: collider.shape,
    })
}

fn resolve_chain(
    spring: SpringBoneSpring,
    data: &SpringBoneData,
    colliders: &[ResolvedCollider],
    bones: &BoneManager,
) -> Option<SpringChainRuntime> {
    let center = spring
        .center
        .filter(|index| bones.get_bone(*index).is_some());
    let space = center.map(TailSpace::Center).unwrap_or(TailSpace::World);
    let collider_indices = spring
        .collider_groups
        .iter()
        .filter_map(|index| data.collider_groups.get(*index))
        .flat_map(|group| group.colliders.iter().copied())
        .filter(|index| *index < colliders.len())
        .collect::<Vec<_>>();

    let mut joints = Vec::new();
    for (joint_index, joint) in spring.joints.iter().enumerate() {
        let Some(resolved) = resolve_joint(joint, spring.joints.get(joint_index + 1), space, bones)
        else {
            continue;
        };
        joints.push(resolved);
    }

    if joints.is_empty() {
        None
    } else {
        Some(SpringChainRuntime {
            joints,
            collider_indices,
        })
    }
}

fn resolve_joint(
    joint: &SpringBoneJoint,
    next_joint: Option<&SpringBoneJoint>,
    space: TailSpace,
    bones: &BoneManager,
) -> Option<SpringJointRuntime> {
    let bone = bones.get_bone(joint.node)?;
    let bone_transform = bone.global_transform();
    let child_world_position = if let Some(next_joint) = next_joint {
        bones.get_bone(next_joint.node)?.position()
    } else {
        virtual_tail_world_position(bone, bones)?
    };

    let local_child_position = bone_transform
        .inverse()
        .transform_point3(child_world_position);
    let rest_length = local_child_position.length();
    if rest_length <= 1e-6 {
        return None;
    }

    let init_local_rotation = bone
        .local_transform()
        .to_scale_rotation_translation()
        .1
        .normalize();
    let state = TailState::new(space, child_world_position, child_world_position, bones);

    Some(SpringJointRuntime {
        bone_index: joint.node,
        bone_axis: local_child_position.normalize(),
        rest_length,
        init_local_rotation,
        hit_radius: joint.hit_radius,
        stiffness: joint.stiffness,
        gravity_power: joint.gravity_power,
        gravity_dir: Vec3::from_array(joint.gravity_dir).normalize_or_zero(),
        drag_force: joint.drag_force.clamp(0.0, 1.0),
        state,
    })
}

fn virtual_tail_world_position(bone: &crate::skeleton::Bone, bones: &BoneManager) -> Option<Vec3> {
    let parent_position = bone
        .parent_id()
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.position())?;
    let direction = (bone.position() - parent_position).normalize_or_zero();
    if direction.length_squared() <= 1e-6 {
        return None;
    }
    Some(bone.position() + direction * 0.07)
}

fn to_tail_space(space: TailSpace, world: Vec3, bones: &BoneManager) -> Vec3 {
    match space {
        TailSpace::World => world,
        TailSpace::Center(center_index) => bones
            .get_bone(center_index)
            .map(|center| center.global_transform().inverse().transform_point3(world))
            .unwrap_or(world),
    }
}

fn from_tail_space(space: TailSpace, value: Vec3, bones: &BoneManager) -> Vec3 {
    match space {
        TailSpace::World => value,
        TailSpace::Center(center_index) => bones
            .get_bone(center_index)
            .map(|center| center.global_transform().transform_point3(value))
            .unwrap_or(value),
    }
}

fn world_rotation_from_tail(
    bones: &BoneManager,
    bone_index: usize,
    init_local_rotation: Quat,
    bone_axis: Vec3,
    next_tail: Vec3,
) -> Quat {
    let Some(bone) = bones.get_bone(bone_index) else {
        return Quat::IDENTITY;
    };
    let parent_rotation = bone
        .parent_id()
        .and_then(|index| bones.get_bone(index))
        .map(|parent| parent.rotation())
        .unwrap_or(Quat::IDENTITY);
    let baseline_rotation = parent_rotation * init_local_rotation;
    let from = (baseline_rotation * bone_axis).normalize_or_zero();
    let to = (next_tail - bone.position()).normalize_or_zero();
    if from.length_squared() <= 1e-6 || to.length_squared() <= 1e-6 {
        baseline_rotation
    } else {
        (Quat::from_rotation_arc(from, to) * baseline_rotation).normalize()
    }
}

fn collide_tail(
    bones: &BoneManager,
    collider: &ResolvedCollider,
    hit_radius: f32,
    next_tail: Vec3,
) -> Option<Vec3> {
    let collider_transform = bones.get_bone(collider.node)?.global_transform();
    match collider.shape {
        ColliderShape::Sphere { offset, radius } => {
            let center = collider_transform.transform_point3(Vec3::from_array(offset));
            push_out_from_sphere(center, radius + hit_radius, next_tail)
        }
        ColliderShape::Capsule {
            offset,
            tail,
            radius,
        } => {
            let start = collider_transform.transform_point3(Vec3::from_array(offset));
            let end = collider_transform.transform_point3(Vec3::from_array(tail));
            let closest = closest_point_on_segment(start, end, next_tail);
            push_out_from_sphere(closest, radius + hit_radius, next_tail)
        }
    }
}

fn push_out_from_sphere(center: Vec3, radius: f32, point: Vec3) -> Option<Vec3> {
    let offset = point - center;
    if offset.length_squared() > radius * radius {
        return None;
    }
    let normal = if offset.length_squared() <= 1e-6 {
        Vec3::Y
    } else {
        offset.normalize()
    };
    Some(center + normal * radius)
}

fn closest_point_on_segment(start: Vec3, end: Vec3, point: Vec3) -> Vec3 {
    let segment = end - start;
    let length_squared = segment.length_squared();
    if length_squared <= 1e-6 {
        return start;
    }
    let t = ((point - start).dot(segment) / length_squared).clamp(0.0, 1.0);
    start + segment * t
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::model::{
        ColliderShape, SpringBoneCollider, SpringBoneColliderGroup, SpringBoneData,
    };
    use crate::skeleton::{BoneLink, BoneManager};

    fn build_chain_bones() -> BoneManager {
        let mut bones = BoneManager::new();

        let mut root = BoneLink::new("root".to_string());
        root.initial_position = Vec3::ZERO;
        root.parent_index = -1;
        bones.add_bone(root);

        let mut bone_a = BoneLink::new("bone_a".to_string());
        bone_a.initial_position = Vec3::new(0.0, 1.0, 0.0);
        bone_a.parent_index = 0;
        bones.add_bone(bone_a);

        let mut bone_b = BoneLink::new("bone_b".to_string());
        bone_b.initial_position = Vec3::new(0.0, 2.0, 0.0);
        bone_b.parent_index = 1;
        bones.add_bone(bone_b);

        bones.build_hierarchy();
        bones
    }

    #[test]
    fn sphere_collision_should_push_tail_outside_radius() {
        let bones = build_chain_bones();
        let collider = ResolvedCollider {
            node: 0,
            shape: ColliderShape::Sphere {
                offset: [0.0, 1.0, 0.0],
                radius: 0.5,
            },
        };

        let pushed = collide_tail(&bones, &collider, 0.2, Vec3::new(0.0, 1.1, 0.0)).unwrap();
        assert!((pushed - Vec3::new(0.0, 1.0, 0.0)).length() >= 0.7 - 1e-5);
    }

    #[test]
    fn capsule_collision_should_push_from_segment() {
        let bones = build_chain_bones();
        let collider = ResolvedCollider {
            node: 0,
            shape: ColliderShape::Capsule {
                offset: [0.0, 0.5, 0.0],
                tail: [0.0, 1.5, 0.0],
                radius: 0.2,
            },
        };

        let pushed = collide_tail(&bones, &collider, 0.2, Vec3::new(0.1, 1.0, 0.0)).unwrap();
        assert!(pushed.x.abs() >= 0.39);
    }

    #[test]
    fn tail_state_should_round_trip_center_space() {
        let bones = build_chain_bones();
        let state = TailState::new(
            TailSpace::Center(1),
            Vec3::new(0.0, 2.0, 0.0),
            Vec3::new(0.0, 2.0, 0.0),
            &bones,
        );
        assert_eq!(state.current_world(&bones), Vec3::new(0.0, 2.0, 0.0));
    }

    #[test]
    fn process_should_keep_chain_initialized() {
        let mut bones = build_chain_bones();
        let runtime_data = SpringBoneData {
            springs: vec![SpringBoneSpring {
                joints: vec![
                    SpringBoneJoint {
                        node: 1,
                        hit_radius: 0.0,
                        stiffness: 1.0,
                        gravity_power: 0.0,
                        gravity_dir: [0.0, -1.0, 0.0],
                        drag_force: 0.2,
                    },
                    SpringBoneJoint {
                        node: 2,
                        hit_radius: 0.0,
                        stiffness: 1.0,
                        gravity_power: 0.0,
                        gravity_dir: [0.0, -1.0, 0.0],
                        drag_force: 0.2,
                    },
                ],
                collider_groups: vec![0],
                center: Some(1),
            }],
            colliders: vec![SpringBoneCollider {
                node: 0,
                shape: ColliderShape::Sphere {
                    offset: [0.0, -10.0, 0.0],
                    radius: 0.1,
                },
            }],
            collider_groups: vec![SpringBoneColliderGroup { colliders: vec![0] }],
        };

        let mut runtime = SpringBoneRuntime::new(runtime_data, &bones);
        runtime.process(&mut bones, 0.016);

        assert_eq!(runtime.len(), 1);
        let bone = bones.get_bone(1).unwrap();
        assert!(bone.rotation().length_squared() > 0.0);
    }
}
