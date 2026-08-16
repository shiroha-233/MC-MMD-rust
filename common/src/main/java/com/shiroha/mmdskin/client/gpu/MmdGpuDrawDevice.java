// 负责把帧绘制请求批量准备并提交到 Minecraft 26.2 RenderPass。
// 26.2 适配：uniform 全部通过 std140 UBO 块 + transient memory 上传；
// 采样器与纹理视图由 GpuTextureView/GpuSampler 提供；深度方向已反转。
package com.shiroha.mmdskin.client.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shiroha.mmdskin.bridge.render.NativeDrawCommand;import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.client.draw.MmdDrawDevice;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.metrics.ModelGpuMetricsPort;
import com.shiroha.mmdskin.client.render.state.CameraMatrices;
import com.shiroha.mmdskin.client.texture.MinecraftTextureResource;
import com.shiroha.mmdskin.client.texture.TextureLease;
import com.shiroha.mmdskin.client.texture.TextureRepository;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class MmdGpuDrawDevice implements MmdDrawDevice, ModelGpuMetricsPort, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Comparator<PreparedDraw> DRAW_ORDER = Comparator
            .comparing(PreparedDraw::transparent)
            .thenComparing((PreparedDraw draw) -> draw.transparent() ? -draw.distanceSquared() : 0.0D)
            .thenComparingLong(PreparedDraw::sequence);
    // 26.2 的 uniform offset 对齐取决于 GPU；Minecraft 的 RenderPass 要求至少 256 字节。
    private static final int UBO_ALIGNMENT = 256;

    // std140 布局常量（与 mmd_entity.vsh/fsh 的 MmdTransforms 块一致）
    private static final int TRANSFORMS_SIZE = 128;

    // std140 布局常量（与 mmd_entity.vsh/fsh 的 MmdModel 块一致）
    private static final int MODEL_SIZE = 176;
    private static final int MODEL_OFFSET_MAT = 0;
    private static final int MODEL_OFFSET_LIGHT0 = 64;
    private static final int MODEL_OFFSET_LIGHT1 = 80;
    private static final int MODEL_OFFSET_OUTLINE_WIDTH = 92;
    private static final int MODEL_OFFSET_MATERIAL_COLOR = 96;
    private static final int MODEL_OFFSET_ALPHA = 112;
    private static final int MODEL_OFFSET_LIGHT_FACTOR = 116;
    private static final int MODEL_OFFSET_EMISSION = 120;
    private static final int MODEL_OFFSET_OUTLINE_COLOR = 128;
    private static final int MODEL_OFFSET_TOON_LEVELS = 140;
    private static final int MODEL_OFFSET_TOON_SHADOW = 144;
    private static final int MODEL_OFFSET_RIM_POWER = 156;
    private static final int MODEL_OFFSET_RIM_INTENSITY = 160;
    private static final int MODEL_OFFSET_SPECULAR_POWER = 164;
    private static final int MODEL_OFFSET_SPECULAR_INTENSITY = 168;

    // std140 布局：mat4 MmdBonesArray[48]
    private static final int BONES_ENTRY_SIZE = 64;
    private static final int BONES_SIZE = NativeRenderDataPort.MAX_PALETTE_BONES * BONES_ENTRY_SIZE;

    private static final Vector3f LIGHT0_DIRECTION = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT1_DIRECTION = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    private final NativeRenderDataPort nativeData;
    private final TextureRepository textures;
    private final Map<Long, GpuMesh> meshes = new HashMap<>();
    private final Map<GpuTexture, GpuTextureView> textureViews = new HashMap<>();
    private GpuTexture whiteTexture;
    private GpuTextureView whiteTextureView;
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

        // 26.2 物品栏纸娃娃/PIP 走离屏渲染：PictureInPictureRenderer.prepare 期间
        // RenderSystem.outputColorTextureOverride/outputDepthTextureOverride 指向 PIP 离屏
        // 纹理；此时应画到离屏 target，否则画到主 target 会被 GUI 覆盖/错位。
        GpuTextureView colorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView depthOverride = RenderSystem.outputDepthTextureOverride;
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTextureView colorView = colorOverride != null ? colorOverride : target.getColorTextureView();
        GpuTextureView depthView = (depthOverride != null
                ? depthOverride
                : (target.useDepth ? target.getDepthTextureView() : null));
        if (colorView == null || (depthOverride == null && target.useDepth && depthView == null)) {
            closeTextureLeases(draws);
            return;
        }

        CameraMatrices camera = CameraMatrices.capture();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(
                () -> "MMD model pass",
                colorView,
                Optional.empty(),
                depthView,
                OptionalDouble.empty())) {
            GpuBufferSlice fog = RenderSystem.getShaderFog();
            if (fog != null) {
                pass.setUniform("Fog", fog);
            }
            for (PreparedDraw draw : draws) {
                submit(pass, encoder, draw, fallback, sampler, uniforms, camera);
            }
        } finally {
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
                for (NativeDrawCommand command : mesh.commands) {
                    if (!command.visible() || command.indexCount() == 0) {
                        continue;
                    }
                    TextureLease textureLease = acquireTexture(request, command);
                    GpuTexture texture = texture(textureLease, fallback);
                    boolean transparent = requiresTransparentPipeline(request.transparent(), command);
                    double distanceSquared = commandDistanceSquared(modelMatrix, command);
                    boolean toon = isToon(request.pipeline());
                    draws.add(new PreparedDraw(request, modelMatrix, mesh, command,
                            selectMainPipeline(request.pipeline(), command, transparent),
                            texture, textureLease, transparent, distanceSquared, sequence++, false));
                    if (toon && uniforms.outlineEnabled()
                            && (command.flags() & NativeDrawCommand.FLAG_OUTLINE) != 0
                            && mesh.outlineIndexBuffer != null) {
                        draws.add(new PreparedDraw(request, modelMatrix, mesh, command, PipelineVariant.TOON_OUTLINE,
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

    private void submit(RenderPass pass, CommandEncoder encoder, PreparedDraw draw,
                        GpuTexture fallback, GpuSampler sampler, FrameUniforms uniforms,
                        CameraMatrices camera) {
        NativeDrawCommand command = draw.command();
        GpuMesh mesh = draw.mesh();
        pass.setPipeline(MmdRenderPipelines.pipeline(draw.pipeline(), mesh.gpuSkinning));

        GpuBufferSlice transforms = encoder.transientMemory().uploadGpu(
                writeTransforms(camera), UBO_ALIGNMENT, GpuBuffer.USAGE_UNIFORM);
        pass.setUniform("MmdTransforms", transforms);
        GpuBufferSlice model = encoder.transientMemory().uploadGpu(
                writeModel(draw, command, uniforms), UBO_ALIGNMENT, GpuBuffer.USAGE_UNIFORM);
        pass.setUniform("MmdModel", model);
        if (mesh.gpuSkinning) {
            int offset = command.paletteOffset();
            int count = command.paletteCount();
            if (offset > mesh.paletteMatrices.length - count) {
                throw new IllegalStateException("bone palette range exceeds copied matrices");
            }
            GpuBufferSlice bones = encoder.transientMemory().uploadGpu(
                    writeBones(mesh.paletteMatrices, offset, count), UBO_ALIGNMENT, GpuBuffer.USAGE_UNIFORM);
            pass.setUniform("MmdBones", bones);
        }

        GpuTexture texture = draw.texture() == null ? fallback : draw.texture();
        pass.bindTexture("Sampler0", textureView(texture), sampler);
        pass.setVertexBuffer(0, new GpuBufferSlice(mesh.vertexBuffer, 0L, mesh.vertexBuffer.size()));
        pass.setIndexBuffer(draw.outline() ? mesh.outlineIndexBuffer : mesh.indexBuffer, mesh.indexType);
        // 26.2 参数顺序: indexCount, instanceCount, firstIndex, vertexOffset, firstInstance
        pass.drawIndexed(command.indexCount(), 1, command.firstIndex(), 0, 0);
    }

    private GpuTextureView textureView(GpuTexture texture) {
        return textureViews.computeIfAbsent(texture,
                t -> RenderSystem.getDevice().createTextureView(t));
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer writeTransforms(CameraMatrices camera) {
        ByteBuffer buffer = direct(TRANSFORMS_SIZE);
        camera.modelView().get(0, buffer);
        camera.projection().get(64, buffer);
        return buffer;
    }

    private static ByteBuffer writeModel(PreparedDraw draw, NativeDrawCommand command, FrameUniforms uniforms) {
        ByteBuffer buffer = direct(MODEL_SIZE);
        draw.modelMatrix().get(MODEL_OFFSET_MAT, buffer);
        buffer.putFloat(MODEL_OFFSET_LIGHT0, LIGHT0_DIRECTION.x);
        buffer.putFloat(MODEL_OFFSET_LIGHT0 + 4, LIGHT0_DIRECTION.y);
        buffer.putFloat(MODEL_OFFSET_LIGHT0 + 8, LIGHT0_DIRECTION.z);
        buffer.putFloat(MODEL_OFFSET_LIGHT1, LIGHT1_DIRECTION.x);
        buffer.putFloat(MODEL_OFFSET_LIGHT1 + 4, LIGHT1_DIRECTION.y);
        buffer.putFloat(MODEL_OFFSET_LIGHT1 + 8, LIGHT1_DIRECTION.z);
        buffer.putFloat(MODEL_OFFSET_OUTLINE_WIDTH, draw.outline() ? uniforms.outlineWidth() : 0.0F);
        writeMaterialColor(buffer, command.materialColor());
        buffer.putFloat(MODEL_OFFSET_ALPHA, command.alpha());
        buffer.putFloat(MODEL_OFFSET_LIGHT_FACTOR, uniforms.lightingEnabled()
                ? lightFactor(draw.request().snapshot().packedLight()) : 1.0F);
        buffer.putFloat(MODEL_OFFSET_EMISSION, draw.pipeline() == PipelineVariant.GLOWING ? 1.0F : 0.0F);
        buffer.putFloat(MODEL_OFFSET_OUTLINE_COLOR, uniforms.outlineR());
        buffer.putFloat(MODEL_OFFSET_OUTLINE_COLOR + 4, uniforms.outlineG());
        buffer.putFloat(MODEL_OFFSET_OUTLINE_COLOR + 8, uniforms.outlineB());
        buffer.putInt(MODEL_OFFSET_TOON_LEVELS, uniforms.toonLevels());
        buffer.putFloat(MODEL_OFFSET_TOON_SHADOW, uniforms.shadowR());
        buffer.putFloat(MODEL_OFFSET_TOON_SHADOW + 4, uniforms.shadowG());
        buffer.putFloat(MODEL_OFFSET_TOON_SHADOW + 8, uniforms.shadowB());
        buffer.putFloat(MODEL_OFFSET_RIM_POWER, uniforms.rimPower());
        buffer.putFloat(MODEL_OFFSET_RIM_INTENSITY, uniforms.rimIntensity());
        buffer.putFloat(MODEL_OFFSET_SPECULAR_POWER, uniforms.specularPower());
        buffer.putFloat(MODEL_OFFSET_SPECULAR_INTENSITY, uniforms.specularIntensity());
        return buffer;
    }

    private static ByteBuffer writeBones(float[][] paletteMatrices, int offset, int count) {
        ByteBuffer buffer = direct(BONES_SIZE);
        for (int index = 0; index < count; index++) {
            float[] matrix = paletteMatrices[offset + index];
            int byteOffset = index * BONES_ENTRY_SIZE;
            for (int component = 0; component < 16; component++) {
                buffer.putFloat(byteOffset + component * Float.BYTES, matrix[component]);
            }
        }
        return buffer;
    }

    private static void writeMaterialColor(ByteBuffer buffer, int packed) {
        if (packed == 0) {
            buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR, 1.0F);
            buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 4, 1.0F);
            buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 8, 1.0F);
            buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 12, 1.0F);
            return;
        }
        buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR, (packed & 0xFF) / 255.0F);
        buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 4, (packed >>> 8 & 0xFF) / 255.0F);
        buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 8, (packed >>> 16 & 0xFF) / 255.0F);
        buffer.putFloat(MODEL_OFFSET_MATERIAL_COLOR + 12, (packed >>> 24 & 0xFF) / 255.0F);
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
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        whiteTextureView = null;
        try (NativeImage image = new NativeImage(1, 1, false)) {
            image.setPixel(0, 0, 0xFFFFFFFF);
            encoder.writeToTexture(whiteTexture, image);
        }
        return whiteTexture;
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
            whiteTextureView = null;
        }
    }

    private final class GpuMesh implements AutoCloseable {
        private GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private GpuBuffer outlineIndexBuffer;
        private com.mojang.blaze3d.IndexType indexType;
        private ByteBuffer vertexStaging;
        private ByteBuffer commandStaging;
        private ByteBuffer paletteStaging;
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
                vertexStaging.clear().limit(description.vertexBufferBytes());
                requireWritten("copyFrameVertices",
                        nativeData.copyFrameVertices(request.model().nativeHandle(), vertexStaging),
                        description.vertexBufferBytes());
                encoder.writeToBuffer(bufferSlice(vertexBuffer), vertexStaging);
                recordVertexUpload(description.vertexBufferBytes());
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
            }
        }

        private static GpuBufferSlice bufferSlice(GpuBuffer buffer) {
            return new GpuBufferSlice(buffer, 0L, buffer.size());
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
            uploadedRevision = -1L;
            commandRevision = -1L;
            paletteRevision = -1L;
            commands = List.of();
        }

        private static ByteBuffer direct(int bytes) {
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        private static com.mojang.blaze3d.IndexType toGpuIndexType(NativeRenderDataPort.IndexType type) {
            return type == NativeRenderDataPort.IndexType.SHORT
                    ? com.mojang.blaze3d.IndexType.SHORT : com.mojang.blaze3d.IndexType.INT;
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
        }

        private long estimatedGpuBytes() {
            long bytes = vertexBuffer == null ? 0L : vertexBytes;
            if (indexBuffer != null) {
                bytes = Math.addExact(bytes, indexBytes);
            }
            if (outlineIndexBuffer != null) {
                bytes = Math.addExact(bytes, indexBytes);
            }
            return bytes;
        }

        @Override
        public void close() {
            closeBuffers();
            vertexStaging = null;
            commandStaging = null;
            paletteStaging = null;
            paletteMatrices = new float[0][];
            commands = List.of();
        }
    }

    private record PreparedDraw(
            MmdDrawRequest request,
            Matrix4f modelMatrix,
            GpuMesh mesh,
            NativeDrawCommand command,
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
