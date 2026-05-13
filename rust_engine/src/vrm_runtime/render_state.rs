pub enum VrmView {
    Hmd,
    Mirror,
}

pub struct VrmRenderState<'a> {
    pub positions: &'a [f32],
    pub normals: &'a [f32],
    pub uvs: &'a [f32],
    pub visible_materials: &'a [bool],
}
