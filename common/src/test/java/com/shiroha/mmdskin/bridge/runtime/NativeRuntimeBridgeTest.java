package com.shiroha.mmdskin.bridge.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 验证 TaCZ 端口在进入 JNI 之前的基础输入校验。 */
class NativeRuntimeBridgeTest {
    @Test
    void taczArmTargetPacketRequiresThirtyTwoFiniteFloats() {
        assertFalse(NativeRuntimeBridge.isValidTaczArmTargetPacket(null, 0b11));
        assertFalse(NativeRuntimeBridge.isValidTaczArmTargetPacket(new float[31], 0b11));

        float[] valid = twoIdentityMatrices();
        assertTrue(NativeRuntimeBridge.isValidTaczArmTargetPacket(valid, 0b11));
        assertTrue(NativeRuntimeBridge.isValidTaczArmTargetPacket(valid, 0b01));
        assertFalse(NativeRuntimeBridge.isValidTaczArmTargetPacket(valid, 0));

        valid[7] = Float.NaN;
        assertFalse(NativeRuntimeBridge.isValidTaczArmTargetPacket(valid, 0b11));
        valid[7] = Float.POSITIVE_INFINITY;
        assertFalse(NativeRuntimeBridge.isValidTaczArmTargetPacket(valid, 0b11));
    }

    private static float[] twoIdentityMatrices() {
        float[] matrices = new float[32];
        for (int offset : new int[]{0, 16}) {
            matrices[offset] = 1.0f;
            matrices[offset + 5] = 1.0f;
            matrices[offset + 10] = 1.0f;
            matrices[offset + 15] = 1.0f;
        }
        return matrices;
    }
}
