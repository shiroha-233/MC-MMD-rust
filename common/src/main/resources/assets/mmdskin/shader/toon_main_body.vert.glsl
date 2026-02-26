#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPos;

void main() {
    // Position 和 Normal 已经是蒙皮后的数据（由 Rust 引擎计算）
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);

    mat3 normalMatrix = mat3(ModelViewMat);
    vec3 transformedNormal = normalMatrix * Normal;

    gl_Position = ProjMat * viewPosition;
    texCoord0 = UV0;
    viewNormal = normalize(transformedNormal);
    viewPos = viewPosition.xyz;
}
