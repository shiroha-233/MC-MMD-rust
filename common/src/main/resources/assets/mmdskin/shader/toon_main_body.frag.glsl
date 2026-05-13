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

vec3 shiftHueTowardCool(vec3 color, float strength) {
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 coolTint = vec3(luminance * 0.85, luminance * 0.88, luminance * 1.12);
    return mix(color, coolTint, strength);
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
    float shadowEdge = 0.5;
    float edgeSoftness = mix(0.28, 0.12, clamp((bands - 2.0) / 3.0, 0.0, 1.0));

    float toonRamp;
    if (bands <= 2.0) {
        toonRamp = smoothstep(shadowEdge - edgeSoftness, shadowEdge + edgeSoftness, halfLambert);
    } else {
        float scaled = halfLambert * (bands - 1.0);
        float step = floor(scaled);
        float frac = fract(scaled);
        float blend = smoothstep(0.5 - edgeSoftness, 0.5 + edgeSoftness, frac);
        toonRamp = saturate((step + blend) / (bands - 1.0));
    }

    vec3 shadowTint = shiftHueTowardCool(albedo * ShadowColor, 0.35);
    vec3 litColor = albedo;
    vec3 baseColor = mix(shadowTint, litColor, toonRamp);

    float ambient = 0.32;
    float lightFactor = max(LightIntensity, ambient);
    vec3 finalColor = baseColor * lightFactor;
    finalColor = max(finalColor, albedo * ambient * 0.6);

    vec3 halfDir = normalize(lightDir + viewDir);
    float specAngle = saturate(dot(normal, halfDir));
    float specular = pow(specAngle, max(SpecularPower, 1.0));
    float specThreshold = mix(0.75, 0.92, clamp(SpecularPower / 64.0, 0.0, 1.0));
    float specBand = smoothstep(specThreshold - 0.04, specThreshold + 0.04, specular);
    float specMask = smoothstep(0.4, 0.7, halfLambert);
    finalColor += vec3(1.0) * specBand * SpecularIntensity * specMask * 0.4;

    float fresnel = 1.0 - saturate(dot(viewDir, normal));
    float rim = pow(fresnel, max(RimPower, 0.5));
    float rimEdge = smoothstep(0.6, 0.85, rim);
    float rimMask = mix(1.0, 0.3, toonRamp);
    vec3 rimColor = mix(albedo, vec3(1.0), 0.5);
    finalColor += rimColor * rimEdge * RimIntensity * rimMask * 0.5;

    fragColor = vec4(finalColor, texColor.a);
    fragData1 = vec4(normal * 0.5 + 0.5, 1.0);
    fragData2 = vec4(0.0, 0.0, 0.0, 1.0);
    fragData3 = vec4(0.0, 0.0, 0.0, 1.0);
}
