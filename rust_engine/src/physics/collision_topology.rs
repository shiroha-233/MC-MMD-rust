//! 基于 PMX 关节图的动态刚体自碰撞过滤。
//!
//! 过滤只作用于同碰撞组的动态刚体子图。动态刚体与跟骨刚体、不同组刚体
//! 之间的碰撞始终保留，避免衣物失去身体碰撞后穿模。

use std::collections::VecDeque;

use glam::{Mat4, Vec3};

/// 碰撞稳定模式。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollisionStabilityMode {
    /// 不增加拓扑过滤；直接相邻刚体仍由 Bullet 约束的禁碰参数处理。
    Strict,
    /// 忽略同组动态子图中距离不超过 2 的刚体对。
    Stable,
    /// 忽略同组动态子图同一连通分量内的全部刚体对。
    Relaxed,
}

impl CollisionStabilityMode {
    /// 将外部整数映射为模式；未知值回退到默认 Stable。
    pub fn from_i32(value: i32) -> Self {
        match value {
            0 => Self::Strict,
            1 => Self::Stable,
            2 => Self::Relaxed,
            _ => Self::Stable,
        }
    }

    /// 返回稳定的诊断名称，避免依赖 Debug 格式作为日志协议。
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Strict => "Strict",
            Self::Stable => "Stable",
            Self::Relaxed => "Relaxed",
        }
    }
}

/// 拓扑过滤所需的最小刚体描述。
#[derive(Debug, Clone, Copy)]
pub struct CollisionBody {
    pub group: u8,
    pub collision_mask: u16,
    pub is_dynamic: bool,
    /// 是否属于裙摆或下装动态系统，用于收窄 Stable 模式的全连通分量过滤。
    pub is_skirt: bool,
    /// 是否属于尾巴动态链，用于隔离初始已穿入后裙的跨碰撞组刚体对。
    pub is_tail: bool,
    pub is_active: bool,
    /// 初始姿态下旋转碰撞体的保守世界 AABB；缺失时仅使用拓扑规则。
    pub initial_aabb: Option<CollisionAabb>,
}

/// 初始姿态的世界轴对齐包围盒，仅用于构建期稳定过滤和诊断。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CollisionAabb {
    pub center: Vec3,
    pub half_extents: Vec3,
}

impl CollisionAabb {
    /// 将局部半尺寸按刚体旋转投影到世界轴，得到保守 AABB。
    pub fn from_transform(transform: Mat4, local_half_extents: Vec3) -> Option<Self> {
        if !transform.is_finite()
            || !local_half_extents.is_finite()
            || local_half_extents.cmple(Vec3::ZERO).any()
        {
            return None;
        }
        let x = transform.x_axis.truncate().abs();
        let y = transform.y_axis.truncate().abs();
        let z = transform.z_axis.truncate().abs();
        let half_extents =
            x * local_half_extents.x + y * local_half_extents.y + z * local_half_extents.z;
        Some(Self {
            center: transform.w_axis.truncate(),
            half_extents,
        })
    }

    fn overlaps(self, other: Self) -> bool {
        let separation = (self.center - other.center).abs();
        separation
            .cmplt(self.half_extents + other.half_extents)
            .all()
    }
}

/// 构建阶段生成的精确过滤计划及诊断统计。
#[derive(Debug, Default, PartialEq, Eq)]
pub struct CollisionFilterPlan {
    pub pairs: Vec<(usize, usize)>,
    pub preserved_dynamic_kinematic_pairs: usize,
    /// Stable 模式中过滤的“尾部 FollowBone 锚点-动态裙摆”初始重叠对。
    pub filtered_tail_anchor_skirt_pairs: usize,
    pub largest_dynamic_component: usize,
    pub initial_overlap_dynamic_dynamic_pairs: usize,
    pub initial_overlap_dynamic_kinematic_pairs: usize,
    pub filtered_initial_overlap_pairs: usize,
}

