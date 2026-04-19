mod app;
mod avatar_rig;
mod control_window;
mod room;
mod scene;
mod space;
mod vulkan;
mod xr;

fn main() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
        .format_timestamp_millis()
        .init();

    if let Err(err) = app::run() {
        eprintln!("VR Avatar Demo 启动失败: {err:#}");
        std::process::exit(1);
    }
}
