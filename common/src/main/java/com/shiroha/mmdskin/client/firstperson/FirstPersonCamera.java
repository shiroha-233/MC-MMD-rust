// 负责依据第一人称会话快照计算相机、交互眼位与视线方向。
package com.shiroha.mmdskin.client.firstperson;

import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class FirstPersonCamera {
    private final FirstPersonSession session;

    public FirstPersonCamera(FirstPersonSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public Optional<CameraPose> resolve(Entity entity, float partialTick, boolean detached) {
        if (detached || !isLocalCameraEntity(entity) || !session.eyeCameraActive()) {
            return Optional.empty();
        }
        if (session.vrEyePassActive()) {
            Vec3 position = resolveVrEyePosition(entity, partialTick);
            return Optional.of(new CameraPose(position, entity.getViewYRot(partialTick),
                    entity.getViewXRot(partialTick), false));
        }
        if (!session.eyeBoneValid()) {
            return Optional.empty();
        }
        float yaw = entity.getViewYRot(partialTick);
        float pitch = entity.getViewXRot(partialTick);
        Vec3 eye = rotatedEyePosition(entity, partialTick);
        Vec3 position = applyViewOffsets(
                eye,
                yaw,
                pitch,
                ConfigManager.getFirstPersonCameraForwardOffset(),
                ConfigManager.getFirstPersonCameraVerticalOffset());
        return Optional.of(new CameraPose(position, yaw, pitch, true));
    }

    public Optional<Vec3> resolveInteractionEyePosition(Entity entity, float partialTick, Camera camera) {
        if (!isLocalCameraEntity(entity) || !session.eyeCameraActive()) {
            return Optional.empty();
        }
        if (!session.vrEyePassActive() && !session.eyeBoneValid()) {
            return Optional.empty();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON
                && camera.isInitialized() && camera.entity() == entity) {
            return Optional.of(camera.position());
        }
        if (session.vrEyePassActive()) {
            return Optional.of(resolveVrEyePosition(entity, partialTick));
        }
        return Optional.of(rotatedEyePosition(entity, partialTick));
    }

    public Optional<Vec3> resolveViewVector(Entity entity, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isLocalCameraEntity(entity) || !session.eyeCameraActive()
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON
                || !camera.isInitialized() || camera.entity() != entity) {
            return Optional.empty();
        }
        return Optional.of(viewVector(camera.yRot(), camera.xRot()));
    }

    public boolean shouldValidateVanillaReach(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return session.desktopActive()
                && session.eyeBoneValid()
                && minecraft.player != null
                && minecraft.player.getUUID().equals(entity.getUUID())
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    public Vec3 vanillaEyePosition(LivingEntity entity, float partialTick) {
        Vec3 origin = interpolatedOrigin(entity, partialTick);
        return origin.add(0.0D, entity.getEyeHeight(), 0.0D);
    }

    public Vec3 rotatedEyePosition(Entity entity, float partialTick) {
        Vec3 origin = interpolatedOrigin(entity, partialTick);
        float bodyYaw = entity instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        return rotateEyeOffset(origin, bodyYaw, session.scaledEyeBoneOffset());
    }

    private Vec3 resolveVrEyePosition(Entity entity, float partialTick) {
        if (!(entity instanceof Player player)) {
            return rotatedEyePosition(entity, partialTick);
        }
        Vec3 head = session.vrRuntime().getWorldRenderHeadPosition(player);
        if (head != null) {
            return head;
        }
        Vec3 origin = session.renderOrigin(player, partialTick)
                .add(session.localVrModelRootOffset(player));
        return rotateEyeOffset(origin, session.bodyYawDegrees(player, partialTick),
                session.scaledEyeBoneOffset());
    }

    private static boolean isLocalCameraEntity(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity != null && minecraft.player != null
                && minecraft.player.getUUID().equals(entity.getUUID());
    }

    static Vec3 interpolatedOrigin(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    static Vec3 rotateEyeOffset(Vec3 origin, float bodyYawDegrees, Vec3 offset) {
        double radians = Math.toRadians(bodyYawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;
        return origin.add(x, offset.y, z);
    }

    static Vec3 applyViewOffsets(Vec3 eye, float yawDegrees, float pitchDegrees,
                                 double forward, double vertical) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        double rotatedY = vertical * Math.cos(pitch) - forward * Math.sin(pitch);
        double horizontal = vertical * Math.sin(pitch) + forward * Math.cos(pitch);
        return eye.add(-Math.sin(yaw) * horizontal, rotatedY, Math.cos(yaw) * horizontal);
    }

    static Vec3 viewVector(float yawDegrees, float pitchDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(-yawDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch, boolean applyRotation) {
        public CameraPose {
            position = Objects.requireNonNull(position, "position");
        }
    }
}
