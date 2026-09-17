#version 330
// 文件职责：把 CPU 或 palette GPU 蒙皮后的 MMD 顶点转换到实体渲染空间。
// 26.2 适配：uniform 全部改为 std140 UBO 块（MmdTransforms/MmdModel/MmdBones），
// 灯光方向自包含于 MmdModel 块，雾使用原版 Fog 块（RenderSystem.setShaderFog）。

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
#ifdef MMD_GPU_SKINNING
in ivec2 UV1;
in ivec2 UV2;
#endif

layout(std140) uniform MmdTransforms {
    mat4 ModelViewMat;
    mat4 ProjMat;
};

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

#ifdef MMD_GPU_SKINNING
layout(std140) uniform MmdBones {
    mat4 MmdBonesArray[48];
};
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPosition;
out vec3 viewLightDirection;
out float outlineFade;

// 复刻原版 light.glsl 的光照公式（自包含，不依赖原版 Lighting UBO）
#define MMEDGE_LIGHT_POWER (0.6)
#define MMEDGE_AMBIENT_LIGHT (0.4)

vec2 mmd_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {
    return vec2(dot(lightDir0, normal), dot(lightDir1, normal));
}

vec4 mmd_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    vec2 light = mmd_compute_light(lightDir0, lightDir1, normal);
    vec2 lightValue = max(vec2(0.0), light);
    float lightAccum = min(1.0, (lightValue.x + lightValue.y) * MMEDGE_LIGHT_POWER + MMEDGE_AMBIENT_LIGHT);
    return vec4(color.rgb * lightAccum, color.a);
}

void main() {
    vec3 skinPosition = Position;
    vec3 skinNormal = Normal;
    vec4 lightColor = Color;
#ifdef MMD_GPU_SKINNING
    mat4 skinMatrix = MmdBonesArray[UV1.x] * Color.r
        + MmdBonesArray[UV1.y] * Color.g
        + MmdBonesArray[UV2.x] * Color.b
        + MmdBonesArray[UV2.y] * Color.a;
    skinPosition = (skinMatrix * vec4(Position, 1.0)).xyz;
    skinNormal = normalize(mat3(skinMatrix) * Normal);
    lightColor = vec4(1.0);
#endif
    vec3 transformedNormal = normalize(mat3(MmdModelMat) * skinNormal);
    vec4 modelPosition = MmdModelMat * vec4(skinPosition, 1.0);
    vec4 baseViewPosition = ModelViewMat * modelPosition;
    float viewDepth = length(baseViewPosition.xyz);
    outlineFade = 1.0 - smoothstep(25.0, 40.0, viewDepth);
#ifdef MMD_OUTLINE
    float distanceWidth = mix(0.8, 1.2, smoothstep(0.0, 25.0, viewDepth));
    modelPosition.xyz += transformedNormal * MmdOutlineWidth * distanceWidth;
#endif
    vec4 viewPos = ModelViewMat * modelPosition;
    gl_Position = ProjMat * viewPos;
    sphericalVertexDistance = fog_spherical_distance(viewPos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos.xyz);
    vertexColor = mmd_mix_light(
        MmdLight0Direction,
        MmdLight1Direction,
        transformedNormal,
        lightColor
    );
    texCoord0 = UV0;
    viewNormal = normalize(mat3(ModelViewMat) * transformedNormal);
    viewPosition = viewPos.xyz;
    viewLightDirection = normalize(mat3(ModelViewMat) * vec3(1.0, 0.75, 0.0));
}
