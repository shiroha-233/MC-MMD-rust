use std::ffi::CString;
use std::mem::size_of;
use std::ptr::copy_nonoverlapping;

use anyhow::{anyhow, Context, Result};
use ash::vk::Handle;
use ash::{vk, Device, Entry, Instance};
use bytemuck::{Pod, Zeroable};
use glam::{Mat4, Quat, Vec3, Vec4};
use naga::back::spv;
use naga::valid::{Capabilities, ValidationFlags, Validator};
use openxr as xr;
use winit::dpi::PhysicalSize;
use winit::raw_window_handle::{HasDisplayHandle, HasWindowHandle};
use winit::window::Window;

use crate::scene::{ModelRenderData, SceneAssets};
use crate::xr::{BegunFrame, VulkanSessionInfo, XrBootstrap, XrRuntime};

const XR_NEAR: f32 = 0.05;
const XR_FAR: f32 = 50.0;
const DEPTH_FORMAT: vk::Format = vk::Format::D32_SFLOAT;
const FRAMES_IN_FLIGHT: usize = 2;

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct GpuVertex {
    position: [f32; 3],
    normal: [f32; 3],
    uv: [f32; 2],
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct PushConstants {
    mvp: [[f32; 4]; 4],
    diffuse: [f32; 4],
    params: [f32; 4],
}

struct BufferResource {
    buffer: vk::Buffer,
    memory: vk::DeviceMemory,
    size: vk::DeviceSize,
}

struct ImageResource {
    image: vk::Image,
    memory: vk::DeviceMemory,
    view: vk::ImageView,
}

struct TextureResource {
    image: ImageResource,
    descriptor_set: vk::DescriptorSet,
}

struct PipelineBundle {
    render_pass: vk::RenderPass,
    pipeline: vk::Pipeline,
}

struct MirrorSwapchain {
    swapchain: vk::SwapchainKHR,
    format: vk::Format,
    extent: vk::Extent2D,
    image_views: Vec<vk::ImageView>,
    framebuffers: Vec<vk::Framebuffer>,
    depth: Option<ImageResource>,
}

struct XrSwapchainBundle {
    handle: xr::Swapchain<xr::Vulkan>,
    extent: vk::Extent2D,
    rect: xr::Rect2Di,
    layer_views: Vec<[vk::ImageView; 2]>,
    framebuffers: Vec<[vk::Framebuffer; 2]>,
    depth: ImageResource,
}

struct FrameSync {
    fence: vk::Fence,
    image_available: vk::Semaphore,
    render_finished: vk::Semaphore,
    command_buffer: vk::CommandBuffer,
}

pub struct VulkanRenderer {
    instance: Instance,
    physical_device: vk::PhysicalDevice,
    device: Device,
    queue_family_index: u32,
    queue: vk::Queue,
    memory_properties: vk::PhysicalDeviceMemoryProperties,
    surface_loader: ash::khr::surface::Instance,
    surface: vk::SurfaceKHR,
    swapchain_loader: ash::khr::swapchain::Device,
    mirror: MirrorSwapchain,
    mirror_pipeline: PipelineBundle,
    xr_pipeline: Option<PipelineBundle>,
    command_pool: vk::CommandPool,
    frames: Vec<FrameSync>,
    frame_index: usize,
    descriptor_pool: vk::DescriptorPool,
    descriptor_set_layout: vk::DescriptorSetLayout,
    pipeline_layout: vk::PipelineLayout,
    sampler: vk::Sampler,
    default_texture: TextureResource,
    textures: Vec<TextureResource>,
    index_buffer: BufferResource,
    hmd_vertex_buffer: BufferResource,
    mirror_vertex_buffer: BufferResource,
    scratch_hmd_vertices: Vec<GpuVertex>,
    scratch_mirror_vertices: Vec<GpuVertex>,
    xr_swapchain: Option<XrSwapchainBundle>,
    xr_swapchain_format: Option<vk::Format>,
    vert_spv: Vec<u32>,
    frag_spv: Vec<u32>,
}

impl VulkanRenderer {
    pub fn new(window: &Window, bootstrap: &XrBootstrap, assets: &SceneAssets) -> Result<Self> {
        log::info!("Vulkan: 加载 Entry");
        let entry = unsafe { Entry::load().context("加载 Vulkan Entry 失败")? };

        let display_handle = window
            .display_handle()
            .context("获取桌面显示句柄失败")?
            .as_raw();
        let extension_names = ash_window::enumerate_required_extensions(display_handle)
            .map_err(|err| anyhow!("枚举桌面 Vulkan 扩展失败: {err:?}"))?
            .to_vec();

        let vk_target_version = vk::make_api_version(0, 1, 1, 0);
        let vk_target_version_xr = xr::Version::new(1, 1, 0);
        let requirements = bootstrap
            .instance()
            .graphics_requirements::<xr::Vulkan>(bootstrap.system())
            .context("查询 OpenXR Vulkan 需求失败")?;
        if vk_target_version_xr < requirements.min_api_version_supported
            || vk_target_version_xr.major() > requirements.max_api_version_supported.major()
        {
            return Err(anyhow!(
                "OpenXR runtime 需要 Vulkan 版本区间 {} - {}.x",
                requirements.min_api_version_supported,
                requirements.max_api_version_supported.major()
            ));
        }

        let app_name = CString::new("vr-pmx-demo").unwrap();
        let engine_name = CString::new("mmd_engine").unwrap();
        let app_info = vk::ApplicationInfo::default()
            .application_name(&app_name)
            .application_version(1)
            .engine_name(&engine_name)
            .engine_version(1)
            .api_version(vk_target_version);
        let create_info = vk::InstanceCreateInfo::default()
            .application_info(&app_info)
            .enabled_extension_names(&extension_names);

        log::info!("Vulkan: 创建 Vulkan/OpenXR Instance");
        let instance = unsafe {
            bootstrap
                .instance()
                .create_vulkan_instance(
                    bootstrap.system(),
                    std::mem::transmute(entry.static_fn().get_instance_proc_addr),
                    &create_info as *const _ as *const _,
                )
                .context("OpenXR 创建 Vulkan Instance 失败")?
                .map_err(|err| anyhow!("Vulkan Instance 创建失败: {err:?}"))
        }?;
        let instance =
            unsafe { Instance::load(entry.static_fn(), vk::Instance::from_raw(instance as _)) };

        log::info!("Vulkan: 创建桌面 Surface");
        let surface = unsafe {
            ash_window::create_surface(
                &entry,
                &instance,
                display_handle,
                window
                    .window_handle()
                    .context("获取桌面窗口句柄失败")?
                    .as_raw(),
                None,
            )
            .map_err(|err| anyhow!("创建桌面 Vulkan Surface 失败: {err:?}"))?
        };
        let surface_loader = ash::khr::surface::Instance::new(&entry, &instance);

        log::info!("Vulkan: 选择物理设备/队列");
        let physical_device = vk::PhysicalDevice::from_raw(unsafe {
            bootstrap
                .instance()
                .vulkan_graphics_device(bootstrap.system(), instance.handle().as_raw() as _)
                .context("查询 OpenXR 推荐 Vulkan 物理设备失败")? as _
        });
        let memory_properties =
            unsafe { instance.get_physical_device_memory_properties(physical_device) };
        let queue_family_index =
            Self::find_queue_family(&instance, &surface_loader, physical_device, surface)?;

        let mut multiview_features = vk::PhysicalDeviceMultiviewFeatures::default();
        let queue_priorities = [1.0f32];
        let queue_info = [vk::DeviceQueueCreateInfo::default()
            .queue_family_index(queue_family_index)
            .queue_priorities(&queue_priorities)];
        let device_extensions = vec![ash::khr::swapchain::NAME.as_ptr()];
        let device_info = vk::DeviceCreateInfo::default()
            .queue_create_infos(&queue_info)
            .enabled_extension_names(&device_extensions)
            .push_next(&mut multiview_features);

        log::info!("Vulkan: 创建逻辑设备");
        let device = unsafe {
            bootstrap
                .instance()
                .create_vulkan_device(
                    bootstrap.system(),
                    std::mem::transmute(entry.static_fn().get_instance_proc_addr),
                    physical_device.as_raw() as _,
                    &device_info as *const _ as *const _,
                )
                .context("OpenXR 创建 Vulkan Device 失败")?
                .map_err(|err| anyhow!("Vulkan Device 创建失败: {err:?}"))
        }?;
        let device = unsafe { Device::load(instance.fp_v1_0(), vk::Device::from_raw(device as _)) };

        let queue = unsafe { device.get_device_queue(queue_family_index, 0) };
        let swapchain_loader = ash::khr::swapchain::Device::new(&instance, &device);

        let descriptor_set_layout = Self::create_descriptor_set_layout(&device)?;
        let descriptor_pool =
            Self::create_descriptor_pool(&device, assets.texture_paths.len() + 1)?;
        let pipeline_layout = Self::create_pipeline_layout(&device, descriptor_set_layout)?;
        let sampler = Self::create_sampler(&device)?;

        let vert_spv = compile_shader(
            include_str!("shaders/mesh.vert.glsl"),
            naga::ShaderStage::Vertex,
            "mesh.vert.wgsl",
        )?;
        let frag_spv = compile_shader(
            include_str!("shaders/mesh.frag.glsl"),
            naga::ShaderStage::Fragment,
            "mesh.frag.wgsl",
        )?;

        log::info!("Vulkan: 创建桌面镜像 Swapchain");
        let mirror = Self::create_mirror_swapchain(
            &instance,
            &device,
            physical_device,
            &surface_loader,
            &swapchain_loader,
            surface,
            window.inner_size(),
        )?;
        log::info!("Vulkan: 创建渲染管线和命令资源");
        let mirror_pipeline = Self::create_pipeline_bundle(
            &device,
            pipeline_layout,
            mirror.format,
            vk::ImageLayout::PRESENT_SRC_KHR,
            &vert_spv,
            &frag_spv,
        )?;

        let command_pool = vk_result(
            unsafe {
                device.create_command_pool(
                    &vk::CommandPoolCreateInfo::default()
                        .queue_family_index(queue_family_index)
                        .flags(vk::CommandPoolCreateFlags::RESET_COMMAND_BUFFER),
                    None,
                )
            },
            "创建 Vulkan CommandPool 失败",
        )?;
        let command_buffers = vk_result(
            unsafe {
                device.allocate_command_buffers(
                    &vk::CommandBufferAllocateInfo::default()
                        .command_pool(command_pool)
                        .level(vk::CommandBufferLevel::PRIMARY)
                        .command_buffer_count(FRAMES_IN_FLIGHT as u32),
                )
            },
            "分配 Vulkan CommandBuffer 失败",
        )?;
        let mut frames = Vec::with_capacity(FRAMES_IN_FLIGHT);
        for command_buffer in command_buffers {
            let fence = vk_result(
                unsafe {
                    device.create_fence(
                        &vk::FenceCreateInfo::default().flags(vk::FenceCreateFlags::SIGNALED),
                        None,
                    )
                },
                "创建 Vulkan Fence 失败",
            )?;
            let image_available = vk_result(
                unsafe { device.create_semaphore(&vk::SemaphoreCreateInfo::default(), None) },
                "创建 image_available Semaphore 失败",
            )?;
            let render_finished = vk_result(
                unsafe { device.create_semaphore(&vk::SemaphoreCreateInfo::default(), None) },
                "创建 render_finished Semaphore 失败",
            )?;
            frames.push(FrameSync {
                fence,
                image_available,
                render_finished,
                command_buffer,
            });
        }

        let index_buffer = Self::create_host_visible_buffer(
            &instance,
            &device,
            &memory_properties,
            (assets.indices.len() * size_of::<u32>()) as u64,
            vk::BufferUsageFlags::INDEX_BUFFER,
        )?;
        Self::write_buffer(
            &device,
            &index_buffer,
            bytemuck::cast_slice(&assets.indices),
        )?;

        let vertex_buffer_size = (assets.vertex_count * size_of::<GpuVertex>()) as u64;
        let hmd_vertex_buffer = Self::create_host_visible_buffer(
            &instance,
            &device,
            &memory_properties,
            vertex_buffer_size,
            vk::BufferUsageFlags::VERTEX_BUFFER,
        )?;
        let mirror_vertex_buffer = Self::create_host_visible_buffer(
            &instance,
            &device,
            &memory_properties,
            vertex_buffer_size,
            vk::BufferUsageFlags::VERTEX_BUFFER,
        )?;

        let mut renderer = Self {
            instance,
            physical_device,
            device,
            queue_family_index,
            queue,
            memory_properties,
            surface_loader,
            surface,
            swapchain_loader,
            mirror,
            mirror_pipeline,
            xr_pipeline: None,
            command_pool,
            frames,
            frame_index: 0,
            descriptor_pool,
            descriptor_set_layout,
            pipeline_layout,
            sampler,
            default_texture: TextureResource {
                image: ImageResource {
                    image: vk::Image::null(),
                    memory: vk::DeviceMemory::null(),
                    view: vk::ImageView::null(),
                },
                descriptor_set: vk::DescriptorSet::null(),
            },
            textures: Vec::new(),
            index_buffer,
            hmd_vertex_buffer,
            mirror_vertex_buffer,
            scratch_hmd_vertices: Vec::with_capacity(assets.vertex_count),
            scratch_mirror_vertices: Vec::with_capacity(assets.vertex_count),
            xr_swapchain: None,
            xr_swapchain_format: None,
            vert_spv,
            frag_spv,
        };

        log::info!("Vulkan: 创建默认纹理");
        renderer.default_texture =
            renderer.create_texture_from_rgba8(1, 1, &[255, 255, 255, 255])?;
        log::info!("Vulkan: 上传 {} 张模型纹理", assets.texture_paths.len());
        renderer.textures = renderer.load_textures(&assets.texture_paths)?;
        log::info!("Vulkan: 重建桌面 Framebuffer");
        renderer.rebuild_mirror_framebuffers()?;
        log::info!("Vulkan: 初始化完成");

        Ok(renderer)
    }

    pub fn session_info(&self) -> VulkanSessionInfo {
        VulkanSessionInfo {
            instance: self.instance.handle().as_raw() as *const _,
            physical_device: self.physical_device.as_raw() as *const _,
            device: self.device.handle().as_raw() as *const _,
            queue_family_index: self.queue_family_index,
            queue_index: 0,
        }
    }

    pub fn initialize_xr_targets(&mut self, runtime: &XrRuntime) -> Result<()> {
        let swapchain_formats = runtime
            .session
            .enumerate_swapchain_formats()
            .context("枚举 OpenXR Swapchain 格式失败")?;
        let preferred = [
            vk::Format::R8G8B8A8_SRGB,
            vk::Format::B8G8R8A8_SRGB,
            vk::Format::R8G8B8A8_UNORM,
            vk::Format::B8G8R8A8_UNORM,
        ];
        let xr_format_raw = preferred
            .iter()
            .find_map(|candidate| {
                let raw = candidate.as_raw() as u32;
                swapchain_formats.contains(&raw).then_some(raw)
            })
            .or_else(|| swapchain_formats.first().copied())
            .ok_or_else(|| anyhow!("OpenXR runtime 未返回可用的 Swapchain 格式"))?;
        let xr_format = vk::Format::from_raw(xr_format_raw as i32);

        let view_config_views = runtime
            .session
            .instance()
            .enumerate_view_configuration_views(
                runtime.system(),
                xr::ViewConfigurationType::PRIMARY_STEREO,
            );
        let view_config_views = view_config_views.context("读取 OpenXR View 配置失败")?;
        let first_view = view_config_views
            .first()
            .copied()
            .ok_or_else(|| anyhow!("OpenXR runtime 未提供双眼 View 配置"))?;
        let extent = vk::Extent2D {
            width: first_view.recommended_image_rect_width,
            height: first_view.recommended_image_rect_height,
        };
        let handle = runtime
            .session
            .create_swapchain(&xr::SwapchainCreateInfo {
                create_flags: xr::SwapchainCreateFlags::EMPTY,
                usage_flags: xr::SwapchainUsageFlags::COLOR_ATTACHMENT
                    | xr::SwapchainUsageFlags::SAMPLED,
                format: xr_format_raw,
                sample_count: 1,
                width: extent.width,
                height: extent.height,
                face_count: 1,
                array_size: 2,
                mip_count: 1,
            })
            .context("创建 OpenXR Swapchain 失败")?;
        let images = handle
            .enumerate_images()
            .context("枚举 OpenXR Swapchain 图像失败")?;

        let xr_pipeline = Self::create_pipeline_bundle(
            &self.device,
            self.pipeline_layout,
            xr_format,
            vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL,
            &self.vert_spv,
            &self.frag_spv,
        )?;
        let depth = Self::create_depth_image(
            &self.instance,
            &self.device,
            &self.memory_properties,
            extent,
        )?;

        let mut layer_views = Vec::with_capacity(images.len());
        let mut framebuffers = Vec::with_capacity(images.len());
        for raw_image in images {
            let image = vk::Image::from_raw(raw_image);
            let left_view = vk_result(
                unsafe {
                    self.device.create_image_view(
                        &vk::ImageViewCreateInfo::default()
                            .image(image)
                            .view_type(vk::ImageViewType::TYPE_2D)
                            .format(xr_format)
                            .subresource_range(color_subresource_range(0, 1)),
                        None,
                    )
                },
                "创建 OpenXR 左眼 ImageView 失败",
            )?;
            let right_view = vk_result(
                unsafe {
                    self.device.create_image_view(
                        &vk::ImageViewCreateInfo::default()
                            .image(image)
                            .view_type(vk::ImageViewType::TYPE_2D)
                            .format(xr_format)
                            .subresource_range(color_subresource_range(1, 1)),
                        None,
                    )
                },
                "创建 OpenXR 右眼 ImageView 失败",
            )?;

            let left_fb = vk_result(
                unsafe {
                    self.device.create_framebuffer(
                        &vk::FramebufferCreateInfo::default()
                            .render_pass(xr_pipeline.render_pass)
                            .attachments(&[left_view, depth.view])
                            .width(extent.width)
                            .height(extent.height)
                            .layers(1),
                        None,
                    )
                },
                "创建 OpenXR 左眼 Framebuffer 失败",
            )?;
            let right_fb = vk_result(
                unsafe {
                    self.device.create_framebuffer(
                        &vk::FramebufferCreateInfo::default()
                            .render_pass(xr_pipeline.render_pass)
                            .attachments(&[right_view, depth.view])
                            .width(extent.width)
                            .height(extent.height)
                            .layers(1),
                        None,
                    )
                },
                "创建 OpenXR 右眼 Framebuffer 失败",
            )?;

            layer_views.push([left_view, right_view]);
            framebuffers.push([left_fb, right_fb]);
        }

        self.xr_pipeline = Some(xr_pipeline);
        self.xr_swapchain_format = Some(xr_format);
        self.xr_swapchain = Some(XrSwapchainBundle {
            handle,
            extent,
            rect: xr::Rect2Di {
                offset: xr::Offset2Di { x: 0, y: 0 },
                extent: xr::Extent2Di {
                    width: extent.width as i32,
                    height: extent.height as i32,
                },
            },
            layer_views,
            framebuffers,
            depth,
        });

        Ok(())
    }

    pub fn mirror_aspect(&self) -> f32 {
        if self.mirror.extent.height == 0 {
            16.0 / 9.0
        } else {
            self.mirror.extent.width as f32 / self.mirror.extent.height as f32
        }
    }

    pub fn resize_mirror(&mut self, window: &Window) -> Result<()> {
        let size = window.inner_size();
        if size.width == 0 || size.height == 0 {
            return Ok(());
        }

        vk_result(
            unsafe { self.device.device_wait_idle() },
            "等待 Vulkan 设备空闲失败",
        )?;
        self.destroy_mirror_targets();
        self.mirror = Self::create_mirror_swapchain(
            &self.instance,
            &self.device,
            self.physical_device,
            &self.surface_loader,
            &self.swapchain_loader,
            self.surface,
            size,
        )?;
        self.mirror_pipeline = Self::create_pipeline_bundle(
            &self.device,
            self.pipeline_layout,
            self.mirror.format,
            vk::ImageLayout::PRESENT_SRC_KHR,
            &self.vert_spv,
            &self.frag_spv,
        )?;
        self.rebuild_mirror_framebuffers()?;
        Ok(())
    }

    pub fn render_mirror_only(
        &mut self,
        window: &Window,
        assets: &SceneAssets,
        mirror_data: &ModelRenderData<'_>,
        mirror_model_matrix: Mat4,
        mirror_view_proj: Mat4,
    ) -> Result<()> {
        self.render_internal(
            window,
            assets,
            None,
            None,
            mirror_data,
            Mat4::IDENTITY,
            mirror_model_matrix,
            mirror_view_proj,
        )?;
        Ok(())
    }

    pub fn render_xr_and_mirror(
        &mut self,
        window: &Window,
        runtime: &mut XrRuntime,
        frame: BegunFrame,
        assets: &SceneAssets,
        hmd_data: &ModelRenderData<'_>,
        mirror_data: &ModelRenderData<'_>,
        xr_model_matrix: Mat4,
        mirror_model_matrix: Mat4,
        mirror_view_proj: Mat4,
    ) -> Result<()> {
        if !frame.should_render {
            self.render_mirror_only(
                window,
                assets,
                mirror_data,
                mirror_model_matrix,
                mirror_view_proj,
            )?;
            runtime.end_frame_empty(frame.predicted_display_time)?;
            return Ok(());
        }

        self.render_internal(
            window,
            assets,
            Some(&frame),
            Some(hmd_data),
            mirror_data,
            xr_model_matrix,
            mirror_model_matrix,
            mirror_view_proj,
        )?;
        let projection_views = self.build_projection_views(&frame)?;
        runtime.end_frame_projection(frame.predicted_display_time, &projection_views)?;
        Ok(())
    }

    fn render_internal(
        &mut self,
        window: &Window,
        assets: &SceneAssets,
        xr_frame: Option<&BegunFrame>,
        hmd_data: Option<&ModelRenderData<'_>>,
        mirror_data: &ModelRenderData<'_>,
        xr_model_matrix: Mat4,
        mirror_model_matrix: Mat4,
        mirror_view_proj: Mat4,
    ) -> Result<()> {
        let frame_index = self.frame_index;
        let fence = self.frames[frame_index].fence;
        let image_available = self.frames[frame_index].image_available;
        let render_finished = self.frames[frame_index].render_finished;
        let command_buffer = self.frames[frame_index].command_buffer;
        vk_result(
            unsafe { self.device.wait_for_fences(&[fence], true, u64::MAX) },
            "等待上一帧 Fence 失败",
        )?;
        vk_result(
            unsafe { self.device.reset_fences(&[fence]) },
            "重置 Fence 失败",
        )?;
        vk_result(
            unsafe {
                self.device
                    .reset_command_buffer(command_buffer, vk::CommandBufferResetFlags::empty())
            },
            "重置 CommandBuffer 失败",
        )?;

        let mirror_image = if window.inner_size().width > 0 && window.inner_size().height > 0 {
            self.acquire_mirror_image(window, image_available)?
        } else {
            None
        };

        if let Some(data) = hmd_data {
            Self::upload_dynamic_vertices(
                &self.device,
                &mut self.scratch_hmd_vertices,
                &self.hmd_vertex_buffer,
                data,
            )?;
        }
        Self::upload_dynamic_vertices(
            &self.device,
            &mut self.scratch_mirror_vertices,
            &self.mirror_vertex_buffer,
            mirror_data,
        )?;

        vk_result(
            unsafe {
                self.device.begin_command_buffer(
                    command_buffer,
                    &vk::CommandBufferBeginInfo::default()
                        .flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT),
                )
            },
            "开始录制 CommandBuffer 失败",
        )?;

        let mut xr_image_to_release = false;
        if let (Some(frame), Some(data), Some(xr_pipeline)) =
            (xr_frame, hmd_data, self.xr_pipeline.as_ref())
        {
            let (xr_image_index, xr_framebuffers, xr_extent) = {
                let xr_swapchain = self
                    .xr_swapchain
                    .as_mut()
                    .ok_or_else(|| anyhow!("OpenXR Swapchain 尚未初始化"))?;
                let xr_image_index = xr_swapchain
                    .handle
                    .acquire_image()
                    .context("获取 OpenXR Swapchain 图像失败")?;
                xr_swapchain
                    .handle
                    .wait_image(xr::Duration::INFINITE)
                    .context("等待 OpenXR Swapchain 图像失败")?;
                (
                    xr_image_index,
                    xr_swapchain.framebuffers[xr_image_index as usize],
                    xr_swapchain.extent,
                )
            };

            for eye in 0..2usize {
                let mvp = projection_from_fov(frame.views[eye].fov, XR_NEAR, XR_FAR)
                    * view_from_pose(frame.views[eye].pose)
                    * xr_model_matrix;
                self.record_scene_pass(
                    command_buffer,
                    xr_pipeline,
                    xr_framebuffers[eye],
                    xr_extent,
                    assets,
                    data,
                    &self.hmd_vertex_buffer,
                    mvp,
                    false,
                )?;
            }
            let _ = xr_image_index;
            xr_image_to_release = true;
        }

        if let Some(image_index) = mirror_image {
            self.record_scene_pass(
                command_buffer,
                &self.mirror_pipeline,
                self.mirror.framebuffers[image_index as usize],
                self.mirror.extent,
                assets,
                mirror_data,
                &self.mirror_vertex_buffer,
                mirror_view_proj * mirror_model_matrix,
                false,
            )?;
        }

        vk_result(
            unsafe { self.device.end_command_buffer(command_buffer) },
            "结束 CommandBuffer 录制失败",
        )?;

        let mut wait_semaphores = Vec::new();
        let mut wait_stages = Vec::new();
        let mut signal_semaphores = Vec::new();
        if mirror_image.is_some() {
            wait_semaphores.push(image_available);
            wait_stages.push(vk::PipelineStageFlags::COLOR_ATTACHMENT_OUTPUT);
            signal_semaphores.push(render_finished);
        }

        let command_buffers = [command_buffer];
        let submit_info = vk::SubmitInfo::default()
            .wait_semaphores(&wait_semaphores)
            .wait_dst_stage_mask(&wait_stages)
            .command_buffers(&command_buffers)
            .signal_semaphores(&signal_semaphores);
        vk_result(
            unsafe { self.device.queue_submit(self.queue, &[submit_info], fence) },
            "提交 Vulkan 图形队列失败",
        )?;

        if xr_image_to_release {
            if let Some(xr_swapchain) = self.xr_swapchain.as_mut() {
                xr_swapchain
                    .handle
                    .release_image()
                    .context("释放 OpenXR Swapchain 图像失败")?;
            }
        }

        if let Some(image_index) = mirror_image {
            let present_wait = [render_finished];
            let present_swapchains = [self.mirror.swapchain];
            let present_indices = [image_index];
            let present_info = vk::PresentInfoKHR::default()
                .wait_semaphores(&present_wait)
                .swapchains(&present_swapchains)
                .image_indices(&present_indices);
            match unsafe {
                self.swapchain_loader
                    .queue_present(self.queue, &present_info)
            } {
                Ok(suboptimal) => {
                    if suboptimal {
                        self.resize_mirror(window)?;
                    }
                }
                Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                    self.resize_mirror(window)?;
                }
                Err(err) => return Err(anyhow!("提交桌面镜像失败: {err:?}")),
            }
        }

        self.frame_index = (self.frame_index + 1) % self.frames.len();
        Ok(())
    }

    fn build_projection_views<'a>(
        &'a self,
        frame: &BegunFrame,
    ) -> Result<[xr::CompositionLayerProjectionView<'a, xr::Vulkan>; 2]> {
        let xr_swapchain = self
            .xr_swapchain
            .as_ref()
            .ok_or_else(|| anyhow!("OpenXR Swapchain 尚未初始化"))?;
        Ok([
            xr::CompositionLayerProjectionView::new()
                .pose(frame.views[0].pose)
                .fov(frame.views[0].fov)
                .sub_image(
                    xr::SwapchainSubImage::new()
                        .swapchain(&xr_swapchain.handle)
                        .image_array_index(0)
                        .image_rect(xr_swapchain.rect),
                ),
            xr::CompositionLayerProjectionView::new()
                .pose(frame.views[1].pose)
                .fov(frame.views[1].fov)
                .sub_image(
                    xr::SwapchainSubImage::new()
                        .swapchain(&xr_swapchain.handle)
                        .image_array_index(1)
                        .image_rect(xr_swapchain.rect),
                ),
        ])
    }

    fn record_scene_pass(
        &self,
        command_buffer: vk::CommandBuffer,
        pipeline: &PipelineBundle,
        framebuffer: vk::Framebuffer,
        extent: vk::Extent2D,
        assets: &SceneAssets,
        render_data: &ModelRenderData<'_>,
        vertex_buffer: &BufferResource,
        mvp: Mat4,
        flip_viewport_y: bool,
    ) -> Result<()> {
        let clear_values = [
            vk::ClearValue {
                color: vk::ClearColorValue {
                    float32: [0.07, 0.08, 0.11, 1.0],
                },
            },
            vk::ClearValue {
                depth_stencil: vk::ClearDepthStencilValue {
                    depth: 1.0,
                    stencil: 0,
                },
            },
        ];
        let (viewport_y, viewport_height) = if flip_viewport_y {
            (extent.height as f32, -(extent.height as f32))
        } else {
            (0.0, extent.height as f32)
        };

        unsafe {
            self.device.cmd_begin_render_pass(
                command_buffer,
                &vk::RenderPassBeginInfo::default()
                    .render_pass(pipeline.render_pass)
                    .framebuffer(framebuffer)
                    .render_area(vk::Rect2D {
                        offset: vk::Offset2D { x: 0, y: 0 },
                        extent,
                    })
                    .clear_values(&clear_values),
                vk::SubpassContents::INLINE,
            );

            self.device.cmd_bind_pipeline(
                command_buffer,
                vk::PipelineBindPoint::GRAPHICS,
                pipeline.pipeline,
            );
            self.device.cmd_set_viewport(
                command_buffer,
                0,
                &[vk::Viewport {
                    x: 0.0,
                    y: viewport_y,
                    width: extent.width as f32,
                    height: viewport_height,
                    min_depth: 0.0,
                    max_depth: 1.0,
                }],
            );
            self.device.cmd_set_scissor(
                command_buffer,
                0,
                &[vk::Rect2D {
                    offset: vk::Offset2D { x: 0, y: 0 },
                    extent,
                }],
            );
            self.device
                .cmd_bind_vertex_buffers(command_buffer, 0, &[vertex_buffer.buffer], &[0]);
            self.device.cmd_bind_index_buffer(
                command_buffer,
                self.index_buffer.buffer,
                0,
                vk::IndexType::UINT32,
            );
        }

        let light_dir = Vec3::new(0.45, 1.0, 0.25).normalize();
        for submesh in &assets.submeshes {
            let material_index = submesh.material_id.max(0) as usize;
            if material_index >= assets.materials.len() {
                continue;
            }
            if !render_data
                .visible_materials
                .get(material_index)
                .copied()
                .unwrap_or(true)
            {
                continue;
            }

            let material = &assets.materials[material_index];
            let descriptor_set = self.texture_descriptor(material.texture_index);
            let cutoff = -1.0;
            let push = PushConstants {
                mvp: mvp.to_cols_array_2d(),
                diffuse: material.diffuse.to_array(),
                params: [light_dir.x, light_dir.y, light_dir.z, cutoff],
            };
            unsafe {
                self.device.cmd_bind_descriptor_sets(
                    command_buffer,
                    vk::PipelineBindPoint::GRAPHICS,
                    self.pipeline_layout,
                    0,
                    &[descriptor_set],
                    &[],
                );
                self.device.cmd_push_constants(
                    command_buffer,
                    self.pipeline_layout,
                    vk::ShaderStageFlags::VERTEX | vk::ShaderStageFlags::FRAGMENT,
                    0,
                    bytemuck::bytes_of(&push),
                );
                self.device.cmd_draw_indexed(
                    command_buffer,
                    submesh.index_count,
                    1,
                    submesh.begin_index,
                    0,
                    0,
                );
            }
        }

        unsafe {
            self.device.cmd_end_render_pass(command_buffer);
        }
        Ok(())
    }

    fn texture_descriptor(&self, texture_index: i32) -> vk::DescriptorSet {
        if texture_index >= 0 {
            self.textures
                .get(texture_index as usize)
                .map(|tex| tex.descriptor_set)
                .unwrap_or(self.default_texture.descriptor_set)
        } else {
            self.default_texture.descriptor_set
        }
    }

    fn load_textures(&self, texture_paths: &[String]) -> Result<Vec<TextureResource>> {
        let mut textures = Vec::with_capacity(texture_paths.len());
        for (index, path) in texture_paths.iter().enumerate() {
            let texture = self
                .load_texture(path)
                .with_context(|| format!("上传纹理失败 [{}]: {}", index, path))?;
            textures.push(texture);
        }
        Ok(textures)
    }

    fn upload_dynamic_vertices(
        device: &Device,
        scratch: &mut Vec<GpuVertex>,
        buffer: &BufferResource,
        render_data: &ModelRenderData<'_>,
    ) -> Result<()> {
        let vertex_count = render_data.positions.len() / 3;
        scratch.clear();
        scratch.reserve(vertex_count.saturating_sub(scratch.capacity()));
        for index in 0..vertex_count {
            let pos = index * 3;
            let uv = index * 2;
            scratch.push(GpuVertex {
                position: [
                    render_data.positions.get(pos).copied().unwrap_or(0.0),
                    render_data.positions.get(pos + 1).copied().unwrap_or(0.0),
                    render_data.positions.get(pos + 2).copied().unwrap_or(0.0),
                ],
                normal: [
                    render_data.normals.get(pos).copied().unwrap_or(0.0),
                    render_data.normals.get(pos + 1).copied().unwrap_or(1.0),
                    render_data.normals.get(pos + 2).copied().unwrap_or(0.0),
                ],
                uv: [
                    render_data.uvs.get(uv).copied().unwrap_or(0.0),
                    render_data.uvs.get(uv + 1).copied().unwrap_or(0.0),
                ],
            });
        }
        Self::write_buffer(device, buffer, bytemuck::cast_slice(scratch))
    }

    fn load_texture(&self, path: &str) -> Result<TextureResource> {
        if path.is_empty() {
            return self.create_texture_from_rgba8(1, 1, &[255, 255, 255, 255]);
        }

        match image::open(path) {
            Ok(image) => {
                let rgba = image::imageops::flip_vertical(&image.to_rgba8());
                let (width, height) = rgba.dimensions();
                self.create_texture_from_rgba8(width, height, rgba.as_raw())
            }
            Err(err) => {
                log::warn!("纹理加载失败，回退白图 {}: {}", path, err);
                self.create_texture_from_rgba8(1, 1, &[255, 255, 255, 255])
            }
        }
    }

    fn create_texture_from_rgba8(
        &self,
        width: u32,
        height: u32,
        pixels: &[u8],
    ) -> Result<TextureResource> {
        let staging = Self::create_host_visible_buffer(
            &self.instance,
            &self.device,
            &self.memory_properties,
            pixels.len() as u64,
            vk::BufferUsageFlags::TRANSFER_SRC,
        )?;
        Self::write_buffer(&self.device, &staging, pixels)?;

        let image = Self::create_image(
            &self.instance,
            &self.device,
            &self.memory_properties,
            vk::Extent2D { width, height },
            vk::Format::R8G8B8A8_SRGB,
            vk::ImageUsageFlags::TRANSFER_DST | vk::ImageUsageFlags::SAMPLED,
            vk::ImageAspectFlags::COLOR,
        )?;

        self.execute_one_time_commands(|device, command_buffer| unsafe {
            transition_image_layout(
                device,
                command_buffer,
                image.image,
                vk::ImageLayout::UNDEFINED,
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                vk::ImageAspectFlags::COLOR,
            );
            device.cmd_copy_buffer_to_image(
                command_buffer,
                staging.buffer,
                image.image,
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                &[vk::BufferImageCopy::default()
                    .image_subresource(vk::ImageSubresourceLayers {
                        aspect_mask: vk::ImageAspectFlags::COLOR,
                        mip_level: 0,
                        base_array_layer: 0,
                        layer_count: 1,
                    })
                    .image_extent(vk::Extent3D {
                        width,
                        height,
                        depth: 1,
                    })],
            );
            transition_image_layout(
                device,
                command_buffer,
                image.image,
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                vk::ImageLayout::SHADER_READ_ONLY_OPTIMAL,
                vk::ImageAspectFlags::COLOR,
            );
        })?;
        self.destroy_buffer(&staging);

        let descriptor_set = self.allocate_descriptor_set()?;
        let sampler_info = [vk::DescriptorImageInfo::default().sampler(self.sampler)];
        let image_info = [vk::DescriptorImageInfo::default()
            .image_view(image.view)
            .image_layout(vk::ImageLayout::SHADER_READ_ONLY_OPTIMAL)];
        let write = [
            vk::WriteDescriptorSet::default()
                .dst_set(descriptor_set)
                .dst_binding(0)
                .descriptor_type(vk::DescriptorType::SAMPLER)
                .image_info(&sampler_info),
            vk::WriteDescriptorSet::default()
                .dst_set(descriptor_set)
                .dst_binding(1)
                .descriptor_type(vk::DescriptorType::SAMPLED_IMAGE)
                .image_info(&image_info),
        ];
        unsafe {
            self.device.update_descriptor_sets(&write, &[]);
        }

        Ok(TextureResource {
            image,
            descriptor_set,
        })
    }

    fn allocate_descriptor_set(&self) -> Result<vk::DescriptorSet> {
        let layouts = [self.descriptor_set_layout];
        let descriptor_sets = vk_result(
            unsafe {
                self.device.allocate_descriptor_sets(
                    &vk::DescriptorSetAllocateInfo::default()
                        .descriptor_pool(self.descriptor_pool)
                        .set_layouts(&layouts),
                )
            },
            "分配纹理 DescriptorSet 失败",
        )?;
        descriptor_sets
            .into_iter()
            .next()
            .ok_or_else(|| anyhow!("未分配到纹理 DescriptorSet"))
    }

    fn execute_one_time_commands<F>(&self, recorder: F) -> Result<()>
    where
        F: FnOnce(&Device, vk::CommandBuffer),
    {
        let command_buffer = vk_result(
            unsafe {
                self.device.allocate_command_buffers(
                    &vk::CommandBufferAllocateInfo::default()
                        .command_pool(self.command_pool)
                        .level(vk::CommandBufferLevel::PRIMARY)
                        .command_buffer_count(1),
                )
            },
            "分配一次性 CommandBuffer 失败",
        )?[0];

        vk_result(
            unsafe {
                self.device.begin_command_buffer(
                    command_buffer,
                    &vk::CommandBufferBeginInfo::default()
                        .flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT),
                )
            },
            "开始一次性 CommandBuffer 失败",
        )?;

        recorder(&self.device, command_buffer);

        vk_result(
            unsafe { self.device.end_command_buffer(command_buffer) },
            "结束一次性 CommandBuffer 失败",
        )?;
        let command_buffers = [command_buffer];
        let submit = [vk::SubmitInfo::default().command_buffers(&command_buffers)];
        vk_result(
            unsafe {
                self.device
                    .queue_submit(self.queue, &submit, vk::Fence::null())
            },
            "提交一次性 CommandBuffer 失败",
        )?;
        vk_result(
            unsafe { self.device.queue_wait_idle(self.queue) },
            "等待队列空闲失败",
        )?;
        unsafe {
            self.device
                .free_command_buffers(self.command_pool, &[command_buffer]);
        }
        Ok(())
    }

    fn acquire_mirror_image(
        &mut self,
        window: &Window,
        semaphore: vk::Semaphore,
    ) -> Result<Option<u32>> {
        match unsafe {
            self.swapchain_loader.acquire_next_image(
                self.mirror.swapchain,
                u64::MAX,
                semaphore,
                vk::Fence::null(),
            )
        } {
            Ok((index, suboptimal)) => {
                if suboptimal {
                    self.resize_mirror(window)?;
                }
                Ok(Some(index))
            }
            Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                self.resize_mirror(window)?;
                Ok(None)
            }
            Err(err) => Err(anyhow!("获取桌面镜像 Swapchain 图像失败: {err:?}")),
        }
    }

    fn create_descriptor_set_layout(device: &Device) -> Result<vk::DescriptorSetLayout> {
        vk_result(
            unsafe {
                device.create_descriptor_set_layout(
                    &vk::DescriptorSetLayoutCreateInfo::default().bindings(&[
                        vk::DescriptorSetLayoutBinding::default()
                            .binding(0)
                            .descriptor_type(vk::DescriptorType::SAMPLER)
                            .descriptor_count(1)
                            .stage_flags(vk::ShaderStageFlags::FRAGMENT),
                        vk::DescriptorSetLayoutBinding::default()
                            .binding(1)
                            .descriptor_type(vk::DescriptorType::SAMPLED_IMAGE)
                            .descriptor_count(1)
                            .stage_flags(vk::ShaderStageFlags::FRAGMENT),
                    ]),
                    None,
                )
            },
            "创建 DescriptorSetLayout 失败",
        )
    }

    fn create_descriptor_pool(device: &Device, texture_count: usize) -> Result<vk::DescriptorPool> {
        vk_result(
            unsafe {
                device.create_descriptor_pool(
                    &vk::DescriptorPoolCreateInfo::default()
                        .max_sets(texture_count as u32)
                        .pool_sizes(&[
                            vk::DescriptorPoolSize {
                                ty: vk::DescriptorType::SAMPLER,
                                descriptor_count: texture_count as u32,
                            },
                            vk::DescriptorPoolSize {
                                ty: vk::DescriptorType::SAMPLED_IMAGE,
                                descriptor_count: texture_count as u32,
                            },
                        ]),
                    None,
                )
            },
            "创建 DescriptorPool 失败",
        )
    }

    fn create_pipeline_layout(
        device: &Device,
        descriptor_set_layout: vk::DescriptorSetLayout,
    ) -> Result<vk::PipelineLayout> {
        let push_constant_range = vk::PushConstantRange::default()
            .stage_flags(vk::ShaderStageFlags::VERTEX | vk::ShaderStageFlags::FRAGMENT)
            .offset(0)
            .size(size_of::<PushConstants>() as u32);
        vk_result(
            unsafe {
                device.create_pipeline_layout(
                    &vk::PipelineLayoutCreateInfo::default()
                        .set_layouts(&[descriptor_set_layout])
                        .push_constant_ranges(&[push_constant_range]),
                    None,
                )
            },
            "创建 PipelineLayout 失败",
        )
    }

    fn create_sampler(device: &Device) -> Result<vk::Sampler> {
        vk_result(
            unsafe {
                device.create_sampler(
                    &vk::SamplerCreateInfo::default()
                        .mag_filter(vk::Filter::LINEAR)
                        .min_filter(vk::Filter::LINEAR)
                        .mipmap_mode(vk::SamplerMipmapMode::LINEAR)
                        .address_mode_u(vk::SamplerAddressMode::REPEAT)
                        .address_mode_v(vk::SamplerAddressMode::REPEAT)
                        .address_mode_w(vk::SamplerAddressMode::REPEAT)
                        .max_lod(1.0),
                    None,
                )
            },
            "创建纹理 Sampler 失败",
        )
    }

    fn create_pipeline_bundle(
        device: &Device,
        pipeline_layout: vk::PipelineLayout,
        color_format: vk::Format,
        color_final_layout: vk::ImageLayout,
        vert_spv: &[u32],
        frag_spv: &[u32],
    ) -> Result<PipelineBundle> {
        let render_pass = Self::create_render_pass(device, color_format, color_final_layout)?;
        let vert_module = vk_result(
            unsafe {
                device.create_shader_module(
                    &vk::ShaderModuleCreateInfo::default().code(vert_spv),
                    None,
                )
            },
            "创建顶点 ShaderModule 失败",
        )?;
        let frag_module = vk_result(
            unsafe {
                device.create_shader_module(
                    &vk::ShaderModuleCreateInfo::default().code(frag_spv),
                    None,
                )
            },
            "创建片元 ShaderModule 失败",
        )?;

        let entry_name = CString::new("main").unwrap();
        let vertex_binding = [vk::VertexInputBindingDescription::default()
            .binding(0)
            .stride(size_of::<GpuVertex>() as u32)
            .input_rate(vk::VertexInputRate::VERTEX)];
        let vertex_attributes = [
            vk::VertexInputAttributeDescription::default()
                .location(0)
                .binding(0)
                .format(vk::Format::R32G32B32_SFLOAT)
                .offset(0),
            vk::VertexInputAttributeDescription::default()
                .location(1)
                .binding(0)
                .format(vk::Format::R32G32B32_SFLOAT)
                .offset(12),
            vk::VertexInputAttributeDescription::default()
                .location(2)
                .binding(0)
                .format(vk::Format::R32G32_SFLOAT)
                .offset(24),
        ];
        let color_blend_attachment = vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::SRC_ALPHA)
            .dst_color_blend_factor(vk::BlendFactor::ONE_MINUS_SRC_ALPHA)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ONE_MINUS_SRC_ALPHA)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(
                vk::ColorComponentFlags::R
                    | vk::ColorComponentFlags::G
                    | vk::ColorComponentFlags::B
                    | vk::ColorComponentFlags::A,
            );
        let dynamic_states = [vk::DynamicState::VIEWPORT, vk::DynamicState::SCISSOR];
        let pipeline = match unsafe {
            device.create_graphics_pipelines(
                vk::PipelineCache::null(),
                &[vk::GraphicsPipelineCreateInfo::default()
                    .stages(&[
                        vk::PipelineShaderStageCreateInfo::default()
                            .stage(vk::ShaderStageFlags::VERTEX)
                            .module(vert_module)
                            .name(&entry_name),
                        vk::PipelineShaderStageCreateInfo::default()
                            .stage(vk::ShaderStageFlags::FRAGMENT)
                            .module(frag_module)
                            .name(&entry_name),
                    ])
                    .vertex_input_state(
                        &vk::PipelineVertexInputStateCreateInfo::default()
                            .vertex_binding_descriptions(&vertex_binding)
                            .vertex_attribute_descriptions(&vertex_attributes),
                    )
                    .input_assembly_state(
                        &vk::PipelineInputAssemblyStateCreateInfo::default()
                            .topology(vk::PrimitiveTopology::TRIANGLE_LIST),
                    )
                    .viewport_state(
                        &vk::PipelineViewportStateCreateInfo::default()
                            .viewport_count(1)
                            .scissor_count(1),
                    )
                    .rasterization_state(
                        &vk::PipelineRasterizationStateCreateInfo::default()
                            .polygon_mode(vk::PolygonMode::FILL)
                            .line_width(1.0)
                            .cull_mode(vk::CullModeFlags::NONE)
                            .front_face(vk::FrontFace::COUNTER_CLOCKWISE),
                    )
                    .multisample_state(
                        &vk::PipelineMultisampleStateCreateInfo::default()
                            .rasterization_samples(vk::SampleCountFlags::TYPE_1),
                    )
                    .depth_stencil_state(
                        &vk::PipelineDepthStencilStateCreateInfo::default()
                            .depth_test_enable(true)
                            .depth_write_enable(true)
                            .depth_compare_op(vk::CompareOp::LESS_OR_EQUAL),
                    )
                    .color_blend_state(
                        &vk::PipelineColorBlendStateCreateInfo::default()
                            .attachments(&[color_blend_attachment]),
                    )
                    .dynamic_state(
                        &vk::PipelineDynamicStateCreateInfo::default()
                            .dynamic_states(&dynamic_states),
                    )
                    .layout(pipeline_layout)
                    .render_pass(render_pass)
                    .subpass(0)],
                None,
            )
        } {
            Ok(mut pipelines) => pipelines.remove(0),
            Err((_, err)) => return Err(anyhow!("创建图形 Pipeline 失败: {:?}", err)),
        };

        unsafe {
            device.destroy_shader_module(vert_module, None);
            device.destroy_shader_module(frag_module, None);
        }

        Ok(PipelineBundle {
            render_pass,
            pipeline,
        })
    }

    fn create_render_pass(
        device: &Device,
        color_format: vk::Format,
        color_final_layout: vk::ImageLayout,
    ) -> Result<vk::RenderPass> {
        let attachments = [
            vk::AttachmentDescription::default()
                .format(color_format)
                .samples(vk::SampleCountFlags::TYPE_1)
                .load_op(vk::AttachmentLoadOp::CLEAR)
                .store_op(vk::AttachmentStoreOp::STORE)
                .initial_layout(vk::ImageLayout::UNDEFINED)
                .final_layout(color_final_layout),
            vk::AttachmentDescription::default()
                .format(DEPTH_FORMAT)
                .samples(vk::SampleCountFlags::TYPE_1)
                .load_op(vk::AttachmentLoadOp::CLEAR)
                .store_op(vk::AttachmentStoreOp::DONT_CARE)
                .stencil_load_op(vk::AttachmentLoadOp::DONT_CARE)
                .stencil_store_op(vk::AttachmentStoreOp::DONT_CARE)
                .initial_layout(vk::ImageLayout::UNDEFINED)
                .final_layout(vk::ImageLayout::DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
        ];
        let color_ref = [vk::AttachmentReference {
            attachment: 0,
            layout: vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL,
        }];
        let depth_ref = vk::AttachmentReference {
            attachment: 1,
            layout: vk::ImageLayout::DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
        };
        vk_result(
            unsafe {
                device.create_render_pass(
                    &vk::RenderPassCreateInfo::default()
                        .attachments(&attachments)
                        .subpasses(&[vk::SubpassDescription::default()
                            .pipeline_bind_point(vk::PipelineBindPoint::GRAPHICS)
                            .color_attachments(&color_ref)
                            .depth_stencil_attachment(&depth_ref)])
                        .dependencies(&[vk::SubpassDependency::default()
                            .src_subpass(vk::SUBPASS_EXTERNAL)
                            .dst_subpass(0)
                            .src_stage_mask(vk::PipelineStageFlags::COLOR_ATTACHMENT_OUTPUT)
                            .dst_stage_mask(vk::PipelineStageFlags::COLOR_ATTACHMENT_OUTPUT)
                            .dst_access_mask(
                                vk::AccessFlags::COLOR_ATTACHMENT_WRITE
                                    | vk::AccessFlags::DEPTH_STENCIL_ATTACHMENT_WRITE,
                            )]),
                    None,
                )
            },
            "创建 RenderPass 失败",
        )
    }

    fn create_mirror_swapchain(
        instance: &Instance,
        device: &Device,
        physical_device: vk::PhysicalDevice,
        surface_loader: &ash::khr::surface::Instance,
        swapchain_loader: &ash::khr::swapchain::Device,
        surface: vk::SurfaceKHR,
        size: PhysicalSize<u32>,
    ) -> Result<MirrorSwapchain> {
        let capabilities = vk_result(
            unsafe {
                surface_loader.get_physical_device_surface_capabilities(physical_device, surface)
            },
            "读取桌面 SurfaceCapabilities 失败",
        )?;
        let formats = vk_result(
            unsafe { surface_loader.get_physical_device_surface_formats(physical_device, surface) },
            "读取桌面 SurfaceFormats 失败",
        )?;
        let present_modes = vk_result(
            unsafe {
                surface_loader.get_physical_device_surface_present_modes(physical_device, surface)
            },
            "读取桌面 PresentModes 失败",
        )?;

        let surface_format = formats
            .iter()
            .find(|format| format.format == vk::Format::B8G8R8A8_SRGB)
            .copied()
            .or_else(|| {
                formats
                    .iter()
                    .find(|format| format.format == vk::Format::R8G8B8A8_SRGB)
                    .copied()
            })
            .unwrap_or_else(|| formats[0]);
        let present_mode = if present_modes.contains(&vk::PresentModeKHR::MAILBOX) {
            vk::PresentModeKHR::MAILBOX
        } else {
            vk::PresentModeKHR::FIFO
        };

        let mut extent = if capabilities.current_extent.width == u32::MAX {
            vk::Extent2D {
                width: size.width.max(1),
                height: size.height.max(1),
            }
        } else {
            capabilities.current_extent
        };
        extent.width = extent.width.clamp(
            capabilities.min_image_extent.width,
            capabilities.max_image_extent.width.max(1),
        );
        extent.height = extent.height.clamp(
            capabilities.min_image_extent.height,
            capabilities.max_image_extent.height.max(1),
        );

        let min_image_count = (capabilities.min_image_count + 1).min(
            capabilities
                .max_image_count
                .max(capabilities.min_image_count + 1),
        );
        let swapchain = vk_result(
            unsafe {
                swapchain_loader.create_swapchain(
                    &vk::SwapchainCreateInfoKHR::default()
                        .surface(surface)
                        .min_image_count(min_image_count)
                        .image_format(surface_format.format)
                        .image_color_space(surface_format.color_space)
                        .image_extent(extent)
                        .image_array_layers(1)
                        .image_usage(vk::ImageUsageFlags::COLOR_ATTACHMENT)
                        .image_sharing_mode(vk::SharingMode::EXCLUSIVE)
                        .pre_transform(capabilities.current_transform)
                        .composite_alpha(vk::CompositeAlphaFlagsKHR::OPAQUE)
                        .present_mode(present_mode)
                        .clipped(true),
                    None,
                )
            },
            "创建桌面镜像 Swapchain 失败",
        )?;

        let images = vk_result(
            unsafe { swapchain_loader.get_swapchain_images(swapchain) },
            "读取桌面镜像 Swapchain 图像失败",
        )?;
        let image_views = images
            .iter()
            .map(|image| {
                vk_result(
                    unsafe {
                        device.create_image_view(
                            &vk::ImageViewCreateInfo::default()
                                .image(*image)
                                .view_type(vk::ImageViewType::TYPE_2D)
                                .format(surface_format.format)
                                .subresource_range(color_subresource_range(0, 1)),
                            None,
                        )
                    },
                    "创建桌面镜像 ImageView 失败",
                )
            })
            .collect::<Result<Vec<_>>>()?;
        let depth = Some(Self::create_depth_image(
            instance,
            device,
            &unsafe { instance.get_physical_device_memory_properties(physical_device) },
            extent,
        )?);

        Ok(MirrorSwapchain {
            swapchain,
            format: surface_format.format,
            extent,
            image_views,
            framebuffers: Vec::new(),
            depth,
        })
    }

    fn create_depth_image(
        instance: &Instance,
        device: &Device,
        memory_properties: &vk::PhysicalDeviceMemoryProperties,
        extent: vk::Extent2D,
    ) -> Result<ImageResource> {
        Self::create_image(
            instance,
            device,
            memory_properties,
            extent,
            DEPTH_FORMAT,
            vk::ImageUsageFlags::DEPTH_STENCIL_ATTACHMENT,
            vk::ImageAspectFlags::DEPTH,
        )
    }

    fn create_image(
        _instance: &Instance,
        device: &Device,
        memory_properties: &vk::PhysicalDeviceMemoryProperties,
        extent: vk::Extent2D,
        format: vk::Format,
        usage: vk::ImageUsageFlags,
        aspect: vk::ImageAspectFlags,
    ) -> Result<ImageResource> {
        let image = vk_result(
            unsafe {
                device.create_image(
                    &vk::ImageCreateInfo::default()
                        .image_type(vk::ImageType::TYPE_2D)
                        .format(format)
                        .extent(vk::Extent3D {
                            width: extent.width,
                            height: extent.height,
                            depth: 1,
                        })
                        .mip_levels(1)
                        .array_layers(1)
                        .samples(vk::SampleCountFlags::TYPE_1)
                        .tiling(vk::ImageTiling::OPTIMAL)
                        .usage(usage)
                        .sharing_mode(vk::SharingMode::EXCLUSIVE)
                        .initial_layout(vk::ImageLayout::UNDEFINED),
                    None,
                )
            },
            "创建 Vulkan Image 失败",
        )?;
        let requirements = unsafe { device.get_image_memory_requirements(image) };
        let memory = Self::allocate_memory(
            device,
            memory_properties,
            requirements,
            vk::MemoryPropertyFlags::DEVICE_LOCAL,
        )?;
        vk_result(
            unsafe { device.bind_image_memory(image, memory, 0) },
            "绑定 Vulkan Image 内存失败",
        )?;
        let view = vk_result(
            unsafe {
                device.create_image_view(
                    &vk::ImageViewCreateInfo::default()
                        .image(image)
                        .view_type(vk::ImageViewType::TYPE_2D)
                        .format(format)
                        .subresource_range(vk::ImageSubresourceRange {
                            aspect_mask: aspect,
                            base_mip_level: 0,
                            level_count: 1,
                            base_array_layer: 0,
                            layer_count: 1,
                        }),
                    None,
                )
            },
            "创建 Vulkan ImageView 失败",
        )?;

        Ok(ImageResource {
            image,
            memory,
            view,
        })
    }

    fn create_host_visible_buffer(
        instance: &Instance,
        device: &Device,
        memory_properties: &vk::PhysicalDeviceMemoryProperties,
        size: u64,
        usage: vk::BufferUsageFlags,
    ) -> Result<BufferResource> {
        let buffer = vk_result(
            unsafe {
                device.create_buffer(
                    &vk::BufferCreateInfo::default()
                        .size(size)
                        .usage(usage)
                        .sharing_mode(vk::SharingMode::EXCLUSIVE),
                    None,
                )
            },
            "创建 Vulkan Buffer 失败",
        )?;
        let requirements = unsafe { device.get_buffer_memory_requirements(buffer) };
        let memory = Self::allocate_memory(
            device,
            memory_properties,
            requirements,
            vk::MemoryPropertyFlags::HOST_VISIBLE | vk::MemoryPropertyFlags::HOST_COHERENT,
        )?;
        vk_result(
            unsafe { device.bind_buffer_memory(buffer, memory, 0) },
            "绑定 Vulkan Buffer 内存失败",
        )?;
        let _ = instance;
        Ok(BufferResource {
            buffer,
            memory,
            size,
        })
    }

    fn allocate_memory(
        device: &Device,
        memory_properties: &vk::PhysicalDeviceMemoryProperties,
        requirements: vk::MemoryRequirements,
        flags: vk::MemoryPropertyFlags,
    ) -> Result<vk::DeviceMemory> {
        let memory_type_index =
            find_memory_type(memory_properties, requirements.memory_type_bits, flags)
                .ok_or_else(|| anyhow!("未找到满足要求的 Vulkan 内存类型"))?;
        vk_result(
            unsafe {
                device.allocate_memory(
                    &vk::MemoryAllocateInfo::default()
                        .allocation_size(requirements.size)
                        .memory_type_index(memory_type_index),
                    None,
                )
            },
            "分配 Vulkan 内存失败",
        )
    }

    fn write_buffer(device: &Device, buffer: &BufferResource, bytes: &[u8]) -> Result<()> {
        if bytes.len() as u64 > buffer.size {
            return Err(anyhow!(
                "写入 Buffer 数据过大: {} > {}",
                bytes.len(),
                buffer.size
            ));
        }
        let mapped = vk_result(
            unsafe {
                device.map_memory(
                    buffer.memory,
                    0,
                    bytes.len() as u64,
                    vk::MemoryMapFlags::empty(),
                )
            },
            "映射 Vulkan 内存失败",
        )?;
        unsafe {
            copy_nonoverlapping(bytes.as_ptr(), mapped.cast::<u8>(), bytes.len());
            device.unmap_memory(buffer.memory);
        }
        Ok(())
    }

    fn find_queue_family(
        instance: &Instance,
        surface_loader: &ash::khr::surface::Instance,
        physical_device: vk::PhysicalDevice,
        surface: vk::SurfaceKHR,
    ) -> Result<u32> {
        let families =
            unsafe { instance.get_physical_device_queue_family_properties(physical_device) };
        for (index, family) in families.iter().enumerate() {
            let supports_present = vk_result(
                unsafe {
                    surface_loader.get_physical_device_surface_support(
                        physical_device,
                        index as u32,
                        surface,
                    )
                },
                "查询桌面 Surface Present 支持失败",
            )?;
            if family.queue_flags.contains(vk::QueueFlags::GRAPHICS) && supports_present {
                return Ok(index as u32);
            }
        }
        Err(anyhow!("未找到同时支持 graphics/present 的 Vulkan 队列族"))
    }

    fn destroy_buffer(&self, buffer: &BufferResource) {
        unsafe {
            self.device.destroy_buffer(buffer.buffer, None);
            self.device.free_memory(buffer.memory, None);
        }
    }

    fn destroy_mirror_targets(&mut self) {
        unsafe {
            for framebuffer in self.mirror.framebuffers.drain(..) {
                self.device.destroy_framebuffer(framebuffer, None);
            }
            if let Some(depth) = self.mirror.depth.take() {
                self.device.destroy_image_view(depth.view, None);
                self.device.destroy_image(depth.image, None);
                self.device.free_memory(depth.memory, None);
            }
            for view in self.mirror.image_views.drain(..) {
                self.device.destroy_image_view(view, None);
            }
            if self.mirror_pipeline.pipeline != vk::Pipeline::null() {
                self.device
                    .destroy_pipeline(self.mirror_pipeline.pipeline, None);
                self.device
                    .destroy_render_pass(self.mirror_pipeline.render_pass, None);
                self.mirror_pipeline.pipeline = vk::Pipeline::null();
                self.mirror_pipeline.render_pass = vk::RenderPass::null();
            }
            if self.mirror.swapchain != vk::SwapchainKHR::null() {
                self.swapchain_loader
                    .destroy_swapchain(self.mirror.swapchain, None);
                self.mirror.swapchain = vk::SwapchainKHR::null();
            }
        }
    }

    fn rebuild_mirror_framebuffers(&mut self) -> Result<()> {
        let depth_view = self
            .mirror
            .depth
            .as_ref()
            .ok_or_else(|| anyhow!("桌面镜像深度缓冲未初始化"))?
            .view;
        self.mirror.framebuffers = self
            .mirror
            .image_views
            .iter()
            .map(|view| {
                vk_result(
                    unsafe {
                        self.device.create_framebuffer(
                            &vk::FramebufferCreateInfo::default()
                                .render_pass(self.mirror_pipeline.render_pass)
                                .attachments(&[*view, depth_view])
                                .width(self.mirror.extent.width)
                                .height(self.mirror.extent.height)
                                .layers(1),
                            None,
                        )
                    },
                    "创建桌面镜像 Framebuffer 失败",
                )
            })
            .collect::<Result<Vec<_>>>()?;
        Ok(())
    }
}

