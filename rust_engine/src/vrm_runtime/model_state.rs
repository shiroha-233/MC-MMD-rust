use crate::model::{MmdModel, VrmExtensions};

use super::{
    ConstraintRuntime, ControlRigRuntime, ExpressionRuntime, FirstPersonRuntime,
    FirstPersonSnapshot, LookAtRuntime, SpringBoneRuntime, VrmRuntimeInput, VrmRuntimeOutput,
};

pub(crate) struct VrmModelRuntimeState {
    pub(crate) control_rig: ControlRigRuntime,
    pub(crate) constraints: ConstraintRuntime,
    pub(crate) look_at: LookAtRuntime,
    pub(crate) expression: ExpressionRuntime,
    pub(crate) first_person: FirstPersonRuntime,
    pub(crate) spring_bone: SpringBoneRuntime,
    pub(crate) input: VrmRuntimeInput,
    pub(crate) output: VrmRuntimeOutput,
}

impl VrmModelRuntimeState {
    pub(crate) fn new(model: &mut MmdModel, extensions: VrmExtensions) -> Self {
        Self {
            control_rig: ControlRigRuntime::new(model),
            constraints: ConstraintRuntime::new(extensions.node_constraints, &model.bone_manager),
            look_at: LookAtRuntime::new(extensions.look_at.clone()),
            expression: ExpressionRuntime::new(&extensions.expressions),
            first_person: FirstPersonRuntime::new(
                extensions.first_person,
                extensions.look_at.clone(),
            ),
            spring_bone: SpringBoneRuntime::new(extensions.spring_bones, &model.bone_manager),
            input: VrmRuntimeInput::default(),
            output: VrmRuntimeOutput::default(),
        }
    }

    pub(crate) fn initialize_output(&mut self, model: &mut MmdModel) {
        let baseline_visible = (0..model.material_count())
            .map(|index| model.is_material_visible(index))
            .collect::<Vec<_>>();
        let snapshot =
            self.first_person
                .capture(model, &baseline_visible, model.is_first_person_enabled());
        self.apply_snapshot(model, snapshot);
    }

    pub(crate) fn apply_inputs(&mut self, model: &mut MmdModel) {
        self.control_rig.apply_tracking(
            model,
            self.input.tracking,
            self.input.hand_calibration,
            self.input.body_calibration,
        );
    }

    pub(crate) fn process_expressions(&mut self, model: &mut MmdModel) {
        let eye_direction = self.look_at.resolve(
            self.input.look_at,
            self.input.tracking.map(|tracking| tracking.head.position),
        );
        self.look_at.apply_to_model(model, eye_direction);
        let expression_state = self.expression.process(
            model,
            &self.input.expression_weights,
            eye_direction,
            self.look_at.look_at_type(),
        );
        self.output.eye_direction = eye_direction;
        self.output.input_weights = expression_state.input_weights;
        self.output.actual_weights = expression_state.actual_weights;
        self.output.blink_override_rate = expression_state.blink_override_rate;
        self.output.look_at_override_rate = expression_state.look_at_override_rate;
        self.output.mouth_override_rate = expression_state.mouth_override_rate;
    }

    pub(crate) fn process_post_ik(&mut self, model: &mut MmdModel, delta_time: f32) {
        self.constraints.process(&mut model.bone_manager);
        self.spring_bone
            .process(&mut model.bone_manager, delta_time);
    }

    pub(crate) fn refresh_output(&mut self, model: &mut MmdModel) {
        let baseline_visible = if self.output.mirror_visible_materials.is_empty() {
            (0..model.material_count())
                .map(|index| model.is_material_visible(index))
                .collect::<Vec<_>>()
        } else {
            self.output.mirror_visible_materials.clone()
        };
        let snapshot = self
            .first_person
            .capture(model, &baseline_visible, self.input.first_person);
        self.apply_snapshot(model, snapshot);
    }

    fn apply_snapshot(&mut self, model: &mut MmdModel, snapshot: FirstPersonSnapshot) {
        self.output.hmd_visible_materials = snapshot.hmd_visible_materials.clone();
        self.output.mirror_visible_materials = snapshot.mirror_visible_materials.clone();
        self.output.first_person_source = snapshot.source.to_string();
        self.output.view_anchor_model = self.first_person.resolve_view_anchor_model(model);
        self.output.head_rotation = model
            .tracked_head_bone_index()
            .map(|index| {
                glam::Quat::from_mat4(&model.bone_manager.get_global_transform(index)).normalize()
            })
            .unwrap_or(glam::Quat::IDENTITY);
        let vr_debug = model.vr_debug_state();
        self.output.head_local_model = vr_debug.head_local_model;
        self.output.body_anchor_model = vr_debug.body_anchor_model;
        self.output.left_palm_target_model = vr_debug.left_palm_target_model;
        self.output.right_palm_target_model = vr_debug.right_palm_target_model;
        self.output.left_wrist_solved_model = vr_debug.left_wrist_solved_model;
        self.output.right_wrist_solved_model = vr_debug.right_wrist_solved_model;
        self.output.left_wrist_error_cm = vr_debug.left_wrist_error_cm;
        self.output.right_wrist_error_cm = vr_debug.right_wrist_error_cm;

        model.replace_material_visibility(if self.input.first_person {
            snapshot.hmd_visible_materials
        } else {
            snapshot.mirror_visible_materials
        });
    }
}
