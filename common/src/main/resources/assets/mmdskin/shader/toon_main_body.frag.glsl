#version 330 core

in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPos;

uniform sampler2D Sampler0;
uniform float LightIntensity;
uniform int ToonLevels;          // 色阶数量（2-5）
uniform float RimPower;          // 边缘光锐度
uniform float RimIntensity;      // 边缘光强度
uniform vec3 ShadowColor;        // 阴影色调
uniform float SpecularPower;     // 高光锐度
uniform float SpecularIntensity; // 高光强度

// MRT 多输出：兼容 Iris G-buffer FBO
// location 0 → colortex0（漫反射色 + alpha）
// location 1 → colortex1（编码法线）
// location 2 → colortex2（光照图 / 高光数据）
// location 3 → colortex3（保留）
layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragData1;
layout(location = 2) out vec4 fragData2;
layout(location = 3) out vec4 fragData3;
// 色阶化函数
float toonify(float value, int levels) {
    return floor(value * float(levels) + 0.5) / float(levels);
}
void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    vec3 normal = normalize(viewNormal);

    // 主光源方向（视图空间）
    vec3 lightDir = normalize(vec3(0.2, 1.0, -0.7));
    vec3 viewDir = normalize(-viewPos);

    // === 漫反射（色阶化） ===
    float NdotL = dot(normal, lightDir);
    float diffuse = max(NdotL, 0.0);
    float toonDiffuse = toonify(diffuse, ToonLevels);

    // === 高光（色阶化） ===
    vec3 halfDir = normalize(lightDir + viewDir);
    float NdotH = max(dot(normal, halfDir), 0.0);
    float specular = pow(NdotH, SpecularPower);
    // 卡通高光：硬边界
    float toonSpecular = step(0.5, specular) * SpecularIntensity;

    // === 边缘光（Rim Light） ===
    float rim = 1.0 - max(dot(viewDir, normal), 0.0);
    rim = pow(rim, RimPower) * RimIntensity;

    // === 阴影混合 ===
    // 亮部使用原色，暗部混合阴影色
    vec3 litColor = texColor.rgb;
    vec3 shadowedColor = texColor.rgb * ShadowColor;
    vec3 baseColor = mix(shadowedColor, litColor, toonDiffuse);

    // === 最终合成 ===
    vec3 finalColor = baseColor * LightIntensity;
    finalColor += toonSpecular * vec3(1.0);  // 高光（白色）
    finalColor += rim * texColor.rgb;         // 边缘光

    // 环境光保底
    float ambient = 0.15;
    finalColor = max(finalColor, texColor.rgb * ambient);

    if (texColor.a < 0.004) discard;

    // MRT 输出
    fragColor  = vec4(finalColor, texColor.a);          // 漫反射色
    fragData1  = vec4(normal * 0.5 + 0.5, 1.0);         // 编码法线 [-1,1]→[0,1]
    fragData2  = vec4(0.0, 0.0, 0.0, 1.0);              // 光照图占位
    fragData3  = vec4(0.0, 0.0, 0.0, 1.0);              // 保留
}
