package com.shiroha.mmdskin.bridge.runtime;

/** Native capability for externally driven per-frame MMD bone transforms. */
public interface NativeBoneOverridePort {
    NativeBoneOverridePort NOOP = new NativeBoneOverridePort() {
        @Override
        public boolean setBoneOverride(long modelHandle, int boneIndex,
                                       float tx, float ty, float tz,
                                       float qx, float qy, float qz, float qw) {
            return false;
        }

        @Override
        public boolean setBoneOverrideByName(long modelHandle, String boneName,
                                             float tx, float ty, float tz,
                                             float qx, float qy, float qz, float qw) {
            return false;
        }

        @Override
        public void clearBoneOverrides(long modelHandle) {
        }

        @Override
        public int setBoneOverrideBatch(long modelHandle, int[] boneIndices, float[] transforms) {
            return 0;
        }

        @Override
        public void setExternalIkOverride(long modelHandle, String ikName, boolean enabled) {
        }

        @Override
        public void clearExternalIkOverrides(long modelHandle) {
        }
    };

    static NativeBoneOverridePort noop() {
        return NOOP;
    }

    boolean setBoneOverride(long modelHandle, int boneIndex,
                            float tx, float ty, float tz,
                            float qx, float qy, float qz, float qw);

    boolean setBoneOverrideByName(long modelHandle, String boneName,
                                  float tx, float ty, float tz,
                                  float qx, float qy, float qz, float qw);

    void clearBoneOverrides(long modelHandle);

    int setBoneOverrideBatch(long modelHandle, int[] boneIndices, float[] transforms);

    void setExternalIkOverride(long modelHandle, String ikName, boolean enabled);

    void clearExternalIkOverrides(long modelHandle);
}
