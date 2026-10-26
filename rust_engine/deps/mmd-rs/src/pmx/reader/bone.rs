use crate::{
  pmx::bone::*,
  reader::{helpers::ReadHelpers, MaterialReader},
  Config, DefaultConfig, Error, Result, Settings,
};
use byteorder::{ReadBytesExt, LE};
use enumflags2::BitFlags;
use std::marker::PhantomData;
// `use std::{io::Read, usize};` 里的 `usize` 走的是 std 的 **usize 模块**路径，
// 而该模块在 Rust 1.99 起被标记 deprecated。本 crate 顶部有 `#![deny(warnings)]`，
// 于是新工具链上直接编译失败（CI 用 rustc 1.99.1 时报
// "use of deprecated module `std::usize`"）。
// 这里根本不需要它 —— 文件里用到的 `usize` 都是 primitive 类型（`as usize` /
// `-> usize`），primitive 在 prelude 里本来就可用。
use std::io::Read;

pub struct BoneReader<R> {
  pub settings: Settings,
  pub count: i32,
  pub remaining: i32,
  pub(crate) read: R,
  pub(crate) poison: bool,
}

impl<R: Read> BoneReader<R> {
  pub fn new(mut m: MaterialReader<R>) -> Result<BoneReader<R>> {
    assert!(!m.poison);
    while m.remaining > 0 {
      m.next::<DefaultConfig>()?;
    }
    let count = m.read.read_i32::<LE>()?;

    Ok(BoneReader {
      settings: m.settings,
      count,
      remaining: count,
      read: m.read,
      poison: false,
    })
  }

  pub fn next<C: Config>(&mut self) -> Result<Option<Bone<C>>> {
    assert!(!self.poison);
    let result = self.next_impl::<C>();
    if result.is_err() {
      self.poison = true;
    }
    result
  }

  fn next_impl<C: Config>(&mut self) -> Result<Option<Bone<C>>> {
    if self.remaining <= 0 {
      return Ok(None);
    }

    self.remaining -= 1;

    let local_name = self.read.read_text(self.settings.text_encoding)?;
    let universal_name = self.read.read_text(self.settings.text_encoding)?;
    let position = self.read.read_vec3::<C>()?;
    let parent = self.read.read_index(self.settings.bone_index_size)?;
    let transform_level = self.read.read_i32::<LE>()?;
    let bone_flags = BitFlags::from_bits(self.read.read_u16::<LE>()?).unwrap();

    let connection = bone_flags
      .contains(BoneFlags::Connection)
      .then(|| {
        Ok::<_, Error>(Connection::Index(
          self.read.read_index(self.settings.bone_index_size)?,
        ))
      })
      .unwrap_or_else(|| Ok::<_, Error>(Connection::Position(self.read.read_vec3::<C>()?)))?;

    let additional = bone_flags
      .intersects(BoneFlags::AddRotation | BoneFlags::AddMovement)
      .then(|| {
        Ok::<_, Error>(Additional {
          parent: self.read.read_index(self.settings.bone_index_size)?,
          rate: self.read.read_f32::<LE>()?,
        })
      })
      .transpose()?;

    let fixed_axis = bone_flags
      .contains(BoneFlags::FixedAxis)
      .then(|| self.read.read_vec3::<C>())
      .transpose()?;

    let local_axis = bone_flags
      .contains(BoneFlags::LocalAxis)
      .then(|| {
        Ok::<_, Error>(LocalAxis {
          x: self.read.read_vec3::<C>()?,
          z: self.read.read_vec3::<C>()?,
        })
      })
      .transpose()?;

    let external_parent_transform = bone_flags
      .contains(BoneFlags::ExternalParentTransform)
      .then(|| self.read.read_i32::<LE>())
      .transpose()?;

    let inverse_kinematics = if bone_flags.contains(BoneFlags::InverseKinematics) {
      let ik_bone = self.read.read_index(self.settings.bone_index_size)?;
      let iterations = self.read.read_u32::<LE>()?;
      let limit_angle = self.read.read_f32::<LE>()?;
      let link_count = self.read.read_u32::<LE>()? as usize;
      let mut links = Vec::with_capacity(link_count);
      for _i in 0..link_count {
        let ik_bone = self
          .read
          .read_index::<C::BoneIndex>(self.settings.bone_index_size)?;
        let limits = if self.read.read_u8()? != 0 {
          Some((self.read.read_vec3::<C>()?, self.read.read_vec3::<C>()?))
        } else {
          None
        };
        links.push(IKLink { ik_bone, limits })
      }

      Some(InverseKinematics {
        ik_bone,
        iterations,
        limit_angle,
        links,
      })
    } else {
      None
    };

    Ok(Some(Bone {
      local_name,
      universal_name,
      position,
      parent,
      transform_level,
      bone_flags,
      connection,
      additional,
      fixed_axis,
      local_axis,
      external_parent_transform,
      inverse_kinematics,
    }))
  }

  pub fn iter<C>(&mut self) -> BoneIterator<'_, R, C> {
    BoneIterator {
      reader: self,
      phantom: PhantomData,
    }
  }
}

pub struct BoneIterator<'a, R, C = DefaultConfig> {
  reader: &'a mut BoneReader<R>,
  phantom: PhantomData<C>,
}

impl<R: Read, C: Config> Iterator for BoneIterator<'_, R, C> {
  type Item = Result<Bone<C>>;

  fn next(&mut self) -> Option<Self::Item> {
    self
      .reader
      .next()
      .map_or_else(|e| Some(Err(e)), |v| v.map(Ok))
  }

  fn size_hint(&self) -> (usize, Option<usize>) {
    (
      self.reader.remaining as usize,
      Some(self.reader.remaining as usize),
    )
  }
}

impl<R: Read, C: Config> ExactSizeIterator for BoneIterator<'_, R, C> {
  fn len(&self) -> usize {
    self.reader.remaining as usize
  }
}
