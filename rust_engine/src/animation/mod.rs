//! 动画系统 - 复刻 mdanceio 实现
//!
//! 提供 VMD 动画解析、关键帧插值、动画层管理等功能。

mod animation_layer;
mod bezier_curve;
pub(crate) mod fbx_bone_mapping;
pub mod fbx_loader;
pub(crate) mod fbx_parser;
mod interpolation;
mod keyframe;
mod motion;
mod motion_track;
mod vmd_loader;
pub mod vmd_writer;
mod vpd_file;

pub use animation_layer::{
    AnimationLayer, AnimationLayerConfig, AnimationLayerManager, AnimationLayerState, BonePose,
    PoseSnapshot,
};
pub use bezier_curve::{BezierCurve, BezierCurveCache, Curve};
pub use interpolation::{BoneKeyframeInterpolation, KeyframeInterpolationPoint};
pub use keyframe::{BoneKeyframe, CameraInterpolation, CameraKeyframe, MorphKeyframe};
pub use motion::Motion;
pub use motion_track::{
    BoneFrameTransform, BoneMotionTrack, CameraFrameTransform, CameraMotionTrack, MorphMotionTrack,
    MotionTrack,
};
pub use vmd_loader::{VmdAnimation, VmdFile};
pub use vmd_writer::write_vmd;
pub use vpd_file::{VpdBone, VpdFile, VpdMorph};
