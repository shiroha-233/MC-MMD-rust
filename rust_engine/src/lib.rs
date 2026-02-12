//! MMD Engine - Rust 实现的 MMD 运行时引擎
//!
//! 提供与 C++ 原生引擎等价的功能：
//! - PMX/PMD 模型加载
//! - VMD 动画解析和播放
//! - 骨骼系统和 IK 求解
//! - Morph 变形系统
//! - 顶点蒙皮计算
//! - JNI 接口

pub mod animation;
pub mod jni_bridge;
pub mod model;
pub mod morph;
pub mod physics;
pub mod skeleton;
pub mod skinning;
pub mod texture;

pub use animation::{VmdAnimation, VmdFile};
pub use model::MmdModel;
pub use morph::{Morph, MorphManager};
pub use physics::{MMDPhysics, MmdRigidBodyData, MmdJointData, PhysicsMode};
pub use skeleton::{Bone, BoneManager, IkSolver};
pub use texture::Texture;

use thiserror::Error;

#[derive(Error, Debug)]
pub enum MmdError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("PMX parse error: {0}")]
    PmxParse(String),

    #[error("VMD parse error: {0}")]
    VmdParse(String),

    #[error("Animation error: {0}")]
    Animation(String),

    #[error("Texture error: {0}")]
    Texture(String),

    #[error("VPD parse error: {0}")]
    VpdParse(String),
}

pub type Result<T> = std::result::Result<T, MmdError>;