impl Drop for VulkanRenderer {
    fn drop(&mut self) {
        unsafe {
            let _ = self.device.device_wait_idle();
        }

        if let Some(xr_swapchain) = self.xr_swapchain.take() {
            unsafe {
                for fb_pair in xr_swapchain.framebuffers {
                    self.device.destroy_framebuffer(fb_pair[0], None);
                    self.device.destroy_framebuffer(fb_pair[1], None);
                }
                for view_pair in xr_swapchain.layer_views {
                    self.device.destroy_image_view(view_pair[0], None);
                    self.device.destroy_image_view(view_pair[1], None);
                }
                self.device
                    .destroy_image_view(xr_swapchain.depth.view, None);
                self.device.destroy_image(xr_swapchain.depth.image, None);
                self.device.free_memory(xr_swapchain.depth.memory, None);
            }
            drop(xr_swapchain.handle);
        }

        if let Some(xr_pipeline) = self.xr_pipeline.take() {
            unsafe {
                self.device.destroy_pipeline(xr_pipeline.pipeline, None);
                self.device
                    .destroy_render_pass(xr_pipeline.render_pass, None);
            }
        }

        self.destroy_mirror_targets();

        unsafe {
            for texture in self.textures.drain(..) {
                self.device.destroy_image_view(texture.image.view, None);
                self.device.destroy_image(texture.image.image, None);
                self.device.free_memory(texture.image.memory, None);
            }
            self.device
                .destroy_image_view(self.default_texture.image.view, None);
            self.device
                .destroy_image(self.default_texture.image.image, None);
            self.device
                .free_memory(self.default_texture.image.memory, None);

            self.device.destroy_sampler(self.sampler, None);
            self.device
                .destroy_descriptor_pool(self.descriptor_pool, None);
            self.device
                .destroy_descriptor_set_layout(self.descriptor_set_layout, None);
            self.device
                .destroy_pipeline_layout(self.pipeline_layout, None);

            self.destroy_buffer(&self.index_buffer);
            self.destroy_buffer(&self.hmd_vertex_buffer);
            self.destroy_buffer(&self.mirror_vertex_buffer);

            for frame in self.frames.drain(..) {
                self.device.destroy_fence(frame.fence, None);
                self.device.destroy_semaphore(frame.image_available, None);
                self.device.destroy_semaphore(frame.render_finished, None);
            }

            self.device.destroy_command_pool(self.command_pool, None);
            self.device.destroy_device(None);
            self.surface_loader.destroy_surface(self.surface, None);
            self.instance.destroy_instance(None);
        }
    }
}

