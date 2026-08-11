/**
 * Bullet3 C Wrapper 实现
 * 
 * 将 Bullet3 C++ API 封装为纯 C 接口，供 Rust FFI 调用。
 */

#include "bw_api.h"

#include <algorithm>
#include <atomic>
#include <cmath>

/* 
 * Bullet3 classes override operator new (btAlignedAlloc), 
 * so std::nothrow placement new is not available.
 * On MSVC (/EHsc), use try-catch to handle allocation failure.
 * On other platforms (-fno-exceptions), OOM will abort.
 */
#if defined(_MSC_VER) || defined(__cpp_exceptions) || defined(__EXCEPTIONS)
  #define BW_TRY try {
  #define BW_CATCH_NULL } catch (...) { return nullptr; }
#else
  #define BW_TRY
  #define BW_CATCH_NULL
#endif

// LinearMath
#include "LinearMath/btDefaultMotionState.h"
#include "LinearMath/btAlignedAllocator.h"

// Collision
#include "BulletCollision/BroadphaseCollision/btDbvtBroadphase.h"
#include "BulletCollision/CollisionDispatch/btDefaultCollisionConfiguration.h"
#include "BulletCollision/CollisionDispatch/btCollisionDispatcher.h"
#include "BulletCollision/CollisionShapes/btSphereShape.h"
#include "BulletCollision/CollisionShapes/btBoxShape.h"
#include "BulletCollision/CollisionShapes/btCapsuleShape.h"

// Dynamics
#include "BulletDynamics/Dynamics/btDiscreteDynamicsWorld.h"
#include "BulletDynamics/Dynamics/btRigidBody.h"
#include "BulletDynamics/ConstraintSolver/btSequentialImpulseConstraintSolver.h"
#include "BulletDynamics/ConstraintSolver/btGeneric6DofSpringConstraint.h"

/* ===== 分配计数器（原子操作，线程安全） ===== */

static std::atomic<int> g_alloc_worlds{0};
static std::atomic<int> g_alloc_shapes{0};
static std::atomic<int> g_alloc_rigid_bodies{0};
static std::atomic<int> g_alloc_constraints{0};
static std::atomic<int> g_alloc_motion_states{0};

BW_AllocStats bw_get_alloc_stats(void) {
    BW_AllocStats s;
    s.worlds        = g_alloc_worlds.load(std::memory_order_relaxed);
    s.shapes        = g_alloc_shapes.load(std::memory_order_relaxed);
    s.rigid_bodies  = g_alloc_rigid_bodies.load(std::memory_order_relaxed);
    s.constraints   = g_alloc_constraints.load(std::memory_order_relaxed);
    s.motion_states = g_alloc_motion_states.load(std::memory_order_relaxed);
    return s;
}

/* ===== 辅助：列主序 float[16] ↔ btTransform ===== */

static btTransform mat4_to_bt(const float* m) {
    // 输入: 列主序 4x4 (OpenGL/glam 格式)
    // m[0..3] = col0, m[4..7] = col1, m[8..11] = col2, m[12..15] = col3
    btMatrix3x3 basis(
        m[0], m[4], m[8],   // row 0
        m[1], m[5], m[9],   // row 1
        m[2], m[6], m[10]   // row 2
    );
    btVector3 origin(m[12], m[13], m[14]);
    return btTransform(basis, origin);
}

static void bt_to_mat4(const btTransform& t, float* m) {
    // 输出: 列主序 4x4
    const btMatrix3x3& b = t.getBasis();
    const btVector3& o = t.getOrigin();
    // col 0
    m[0]  = b[0][0]; m[1]  = b[1][0]; m[2]  = b[2][0]; m[3]  = 0.0f;
    // col 1
    m[4]  = b[0][1]; m[5]  = b[1][1]; m[6]  = b[2][1]; m[7]  = 0.0f;
    // col 2
    m[8]  = b[0][2]; m[9]  = b[1][2]; m[10] = b[2][2]; m[11] = 0.0f;
    // col 3
    m[12] = o[0];    m[13] = o[1];    m[14] = o[2];    m[15] = 1.0f;
}

