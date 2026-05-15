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

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < AlphaCutoff) {
        discard;
    }

    vec3 albedo = texColor.rgb;
    vec3 normal = normalize(viewNormal);
    vec3 lightDir = normalize(viewLightDir);
    vec3 viewDir = normalize(-viewPos);

    float NdotL = dot(normal, lightDir);
    float halfLambert = NdotL * 0.5 + 0.5;

    float bands = float(max(ToonLevels, 2));
    float edgeSoftness = mix(0.08, 0.04, clamp((bands - 2.0) / 3.0, 0.0, 1.0));

    float toonRamp;
    if (bands <= 2.0) {
        toonRamp = smoothstep(0.5 - edgeSoftness, 0.5 + edgeSoftness, halfLambert);
    } else {
        float scaled = halfLambert * (bands - 1.0);
        float s = floor(scaled);
        float f = fract(scaled);
        float blend = smoothstep(0.5 - edgeSoftness, 0.5 + edgeSoftness, f);
        toonRamp = saturate((s + blend) / (bands - 1.0));
    }

    vec3 shadowColor = albedo * ShadowColor;
    float shadowLum = dot(shadowColor, vec3(0.299, 0.587, 0.114));
    vec3 coolShift = vec3(shadowLum * 0.85, shadowLum * 0.88, shadowLum * 1.1);
    vec3 shadowTint = mix(shadowColor, coolShift, 0.2);

    vec3 baseColor = mix(shadowTint, albedo, toonRamp);

    float ambient = 0.2;
    float lightFactor = max(LightIntensity, ambient);
    vec3 finalColor = baseColor * lightFactor;
    finalColor = max(finalColor, albedo * ambient * 0.5);

    vec3 halfDir = normalize(lightDir + viewDir);
    float specAngle = saturate(dot(normal, halfDir));
    float specular = pow(specAngle, max(SpecularPower, 1.0));
    float specThreshold = mix(0.82, 0.96, clamp(SpecularPower / 96.0, 0.0, 1.0));
    float specBand = smoothstep(specThreshold - 0.02, specThreshold + 0.02, specular);
    float specMask = step(0.5, halfLambert);
    finalColor += vec3(1.0) * specBand * SpecularIntensity * specMask * 0.5;

    float fresnel = 1.0 - saturate(dot(viewDir, normal));
    float rim = pow(fresnel, max(RimPower, 1.0));
    float rimEdge = smoothstep(0.7, 0.9, rim);
    float rimMask = 1.0 - toonRamp;
    finalColor += albedo * rimEdge * RimIntensity * rimMask * 0.35;

    fragColor = vec4(finalColor, texColor.a);
    fragData1 = vec4(normal * 0.5 + 0.5, 1.0);
    fragData2 = vec4(0.0, 0.0, 0.0, 1.0);
    fragData3 = vec4(0.0, 0.0, 0.0, 1.0);
}
