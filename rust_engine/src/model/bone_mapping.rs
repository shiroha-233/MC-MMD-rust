//! VRM ↔ MMD 骨骼名双向映射

/// VRM humanoid bone name → MMD 日文骨骼名
///
/// 未映射的骨骼名保留原名返回
pub fn vrm_to_mmd_bone_name(vrm_name: &str) -> &str {
    match vrm_name {
        // === 必需骨骼 ===
        "hips" => "下半身",
        "spine" => "上半身",
        "head" => "頭",
        "leftUpperArm" => "左腕",
        "leftLowerArm" => "左ひじ",
        "leftHand" => "左手首",
        "rightUpperArm" => "右腕",
        "rightLowerArm" => "右ひじ",
        "rightHand" => "右手首",
        "leftUpperLeg" => "左足",
        "leftLowerLeg" => "左ひざ",
        "leftFoot" => "左足首",
        "rightUpperLeg" => "右足",
        "rightLowerLeg" => "右ひざ",
        "rightFoot" => "右足首",

        // === 可选骨骼 ===
        "chest" => "上半身2",
        "upperChest" => "上半身3",
        "neck" => "首",
        "leftShoulder" => "左肩",
        "rightShoulder" => "右肩",
        "leftToes" => "左つま先",
        "rightToes" => "右つま先",
        "leftEye" => "左目",
        "rightEye" => "右目",
        "jaw" => "あご",

        // === 左手指骨骼 ===
        "leftThumbMetacarpal" => "左親指０",
        "leftThumbProximal" => "左親指１",
        "leftThumbDistal" => "左親指２",
        "leftIndexProximal" => "左人指１",
        "leftIndexIntermediate" => "左人指２",
        "leftIndexDistal" => "左人指３",
        "leftMiddleProximal" => "左中指１",
        "leftMiddleIntermediate" => "左中指２",
        "leftMiddleDistal" => "左中指３",
        "leftRingProximal" => "左薬指１",
        "leftRingIntermediate" => "左薬指２",
        "leftRingDistal" => "左薬指３",
        "leftLittleProximal" => "左小指１",
        "leftLittleIntermediate" => "左小指２",
        "leftLittleDistal" => "左小指３",

        // === 右手指骨骼 ===
        "rightThumbMetacarpal" => "右親指０",
        "rightThumbProximal" => "右親指１",
        "rightThumbDistal" => "右親指２",
        "rightIndexProximal" => "右人指１",
        "rightIndexIntermediate" => "右人指２",
        "rightIndexDistal" => "右人指３",
        "rightMiddleProximal" => "右中指１",
        "rightMiddleIntermediate" => "右中指２",
        "rightMiddleDistal" => "右中指３",
        "rightRingProximal" => "右薬指１",
        "rightRingIntermediate" => "右薬指２",
        "rightRingDistal" => "右薬指３",
        "rightLittleProximal" => "右小指１",
        "rightLittleIntermediate" => "右小指２",
        "rightLittleDistal" => "右小指３",

        other => other,
    }
}