/* ===== 物理世界 ===== */

// 运动学-动态碰撞过滤回调（宽相阶段拦截，解决头发穿透胸部抖动）
struct KinematicDynamicFilter : public btOverlapFilterCallback {
    bool needBroadphaseCollision(btBroadphaseProxy* proxy0, btBroadphaseProxy* proxy1) const override {
        bool collides = (proxy0->m_collisionFilterGroup & proxy1->m_collisionFilterMask) != 0;
        collides = collides && (proxy1->m_collisionFilterGroup & proxy0->m_collisionFilterMask) != 0;
        if (!collides) return false;

        auto* obj0 = static_cast<btCollisionObject*>(proxy0->m_clientObject);
        auto* obj1 = static_cast<btCollisionObject*>(proxy1->m_clientObject);
        if (!obj0 || !obj1) return true;

        bool k0 = obj0->isKinematicObject();
        bool k1 = obj1->isKinematicObject();
        if (k0 != k1) return false;

        return true;
    }
};

struct BW_World {
    btDefaultCollisionConfiguration* config;
    btCollisionDispatcher* dispatcher;
    btDbvtBroadphase* broadphase;
    btSequentialImpulseConstraintSolver* solver;
    btDiscreteDynamicsWorld* world;
    KinematicDynamicFilter* kinematicFilter;
};

BW_World* bw_world_create(float gravity_x, float gravity_y, float gravity_z) {
    BW_TRY
    BW_World* w = new BW_World();
    w->config     = new btDefaultCollisionConfiguration();
    w->dispatcher = new btCollisionDispatcher(w->config);
    w->broadphase = new btDbvtBroadphase();
    w->solver     = new btSequentialImpulseConstraintSolver();
    w->world      = new btDiscreteDynamicsWorld(
        w->dispatcher, w->broadphase, w->solver, w->config);
    w->world->setGravity(btVector3(gravity_x, gravity_y, gravity_z));
    w->kinematicFilter = nullptr;
    g_alloc_worlds.fetch_add(1, std::memory_order_relaxed);
    return w;
    BW_CATCH_NULL
}

void bw_world_destroy(BW_World* w) {
    if (!w) return;
    delete w->kinematicFilter;
    delete w->world;
    delete w->solver;
    delete w->broadphase;
    delete w->dispatcher;
    delete w->config;
    delete w;
    g_alloc_worlds.fetch_sub(1, std::memory_order_relaxed);
}

void bw_world_step(BW_World* w, float dt, int max_substeps, float fixed_dt) {
    if (!w) return;
    w->world->stepSimulation(dt, max_substeps, fixed_dt);
}

void bw_world_detect_collisions(BW_World* w) {
    if (!w) return;
    // 初始化阶段只需要建立真实接触流形，不能让求解器改写刚体状态。
    w->world->performDiscreteCollisionDetection();
}

void bw_world_set_gravity(BW_World* w, float x, float y, float z) {
    if (!w) return;
    w->world->setGravity(btVector3(x, y, z));
}

