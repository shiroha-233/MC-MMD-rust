#version 330 core

in vec3 viewNormal;
in vec3 viewPos;

uniform vec3 OutlineColor;
// MRT 多输出：兼容 Iris G-buffer FBO
layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragData1;
layout(location = 2) out vec4 fragData2;
layout(location = 3) out vec4 fragData3;

void main() {
    // 计算视线方向（从片段指向相机）
    vec3 viewDir = normalize(-viewPos);
    vec3 normal = normalize(viewNormal);

    // 法线朝向相机的区域不应该有描边（凹陷区域）
    float NdotV = dot(normal, viewDir);
    if (NdotV > 0.1) {
        discard;
    }

    // MRT 输出
    fragColor  = vec4(OutlineColor, 1.0);               // 描边色
    fragData1  = vec4(normal * 0.5 + 0.5, 1.0);         // 编码法线
    fragData2  = vec4(0.0, 0.0, 0.0, 1.0);              // 光照图占位
    fragData3  = vec4(0.0, 0.0, 0.0, 1.0);              // 保留
}
