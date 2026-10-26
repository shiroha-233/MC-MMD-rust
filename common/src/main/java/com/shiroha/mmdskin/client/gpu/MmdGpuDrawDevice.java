// 负责把帧绘制请求批量准备并提交到 Minecraft 26.3 RenderPass。
package com.shiroha.mmdskin.client.gpu;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.shiroha.mmdskin.bridge.render.NativeDrawCommand;
import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.client.draw.MmdDrawDevice;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.metrics.ModelGpuMetricsPort;
import com.shiroha.mmdskin.client.texture.MinecraftTextureResource;
import com.shiroha.mmdskin.client.texture.TextureLease;
import com.shiroha.mmdskin.client.texture.TextureRepository;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MmdGpuDrawDevice implements MmdDrawDevice, ModelGpuMetricsPort, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Comparator<PreparedDraw> DRAW_ORDER = Comparator
            .comparing(PreparedDraw::transparent)
            .thenComparing((PreparedDraw draw) -> draw.transparent() ? -draw.distanceSquared() : 0.0D)
            .thenComparingLong(PreparedDraw::sequence);
    /**
     * MMD 自有 uniform 块（std140）字节数。成员顺序必须与 MmdUniforms uniform block 一致：
     * mat4 MmdModelMat / mat4 MmdViewMat / vec4 MmdMaterialColor / vec4 MmdColorModulator /
     * float MmdAlpha / float MmdLightFactor / float MmdOutlineWidth / float MmdEmission /
     * int MmdToonLevels / float MmdRimPower / float MmdRimIntensity /
     * float MmdSpecularPower / float MmdSpecularIntensity /
     * vec3 MmdOutlineColor / vec3 MmdToonShadowColor。
     *
     * <p>std140 自然对齐后的成员偏移为 0/64/128/144/160/164/168/172/176/180/184/188/192/208/224，
     * 块大小 240 字节。
     */
    private static final int UNIFORM_BLOCK_BYTES = 240;
    /** 单个 draw command 的骨骼调色板槽位大小：48 个 mat4。 */
    private static final int BONE_SLOT_BYTES = MmdRenderPipelines.MAX_PALETTE_BONES * 16 * Float.BYTES;

    /**
     * UBO 槽位的**步长**（不是内容大小）。内容固定 240 字节，但槽位偏移必须按设备的
     * {@code minUniformBufferOffsetAlignment} 对齐——vanilla 的
     * {@code DynamicGpuDataStorageMapped} 就是这么取整的（内容 240、步长 256）。
     * 步长不对齐时第 2 个 draw 之后的偏移不是对齐倍数，同帧多个模型的材质参数会串台。
     */
    private int uniformStride;

    private final NativeRenderDataPort nativeData;
    private final TextureRepository textures;
    private final Map<Long, GpuMesh> meshes = new HashMap<>();
    private final Map<GpuTexture, GpuTextureView> textureViews = new IdentityHashMap<>();
    private GpuTexture whiteTexture;
    private GpuSampler sampler;
    private GpuBuffer uniformBuffer;
    private ByteBuffer uniformStaging;
    private long uploadedVertexBytes;
    private long uploadCount;
    private boolean closed;

    public MmdGpuDrawDevice(NativeRenderDataPort nativeData, TextureRepository textures) {
        this.nativeData = java.util.Objects.requireNonNull(nativeData, "nativeData");
        this.textures = textures;
    }

    @Override
    public void draw(List<MmdDrawRequest> orderedRequests) {
        RenderSystem.assertOnRenderThread();
        if (closed || orderedRequests.isEmpty() || !MmdRenderPipelines.isReady()) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTexture fallback = whiteTexture(encoder);
        FrameUniforms uniforms = FrameUniforms.capture();
        List<PreparedDraw> draws = prepare(orderedRequests, encoder, fallback, uniforms);
        if (draws.isEmpty()) {
            return;
        }
        draws.sort(DRAW_ORDER);
        purgeTextureViews();

        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        GpuTextureView depth = target.hasDepth() ? target.getDepthTextureView() : null;
        if (color == null) {
            closeTextureLeases(draws);
            return;
        }

        // 26.3 的 uniform 全部通过 uniform buffer 绑定，必须在打开 Render Pass 之前写完。
        //
        // 全局视图矩阵（原 vanilla DynamicTransforms 块里的 ModelViewMat）直接写进我们自己的
        // MmdUniforms 块，不再走 RenderSystem.getDynamicUniforms().writeTransform(...)：
        // vanilla 的动态变换槽位是"每帧预算"，我们每帧多占一个槽位就会把 DynamicGpuData
        // 的 per-frame 容量打爆，vanilla 会在帧内重建 transform UBO 的 ring buffer，
        // 被换下来的旧 ring buffer 会在下一次 endFrame() 时被无 fence 等待地 close()，
        // 而引用它的 GPU 命令还在飞行中 —— 于是 Vulkan 层 use-after-free，进程原生崩溃
        // （没有 Java 崩溃报告）。
        Matrix4f viewMatrix = RenderSystem.getModelViewMatrixCopy();
        writeDrawUniforms(encoder, draws, uniforms, viewMatrix);

        // 与 vanilla 主 pass 完全一致地用 6 参重载建 pass：
        //   createRenderPass(label, colorView, Optional.empty(), depthView, OptionalDouble.empty())
        // 原版主 pass（LevelRenderer.lambda$addMainPass$0）就是这么建的，
        // 走 RenderPassDescriptor 构建器会与原版在深度附件的处理上产生差异，
        // 表现为 MMD 模型无视地形遮挡、直接盖在方块上。
        if (depth == null) {
            closeTextureLeases(draws);
            return;
        }
        try (RenderPass pass = encoder.createRenderPass(
                () -> "MMD entity pass",
                color,
                java.util.Optional.empty(),
                depth,
                java.util.OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            for (int index = 0; index < draws.size(); index++) {
                submit(pass, draws.get(index), index, fallback);
            }
        } finally {
            // 26.3 的 CommandEncoder 是"记录式"的：不调用 submit()，这一帧录进去的
            // 顶点/骨骼/uniform 上传和整个 RenderPass 都不会真正下发到 GPU。
            // 26.3 移植时漏了这一句 → MMD 模型完全不渲染。
            encoder.submit();
            closeTextureLeases(draws);
        }
    }

    private List<PreparedDraw> prepare(List<MmdDrawRequest> requests, CommandEncoder encoder,
                                       GpuTexture fallback, FrameUniforms uniforms) {
        ArrayList<PreparedDraw> draws = new ArrayList<>();
        long sequence = 0L;
        for (MmdDrawRequest request : requests) {
            try {
                NativeRenderDataPort.MeshDescription description =
                        nativeData.describeMesh(request.model().nativeHandle());
                if (description.indexCount() == 0 || description.vertexCount() == 0) {
                    continue;
                }
                GpuMesh mesh = meshes.computeIfAbsent(request.model().modelInstanceId(), ignored -> new GpuMesh());
                mesh.prepare(nativeData, encoder, request, description);
                Matrix4f modelMatrix = request.snapshot().transform().toMatrix()
                        .scale(request.model().renderScale());
                List<NativeDrawCommand> commands = mesh.commands;
                for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
                    NativeDrawCommand command = commands.get(commandIndex);
                    if (!command.visible() || command.indexCount() == 0) {
                        continue;
                    }
                    TextureLease textureLease = acquireTexture(request, command);
                    GpuTexture texture = texture(textureLease, fallback);
                    boolean transparent = requiresTransparentPipeline(request.transparent(), command);
                    double distanceSquared = commandDistanceSquared(modelMatrix, command);
                    boolean toon = isToon(request.pipeline());
                    draws.add(new PreparedDraw(request, modelMatrix, mesh, command, commandIndex,
                            selectMainPipeline(request.pipeline(), command, transparent),
                            texture, textureLease, transparent, distanceSquared, sequence++, false));
                    if (toon && uniforms.outlineEnabled()
                            && (command.flags() & NativeDrawCommand.FLAG_OUTLINE) != 0
                            && mesh.outlineIndexBuffer != null) {
                        draws.add(new PreparedDraw(request, modelMatrix, mesh, command, commandIndex,
                                PipelineVariant.TOON_OUTLINE,
                                texture, null, transparent, distanceSquared, sequence++, true));
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.error("MMD GPU 资源准备失败，模型回退将在下一帧生效: {}",
                        request.snapshot().modelKey(), exception);
            }
        }
        return draws;
    }

    private TextureLease acquireTexture(MmdDrawRequest request, NativeDrawCommand command) {
        if (textures == null || command.textureIndex() < 0) {
            return null;
        }
        return textures.acquire(request.model().nativeHandle(), command.textureIndex()).orElse(null);
    }

    private static GpuTexture texture(TextureLease lease, GpuTexture fallback) {
        if (lease == null || !(lease.resource() instanceof MinecraftTextureResource resource)) {
            return fallback;
        }
        return resource.gpuTexture();
    }

    static boolean requiresTransparentPipeline(boolean requestTransparent, NativeDrawCommand command) {
        return requestTransparent || (command.flags() & NativeDrawCommand.FLAG_TRANSPARENT) != 0;
    }

    /**
     * 把每个 draw 的 MmdUniforms uniform block 写进本帧共享的 uniform buffer。
     * 26.3 不再支持逐值的 uniform 写入，只能整块上传。
     */
    private void writeDrawUniforms(CommandEncoder encoder, List<PreparedDraw> draws,
                                    FrameUniforms uniforms, Matrix4f viewMatrix) {
        GpuBuffer buffer = ensureUniformBuffer(draws.size());
        float[] materialColor = new float[4];
        for (int index = 0; index < draws.size(); index++) {
            PreparedDraw draw = draws.get(index);
            NativeDrawCommand command = draw.command();
            materialColor(command.materialColor(), materialColor);
            uniformStaging.clear();
            Std140Builder builder = Std140Builder.intoBuffer(uniformStaging);
            builder.putMat4f(draw.modelMatrix())
                    .putMat4f(viewMatrix)
                    .putVec4(materialColor[0], materialColor[1], materialColor[2], materialColor[3])
                    .putVec4(1.0F, 1.0F, 1.0F, 1.0F)
                    .putFloat(command.alpha())
                    .putFloat(uniforms.lightingEnabled()
                            ? lightFactor(draw.request().snapshot().packedLight()) : 1.0F)
                    .putFloat(draw.outline() ? uniforms.outlineWidth() : 0.0F)
                    .putFloat(draw.pipeline() == PipelineVariant.GLOWING ? 1.0F : 0.0F)
                    .putInt(uniforms.toonLevels())
                    .putFloat(uniforms.rimPower())
                    .putFloat(uniforms.rimIntensity())
                    .putFloat(uniforms.specularPower())
                    .putFloat(uniforms.specularIntensity())
                    .putVec3(uniforms.outlineR(), uniforms.outlineG(), uniforms.outlineB())
                    .putVec3(uniforms.shadowR(), uniforms.shadowG(), uniforms.shadowB());
            // 写回 position 0，让 writeToBuffer 提交完整的 std140 块。
            // 注意：对齐填充字节（196..207 / 220..223）和尾部（236..239）不会被清零，
            // 但 GLSL std140 不读这些填充字节，成员偏移已逐项核对过。
            int stride = uniformStride();
            uniformStaging.position(0);
            uniformStaging.limit(stride);
            encoder.writeToBuffer(
                    buffer.slice((long) index * stride, stride), uniformStaging);
        }
    }

    /**
     * UBO 槽位步长：内容 240 字节，偏移按设备对齐值向上取整。
     */
    private int uniformStride() {
        int stride = uniformStride;
        if (stride == 0) {
            int alignment = RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();
            if (alignment <= 0) {
                alignment = 256;
            }
            stride = Math.max(UNIFORM_BLOCK_BYTES, Mth.roundToward(UNIFORM_BLOCK_BYTES, alignment));
            uniformStride = stride;
        }
        return stride;
    }

    private GpuBuffer ensureUniformBuffer(int slots) {
        int stride = uniformStride();
        long required = Math.multiplyExact((long) Math.max(slots, 1), stride);
        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < required) {
            long previous = uniformBuffer == null || uniformBuffer.isClosed() ? 0L : uniformBuffer.size();
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            long capacity = Math.max(required, previous * 2L);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MMD uniform blocks",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    capacity);
        }
        if (uniformStaging == null || uniformStaging.capacity() < stride) {
            uniformStaging = ByteBuffer.allocateDirect(stride).order(ByteOrder.LITTLE_ENDIAN);
        }
        return uniformBuffer;
    }

    private void submit(RenderPass pass, PreparedDraw draw, int slot, GpuTexture fallback) {
        NativeDrawCommand command = draw.command();
        GpuMesh mesh = draw.mesh();
        CompiledRenderPipeline pipeline = MmdRenderPipelines.compiledPipeline(draw.pipeline(), mesh.gpuSkinning);
        if (pipeline == null) {
            // 管线没编译成功时静默跳过 = "MMD 模型偶尔/完全不出现且没有任何报错"。
            MmdRenderPipelines.logMissingPipeline(draw.pipeline(), mesh.gpuSkinning);
            return;
        }
        pass.setPipeline(pipeline);
        int stride = uniformStride();
        pass.setUniform(MmdRenderPipelines.MMD_UNIFORMS_BLOCK,
                uniformBuffer.slice((long) slot * stride, stride));
        if (mesh.gpuSkinning && mesh.boneBuffer != null) {
            pass.setUniform(MmdRenderPipelines.MMD_BONES_BLOCK,
                    mesh.boneBuffer.slice((long) draw.commandIndex() * BONE_SLOT_BYTES, BONE_SLOT_BYTES));
        }
        GpuTexture texture = draw.texture() == null ? fallback : draw.texture();
        GpuTextureView view = textureView(texture);
        if (view != null) {
            pass.setUniform("Sampler0", view, sampler());
        }
        pass.setVertexBuffer(0, mesh.vertexBuffer.slice());
        pass.setIndexBuffer(draw.outline() ? mesh.outlineIndexBuffer : mesh.indexBuffer, mesh.indexType);
        pass.drawIndexed(command.indexCount(), 1, command.firstIndex(), 0, 0);
    }

    private static void materialColor(int packed, float[] out) {
        if (packed == 0) {
            out[0] = 1.0F;
            out[1] = 1.0F;
            out[2] = 1.0F;
            out[3] = 1.0F;
            return;
        }
        out[0] = (packed & 0xFF) / 255.0F;
        out[1] = (packed >>> 8 & 0xFF) / 255.0F;
        out[2] = (packed >>> 16 & 0xFF) / 255.0F;
        out[3] = (packed >>> 24 & 0xFF) / 255.0F;
    }

    private static float lightFactor(int packedLight) {
        int block = packedLight & 0xFFFF;
        int sky = packedLight >>> 16 & 0xFFFF;
        return Math.clamp(0.2F + Math.max(block, sky) / 240.0F * 0.8F, 0.2F, 1.0F);
    }

    private static double commandDistanceSquared(Matrix4f modelMatrix, NativeDrawCommand command) {
        Vector3f center = modelMatrix.transformPosition(
                new Vector3f(command.centerX(), command.centerY(), command.centerZ()));
        return (double) center.x * center.x + (double) center.y * center.y + (double) center.z * center.z;
    }

    private static PipelineVariant selectMainPipeline(PipelineVariant requested,
                                                       NativeDrawCommand command,
                                                       boolean transparent) {
        if (requested == PipelineVariant.GLOWING) {
            return PipelineVariant.GLOWING;
        }
        boolean doubleSided = (command.flags() & NativeDrawCommand.FLAG_DOUBLE_SIDED) != 0;
        if (isToon(requested)) {
            if (transparent) {
                return doubleSided ? PipelineVariant.TOON_TRANSLUCENT_DOUBLE_SIDED
                        : PipelineVariant.TOON_TRANSLUCENT;
            }
            return doubleSided ? PipelineVariant.TOON_DOUBLE_SIDED : PipelineVariant.TOON;
        }
        if (transparent) {
            return doubleSided ? PipelineVariant.TRANSLUCENT_DOUBLE_SIDED : PipelineVariant.TRANSLUCENT;
        }
        return doubleSided ? PipelineVariant.OPAQUE_DOUBLE_SIDED : PipelineVariant.OPAQUE;
    }

    private static boolean isToon(PipelineVariant variant) {
        return variant == PipelineVariant.TOON
                || variant == PipelineVariant.TOON_DOUBLE_SIDED
                || variant == PipelineVariant.TOON_TRANSLUCENT
                || variant == PipelineVariant.TOON_TRANSLUCENT_DOUBLE_SIDED;
    }

    private GpuTexture whiteTexture(CommandEncoder encoder) {
        if (whiteTexture != null && !whiteTexture.isClosed()) {
            return whiteTexture;
        }
        whiteTexture = RenderSystem.getDevice().createTexture(
                () -> "MMD fallback white texture",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        try (NativeImage image = new NativeImage(1, 1, false)) {
            image.setPixel(0, 0, 0xFFFFFFFF);
            encoder.writeToTexture(whiteTexture, image);
        }
        return whiteTexture;
    }

    private GpuSampler sampler() {
        if (sampler == null || sampler.isClosed()) {
            sampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
        return sampler;
    }

    /**
     * 26.3 的采样需要 GpuTextureView，纹理本身不再携带过滤设置，这里按纹理缓存视图。
     */
    private GpuTextureView textureView(GpuTexture texture) {
        if (texture == null || texture.isClosed()) {
            return null;
        }
        GpuTextureView view = textureViews.get(texture);
        if (view == null || view.isClosed()) {
            view = RenderSystem.getDevice().createTextureView(texture);
            textureViews.put(texture, view);
        }
        return view;
    }

    private void purgeTextureViews() {
        textureViews.entrySet().removeIf(entry -> {
            if (entry.getKey().isClosed()) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    private static void closeTextureLeases(List<PreparedDraw> draws) {
        for (PreparedDraw draw : draws) {
            if (draw.textureLease() != null) {
                draw.textureLease().close();
            }
        }
    }

    public void releaseModel(long modelInstanceId) {
        RenderSystem.assertOnRenderThread();
        GpuMesh mesh = meshes.remove(modelInstanceId);
        if (mesh != null) {
            mesh.close();
        }
    }

    @Override
    public long estimatedModelGpuBytes() {
        long bytes = 0L;
        for (GpuMesh mesh : meshes.values()) {
            bytes = Math.addExact(bytes, mesh.estimatedGpuBytes());
        }
        return bytes;
    }

    @Override
    public long uploadedVertexBytes() {
        return uploadedVertexBytes;
    }

    @Override
    public long uploadCount() {
        return uploadCount;
    }

    private void recordVertexUpload(long bytes) {
        uploadedVertexBytes = Math.addExact(uploadedVertexBytes, bytes);
        uploadCount = Math.addExact(uploadCount, 1L);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        meshes.values().forEach(GpuMesh::close);
        meshes.clear();
        for (GpuTextureView view : textureViews.values()) {
            view.close();
        }
        textureViews.clear();
        if (whiteTexture != null) {
            whiteTexture.close();
            whiteTexture = null;
        }
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        uniformStaging = null;
    }

    private final class GpuMesh implements AutoCloseable {
        private GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private GpuBuffer outlineIndexBuffer;
        private GpuBuffer boneBuffer;
        private IndexType indexType;
        private ByteBuffer vertexStaging;
        private ByteBuffer commandStaging;
        private ByteBuffer paletteStaging;
        private ByteBuffer boneStaging;
        private float[][] paletteMatrices = new float[0][];
        private List<NativeDrawCommand> commands = List.of();
        private int vertexBytes;
        private int indexBytes;
        private int indexCount;
        private int drawCount;
        private long uploadedRevision = -1L;
        private long commandRevision = -1L;
        private long paletteRevision = -1L;
        private boolean gpuSkinning;
        private int vertexStride;
        private int paletteEntryCount;

        private void prepare(NativeRenderDataPort nativeData, CommandEncoder encoder,
                             MmdDrawRequest request, NativeRenderDataPort.MeshDescription description) {
            if (!matches(description)) {
                rebuildTopology(nativeData, encoder, request.model().nativeHandle(), description);
            }
            if (uploadedRevision != description.vertexRevision()) {
                int vertexBytes = description.vertexBufferBytes();
                vertexStaging.clear().limit(vertexBytes);
                requireWritten("copyFrameVertices",
                        nativeData.copyFrameVertices(request.model().nativeHandle(), vertexStaging),
                        vertexBytes);
                // 26.3 的 writeToBuffer 提交的是 ByteBuffer.remaining() 字节，不是目标 slice 的长度。
                // native 写完之后 position 停在数据末尾（remaining=0），
                // 不拨回 0 就等于"什么都没传" → 顶点缓冲全是 0 → 所有顶点塌在原点 → 模型不可见。
                vertexStaging.position(0);
                vertexStaging.limit(vertexBytes);
                encoder.writeToBuffer(vertexBuffer.slice(), vertexStaging);
                recordVertexUpload(vertexBytes);
                uploadedRevision = description.vertexRevision();
            }
            if (commandRevision != description.materialRevision()
                    || commands.size() != description.drawCommandCount()) {
                int commandBytes = Math.multiplyExact(description.drawCommandCount(), NativeDrawCommand.BYTES);
                commandStaging.clear().limit(commandBytes);
                requireWritten("copyDrawCommands",
                        nativeData.copyDrawCommands(request.model().nativeHandle(), commandStaging), commandBytes);
                ArrayList<NativeDrawCommand> decoded = new ArrayList<>(description.drawCommandCount());
                for (int index = 0; index < description.drawCommandCount(); index++) {
                    decoded.add(NativeDrawCommand.read(commandStaging, index));
                }
                commands = List.copyOf(decoded);
                commandRevision = description.materialRevision();
                // draw command 的 palette 区间可能随材质变化，必须重新写骨骼槽位。
                paletteRevision = -1L;
            }
            if (description.gpuSkinningActive() && paletteRevision != description.poseRevision()) {
                paletteStaging.clear().limit(description.paletteBufferBytes());
                requireWritten("copyBonePaletteMatrices",
                        nativeData.copyBonePaletteMatrices(request.model().nativeHandle(), paletteStaging),
                        description.paletteBufferBytes());
                for (int entry = 0; entry < paletteMatrices.length; entry++) {
                    float[] matrix = paletteMatrices[entry];
                    int byteOffset = entry * 16 * Float.BYTES;
                    for (int component = 0; component < 16; component++) {
                        matrix[component] = paletteStaging.getFloat(byteOffset + component * Float.BYTES);
                    }
                }
                paletteRevision = description.poseRevision();
                uploadBonePalette(encoder);
            }
        }

        /**
         * 26.3 的骨骼调色板必须放进 uniform buffer，且着色器侧的 MmdBones 块固定 48 个 mat4，
         * 因此每个 draw command 独占一个 48 槽位的区间。
         */
        private void uploadBonePalette(CommandEncoder encoder) {
            if (boneBuffer == null || commands.isEmpty()) {
                return;
            }
            for (int index = 0; index < commands.size(); index++) {
                NativeDrawCommand command = commands.get(index);
                int offset = command.paletteOffset();
                int count = command.paletteCount();
                if (count <= 0 || offset < 0 || offset + count > paletteMatrices.length) {
                    continue;
                }
                boneStaging.clear();
                for (int entry = 0; entry < count; entry++) {
                    float[] matrix = paletteMatrices[offset + entry];
                    for (int component = 0; component < 16; component++) {
                        boneStaging.putFloat(matrix[component]);
                    }
                }
                // 26.3 的 writeToBuffer 提交的是 ByteBuffer.remaining() 字节，
                // 不是目标 slice 的长度。写完必须把 position 拨回 0、limit 收到
                // 本次实际写入的字节数，否则提交的是「写入点之后的剩余部分」
                // （真正的矩阵一个字节都传不过去，MmdBones 全零 → 模型坍缩成一点）。
                int payloadBytes = count * 16 * Float.BYTES;
                boneStaging.position(0);
                boneStaging.limit(payloadBytes);
                encoder.writeToBuffer(
                        boneBuffer.slice((long) index * BONE_SLOT_BYTES, payloadBytes),
                        boneStaging);
                // 复位 limit，下一轮 clear() 依赖它恢复到完整容量。
                boneStaging.limit(boneStaging.capacity());
            }
        }

        private boolean matches(NativeRenderDataPort.MeshDescription description) {
            return vertexBuffer != null && indexBuffer != null
                    && vertexBytes == description.vertexBufferBytes()
                    && indexBytes == description.indexBufferBytes()
                    && indexCount == description.indexCount()
                    && drawCount == description.drawCommandCount()
                    && gpuSkinning == description.gpuSkinningActive()
                    && vertexStride == description.vertexStride()
                    && paletteEntryCount == description.paletteEntryCount()
                    && indexType == toGpuIndexType(description.indexType());
        }

        private void rebuildTopology(NativeRenderDataPort nativeData, CommandEncoder encoder,
                                     long modelHandle, NativeRenderDataPort.MeshDescription description) {
            closeBuffers();
            vertexBytes = description.vertexBufferBytes();
            indexBytes = description.indexBufferBytes();
            indexCount = description.indexCount();
            drawCount = description.drawCommandCount();
            gpuSkinning = description.gpuSkinningActive();
            vertexStride = description.vertexStride();
            paletteEntryCount = description.paletteEntryCount();
            indexType = toGpuIndexType(description.indexType());
            ByteBuffer indices = direct(description.indexBufferBytes());
            requireWritten("copyIndices", nativeData.copyIndices(modelHandle, indices),
                    description.indexBufferBytes());
            indices.clear().limit(description.indexBufferBytes());
            indexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MMD static indices " + modelHandle,
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indices);
            ByteBuffer outlineIndices = OutlineIndexBuilder.reverseTriangles(
                    indices, description.indexCount(), description.indexType());
            outlineIndexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MMD outline indices " + modelHandle,
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, outlineIndices);
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "MMD dynamic vertices " + modelHandle,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, description.vertexBufferBytes());
            vertexStaging = direct(description.vertexBufferBytes());
            commandStaging = direct(Math.multiplyExact(description.drawCommandCount(), NativeDrawCommand.BYTES));
            paletteStaging = direct(description.paletteBufferBytes());
            paletteMatrices = new float[description.paletteEntryCount()][16];
            if (gpuSkinning) {
                boneBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "MMD bone palette " + modelHandle,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        Math.multiplyExact((long) Math.max(drawCount, 1), BONE_SLOT_BYTES));
                boneStaging = direct(BONE_SLOT_BYTES);
            }
            uploadedRevision = -1L;
            commandRevision = -1L;
            paletteRevision = -1L;
            commands = List.of();
        }

        private static ByteBuffer direct(int bytes) {
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        private static IndexType toGpuIndexType(NativeRenderDataPort.IndexType type) {
            return type == NativeRenderDataPort.IndexType.SHORT
                    ? IndexType.SHORT : IndexType.INT;
        }

        private static void requireWritten(String operation, int actual, int expected) {
            if (actual != expected) {
                throw new IllegalStateException(operation + " wrote " + actual + " bytes, expected " + expected);
            }
        }

        private void closeBuffers() {
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
            if (indexBuffer != null) {
                indexBuffer.close();
                indexBuffer = null;
            }
            if (outlineIndexBuffer != null) {
                outlineIndexBuffer.close();
                outlineIndexBuffer = null;
            }
            if (boneBuffer != null) {
                boneBuffer.close();
                boneBuffer = null;
            }
        }

        private long estimatedGpuBytes() {
            long bytes = vertexBuffer == null ? 0L : vertexBytes;
            if (indexBuffer != null) {
                bytes = Math.addExact(bytes, indexBytes);
            }
            if (outlineIndexBuffer != null) {
                bytes = Math.addExact(bytes, indexBytes);
            }
            if (boneBuffer != null) {
                bytes = Math.addExact(bytes, Math.multiplyExact((long) drawCount, BONE_SLOT_BYTES));
            }
            return bytes;
        }

        @Override
        public void close() {
            closeBuffers();
            vertexStaging = null;
            commandStaging = null;
            paletteStaging = null;
            boneStaging = null;
            paletteMatrices = new float[0][];
            commands = List.of();
        }
    }

    private record PreparedDraw(
            MmdDrawRequest request,
            Matrix4f modelMatrix,
            GpuMesh mesh,
            NativeDrawCommand command,
            int commandIndex,
            PipelineVariant pipeline,
            GpuTexture texture,
            TextureLease textureLease,
            boolean transparent,
            double distanceSquared,
            long sequence,
            boolean outline) {
    }

    private record FrameUniforms(
            boolean lightingEnabled,
            boolean outlineEnabled,
            int toonLevels,
            float outlineWidth,
            float outlineR,
            float outlineG,
            float outlineB,
            float shadowR,
            float shadowG,
            float shadowB,
            float rimPower,
            float rimIntensity,
            float specularPower,
            float specularIntensity) {

        private static FrameUniforms capture() {
            return new FrameUniforms(
                    ConfigManager.isLightingEnabled(),
                    ConfigManager.isToonOutlineEnabled(),
                    ConfigManager.getToonLevels(),
                    ConfigManager.getToonOutlineWidth(),
                    ConfigManager.getToonOutlineR(),
                    ConfigManager.getToonOutlineG(),
                    ConfigManager.getToonOutlineB(),
                    ConfigManager.getToonShadowR(),
                    ConfigManager.getToonShadowG(),
                    ConfigManager.getToonShadowB(),
                    ConfigManager.getToonRimPower(),
                    ConfigManager.getToonRimIntensity(),
                    ConfigManager.getToonSpecularPower(),
                    ConfigManager.getToonSpecularIntensity());
        }
    }
}