void bw_world_add_rigid_body(BW_World* w, BW_RigidBody* rb, int group, int mask) {
    if (!w || !rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    w->world->addRigidBody(body, (short)group, (short)mask);
}

void bw_world_remove_rigid_body(BW_World* w, BW_RigidBody* rb) {
    if (!w || !rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    w->world->removeRigidBody(body);
}

void bw_world_add_constraint(BW_World* w, BW_Constraint* c, bool disable_collision) {
    if (!w || !c) return;
    btTypedConstraint* constraint = (btTypedConstraint*)c;
    w->world->addConstraint(constraint, disable_collision);
}

void bw_world_remove_constraint(BW_World* w, BW_Constraint* c) {
    if (!w || !c) return;
    btTypedConstraint* constraint = (btTypedConstraint*)c;
    w->world->removeConstraint(constraint);
}

void bw_world_set_kinematic_filter(BW_World* w, bool enabled) {
    if (!w) return;
    if (enabled) {
        if (!w->kinematicFilter) {
            w->kinematicFilter = new KinematicDynamicFilter();
        }
        w->world->getPairCache()->setOverlapFilterCallback(w->kinematicFilter);
    } else {
        w->world->getPairCache()->setOverlapFilterCallback(nullptr);
        delete w->kinematicFilter;
        w->kinematicFilter = nullptr;
    }
}

int bw_world_get_contact_manifold_count(BW_World* w) {
    if (!w || !w->dispatcher) return 0;
    int count = 0;
    for (int i = 0; i < w->dispatcher->getNumManifolds(); ++i) {
        btPersistentManifold* manifold = w->dispatcher->getManifoldByIndexInternal(i);
        bool penetrating = false;
        for (int j = 0; j < manifold->getNumContacts(); ++j) {
            if (manifold->getContactPoint(j).getDistance() < 0.0f) {
                penetrating = true;
                break;
            }
        }
        if (penetrating) ++count;
    }
    return count;
}

int bw_world_copy_contact_manifolds(
    BW_World* w, BW_ContactManifold* output, int capacity)
{
    if (!w || !w->dispatcher || !output || capacity <= 0) return 0;
    int written = 0;
    for (int i = 0; i < w->dispatcher->getNumManifolds() && written < capacity; ++i) {
        btPersistentManifold* manifold = w->dispatcher->getManifoldByIndexInternal(i);
        BW_ContactManifold item{};
        item.body_a = (BW_RigidBody*)manifold->getBody0();
        item.body_b = (BW_RigidBody*)manifold->getBody1();

        const btManifoldPoint* deepest = nullptr;
        for (int j = 0; j < manifold->getNumContacts(); ++j) {
            const btManifoldPoint& point = manifold->getContactPoint(j);
            if (point.getDistance() >= 0.0f) continue;
            ++item.contact_count;
            const float depth = -point.getDistance();
            item.total_applied_impulse += point.getAppliedImpulse();
            item.max_applied_impulse = std::max(
                item.max_applied_impulse, (float)point.getAppliedImpulse());
            if (!deepest || depth > item.max_penetration_depth) {
                deepest = &point;
                item.max_penetration_depth = depth;
            }
        }
        if (!deepest) continue;

        const btVector3& point_a = deepest->getPositionWorldOnA();
        const btVector3& point_b = deepest->getPositionWorldOnB();
        const btVector3& normal = deepest->m_normalWorldOnB;
        for (int axis = 0; axis < 3; ++axis) {
            item.point_a[axis] = point_a[axis];
            item.point_b[axis] = point_b[axis];
            item.normal_on_b[axis] = normal[axis];
        }
        output[written++] = item;
    }
    return written;
}

/* ===== 碰撞形状 ===== */

BW_Shape* bw_shape_sphere(float radius) {
    BW_TRY
    auto* s = new btSphereShape(radius);
    g_alloc_shapes.fetch_add(1, std::memory_order_relaxed);
    return (BW_Shape*)s;
    BW_CATCH_NULL
}

BW_Shape* bw_shape_box(float hx, float hy, float hz) {
    BW_TRY
    auto* s = new btBoxShape(btVector3(hx, hy, hz));
    g_alloc_shapes.fetch_add(1, std::memory_order_relaxed);
    return (BW_Shape*)s;
    BW_CATCH_NULL
}

BW_Shape* bw_shape_capsule(float radius, float height) {
    BW_TRY
    auto* s = new btCapsuleShape(radius, height);
    g_alloc_shapes.fetch_add(1, std::memory_order_relaxed);
    return (BW_Shape*)s;
    BW_CATCH_NULL
}

void bw_shape_destroy(BW_Shape* shape) {
    if (!shape) return;
    delete (btCollisionShape*)shape;
    g_alloc_shapes.fetch_sub(1, std::memory_order_relaxed);
}

/* ===== 刚体 ===== */

BW_RigidBody* bw_rigid_body_create(const BW_RigidBodyInfo* info) {
    if (!info || !info->shape) return nullptr;
    BW_TRY
    btCollisionShape* shape = (btCollisionShape*)info->shape;
    btTransform startTransform = mat4_to_bt(info->initial_transform);
    
    btVector3 localInertia(0, 0, 0);
    float mass = info->is_kinematic ? 0.0f : info->mass;
    if (mass > 0.0f) {
        shape->calculateLocalInertia(mass, localInertia);
    }
    
    btDefaultMotionState* motionState = new btDefaultMotionState(startTransform);
    g_alloc_motion_states.fetch_add(1, std::memory_order_relaxed);
    
    btRigidBody::btRigidBodyConstructionInfo rbInfo(mass, motionState, shape, localInertia);
    rbInfo.m_linearDamping = info->linear_damping;
    rbInfo.m_angularDamping = info->angular_damping;
    rbInfo.m_friction = info->friction;
    rbInfo.m_restitution = info->restitution;
    rbInfo.m_additionalDamping = info->additional_damping;
    
    btRigidBody* body = new btRigidBody(rbInfo);
    
    if (info->is_kinematic) {
        body->setCollisionFlags(
            body->getCollisionFlags() | btCollisionObject::CF_KINEMATIC_OBJECT);
        body->setActivationState(DISABLE_DEACTIVATION);
    } else if (info->disable_deactivation) {
        body->setActivationState(DISABLE_DEACTIVATION);
    }
    
    if (info->no_contact_response) {
        body->setCollisionFlags(
            body->getCollisionFlags() | btCollisionObject::CF_NO_CONTACT_RESPONSE);
    }
    
    g_alloc_rigid_bodies.fetch_add(1, std::memory_order_relaxed);
    return (BW_RigidBody*)body;
    BW_CATCH_NULL
}

void bw_rigid_body_destroy(BW_RigidBody* rb) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    if (body->getMotionState()) {
        delete body->getMotionState();
        g_alloc_motion_states.fetch_sub(1, std::memory_order_relaxed);
    }
    delete body;
    g_alloc_rigid_bodies.fetch_sub(1, std::memory_order_relaxed);
}

void bw_rigid_body_get_transform(BW_RigidBody* rb, float* matrix4x4) {
    if (!rb || !matrix4x4) return;
    btRigidBody* body = (btRigidBody*)rb;
    btTransform t;
    body->getMotionState()->getWorldTransform(t);
    bt_to_mat4(t, matrix4x4);
}

void bw_rigid_body_set_transform(BW_RigidBody* rb, const float* matrix4x4) {
    if (!rb || !matrix4x4) return;
    btRigidBody* body = (btRigidBody*)rb;
    btTransform t = mat4_to_bt(matrix4x4);
    body->setWorldTransform(t);
    body->setInterpolationWorldTransform(t);
    body->getMotionState()->setWorldTransform(t);
}

void bw_rigid_body_set_kinematic_target(BW_RigidBody* rb, const float* matrix4x4) {
    if (!rb || !matrix4x4) return;
    btRigidBody* body = (btRigidBody*)rb;
    btTransform t = mat4_to_bt(matrix4x4);

    /*
     * 运动学目标只写入 MotionState。Bullet 会在 saveKinematicState 中
     * 使用上一物理姿态和当前目标计算线速度及角速度。
     */
    if (body->getMotionState()) {
        body->getMotionState()->setWorldTransform(t);
    } else {
        body->setWorldTransform(t);
    }
    body->activate(true);
}

void bw_rigid_body_get_position(BW_RigidBody* rb, float* x, float* y, float* z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    btTransform t;
    body->getMotionState()->getWorldTransform(t);
    const btVector3& o = t.getOrigin();
    if (x) *x = o.x();
    if (y) *y = o.y();
    if (z) *z = o.z();
}

void bw_rigid_body_get_rotation(BW_RigidBody* rb, float* x, float* y, float* z, float* w) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    btTransform t;
    body->getMotionState()->getWorldTransform(t);
    btQuaternion q = t.getRotation();
    if (x) *x = q.x();
    if (y) *y = q.y();
    if (z) *z = q.z();
    if (w) *w = q.w();
}

