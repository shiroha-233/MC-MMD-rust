use egui::{Color32, ComboBox, RichText, TextEdit};
use rfd::FileDialog;

#[derive(Clone, Copy)]
pub struct ViewerSnapshot {
    pub has_model: bool,
    pub has_animation: bool,
    pub playing: bool,
    pub current_frame: f32,
}

pub enum ViewerAction {
    LoadModel(String),
    RefreshFbxStacks(String),
    LoadAnimation {
        path: String,
        stack_name: Option<String>,
    },
    ClearAnimation,
    ToggleAnimation,
    ResetAnimation,
    PersistState,
}

struct StatusMessage {
    text: String,
    is_error: bool,
}

pub struct ViewerGuiState {
    model_path: String,
    animation_path: String,
    fbx_stacks: Vec<String>,
    selected_fbx_stack: usize,
    status: Option<StatusMessage>,
}

impl ViewerGuiState {
    pub fn new(model_path: Option<String>, animation_path: Option<String>) -> Self {
        Self {
            model_path: model_path.unwrap_or_default(),
            animation_path: animation_path.unwrap_or_default(),
            fbx_stacks: Vec::new(),
            selected_fbx_stack: 0,
            status: None,
        }
    }

    pub fn set_animation_path(&mut self, path: String) {
        self.animation_path = path;
        if !self.is_fbx_path() {
            self.clear_fbx_stacks();
        }
    }

    pub fn set_fbx_stacks(&mut self, stacks: Vec<String>) {
        self.fbx_stacks = stacks;
        self.selected_fbx_stack = 0;
    }

    pub fn restore_selected_fbx_stack(&mut self, stack_name: Option<&str>) {
        let Some(stack_name) = stack_name else {
            self.selected_fbx_stack = 0;
            return;
        };

        if let Some(index) = self.fbx_stacks.iter().position(|name| name == stack_name) {
            self.selected_fbx_stack = index;
        } else {
            self.selected_fbx_stack = 0;
        }
    }

    pub fn model_path(&self) -> &str {
        &self.model_path
    }

    pub fn animation_path(&self) -> &str {
        &self.animation_path
    }

    pub fn selected_stack_name(&self) -> Option<String> {
        if self.is_fbx_path() {
            self.fbx_stacks.get(self.selected_fbx_stack).cloned()
        } else {
            None
        }
    }

    pub fn set_info<T: Into<String>>(&mut self, message: T) {
        self.status = Some(StatusMessage {
            text: message.into(),
            is_error: false,
        });
    }

    pub fn set_error<T: Into<String>>(&mut self, message: T) {
        self.status = Some(StatusMessage {
            text: message.into(),
            is_error: true,
        });
    }