fn compile_shader(source: &str, stage: naga::ShaderStage, name: &str) -> Result<Vec<u32>> {
    let module = naga::front::wgsl::parse_str(source)
        .map_err(|err| anyhow!("解析 WGSL 失败 {name}: {err}"))?;
    let capabilities = Capabilities::PUSH_CONSTANT | Capabilities::CUBE_ARRAY_TEXTURES;
    let mut validator = Validator::new(ValidationFlags::all(), capabilities);
    let info = validator
        .validate(&module)
        .map_err(|err| anyhow!("校验 WGSL 失败 {name}: {err}"))?;

    spv::write_vec(
        &module,
        &info,
        &spv::Options::default(),
        Some(&spv::PipelineOptions {
            shader_stage: stage,
            entry_point: String::from("main"),
        }),
    )
    .map_err(|err| anyhow!("生成 SPIR-V 失败 {name}: {err}"))
}

fn projection_from_fov(fov: xr::Fovf, near: f32, far: f32) -> Mat4 {
    let tan_left = fov.angle_left.tan();
    let tan_right = fov.angle_right.tan();
    let tan_down = fov.angle_down.tan();
    let tan_up = fov.angle_up.tan();
    let width = tan_right - tan_left;
    let height = tan_up - tan_down;

    Mat4::from_cols(
        Vec4::new(2.0 / width, 0.0, 0.0, 0.0),
        Vec4::new(0.0, 2.0 / height, 0.0, 0.0),
        Vec4::new(
            (tan_right + tan_left) / width,
            (tan_up + tan_down) / height,
            -far / (far - near),
            -1.0,
        ),
        Vec4::new(0.0, 0.0, -(far * near) / (far - near), 0.0),
    )
}