void bw_rigid_body_set_linear_velocity(BW_RigidBody* rb, float x, float y, float z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setLinearVelocity(btVector3(x, y, z));
}

void bw_rigid_body_set_angular_velocity(BW_RigidBody* rb, float x, float y, float z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setAngularVelocity(btVector3(x, y, z));
}

void bw_rigid_body_get_linear_velocity(BW_RigidBody* rb, float* x, float* y, float* z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    const btVector3& v = body->getLinearVelocity();
    if (x) *x = v.x();
    if (y) *y = v.y();
    if (z) *z = v.z();
}

void bw_rigid_body_get_angular_velocity(BW_RigidBody* rb, float* x, float* y, float* z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    const btVector3& v = body->getAngularVelocity();
    if (x) *x = v.x();
    if (y) *y = v.y();
    if (z) *z = v.z();
}

void bw_rigid_body_set_damping(BW_RigidBody* rb, float linear, float angular) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setDamping(linear, angular);
}

void bw_rigid_body_set_friction(BW_RigidBody* rb, float friction) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setFriction(friction);
}

void bw_rigid_body_set_restitution(BW_RigidBody* rb, float restitution) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setRestitution(restitution);
}

void bw_rigid_body_set_activation_state(BW_RigidBody* rb, int state) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->setActivationState(state);
}

