/**
 * Bullet3 C Wrapper API
 * 
 * 纯 C 接口封装 Bullet3 核心功能，供 Rust FFI 调用。
 * 仅包含 MMD 物理所需的最小 API 子集。
 */

#ifndef BW_API_H
#define BW_API_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ===== 分配计数器（调试用） ===== */
typedef struct {
    int worlds;
    int shapes;
    int rigid_bodies;
    int constraints;
    int motion_states;
} BW_AllocStats;

/* 获取当前存活的 C++ 对象计数 */
BW_AllocStats bw_get_alloc_stats(void);

/* 不透明指针类型 */
typedef struct BW_World BW_World;
typedef struct BW_Shape BW_Shape;
typedef struct BW_RigidBody BW_RigidBody;
typedef struct BW_Constraint BW_Constraint;

/* Bullet3 激活状态常量 */
#define BW_ACTIVE_TAG 1
#define BW_ISLAND_SLEEPING 2
#define BW_WANTS_DEACTIVATION 3
#define BW_DISABLE_DEACTIVATION 4
#define BW_DISABLE_SIMULATION 5

/* Bullet3 约束参数常量 */
#define BW_CONSTRAINT_STOP_ERP 2
#define BW_CONSTRAINT_STOP_CFM 3

/* ===== 刚体构建信息 ===== */
typedef struct {
    float mass;
    float linear_damping;
    float angular_damping;
    float friction;
    float restitution;
    bool additional_damping;
    bool is_kinematic;
    bool disable_deactivation;
    bool no_contact_response;
    BW_Shape* shape;
    float initial_transform[16]; /* 4x4 列主序 */
} BW_RigidBodyInfo;

/* ===== 物理世界 ===== */
BW_World* bw_world_create(float gravity_x, float gravity_y, float gravity_z);
void bw_world_destroy(BW_World* world);
void bw_world_step(BW_World* world, float dt, int max_substeps, float fixed_dt);
void bw_world_set_gravity(BW_World* world, float x, float y, float z);
void bw_world_add_rigid_body(BW_World* world, BW_RigidBody* rb, int group, int mask);
void bw_world_remove_rigid_body(BW_World* world, BW_RigidBody* rb);
void bw_world_add_constraint(BW_World* world, BW_Constraint* c, bool disable_collision);
void bw_world_remove_constraint(BW_World* world, BW_Constraint* c);

/* ===== 碰撞形状 ===== */
BW_Shape* bw_shape_sphere(float radius);
BW_Shape* bw_shape_box(float hx, float hy, float hz);
BW_Shape* bw_shape_capsule(float radius, float height);
void bw_shape_destroy(BW_Shape* shape);

/* ===== 刚体 ===== */
BW_RigidBody* bw_rigid_body_create(const BW_RigidBodyInfo* info);
void bw_rigid_body_destroy(BW_RigidBody* rb);
void bw_rigid_body_get_transform(BW_RigidBody* rb, float* matrix4x4);
void bw_rigid_body_set_transform(BW_RigidBody* rb, const float* matrix4x4);
void bw_rigid_body_get_position(BW_RigidBody* rb, float* x, float* y, float* z);
void bw_rigid_body_get_rotation(BW_RigidBody* rb, float* x, float* y, float* z, float* w);
void bw_rigid_body_set_linear_velocity(BW_RigidBody* rb, float x, float y, float z);
void bw_rigid_body_set_angular_velocity(BW_RigidBody* rb, float x, float y, float z);
void bw_rigid_body_get_linear_velocity(BW_RigidBody* rb, float* x, float* y, float* z);
void bw_rigid_body_set_damping(BW_RigidBody* rb, float linear, float angular);
void bw_rigid_body_set_friction(BW_RigidBody* rb, float friction);
void bw_rigid_body_set_restitution(BW_RigidBody* rb, float restitution);
void bw_rigid_body_set_activation_state(BW_RigidBody* rb, int state);
void bw_rigid_body_force_activation_state(BW_RigidBody* rb, int state);
void bw_rigid_body_set_kinematic(BW_RigidBody* rb, bool kinematic);
float bw_rigid_body_get_mass(BW_RigidBody* rb);
void bw_rigid_body_clear_forces(BW_RigidBody* rb);

/* ===== 6DOF 弹簧约束 ===== */
BW_Constraint* bw_6dof_spring_create(
    BW_RigidBody* a, BW_RigidBody* b,
    const float* frame_a_4x4, const float* frame_b_4x4,
    bool use_linear_ref_a);
void bw_constraint_destroy(BW_Constraint* c);
void bw_6dof_spring_set_linear_lower_limit(BW_Constraint* c, float x, float y, float z);
void bw_6dof_spring_set_linear_upper_limit(BW_Constraint* c, float x, float y, float z);
void bw_6dof_spring_set_angular_lower_limit(BW_Constraint* c, float x, float y, float z);
void bw_6dof_spring_set_angular_upper_limit(BW_Constraint* c, float x, float y, float z);
void bw_6dof_spring_enable_spring(BW_Constraint* c, int index, bool on);
void bw_6dof_spring_set_stiffness(BW_Constraint* c, int index, float stiffness);
void bw_6dof_spring_set_damping(BW_Constraint* c, int index, float damping);
void bw_6dof_spring_set_equilibrium_point(BW_Constraint* c);
void bw_6dof_spring_set_param(BW_Constraint* c, int param, float value, int axis);
void bw_6dof_spring_use_frame_offset(BW_Constraint* c, bool on);

#ifdef __cplusplus
}
#endif

#endif /* BW_API_H */
