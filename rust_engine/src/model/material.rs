//! MMD 材质定义

use glam::{Vec3, Vec4};

/// MMD 材质
#[derive(Clone, Debug)]
pub struct MmdMaterial {
    pub name: String,
    pub diffuse: Vec4,
    pub specular: Vec3,
    pub specular_strength: f32,
    pub ambient: Vec3,
    pub edge_color: Vec4,
    pub edge_scale: f32,
    pub texture_index: i32,
    pub environment_index: i32,
    pub toon_index: i32,
    pub draw_flags: u8,
}

impl MmdMaterial {
    /// 是否双面渲染
    pub fn is_double_sided(&self) -> bool {
        (self.draw_flags & 0x01) != 0
    }
    
    /// 是否投射地面阴影 (GroundShadow, bit 1)
    pub fn ground_shadow(&self) -> bool {
        (self.draw_flags & 0x02) != 0
    }
    
    /// 是否投射到阴影贴图 (DrawShadow, bit 2)
    pub fn draws_shadow(&self) -> bool {
        (self.draw_flags & 0x04) != 0
    }
    
    /// 是否接收阴影 (ReceiveShadow, bit 3)
    pub fn receives_shadow(&self) -> bool {
        (self.draw_flags & 0x08) != 0
    }
    
    /// 是否绘制边缘
    pub fn has_edge(&self) -> bool {
        (self.draw_flags & 0x10) != 0
    }
}

impl Default for MmdMaterial {
    fn default() -> Self {
        Self {
            name: String::new(),
            diffuse: Vec4::new(1.0, 1.0, 1.0, 1.0),
            specular: Vec3::new(0.0, 0.0, 0.0),
            specular_strength: 0.0,
            ambient: Vec3::new(0.5, 0.5, 0.5),
            edge_color: Vec4::new(0.0, 0.0, 0.0, 1.0),
            edge_scale: 1.0,
            texture_index: -1,
            environment_index: -1,
            toon_index: -1,
            draw_flags: 0,
        }
    }
}