void bw_rigid_body_force_activation_state(BW_RigidBody* rb, int state) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->forceActivationState(state);
}

void bw_rigid_body_set_kinematic(BW_RigidBody* rb, bool kinematic) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    int flags = body->getCollisionFlags();
    if (kinematic) {
        flags |= btCollisionObject::CF_KINEMATIC_OBJECT;
    } else {
        flags &= ~btCollisionObject::CF_KINEMATIC_OBJECT;
    }
    body->setCollisionFlags(flags);
    body->setActivationState(DISABLE_DEACTIVATION);
}

float bw_rigid_body_get_mass(BW_RigidBody* rb) {
    if (!rb) return 0.0f;
    btRigidBody* body = (btRigidBody*)rb;
    float inv = body->getInvMass();
    return (inv > 0.0f) ? (1.0f / inv) : 0.0f;
}

void bw_rigid_body_clear_forces(BW_RigidBody* rb) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->clearForces();
}

void bw_rigid_body_apply_central_force(BW_RigidBody* rb, float x, float y, float z) {
    if (!rb) return;
    btRigidBody* body = (btRigidBody*)rb;
    body->applyCentralForce(btVector3(x, y, z));
}

void bw_rigid_body_set_ignore_collision_check(
    BW_RigidBody* rb, BW_RigidBody* other, bool ignore)
{
    if (!rb || !other) return;
    ((btRigidBody*)rb)->setIgnoreCollisionCheck((btRigidBody*)other, ignore);
}

bool bw_rigid_body_check_collide_with(BW_RigidBody* rb, BW_RigidBody* other) {
    if (!rb || !other) return false;
    return ((btRigidBody*)rb)->checkCollideWith((btRigidBody*)other);
}

/* ===== 6DOF 弹簧约束 ===== */

BW_Constraint* bw_6dof_spring_create(
    BW_RigidBody* a, BW_RigidBody* b,
    const float* frame_a_4x4, const float* frame_b_4x4,
    bool use_linear_ref_a)
{
    if (!a || !b || !frame_a_4x4 || !frame_b_4x4) return nullptr;
    
    btRigidBody* bodyA = (btRigidBody*)a;
    btRigidBody* bodyB = (btRigidBody*)b;
    btTransform frameA = mat4_to_bt(frame_a_4x4);
    btTransform frameB = mat4_to_bt(frame_b_4x4);
    
    BW_TRY
    btGeneric6DofSpringConstraint* constraint = new btGeneric6DofSpringConstraint(
        *bodyA, *bodyB, frameA, frameB, use_linear_ref_a);
    g_alloc_constraints.fetch_add(1, std::memory_order_relaxed);
    return (BW_Constraint*)constraint;
    BW_CATCH_NULL
}

