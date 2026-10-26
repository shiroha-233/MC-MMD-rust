// 负责定义并验证 Minecraft 26.3 MMD RenderPipeline 变体。
package com.shiroha.mmdskin.client.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.Set;

/**
 * 26.3 的 MMD RenderPipeline 变体集合。
 *
 * <p><b>深度比较方向：MC 26.3 使用 reversed-Z</b>（近平面 = 1.0、远平面 = 0.0），
 * 所以深度测试必须用 {@code GREATER_THAN_OR_EQUAL}——vanilla 的所有深度管线
 * （{@code net.minecraft.client.renderer.RenderPipelines}）一律如此。
 *
 * <p>26.3 移植初期这里误用了 {@code LESS_THAN_OR_EQUAL}，方向正好相反：
 * 深度缓冲里离相机更近的方块（值更大）反而会让模型片元"通过"测试，
 * 于是 MMD 模型无视地形遮挡、直接盖在方块前面（用户现象：卡进方块里也能看见、
 * 透过方块能看见）。改回 {@code GREATER_THAN_OR_EQUAL} 即为正确方向。
 */
public final class MmdRenderPipelines {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * MMD 自有 uniform 块名。26.3 的 uniform 不再是单值 uniform，而是 std140 uniform block，
     * 成员顺序必须与 {@code MmdGpuDrawDevice} 写入的顺序完全一致。
     */
    public static final String MMD_UNIFORMS_BLOCK = "MmdUniforms";
    /** GPU 蒙皮骨骼调色板块名，固定 48 个 mat4。 */
    public static final String MMD_BONES_BLOCK = "MmdBones";
    /** GPU 蒙皮调色板最大骨骼数，与 native 侧保持一致。 */
    public static final int MAX_PALETTE_BONES = NativeRenderDataPort.MAX_PALETTE_BONES;

    private static final Identifier SHADER = id("core/mmd_entity");
    private static final Executor COMPILE_EXECUTOR = Runnable::run;

