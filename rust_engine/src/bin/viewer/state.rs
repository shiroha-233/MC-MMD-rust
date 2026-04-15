use std::{
    fs,
    path::{Path, PathBuf},
};

use serde_json::{json, Value};

pub struct ViewerPersistedState {
    pub model_path: String,
    pub animation_path: String,
    pub selected_fbx_stack: Option<String>,
}

impl ViewerPersistedState {
    pub fn load() -> Option<Self> {
        let content = fs::read_to_string(state_file_path()).ok()?;
        let value: Value = serde_json::from_str(&content).ok()?;
        Some(Self {
            model_path: value
                .get("model_path")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            animation_path: value
                .get("animation_path")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string(),
            selected_fbx_stack: value
                .get("selected_fbx_stack")
                .and_then(Value::as_str)
                .map(ToString::to_string),
        })
    }

    pub fn save(&self) -> Result<(), String> {
        let path = state_file_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| format!("创建配置目录失败: {}", error))?;
        }

        let content = serde_json::to_string_pretty(&json!({
            "model_path": self.model_path,
            "animation_path": self.animation_path,
            "selected_fbx_stack": self.selected_fbx_stack,
        }))
        .map_err(|error| format!("序列化 viewer 配置失败: {}", error))?;

        fs::write(path, content).map_err(|error| format!("写入 viewer 配置失败: {}", error))
    }
}

fn state_file_path() -> PathBuf {
    app_data_dir()
        .unwrap_or_else(|| {
            std::env::current_dir()
                .unwrap_or_else(|_| PathBuf::from("."))
                .join(".viewer")
        })
        .join("viewer_state.json")
}

fn app_data_dir() -> Option<PathBuf> {
    std::env::var_os("APPDATA")
        .map(PathBuf::from)
        .filter(|path| path.is_absolute())
        .map(|path| path.join(Path::new("mmdskin").join("viewer")))
}
