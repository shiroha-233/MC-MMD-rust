//! 动画系统

mod bezier_curve;
mod interpolation;
mod keyframe;
mod motion_track;
mod motion;
mod vmd_loader;
mod vpd_file;
mod animation_layer;
pub(crate) mod fbx_parser;
pub(crate) mod fbx_bone_mapping;
pub mod fbx_loader;
pub mod vmd_writer;

pub use bezier_curve::{BezierCurve, BezierCurveCache, Curve};
pub use interpolation::{KeyframeInterpolationPoint, BoneKeyframeInterpolation};
pub use keyframe::{BoneKeyframe, MorphKeyframe, CameraKeyframe, CameraInterpolation};
pub use motion_track::{MotionTrack, BoneMotionTrack, MorphMotionTrack, BoneFrameTransform, CameraMotionTrack, CameraFrameTransform};
pub use motion::Motion;
pub use vmd_loader::{VmdFile, VmdAnimation};
pub use vmd_writer::write_vmd;
pub use vpd_file::{VpdFile, VpdBone, VpdMorph};
pub use animation_layer::{AnimationLayer, AnimationLayerManager, AnimationLayerState, AnimationLayerConfig, PoseSnapshot, BonePose};
