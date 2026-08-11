use std::cmp::Ordering;
use std::collections::HashMap;

use glam::Vec3;
use mmd::pmx::joint::Joint as PmxJoint;
use mmd::pmx::rigid_body::RigidBody as PmxRigidBody;

use super::bullet_ffi::{ConstraintDiagnostic, ContactManifold};

pub(crate) const CONTACT_TOP_N: usize = 5;

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct ContactPeak {
    pub body_a_index: usize,
    pub body_b_index: usize,
    pub contact_count: i32,
    pub max_penetration_depth: f32,
    pub max_applied_impulse: f32,
    pub total_applied_impulse: f32,
    pub point_a: Vec3,
    pub point_b: Vec3,
    pub normal_on_b: Vec3,
}

#[derive(Default)]
pub(crate) struct ContactWindow {
    pairs: HashMap<(usize, usize), ContactPeak>,
}

impl ContactWindow {
    pub fn observe(&mut self, manifold: ContactManifold, body_a: usize, body_b: usize) {
        let (key, reversed) = if body_a <= body_b {
            ((body_a, body_b), false)
        } else {
            ((body_b, body_a), true)
        };
        let entry = self.pairs.entry(key).or_insert_with(|| ContactPeak {
            body_a_index: key.0,
            body_b_index: key.1,
            ..ContactPeak::default()
        });
        entry.contact_count = entry.contact_count.max(manifold.contact_count);
        entry.max_applied_impulse = entry
            .max_applied_impulse
            .max(finite_or_infinity(manifold.max_applied_impulse));
        entry.total_applied_impulse += finite_or_infinity(manifold.total_applied_impulse);

        let depth = finite_or_infinity(manifold.max_penetration_depth);
        if depth > entry.max_penetration_depth {
            entry.max_penetration_depth = depth;
            if reversed {
                entry.point_a = manifold.point_b;
                entry.point_b = manifold.point_a;
                entry.normal_on_b = -manifold.normal_on_b;
            } else {
                entry.point_a = manifold.point_a;
                entry.point_b = manifold.point_b;
                entry.normal_on_b = manifold.normal_on_b;
            }
        }
    }

    pub fn top(&self) -> Vec<ContactPeak> {
        let mut contacts: Vec<_> = self.pairs.values().copied().collect();
        contacts.sort_by(|a, b| {
            b.max_penetration_depth
                .partial_cmp(&a.max_penetration_depth)
                .unwrap_or(Ordering::Equal)
                .then_with(|| {
                    b.total_applied_impulse
                        .partial_cmp(&a.total_applied_impulse)
                        .unwrap_or(Ordering::Equal)
                })
        });
        contacts.truncate(CONTACT_TOP_N);
        contacts
    }
}

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct JointLimitPeak {
    pub joint_index: usize,
    pub linear_position: Vec3,
    pub angular_position: Vec3,
    pub linear_violation: Vec3,
    pub angular_violation: Vec3,
    pub max_violation: f32,
}

impl JointLimitPeak {
    pub fn observe(&mut self, joint_index: usize, diagnostic: ConstraintDiagnostic) {
        let max_violation = diagnostic
            .linear_violation
            .max_element()
            .max(diagnostic.angular_violation.max_element());
        let max_violation = finite_or_infinity(max_violation);
        if max_violation > self.max_violation {
            *self = Self {
                joint_index,
                linear_position: diagnostic.linear_position,
                angular_position: diagnostic.angular_position,
                linear_violation: diagnostic.linear_violation,
                angular_violation: diagnostic.angular_violation,
                max_violation,
            };
        }
    }
}

pub(crate) fn model_topology_signature(
    rigid_bodies: &[PmxRigidBody],
    joints: &[PmxJoint],
) -> String {
    let mut hash = Fnv1a64::default();
    hash.write_usize(rigid_bodies.len());
    hash.write_usize(joints.len());
    for body in rigid_bodies {
        hash.write_str(&body.local_name);
        hash.write_i32(body.bone_index);
        hash.write_u8(body.group);
        hash.write_u16(body.un_collision_group_flag);
        hash.write_u8(body.mode as u8);
    }
    for joint in joints {
        hash.write_str(&joint.local_name);
        hash.write_i32(joint.rigid_body_a_index);
        hash.write_i32(joint.rigid_body_b_index);
    }
    format!("fnv1a64:{:016X}", hash.0)
}

fn finite_or_infinity(value: f32) -> f32 {
    if value.is_finite() {
        value
    } else {
        f32::INFINITY
    }
}

struct Fnv1a64(u64);

impl Default for Fnv1a64 {
    fn default() -> Self {
        Self(0xcbf29ce484222325)
    }
}

impl Fnv1a64 {
    fn write(&mut self, bytes: &[u8]) {
        for byte in bytes {
            self.0 ^= u64::from(*byte);
            self.0 = self.0.wrapping_mul(0x100000001b3);
        }
    }

    fn write_str(&mut self, value: &str) {
        self.write_usize(value.len());
        self.write(value.as_bytes());
    }

    fn write_usize(&mut self, value: usize) {
        self.write(&(value as u64).to_le_bytes());
    }

    fn write_i32(&mut self, value: i32) {
        self.write(&value.to_le_bytes());
    }

    fn write_u16(&mut self, value: u16) {
        self.write(&value.to_le_bytes());
    }

    fn write_u8(&mut self, value: u8) {
        self.write(&[value]);
    }
}

#[cfg(test)]
mod tests {
    use super::{ContactWindow, Fnv1a64};
    use crate::physics::bullet_ffi::ContactManifold;
    use glam::Vec3;

    #[test]
    fn contact_window_accumulates_impulse_and_keeps_deepest_point() {
        let mut window = ContactWindow::default();
        for (depth, impulse) in [(0.1, 2.0), (0.3, 4.0)] {
            window.observe(
                ContactManifold {
                    body_a: 1,
                    body_b: 2,
                    contact_count: 2,
                    max_penetration_depth: depth,
                    max_applied_impulse: impulse,
                    total_applied_impulse: impulse,
                    point_a: Vec3::splat(depth),
                    point_b: Vec3::ZERO,
                    normal_on_b: Vec3::Y,
                },
                0,
                1,
            );
        }
        let peak = window.top()[0];
        assert_eq!(peak.max_penetration_depth, 0.3);
        assert_eq!(peak.total_applied_impulse, 6.0);
        assert_eq!(peak.point_a, Vec3::splat(0.3));
    }

    #[test]
    fn fnv_hash_is_deterministic() {
        let mut first = Fnv1a64::default();
        let mut second = Fnv1a64::default();
        first.write(b"same topology");
        second.write(b"same topology");
        assert_eq!(first.0, second.0);
    }
}
