//! VMD 文件写入器

use std::io::Write;
use std::fs::File;
use std::path::Path;

use byteorder::{LittleEndian, WriteBytesExt};

use crate::{MmdError, Result};
use super::motion::Motion;

const VMD_HEADER: &[u8; 30] = b"Vocaloid Motion Data 0002\0\0\0\0\0";

/// 将 Motion 数据写入 VMD 文件
pub fn write_vmd<P: AsRef<Path>>(path: P, model_name: &str, motion: &Motion) -> Result<()> {
    let file = File::create(path.as_ref())
        .map_err(|e| MmdError::Io(e))?;
    let mut w = std::io::BufWriter::new(file);
    write_vmd_to(&mut w, model_name, motion)
}

/// 将 Motion 数据写入任意 Write 目标
pub fn write_vmd_to<W: Write>(w: &mut W, model_name: &str, motion: &Motion) -> Result<()> {
    // 文件头 (30 字节)
    w.write_all(VMD_HEADER)
        .map_err(|e| MmdError::Io(e))?;

    // 模型名 (20 字节, Shift-JIS)
    let name_bytes = encode_shift_jis(model_name);
    write_fixed_bytes(w, &name_bytes, 20)?;

    // 骨骼关键帧
    let mut bone_count: u32 = 0;
    for track in motion.bone_tracks.values() {
        bone_count += track.keyframes.len() as u32;
    }
    w.write_u32::<LittleEndian>(bone_count).map_err(|e| MmdError::Io(e))?;

    for (name, track) in &motion.bone_tracks {
        let name_bytes = encode_shift_jis(name);
        for kf in track.keyframes.values() {
            // 骨骼名 (15 字节)
            write_fixed_bytes(w, &name_bytes, 15)?;
            // 帧索引
            w.write_u32::<LittleEndian>(kf.frame_index).map_err(|e| MmdError::Io(e))?;
            // 平移：运行时读取做了 z = -tz，写入时要还原
            w.write_f32::<LittleEndian>(kf.translation.x).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(kf.translation.y).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(-kf.translation.z).map_err(|e| MmdError::Io(e))?;
            // 旋转：vmd_loader 读取时会执行 (rx, ry, -rz, -rw)
            // 为保证 round-trip 后内部四元数一致，这里写出时对 z/w 取反
            w.write_f32::<LittleEndian>(kf.orientation.x).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(kf.orientation.y).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(-kf.orientation.z).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(-kf.orientation.w).map_err(|e| MmdError::Io(e))?;
            // 插值参数 (64 字节)
            let mut interp = [0u8; 64];
            interp[0] = kf.interpolation_x[0];
            interp[4] = kf.interpolation_x[1];
            interp[8] = kf.interpolation_x[2];
            interp[12] = kf.interpolation_x[3];
            interp[1] = kf.interpolation_y[0];
            interp[5] = kf.interpolation_y[1];
            interp[9] = kf.interpolation_y[2];
            interp[13] = kf.interpolation_y[3];
            interp[2] = kf.interpolation_z[0];
            interp[6] = kf.interpolation_z[1];
            interp[10] = kf.interpolation_z[2];
            interp[14] = kf.interpolation_z[3];
            interp[3] = kf.interpolation_r[0];
            interp[7] = kf.interpolation_r[1];
            interp[11] = kf.interpolation_r[2];
            interp[15] = kf.interpolation_r[3];
            w.write_all(&interp).map_err(|e| MmdError::Io(e))?;
        }
    }

    // Morph 关键帧
    let mut morph_count: u32 = 0;
    for track in motion.morph_tracks.values() {
        morph_count += track.keyframes.len() as u32;
    }
    w.write_u32::<LittleEndian>(morph_count).map_err(|e| MmdError::Io(e))?;

    for (name, track) in &motion.morph_tracks {
        let name_bytes = encode_shift_jis(name);
        for kf in track.keyframes.values() {
            write_fixed_bytes(w, &name_bytes, 15)?;
            w.write_u32::<LittleEndian>(kf.frame_index).map_err(|e| MmdError::Io(e))?;
            w.write_f32::<LittleEndian>(kf.weight).map_err(|e| MmdError::Io(e))?;
        }
    }

    // 相机关键帧 (0)
    w.write_u32::<LittleEndian>(0).map_err(|e| MmdError::Io(e))?;
    // 光照 (0)
    w.write_u32::<LittleEndian>(0).map_err(|e| MmdError::Io(e))?;
    // 阴影 (0)
    w.write_u32::<LittleEndian>(0).map_err(|e| MmdError::Io(e))?;

    // IK 关键帧
    if motion.ik_tracks.is_empty() {
        w.write_u32::<LittleEndian>(0).map_err(|e| MmdError::Io(e))?;
    } else {
        // 收集所有 IK 帧号
        let mut frame_set = std::collections::BTreeSet::new();
        for track in motion.ik_tracks.values() {
            for kf in track.keyframes.values() {
                frame_set.insert(kf.frame_index);
            }
        }
        w.write_u32::<LittleEndian>(frame_set.len() as u32).map_err(|e| MmdError::Io(e))?;

        let ik_names: Vec<&String> = motion.ik_tracks.keys().collect();
        for &frame in &frame_set {
            // 帧索引
            w.write_u32::<LittleEndian>(frame).map_err(|e| MmdError::Io(e))?;
            // 显示标志 (1=显示)
            w.write_u8(1).map_err(|e| MmdError::Io(e))?;
            // IK 信息数量
            w.write_u32::<LittleEndian>(ik_names.len() as u32).map_err(|e| MmdError::Io(e))?;

            for ik_name in &ik_names {
                let ik_name_bytes = encode_shift_jis(ik_name);
                write_fixed_bytes(w, &ik_name_bytes, 20)?;
                let enabled = motion.is_ik_enabled(ik_name, frame);
                w.write_u8(if enabled { 1 } else { 0 }).map_err(|e| MmdError::Io(e))?;
            }
        }
    }

    w.flush().map_err(|e| MmdError::Io(e))?;
    Ok(())
}

/// Shift-JIS 编码（UTF-8 → Shift-JIS）
fn encode_shift_jis(s: &str) -> Vec<u8> {
    let (encoded, _, _) = encoding_rs::SHIFT_JIS.encode(s);
    encoded.into_owned()
}

/// 写入固定长度字节（不足补 0，超出截断）
fn write_fixed_bytes<W: Write>(w: &mut W, data: &[u8], len: usize) -> Result<()> {
    if data.len() >= len {
        w.write_all(&data[..len]).map_err(|e| MmdError::Io(e))?;
    } else {
        w.write_all(data).map_err(|e| MmdError::Io(e))?;
        let pad = vec![0u8; len - data.len()];
        w.write_all(&pad).map_err(|e| MmdError::Io(e))?;
    }
    Ok(())
}
