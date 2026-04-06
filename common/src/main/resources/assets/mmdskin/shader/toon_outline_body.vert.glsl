#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform float OutlineWidth;

out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPos;

void main() {
    mat3 normalMatrix = mat3(ModelViewMat);
    vec3 transformedNormal = normalize(normalMatrix * Normal);
    vec4 vPos = ModelViewMat * vec4(Position, 1.0);

    float viewDepth = max(-vPos.z, 1.0);
    float outlineScale = mix(0.85, 1.80, clamp(viewDepth / 20.0, 0.0, 1.0));
    vPos.xyz += transformedNormal * (OutlineWidth * outlineScale);

    gl_Position = ProjMat * vPos;
    texCoord0 = UV0;
    viewNormal = transformedNormal;
    viewPos = vPos.xyz;
}
