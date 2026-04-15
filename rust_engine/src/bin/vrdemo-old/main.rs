mod app;
mod scene;
mod vulkan;
mod xr;

fn main() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
        .format_timestamp_millis()
        .init();

    if let Err(err) = app::run() {
        eprintln!("VR PMX Demo 启动失败: {err:#}");
        std::process::exit(1);
    }
}
