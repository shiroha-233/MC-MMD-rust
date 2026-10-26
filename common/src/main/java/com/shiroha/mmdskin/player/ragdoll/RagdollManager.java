/* 文件职责：管理死亡布娃娃尸体的登记、模型接管、物理启停、保留策略与新管线提交。 */
package com.shiroha.mmdskin.player.ragdoll;

import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.bridge.runtime.NativeRagdollPort;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.frame.MmdEntityPose;
import com.shiroha.mmdskin.client.frame.MmdModelMotion;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 死亡布娃娃管理器（客户端，从零实现）。
 *
 * <p>核心设计约定：
 * <ul>
 *   <li><b>每次死亡一条独立记录</b>：{@code corpses} 用自增 corpseId 做 key，不用实体 UUID
 *       （玩家复活后 UUID 不变，按 UUID 去重会导致同一玩家第二次死亡不创建尸体）。
 *       同时维护 entity 对象身份 → corpseId 索引，渲染抑制查询 O(1)。</li>
 *   <li><b>地面用方块射线检测</b>：从尸体位置向下 {@link ClipContext} 打射线取真实承重面，
 *       不用 Heightmap——高度图只认"该列最高方块"，悬洞/隧道/树叶/水面都会误判。</li>
 *   <li><b>实体引用弱持有</b>：失败冷却表用 {@link WeakHashMap}，实体被回收后条目自动消失。</li>
 *   <li><b>26.3 新管线</b>：尸体不再走 {@code RenderType}/{@code MultiBufferSource}，
 *       改为与其它 MMD 模型同一条链——{@code MmdFrameUpdater.updateOnce} 推进骨骼，
 *       再把 {@link MmdDrawRequest} 塞进 {@code FrameRenderQueue}，由
 *       {@code MmdGpuDrawDevice} 统一开 Render Pass 提交。</li>
 * </ul>
 */