    pub fn show(&mut self, ctx: &egui::Context, snapshot: ViewerSnapshot) -> Vec<ViewerAction> {
        let mut actions = Vec::new();

        egui::SidePanel::left("viewer_controls")
            .default_width(360.0)
            .min_width(300.0)
            .show(ctx, |ui| {
                ui.heading("资源面板");
                ui.label("在这里选择 PMX 模型和 VMD/FBX 动作。");
                ui.small("选择内容会自动保存，下次打开 viewer 会恢复。");

                if let Some(status) = &self.status {
                    let color = if status.is_error {
                        Color32::from_rgb(210, 70, 70)
                    } else {
                        Color32::from_rgb(80, 170, 95)
                    };
                    ui.colored_label(color, &status.text);
                }

                ui.separator();
                ui.label(RichText::new("PMX 模型").strong());
                if ui
                    .add(
                        TextEdit::singleline(&mut self.model_path)
                            .hint_text("选择 .pmx 文件")
                            .desired_width(f32::INFINITY),
                    )
                    .changed()
                {
                    actions.push(ViewerAction::PersistState);
                }

                ui.horizontal(|ui| {
                    if ui.button("浏览 PMX").clicked() {
                        if let Some(path) = FileDialog::new()
                            .add_filter("PMX Model", &["pmx"])
                            .pick_file()
                        {
                            self.model_path = path.display().to_string();
                            actions.push(ViewerAction::PersistState);
                        }
                    }

                    if ui
                        .add_enabled(
                            !self.model_path.trim().is_empty(),
                            egui::Button::new("加载模型"),
                        )
                        .clicked()
                    {
                        actions.push(ViewerAction::LoadModel(self.model_path.trim().to_string()));
                    }
                });

                ui.separator();
                ui.label(RichText::new("动作文件").strong());
                if ui
                    .add(
                        TextEdit::singleline(&mut self.animation_path)
                            .hint_text("选择 .vmd 或 .fbx 文件")
                            .desired_width(f32::INFINITY),
                    )
                    .changed()
                {
                    if !self.is_fbx_path() {
                        self.clear_fbx_stacks();
                    }
                    actions.push(ViewerAction::PersistState);
                }

                ui.horizontal(|ui| {
                    if ui.button("浏览动作").clicked() {
                        if let Some(path) = FileDialog::new()
                            .add_filter("Animation", &["vmd", "fbx"])
                            .pick_file()
                        {
                            self.animation_path = path.display().to_string();
                            if !self.is_fbx_path() {
                                self.clear_fbx_stacks();
                            }
                            actions.push(ViewerAction::PersistState);
                            if self.is_fbx_path() {
                                actions.push(ViewerAction::RefreshFbxStacks(
                                    self.animation_path.trim().to_string(),
                                ));
                            }
                        }
                    }

                    if ui
                        .add_enabled(
                            self.is_fbx_path() && !self.animation_path.trim().is_empty(),
                            egui::Button::new("读取 FBX 动作列表"),
                        )
                        .clicked()
                    {
                        actions.push(ViewerAction::RefreshFbxStacks(
                            self.animation_path.trim().to_string(),
                        ));
                    }
                });

                if self.is_fbx_path() {
                    if self.fbx_stacks.is_empty() {
                        ui.label("多 Stack FBX 可先读取列表；单 Stack FBX 也可直接加载。");
                    } else {
                        let selected_text = self
                            .fbx_stacks
                            .get(self.selected_fbx_stack)
                            .map(String::as_str)
                            .unwrap_or("默认首个动作");
                        let before = self.selected_fbx_stack;
                        ComboBox::from_label("FBX 动作")
                            .selected_text(selected_text)
                            .show_ui(ui, |ui| {
                                for (index, stack_name) in self.fbx_stacks.iter().enumerate() {
                                    ui.selectable_value(
                                        &mut self.selected_fbx_stack,
                                        index,
                                        stack_name,
                                    );
                                }
                            });
                        if before != self.selected_fbx_stack {
                            actions.push(ViewerAction::PersistState);
                        }
                    }
                }

                ui.horizontal(|ui| {
                    if ui
                        .add_enabled(
                            snapshot.has_model && !self.animation_path.trim().is_empty(),
                            egui::Button::new("加载动作"),
                        )
                        .clicked()
                    {
                        actions.push(ViewerAction::LoadAnimation {
                            path: self.animation_path.trim().to_string(),
                            stack_name: self.selected_stack_name(),
                        });
                    }

                    if ui
                        .add_enabled(snapshot.has_animation, egui::Button::new("清除动作"))
                        .clicked()
                    {
                        actions.push(ViewerAction::ClearAnimation);
                    }
                });

                ui.separator();
                ui.label(RichText::new("播放控制").strong());
                ui.horizontal(|ui| {
                    let play_label = if snapshot.playing { "暂停" } else { "播放" };
                    if ui
                        .add_enabled(snapshot.has_animation, egui::Button::new(play_label))
                        .clicked()
                    {
                        actions.push(ViewerAction::ToggleAnimation);
                    }

                    if ui
                        .add_enabled(snapshot.has_animation, egui::Button::new("重置到第 0 帧"))
                        .clicked()
                    {
                        actions.push(ViewerAction::ResetAnimation);
                    }
                });

                ui.separator();
                ui.label(RichText::new("状态").strong());
                ui.label(format!(
                    "模型: {}",
                    if snapshot.has_model {
                        "已加载"
                    } else {
                        "未加载"
                    }
                ));
                ui.label(format!(
                    "动画: {}",
                    if snapshot.has_animation {
                        "已加载"
                    } else {
                        "未加载"
                    }
                ));
                ui.label(format!(
                    "播放: {}",
                    if snapshot.playing {
                        "进行中"
                    } else {
                        "已暂停"
                    }
                ));
                ui.label(format!("当前帧: {:.2}", snapshot.current_frame));

                ui.separator();
                ui.label(RichText::new("视图快捷键").strong());
                ui.label("WASD/QE 移动，右键旋转，滚轮缩放");
                ui.label("Space 播放/暂停，R 重置动画");
                ui.label("B/G/X/F 切换骨骼、网格、坐标轴、线框");
                ui.label("1/2/3/4 切换正面、侧面、顶部、自由视角");
            });

        actions
    }

    fn is_fbx_path(&self) -> bool {
        self.animation_path
            .trim()
            .to_ascii_lowercase()
            .ends_with(".fbx")
    }

    fn clear_fbx_stacks(&mut self) {
        self.fbx_stacks.clear();
        self.selected_fbx_stack = 0;
    }
}
