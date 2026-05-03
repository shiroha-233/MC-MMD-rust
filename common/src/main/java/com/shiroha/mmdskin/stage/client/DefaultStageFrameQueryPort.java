package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.camera.port.StageFrameQueryPort;

import java.util.Objects;
import java.util.function.Supplier;

/** 文件职责：把舞台当前帧查询适配为同步层可用的 port。 */
public final class DefaultStageFrameQueryPort implements StageFrameQueryPort {
    public static final DefaultStageFrameQueryPort INSTANCE =
            new DefaultStageFrameQueryPort(MMDCameraController::getInstance);

    private final Supplier<MMDCameraController> controllerSupplier;

    public DefaultStageFrameQueryPort(Supplier<MMDCameraController> controllerSupplier) {
        this.controllerSupplier = Objects.requireNonNull(controllerSupplier, "controllerSupplier");
    }

    @Override
    public boolean isStagePresentationActive() {
        return controllerSupplier.get().isActive();
    }

    @Override
    public float getCurrentFrame() {
        return controllerSupplier.get().getCurrentFrame();
    }
}
