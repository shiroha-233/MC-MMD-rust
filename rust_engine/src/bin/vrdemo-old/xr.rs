use std::ffi::c_void;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use glam::{Quat, Vec2, Vec3};
use openxr as xr;

const VIEW_TYPE: xr::ViewConfigurationType = xr::ViewConfigurationType::PRIMARY_STEREO;
const MOVE_AXIS_DEADZONE: f32 = 0.2;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum AppReferenceSpace {
    Stage,
    LocalFallback,
}

impl AppReferenceSpace {
    fn label(self) -> &'static str {
        match self {
            Self::Stage => "space=STAGE",
            Self::LocalFallback => "space=LOCAL(fallback)",
        }
    }
}

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

#[derive(Clone, Copy, Debug, Default)]
pub struct ActionButtons {
    pub teleport_aim_active: bool,
    pub teleport_confirm: bool,
    pub snap_turn_left: bool,
    pub snap_turn_right: bool,
    pub move_axis: Vec2,
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
    pub left_grip: TrackedPose,
    pub left_aim: TrackedPose,
    pub left_hand: TrackedPose,
    pub right_grip: TrackedPose,
    pub right_aim: TrackedPose,
    pub right_hand: TrackedPose,
    pub buttons: ActionButtons,
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
            .context("failed to enumerate OpenXR extensions")?;
        if !available_extensions.khr_vulkan_enable2 {
            return Err(anyhow!(
                "the active OpenXR runtime does not support khr_vulkan_enable2"
            ));
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
            .context("failed to create the OpenXR instance")?;

        let runtime_props = instance
            .properties()
            .context("failed to read OpenXR runtime properties")?;
        let system = instance
            .system(xr::FormFactor::HEAD_MOUNTED_DISPLAY)
            .context("failed to find an HMD system; make sure the runtime and headset are ready")?;
        let environment_blend_mode = instance
            .enumerate_environment_blend_modes(system, VIEW_TYPE)
            .context("failed to enumerate environment blend modes")?
            .into_iter()
            .next()
            .ok_or_else(|| anyhow!("the runtime did not report any environment blend modes"))?;

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
        .context("failed to create the OpenXR Vulkan session")?;

        let action_set = self
            .instance
            .create_action_set("vr_input", "vr input", 0)
            .context("failed to create the OpenXR action set")?;
        let left_hand_path = self
            .instance
            .string_to_path("/user/hand/left")
            .context("failed to resolve the left hand path")?;
        let right_hand_path = self
            .instance
            .string_to_path("/user/hand/right")
            .context("failed to resolve the right hand path")?;

        let left_grip_action = action_set
            .create_action::<xr::Posef>("left_grip", "Left Grip", &[left_hand_path])
            .context("failed to create the left grip pose action")?;
        let right_grip_action = action_set
            .create_action::<xr::Posef>("right_grip", "Right Grip", &[right_hand_path])
            .context("failed to create the right grip pose action")?;
        let left_aim_action = action_set
            .create_action::<xr::Posef>("left_aim", "Left Aim", &[left_hand_path])
            .context("failed to create the left aim pose action")?;
        let right_aim_action = action_set
            .create_action::<xr::Posef>("right_aim", "Right Aim", &[right_hand_path])
            .context("failed to create the right aim pose action")?;
        let teleport_click_action = action_set
            .create_action::<bool>("teleport_click", "Teleport Click", &[right_hand_path])
            .context("failed to create the teleport click action")?;
        let teleport_pull_action = action_set
            .create_action::<f32>("teleport_pull", "Teleport Pull", &[right_hand_path])
            .context("failed to create the teleport pull action")?;
        let snap_turn_axis_action = action_set
            .create_action::<xr::Vector2f>("snap_turn_axis", "Snap Turn Axis", &[left_hand_path])
            .context("failed to create the snap-turn axis action")?;

        let bindings = [
            (
                "/interaction_profiles/khr/simple_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
                Some("/user/hand/right/input/select/click"),
                None,
                None,
            ),
            (
                "/interaction_profiles/valve/index_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
                Some("/user/hand/right/input/thumbstick/click"),
                Some("/user/hand/right/input/trigger/value"),
                Some("/user/hand/left/input/thumbstick"),
            ),
            (
                "/interaction_profiles/oculus/touch_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
                Some("/user/hand/right/input/thumbstick/click"),
                Some("/user/hand/right/input/trigger/value"),
                Some("/user/hand/left/input/thumbstick"),
            ),
            (
                "/interaction_profiles/htc/vive_controller",
                "/user/hand/left/input/grip/pose",
                "/user/hand/right/input/grip/pose",
                "/user/hand/left/input/aim/pose",
                "/user/hand/right/input/aim/pose",
                Some("/user/hand/right/input/trackpad/click"),
                Some("/user/hand/right/input/trigger/value"),
                Some("/user/hand/left/input/trackpad"),
            ),
        ];

        for (
            profile,
            left_grip_path,
            right_grip_path,
            left_aim_path,
            right_aim_path,
            teleport_click_path,
            teleport_pull_path,
            snap_turn_axis_path,
        ) in bindings
        {
            let profile_path = self
                .instance
                .string_to_path(profile)
                .with_context(|| format!("failed to resolve interaction profile `{profile}`"))?;
            let mut suggested = vec![
                xr::Binding::new(
                    &left_grip_action,
                    self.instance
                        .string_to_path(left_grip_path)
                        .with_context(|| format!("failed to resolve `{left_grip_path}`"))?,
                ),
                xr::Binding::new(
                    &right_grip_action,
                    self.instance
                        .string_to_path(right_grip_path)
                        .with_context(|| format!("failed to resolve `{right_grip_path}`"))?,
                ),
                xr::Binding::new(
                    &left_aim_action,
                    self.instance
                        .string_to_path(left_aim_path)
                        .with_context(|| format!("failed to resolve `{left_aim_path}`"))?,
                ),
                xr::Binding::new(
                    &right_aim_action,
                    self.instance
                        .string_to_path(right_aim_path)
                        .with_context(|| format!("failed to resolve `{right_aim_path}`"))?,
                ),
            ];
            if let Some(path) = teleport_click_path {
                suggested.push(xr::Binding::new(
                    &teleport_click_action,
                    self.instance
                        .string_to_path(path)
                        .with_context(|| format!("failed to resolve `{path}`"))?,
                ));
            }
            if let Some(path) = teleport_pull_path {
                suggested.push(xr::Binding::new(
                    &teleport_pull_action,
                    self.instance
                        .string_to_path(path)
                        .with_context(|| format!("failed to resolve `{path}`"))?,
                ));
            }
            if let Some(path) = snap_turn_axis_path {
                suggested.push(xr::Binding::new(
                    &snap_turn_axis_action,
                    self.instance
                        .string_to_path(path)
                        .with_context(|| format!("failed to resolve `{path}`"))?,
                ));
            }
            self.instance
                .suggest_interaction_profile_bindings(profile_path, &suggested)
                .with_context(|| format!("failed to suggest bindings for `{profile}`"))?;
        }

        session
            .attach_action_sets(&[&action_set])
            .context("failed to attach the OpenXR action set")?;

        let left_grip_space = left_grip_action
            .create_space(session.clone(), left_hand_path, xr::Posef::IDENTITY)
            .context("failed to create the left grip action space")?;
        let right_grip_space = right_grip_action
            .create_space(session.clone(), right_hand_path, xr::Posef::IDENTITY)
            .context("failed to create the right grip action space")?;
        let left_aim_space = left_aim_action
            .create_space(session.clone(), left_hand_path, xr::Posef::IDENTITY)
            .context("failed to create the left aim action space")?;
        let right_aim_space = right_aim_action
            .create_space(session.clone(), right_hand_path, xr::Posef::IDENTITY)
            .context("failed to create the right aim action space")?;
        let (app_space, reference_space_kind) = create_app_reference_space(&session)?;
        let view_space = session
            .create_reference_space(xr::ReferenceSpaceType::VIEW, xr::Posef::IDENTITY)
            .context("failed to create the VIEW reference space")?;

        Ok(XrRuntime {
            bootstrap: self,
            session,
            frame_wait,
            frame_stream,
            action_set,
            left_hand_path,
            right_hand_path,
            _left_grip_action: left_grip_action,
            _right_grip_action: right_grip_action,
            _left_aim_action: left_aim_action,
            _right_aim_action: right_aim_action,
            teleport_click_action,
            teleport_pull_action,
            snap_turn_axis_action,
            left_grip_space,
            right_grip_space,
            left_aim_space,
            right_aim_space,
            app_space,
            view_space,
            reference_space_kind,
            session_running: false,
            state_label: "IDLE",
            event_storage: xr::EventDataBuffer::new(),
            previous_teleport_aim_active: false,
            previous_turn_axis_x: 0.0,
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
                    log::info!("OpenXR loader fallback path: {}", candidate.display());
                    return xr::Entry::load_from(&candidate).with_context(|| {
                        format!(
                            "failed to load the OpenXR loader from `{}`",
                            candidate.display()
                        )
                    });
                }
            }
        }
    }

    Err(anyhow!(
        "unable to find an OpenXR loader; install or activate an OpenXR runtime first"
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

fn create_app_reference_space(
    session: &xr::Session<xr::Vulkan>,
) -> Result<(xr::Space, AppReferenceSpace)> {
    match session.create_reference_space(xr::ReferenceSpaceType::STAGE, xr::Posef::IDENTITY) {
        Ok(space) => {
            log::info!("OpenXR reference space: STAGE");
            Ok((space, AppReferenceSpace::Stage))
        }
        Err(stage_error) => {
            log::warn!(
                "OpenXR STAGE reference space is unavailable ({stage_error}); falling back to LOCAL"
            );
            let local_space = session
                .create_reference_space(xr::ReferenceSpaceType::LOCAL, xr::Posef::IDENTITY)
                .context("failed to create the LOCAL reference space fallback")?;
            log::info!("OpenXR reference space: LOCAL fallback");
            Ok((local_space, AppReferenceSpace::LocalFallback))
        }
    }
}

pub struct XrRuntime {
    bootstrap: XrBootstrap,
    pub session: xr::Session<xr::Vulkan>,
    pub frame_wait: xr::FrameWaiter,
    pub frame_stream: xr::FrameStream<xr::Vulkan>,
    action_set: xr::ActionSet,
    left_hand_path: xr::Path,
    right_hand_path: xr::Path,
    _left_grip_action: xr::Action<xr::Posef>,
    _right_grip_action: xr::Action<xr::Posef>,
    _left_aim_action: xr::Action<xr::Posef>,
    _right_aim_action: xr::Action<xr::Posef>,
    teleport_click_action: xr::Action<bool>,
    teleport_pull_action: xr::Action<f32>,
    snap_turn_axis_action: xr::Action<xr::Vector2f>,
    left_grip_space: xr::Space,
    right_grip_space: xr::Space,
    left_aim_space: xr::Space,
    right_aim_space: xr::Space,
    app_space: xr::Space,
    view_space: xr::Space,
    reference_space_kind: AppReferenceSpace,
    session_running: bool,
    state_label: &'static str,
    event_storage: xr::EventDataBuffer,
    previous_teleport_aim_active: bool,
    previous_turn_axis_x: f32,
}

impl XrRuntime {
    pub fn system(&self) -> xr::SystemId {
        self.bootstrap.system
    }

    pub fn runtime_label(&self) -> String {
        format!(
            "{} | session={} | {}",
            self.bootstrap.runtime_label(),
            self.state_label,
            self.reference_space_kind.label()
        )
    }

    pub fn environment_blend_mode(&self) -> xr::EnvironmentBlendMode {
        self.bootstrap.environment_blend_mode
    }

    pub fn is_session_active(&self) -> bool {
        self.session_running
    }

    pub fn poll_events(&mut self) -> Result<bool> {
        while let Some(event) = self
            .bootstrap
            .instance
            .poll_event(&mut self.event_storage)
            .context("failed to poll OpenXR events")?
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
                    log::info!(
                        "OpenXR session state changed: {} ({})",
                        self.state_label,
                        self.reference_space_kind.label()
                    );

                    match change.state() {
                        xr::SessionState::READY => {
                            self.session
                                .begin(VIEW_TYPE)
                                .context("failed to begin the OpenXR session")?;
                            self.session_running = true;
                        }
                        xr::SessionState::STOPPING => {
                            self.session
                                .end()
                                .context("failed to end the OpenXR session")?;
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
                    log::warn!("OpenXR lost {} events", info.lost_event_count());
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

        let frame_state = self
            .frame_wait
            .wait()
            .context("failed to wait for the next OpenXR frame")?;
        self.frame_stream
            .begin()
            .context("failed to begin the OpenXR frame")?;

        self.session
            .sync_actions(&[(&self.action_set).into()])
            .context("failed to sync OpenXR actions")?;

        let teleport_click = self
            .teleport_click_action
            .state(&self.session, self.right_hand_path)
            .context("failed to read the teleport click action")?
            .current_state;
        let teleport_pull = self
            .teleport_pull_action
            .state(&self.session, self.right_hand_path)
            .context("failed to read the teleport pull action")?
            .current_state;
        let snap_turn_axis = self
            .snap_turn_axis_action
            .state(&self.session, self.left_hand_path)
            .context("failed to read the snap-turn axis action")?
            .current_state;

        let teleport_aim_active = teleport_click || teleport_pull > 0.35;
        let teleport_confirm = self.previous_teleport_aim_active && !teleport_aim_active;
        let snap_turn_left = self.previous_turn_axis_x > -0.7 && snap_turn_axis.x <= -0.7;
        let snap_turn_right = self.previous_turn_axis_x < 0.7 && snap_turn_axis.x >= 0.7;
        let mut move_axis = Vec2::new(snap_turn_axis.x, snap_turn_axis.y);
        if move_axis.length_squared() <= MOVE_AXIS_DEADZONE * MOVE_AXIS_DEADZONE {
            move_axis = Vec2::ZERO;
        } else {
            let magnitude = ((move_axis.length() - MOVE_AXIS_DEADZONE)
                / (1.0 - MOVE_AXIS_DEADZONE))
                .clamp(0.0, 1.0);
            move_axis = move_axis.normalize() * magnitude;
        }
        if snap_turn_left || snap_turn_right {
            move_axis.x = 0.0;
        }
        self.previous_teleport_aim_active = teleport_aim_active;
        self.previous_turn_axis_x = snap_turn_axis.x;

        let (_, views) = self
            .session
            .locate_views(
                VIEW_TYPE,
                frame_state.predicted_display_time,
                &self.app_space,
            )
            .context("failed to locate OpenXR views")?;
        let views: [xr::View; 2] = views
            .try_into()
            .map_err(|_| anyhow!("the OpenXR runtime did not return stereo views"))?;

        let head = self
            .view_space
            .locate(&self.app_space, frame_state.predicted_display_time)
            .context("failed to locate the HMD pose")?;
        let left_grip = self
            .left_grip_space
            .locate(&self.app_space, frame_state.predicted_display_time)
            .context("failed to locate the left grip pose")?;
        let right_grip = self
            .right_grip_space
            .locate(&self.app_space, frame_state.predicted_display_time)
            .context("failed to locate the right grip pose")?;
        let left_aim = self
            .left_aim_space
            .locate(&self.app_space, frame_state.predicted_display_time)
            .context("failed to locate the left aim pose")?;
        let right_aim = self
            .right_aim_space
            .locate(&self.app_space, frame_state.predicted_display_time)
            .context("failed to locate the right aim pose")?;

        let left_grip = tracked_pose_from_space(left_grip);
        let right_grip = tracked_pose_from_space(right_grip);
        let left_aim = tracked_pose_from_space(left_aim);
        let right_aim = tracked_pose_from_space(right_aim);

        Ok(Some(BegunFrame {
            predicted_display_time: frame_state.predicted_display_time,
            should_render: frame_state.should_render,
            views,
            head: tracked_pose_from_space(head),
            left_grip,
            left_aim,
            left_hand: preferred_hand_pose(left_grip, left_aim),
            right_grip,
            right_aim,
            right_hand: preferred_hand_pose(right_grip, right_aim),
            buttons: ActionButtons {
                teleport_aim_active,
                teleport_confirm,
                snap_turn_left,
                snap_turn_right,
                move_axis,
            },
        }))
    }

    pub fn end_frame_empty(&mut self, predicted_display_time: xr::Time) -> Result<()> {
        self.frame_stream
            .end(predicted_display_time, self.environment_blend_mode(), &[])
            .context("failed to submit an empty OpenXR frame")
    }

    pub fn end_frame_projection<'a>(
        &mut self,
        predicted_display_time: xr::Time,
        projection_views: &[xr::CompositionLayerProjectionView<'a, xr::Vulkan>; 2],
    ) -> Result<()> {
        let layer = xr::CompositionLayerProjection::new()
            .space(&self.app_space)
            .views(projection_views);
        self.frame_stream
            .end(
                predicted_display_time,
                self.environment_blend_mode(),
                &[&layer],
            )
            .context("failed to submit the OpenXR projection layer")
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