/// 根据同组动态刚体子图生成需要忽略的刚体对。
///
/// 无效索引、自连接和重复边会被忽略。仅统计 PMX 掩码原本允许碰撞的对，
/// 返回值规范为 `(较小索引, 较大索引)`。该函数只在模型构建阶段执行。
pub fn build_filter_plan(
    bodies: &[CollisionBody],
    joints: &[(i32, i32)],
    mode: CollisionStabilityMode,
) -> CollisionFilterPlan {
    let mut plan = CollisionFilterPlan::default();
    if bodies.len() < 2 {
        return plan;
    }

    for a in 0..bodies.len() {
        for b in (a + 1)..bodies.len() {
            if !bodies[a].is_active
                || !bodies[b].is_active
                || !collision_allowed(bodies[a], bodies[b])
            {
                continue;
            }
            let initially_overlapping = aabb_overlap(bodies[a], bodies[b]);
            if bodies[a].is_dynamic != bodies[b].is_dynamic {
                plan.preserved_dynamic_kinematic_pairs += 1;
                if initially_overlapping {
                    plan.initial_overlap_dynamic_kinematic_pairs += 1;
                }
            } else if bodies[a].is_dynamic && bodies[b].is_dynamic && initially_overlapping {
                plan.initial_overlap_dynamic_dynamic_pairs += 1;
            }
        }
    }

    let mut adjacency = vec![Vec::<usize>::new(); bodies.len()];
    for &(a, b) in joints {
        let (Ok(a), Ok(b)) = (usize::try_from(a), usize::try_from(b)) else {
            continue;
        };
        if a >= bodies.len()
            || b >= bodies.len()
            || a == b
            || !bodies[a].is_active
            || !bodies[b].is_active
            || !bodies[a].is_dynamic
            || !bodies[b].is_dynamic
            || bodies[a].group != bodies[b].group
        {
            continue;
        }
        if !adjacency[a].contains(&b) {
            adjacency[a].push(b);
            adjacency[b].push(a);
        }
    }

    let mut distances = vec![usize::MAX; bodies.len()];
    let mut queue = VecDeque::new();
    for start in 0..bodies.len() {
        if !bodies[start].is_active || !bodies[start].is_dynamic || adjacency[start].is_empty() {
            continue;
        }
        distances.fill(usize::MAX);
        queue.clear();
        distances[start] = 0;
        queue.push_back(start);

        while let Some(current) = queue.pop_front() {
            for &next in &adjacency[current] {
                if distances[next] == usize::MAX {
                    distances[next] = distances[current] + 1;
                    queue.push_back(next);
                }
            }
        }

        let component_size = distances
            .iter()
            .filter(|&&distance| distance != usize::MAX)
            .count();
        plan.largest_dynamic_component = plan.largest_dynamic_component.max(component_size);
        for other in (start + 1)..bodies.len() {
            let distance = distances[other];
            let should_ignore = match mode {
                CollisionStabilityMode::Strict => false,
                // 裙摆网格常用错列横向关节，图距离大于 2 的刚体仍可能在绑定姿态互相穿插。
                // 只过滤初态已经重叠的远距离裙片，保留其余自碰撞用于维持裙摆体积。
                CollisionStabilityMode::Stable => {
                    distance <= 2
                        || (bodies[start].is_skirt
                            && bodies[other].is_skirt
                            && distance != usize::MAX
                            && dynamic_pair_initially_overlaps(bodies[start], bodies[other]))
                }
                CollisionStabilityMode::Relaxed => distance != usize::MAX,
            };
            if should_ignore && collision_allowed(bodies[start], bodies[other]) {
                plan.pairs.push((start, other));
                if dynamic_pair_initially_overlaps(bodies[start], bodies[other]) {
                    plan.filtered_initial_overlap_pairs += 1;
                }
            }
        }
    }

    // PMX 中常见多个动态刚体分别挂到同一个 FollowBone 根（例如四片袖子挂到肘部）。
    // 这些兄弟刚体不会出现在上面的纯动态图中；仅当它们在绑定姿态已经重叠时禁碰，
    // 避免碰撞分离与共同锚点约束持续对抗，同时保留正常分离衣片之间的碰撞。
    let mut kinematic_children = vec![Vec::<usize>::new(); bodies.len()];
    for &(a, b) in joints {
        let (Ok(a), Ok(b)) = (usize::try_from(a), usize::try_from(b)) else {
            continue;
        };
        if a >= bodies.len() || b >= bodies.len() || a == b {
            continue;
        }
        let (root, child) = if !bodies[a].is_dynamic && bodies[b].is_dynamic {
            (a, b)
        } else if !bodies[b].is_dynamic && bodies[a].is_dynamic {
            (b, a)
        } else {
            continue;
        };
        if bodies[root].is_active
            && bodies[child].is_active
            && !kinematic_children[root].contains(&child)
        {
            kinematic_children[root].push(child);
        }
    }

    for children in kinematic_children {
        for a_index in 0..children.len() {
            for b_index in (a_index + 1)..children.len() {
                let (a, b) = if children[a_index] < children[b_index] {
                    (children[a_index], children[b_index])
                } else {
                    (children[b_index], children[a_index])
                };
                if mode != CollisionStabilityMode::Strict
                    && bodies[a].group == bodies[b].group
                    && collision_allowed(bodies[a], bodies[b])
                    && dynamic_pair_initially_overlaps(bodies[a], bodies[b])
                {
                    plan.pairs.push((a, b));
                }
            }
        }
    }

    // 尾巴与后裙通常属于不同碰撞组，也没有关节边，因此不会进入上面的连通图过滤。
    // Stable 模式只断开绑定姿态下已经重叠的尾巴-裙摆对。这里也包含 FollowBone
    // 尾根锚点：静止锚点若持续顶住动态后裙，会与裙环关节形成高频能量输入。
    if mode == CollisionStabilityMode::Stable {
        for a in 0..bodies.len() {
            for b in (a + 1)..bodies.len() {
                let tail_skirt_pair = (bodies[a].is_tail && bodies[b].is_skirt)
                    || (bodies[b].is_tail && bodies[a].is_skirt);
                let has_dynamic_skirt = (bodies[a].is_skirt && bodies[a].is_dynamic)
                    || (bodies[b].is_skirt && bodies[b].is_dynamic);
                if tail_skirt_pair
                    && has_dynamic_skirt
                    && bodies[a].is_active
                    && bodies[b].is_active
                    && collision_allowed(bodies[a], bodies[b])
                    && aabb_overlap(bodies[a], bodies[b])
                {
                    plan.pairs.push((a, b));
                }
            }
        }
    }

    // 一个刚体对可能同时由动态链和共享根规则命中。
    plan.pairs.sort_unstable();
    plan.pairs.dedup();
    plan.filtered_tail_anchor_skirt_pairs = plan
        .pairs
        .iter()
        .filter(|&&(a, b)| {
            bodies[a].is_dynamic != bodies[b].is_dynamic
                && ((bodies[a].is_tail && bodies[b].is_skirt)
                    || (bodies[b].is_tail && bodies[a].is_skirt))
        })
        .count();
    plan.preserved_dynamic_kinematic_pairs = plan
        .preserved_dynamic_kinematic_pairs
        .saturating_sub(plan.filtered_tail_anchor_skirt_pairs);
    plan.filtered_initial_overlap_pairs = plan
        .pairs
        .iter()
        .filter(|&&(a, b)| {
            bodies[a].is_dynamic && bodies[b].is_dynamic && aabb_overlap(bodies[a], bodies[b])
        })
        .count();

    plan
}