/// MMD 日文骨骼名 → VRM humanoid bone name（反向映射，用于 VMD 动画）
///
/// MMD 独有骨骼（センター、グルーブ、IK 骨骼）返回 None
pub fn mmd_to_vrm_bone_name(mmd_name: &str) -> Option<&'static str> {
    match mmd_name {
        // === 必需骨骼 ===
        "下半身" => Some("hips"),
        "上半身" => Some("spine"),
        "頭" => Some("head"),
        "左腕" => Some("leftUpperArm"),
        "左ひじ" => Some("leftLowerArm"),
        "左手首" => Some("leftHand"),
        "右腕" => Some("rightUpperArm"),
        "右ひじ" => Some("rightLowerArm"),
        "右手首" => Some("rightHand"),
        "左足" => Some("leftUpperLeg"),
        "左ひざ" => Some("leftLowerLeg"),
        "左足首" => Some("leftFoot"),
        "右足" => Some("rightUpperLeg"),
        "右ひざ" => Some("rightLowerLeg"),
        "右足首" => Some("rightFoot"),

        // === 可选骨骼 ===
        "上半身2" => Some("chest"),
        "上半身3" => Some("upperChest"),
        "首" => Some("neck"),
        "左肩" => Some("leftShoulder"),
        "右肩" => Some("rightShoulder"),
        "左つま先" => Some("leftToes"),
        "右つま先" => Some("rightToes"),
        "左目" => Some("leftEye"),
        "右目" => Some("rightEye"),
        "あご" => Some("jaw"),

        // === 左手指骨骼 ===
        "左親指０" => Some("leftThumbMetacarpal"),
        "左親指１" => Some("leftThumbProximal"),
        "左親指２" => Some("leftThumbDistal"),
        "左人指１" => Some("leftIndexProximal"),
        "左人指２" => Some("leftIndexIntermediate"),
        "左人指３" => Some("leftIndexDistal"),
        "左中指１" => Some("leftMiddleProximal"),
        "左中指２" => Some("leftMiddleIntermediate"),
        "左中指３" => Some("leftMiddleDistal"),
        "左薬指１" => Some("leftRingProximal"),
        "左薬指２" => Some("leftRingIntermediate"),
        "左薬指３" => Some("leftRingDistal"),
        "左小指１" => Some("leftLittleProximal"),
        "左小指２" => Some("leftLittleIntermediate"),
        "左小指３" => Some("leftLittleDistal"),

        // === 右手指骨骼 ===
        "右親指０" => Some("rightThumbMetacarpal"),
        "右親指１" => Some("rightThumbProximal"),
        "右親指２" => Some("rightThumbDistal"),
        "右人指１" => Some("rightIndexProximal"),
        "右人指２" => Some("rightIndexIntermediate"),
        "右人指３" => Some("rightIndexDistal"),
        "右中指１" => Some("rightMiddleProximal"),
        "右中指２" => Some("rightMiddleIntermediate"),
        "右中指３" => Some("rightMiddleDistal"),
        "右薬指１" => Some("rightRingProximal"),
        "右薬指２" => Some("rightRingIntermediate"),
        "右薬指３" => Some("rightRingDistal"),
        "右小指１" => Some("rightLittleProximal"),
        "右小指２" => Some("rightLittleIntermediate"),
        "右小指３" => Some("rightLittleDistal"),

        // === 特殊映射：センター → hips ===
        "センター" => Some("hips"),

        // MMD 独有骨骼，VRM 中无对应
        "グルーブ" | "左足ＩＫ" | "右足ＩＫ" | "左つま先ＩＫ" | "右つま先ＩＫ" => None,

        _ => None,
    }
}

