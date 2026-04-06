#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform vec3 LightDir;

out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPos;
out vec3 viewLightDir;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    mat3 normalMatrix = mat3(ModelViewMat);

    viewNormal = normalize(normalMatrix * Normal);
    viewLightDir = normalize(normalMatrix * normalize(LightDir));
    viewPos = viewPosition.xyz;
    texCoord0 = UV0;

    gl_Position = ProjMat * viewPosition;
}
