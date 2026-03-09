package com.shiroha.mmdskin.stage.client.camera.port;

import java.util.UUID;

public interface StageCameraBroadcastPort {
    void sendRemoteStageStop();

    void sendFrameSync(UUID sessionId, float frame);

    void sendLeave(UUID hostUUID);
}