    private static final BindGroupLayout MMD_UNIFORMS_LAYOUT = BindGroupLayout.builder()
            .withUniform(MMD_UNIFORMS_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout MMD_BONES_LAYOUT = BindGroupLayout.builder()
            .withUniform(MMD_BONES_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();

    /**
     * 26.3 的管线只声明 uniform 块绑定：ProjMat 来自 Minecraft 的 Projection 块，
     * 光照方向来自 Lighting 块，雾参数来自 Fog 块。
     * 框架侧由 {@code RenderSystem.bindDefaultUniforms} 自动绑定 Projection/Fog/Globals/Lighting
     * （它不会碰 DynamicTransforms）。
     *
     * <p>这里**故意不声明** {@code BindGroupLayouts.DYNAMIC_TRANSFORMS}：
     * vanilla 的动态变换槽位是每帧预算，MMD 每帧多占一个槽位就会把 DynamicGpuData
     * 的 per-frame 容量打爆，触发 vanilla 在帧内重建 transform UBO 并在下一帧
     * 无 fence 等待地释放仍在飞行中的旧 buffer —— 原生崩溃。
     * 全局视图矩阵改为写进 MMD 自有的 {@code MmdUniforms} 块。
     */
    private static final RenderPipeline.Snippet BASE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(MMD_UNIFORMS_LAYOUT)
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .buildSnippet();

    private static final RenderPipeline.Snippet CPU_BASE = RenderPipeline.builder(BASE)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .buildSnippet();

    private static final RenderPipeline.Snippet GPU_BASE = RenderPipeline.builder(BASE)
            .withBindGroupLayout(MMD_BONES_LAYOUT)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            // GPU 蒙皮分支（骨骼调色板 + 顶点里的骨骼索引/权重）靠这个宏开启。
            // 1.21.5 的 GPU_BASE 里有这一行，26.3 移植时漏掉了 → shader 走不到蒙皮分支，
            // 模型会以绑定姿势渲染，完全不跟随骨骼动画。
            .withShaderDefine("MMD_GPU_SKINNING")
            .buildSnippet();

    private static final Map<PipelineVariant, RenderPipeline> CPU_PIPELINES = createPipelines(false);
    private static final Map<PipelineVariant, RenderPipeline> GPU_PIPELINES = createPipelines(true);
    /**
     * 当前可用的已编译管线快照。
     *
     * <p>用不可变快照 + volatile 原子替换，而不是可变 HashMap：这份表由资源重载线程写、
     * 由渲染线程每帧读，普通 HashMap 在并发写下可能让渲染线程读到半更新状态，
     * 甚至让 get() 陷入死循环。
     */
    private static volatile Map<RenderPipeline, CompiledRenderPipeline> compiledPipelines = Map.of();
    /**
     * MMD **自己**编译出来的管线（{@code device.compilePipeline} 的产物）。
     * 框架缓存里的管线不归我们管、不能 close；但自编译的这批在资源重载时没人会收走，
     * 必须显式 close，否则每次 F3+T 重载都泄漏一份已编译管线。
     */
    private static final Map<RenderPipeline, CompiledRenderPipeline> ownedPipelines = new ConcurrentHashMap<>();
    /** 记录已经报过"管线缺失"的键，避免每帧刷日志。 */
    private static final Set<String> loggedMissingPipelines = ConcurrentHashMap.newKeySet();
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

    /**
     * 取得绘制用的已编译管线。26.3 的 {@code RenderPass.setPipeline} 只接受
     * {@link CompiledRenderPipeline}，未编译成功时返回 {@code null}，调用方应跳过该次绘制。
     */
    public static CompiledRenderPipeline compiledPipeline(PipelineVariant variant, boolean gpuSkinning) {
        RenderPipeline pipeline = pipeline(variant, gpuSkinning);
        CompiledRenderPipeline compiled = compiledPipelines.get(pipeline);
        if (compiled == null) {
            compiled = resolveCompiled(pipeline);
        }
        return compiled;
    }

    /**
     * 管线不可用时记一条日志（每种组合只记一次）。
     * 之前这里是静默 return，表现为"MMD 模型偶尔/完全不出现且没有任何报错"。
     */
    public static void logMissingPipeline(PipelineVariant variant, boolean gpuSkinning) {
        String key = variant + "/" + gpuSkinning;
        if (loggedMissingPipelines.add(key)) {
            LOGGER.error("MMD 管线不可用，已跳过该次绘制: {}（着色器编译失败或管线缓存未就绪）", key);
        }
    }

    public static List<RenderPipeline> all() {
        java.util.ArrayList<RenderPipeline> pipelines = new java.util.ArrayList<>(CPU_PIPELINES.values());
        pipelines.addAll(GPU_PIPELINES.values());
        return List.copyOf(pipelines);
    }

    public static boolean validateAndActivate(GpuDevice device) {
        return validateAndActivate(device, (ShaderSource) null);
    }

    public static boolean validateAndActivate(GpuDevice device, ShaderSource shaderSource) {
        Map<RenderPipeline, CompiledRenderPipeline> compiled = new HashMap<>();
        boolean valid = shaderSource != null
                && compile(device, shaderSource, CPU_PIPELINES.values(), compiled);
        boolean validGpu = shaderSource != null
                && compile(device, shaderSource, GPU_PIPELINES.values(), compiled);
        Map<RenderPipeline, CompiledRenderPipeline> previousOwned = Map.copyOf(ownedPipelines);
        // 先原子发布新快照，再关旧管线：
        // 反过来的话，close 与 swap 之间渲染线程可能拿到已释放的管线（use-after-close）。
        compiledPipelines = Map.copyOf(compiled);
        ownedPipelines.clear();
        ownedPipelines.putAll(compiled);
        // 上一轮 MMD 自编译的管线要显式关掉，否则每次资源重载泄漏一份已编译管线。
        for (CompiledRenderPipeline previous : previousOwned.values()) {
            closeQuietly(previous);
        }
        ready = valid;
        gpuReady = validGpu;
        if (!valid) {
            LOGGER.error("MMD 管线编译失败，本次回退原版渲染（检查 mmdskin 着色器是否与 MC 26.3 的 UBO 规范匹配）");
        }
        return valid;
    }

    private static void closeQuietly(CompiledRenderPipeline pipeline) {
        try {
            pipeline.close();
        } catch (RuntimeException exception) {
            LOGGER.debug("关闭旧的 MMD 已编译管线失败", exception);
        }
    }

    /**
     * 兼容 1.21.5 的调用形态：只提供着色器正文的回调无法提供 include，
     * 因此该重载只能用于不依赖 include 的着色器，缺失 include 时管线会编译失败并回退原版渲染。
     */
    public static boolean validateAndActivate(
            GpuDevice device,
            BiFunction<Identifier, ShaderType, String> shaderSource) {
        return validateAndActivate(device,
                shaderSource == null ? null : new ShaderSourceAdapter(shaderSource));
    }

    /**
     * 基于资源管理器构建 26.3 的着色器源。加载器在资源重载回调里可以直接使用：
     * {@code MmdRenderPipelines.validateAndActivate(device, MmdRenderPipelines.shaderSource(resourceManager))}。
     */
    public static ShaderSource shaderSource(ResourceManager resourceManager) {
        // 与原版 GameRenderer.preloadUiShader 完全一致：先把所有 include 预读成
        // CachedIncludeSource，让 getInclude() 只剩一次 Map.get。
        //
        // 为什么必须这样：原生 shaderc 是通过 LWJGL 的 FFM upcall **回调进 Java** 来解析
        // #include 的，回调栈里若执行 invokedynamic（字符串拼接）、资源 IO 或原生分配，
        // 就可能在 upcall 帧内触发 StringConcatFactory 的首次 bootstrap —— HotSpot 25.0.1
        // 在这个场景下会写坏自己的内存，直接 EXCEPTION_ACCESS_VIOLATION 把游戏带走
        // （随机复现，与 mod 逻辑无关）。原版的 getInclude() 只有一次查表，从不崩。
        return new ResourceShaderSource(resourceManager, ShaderManager.listAllIncludes(resourceManager));
    }

    public static void activateRegisteredPipelines() {
        Map<RenderPipeline, CompiledRenderPipeline> registered = new HashMap<>();
        for (RenderPipeline pipeline : all()) {
            CompiledRenderPipeline compiled = resolveCompiled(pipeline);
            if (compiled != null) {
                registered.put(pipeline, compiled);
            }
        }
        // 这里的管线来自 Minecraft 自己的管线缓存，不归我们所有 —— 只换引用，不能 close。
        compiledPipelines = Map.copyOf(registered);
        if (registered.isEmpty()) {
            LOGGER.warn("MMD 管线缓存尚未就绪，本次不启用 MMD 渲染");
        }
        ready = !registered.isEmpty();
        gpuReady = !registered.isEmpty();
    }

    /**
     * 从 Minecraft 的管线缓存里取已编译管线。框架缓存尚未建立时返回 {@code null}，
     * 调用方应把该管线视为不可用并回退原版渲染。
     */
    private static CompiledRenderPipeline resolveCompiled(RenderPipeline pipeline) {
        try {
            return RenderSystem.getCompiledPipelineNullable(pipeline);
        } catch (RuntimeException exception) {
            LOGGER.debug("MMD 管线尚未进入 Minecraft 管线缓存: {}", pipeline.getLocation());
            return null;
        }
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

    private static boolean compile(GpuDevice device, ShaderSource shaderSource,
                                   Collection<RenderPipeline> pipelines,
                                   Map<RenderPipeline, CompiledRenderPipeline> compiled) {
        boolean valid = true;
        for (RenderPipeline pipeline : pipelines) {
            try {
                CompiledRenderPipeline result = device.compilePipeline(pipeline, shaderSource, COMPILE_EXECUTOR)
                        .join()
                        .finishCompile();
                if (result == null) {
                    valid = false;
                    continue;
                }
                compiled.put(pipeline, result);
            } catch (RuntimeException exception) {
                LOGGER.error("MMD 管线编译失败: {}", pipeline.getLocation(), exception);
                valid = false;
            }
        }
        return valid;
    }

    private static Map<PipelineVariant, RenderPipeline> createPipelines(boolean gpu) {
        EnumMap<PipelineVariant, RenderPipeline> pipelines = new EnumMap<>(PipelineVariant.class);
        pipelines.put(PipelineVariant.OPAQUE, build("opaque", true, true, true, gpu));
        pipelines.put(PipelineVariant.OPAQUE_DOUBLE_SIDED, build("opaque_double_sided", false, true, true, gpu));
        pipelines.put(PipelineVariant.TRANSLUCENT, build("translucent", true, true, false, gpu));
        pipelines.put(PipelineVariant.TRANSLUCENT_DOUBLE_SIDED, build("translucent_double_sided", false, true, false, gpu));
        pipelines.put(PipelineVariant.TOON, buildToon("toon", true, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_DOUBLE_SIDED, buildToon("toon_double_sided", false, true, true, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT, buildToon("toon_translucent", true, true, false, gpu));
        pipelines.put(PipelineVariant.TOON_TRANSLUCENT_DOUBLE_SIDED,
                buildToon("toon_translucent_double_sided", false, true, false, gpu));
        RenderPipeline.Snippet base = gpu ? GPU_BASE : CPU_BASE;
        String prefix = gpu ? "mmd_gpu_" : "mmd_";
        pipelines.put(PipelineVariant.TOON_OUTLINE, RenderPipeline.builder(base)
                .withLocation(id("pipeline/" + prefix + "toon_outline"))
                .withShaderDefine("MMD_OUTLINE")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(true)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build());
        pipelines.put(PipelineVariant.GLOWING, RenderPipeline.builder(base)
                .withLocation(id("pipeline/" + prefix + "glowing"))
                .withShaderDefine("MMD_EMISSIVE")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build());
        return pipelines;
    }

    private static RenderPipeline build(String name, boolean cull, boolean blend, boolean depthWrite, boolean gpu) {
        RenderPipeline.Builder builder = RenderPipeline.builder(gpu ? GPU_BASE : CPU_BASE)
                .withLocation(id("pipeline/" + (gpu ? "mmd_gpu_" : "mmd_") + name))
                .withCull(cull)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, depthWrite))
                .withColorTargetState(blend
                        ? new ColorTargetState(BlendFunction.TRANSLUCENT)
                        : ColorTargetState.DEFAULT);
        return builder.build();
    }

    private static RenderPipeline buildToon(String name, boolean cull, boolean blend, boolean depthWrite, boolean gpu) {
        RenderPipeline.Builder builder = RenderPipeline.builder(gpu ? GPU_BASE : CPU_BASE)
                .withLocation(id("pipeline/" + (gpu ? "mmd_gpu_" : "mmd_") + name))
                .withShaderDefine("MMD_TOON")
                .withCull(cull)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, depthWrite))
                .withColorTargetState(blend
                        ? new ColorTargetState(BlendFunction.TRANSLUCENT)
                        : ColorTargetState.DEFAULT);
        return builder.build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmdskin", path);
    }

    /**
     * 只提供着色器正文的适配器。26.3 的 include 由 {@link ShaderSource#getInclude} 单独提供，
     * 这里无法凭空构造，因此缺失 include 时让编译失败并回退原版渲染。
     */
    private static final class ShaderSourceAdapter implements ShaderSource {
        private final BiFunction<Identifier, ShaderType, String> shaderSource;

        private ShaderSourceAdapter(BiFunction<Identifier, ShaderType, String> shaderSource) {
            this.shaderSource = shaderSource;
        }

        @Override
        public String getShader(Identifier id, ShaderType type) {
            return shaderSource.apply(id, type);
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            // 原版语义：返回 null，GlslCompiler 会换成它自带的共享 error include。
            // 这里每次 miss 都 createError 会做一次原生分配（在 FFM upcall 里很危险），所以不这么做。
            return null;
        }

        @Override
        public void close() {
        }
    }

    /**
     * 从资源管理器读取 MMD 着色器与 include 的实现。
     */
    private static final class ResourceShaderSource implements ShaderSource {
        private final ResourceManager resourceManager;
        /**
         * 预读好的 include。key 就是 include id（{@code minecraft:light.glsl}），
         * 与原生 {@code GlslCompiler.processInclude} 传进来的 {@code Identifier.tryParse(文本)} 一致。
         */
        private final Map<Identifier, CachedIncludeSource> includes;

        private ResourceShaderSource(ResourceManager resourceManager,
                                     Map<Identifier, CachedIncludeSource> includes) {
            this.resourceManager = resourceManager;
            this.includes = includes;
        }

        @Override
        public String getShader(Identifier id, ShaderType type) {
            Identifier file = type.idConverter().idToFile(id);
            Resource resource = lastResource(file);
            if (resource == null) {
                // 原版这里是返回 null（PipelineBuilder 会打 "Couldn't find source for ..."），
                // 抛异常反而会让整条管线编译链炸掉。
                LOGGER.error("缺少 MMD 着色器资源: {}", file);
                return null;
            }
            try {
                return resource.readAllAsString();
            } catch (IOException exception) {
                LOGGER.error("读取 MMD 着色器失败: {}", file, exception);
                return null;
            }
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            // ★ 这是原生 shaderc 的回调入口：只允许一次 Map.get。
            // 绝对不能在这里做字符串拼接（invokedynamic）、资源 IO 或原生分配 ——
            // 那会让 HotSpot 在 FFM upcall 帧内首次链接 invokedynamic，直接崩掉整个 JVM。
            // 未命中返回 null 是官方支持的（GlslCompiler 会换成它自带的共享 error include）。
            try {
                return includes.get(id);
            } catch (RuntimeException | Error exception) {
                LOGGER.error("解析着色器 include 失败: {}", id, exception);
                return null;
            }
        }

        @Override
        public void close() {
            for (CachedIncludeSource include : includes.values()) {
                include.close();
            }
            includes.clear();
        }

        private Resource lastResource(Identifier file) {
            List<Resource> stack = resourceManager.getResourceStack(file);
            return stack.isEmpty() ? null : stack.get(stack.size() - 1);
        }
    }
}