fn dynamic_pair_initially_overlaps(a: CollisionBody, b: CollisionBody) -> bool {
    a.group == b.group && a.is_dynamic && b.is_dynamic && aabb_overlap(a, b)
}

fn aabb_overlap(a: CollisionBody, b: CollisionBody) -> bool {
    matches!((a.initial_aabb, b.initial_aabb), (Some(a), Some(b)) if a.overlaps(b))
}

/// Bullet 只有在双方组位均被对方掩码允许时才会创建接触。
fn collision_allowed(a: CollisionBody, b: CollisionBody) -> bool {
    let a_group = 1u16 << a.group.min(15);
    let b_group = 1u16 << b.group.min(15);
    a.collision_mask & b_group != 0 && b.collision_mask & a_group != 0
}

#[cfg(test)]
mod tests {
    use super::{build_filter_plan, CollisionAabb, CollisionBody, CollisionStabilityMode};
    use glam::{Mat4, Vec3};

    const DYNAMIC_GROUP_7: CollisionBody = CollisionBody {
        group: 7,
        collision_mask: 0xFFFF,
        is_dynamic: true,
        is_skirt: false,
        is_tail: false,
        is_active: true,
        initial_aabb: None,
    };

    #[test]
    fn stable_and_relaxed_filter_same_group_dynamic_chain() {
        let bodies = [DYNAMIC_GROUP_7; 4];
        let joints = [(0, 1), (1, 2), (2, 3)];
        assert!(
            build_filter_plan(&bodies, &joints, CollisionStabilityMode::Strict)
                .pairs
                .is_empty()
        );
        assert_eq!(
            build_filter_plan(&bodies, &joints, CollisionStabilityMode::Stable).pairs,
            vec![(0, 1), (0, 2), (1, 2), (1, 3), (2, 3)]
        );
        let relaxed = build_filter_plan(&bodies, &joints, CollisionStabilityMode::Relaxed);
        assert_eq!(relaxed.pairs.len(), 6);
        assert_eq!(relaxed.largest_dynamic_component, 4);
    }

