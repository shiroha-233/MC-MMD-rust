// 负责把 Fabric 相机入口适配到舞台相机与实例化第一人称相机模块。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import com.shiroha.mmdskin.player.ragdoll.RagdollManager;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract Entity entity();

    @Shadow
    public abstract boolean isDetached();

    @Shadow
    public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    /**
     * 相机朝向四元数。
     *
     * <p>26.3 的 {@code Camera} 没有 roll 字段/方法，镜头横滚只能叠加在这个四元数上。
     * {@code Camera.rotation()} 返回的就是字段本身（不是副本），所以可以直接就地左乘。
     */
    @Shadow
    public abstract Quaternionf rotation();

    /**
     * 相机上方向基向量（世界空间）。横滚会改变它，必须跟着 {@link #rotation()} 一起转，
     * 否则 {@code Camera.upVector()} 会和相机朝向自相矛盾。
     */
    @Shadow
    @Final
    private Vector3f up;

    /**
     * 相机左方向基向量（世界空间）。它就是 {@code -right}：
     * {@code Camera} 的静态常量是 {@code FORWARDS=(0,0,-1)}、{@code UP=(0,1,0)}、
     * {@code LEFT=(-1,0,0)}，{@code left} 字段是它们经相机四元数旋转后的结果。
     */
    @Shadow
    @Final
    private Vector3f left;

    /**
     * 26.3：旧的 {@code Camera.setup(BlockGetter, Entity, boolean, boolean, float)} 已被拆成
     * {@code update(DeltaTracker)}——相机位姿在 {@code alignWithEntity} 里算好，
     * 随后同一个方法里依次算 FOV、视旋转矩阵、裁剪视锥体和投影矩阵。
     *
     * <p>所以注入点选在 {@code alignWithEntity} 之后（而不是整个方法的 TAIL）：
     * 被覆盖的 position/rotation 之后还会被 {@code prepareCullFrustum} 和
     * {@code setupPerspective} 用到，不会出现"相机在别处、裁剪还在原地"的错位。
     */
    @Inject(
            method = "update",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER))
    private void onCameraAligned(DeltaTracker deltaTracker, CallbackInfo callback) {
        // 死亡观战相机接管（优先级最高：高于舞台相机与第一人称 MMD 相机）。
        // 26.3 里 Camera.setup 已被拆成 update(DeltaTracker)，alignWithEntity 之后
        // 才 setPosition/setRotation，这里覆盖后 prepareCullFrustum / setupPerspective
        // 用的都是新的相机位姿，不会出现"相机在别处、裁剪还在原地"的错位。
        double[] deathCam = RagdollManager.getInstance().deathCamTarget();
        if (deathCam != null) {
            setPosition(deathCam[0], deathCam[1], deathCam[2]);
            setRotation((float) deathCam[3], (float) deathCam[4]);
            // 死亡观战相机优先级高于舞台相机：这里直接返回，横滚不会被施加。
            return;
        }

        Entity entity = this.entity();
        if (entity == null) {
            return;
        }
        float partialTick = this.getCameraEntityPartialTicks(deltaTracker);
        boolean detached = this.isDetached();

        MMDCameraController stageCamera = MMDCameraController.getInstance();
        if (stageCamera.isActive()) {
            stageCamera.checkEscapeKey();
            if (stageCamera.isActive()) {
                stageCamera.updateCamera();
                if (stageCamera.isActive()) {
                    setPosition(stageCamera.getCameraX(), stageCamera.getCameraY(), stageCamera.getCameraZ());
                    setRotation(stageCamera.getCameraYaw(), stageCamera.getCameraPitch());
                    // 舞台相机横滚：必须紧跟在 setRotation 之后叠加——
                    // setRotation 会把 rotation 整个覆写成 yaw/pitch 四元数，提前叠加会被冲掉；
                    // 而 alignWithEntity 之后这个位置又早于 Camera.update 里的 getViewRotationMatrix，
                    // 视图矩阵会用带 roll 的四元数重建。
                    applyStageCameraRoll(stageCamera.getCameraRoll());
                }
            }
            return;
        }

        if (entity instanceof LivingEntity living
                && YsmCompat.isYsmModelActive(living)
                && !YsmCompat.isDisableSelfModel()) {
            return;
        }
        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        runtime.firstPerson().camera()
                .resolve(entity, partialTick, detached)
                .ifPresent(pose -> {
                    setPosition(pose.position().x, pose.position().y, pose.position().z);
                    if (pose.applyRotation()) {
                        setRotation(pose.yaw(), pose.pitch());
                    }
                });
    }

    /**
     * 把舞台相机的镜头横滚（Roll）叠加到当前相机位姿上。
     *
     * <p>为什么改 {@link #rotation()}，而不是另开一个 {@code GameRendererMixin} 去转渲染位姿：
     * 26.3 的世界视图矩阵完全由 {@code Camera.rotation()} 派生——
     * {@code Camera.update} 里 {@code getViewRotationMatrix} 用
     * {@code rotation().conjugate()} 重建视图矩阵，
     * {@code Camera.extractRenderState} 再把同一个四元数抄进
     * {@code CameraRenderState.orientation}，
     * {@code LevelRenderer.render} 最后用
     * {@code CameraRenderState.viewRotationMatrix} 乘进 modelViewStack。
     * 只改这一处，视图矩阵、相机朝向、视锥剔除（{@code Camera.prepareCullFrustum}
     * 用的就是同一个视图矩阵）、区块剔除会同时带上 roll；
     * 如果只在渲染层单独转一次位姿，画面滚了而剔除锥没滚，屏幕边缘的方块/实体
     * 会被错误剔除（闪烁、缺面）。
     *
     * <p>符号约定：native 侧（{@code rust_engine/src/animation/motion_track.rs}）算出的
     * {@code mc_roll} 是"绕相机前向轴、让 up 轴倒向 right 轴"的右手旋转角，
     * 因此朝向四元数要左乘 {@code rotationZ(-θ)}（相机局部 +Z 是朝后方向），
     * up/left 两个基向量按同一个世界空间旋转同步更新。
     *
     * <p>{@code forwards} 是旋转轴自身，绕前向轴旋转不会改变它，所以刻意不碰它——
     * {@code Camera.getNearPlane} 依赖它算近裁剪面。
     *
     * <p>单位：{@code MMDCameraController.getCameraRoll()} 返回角度（控制器里做过
     * {@code Math.toDegrees}），而 JOML 的四元数接口一律用弧度，必须先转换。
     */
    private void applyStageCameraRoll(float rollDegrees) {
        if (rollDegrees == 0.0f) {
            // 没有横滚时一个字节都不改，保证行为与移植前完全一致。
            return;
        }
        float theta = (float) Math.toRadians(rollDegrees);
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);

        // up 倒向 right（right = -left），left 反向同步；两个基向量必须用同一组旧值算。
        float upX = up.x;
        float upY = up.y;
        float upZ = up.z;
        float leftX = left.x;
        float leftY = left.y;
        float leftZ = left.z;
        up.set(upX * cos - leftX * sin, upY * cos - leftY * sin, upZ * cos - leftZ * sin);
        left.set(leftX * cos + upX * sin, leftY * cos + upY * sin, leftZ * cos + upZ * sin);

        // 朝向四元数：rotationYXZ 里相机局部 +Z 是朝后方向，所以"绕前向轴右滚"
        // 等价于"绕局部 +Z 转 -θ"。
        rotation().mul(new Quaternionf().rotationZ(-theta));
    }

    /**
     * 舞台模式接管 FOV。
     *
     * <p>26.3 里 FOV 不再是逐值 uniform：{@code Camera.update} 先调用
     * {@code calculateFov(partialTick)} 把结果写进 {@code Camera.fov} 字段，
     * 再在同一个方法里把这个字段交给 {@code setupPerspective} 建投影矩阵。
     * 因此要接管真正的 3D 视角，必须改 {@code calculateFov} 的返回值——
     * 只改 {@code getFov()} 只会影响 HUD（投影矩阵用的是字段，不是 getFov()）。
     */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void onCalculateFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue(controller.getCameraFov());
        }
    }
}
