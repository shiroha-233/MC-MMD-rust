//! FBX 骨骼名 → MMD 标准骨骼名映射

/// 将 FBX 骨骼名映射到 PMX 标准骨骼名（依次尝试 Mixamo → Unreal Mannequin → 原名）
pub fn map_fbx_bone_name(fbx_name: &str) -> String {
    let stripped = fbx_name.strip_prefix("mixamorig:").unwrap_or(fbx_name);

    if let Some(mapped) = mixamo_to_mmd(stripped) {
        return mapped.to_string();
    }
    if let Some(mapped) = unreal_mannequin_to_mmd(stripped) {
        return mapped.to_string();
    }
    fbx_name.to_string()
}

/// Mixamo 骨骼名 → MMD 标准骨骼名
fn mixamo_to_mmd(name: &str) -> Option<&'static str> {
    match name {
        "Hips" => Some("センター"),
        "Spine" => Some("上半身"),
        "Spine1" => Some("上半身2"),
        "Spine2" => Some("上半身3"),
        "Neck" => Some("首"),
        "Head" => Some("頭"),
        "LeftShoulder" => Some("左肩"),
        "LeftArm" => Some("左腕"),
        "LeftForeArm" => Some("左ひじ"),
        "LeftHand" => Some("左手首"),
        "RightShoulder" => Some("右肩"),
        "RightArm" => Some("右腕"),
        "RightForeArm" => Some("右ひじ"),
        "RightHand" => Some("右手首"),
        "LeftUpLeg" => Some("左足"),
        "LeftLeg" => Some("左ひざ"),
        "LeftFoot" => Some("左足首"),
        "LeftToeBase" => Some("左つま先"),
        "RightUpLeg" => Some("右足"),
        "RightLeg" => Some("右ひざ"),
        "RightFoot" => Some("右足首"),
        "RightToeBase" => Some("右つま先"),
        "LeftHandThumb1" => Some("左親指０"),
        "LeftHandThumb2" => Some("左親指１"),
        "LeftHandThumb3" => Some("左親指２"),
        "LeftHandIndex1" => Some("左人指１"),
        "LeftHandIndex2" => Some("左人指２"),
        "LeftHandIndex3" => Some("左人指３"),
        "LeftHandMiddle1" => Some("左中指１"),
        "LeftHandMiddle2" => Some("左中指２"),
        "LeftHandMiddle3" => Some("左中指３"),
        "LeftHandRing1" => Some("左薬指１"),
        "LeftHandRing2" => Some("左薬指２"),
        "LeftHandRing3" => Some("左薬指３"),
        "LeftHandPinky1" => Some("左小指１"),
        "LeftHandPinky2" => Some("左小指２"),
        "LeftHandPinky3" => Some("左小指３"),
        "RightHandThumb1" => Some("右親指０"),
        "RightHandThumb2" => Some("右親指１"),
        "RightHandThumb3" => Some("右親指２"),
        "RightHandIndex1" => Some("右人指１"),
        "RightHandIndex2" => Some("右人指２"),
        "RightHandIndex3" => Some("右人指３"),
        "RightHandMiddle1" => Some("右中指１"),
        "RightHandMiddle2" => Some("右中指２"),
        "RightHandMiddle3" => Some("右中指３"),
        "RightHandRing1" => Some("右薬指１"),
        "RightHandRing2" => Some("右薬指２"),
        "RightHandRing3" => Some("右薬指３"),
        "RightHandPinky1" => Some("右小指１"),
        "RightHandPinky2" => Some("右小指２"),
        "RightHandPinky3" => Some("右小指３"),
        _ => None,
    }
}

/// Unreal Mannequin 骨骼名 → MMD 标准骨骼名
fn unreal_mannequin_to_mmd(name: &str) -> Option<&'static str> {
    match name {
        "root" => Some("全ての親"),
        "pelvis" => Some("センター"),
        "spine_01" => Some("上半身"),
        "spine_02" => Some("上半身2"),
        "spine_03" => Some("上半身3"),
        "neck_01" => Some("首"),
        "head" => Some("頭"),
        "clavicle_l" => Some("左肩"),
        "upperarm_l" => Some("左腕"),
        "lowerarm_l" => Some("左ひじ"),
        "hand_l" => Some("左手首"),
        "clavicle_r" => Some("右肩"),
        "upperarm_r" => Some("右腕"),
        "lowerarm_r" => Some("右ひじ"),
        "hand_r" => Some("右手首"),
        "thigh_l" => Some("左足"),
        "calf_l" => Some("左ひざ"),
        "foot_l" => Some("左足首"),
        "ball_l" => Some("左つま先"),
        "thigh_r" => Some("右足"),
        "calf_r" => Some("右ひざ"),
        "foot_r" => Some("右足首"),
        "ball_r" => Some("右つま先"),
        "thumb_01_l" => Some("左親指０"),
        "thumb_02_l" => Some("左親指１"),
        "thumb_03_l" => Some("左親指２"),
        "index_01_l" => Some("左人指１"),
        "index_02_l" => Some("左人指２"),
        "index_03_l" => Some("左人指３"),
        "middle_01_l" => Some("左中指１"),
        "middle_02_l" => Some("左中指２"),
        "middle_03_l" => Some("左中指３"),
        "ring_01_l" => Some("左薬指１"),
        "ring_02_l" => Some("左薬指２"),
        "ring_03_l" => Some("左薬指３"),
        "pinky_01_l" => Some("左小指１"),
        "pinky_02_l" => Some("左小指２"),
        "pinky_03_l" => Some("左小指３"),
        "thumb_01_r" => Some("右親指０"),
        "thumb_02_r" => Some("右親指１"),
        "thumb_03_r" => Some("右親指２"),
        "index_01_r" => Some("右人指１"),
        "index_02_r" => Some("右人指２"),
        "index_03_r" => Some("右人指３"),
        "middle_01_r" => Some("右中指１"),
        "middle_02_r" => Some("右中指２"),
        "middle_03_r" => Some("右中指３"),
        "ring_01_r" => Some("右薬指１"),
        "ring_02_r" => Some("右薬指２"),
        "ring_03_r" => Some("右薬指３"),
        "pinky_01_r" => Some("右小指１"),
        "pinky_02_r" => Some("右小指２"),
        "pinky_03_r" => Some("右小指３"),
        _ => None,
    }
}
