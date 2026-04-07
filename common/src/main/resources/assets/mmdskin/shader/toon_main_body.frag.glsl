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
    float softness = mix(0.14, 0.05, clamp((float(ToonLevels) - 2.0) / 3.0, 0.0, 1.0));
    float toonDiffuse = softBand(diffuse * 0.96 + 0.02, bands, softness);
    float litMask = smoothstep(0.68, 0.90, diffuse);

    vec3 halfDir = normalize(lightDir + viewDir);
    float specular = pow(saturate(dot(normal, halfDir)), max(SpecularPower, 1.0));
    float highlightThreshold = mix(0.88, 0.975, clamp(SpecularPower / 128.0, 0.0, 1.0));
    float specularBand = smoothstep(highlightThreshold - 0.012, highlightThreshold + 0.015, specular);

    float fresnel = 1.0 - saturate(dot(viewDir, normal));
    float rim = pow(fresnel, max(RimPower, 0.0001));
    rim = smoothstep(0.82, 0.97, rim) * RimIntensity;
    rim *= (1.0 - litMask * 0.8);

    vec3 shadowedColor = albedo * ShadowColor;
    vec3 baseColor = mix(shadowedColor, albedo, toonDiffuse);

    float ambient = 0.16;
    vec3 finalColor = baseColor * max(LightIntensity, ambient);
    finalColor = max(finalColor, albedo * ambient);

    float toonSpecular = specularBand * SpecularIntensity * litMask * (1.0 - rim * 0.75);
    vec3 specularColor = vec3(1.0);
    vec3 rimColor = mix(vec3(1.0), ShadowColor, 0.55);

    finalColor += specularColor * (toonSpecular * 0.35);
    finalColor += albedo * rimColor * rim;

    fragColor = vec4(finalColor, texColor.a);
    fragData1 = vec4(normal * 0.5 + 0.5, 1.0);
    fragData2 = vec4(0.0, 0.0, 0.0, 1.0);
    fragData3 = vec4(0.0, 0.0, 0.0, 1.0);
}
