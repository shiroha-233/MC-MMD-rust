#version 330
// 文件职责：计算 MMD 实体主通道、Toon、描边与发光片元颜色。
// 26.2 适配：uniform 改为 std140 UBO 块，雾使用原版 apply_fog（Fog 块）。

#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler0;

// 与 mmd_entity.vsh 的 MmdModel 块保持完全相同的成员顺序与 std140 布局
layout(std140) uniform MmdModel {
    mat4 MmdModelMat;
    vec3 MmdLight0Direction;
    vec3 MmdLight1Direction;
    float MmdOutlineWidth;
    vec4 MmdMaterialColor;
    float MmdAlpha;
    float MmdLightFactor;
    float MmdEmission;
    vec3 MmdOutlineColor;
    int MmdToonLevels;
    vec3 MmdToonShadowColor;
    float MmdRimPower;
    float MmdRimIntensity;
    float MmdSpecularPower;
    float MmdSpecularIntensity;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPosition;
in vec3 viewLightDirection;
in float outlineFade;

out vec4 fragColor;

void main() {
#ifdef MMD_OUTLINE
    vec4 texel = texture(Sampler0, texCoord0);
    float luminance = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
    vec4 color = vec4(MmdOutlineColor * mix(1.0, luminance, 0.4),
        texel.a * MmdAlpha * outlineFade);
#else
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
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
