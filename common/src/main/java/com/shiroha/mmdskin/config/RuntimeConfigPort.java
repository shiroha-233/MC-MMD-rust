package com.shiroha.mmdskin.config;

/** 文件职责：向热点运行时路径暴露精简且可注入的配置视图。 */
public interface RuntimeConfigPort {
    RuntimeConfigPort DEFAULT = new RuntimeConfigPort() {
        @Override
        public boolean isPhysicsEnabled() {
            return true;
        }

        @Override
        public boolean isGpuSkinningEnabled() {
            return false;
        }

        @Override
        public boolean isMmdShaderEnabled() {
            return false;
        }

        @Override
        public boolean isFirstPersonModelEnabled() {
            return false;
        }

        @Override
        public float getFirstPersonCameraForwardOffset() {
            return 0.0f;
        }

        @Override
        public float getFirstPersonCameraVerticalOffset() {
            return 0.0f;
        }

        @Override
        public boolean isVrEnabled() {
            return false;
        }

        @Override
        public float getVrArmIkStrength() {
            return 1.0f;
        }
    };

    static RuntimeConfigPort defaults() {
        return DEFAULT;
    }

    static RuntimeConfigPort fromConfigManager() {
        return new RuntimeConfigPort() {
            @Override
            public boolean isPhysicsEnabled() {
                return ConfigManager.isPhysicsEnabled();
            }

            @Override
            public boolean isGpuSkinningEnabled() {
                return ConfigManager.isGpuSkinningEnabled();
            }

            @Override
            public boolean isMmdShaderEnabled() {
                return ConfigManager.isMMDShaderEnabled();
            }

            @Override
            public boolean isFirstPersonModelEnabled() {
                return ConfigManager.isFirstPersonModelEnabled();
            }

            @Override
            public float getFirstPersonCameraForwardOffset() {
                return ConfigManager.getFirstPersonCameraForwardOffset();
            }

            @Override
            public float getFirstPersonCameraVerticalOffset() {
                return ConfigManager.getFirstPersonCameraVerticalOffset();
            }

            @Override
            public boolean isVrEnabled() {
                return ConfigManager.isVREnabled();
            }

            @Override
            public float getVrArmIkStrength() {
                return ConfigManager.getVRArmIKStrength();
            }
        };
    }

    boolean isPhysicsEnabled();

    boolean isGpuSkinningEnabled();

    boolean isMmdShaderEnabled();

    boolean isFirstPersonModelEnabled();

    float getFirstPersonCameraForwardOffset();

    float getFirstPersonCameraVerticalOffset();

    boolean isVrEnabled();

    float getVrArmIkStrength();
}
