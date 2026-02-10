//! MMD 物理系统模块
//!
//! 使用 Rapier3D 物理引擎实现，对应 C++ saba 库的 MMDPhysics 功能。
//! 
//! ## 功能对应关系
//! | Bullet3 (C++) | Rapier (Rust) |
//! |---------------|---------------|
//! | btDiscreteDynamicsWorld | PhysicsPipeline + RigidBodySet + ColliderSet |
//! | btRigidBody | RigidBody + Collider |
//! | btGeneric6DofSpringConstraint | GenericJoint (limits + ForceBased motor springs) |
//! | btSphereShape | ColliderBuilder::ball() |
//! | btBoxShape | ColliderBuilder::cuboid() |
//! | btCapsuleShape | ColliderBuilder::capsule() |

mod mmd_physics;
mod mmd_rigid_body;
mod mmd_joint;
pub mod spring_6dof;
pub mod config;

pub use mmd_physics::MMDPhysics;
pub use mmd_rigid_body::{MMDRigidBody, RigidBodyType};
pub use mmd_joint::MMDJoint;
pub use config::{PhysicsConfig, get_config, set_config, reset_config};
