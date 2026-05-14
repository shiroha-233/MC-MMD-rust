struct PushConstants {
    mvp: mat4x4<f32>,
    diffuse: vec4<f32>,
    params: vec4<f32>,
};

var<push_constant> pc: PushConstants;

@group(0) @binding(0)
var tex_sampler: sampler;

@group(0) @binding(1)
var tex: texture_2d<f32>;

struct FragmentInput {
    @location(0) normal: vec3<f32>,
    @location(1) uv: vec2<f32>,
};

@fragment
fn main(input: FragmentInput) -> @location(0) vec4<f32> {
    let tex_color = textureSample(tex, tex_sampler, input.uv);
    let base = tex_color * pc.diffuse;
    if (pc.params.w > 0.0 && base.a < pc.params.w) {
        discard;
    }
    if (base.a < 0.01) {
        discard;
    }

    let light_dir = normalize(pc.params.xyz);
    let normal = normalize(input.normal);
    var ndotl = dot(normal, light_dir);
    if (ndotl < 0.0) {
        ndotl = -ndotl * 0.35;
    }

    let ambient = 0.72;
    let diffuse_term = ndotl * 0.38;
    let lit = base.rgb * (ambient + diffuse_term);
    return vec4<f32>(lit, base.a);
}
