#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform float OutlineWidth;

out vec3 viewNormal;
out vec3 viewPos;

void main() {
    // Position 和 Normal 已经是蒙皮后的数据
    mat3 normalMatrix = mat3(ModelViewMat);
    vec3 transformedNormal = normalize(normalMatrix * Normal);

    // 沿法线方向扩张顶点（背面扩张法）
    vec4 vPos = ModelViewMat * vec4(Position, 1.0);
    vPos.xyz += transformedNormal * OutlineWidth;

    gl_Position = ProjMat * vPos;
    viewNormal = transformedNormal;
    viewPos = vPos.xyz;
}
