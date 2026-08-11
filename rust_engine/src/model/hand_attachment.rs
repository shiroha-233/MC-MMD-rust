use crate::skeleton::BoneManager;

/// 查找模型作者提供的手持挂点；显式挂点优先于 MMD 常见 Dummy 别名。
pub(crate) fn find_hand_attachment(
    bones: &BoneManager,
    explicit_name: &str,
    dummy_side: char,
) -> Option<usize> {
    bones.find_bone_by_name(explicit_name).or_else(|| {
        (0..bones.bone_count()).find(|&index| {
            bones
                .get_bone(index)
                .is_some_and(|bone| is_dummy_attachment_name(&bone.name, dummy_side))
        })
    })
}

fn is_dummy_attachment_name(name: &str, side: char) -> bool {
    // 一部分较早的 MMD 模型使用“右ダミー/左ダミー”，而不是英文侧别后缀。
    if matches!((name, side), ("右ダミー", 'R') | ("左ダミー", 'L')) {
        return true;
    }

    let Some(suffix) = name.strip_prefix("ダミー") else {
        return false;
    };
    suffix.trim_matches(|character: char| {
        character == '_' || character == '.' || character.is_whitespace()
    }) == side.to_string()
}

/// 将 TaCZ 主手 VMD 的通用右挂点轨道映射到模型实际使用的 Dummy 名称。
pub(crate) fn find_vmd_attachment_target(bones: &BoneManager, track_name: &str) -> Option<usize> {
    is_right_attachment_track(track_name)
        .then(|| find_hand_attachment(bones, "Hand_Attach_R", 'R'))
        .flatten()
}

/// 同一 VMD 可能同时保存多种右挂点别名；只允许优先级最高的轨道重定向。
pub(crate) fn is_preferred_vmd_attachment_track<'a>(
    track_name: &str,
    all_track_names: impl Iterator<Item = &'a String>,
) -> bool {
    if !is_right_attachment_track(track_name) {
        return false;
    }
    let current_priority = right_attachment_track_priority(track_name);
    all_track_names
        .filter(|name| is_right_attachment_track(name))
        .all(|name| right_attachment_track_priority(name) >= current_priority)
}

fn right_attachment_track_priority(name: &str) -> usize {
    if name == "Hand_Attach_R" {
        0
    } else if name == "右ダミー" {
        1
    } else {
        2
    }
}

fn is_right_attachment_track(name: &str) -> bool {
    name == "Hand_Attach_R" || name == "右ダミー" || is_dummy_attachment_name(name, 'R')
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::skeleton::BoneLink;

    #[test]
    fn accepts_common_separators_and_rejects_the_opposite_side() {
        for name in ["ダミー_R", "ダミー.R", "ダミー R", "右ダミー"] {
            let mut bones = BoneManager::new();
            bones.add_bone(BoneLink::new(name.to_string()));
            assert_eq!(find_hand_attachment(&bones, "Hand_Attach_R", 'R'), Some(0));
        }

        let mut bones = BoneManager::new();
        bones.add_bone(BoneLink::new("ダミー_L".to_string()));
        assert_eq!(find_hand_attachment(&bones, "Hand_Attach_R", 'R'), None);
    }

    #[test]
    fn redirects_generic_vmd_track_to_japanese_dummy() {
        let mut bones = BoneManager::new();
        bones.add_bone(BoneLink::new("右ダミー".to_string()));

        assert_eq!(find_vmd_attachment_target(&bones, "Hand_Attach_R"), Some(0));
        assert_eq!(find_vmd_attachment_target(&bones, "Hand_Attach_L"), None);
    }

    #[test]
    fn does_not_redirect_left_attachment_tracks() {
        let mut bones = BoneManager::new();
        bones.add_bone(BoneLink::new("左ダミー".to_string()));

        assert_eq!(find_vmd_attachment_target(&bones, "Hand_Attach_L"), None);
        assert_eq!(find_vmd_attachment_target(&bones, "左ダミー"), None);
        assert!(!is_preferred_vmd_attachment_track(
            "Hand_Attach_L",
            ["Hand_Attach_L".to_string()].iter(),
        ));
    }

    #[test]
    fn prefers_explicit_track_when_vmd_contains_multiple_aliases() {
        let names = ["ダミー.R".to_string(), "Hand_Attach_R".to_string()];

        assert!(!is_preferred_vmd_attachment_track("ダミー.R", names.iter()));
        assert!(is_preferred_vmd_attachment_track(
            "Hand_Attach_R",
            names.iter()
        ));
    }
}