fn view_from_pose(pose: xr::Posef) -> Mat4 {
    let rotation = Quat::from_xyzw(
        pose.orientation.x,
        pose.orientation.y,
        pose.orientation.z,
        pose.orientation.w,
    )
    .normalize();
    let translation = Vec3::new(pose.position.x, pose.position.y, pose.position.z);
    Mat4::from_rotation_translation(rotation, translation).inverse()
}

fn transition_image_layout(
    device: &Device,
    command_buffer: vk::CommandBuffer,
    image: vk::Image,
    old_layout: vk::ImageLayout,
    new_layout: vk::ImageLayout,
    aspect_mask: vk::ImageAspectFlags,
) {
    let (src_stage, src_access) = match old_layout {
        vk::ImageLayout::UNDEFINED => (
            vk::PipelineStageFlags::TOP_OF_PIPE,
            vk::AccessFlags::empty(),
        ),
        vk::ImageLayout::TRANSFER_DST_OPTIMAL => (
            vk::PipelineStageFlags::TRANSFER,
            vk::AccessFlags::TRANSFER_WRITE,
        ),
        _ => (
            vk::PipelineStageFlags::TOP_OF_PIPE,
            vk::AccessFlags::empty(),
        ),
    };
    let (dst_stage, dst_access) = match new_layout {
        vk::ImageLayout::TRANSFER_DST_OPTIMAL => (
            vk::PipelineStageFlags::TRANSFER,
            vk::AccessFlags::TRANSFER_WRITE,
        ),
        vk::ImageLayout::SHADER_READ_ONLY_OPTIMAL => (
            vk::PipelineStageFlags::FRAGMENT_SHADER,
            vk::AccessFlags::SHADER_READ,
        ),
        _ => (
            vk::PipelineStageFlags::BOTTOM_OF_PIPE,
            vk::AccessFlags::empty(),
        ),
    };
    let barrier = vk::ImageMemoryBarrier::default()
        .old_layout(old_layout)
        .new_layout(new_layout)
        .src_access_mask(src_access)
        .dst_access_mask(dst_access)
        .image(image)
        .subresource_range(vk::ImageSubresourceRange {
            aspect_mask,
            base_mip_level: 0,
            level_count: 1,
            base_array_layer: 0,
            layer_count: 1,
        });
    unsafe {
        device.cmd_pipeline_barrier(
            command_buffer,
            src_stage,
            dst_stage,
            vk::DependencyFlags::empty(),
            &[],
            &[],
            &[barrier],
        );
    }
}

fn color_subresource_range(base_layer: u32, layer_count: u32) -> vk::ImageSubresourceRange {
    vk::ImageSubresourceRange {
        aspect_mask: vk::ImageAspectFlags::COLOR,
        base_mip_level: 0,
        level_count: 1,
        base_array_layer: base_layer,
        layer_count,
    }
}

fn find_memory_type(
    memory_properties: &vk::PhysicalDeviceMemoryProperties,
    type_bits: u32,
    required: vk::MemoryPropertyFlags,
) -> Option<u32> {
    for index in 0..memory_properties.memory_type_count {
        let supported = (type_bits & (1 << index)) != 0;
        let flags = memory_properties.memory_types[index as usize].property_flags;
        if supported && flags.contains(required) {
            return Some(index);
        }
    }
    None
}

fn vk_result<T>(result: ash::prelude::VkResult<T>, context: &str) -> Result<T> {
    result.map_err(|err| anyhow!("{}: {:?}", context, err))
}
