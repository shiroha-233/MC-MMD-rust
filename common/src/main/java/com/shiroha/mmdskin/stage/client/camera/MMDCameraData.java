package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.NativeFunc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 负责读取并缓存 JNI 返回的 MMD 相机帧数据。 */
public class MMDCameraData {

    private final ByteBuffer buffer;

    private final Vector3f position = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private float fov = 30.0f;
    private boolean perspective = true;

    private long animHandle;
    
    public MMDCameraData() {
        this.buffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
    }
    
    public void setAnimHandle(long animHandle) {
        this.animHandle = animHandle;
    }

    public void update(float frame) {
        if (animHandle == 0) return;
        
        NativeFunc.GetInst().GetCameraTransform(animHandle, frame, buffer);
        
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