void bw_constraint_destroy(BW_Constraint* c) {
    if (!c) return;
    delete (btTypedConstraint*)c;
    g_alloc_constraints.fetch_sub(1, std::memory_order_relaxed);
}

void bw_6dof_spring_set_linear_lower_limit(BW_Constraint* c, float x, float y, float z) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setLinearLowerLimit(btVector3(x, y, z));
}

void bw_6dof_spring_set_linear_upper_limit(BW_Constraint* c, float x, float y, float z) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setLinearUpperLimit(btVector3(x, y, z));
}

void bw_6dof_spring_set_angular_lower_limit(BW_Constraint* c, float x, float y, float z) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setAngularLowerLimit(btVector3(x, y, z));
}

void bw_6dof_spring_set_angular_upper_limit(BW_Constraint* c, float x, float y, float z) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setAngularUpperLimit(btVector3(x, y, z));
}

void bw_6dof_spring_enable_spring(BW_Constraint* c, int index, bool on) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->enableSpring(index, on);
}

void bw_6dof_spring_set_stiffness(BW_Constraint* c, int index, float stiffness) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setStiffness(index, stiffness);
}

void bw_6dof_spring_set_damping(BW_Constraint* c, int index, float damping) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setDamping(index, damping);
}

void bw_6dof_spring_set_equilibrium_point(BW_Constraint* c) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setEquilibriumPoint();
}

void bw_6dof_spring_set_param(BW_Constraint* c, int param, float value, int axis) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setParam(param, value, axis);
}

void bw_6dof_spring_use_frame_offset(BW_Constraint* c, bool on) {
    if (!c) return;
    btGeneric6DofSpringConstraint* con = (btGeneric6DofSpringConstraint*)c;
    con->setUseFrameOffset(on);
}

static float limit_violation(float value, float lower, float upper) {
    if (lower > upper) return 0.0f;
    if (value < lower) return lower - value;
    if (value > upper) return value - upper;
    return 0.0f;
}

bool bw_6dof_spring_get_diagnostic(
    BW_Constraint* c, BW_ConstraintDiagnostic* output)
{
    if (!c || !output) return false;
    auto* con = (btGeneric6DofSpringConstraint*)c;

    // Refresh Bullet's cached values so diagnostics work before the first step.
    con->calculateTransforms(
        con->getRigidBodyA().getCenterOfMassTransform(),
        con->getRigidBodyB().getCenterOfMassTransform());
    bt_to_mat4(con->getFrameOffsetA(), output->frame_a);
    bt_to_mat4(con->getFrameOffsetB(), output->frame_b);

    btVector3 linear_lower;
    btVector3 linear_upper;
    btVector3 angular_lower;
    btVector3 angular_upper;
    con->getLinearLowerLimit(linear_lower);
    con->getLinearUpperLimit(linear_upper);
    con->getAngularLowerLimit(angular_lower);
    con->getAngularUpperLimit(angular_upper);

    for (int axis = 0; axis < 3; ++axis) {
        output->linear_position[axis] = con->getRelativePivotPosition(axis);
        output->angular_position[axis] = con->getAngle(axis);
        output->linear_lower[axis] = linear_lower[axis];
        output->linear_upper[axis] = linear_upper[axis];
        output->angular_lower[axis] = angular_lower[axis];
        output->angular_upper[axis] = angular_upper[axis];
        output->linear_violation[axis] = limit_violation(
            output->linear_position[axis], linear_lower[axis], linear_upper[axis]);
        output->angular_violation[axis] = limit_violation(
            output->angular_position[axis], angular_lower[axis], angular_upper[axis]);
    }
    for (int axis = 0; axis < 6; ++axis) {
        output->stiffness[axis] = con->getStiffness(axis);
        output->damping[axis] = con->getDamping(axis);
        output->equilibrium[axis] = con->getEquilibriumPoint(axis);
        output->spring_enabled[axis] = con->isSpringEnabled(axis) ? 1 : 0;
    }
    output->use_frame_offset = con->getUseFrameOffset() ? 1 : 0;
    return true;
}
