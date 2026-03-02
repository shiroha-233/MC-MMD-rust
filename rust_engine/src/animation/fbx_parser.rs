//! FBX 二进制格式解析器

use std::io::{Read, Seek, SeekFrom};
use byteorder::{LittleEndian, ReadBytesExt};
use flate2::read::ZlibDecoder;
use crate::{MmdError, Result};

const FBX_MAGIC: &[u8] = b"Kaydara FBX Binary  \x00";
const FBX_MAGIC_LEN: usize = 21;
const FBX_HEADER_LEN: usize = 27;

/// FBX 节点
#[derive(Debug, Clone)]
pub struct FbxNode {
    pub name: String,
    pub properties: Vec<FbxProperty>,
    pub children: Vec<FbxNode>,
}

/// FBX 属性值
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub enum FbxProperty {
    Bool(bool),
    I16(i16),
    I32(i32),
    I64(i64),
    F32(f32),
    F64(f64),
    String(String),
    Binary(Vec<u8>),
    BoolArray(Vec<bool>),
    I32Array(Vec<i32>),
    I64Array(Vec<i64>),
    F32Array(Vec<f32>),
    F64Array(Vec<f64>),
}

impl FbxProperty {
    pub fn as_i64(&self) -> Option<i64> {
        match self {
            FbxProperty::I64(v) => Some(*v),
            FbxProperty::I32(v) => Some(*v as i64),
            _ => None,
        }
    }

    pub fn as_f64(&self) -> Option<f64> {
        match self {
            FbxProperty::F64(v) => Some(*v),
            FbxProperty::F32(v) => Some(*v as f64),
            FbxProperty::I64(v) => Some(*v as f64),
            FbxProperty::I32(v) => Some(*v as f64),
            _ => None,
        }
    }

    pub fn as_string(&self) -> Option<&str> {
        match self {
            FbxProperty::String(s) => Some(s),
            _ => None,
        }
    }

    pub fn as_i64_array(&self) -> Option<&[i64]> {
        match self {
            FbxProperty::I64Array(v) => Some(v),
            _ => None,
        }
    }

    pub fn as_f32_array(&self) -> Option<&[f32]> {
        match self {
            FbxProperty::F32Array(v) => Some(v),
            _ => None,
        }
    }

    pub fn as_f64_array(&self) -> Option<&[f64]> {
        match self {
            FbxProperty::F64Array(v) => Some(v),
            _ => None,
        }
    }
}

impl FbxNode {
    /// 按名称查找第一个子节点
    pub fn find_child(&self, name: &str) -> Option<&FbxNode> {
        self.children.iter().find(|c| c.name == name)
    }

    /// 按名称查找所有子节点
    #[allow(dead_code)]
    pub fn find_children<'a>(&'a self, name: &'a str) -> impl Iterator<Item = &'a FbxNode> + 'a {
        self.children.iter().filter(move |c| c.name == name)
    }
}

/// 解析 FBX 二进制文件，返回顶层节点列表
pub fn parse_fbx<R: Read + Seek>(reader: &mut R) -> Result<Vec<FbxNode>> {
    let mut header = [0u8; FBX_HEADER_LEN];
    reader.read_exact(&mut header)
        .map_err(|e| MmdError::FbxParse(format!("读取头部失败: {}", e)))?;

    if &header[..FBX_MAGIC_LEN] != FBX_MAGIC {
        return Err(MmdError::FbxParse("无效的 FBX 文件头".into()));
    }

    let version = u32::from_le_bytes([header[23], header[24], header[25], header[26]]);
    let use_64bit = version >= 7500;

    let mut nodes = Vec::new();
    loop {
        match parse_node(reader, use_64bit)? {
            Some(node) => nodes.push(node),
            None => break,
        }
    }
    Ok(nodes)
}

/// 解析单个 FBX 节点（递归）
fn parse_node<R: Read + Seek>(reader: &mut R, use_64bit: bool) -> Result<Option<FbxNode>> {
    let (end_offset, num_properties, property_list_len, name_len) = if use_64bit {
        let end = reader.read_u64::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let num = reader.read_u64::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let pll = reader.read_u64::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let nl = reader.read_u8()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        (end as u64, num as u32, pll as u64, nl)
    } else {
        let end = reader.read_u32::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let num = reader.read_u32::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let pll = reader.read_u32::<LittleEndian>()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        let nl = reader.read_u8()
            .map_err(|e| MmdError::FbxParse(format!("读取节点头失败: {}", e)))?;
        (end as u64, num, pll as u64, nl)
    };

    // 空记录 = 节点列表结束
    if end_offset == 0 {
        return Ok(None);
    }

    let mut name_buf = vec![0u8; name_len as usize];
    reader.read_exact(&mut name_buf)
        .map_err(|e| MmdError::FbxParse(format!("读取节点名失败: {}", e)))?;
    let name = String::from_utf8_lossy(&name_buf).into_owned();

    // 解析属性
    let prop_start = reader.stream_position()
        .map_err(|e| MmdError::FbxParse(format!("获取位置失败: {}", e)))?;
    let mut properties = Vec::with_capacity(num_properties as usize);
    for _ in 0..num_properties {
        properties.push(parse_property(reader)?);
    }
    // 跳过可能的属性填充
    let prop_end = prop_start + property_list_len;
    reader.seek(SeekFrom::Start(prop_end))
        .map_err(|e| MmdError::FbxParse(format!("跳转失败: {}", e)))?;

    // 解析子节点
    let mut children = Vec::new();
    let current = reader.stream_position()
        .map_err(|e| MmdError::FbxParse(format!("获取位置失败: {}", e)))?;
    if current < end_offset {
        loop {
            let pos = reader.stream_position()
                .map_err(|e| MmdError::FbxParse(format!("获取位置失败: {}", e)))?;
            if pos >= end_offset {
                break;
            }
            match parse_node(reader, use_64bit)? {
                Some(child) => children.push(child),
                None => break,
            }
        }
    }

    // 确保读到 end_offset
    reader.seek(SeekFrom::Start(end_offset))
        .map_err(|e| MmdError::FbxParse(format!("跳转到节点末尾失败: {}", e)))?;

    Ok(Some(FbxNode { name, properties, children }))
}

