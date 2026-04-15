use std::ffi::c_void;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use glam::{Quat, Vec3};
use openxr as xr;

const VIEW_TYPE: xr::ViewConfigurationType = xr::ViewConfigurationType::PRIMARY_STEREO;

#[derive(Clone, Copy, Debug, Default)]
pub struct TrackedPose {
    pub position: Vec3,
    pub orientation: Quat,
    pub valid: bool,
}

impl TrackedPose {
    pub fn identity() -> Self {
        Self {
            position: Vec3::ZERO,
            orientation: Quat::IDENTITY,
            valid: false,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct VulkanSessionInfo {
    pub instance: *const c_void,
    pub physical_device: *const c_void,
    pub device: *const c_void,
    pub queue_family_index: u32,
    pub queue_index: u32,
}

pub struct BegunFrame {
    pub predicted_display_time: xr::Time,
    pub should_render: bool,
    pub views: [xr::View; 2],
    pub head: TrackedPose,
    pub left_hand: TrackedPose,
    pub right_hand: TrackedPose,
}

pub struct XrBootstrap {
    instance: xr::Instance,
    system: xr::SystemId,
    runtime_name: String,
    runtime_version: xr::Version,
    environment_blend_mode: xr::EnvironmentBlendMode,
}

impl XrBootstrap {
    pub fn new() -> Result<Self> {
        let entry = load_openxr_entry()?;

        let available_extensions = entry
            .enumerate_extensions()
            .context("枚举 OpenXR 扩展失败")?;
        if !available_extensions.khr_vulkan_enable2 {
            return Err(anyhow!("当前 OpenXR runtime 不支持 khr_vulkan_enable2"));
        }

        let mut enabled_extensions = xr::ExtensionSet::default();
        enabled_extensions.khr_vulkan_enable2 = true;

        let instance = entry
            .create_instance(
                &xr::ApplicationInfo {
                    application_name: "VR PMX Demo",
                    application_version: 1,
                    engine_name: "mmd_engine",
                    engine_version: 1,
                    api_version: xr::Version::new(1, 0, 0),
                },
                &enabled_extensions,
                &[],
            )
            .context("创建 OpenXR Instance 失败")?;

        let runtime_props = instance
            .properties()
            .context("读取 OpenXR runtime 属性失败")?;
        let system = instance
            .system(xr::FormFactor::HEAD_MOUNTED_DISPLAY)
            .context("未找到头显系统，请确认 SteamVR 已启动并识别 HMD")?;
        let environment_blend_mode = instance
            .enumerate_environment_blend_modes(system, VIEW_TYPE)
            .context("枚举 OpenXR 环境混合模式失败")?
            .into_iter()
            .next()
            .ok_or_else(|| anyhow!("OpenXR runtime 未返回可用的环境混合模式"))?;

        log::info!(
            "OpenXR runtime: {} {}",
            runtime_props.runtime_name,
            runtime_props.runtime_version
        );

        Ok(Self {
            instance,
            system,
            runtime_name: runtime_props.runtime_name,
            runtime_version: runtime_props.runtime_version,
            environment_blend_mode,
        })
    }

    pub fn instance(&self) -> &xr::Instance {
        &self.instance
    }

    pub fn system(&self) -> xr::SystemId {
        self.system
    }

    pub fn runtime_name(&self) -> &str {
        &self.runtime_name
    }

    pub fn runtime_label(&self) -> String {
        format!("{} {}", self.runtime_name, self.runtime_version)
    }

    pub fn create_runtime(self, session_info: VulkanSessionInfo) -> Result<XrRuntime> {
        let (session, frame_wait, frame_stream) = unsafe {
            self.instance.create_session::<xr::Vulkan>(
                self.system,
                &xr::vulkan::SessionCreateInfo {
                    instance: session_info.instance,
                    physical_device: session_info.physical_device,
                    device: session_info.device,
                    queue_family_index: session_info.queue_family_index,
                    queue_index: session_info.queue_index,
                },
            )
        }
        .context("创建 OpenXR Vulkan Session 失败")?;

        let action_set = self
            .instance
            .create_action_set("vr_input", "vr input", 0)
            .context("创建 OpenXR ActionSet 失败")?;
        let left_hand_path = self
            .instance
            .string_to_path("/user/hand/left")
            .context("解析左手用户路径失败")?;
        let right_hand_path = self
            .instance
            .string_to_path("/user/hand/right")
            .context("解析右手用户路径失败")?;

        let left_grip_action = action_set
            .create_action::<xr::Posef>("left_grip", "Left Grip", &[left_hand_path])
            .context("创建左手 Grip Pose Action 失败")?;
        let right_grip_action = action_set
            .create_action::<xr::Posef>("right_grip", "Right Grip", &[right_hand_path])
            .context("创建右手 Grip Pose Action 失败")?;
        let left_aim_action = action_set
            .create_action::<xr::Posef>("left_aim", "Left Aim", &[left_hand_path])
            .context("创建左手 Aim Pose Action 失败")?;
        let right_aim_action = action_set
            .create_action::<xr::Posef>("right_aim", "Right Aim", &[right_hand_path])
            .context("创建右手 Aim Pose Action 失败")?;

        let bindings = [
            (
                "/interaction_profiles/khr/simple_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
            ),
            (
                "/interaction_profiles/valve/index_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
            ),
            (
                "/interaction_profiles/oculus/touch_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
            ),
            (
                "/interaction_profiles/htc/vive_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
            ),
        ];

        for (profile, left_grip_path, right_grip_path, left_aim_path, right_aim_path) in bindings {
            let profile_path = self
                .instance
                .string_to_path(profile)
                .with_context(|| format!("解析 Interaction Profile 失败: {profile}"))?;
            let suggested = [
                xr::Binding::new(
                    &left_grip_action,
                    self.instance
                        .string_to_path(left_grip_path)
                        .with_context(|| format!("解析左手路径失败: {left_grip_path}"))?,
                ),
                xr::Binding::new(
                    &right_grip_action,
                    self.instance
                        .string_to_path(right_grip_path)
                        .with_context(|| format!("解析右手路径失败: {right_grip_path}"))?,
                ),
                xr::Binding::new(
                    &left_aim_action,
                    self.instance
                        .string_to_path(left_aim_path)
                        .with_context(|| format!("解析左手路径失败: {left_aim_path}"))?,
                ),
                xr::Binding::new(
                    &right_aim_action,
                    self.instance
                        .string_to_path(right_aim_path)
                        .with_context(|| format!("解析右手路径失败: {right_aim_path}"))?,
                ),
            ];
            self.instance
                .suggest_interaction_profile_bindings(profile_path, &suggested)
                .with_context(|| format!("建议绑定失败: {profile}"))?;
        }

        session
            .attach_action_sets(&[&action_set])
            .context("附加 OpenXR ActionSet 失败")?;

        let left_grip_space = left_grip_action
            .create_space(session.clone(), left_hand_path, xr::Posef::IDENTITY)
            .context("创建左手 Grip Action Space 失败")?;
        let right_grip_space = right_grip_action
            .create_space(session.clone(), right_hand_path, xr::Posef::IDENTITY)
            .context("创建右手 Grip Action Space 失败")?;
        let left_aim_space = left_aim_action
            .create_space(session.clone(), left_hand_path, xr::Posef::IDENTITY)
            .context("创建左手 Aim Action Space 失败")?;
        let right_aim_space = right_aim_action
            .create_space(session.clone(), right_hand_path, xr::Posef::IDENTITY)
            .context("创建右手 Aim Action Space 失败")?;

        let local_space = session
            .create_reference_space(xr::ReferenceSpaceType::LOCAL, xr::Posef::IDENTITY)
            .context("创建 LOCAL 参考空间失败")?;
        let view_space = session
            .create_reference_space(xr::ReferenceSpaceType::VIEW, xr::Posef::IDENTITY)
            .context("创建 VIEW 参考空间失败")?;

        Ok(XrRuntime {
            bootstrap: self,
            session,
            frame_wait,
            frame_stream,
            action_set,
            _left_grip_action: left_grip_action,
            _right_grip_action: right_grip_action,
            _left_aim_action: left_aim_action,
            _right_aim_action: right_aim_action,
            left_grip_space,
            right_grip_space,
            left_aim_space,
            right_aim_space,
            pub_local_space: local_space,
            view_space,
            session_running: false,
            state_label: "IDLE",
            event_storage: xr::EventDataBuffer::new(),
        })
    }
}

fn load_openxr_entry() -> Result<xr::Entry> {
    unsafe {
        if let Ok(entry) = xr::Entry::load() {
            return Ok(entry);
        }

        #[cfg(target_os = "windows")]
        {
            for candidate in candidate_openxr_loader_paths() {
                if candidate.exists() {
                    log::info!("OpenXR loader 回退路径: {}", candidate.display());
                    return xr::Entry::load_from(&candidate).with_context(|| {
                        format!("从回退路径加载 OpenXR loader 失败: {}", candidate.display())
                    });
                }
            }
        }
    }

    Err(anyhow!(
        "未找到 OpenXR loader，请确认 SteamVR 已安装 OpenXR Runtime"
    ))
}

#[cfg(target_os = "windows")]
fn candidate_openxr_loader_paths() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Ok(base) = std::env::var("ProgramFiles(x86)") {
        paths.push(
            Path::new(&base)
                .join("Steam")
                .join("steamapps")
                .join("common")
                .join("SteamVR")
                .join("bin")
                .join("win64")
                .join("openxr_loader.dll"),
        );
    }
    paths.push(PathBuf::from(
        r"C:\Program Files (x86)\Steam\steamapps\common\SteamVR\bin\win64\openxr_loader.dll",
    ));
    paths.push(PathBuf::from(r"C:\Windows\System32\openxr_loader.dll"));
    paths
}

pub struct XrRuntime {
    bootstrap: XrBootstrap,
    pub session: xr::Session<xr::Vulkan>,
    pub frame_wait: xr::FrameWaiter,
    pub frame_stream: xr::FrameStream<xr::Vulkan>,
    action_set: xr::ActionSet,
    _left_grip_action: xr::Action<xr::Posef>,
    _right_grip_action: xr::Action<xr::Posef>,
    _left_aim_action: xr::Action<xr::Posef>,
    _right_aim_action: xr::Action<xr::Posef>,
    left_grip_space: xr::Space,
    right_grip_space: xr::Space,
    left_aim_space: xr::Space,
    right_aim_space: xr::Space,
    pub pub_local_space: xr::Space,
    view_space: xr::Space,
    pub session_running: bool,
    state_label: &'static str,
    event_storage: xr::EventDataBuffer,
}

impl XrRuntime {
    pub fn system(&self) -> xr::SystemId {
        self.bootstrap.system
    }

    pub fn runtime_label(&self) -> String {
        format!("{} | {}", self.bootstrap.runtime_label(), self.state_label)
    }

    pub fn environment_blend_mode(&self) -> xr::EnvironmentBlendMode {
        self.bootstrap.environment_blend_mode
    }

    pub fn poll_events(&mut self) -> Result<bool> {
        while let Some(event) = self
            .bootstrap
            .instance
            .poll_event(&mut self.event_storage)
            .context("轮询 OpenXR 事件失败")?
        {
            use xr::Event::*;
            match event {
                SessionStateChanged(change) => {
                    self.state_label = match change.state() {
                        xr::SessionState::IDLE => "IDLE",
                        xr::SessionState::READY => "READY",
                        xr::SessionState::SYNCHRONIZED => "SYNCHRONIZED",
                        xr::SessionState::VISIBLE => "VISIBLE",
                        xr::SessionState::FOCUSED => "FOCUSED",
                        xr::SessionState::STOPPING => "STOPPING",
                        xr::SessionState::LOSS_PENDING => "LOSS_PENDING",
                        xr::SessionState::EXITING => "EXITING",
                        _ => "UNKNOWN",
                    };
                    log::info!("OpenXR 状态切换: {}", self.state_label);

                    match change.state() {
                        xr::SessionState::READY => {
                            self.session
                                .begin(VIEW_TYPE)
                                .context("OpenXR Session begin 失败")?;
                            self.session_running = true;
                        }
                        xr::SessionState::STOPPING => {
                            self.session.end().context("OpenXR Session end 失败")?;
                            self.session_running = false;
                        }
                        xr::SessionState::EXITING | xr::SessionState::LOSS_PENDING => {
                            return Ok(true);
                        }
                        _ => {}
                    }
                }
                InstanceLossPending(_) => return Ok(true),
                EventsLost(info) => {
                    log::warn!("OpenXR 丢失事件: {}", info.lost_event_count());
                }
                _ => {}
            }
        }
        Ok(false)
    }

    pub fn begin_frame(&mut self) -> Result<Option<BegunFrame>> {
        if !self.session_running {
            return Ok(None);
        }

        let frame_state = self.frame_wait.wait().context("等待 OpenXR 帧失败")?;
        self.frame_stream.begin().context("开始 OpenXR 帧失败")?;

        self.session
            .sync_actions(&[(&self.action_set).into()])
            .context("同步 OpenXR 输入失败")?;

        let (_, views) = self
            .session
            .locate_views(
                VIEW_TYPE,
                frame_state.predicted_display_time,
                &self.pub_local_space,
            )
            .context("定位 OpenXR 视图失败")?;
        let views: [xr::View; 2] = views
            .try_into()
            .map_err(|_| anyhow!("OpenXR runtime 未返回双眼视图"))?;

        let head = self
            .view_space
            .locate(&self.pub_local_space, frame_state.predicted_display_time)
            .context("定位头显 Pose 失败")?;
        let left_grip = self
            .left_grip_space
            .locate(&self.pub_local_space, frame_state.predicted_display_time)
            .context("定位左手 Grip Pose 失败")?;
        let right_grip = self
            .right_grip_space
            .locate(&self.pub_local_space, frame_state.predicted_display_time)
            .context("定位右手 Grip Pose 失败")?;
        let left_aim = self
            .left_aim_space
            .locate(&self.pub_local_space, frame_state.predicted_display_time)
            .context("定位左手 Aim Pose 失败")?;
        let right_aim = self
            .right_aim_space
            .locate(&self.pub_local_space, frame_state.predicted_display_time)
            .context("定位右手 Aim Pose 失败")?;

        Ok(Some(BegunFrame {
            predicted_display_time: frame_state.predicted_display_time,
            should_render: frame_state.should_render,
            views,
            head: tracked_pose_from_space(head),
            left_hand: preferred_hand_pose(
                tracked_pose_from_space(left_grip),
                tracked_pose_from_space(left_aim),
            ),
            right_hand: preferred_hand_pose(
                tracked_pose_from_space(right_grip),
                tracked_pose_from_space(right_aim),
            ),
        }))
    }

    pub fn end_frame_empty(&mut self, predicted_display_time: xr::Time) -> Result<()> {
        self.frame_stream
            .end(predicted_display_time, self.environment_blend_mode(), &[])
            .context("提交空 OpenXR 帧失败")
    }

    pub fn end_frame_projection<'a>(
        &mut self,
        predicted_display_time: xr::Time,
        projection_views: &[xr::CompositionLayerProjectionView<'a, xr::Vulkan>; 2],
    ) -> Result<()> {
        let layer = xr::CompositionLayerProjection::new()
            .space(&self.pub_local_space)
            .views(projection_views);
        self.frame_stream
            .end(
                predicted_display_time,
                self.environment_blend_mode(),
                &[&layer],
            )
            .context("提交 OpenXR Projection Layer 失败")
    }
}

fn preferred_hand_pose(primary: TrackedPose, fallback: TrackedPose) -> TrackedPose {
    if primary.valid {
        primary
    } else if fallback.valid {
        fallback
    } else {
        TrackedPose::identity()
    }
}

fn tracked_pose_from_space(space: xr::SpaceLocation) -> TrackedPose {
    let valid = space
        .location_flags
        .contains(xr::SpaceLocationFlags::POSITION_VALID)
        && space
            .location_flags
            .contains(xr::SpaceLocationFlags::ORIENTATION_VALID);
    if !valid {
        return TrackedPose::identity();
    }

    TrackedPose {
        position: Vec3::new(
            space.pose.position.x,
            space.pose.position.y,
            space.pose.position.z,
        ),
        orientation: Quat::from_xyzw(
            space.pose.orientation.x,
            space.pose.orientation.y,
            space.pose.orientation.z,
            space.pose.orientation.w,
        )
        .normalize(),
        valid: true,
    }
}