public final class RagdollManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final RagdollManager INSTANCE = new RagdollManager();

    /** 世界坐标 ↔ 模型局部单位换算（与引擎 UNIT_TO_M 一致）。 */
    private static final float MODEL_SCALE = 0.09f;

    /** 世界重力（米/s²，负值向下）。 */
    private static final float GRAVITY_METERS_PER_SEC2 = -9.81f;

    /**
     * 一个"模型单位"实际对应多少米。
     *
     * <p>渲染侧把整个模型根矩阵按 {@code renderScale = 0.09 × 用户模型缩放} 缩放
     * （见 {@code MmdModelInstance#applyModelConfig} 与 {@code MmdGpuDrawDevice} 的 modelMatrix.scale），
     * 所以骨骼位移进入世界坐标时一定会被乘上这个系数。物理换算必须用同一个值，
     * 否则用户把模型缩放调离 1.0 时，布娃娃的初速度 / 重力 / 地面高度会整体偏掉一个缩放因子。
     *
     * <p>模型缩放为 1.0 时返回 0.09，与引擎 {@code UNIT_TO_M} 完全一致，
     * 重力也算成 -9.81 / 0.09 = -109（正是引擎默认值）→ 行为与旧版逐位相同。
     */
    private static float unitToMeter(String modelName) {
        float scale = 1.0f;
        try {
            ModelConfigData config = ModelConfigManager.getLiveConfig(modelName);
            if (config != null && Float.isFinite(config.modelScale) && config.modelScale > 0.0f) {
                scale = config.modelScale;
            }
        } catch (RuntimeException e) {
            // 配置读取失败时退回 1.0（等价于旧的固定 0.09 行为）。
            LOGGER.warn("[Ragdoll] 读取模型缩放失败，退回默认单位换算: {}", modelName, e);
        }
        return MODEL_SCALE * scale;
    }

    /**
     * 死亡动画要跳过的开头帧数（30fps）。
     *
     * <p>MMD 死亡动画的第 0 帧通常是**绑定姿势**（站直、手臂微张）。死亡时若从第 0 帧
     * 渐变过去，交叉淡入会让手臂从"垂着"慢慢"张开到两侧"——看起来就是角色先僵一下。
     */
    public static final float DEATH_ANIM_SKIP_FRAMES = 3.0f;
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    /** 地面高度复检节流（毫秒）。 */
    private static final long GROUND_CHECK_INTERVAL_MS = 1000;
    /** 模型缓存续命 / 重载检测间隔（毫秒）。 */
    private static final long CACHE_TOUCH_INTERVAL_MS = 5000;
    /** 布娃娃启动失败后的重试冷却（毫秒）。 */
    private static final long FAILED_RETRY_COOLDOWN_MS = 5000;
    /** 尸体渲染距离剔除（方块，平方）。 */
    private static final double RENDER_CULL_DISTANCE_SQ = 64.0 * 64.0;
    /** 地面射线最大下探深度（方块）。 */
    private static final double GROUND_RAY_MAX_DEPTH = 32.0;
    /** 模型就绪后至少渲染这么多帧才允许启物理（骨骼必须先离开绑定姿势）。 */
    private static final int MIN_RENDERED_FRAMES = 3;
    /** 模型就绪后最多等这么久就无条件启物理（防止尸体在视野外永不启物理）。 */
    private static final long RENDER_READY_FALLBACK_MS = 2000L;
    /** 尸体模型的 ownerId 前缀（每条尸体记录一个独立模型实例）。 */
    private static final String CORPSE_OWNER_PREFIX = "corpse:";

    /** 每次死亡一个独立记录；key = 自增 corpseId。 */
    private final Map<Long, Corpse> corpses = new LinkedHashMap<>();
    private long nextCorpseId = 1L;

    /** entity 对象身份 → corpseId 索引（渲染抑制查询 O(1)；仅客户端线程访问）。 */
    private final IdentityHashMap<LivingEntity, Long> corpseIdByEntity = new IdentityHashMap<>();
    /**
     * 实体 UUID → 尸体 id。渲染路由手里只有快照里的实体 UUID（拿不到实体对象），
     * 用这张表做 O(1) 查找，避免每帧遍历全部尸体。
     */
    private final Map<UUID, Long> corpseIdByEntityId = new HashMap<>();

    /** 布娃娃启动失败的实体（5 秒内不重试；WeakHashMap 随实体回收自动清理）。 */
    private final WeakHashMap<LivingEntity, Long> failedAttempts = new WeakHashMap<>();

    /**
     * 待销毁的尸体模型 ownerId 队列。
     *
     * <p>尸体登记发生在渲染过程中（渲染栈里的死亡解析点），伴随的"上限逐出 → 销毁模型"
     * 如果同步执行，会在画面绘制到一半时删除 GL 对象——紧接着新尸体的工厂创建新对象时
     * 拿到被 GL 复用的 id，加上 Mojang 状态缓存失同步，新尸体就会出现空贴图/闪一下/连锁消失。
     * 销毁一律入队，延迟到 tick（帧间隙）执行。
     */
    private final ArrayDeque<String> pendingModelDisposals = new ArrayDeque<>();

    private final NativeRagdollPort ragdollPort = NativePortAdapters.ragdoll();

    // ======== 死亡界面劫持（相机） ========
    private boolean cameraHijacked;
    private CameraType savedCameraType;
    // ======== 死亡观战相机（单击进入：鼠标环视 + 滚轮缩放） ========
    /** 是否处于"自由观战"状态（死亡屏单击进入，鼠标环视 + 滚轮缩放）。 */
    private boolean deathCamObserve;
    /** 自由观战的水平朝向（度）。 */
    private float deathCamYaw;
    /** 自由观战的俯仰角（度），默认略微俯视。 */
    private float deathCamPitch = 25.0f;
    /** 自由观战相机到支点的距离（格）。 */
    private double deathCamDistance = 4.5;
    /** 当前世界（换世界 / 断线时清空尸体）。 */
    private Level trackedLevel;

    private static final class Corpse {
        final long id;
        final UUID subjectId;
        /** 尸体专用渲染标识（避免与实体自身的快照撞 id）。 */
        final UUID renderId;
        /** 精确绑定到死亡时的实体对象；复活后是新的实体对象，即使 UUID 相同也不互相抑制。 */
        final LivingEntity sourceEntity;
        /** 尸体专用缓存键（与活人模型分离，每次死亡独立）。 */
        final ModelKey corpseKey;
        final long deathTimeMs;
        /** 布娃娃启动参数快照（[0] 随地面复检/位置跟踪动态刷新）。 */
        final float[] pendingParams;
        /**
         * 一个模型单位对应多少米（含用户模型缩放）。
         * 地面高度随位置/地面复检动态刷新时必须用同一个值换算，否则缩放不为 1 时地面高度会漂。
         */
        final float unitToMeter;
        /** 尸体世界坐标（死亡动画窗口内跟踪实体，布娃娃启用后冻结为模型原点）。 */
        double x;
        double y;
        double z;
        float yawDegrees;
        /** 当前地面世界高度（射线检测）。 */
        double groundWorldY;
        long lastGroundCheckMs;
        long lastCacheTouchMs;
        boolean renderReady;
        boolean ragdollReady;
        boolean deathAnimStarted;
        // ===== 死亡实体跟踪（登记即开始，直到启物理交接） =====
        /** 上一 tick 的实体位置（计算交接速度用）。 */
        double lastTickX;
        double lastTickY;
        double lastTickZ;
        boolean hasLastTickPos;
        /** 实体速度 EMA（格/tick）——位置差分比 getDeltaMovement 更能代表"玩家当前真实运动"。 */
        double emaVelX;
        double emaVelY;
        double emaVelZ;
        int velocitySamples;
        /** 尸体模型就绪（首次渲染前）的时刻；用于等动画真正应用到骨骼。 */
        long readyMs;
        /** 尸体模型实际渲染过的帧数（动画真正作用到骨骼的次数）。 */
        int renderedFrames;
        /** 启物理成功的时刻（物理保持时长计时起点）。 */
        long ragdollReadyMs;
        /** 物理已冻结（保持期到点）：姿势保持、跳过布娃娃步进与 MMD 物理。 */
        boolean physicsFrozen;
        /** 缓存的打包光照（tick 里刷新，避免每帧重复查光照引擎）。 */
        int packedLight = LightCoordsUtil.FULL_BRIGHT;
        /** 尸体模型实例（异步加载完成后填充）。 */
        MmdModelInstance model;
        /** 尸体模型租约（常驻持有，防止被模型池回收）。 */
        ModelLease lease;

        Corpse(long id, UUID subjectId, LivingEntity sourceEntity, ModelKey corpseKey,
               long deathTimeMs, float[] pendingParams, float unitToMeter,
               double x, double y, double z, float yawDegrees, double groundWorldY) {
            this.id = id;
            this.subjectId = subjectId;
            this.renderId = UUID.nameUUIDFromBytes(
                    ("mmdskin:corpse:" + id).getBytes(StandardCharsets.UTF_8));
            this.sourceEntity = sourceEntity;
            this.corpseKey = corpseKey;
            this.deathTimeMs = deathTimeMs;
            this.pendingParams = pendingParams;
            this.unitToMeter = unitToMeter;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yawDegrees = yawDegrees;
            this.groundWorldY = groundWorldY;
            this.lastGroundCheckMs = deathTimeMs;
            this.lastCacheTouchMs = 0L;
        }
    }

    private RagdollManager() {
    }

    public static RagdollManager getInstance() {
        return INSTANCE;
    }

    // ========================================================================
    // 死亡触发
    // ========================================================================

    /**
     * 尝试为死亡实体登记尸体（渲染层死亡解析点调用，每帧可重复调用，内部幂等）。
     *
     * @param entity    死亡中的实体
     * @param modelName 该实体当前使用的 MMD 模型名（没有 MMD 模型则不登记）
     * @return true = 该实体的尸体管线已登记
     */
    public boolean tryEnterRagdoll(LivingEntity entity, String modelName) {
        if (!ConfigManager.isRagdollEnabled()) {
            return false;
        }
        if (entity == null || modelName == null || modelName.isBlank()) {
            return false;
        }
        if (!entity.isDeadOrDying()) {
            // 复活后的新实体对象：UUID 相同，但已经不是那具尸体对应的死亡实体。
            // 必须把旧尸体从 UUID 索引里摘掉，否则渲染路由会连复活后的本体一起抑制掉
            //（玩家复活后整个人凭空消失，直到尸体被逐出为止）。
            dropSuppressionIndex(entity.getUUID());
            return false;
        }
        if (corpseIdByEntity.containsKey(entity)) {
            return true;
        }
        Long lastFail = failedAttempts.get(entity);
        if (lastFail != null && System.currentTimeMillis() - lastFail < FAILED_RETRY_COOLDOWN_MS) {
            return false;
        }

        UUID subjectId = entity.getUUID();
        double groundWorldY = sampleGroundY(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getY());
        // 单位换算随该模型的实际渲染缩放走（渲染缩放 = 0.09 × 用户模型缩放）。
        float unitToMeter = unitToMeter(modelName);
        float[] params = buildRagdollParams(entity, groundWorldY, unitToMeter);

        // 尸体专用缓存键：每次死亡独立（带 corpseId）——每具尸体的实例、动画、物理完全独立，
        // 同玩家的多具尸体可以共存且互不联动。
        long corpseId = nextCorpseId++;
        ModelKey corpseKey = new ModelKey(modelName,
                CORPSE_OWNER_PREFIX + subjectId + "|" + corpseId, ModelKey.Usage.ENTITY);

        Corpse corpse = new Corpse(
                corpseId, subjectId, entity, corpseKey,
                System.currentTimeMillis(), params, unitToMeter,
                entity.getX(), entity.getY(), entity.getZ(),
                entity.yBodyRot,
                groundWorldY);
        corpses.put(corpseId, corpse);
        corpseIdByEntity.put(entity, corpseId);
        corpseIdByEntityId.put(subjectId, corpseId);
        evictOverflow();

        LOGGER.info("[Ragdoll] 尸体登记: id={} {} (model={}, 当前 {} 具)",
                corpseId, subjectId, modelName, corpses.size());
        return true;
    }

    /**
     * 布娃娃启动参数快照。契约见 {@link com.shiroha.mmdskin.bridge.runtime.NativeRagdollPort}。
     *
     * @param unitToMeter 一个模型单位对应多少米（已含用户模型缩放）
     */
    private float[] buildRagdollParams(LivingEntity entity, double groundWorldY, float unitToMeter) {
        float[] params = new float[16];
        // [0] 模型局部地面高度（实体脚底相对模型原点）
        params[0] = (float) ((groundWorldY - entity.getY()) / unitToMeter);
        // [1-3] 死亡初速度（世界 m/s；引擎按 yaw 旋转换算到模型局部空间）。
        // 注意单位：getDeltaMovement 是"格/tick"，契约要"米/秒"，1 tick = 1/20 秒 → ×20。
        // 漏乘 20 就是"布娃娃没有惯性"的根因（速度只剩 1/20，看起来原地瘫倒）。
        Vec3 delta = entity.getDeltaMovement();
        params[1] = (float) (delta.x * 20.0);
        params[2] = (float) (delta.y * 20.0);
        params[3] = (float) (delta.z * 20.0);
        // [4] yaw（弧度，速度换算用）
        params[4] = entity.yBodyRot * DEGREES_TO_RADIANS;
        // [5] 厚度 / [6] 摩擦 / [7] 恢复 / [8] 线阻尼 / [9] 角阻尼：0 = 引擎默认（lab 手感）
        // [10] 重力 Y（模型单位/s²）。必须显式给，不能留 0 走引擎默认：
        //      引擎默认是 -9.81 / 0.09，只在模型缩放 = 1.0 时才与真实重力一致。
        params[10] = GRAVITY_METERS_PER_SEC2 / unitToMeter;
        // [11] 关节强度（用户配置；引擎侧默认 1.35）
        params[11] = ConfigManager.getRagdollJointStrength();
        // [12] 总质量 / [13] 手臂限位：0 = 引擎默认
        // [14] 世界米 → 模型单位换算（含用户模型缩放；速度换算用）
        params[14] = unitToMeter;
        return params;
    }

    // ========================================================================
    // 每 tick：模型就绪检测 + 保留策略 + 相机劫持
    // ========================================================================

    public void tick(Minecraft minecraft) {
        drainPendingModelDisposals();
        if (trackedLevel != minecraft.level) {
            // 换世界 / 断线：尸体全部失效
            trackedLevel = minecraft.level;
            clearAll();
        }
        if (!ConfigManager.isRagdollEnabled()) {
            // 布娃娃开关被关掉：已存在的尸体要立刻清掉，否则它们还会继续被推进、
            // 继续占用 Bullet 物理，直到保留策略或保持期到期。
            if (!corpses.isEmpty()) {
                LOGGER.info("[Ragdoll] 布娃娃已关闭，清除现有尸体 {} 具", corpses.size());
                clearAll();
            }
            tickCameraHijack(minecraft);
            return;
        }
        if (corpses.isEmpty()) {
            // 尸体被逐空后仍要继续维护相机劫持，否则玩家会永久停在第三人称
            tickCameraHijack(minecraft);
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            clearAll();
            return;
        }
        long now = System.currentTimeMillis();

        Iterator<Corpse> it = corpses.values().iterator();
        while (it.hasNext()) {
            Corpse corpse = it.next();

            // 保留策略逐出
            if (shouldEvictCorpse(minecraft, corpse, now)) {
                releaseCorpse(corpse);
                it.remove();
                continue;
            }

            // 模型就绪检测（异步加载完成 → 接管渲染并播死亡动画）
            if (corpse.model == null || now - corpse.lastCacheTouchMs > CACHE_TOUCH_INTERVAL_MS) {
                corpse.lastCacheTouchMs = now;
                touchCache(corpse);
            }

            // 登记即跟踪死亡实体（击退/下落还在飞）：尸体模型出现的位置、启物理的交接速度都来自这里
            if (!corpse.ragdollReady
                    && corpse.sourceEntity != null && !corpse.sourceEntity.isRemoved()) {
                LivingEntity tracked = corpse.sourceEntity;
                corpse.x = tracked.getX();
                corpse.y = tracked.getY();
                corpse.z = tracked.getZ();
                corpse.yawDegrees = tracked.yBodyRot;
                corpse.pendingParams[0] = (float) ((corpse.groundWorldY - corpse.y) / corpse.unitToMeter);

                if (corpse.hasLastTickPos) {
                    double dvx = corpse.x - corpse.lastTickX;
                    double dvy = corpse.y - corpse.lastTickY;
                    double dvz = corpse.z - corpse.lastTickZ;
                    corpse.emaVelX = corpse.emaVelX * 0.5 + dvx * 0.5;
                    corpse.emaVelY = corpse.emaVelY * 0.5 + dvy * 0.5;
                    corpse.emaVelZ = corpse.emaVelZ * 0.5 + dvz * 0.5;
                    corpse.velocitySamples++;
                }
                corpse.lastTickX = corpse.x;
                corpse.lastTickY = corpse.y;
                corpse.lastTickZ = corpse.z;
                corpse.hasLastTickPos = true;
            }

            // 地面高度复检（射线检测，节流；模型就绪即生效；冻结后无意义，跳过省性能）
            if (corpse.renderReady && !corpse.physicsFrozen
                    && now - corpse.lastGroundCheckMs > GROUND_CHECK_INTERVAL_MS) {
                corpse.lastGroundCheckMs = now;
                double groundY = sampleGroundY(minecraft.level, corpse.x, corpse.y, corpse.z, corpse.groundWorldY);
                if (Math.abs(groundY - corpse.groundWorldY) > 0.01) {
                    LOGGER.info("[Ragdoll] 地面高度变化: id={} {} -> {}", corpse.id, corpse.groundWorldY, groundY);
                    corpse.groundWorldY = groundY;
                    corpse.pendingParams[0] = (float) ((groundY - corpse.y) / corpse.unitToMeter);
                    long handle = handleOf(corpse);
                    if (corpse.ragdollReady && handle != 0L) {
                        safeSetRagdollGround(handle, corpse.pendingParams[0]);
                    }
                }
            }

            // 刷新尸体所在位置的原版打包光照（tick 频率即可；26.3 没有 LevelRenderer.getLightColor）
            refreshPackedLight(minecraft.level, corpse);

            // 物理保持期到点 → 冻结（性能优化）：跳过布娃娃步进 + 关 MMD 物理（发饰/裙摆），
            // 接管骨全局矩阵停在最后一次物理姿势，尸体可见但不再消耗物理 CPU。
            if (corpse.ragdollReady && !corpse.physicsFrozen) {
                int keepSeconds = ConfigManager.getRagdollPhysicsKeepSeconds();
                long handle = handleOf(corpse);
                if (keepSeconds > 0 && now - corpse.ragdollReadyMs > keepSeconds * 1000L && handle != 0L) {
                    safeFreezeRagdoll(handle);
                    corpse.physicsFrozen = true;
                    LOGGER.info("[Ragdoll] 物理保持期到点，尸体冻结: id={} ({} s)", corpse.id, keepSeconds);
                }
            }

            // 死亡动画窗口结束 → 从当前姿势启物理。
            // 前置：模型至少实际渲染过 3 帧（动画已应用到骨骼，离开 T-pose）；
            // 兜底：2 秒后无条件启用，防止尸体在视野外永不启物理。
            if (corpse.renderReady && !corpse.ragdollReady
                    && corpse.readyMs > 0L
                    && (corpse.renderedFrames >= MIN_RENDERED_FRAMES
                        || now - corpse.readyMs >= RENDER_READY_FALLBACK_MS)) {
                long deathAnimMs = (long) (ConfigManager.getRagdollDeathAnimSeconds() * 1000.0f);
                if (now - corpse.deathTimeMs >= deathAnimMs) {
                    if (!enableRagdollOn(corpse)) {
                        releaseCorpse(corpse);
                        it.remove();
                    }
                }
            }
        }

        evictOverflow();
        tickCameraHijack(minecraft);
    }

    private boolean shouldEvictCorpse(Minecraft minecraft, Corpse corpse, long now) {
        String keepMode = ConfigManager.getCorpseKeepMode();
        switch (keepMode) {
            case "SECONDS" -> {
                if (now - corpse.deathTimeMs > ConfigManager.getCorpseKeepSeconds() * 1000L) {
                    LOGGER.info("[Ragdoll] 保留期到点逐出: id={} ({} s)", corpse.id,
                            ConfigManager.getCorpseKeepSeconds());
                    return true;
                }
            }
            case "FOREVER" -> {
                // 不按时间/距离逐出，仅受数量上限约束
                return false;
            }
            // NEARBY（默认）
            default -> {
                if (minecraft.player != null) {
                    double radius = ConfigManager.getCorpseKeepRadius();
                    double dx = corpse.x - minecraft.player.getX();
                    double dy = corpse.y - minecraft.player.getY();
                    double dz = corpse.z - minecraft.player.getZ();
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        LOGGER.info("[Ragdoll] 超出附近保留半径({} 格)逐出: id={}", (int) radius, corpse.id);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 尸体加载距离闸门。
     *
     * <p>{@code modelLoadMaxDistance} &lt;= 0 表示不限制（闸门关闭，等价于旧行为）。
     * 距离按玩家位置算（相机就在玩家眼睛上，与实体路径的口径一致）。
     */
    private static boolean exceedsModelLoadDistance(Corpse corpse) {
        float maxDistance = ConfigManager.getModelLoadMaxDistance();
        if (maxDistance <= 0.0F) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        double dx = corpse.x - minecraft.player.getX();
        double dy = corpse.y - minecraft.player.getY();
        double dz = corpse.z - minecraft.player.getZ();
        return dx * dx + dy * dy + dz * dz > (double) maxDistance * maxDistance;
    }

    /**
     * 尸体是否还需要每帧推进模型。
     *
     * <p>冻结（{@code physicsFrozen}）后姿势已经定型，引擎侧也不再步进布娃娃，
     * 继续推进动画只是白烧 CPU（动画求值 + 骨骼矩阵 + 蒙皮）。
     *
     * <p>停止的前提是"布娃娃已就绪 || 已冻结"：布娃娃还没就绪时绝不能停推，
     * 否则远离视线的尸体骨骼永远停在 T-pose，2 秒兜底启物理就会按 T-pose 摊开
     *（26.3 移植期踩过的坑）。
     */
    private static boolean shouldAdvanceCorpse(Corpse corpse) {
        if (!corpse.ragdollReady && !corpse.physicsFrozen) {
            // 布娃娃还没就绪：必须继续推进，骨骼要从绑定姿势（T-pose）走出来。
            return true;
        }
        // 已就绪：只有冻结之后才停（冻结 = 姿势定型）。
        return !corpse.physicsFrozen;
    }

    /** 获取尸体模型；实例被外部销毁时干净复位，等新实例加载后完整重来。 */
    private void touchCache(Corpse corpse) {
        try {
            MmdClientRenderRuntime runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
            if (runtime == null) {
                return;
            }
            if (corpse.lease != null) {
                MmdModelInstance held = corpse.model;
                if (held == null || held.handle() == 0L) {
                    // 外部销毁恢复：设置保存(reloadAll)/模型池清理会销毁所有实例，
                    // 尸体手里可能还攥着已销毁的旧实例——干净复位，等新实例加载后完整重来。
                    LOGGER.info("[Ragdoll] 尸体模型实例被外部销毁，等待重载: id={}", corpse.id);
                    closeLease(corpse);
                    corpse.model = null;
                    corpse.renderReady = false;
                    corpse.ragdollReady = false;
                    corpse.deathAnimStarted = false;
                    corpse.renderedFrames = 0;
                }
                return;
            }
            // 加载前距离闸门：离玩家太远的尸体不加载模型。
            // 尸体比实体更贵（一具尸体 = 1 个 native 模型 + 1 个 Bullet 世界），
            // 而渲染剔除本来就在 64 格之外，太远的尸体加载了也看不见，纯浪费。
            // 这里只挡"加载"，尸体记录本身保留，玩家走近时再加载。
            if (exceedsModelLoadDistance(corpse)) {
                return;
            }
            ModelLease acquired = runtime.acquire(corpse.corpseKey).orElse(null);
            if (acquired == null) {
                // 模型还在异步加载
                return;
            }
            MmdModelInstance acquiredInstance = acquired.instance();
            if (acquiredInstance.handle() == 0L) {
                // 仓库里残留了已销毁的实例，等仓库重建后再取
                acquired.close();
                return;
            }
            corpse.lease = acquired;
            corpse.model = acquiredInstance;
            corpse.renderReady = true;
            corpse.readyMs = System.currentTimeMillis();
            startCorpseDeathAnim(corpse);
            LOGGER.info("[Ragdoll] 尸体模型就绪: id={} ({} ms)",
                    corpse.id, corpse.readyMs - corpse.deathTimeMs);
        } catch (Exception e) {
            LOGGER.warn("[Ragdoll] 尸体模型获取失败: id={}", corpse.id, e);
        }
    }

    /** 播放死亡动画并按已流逝时间对齐进度（30fps）。 */
    private void startCorpseDeathAnim(Corpse corpse) {
        if (corpse.deathAnimStarted || corpse.model == null) {
            return;
        }
        corpse.deathAnimStarted = true;
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        long dieAnim = runtime.animations().animationFor(corpse.model, EntityAnimState.State.Die.propertyName);
        if (dieAnim == 0L) {
            return;
        }
        corpse.model.changeAnimation(dieAnim, 0);
        corpse.model.setLayerLoop(0, false);
        float elapsedSeconds = (System.currentTimeMillis() - corpse.deathTimeMs) / 1000.0f;
        // 按已流逝时间对齐（接得上本体那边），并跳过死亡动画开头的绑定姿势帧
        corpse.model.seekLayer(0, elapsedSeconds * 30.0f + DEATH_ANIM_SKIP_FRAMES);
    }

    /** 启物理；false = 启动失败（记冷却并逐出该尸体）。 */
    private boolean enableRagdollOn(Corpse corpse) {
        long handle = handleOf(corpse);
        if (handle == 0L) {
            return false;
        }
        float[] params = corpse.pendingParams.clone();
        // 交接速度：优先用位置差分 EMA——它是玩家死亡后仍在飞行的"当前真实速度"，
        // 尸体全程跟踪实体，启物理瞬间以此接管轨迹，不分离、不丢失惯性。
        // （×20：EMA 是格/tick，FFI 契约要米/秒）
        // 实体已消失（mob 死亡 20 tick 后被移除）时退回登记时的速度快照。
        if (corpse.velocitySamples >= 2) {
            params[1] = (float) (corpse.emaVelX * 20.0);
            params[2] = (float) (corpse.emaVelY * 20.0);
            params[3] = (float) (corpse.emaVelZ * 20.0);
        }
        LivingEntity source = corpse.sourceEntity;
        if (source != null && !source.isRemoved()) {
            params[4] = source.yBodyRot * DEGREES_TO_RADIANS;
        }
        boolean ok;
        try {
            ok = ragdollPort.enterRagdoll(handle, params);
        } catch (Throwable error) {
            LOGGER.warn("[Ragdoll] 布娃娃启动异常: id={}", corpse.id, error);
            ok = false;
        }
        if (!ok) {
            LOGGER.info("[Ragdoll] 布娃娃启动失败（冷却 {} ms）: id={}", FAILED_RETRY_COOLDOWN_MS, corpse.id);
            failedAttempts.put(corpse.sourceEntity, System.currentTimeMillis());
            return false;
        }
        corpse.ragdollReady = true;
        corpse.ragdollReadyMs = System.currentTimeMillis();
        LOGGER.info("[Ragdoll] 布娃娃启用: id={} 交接速度=({}, {}, {}) m/s",
                corpse.id, params[1], params[2], params[3]);
        return true;
    }

    // ========================================================================
    // 尸体渲染（26.3 新管线：Frame Queue → MmdGpuDrawDevice）
    // ========================================================================

    /**
     * 把已就绪的尸体转换为世界 Frame Queue 请求。
     *
     * <p>与 {@code SceneRenderContributor} 同款：先 {@code updateOnce} 推进骨骼（引擎在
     * {@code UpdateModel} 内部做动画更新 + 布娃娃步进），再把绘制请求塞进队列，
     * 由 {@code MmdGpuDrawDevice} 在统一的 Render Pass 里提交。
     *
     * @param cameraX/cameraY/cameraZ 相机世界坐标（26.3 的模型矩阵是相机相对的）
     */
    public void contribute(double cameraX, double cameraY, double cameraZ,
                           long frameId, float deltaSeconds) {
        if (corpses.isEmpty()) {
            return;
        }
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        for (Corpse corpse : corpses.values()) {
            if (!corpse.renderReady || corpse.model == null) {
                continue;
            }
            MmdModelInstance instance = corpse.model;
            if (instance.handle() == 0L) {
                continue;
            }
            double dx = corpse.x - cameraX;
            double dy = corpse.y - cameraY;
            double dz = corpse.z - cameraZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            boolean visible = distanceSquared <= RENDER_CULL_DISTANCE_SQ;

            Matrix4f transform = new Matrix4f()
                    .translation((float) dx, (float) dy, (float) dz)
                    .rotateY((float) Math.toRadians(-corpse.yawDegrees));
            MmdRenderSnapshot snapshot = new MmdRenderSnapshot(
                    corpse.renderId,
                    corpse.corpseKey,
                    MmdEntityPose.standing(),
                    MmdAnimationIntent.none(),
                    new MmdModelMotion(
                            (float) corpse.x,
                            (float) corpse.y,
                            (float) corpse.z,
                            corpse.yawDegrees * DEGREES_TO_RADIANS,
                            true),
                    ModelTransform.from(transform),
                    MmdRenderSnapshot.Visibility.OPAQUE,
                    false,
                    corpse.packedLight,
                    distanceSquared,
                    MmdRenderSnapshot.Context.WORLD);
            // 无论是否进入视锥都要推进模型：26.3 的动画/物理推进发生在 UpdateModel 里，
            // 只在绘制时推进的话，远离视线的尸体骨骼会一直停在绑定姿势（T-pose），
            // 后面按"2 秒兜底"启物理就会按 T-pose 摊开。
            // 例外：冻结后的尸体不再推进（姿势已定型，继续推进没有收益）。
            if (shouldAdvanceCorpse(corpse)) {
                runtime.frameUpdater().updateOnce(frameId, instance, snapshot, deltaSeconds);
                // 计数口径是"推进过的帧数"而不是"真正绘制过的帧数"：
                // 26.3 把推进与绘制解耦了，远离视线的尸体每帧也在推进动画/骨骼，
                // 却要等满 2 秒兜底才启物理，多余的延迟。
                corpse.renderedFrames++;
            }
            if (!visible) {
                continue;
            }
            runtime.frameQueue().enqueue(new MmdDrawRequest(instance, snapshot, choosePipeline(), false));
        }
    }

    /**
     * 刷新尸体所在方块的打包光照（tick 频率即可，光照变化很慢）。
     *
     * <p>26.3 已经没有 {@code LevelRenderer.getLightColor}，改用 {@link LightCoordsUtil}。
     */
    private static void refreshPackedLight(Level level, Corpse corpse) {
        if (level == null) {
            corpse.packedLight = LightCoordsUtil.FULL_BRIGHT;
            return;
        }
        try {
            corpse.packedLight = LightCoordsUtil.getLightCoords(level,
                    BlockPos.containing(corpse.x, corpse.y + 0.5, corpse.z));
        } catch (RuntimeException exception) {
            corpse.packedLight = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    /** 尸体与其它 MMD 模型保持一致的管线选择（透明/卡通跟随全局配置）。 */
    private static PipelineVariant choosePipeline() {
        if (ConfigManager.isToonRenderingEnabled()) {
            return PipelineVariant.TOON;
        }
        return PipelineVariant.OPAQUE;
    }

    // ========================================================================
    // 地面采样（射线检测）
    // ========================================================================

    /**
     * 从 (x, y+0.5, z) 垂直向下射线取最高承托面（含液体表面）。
     *
     * <p>必须含流体：只打实心方块的话，死亡点有水/岩浆时地面平面会落到液体底面，
     * 尸体沉进液体方块里——液体在半透明通道写深度，尸体在通道结束后绘制，
     * 被液面深度测试整块遮挡 → "贴图消失/创建失败"。取两条射线的较高命中，
     * 尸体停在液体表面（视觉上躺在水/岩浆面上），可见且符合直觉。
     *
     * @param fallback 两条射线都未命中时的返回值（保持当前地面，不突变）
     */
    private double sampleGroundY(Level level, double x, double y, double z, double fallback) {
        if (level == null) {
            return fallback;
        }
        try {
            Vec3 from = new Vec3(x, y + 0.5, z);
            Vec3 to = new Vec3(x, y - GROUND_RAY_MAX_DEPTH, z);
            double ground = Double.NEGATIVE_INFINITY;

            ClipContext solidClip = new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    Minecraft.getInstance().player);
            BlockHitResult solidHit = level.clip(solidClip);
            if (solidHit.getType() == HitResult.Type.BLOCK) {
                ground = solidHit.getLocation().y;
            }

            // 含流体二次采样：命中液体表面（水顶面 ≈ 0.875 格高）
            ClipContext fluidClip = new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    Minecraft.getInstance().player);
            BlockHitResult fluidHit = level.clip(fluidClip);
            if (fluidHit.getType() == HitResult.Type.BLOCK) {
                ground = Math.max(ground, fluidHit.getLocation().y);
            }

            if (ground != Double.NEGATIVE_INFINITY) {
                return ground;
            }
        } catch (Exception e) {
            LOGGER.debug("[Ragdoll] 地面射线检测失败，保持当前地面", e);
        }
        return fallback;
    }

    // ========================================================================
    // 实体渲染抑制 / 死亡白烟
    // ========================================================================

    /**
     * 死亡实体本体的渲染抑制。
     *
     * <p><b>尸体能画了才接管</b>，不是"登记即隐藏"：尸体模型是异步加载的，
     * 登记到就绪之间有几帧的空窗；若登记即隐藏，这几帧里本体已被藏掉、尸体又还没就绪，
     * 角色就会凭空闪一下。所以空窗期让本体继续渲染（死亡动画照播），
     * 尸体模型就绪后再由它接管——接得上是因为尸体模型就绪时会按已流逝时间对齐死亡动画。
     * 只对死亡实体本体生效：复活后的新实体 health&gt;0，不受抑制。
     *
     * <p>26.3 上"接管"这一步需要在实体渲染路由里取消本体绘制（见
     * {@code client/draw/MmdRenderRouter} 与平台 mixin），当前由渲染层负责接线；
     * 本方法只提供判定。
     */
    public boolean shouldSuppressEntityRender(LivingEntity entity) {
        if (entity == null || !entity.isDeadOrDying()) {
            return false;
        }
        Corpse corpse = findCorpseForEntity(entity);
        if (corpse == null) {
            return false;
        }
        // 尸体模型还没就绪 -> 本体继续渲染，避免死亡瞬间出现空窗闪烁
        return corpse.renderReady && corpse.model != null;
    }

    /**
     * 把某个 UUID 从抑制索引里摘掉（复活后的新实体对象出现时调用）。
     *
     * <p>只删索引，<b>不动尸体本身</b>——尸体仍然要保留、继续渲染、继续跑物理。
     *
     * <p>为什么需要：玩家复活后 UUID 不变，但实体对象是新的。抑制索引按 UUID 查，
     * 如果不摘掉，渲染路由会把"复活后的本体"也当成死亡实体本体一起抑制掉，
     * 表现为玩家复活后整个人凭空消失（本体、MMD 模型、名牌、手持物全没了）。
     */
    private void dropSuppressionIndex(UUID subjectId) {
        if (subjectId == null) {
            return;
        }
        corpseIdByEntityId.remove(subjectId);
    }

    /** 是否应隐藏该实体的原版死亡白烟（死亡粒子劫持）。 */
    public boolean shouldHideDeathPoof(Entity entity) {
        if (!ConfigManager.isHideDeathPoof()) {
            return false;
        }
        return entity instanceof LivingEntity living && findCorpseForEntity(living) != null;
    }

    /**
     * 渲染路由用的抑制判定：手里只有快照里的实体 UUID，没有实体对象。
     *
     * <p>语义与 {@link #shouldSuppressEntityRender(LivingEntity)} 相同——
     * 尸体模型就绪后，本体不再绘制（否则死亡瞬间会同时看到本体和尸体两重身影）。
     *
     * <p>注意：索引里只登记"当前正在死亡的那个实体对象"对应的尸体。
     * 玩家复活后会有同 UUID 的新实体对象出现，此时 {@link #tryEnterRagdoll} 会把索引摘掉，
     * 所以这里不会误伤复活后的玩家。
     */
    public boolean shouldSuppressEntityRender(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Long id = corpseIdByEntityId.get(entityId);
        if (id == null) {
            return false;
        }
        Corpse corpse = corpses.get(id);
        if (corpse == null) {
            return false;
        }
        // 尸体模型还没就绪 -> 本体继续渲染，避免死亡瞬间出现空窗闪烁
        return corpse.renderReady && corpse.model != null;
    }

    /** O(1) 身份查找：entity 对象 → corpse。 */
    private Corpse findCorpseForEntity(LivingEntity entity) {
        Long id = corpseIdByEntity.get(entity);
        return id != null ? corpses.get(id) : null;
    }

    public int corpseCount() {
        return corpses.size();
    }

    // ========================================================================
    // 死亡界面劫持（相机）
    // ========================================================================

    /** 死亡界面劫持是否生效（透明死亡屏 + 自动第三人称 + 观战相机）。 */
    public boolean isDeathScreenHijackActive() {
        return ConfigManager.isHijackDeathScreen();
    }

    /**
     * 单击切换自由观战（死亡屏点击层调用；不满足双击复活时走到这里）。
     *
     * <p>26.3 适配：{@code Entity.yHeadRot} 字段改为 {@code getYHeadRot()} 方法；
     * 鼠标抓取仍是 {@code MouseHandler.grabMouse()/releaseMouse()}（SDL 输入栈下未改名）。
     */
    public void toggleDeathCamObserve() {
        Minecraft minecraft = Minecraft.getInstance();
        deathCamObserve = !deathCamObserve;
        if (deathCamObserve) {
            deathCamYaw = minecraft.player != null ? minecraft.player.getYHeadRot() : deathCamYaw;
            minecraft.mouseHandler.releaseMouse();
        } else {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public boolean isDeathCamObserve() {
        return deathCamObserve;
    }

    /** 自由观战鼠标环视（灵敏度跟随 MC 设置，不硬编码）。 */
    public void deathCamMouseMove(double dx, double dy) {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double degreesPerPx = 0.4 * (0.5 + sensitivity);
        deathCamYaw -= (float) (dx * degreesPerPx);
        deathCamPitch = (float) Mth.clamp(deathCamPitch - dy * degreesPerPx, -85.0, 85.0);
    }

    /** 自由观战滚轮缩放。 */
    public void deathCamScroll(double delta) {
        deathCamDistance = Mth.clamp(deathCamDistance - delta * 0.6, 0.8, 30.0);
    }

    /**
     * 相机接管目标（{@code CameraMixin} 每帧调用）。
     *
     * @return {x, y, z, yawDegrees, pitchDegrees}；非观战状态返回 null（不接管）
     */
    public double[] deathCamTarget() {
        if (!deathCamObserve) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        // 支点：本地玩家的尸体位置（没有就玩家位置）+1m
        double px;
        double py;
        double pz;
        Corpse corpse = findCorpseForEntity(minecraft.player);
        if (corpse != null) {
            px = corpse.x;
            py = corpse.y;
            pz = corpse.z;
        } else {
            px = minecraft.player.getX();
            py = minecraft.player.getY();
            pz = minecraft.player.getZ();
        }
        py += 1.0;

        // 期望相机位置 = 支点 - 视线 × 距离；被地形挡住时像原版第三人称一样拉近
        double radYaw = Math.toRadians(deathCamYaw);
        double radPitch = Math.toRadians(deathCamPitch);
        double lx = -Math.sin(radYaw) * Math.cos(radPitch);
        double ly = -Math.sin(radPitch);
        double lz = Math.cos(radYaw) * Math.cos(radPitch);
        Vec3 pivot = new Vec3(px, py, pz);
        Vec3 desired = pivot.subtract(lx * deathCamDistance, ly * deathCamDistance, lz * deathCamDistance);
        try {
            ClipContext clip = new ClipContext(
                    pivot, desired,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    minecraft.player);
            HitResult hit = minecraft.level.clip(clip);
            if (hit.getType() != HitResult.Type.MISS) {
                desired = hit.getLocation().add(lx * 0.2, ly * 0.2, lz * 0.2);
            }
        } catch (Exception ignored) {
            // 射线失败时保持期望位置
        }

        return new double[]{desired.x, desired.y, desired.z, deathCamYaw, deathCamPitch};
    }

    private void tickCameraHijack(Minecraft minecraft) {
        boolean localDead = minecraft.player != null && minecraft.player.isDeadOrDying();
        if (localDead && !cameraHijacked && ConfigManager.isHijackDeathScreen()) {
            if (minecraft.options != null) {
                savedCameraType = minecraft.options.getCameraType();
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                cameraHijacked = true;
            }
        } else if (!localDead && cameraHijacked) {
            restoreCamera(minecraft);
        }
    }

    private void restoreCamera(Minecraft minecraft) {
        if (cameraHijacked && minecraft.options != null && savedCameraType != null) {
            minecraft.options.setCameraType(savedCameraType);
        }
        cameraHijacked = false;
        // 退出死亡状态时必须把自由观战关掉并把鼠标抓回，否则鼠标会一直飘在屏幕上。
        if (deathCamObserve) {
            deathCamObserve = false;
            if (minecraft.mouseHandler != null && minecraft.gui.screen() == null) {
                minecraft.mouseHandler.grabMouse();
            }
        }
    }

    // ========================================================================
    // 生命周期
    // ========================================================================

    /** 断线 / 换世界清理。 */
    public void clearAll() {
        for (Corpse corpse : corpses.values()) {
            releaseCorpse(corpse);
        }
        corpses.clear();
        corpseIdByEntity.clear();
        corpseIdByEntityId.clear();
        failedAttempts.clear();
        // 断线点不在渲染过程中，队列立即清空
        drainPendingModelDisposals();
        restoreCamera(Minecraft.getInstance());
    }

    private void evictOverflow() {
        int max = ConfigManager.getMaxCorpses();
        if (max <= 0 || corpses.size() <= max) {
            return;
        }
        ArrayList<Corpse> sorted = new ArrayList<>(corpses.values());
        sorted.sort(Comparator.comparingLong(c -> c.deathTimeMs));
        int toEvict = corpses.size() - max;
        for (int i = 0; i < toEvict && i < sorted.size(); i++) {
            Corpse corpse = sorted.get(i);
            corpses.remove(corpse.id);
            releaseCorpse(corpse);
            LOGGER.info("[Ragdoll] 数量上限逐出: id={}", corpse.id);
        }
    }

    private void releaseCorpse(Corpse corpse) {
        try {
            long handle = handleOf(corpse);
            if (handle != 0L) {
                ragdollPort.disableRagdoll(handle);
            }
        } catch (Throwable error) {
            LOGGER.warn("[Ragdoll] 尸体释放异常: id={}", corpse.id, error);
        }
        Long indexedId = corpseIdByEntity.get(corpse.sourceEntity);
        if (indexedId != null && indexedId == corpse.id) {
            corpseIdByEntity.remove(corpse.sourceEntity);
        }
        Long uuidIndexedId = corpseIdByEntityId.get(corpse.subjectId);
        if (uuidIndexedId != null && uuidIndexedId == corpse.id) {
            corpseIdByEntityId.remove(corpse.subjectId);
        }
        // 立即彻底销毁尸体模型实例（GL 缓冲 + 贴图引用 + native 模型）。
        // 销毁不入队执行处：入队后由 tick 在帧间隙执行（见 pendingModelDisposals），
        // 绝不在渲染过程中删 GL 对象。
        pendingModelDisposals.add(corpse.corpseKey.ownerId());
        closeLease(corpse);
        corpse.model = null;
    }

    /** 在帧间隙执行待销毁队列（tick 开头调用；绝不在渲染过程中调）。 */
    private void drainPendingModelDisposals() {
        if (pendingModelDisposals.isEmpty()) {
            return;
        }
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        while (!pendingModelDisposals.isEmpty()) {
            String ownerId = pendingModelDisposals.poll();
            try {
                if (runtime != null) {
                    runtime.models().invalidateOwner(ownerId);
                }
            } catch (Exception e) {
                LOGGER.warn("[Ragdoll] 尸体模型销毁异常: {}", ownerId, e);
            }
        }
    }

    private void closeLease(Corpse corpse) {
        ModelLease lease = corpse.lease;
        corpse.lease = null;
        if (lease == null) {
            return;
        }
        try {
            lease.close();
        } catch (Exception e) {
            LOGGER.debug("[Ragdoll] 尸体模型租约释放异常: id={}", corpse.id, e);
        }
    }

    private static long handleOf(Corpse corpse) {
        MmdModelInstance instance = corpse.model;
        return instance == null ? 0L : instance.handle();
    }

    // ========================================================================
    // native 兜底
    //
    // Rust 原生库没重编（缺少布娃娃导出）时，调用会抛 UnsatisfiedLinkError——
    // 那是 Error 不是 Exception，必须单独兜住，否则整个客户端会被带崩。
    // 兜住之后的表现是：尸体照常渲染与播死亡动画，只是没有布娃娃物理。
    // ========================================================================

    private void safeSetRagdollGround(long handle, float groundLocalY) {
        try {
            ragdollPort.setRagdollGround(handle, groundLocalY);
        } catch (Throwable error) {
            LOGGER.warn("[Ragdoll] 更新地面高度失败（原生库可能未重编）: handle={}", handle, error);
        }
    }

    private void safeFreezeRagdoll(long handle) {
        try {
            ragdollPort.setModelPhysicsEnabled(handle, false);
            ragdollPort.setRagdollFrozen(handle, true);
        } catch (Throwable error) {
            LOGGER.warn("[Ragdoll] 冻结失败（原生库可能未重编）: handle={}", handle, error);
        }
    }
}
