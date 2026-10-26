#version 330
#extension GL_ARB_separate_shader_objects : require

// 文件职责：把 CPU 或 palette GPU 蒙皮后的 MMD 顶点转换到实体渲染空间。
// MC 26.3 规范：uniform 全部改为 std140 uniform block，include 语法为 #include <minecraft:xxx.glsl>。

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:projection.glsl>

#ifdef MMD_GPU_SKINNING
// DefaultVertexFormat.ENTITY: Position, Color, UV0, UV1, UV2, Normal
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;
#else
// DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL: Position, UV0, Color, Normal
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;
#endif

// MMD 自有 uniform 块：成员顺序必须与 MmdGpuDrawDevice 的 Std140Builder 写入顺序逐项一致，
// std140 自然对齐后各成员偏移为 0/64/128/144/160/164/168/172/176/180/184/188/192/208/224，块大小 240 字节。
// 视图矩阵（原 vanilla DynamicTransforms 的 ModelViewMat）放在这里，
// 这样 MMD 就完全不再占用 vanilla 动态变换槽位（那会撑爆 per-frame 槽位并触发原生崩溃）。
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

#ifdef MMD_GPU_SKINNING
// GPU 蒙皮骨骼调色板：48 个 mat4，每个槽位 3072 字节。
// 注意：块名必须叫 MmdBones（Java 侧按这个名字绑定），块内成员不能重名，
// GLSL 不允许块名与成员名相同，故成员用 BoneMatrices。
layout(std140) uniform MmdBones {
    mat4 BoneMatrices[48];
};
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out vec3 viewNormal;
layout(location = 5) out vec3 viewPosition;
layout(location = 6) out vec3 viewLightDirection;
layout(location = 7) out float outlineFade;

void main() {
    vec3 skinPosition = Position;
    vec3 skinNormal = Normal;
    vec4 lightColor = Color;
#ifdef MMD_GPU_SKINNING
    mat4 skinMatrix = BoneMatrices[UV1.x] * Color.r
        + BoneMatrices[UV1.y] * Color.g
        + BoneMatrices[UV2.x] * Color.b
        + BoneMatrices[UV2.y] * Color.a;
    skinPosition = (skinMatrix * vec4(Position, 1.0)).xyz;
    skinNormal = normalize(mat3(skinMatrix) * Normal);
    lightColor = vec4(1.0);
#endif
    vec3 transformedNormal = normalize(mat3(MmdModelMat) * skinNormal);
    vec4 modelPosition = MmdModelMat * vec4(skinPosition, 1.0);
    vec4 baseViewPosition = MmdViewMat * modelPosition;
    float viewDepth = length(baseViewPosition.xyz);
    outlineFade = 1.0 - smoothstep(25.0, 40.0, viewDepth);
#ifdef MMD_OUTLINE
    float distanceWidth = mix(0.8, 1.2, smoothstep(0.0, 25.0, viewDepth));
    modelPosition.xyz += transformedNormal * MmdOutlineWidth * distanceWidth;
#endif
    vec4 viewPos = MmdViewMat * modelPosition;
    gl_Position = ProjMat * viewPos;

    // 26.3 的雾拆成球面/柱面两个距离；沿用 1.21.5 的视空间位置，保证与旧版视觉一致。
    sphericalVertexDistance = fog_spherical_distance(viewPos.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPos.xyz);

    vertexColor = minecraft_mix_light(
        Light0_Direction,
        Light1_Direction,
        transformedNormal,
        lightColor
    );
    texCoord0 = UV0;
    viewNormal = normalize(mat3(MmdViewMat) * transformedNormal);
    viewPosition = viewPos.xyz;
    viewLightDirection = normalize(mat3(MmdViewMat) * vec3(1.0, 0.75, 0.0));
}
