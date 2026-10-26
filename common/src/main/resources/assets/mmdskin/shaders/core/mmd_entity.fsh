#version 330
#extension GL_ARB_separate_shader_objects : require

// 文件职责：计算 MMD 实体主通道、Toon、描边与发光片元颜色。
// MC 26.3 规范：uniform 全部改为 std140 uniform block，include 语法为 #include <minecraft:xxx.glsl>。

#include <minecraft:fog.glsl>

uniform sampler2D Sampler0;

// MMD 自有 uniform 块：成员顺序必须与 MmdGpuDrawDevice 的 Std140Builder 写入顺序逐项一致，
// std140 自然对齐后各成员偏移为 0/64/128/144/160/164/168/172/176/180/184/188/192/208/224，块大小 240 字节。
layout(std140) uniform MmdUniforms {
    mat4 MmdModelMat;
    mat4 MmdViewMat;
    vec4 MmdMaterialColor;
    vec4 MmdColorModulator;
    float MmdAlpha;
    float MmdLightFactor;
    float MmdOutlineWidth;
    float MmdEmission;
    int MmdToonLevels;
    float MmdRimPower;
    float MmdRimIntensity;
    float MmdSpecularPower;
    float MmdSpecularIntensity;
    vec3 MmdOutlineColor;
    vec3 MmdToonShadowColor;
};

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 viewNormal;
layout(location = 5) in vec3 viewPosition;
layout(location = 6) in vec3 viewLightDirection;
layout(location = 7) in float outlineFade;

layout(location = 0) out vec4 fragColor;

void main() {
#ifdef MMD_OUTLINE
    vec4 texel = texture(Sampler0, texCoord0);
    float luminance = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
    vec4 color = vec4(MmdOutlineColor * mix(1.0, luminance, 0.4),
        texel.a * MmdAlpha * outlineFade);
#else
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * MmdColorModulator;
    color.rgb *= MmdMaterialColor.rgb;
    color.rgb *= mix(MmdLightFactor, 1.0, MmdEmission);
    color.a *= MmdAlpha;
#ifdef MMD_TOON
    vec3 normal = normalize(viewNormal);
    vec3 viewDirection = normalize(-viewPosition);
    vec3 lightDirection = normalize(viewLightDirection);
    float levels = max(float(MmdToonLevels), 2.0);
    float halfLambert = clamp(dot(normal, lightDirection) * 0.5 + 0.5, 0.0, 1.0);
    float scaledBand = halfLambert * (levels - 1.0);
    float bandBase = floor(scaledBand);
    float lightBand = (bandBase + smoothstep(0.42, 0.58, fract(scaledBand))) / (levels - 1.0);
    vec3 coldShadow = mix(MmdToonShadowColor, vec3(0.55, 0.65, 0.85), 0.2);
    vec3 albedo = color.rgb;
    color.rgb = albedo * mix(coldShadow, vec3(1.0), lightBand);

    float shadowMask = 1.0 - smoothstep(0.45, 0.7, halfLambert);
    float rim = pow(1.0 - max(dot(normal, viewDirection), 0.0), MmdRimPower)
        * MmdRimIntensity * shadowMask;
    vec3 halfDirection = normalize(lightDirection + viewDirection);
    float specularRaw = pow(max(dot(normal, halfDirection), 0.0), MmdSpecularPower);
    float specular = smoothstep(0.45, 0.55, specularRaw) * MmdSpecularIntensity
        * smoothstep(0.45, 0.7, halfLambert);
    color.rgb += albedo * rim + vec3(specular);
#endif
#ifdef MMD_EMISSIVE
    color.rgb = max(color.rgb, texture(Sampler0, texCoord0).rgb * MmdMaterialColor.rgb);
#endif
#endif
    if (color.a <= 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
