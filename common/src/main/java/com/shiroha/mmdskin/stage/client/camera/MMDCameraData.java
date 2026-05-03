package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 负责读取并缓存 JNI 返回的 MMD 相机帧数据。 */
public class MMDCameraData {
    private static final NativeAnimationPort NOOP_ANIMATION_PORT = new NativeAnimationPort() {
        @Override
        public long loadAnimation(long modelHandle, String animationPath) {
            return 0L;
        }

        @Override
        public void deleteAnimation(long animationHandle) {
        }

        @Override
        public void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle) {
        }

        @Override
        public boolean hasCameraData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasBoneData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasMorphData(long animationHandle) {
            return false;
        }

        @Override
        public float getAnimationMaxFrame(long animationHandle) {
            return 0.0f;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, ByteBuffer targetBuffer) {
        }
    };

    private final ByteBuffer buffer;

    private final Vector3f position = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private float fov = 30.0f;
    private boolean perspective = true;

    private long animHandle;
    private NativeAnimationPort animationPort = NOOP_ANIMATION_PORT;
    
    public MMDCameraData() {
        this.buffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
    }
    
    public void setAnimHandle(long animHandle) {
        this.animHandle = animHandle;
    }

    public void setAnimationPort(NativeAnimationPort animationPort) {
        this.animationPort = animationPort != null ? animationPort : NOOP_ANIMATION_PORT;
    }

    public void update(float frame) {
        if (animHandle == 0) return;

        animationPort.getCameraTransform(animHandle, frame, buffer);
        
        buffer.rewind();
        float px = buffer.getFloat();
        float py = buffer.getFloat();
        float pz = buffer.getFloat();
        float rx = buffer.getFloat();
        float ry = buffer.getFloat();
        float rz = buffer.getFloat();
        fov = buffer.getFloat();
        int isPerspective = buffer.getInt();
        
        position.set(px, py, pz);
        rotation.set(rx, ry, rz);
        perspective = isPerspective != 0;
    }
    
    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public float getFov() {
        return fov;
    }

    public boolean isPerspective() {
        return perspective;
    }

    public float getPitch() {
        return rotation.x;
    }

    public float getYaw() {
        return rotation.y;
    }

    public float getRoll() {
        return rotation.z;
    }
}
