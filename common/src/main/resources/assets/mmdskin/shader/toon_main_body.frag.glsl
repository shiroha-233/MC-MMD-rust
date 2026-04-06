#version 330 core

in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPos;
in vec3 viewLightDir;

uniform sampler2D Sampler0;
uniform float LightIntensity;
uniform int ToonLevels;
uniform float RimPower;
uniform float RimIntensity;
uniform vec3 ShadowColor;
uniform float SpecularPower;
uniform float SpecularIntensity;
uniform float AlphaCutoff;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragData1;
layout(location = 2) out vec4 fragData2;
layout(location = 3) out vec4 fragData3;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float softBand(float value, float bands, float softness) {
    value = saturate(value);
    float scaled = value * bands;
    float lower = floor(scaled);
    float fraction = fract(scaled);
    float blend = smoothstep(0.5 - softness, 0.5 + softness, fraction);
    return clamp((lower + blend) / bands, 0.0, 1.0);
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < AlphaCutoff) {
        discard;
    }

    vec3 albedo = texColor.rgb;
    vec3 normal = normalize(viewNormal);
    vec3 lightDir = normalize(viewLightDir);
    vec3 viewDir = normalize(-viewPos);

    float diffuse = saturate(dot(normal, lightDir));
    float bands = float(max(ToonLevels, 2));
    float softness = mix(0.20, 0.08, clamp((float(ToonLevels) - 2.0) / 3.0, 0.0, 1.0));
    float toonDiffuse = softBand(diffuse, bands, softness);
    toonDiffuse = max(toonDiffuse, diffuse * 0.28 + 0.08);

    vec3 halfDir = normalize(lightDir + viewDir);
    float specular = pow(saturate(dot(normal, halfDir)), max(SpecularPower, 1.0));
    float highlightThreshold = mix(0.55, 0.85, clamp(SpecularPower / 128.0, 0.0, 1.0));
    float toonSpecular = smoothstep(highlightThreshold - 0.08, highlightThreshold + 0.08, specular)
            * SpecularIntensity;

    float rim = 1.0 - saturate(dot(viewDir, normal));
    rim = pow(rim, max(RimPower, 0.0001));
    rim = smoothstep(0.20, 1.0, rim) * RimIntensity;

    vec3 shadowedColor = albedo * mix(ShadowColor, vec3(1.0), 0.18);
    vec3 baseColor = mix(shadowedColor, albedo, toonDiffuse);

    float ambient = 0.18;
    vec3 finalColor = baseColor * max(LightIntensity, ambient);
    finalColor = max(finalColor, albedo * ambient);
    finalColor += vec3(toonSpecular);
    finalColor += albedo * rim;

    fragColor = vec4(finalColor, texColor.a);
    fragData1 = vec4(normal * 0.5 + 0.5, 1.0);
    fragData2 = vec4(0.0, 0.0, 0.0, 1.0);
    fragData3 = vec4(0.0, 0.0, 0.0, 1.0);
}
