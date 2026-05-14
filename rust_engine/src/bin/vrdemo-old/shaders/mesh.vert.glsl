struct PushConstants {
    mvp: mat4x4<f32>,
    diffuse: vec4<f32>,
    params: vec4<f32>,
};

var<push_constant> pc: PushConstants;

struct VertexInput {
    @location(0) position: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) normal: vec3<f32>,
    @location(1) uv: vec2<f32>,
};

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = pc.mvp * vec4<f32>(input.position, 1.0);
    out.normal = input.normal;
    out.uv = input.uv;
    return out;
}
