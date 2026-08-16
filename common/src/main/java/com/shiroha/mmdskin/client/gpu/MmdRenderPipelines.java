// 负责定义并验证 Minecraft 26.2 MMD RenderPipeline 变体。
package com.shiroha.mmdskin.client.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MmdRenderPipelines {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier SHADER = id("core/mmd_entity");
    private static final int MAX_PALETTE_BONES = NativeRenderDataPort.MAX_PALETTE_BONES;

    // 26.2 绑定组布局：uniform 一律为 std140 UBO 块（块名 = 布局 uniform 名）
    private static final BindGroupLayout MMD_LAYOUT = BindGroupLayout.builder()
            .withUniform("MmdTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("MmdModel", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .build();
    private static final BindGroupLayout MMD_BONES_LAYOUT = BindGroupLayout.builder()
            .withUniform("MmdBones", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout FOG_LAYOUT = BindGroupLayouts.FOG;

    // 顶点格式必须与 Rust 引擎的字节布局完全一致：
    // CPU 回退 28 字节 = Position(12) + UV0(8) + Color(4) + Normal(4)；
    // GPU 蒙皮 36 字节 = Position(12) + Color/权重(4) + UV0(8) + UV1/UV2 骨骼(8) + Normal(4)。
    // 26.2 的 DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL 把 UV0 与 Color 调换了顺序，
    // 直接使用会导致 GPU 把 UV 字节当颜色读到，fsh 中 alpha<=0.1 被 discard，模型整体透明。
    private static final VertexFormat MMD_CPU_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .build();
    private static final VertexFormat MMD_GPU_FORMAT = VertexFormat.builder(0)
            .addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
            .addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
            .addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
            .addAttribute(DefaultVertexFormat.UV1_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
            .addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
            .build();

    // 26.2 深度方向反转：比较方向为 GREATER_THAN_OR_EQUAL（与原版 RenderPipelines 一致）
    private static final DepthStencilState DEPTH_WRITE =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);
    private static final DepthStencilState DEPTH_NO_WRITE =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    private static final Map<PipelineVariant, RenderPipeline> CPU_PIPELINES = createPipelines(false);
    private static final Map<PipelineVariant, RenderPipeline> GPU_PIPELINES = createPipelines(true);
    private static volatile boolean ready;
    private static volatile boolean gpuReady;

    private MmdRenderPipelines() {
    }

    public static RenderPipeline pipeline(PipelineVariant variant) {
        return pipeline(variant, false);
    }

    public static RenderPipeline pipeline(PipelineVariant variant, boolean gpuSkinning) {
        RenderPipeline pipeline = (gpuSkinning ? GPU_PIPELINES : CPU_PIPELINES).get(variant);
        if (pipeline == null) {
            throw new IllegalArgumentException("unsupported MMD pipeline variant: " + variant);
        }
        return pipeline;
    }

    public static List<RenderPipeline> all() {
        ArrayList<RenderPipeline> pipelines = new ArrayList<>(CPU_PIPELINES.values());
        pipelines.addAll(GPU_PIPELINES.values());
        return List.copyOf(pipelines);
    }

    public static boolean validateAndActivate(GpuDevice device) {
        return validateAndActivate(device, null);
    }

    public static boolean validateAndActivate(
            GpuDevice device,
            ShaderSource shaderSource) {
        boolean valid = validate(device, shaderSource, CPU_PIPELINES.values());
        boolean validGpu = validate(device, shaderSource, GPU_PIPELINES.values());
        ready = valid;
        gpuReady = validGpu;
        return valid;
    }

    private static boolean validate(GpuDevice device,
                                    ShaderSource shaderSource,
                                    java.util.Collection<RenderPipeline> pipelines) {
        boolean valid = true;
        for (RenderPipeline pipeline : pipelines) {
            CompiledRenderPipeline compiled = device.precompilePipeline(pipeline, shaderSource);
            boolean pipelineValid = compiled.isValid();
            valid &= pipelineValid;
            if (!pipelineValid) {
                LOGGER.error("MMD 管线预编译失败: {}", pipeline.getLocation());
            }
        }
        return valid;
    }

    public static void activateRegisteredPipelines() {
        ready = true;
        gpuReady = true;
    }

    public static void deactivate() {
        ready = false;
        gpuReady = false;
    }

    public static boolean isReady() {
        return ready;
    }

    public static boolean isGpuReady() {
        return ready && gpuReady;
    }

    private static Map<PipelineVariant, RenderPipeline> createPipelines(boolean gpu) {
        EnumMap<PipelineVariant, RenderPipeline> pipelines = new EnumMap<>(PipelineVariant.class);
        pipelines.put(PipelineVariant.OPAQUE, build("opaque", true, true, true, gpu).build());
        pipelines.put(PipelineVariant.OPAQUE_DOUBLE_SIDED, build("opaque_double_sided", false, true, true, gpu).build());
        pipelines.put(PipelineVariant.TRANSLUCENT, build("translucent", true, true, false, gpu).build());
        pipelines.put(PipelineVariant.TRANSLUCENT_DOUBLE_SIDED, build("translucent_double_sided", false, true, false, gpu).build());
        pipelines.put(PipelineVariant.TOON, buildToon("toon", true, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_DOUBLE_SIDED, buildToon("toon_double_sided", false, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT, buildToon("toon_translucent", true, true, false, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT_DOUBLE_SIDED,
                buildToon("toon_translucent_double_sided", false, true, false, gpu));
        String prefix = gpu ? "mmd_gpu_" : "mmd_";
        pipelines.put(PipelineVariant.TOON_OUTLINE, build("toon_outline", true, true, false, gpu)
                .withShaderDefine("MMD_OUTLINE")
                .withLocation(id("pipeline/" + prefix + "toon_outline"))
                .build());
        pipelines.put(PipelineVariant.GLOWING, build("glowing", false, true, false, gpu)
                .withShaderDefine("MMD_EMISSIVE")
                .withLocation(id("pipeline/" + prefix + "glowing"))
                .build());
        return pipelines;
    }

    private static RenderPipeline.Builder build(String name, boolean cull, boolean blend,
                                                boolean depthWrite, boolean gpu) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/" + (gpu ? "mmd_gpu_" : "mmd_") + name))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(MMD_LAYOUT)
                .withBindGroupLayout(FOG_LAYOUT)
                .withCull(cull)
                // 26.2 reversed-Z：深度比较 GREATER_THAN_OR_EQUAL；
                // 不透明材质写深度，透明材质不写（避免透明面挡住后面模型）。
                .withDepthStencilState(depthWrite ? DEPTH_WRITE : DEPTH_NO_WRITE)
                .withColorTargetState(blend
                        ? new ColorTargetState(BlendFunction.TRANSLUCENT)
                        : ColorTargetState.DEFAULT)
                .withVertexBinding(0, gpu ? MMD_GPU_FORMAT : MMD_CPU_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (gpu) {
            builder.withShaderDefine("MMD_GPU_SKINNING")
                    .withBindGroupLayout(MMD_BONES_LAYOUT);
        }
        return builder;
    }

    private static RenderPipeline buildToon(String name, boolean cull, boolean blend,
                                            boolean depthWrite, boolean gpu) {
        return build(name, cull, blend, depthWrite, gpu)
                .withShaderDefine("MMD_TOON")
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmdskin", path);
    }
}