/// 判断 MMD 骨骼名是否为センター（中心）骨骼
///
/// センター在 VRM 中映射到 hips 的父级，需要特殊处理
pub fn is_mmd_center_bone(mmd_name: &str) -> bool {
    mmd_name == "センター"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_required_bones_forward() {
        assert_eq!(vrm_to_mmd_bone_name("hips"), "下半身");
        assert_eq!(vrm_to_mmd_bone_name("spine"), "上半身");
        assert_eq!(vrm_to_mmd_bone_name("head"), "頭");
        assert_eq!(vrm_to_mmd_bone_name("leftUpperArm"), "左腕");
        assert_eq!(vrm_to_mmd_bone_name("leftLowerArm"), "左ひじ");
        assert_eq!(vrm_to_mmd_bone_name("leftHand"), "左手首");
        assert_eq!(vrm_to_mmd_bone_name("rightUpperArm"), "右腕");
        assert_eq!(vrm_to_mmd_bone_name("rightLowerArm"), "右ひじ");
        assert_eq!(vrm_to_mmd_bone_name("rightHand"), "右手首");
        assert_eq!(vrm_to_mmd_bone_name("leftUpperLeg"), "左足");
        assert_eq!(vrm_to_mmd_bone_name("leftLowerLeg"), "左ひざ");
        assert_eq!(vrm_to_mmd_bone_name("leftFoot"), "左足首");
        assert_eq!(vrm_to_mmd_bone_name("rightUpperLeg"), "右足");
        assert_eq!(vrm_to_mmd_bone_name("rightLowerLeg"), "右ひざ");
        assert_eq!(vrm_to_mmd_bone_name("rightFoot"), "右足首");
    }

    #[test]
    fn test_optional_bones_forward() {
        assert_eq!(vrm_to_mmd_bone_name("chest"), "上半身2");
        assert_eq!(vrm_to_mmd_bone_name("upperChest"), "上半身3");
        assert_eq!(vrm_to_mmd_bone_name("neck"), "首");
        assert_eq!(vrm_to_mmd_bone_name("leftShoulder"), "左肩");
        assert_eq!(vrm_to_mmd_bone_name("rightShoulder"), "右肩");
        assert_eq!(vrm_to_mmd_bone_name("leftToes"), "左つま先");
        assert_eq!(vrm_to_mmd_bone_name("rightToes"), "右つま先");
        assert_eq!(vrm_to_mmd_bone_name("leftEye"), "左目");
        assert_eq!(vrm_to_mmd_bone_name("rightEye"), "右目");
        assert_eq!(vrm_to_mmd_bone_name("jaw"), "あご");
    }

    #[test]
    fn test_finger_bones_forward() {
        assert_eq!(vrm_to_mmd_bone_name("leftThumbMetacarpal"), "左親指０");
        assert_eq!(vrm_to_mmd_bone_name("leftIndexProximal"), "左人指１");
        assert_eq!(vrm_to_mmd_bone_name("leftMiddleDistal"), "左中指３");
        assert_eq!(vrm_to_mmd_bone_name("rightRingIntermediate"), "右薬指２");
        assert_eq!(vrm_to_mmd_bone_name("rightLittleDistal"), "右小指３");
    }

    #[test]
    fn test_unmapped_passthrough() {
        assert_eq!(vrm_to_mmd_bone_name("customBone"), "customBone");
        assert_eq!(vrm_to_mmd_bone_name(""), "");
    }

    #[test]
    fn test_reverse_mapping() {
        assert_eq!(mmd_to_vrm_bone_name("下半身"), Some("hips"));
        assert_eq!(mmd_to_vrm_bone_name("上半身"), Some("spine"));
        assert_eq!(mmd_to_vrm_bone_name("頭"), Some("head"));
        assert_eq!(mmd_to_vrm_bone_name("左腕"), Some("leftUpperArm"));
        assert_eq!(mmd_to_vrm_bone_name("右足首"), Some("rightFoot"));
        assert_eq!(mmd_to_vrm_bone_name("左親指０"), Some("leftThumbMetacarpal"));
        assert_eq!(mmd_to_vrm_bone_name("右小指３"), Some("rightLittleDistal"));
    }

    #[test]
    fn test_center_bone_mapping() {
        assert_eq!(mmd_to_vrm_bone_name("センター"), Some("hips"));
        assert!(is_mmd_center_bone("センター"));
        assert!(!is_mmd_center_bone("下半身"));
    }

    #[test]
    fn test_mmd_only_bones_return_none() {
        assert_eq!(mmd_to_vrm_bone_name("グルーブ"), None);
        assert_eq!(mmd_to_vrm_bone_name("左足ＩＫ"), None);
        assert_eq!(mmd_to_vrm_bone_name("右足ＩＫ"), None);
        assert_eq!(mmd_to_vrm_bone_name("左つま先ＩＫ"), None);
        assert_eq!(mmd_to_vrm_bone_name("右つま先ＩＫ"), None);
        assert_eq!(mmd_to_vrm_bone_name("未知骨骼"), None);
    }

    #[test]
    fn test_bidirectional_consistency() {
        let vrm_bones = [
            "hips", "spine", "chest", "upperChest", "neck", "head",
            "leftShoulder", "leftUpperArm", "leftLowerArm", "leftHand",
            "rightShoulder", "rightUpperArm", "rightLowerArm", "rightHand",
            "leftUpperLeg", "leftLowerLeg", "leftFoot",
            "rightUpperLeg", "rightLowerLeg", "rightFoot",
            "leftToes", "rightToes", "leftEye", "rightEye", "jaw",
        ];
        for vrm_name in &vrm_bones {
            let mmd_name = vrm_to_mmd_bone_name(vrm_name);
            let back = mmd_to_vrm_bone_name(mmd_name);
            assert_eq!(back, Some(*vrm_name), "双向映射不一致: {vrm_name} → {mmd_name}");
        }
    }
}
