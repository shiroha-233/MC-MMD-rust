package com.shiroha.mmdskin.stage.client.camera;

/** 文件职责：承载舞台相机计算后的不可变姿态数据。 */
record StageCameraPose(double x, double y, double z, float pitch, float yaw, float roll, float fov) {
}