/// 解析单个属性
fn parse_property<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let type_code = reader.read_u8()
        .map_err(|e| MmdError::FbxParse(format!("读取属性类型失败: {}", e)))?;

    match type_code {
        b'C' => {
            let v = reader.read_u8()
                .map_err(|e| MmdError::FbxParse(format!("读取 bool 失败: {}", e)))?;
            Ok(FbxProperty::Bool(v != 0))
        }
        b'Y' => {
            let v = reader.read_i16::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取 i16 失败: {}", e)))?;
            Ok(FbxProperty::I16(v))
        }
        b'I' => {
            let v = reader.read_i32::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取 i32 失败: {}", e)))?;
            Ok(FbxProperty::I32(v))
        }
        b'L' => {
            let v = reader.read_i64::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取 i64 失败: {}", e)))?;
            Ok(FbxProperty::I64(v))
        }
        b'F' => {
            let v = reader.read_f32::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取 f32 失败: {}", e)))?;
            Ok(FbxProperty::F32(v))
        }
        b'D' => {
            let v = reader.read_f64::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取 f64 失败: {}", e)))?;
            Ok(FbxProperty::F64(v))
        }
        b'S' => {
            let len = reader.read_u32::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取字符串长度失败: {}", e)))? as usize;
            let mut buf = vec![0u8; len];
            reader.read_exact(&mut buf)
                .map_err(|e| MmdError::FbxParse(format!("读取字符串失败: {}", e)))?;
            Ok(FbxProperty::String(String::from_utf8_lossy(&buf).into_owned()))
        }
        b'R' => {
            let len = reader.read_u32::<LittleEndian>()
                .map_err(|e| MmdError::FbxParse(format!("读取二进制长度失败: {}", e)))? as usize;
            let mut buf = vec![0u8; len];
            reader.read_exact(&mut buf)
                .map_err(|e| MmdError::FbxParse(format!("读取二进制数据失败: {}", e)))?;
            Ok(FbxProperty::Binary(buf))
        }
        b'b' => parse_bool_array(reader),
        b'i' => parse_i32_array(reader),
        b'l' => parse_i64_array(reader),
        b'f' => parse_f32_array(reader),
        b'd' => parse_f64_array(reader),
        _ => Err(MmdError::FbxParse(format!("未知属性类型: 0x{:02X}", type_code))),
    }
}

/// 读取数组头部，返回解压后的原始字节
fn read_array_data<R: Read>(reader: &mut R, element_size: usize) -> Result<Vec<u8>> {
    let array_len = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::FbxParse(format!("读取数组长度失败: {}", e)))? as usize;
    let encoding = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::FbxParse(format!("读取编码类型失败: {}", e)))?;
    let compressed_len = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::FbxParse(format!("读取压缩长度失败: {}", e)))? as usize;

    let expected_size = array_len * element_size;

    if encoding == 0 {
        let mut buf = vec![0u8; expected_size];
        reader.read_exact(&mut buf)
            .map_err(|e| MmdError::FbxParse(format!("读取数组数据失败: {}", e)))?;
        Ok(buf)
    } else if encoding == 1 {
        let mut compressed = vec![0u8; compressed_len];
        reader.read_exact(&mut compressed)
            .map_err(|e| MmdError::FbxParse(format!("读取压缩数据失败: {}", e)))?;
        let mut decoder = ZlibDecoder::new(&compressed[..]);
        let mut decompressed = vec![0u8; expected_size];
        decoder.read_exact(&mut decompressed)
            .map_err(|e| MmdError::FbxParse(format!("zlib 解压失败: {}", e)))?;
        Ok(decompressed)
    } else {
        Err(MmdError::FbxParse(format!("未知数组编码: {}", encoding)))
    }
}

fn parse_bool_array<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let data = read_array_data(reader, 1)?;
    Ok(FbxProperty::BoolArray(data.into_iter().map(|b| b != 0).collect()))
}

fn parse_i32_array<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let data = read_array_data(reader, 4)?;
    let values = data.chunks_exact(4)
        .map(|c| i32::from_le_bytes([c[0], c[1], c[2], c[3]]))
        .collect();
    Ok(FbxProperty::I32Array(values))
}

fn parse_i64_array<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let data = read_array_data(reader, 8)?;
    let values = data.chunks_exact(8)
        .map(|c| i64::from_le_bytes([c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]]))
        .collect();
    Ok(FbxProperty::I64Array(values))
}

fn parse_f32_array<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let data = read_array_data(reader, 4)?;
    let values = data.chunks_exact(4)
        .map(|c| f32::from_le_bytes([c[0], c[1], c[2], c[3]]))
        .collect();
    Ok(FbxProperty::F32Array(values))
}

fn parse_f64_array<R: Read>(reader: &mut R) -> Result<FbxProperty> {
    let data = read_array_data(reader, 8)?;
    let values = data.chunks_exact(8)
        .map(|c| f64::from_le_bytes([c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]]))
        .collect();
    Ok(FbxProperty::F64Array(values))
}
