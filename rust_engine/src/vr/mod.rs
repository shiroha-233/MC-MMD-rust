//! VR 联动模块 - 独立于现有 IK 求解器

pub mod vr_ik;

pub use vr_ik::VrIkSolver;
pub(crate) use vr_ik::{VrDebugState, VrTrackedPose, VrTrackingFrame, XR_TO_MODEL_SCALE};
