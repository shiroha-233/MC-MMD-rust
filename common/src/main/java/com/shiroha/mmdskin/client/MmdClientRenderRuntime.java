// 负责组合模型、动画、帧更新和绘制队列 Module 的客户端运行时。
package com.shiroha.mmdskin.client;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.bridge.NativeRenderDataPorts;
import com.shiroha.mmdskin.bridge.render.NativeRenderDataPort;
import com.shiroha.mmdskin.bridge.texture.NativeTextureBridgeAdapter;
import com.shiroha.mmdskin.client.animation.AnimationRepository;
import com.shiroha.mmdskin.client.animation.ModelAnimationController;
import com.shiroha.mmdskin.client.draw.FrameRenderQueue;
import com.shiroha.mmdskin.client.frame.MmdFrameUpdater;
import com.shiroha.mmdskin.client.frame.MmdModelFramePreparation;
import com.shiroha.mmdskin.client.firstperson.FirstPersonRuntime;
import com.shiroha.mmdskin.client.gpu.MmdGpuDrawDevice;
import com.shiroha.mmdskin.client.inventory.InventoryRenderSession;
import com.shiroha.mmdskin.client.metrics.RenderMetrics;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.client.model.ModelRepository;
import com.shiroha.mmdskin.client.model.NativeModelBoundsLoader;
import com.shiroha.mmdskin.client.texture.MinecraftTextureDevice;
import com.shiroha.mmdskin.client.texture.NativeTexturePathResolver;
import com.shiroha.mmdskin.client.texture.TextureRepository;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.compat.vr.DefaultVrRuntimePort;
import com.shiroha.mmdskin.scene.client.SceneRenderContributor;
import com.shiroha.mmdskin.scene.client.SceneSession;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MmdClientRenderRuntime implements AutoCloseable {
    private static volatile MmdClientRenderRuntime installed;

    private final ExecutorService modelLoadExecutor;
    private final ModelRepository models;
    private final AnimationRepository animations;
    private final ModelAnimationController animationController;
    private final MmdFrameUpdater frameUpdater;
    private final FrameRenderQueue frameQueue;
    private final TextureRepository textures;
    private final MmdGpuDrawDevice drawDevice;
    private final InventoryRenderSession inventory;
    private final RenderMetrics metrics;
    private final SceneSession scenes;
    private final FirstPersonRuntime firstPerson;
    private long frameId;
    private float frameDeltaSeconds = 1.0F / 20.0F;

    public MmdClientRenderRuntime(ExecutorService modelLoadExecutor,
                                  ModelRepository models,
                                  AnimationRepository animations,
                                  ModelAnimationController animationController,
                                  MmdFrameUpdater frameUpdater,
                                  FrameRenderQueue frameQueue,
                                  TextureRepository textures,
                                  MmdGpuDrawDevice drawDevice) {
        this.modelLoadExecutor = Objects.requireNonNull(modelLoadExecutor, "modelLoadExecutor");
        this.models = Objects.requireNonNull(models, "models");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.animationController = Objects.requireNonNull(animationController, "animationController");
        this.frameUpdater = Objects.requireNonNull(frameUpdater, "frameUpdater");
        this.frameQueue = Objects.requireNonNull(frameQueue, "frameQueue");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.drawDevice = Objects.requireNonNull(drawDevice, "drawDevice");
        this.inventory = new InventoryRenderSession(drawDevice);
        this.metrics = new RenderMetrics(models, frameQueue, textures, drawDevice);
        this.scenes = new SceneSession(models, new SceneRenderContributor(frameQueue, frameUpdater));
        this.firstPerson = new FirstPersonRuntime(
                models, frameUpdater, frameQueue,
                NativePortAdapters.model(), new DefaultVrRuntimePort());
    }

    public static MmdClientRenderRuntime createDefault() {
        synchronizePhysicsConfig();
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mmdskin-model-loader");
            thread.setDaemon(true);
            return thread;
        });
        AnimationRepository animations = new AnimationRepository(NativePortAdapters.animation());
        ModelAnimationController animationController = new ModelAnimationController(animations);
        NativeRenderDataPort nativeData = NativeRenderDataPorts.renderDataPort();
        TextureRepository textures = new TextureRepository(
                new NativeTextureBridgeAdapter(nativeData),
                new MinecraftTextureDevice(),
                new NativeTexturePathResolver(nativeData),
                executor,
                Clock.systemUTC(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Math.multiplyExact((long) ConfigManager.getTextureCacheBudgetMB(), 1024L * 1024L));
        MmdGpuDrawDevice drawDevice = new MmdGpuDrawDevice(nativeData, textures);
        ModelRepository models = new ModelRepository(
                NativePortAdapters.modelLoad(),
                NativePortAdapters.animation(),
                NativePortAdapters.model(),
                NativePortAdapters.modelMatrix(),
                executor,
                Clock.systemUTC(),
                Duration.ofSeconds(5),
                drawDevice::releaseModel,
                new NativeModelBoundsLoader(nativeData));
        return new MmdClientRenderRuntime(executor, models, animations, animationController,
                new MmdFrameUpdater(new MmdModelFramePreparation(animationController)),
                new FrameRenderQueue(), textures, drawDevice);
    }

    public static synchronized void install(MmdClientRenderRuntime runtime) {
        MmdClientRenderRuntime previous = installed;
        installed = Objects.requireNonNull(runtime, "runtime");
        if (previous != null && previous != runtime) {
            previous.close();
        }
    }

    public static MmdClientRenderRuntime current() {
        MmdClientRenderRuntime runtime = installed;
        if (runtime == null) {
            throw new IllegalStateException("MMD client render runtime is not installed");
        }
        return runtime;
    }

    public static Optional<MmdClientRenderRuntime> currentIfInstalled() {
        return Optional.ofNullable(installed);
    }

    public Optional<ModelLease> acquire(ModelKey key) {
        return models.acquire(key);
    }

    public ModelRepository models() {
        return models;
    }

    public AnimationRepository animations() {
        return animations;
    }

    public ModelAnimationController animationController() {
        return animationController;
    }

    public MmdFrameUpdater frameUpdater() {
        return frameUpdater;
    }

    public FrameRenderQueue frameQueue() {
        return frameQueue;
    }

    public TextureRepository textures() {
        return textures;
    }

    public RenderMetrics metrics() {
        return metrics;
    }

    public InventoryRenderSession inventory() {
        return inventory;
    }

    public SceneSession scenes() {
        return scenes;
    }

    public FirstPersonRuntime firstPerson() {
        return firstPerson;
    }

    public void beginFrame(float deltaTicks) {
        frameQueue.clear();
        firstPerson.beginFrame();
        frameId++;
        frameDeltaSeconds = deltaTicksToSeconds(deltaTicks);
    }

    public long frameId() {
        return frameId;
    }

    public float frameDeltaSeconds() {
        return frameDeltaSeconds;
    }

    static float deltaTicksToSeconds(float deltaTicks) {
        if (!Float.isFinite(deltaTicks) || deltaTicks < 0.0F) {
            return 0.0F;
        }
        return Math.clamp(deltaTicks / 20.0F, 0.0F, 0.25F);
    }

    private static void synchronizePhysicsConfig() {
        NativePortAdapters.physics().configure(
                ConfigManager.isPhysicsEnabled(),
                ConfigManager.getPhysicsGravityY(),
                ConfigManager.getPhysicsFps(),
                ConfigManager.getPhysicsMaxSubstepCount(),
                ConfigManager.getPhysicsInertiaStrength(),
                ConfigManager.getPhysicsMaxLinearVelocity(),
                ConfigManager.getPhysicsMaxAngularVelocity(),
                ConfigManager.isPhysicsJointsEnabled(),
                ConfigManager.isPhysicsKinematicFilter(),
                ConfigManager.isPhysicsCollisionEnabled(),
                ConfigManager.getPhysicsCollisionStabilityMode(),
                ConfigManager.isPhysicsDebugLog());
    }

    public void flushWorldFrame(double cameraX, double cameraY, double cameraZ, int packedLight) {
        textures.setBudgetBytes(Math.multiplyExact(
                (long) ConfigManager.getTextureCacheBudgetMB(), 1024L * 1024L));
        textures.tick();
        scenes.contribute(cameraX, cameraY, cameraZ, packedLight, frameId, frameDeltaSeconds);
        frameQueue.flush(drawDevice);
    }

    public void tick() {
        models.tick();
        scenes.tick();
        models.evictUnused(ConfigManager.getModelPoolMaxCount());
    }

    @Override
    public synchronized void close() {
        frameQueue.clear();
        inventory.close();
        firstPerson.close();
        scenes.close();
        animations.close();
        models.close();
        drawDevice.close();
        textures.close();
        modelLoadExecutor.shutdownNow();
        if (installed == this) {
            installed = null;
        }
    }
}
