//! 负责实现模型、动画、舞台、物理、Morph 与 VR 的集中 JNI 入口。
use jni::objects::{JByteBuffer, JClass, JFloatArray, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring};
use jni::JNIEnv;
use std::ptr;
use std::sync::Arc;

use super::{
    catch_bridge, register_animation, register_model, ModelHandle, ANIMATIONS, FBX_CACHE, MODELS,
    NATIVE_RUNTIME,
};
use crate::animation::fbx_loader;
use crate::animation::{VmdAnimation, VmdFile};
use crate::model::{load_pmx, load_vrm};

const VERSION: &str = "v1.0.5";

// 获取版本号
include!("runtime_bindings/model.rs");
include!("runtime_bindings/animation.rs");
include!("runtime_bindings/model_control.rs");
include!("runtime_bindings/animation_layers.rs");
include!("runtime_bindings/physics.rs");
include!("runtime_bindings/material.rs");
include!("runtime_bindings/morph.rs");
include!("runtime_bindings/first_person.rs");
include!("runtime_bindings/vr.rs");