    #[test]
    fn dynamic_to_kinematic_collision_is_always_preserved() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_dynamic: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.preserved_dynamic_kinematic_pairs, 2);
    }

    #[test]
    fn kinematic_bridge_does_not_join_dynamic_components() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_dynamic: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(!plan.pairs.contains(&(0, 2)));
        assert_eq!(plan.largest_dynamic_component, 0);
    }

    #[test]
    fn cross_group_dynamic_bodies_are_not_filtered() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                group: 8,
                ..DYNAMIC_GROUP_7
            },
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn cross_group_body_cannot_bridge_same_group_bodies() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                group: 8,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn inactive_body_cannot_be_endpoint_or_bridge() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_active: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.largest_dynamic_component, 0);
    }

    #[test]
    fn duplicate_and_invalid_edges_do_not_duplicate_pairs() {
        let bodies = [DYNAMIC_GROUP_7; 2];
        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 0), (0, 1), (-1, 0), (0, 9), (1, 1)],
            CollisionStabilityMode::Stable,
        );
        assert_eq!(plan.pairs, vec![(0, 1)]);
    }

    #[test]
    fn pairs_already_disabled_by_pmx_mask_are_not_counted() {
        let bodies = [
            CollisionBody {
                collision_mask: !(1 << 7),
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.largest_dynamic_component, 2);
    }

    #[test]
    fn integer_mapping_uses_stable_as_fallback() {
        assert_eq!(
            CollisionStabilityMode::from_i32(0),
            CollisionStabilityMode::Strict
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(1),
            CollisionStabilityMode::Stable
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(2),
            CollisionStabilityMode::Relaxed
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(99),
            CollisionStabilityMode::Stable
        );
    }

    #[test]
    fn stable_reports_but_preserves_overlap_beyond_graph_distance() {
        let mut bodies = [DYNAMIC_GROUP_7; 4];
        bodies[0].initial_aabb =
            CollisionAabb::from_transform(Mat4::from_translation(Vec3::ZERO), Vec3::splat(1.0));
        bodies[3].initial_aabb = CollisionAabb::from_transform(
            Mat4::from_translation(Vec3::new(1.5, 0.0, 0.0)),
            Vec3::splat(1.0),
        );
        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 2), (2, 3)],
            CollisionStabilityMode::Stable,
        );
        assert!(!plan.pairs.contains(&(0, 3)));
        assert_eq!(plan.initial_overlap_dynamic_dynamic_pairs, 1);
        assert_eq!(plan.filtered_initial_overlap_pairs, 0);
    }

    #[test]
    fn stable_filters_initially_overlapping_distant_skirt_pair() {
        let skirt = CollisionBody {
            is_skirt: true,
            ..DYNAMIC_GROUP_7
        };
        let mut bodies = [skirt; 4];
        let overlap = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        bodies[0].initial_aabb = overlap;
        bodies[3].initial_aabb = overlap;

        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 2), (2, 3)],
            CollisionStabilityMode::Stable,
        );

        assert!(plan.pairs.contains(&(0, 3)));
        assert_eq!(plan.pairs.len(), 6);
    }

    #[test]
    fn stable_preserves_separated_distant_skirt_self_collision() {
        let skirt = CollisionBody {
            is_skirt: true,
            ..DYNAMIC_GROUP_7
        };
        let bodies = [skirt; 4];

        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 2), (2, 3)],
            CollisionStabilityMode::Stable,
        );

        assert!(!plan.pairs.contains(&(0, 3)));
        assert_eq!(plan.pairs.len(), 5);
    }

    #[test]
    fn stable_filters_initially_overlapping_tail_and_skirt_across_groups() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let tail = CollisionBody {
            group: 3,
            is_tail: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };
        let skirt = CollisionBody {
            group: 4,
            is_skirt: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };

        let plan = build_filter_plan(&[tail, skirt], &[], CollisionStabilityMode::Stable);

        assert_eq!(plan.pairs, vec![(0, 1)]);
        assert_eq!(plan.filtered_initial_overlap_pairs, 1);
    }

    #[test]
    fn stable_preserves_separated_tail_and_skirt_collision() {
        let tail = CollisionBody {
            group: 3,
            is_tail: true,
            initial_aabb: CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE),
            ..DYNAMIC_GROUP_7
        };
        let skirt = CollisionBody {
            group: 4,
            is_skirt: true,
            initial_aabb: CollisionAabb::from_transform(
                Mat4::from_translation(Vec3::new(3.0, 0.0, 0.0)),
                Vec3::ONE,
            ),
            ..DYNAMIC_GROUP_7
        };

        let plan = build_filter_plan(&[tail, skirt], &[], CollisionStabilityMode::Stable);

        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn stable_filters_overlapping_tail_anchor_and_dynamic_skirt() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let tail_anchor = CollisionBody {
            group: 3,
            is_dynamic: false,
            is_tail: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };
        let skirt = CollisionBody {
            group: 4,
            is_skirt: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };

        let plan = build_filter_plan(&[tail_anchor, skirt], &[], CollisionStabilityMode::Stable);

        assert_eq!(plan.pairs, vec![(0, 1)]);
        assert_eq!(plan.filtered_tail_anchor_skirt_pairs, 1);
        assert_eq!(plan.preserved_dynamic_kinematic_pairs, 0);
    }

    #[test]
    fn strict_preserves_overlapping_tail_anchor_and_dynamic_skirt() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let tail_anchor = CollisionBody {
            group: 3,
            is_dynamic: false,
            is_tail: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };
        let skirt = CollisionBody {
            group: 4,
            is_skirt: true,
            initial_aabb: bounds,
            ..DYNAMIC_GROUP_7
        };

        let plan = build_filter_plan(&[tail_anchor, skirt], &[], CollisionStabilityMode::Strict);

        assert!(plan.pairs.is_empty());
        assert_eq!(plan.filtered_tail_anchor_skirt_pairs, 0);
        assert_eq!(plan.preserved_dynamic_kinematic_pairs, 1);
    }

    #[test]
    fn stable_preserves_separated_tail_anchor_and_dynamic_skirt() {
        let tail_anchor = CollisionBody {
            group: 3,
            is_dynamic: false,
            is_tail: true,
            initial_aabb: CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE),
            ..DYNAMIC_GROUP_7
        };
        let skirt = CollisionBody {
            group: 4,
            is_skirt: true,
            initial_aabb: CollisionAabb::from_transform(
                Mat4::from_translation(Vec3::new(3.0, 0.0, 0.0)),
                Vec3::ONE,
            ),
            ..DYNAMIC_GROUP_7
        };

        let plan = build_filter_plan(&[tail_anchor, skirt], &[], CollisionStabilityMode::Stable);

        assert!(plan.pairs.is_empty());
        assert_eq!(plan.filtered_tail_anchor_skirt_pairs, 0);
        assert_eq!(plan.preserved_dynamic_kinematic_pairs, 1);
    }

    #[test]
    fn stable_does_not_extend_filter_from_skirt_to_other_dynamic_parts() {
        let skirt = CollisionBody {
            is_skirt: true,
            ..DYNAMIC_GROUP_7
        };
        let bodies = [skirt, skirt, skirt, DYNAMIC_GROUP_7];

        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 2), (2, 3)],
            CollisionStabilityMode::Stable,
        );

        assert!(!plan.pairs.contains(&(0, 3)));
        assert!(plan.pairs.contains(&(1, 3)));
    }

    #[test]
    fn dynamic_kinematic_overlap_is_reported_but_not_filtered() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let bodies = [
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                is_dynamic: false,
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Stable);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.initial_overlap_dynamic_kinematic_pairs, 1);
    }

    #[test]
    fn overlapping_dynamic_siblings_on_kinematic_root_are_filtered() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let bodies = [
            CollisionBody {
                is_dynamic: false,
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
        ];

        let plan = build_filter_plan(&bodies, &[(0, 1), (0, 2)], CollisionStabilityMode::Stable);

        assert_eq!(plan.pairs, vec![(1, 2)]);
        assert_eq!(plan.filtered_initial_overlap_pairs, 1);
    }

    #[test]
    fn separated_dynamic_siblings_on_kinematic_root_keep_collision() {
        let bodies = [
            CollisionBody {
                is_dynamic: false,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE),
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: CollisionAabb::from_transform(
                    Mat4::from_translation(Vec3::new(3.0, 0.0, 0.0)),
                    Vec3::ONE,
                ),
                ..DYNAMIC_GROUP_7
            },
        ];

        let plan = build_filter_plan(&bodies, &[(0, 1), (0, 2)], CollisionStabilityMode::Stable);

        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn strict_keeps_overlapping_dynamic_siblings_collision() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let bodies = [
            CollisionBody {
                is_dynamic: false,
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
        ];

        let plan = build_filter_plan(&bodies, &[(0, 1), (0, 2)], CollisionStabilityMode::Strict);

        assert!(plan.pairs.is_empty());
    }
}
