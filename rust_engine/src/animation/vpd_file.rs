//! VPD 文件加载器
//!
//! 解析 Vocaloid Pose Data 文件

use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;

use glam::{Vec3, Quat};

use crate::{MmdError, Result};

/// VPD 骨骼数据
#[derive(Debug, Clone)]
pub struct VpdBone {
    /// 骨骼名称
    pub name: String,
    /// 平移
    pub translation: Vec3,
    /// 旋转（四元数）
    pub rotation: Quat,
}

/// VPD Morph 数据
#[derive(Debug, Clone)]
pub struct VpdMorph {
    /// Morph 名称
    pub name: String,
    /// 权重
    pub weight: f32,
}

/// VPD 文件数据
#[derive(Debug, Clone)]
pub struct VpdFile {
    /// 模型名称
    pub model_name: String,
    /// 骨骼数据
    pub bones: Vec<VpdBone>,
    /// Morph 数据
    pub morphs: Vec<VpdMorph>,
}

impl VpdFile {
    /// 从文件路径加载 VPD
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let file = File::open(path.as_ref())
            .map_err(|e| MmdError::Io(e))?;
        let reader = BufReader::new(file);
        Self::load_from_reader(reader)
    }

    /// 从字节加载 VPD
    pub fn load_from_bytes(bytes: &[u8]) -> Result<Self> {
        let reader = BufReader::new(bytes);
        Self::load_from_reader(reader)
    }

    /// 从 Reader 加载 VPD
    fn load_from_reader<R: BufRead>(reader: R) -> Result<Self> {
        let mut model_name = String::new();
        let mut bones = Vec::new();
        let mut morphs = Vec::new();

        let mut lines = reader.lines();
        
        // 读取头部
        let header = lines.next()
            .ok_or_else(|| MmdError::VpdParse("Empty file".to_string()))?
            .map_err(|e| MmdError::VpdParse(format!("Failed to read header: {}", e)))?;
        
        if !header.contains("Vocaloid Pose Data file") {
            return Err(MmdError::VpdParse("Invalid VPD header".to_string()));
        }

        // 读取模型名称行
        if let Some(Ok(line)) = lines.next() {
            if let Some(name) = line.strip_suffix(';') {
                model_name = name.trim().to_string();
            }
        }

        // 跳过骨骼数量行
        lines.next();

        // 解析内容
        let mut current_bone_name: Option<String> = None;
        let mut current_values: Vec<f32> = Vec::new();
        let mut current_morph_index: Option<usize> = None;

        for line_result in lines {
            let line = match line_result {
                Ok(l) => l,
                Err(_) => continue,
            };
            
            let line = line.trim();
            if line.is_empty() || line.starts_with("//") {
                continue;
            }

            // 骨骼块开始: Bone0{ボーン名
            if line.starts_with("Bone") && line.contains('{') {
                if let Some(start) = line.find('{') {
                    let name = &line[start + 1..];
                    current_bone_name = Some(name.to_string());
                    current_morph_index = None;
                    current_values.clear();
                }
            }
            // Morph 块开始: Morph0{表情名
            else if line.starts_with("Morph") && line.contains('{') {
                if let Some(start) = line.find('{') {
                    let name = &line[start + 1..];
                    current_bone_name = None;
                    morphs.push(VpdMorph {
                        name: name.to_string(),
                        weight: 0.0,
                    });
                    current_morph_index = Some(morphs.len() - 1);
                }
            }
            // 数值行（骨骼块内）
            else if let Some(ref _name) = current_bone_name {
                // 解析数值 (格式: 0.1,0.2,0.3; 或类似)
                let clean = line.trim_end_matches(';').trim_end_matches('}');
                for part in clean.split(',') {
                    if let Ok(val) = part.trim().parse::<f32>() {
                        current_values.push(val);
                    }
                }
                
                // 当收集到 7 个值时（3 平移 + 4 旋转），创建骨骼
                if current_values.len() >= 7 {
                    if let Some(name) = current_bone_name.take() {
                        // 坐标系转换：Z 轴和 W 分量反转
                        let translation = Vec3::new(
                            current_values[0],
                            current_values[1],
                            -current_values[2],
                        );
                        let rotation = Quat::from_xyzw(
                            current_values[3],
                            current_values[4],
                            -current_values[5],
                            -current_values[6],
                        ).normalize();
                        
                        bones.push(VpdBone {
                            name,
                            translation,
                            rotation,
                        });
                    }
                    current_values.clear();
                }
            }
            // Morph 权重行（显式追踪当前 Morph 索引）
            else if let Some(morph_idx) = current_morph_index {
                let clean = line.trim_end_matches(';').trim_end_matches('}');
                if let Ok(weight) = clean.trim().parse::<f32>() {
                    if let Some(morph) = morphs.get_mut(morph_idx) {
                        morph.weight = weight;
                    }
                }
                current_morph_index = None;
            }
            // 块结束
            else if line.contains('}') {
                current_bone_name = None;
                current_morph_index = None;
                current_values.clear();
            }
        }

        Ok(Self {
            model_name,
            bones,
            morphs,
        })
    }
}
