/// Bullet3 C++ 编译脚本
///
/// 使用 cc crate 编译 Bullet3 源码和 C Wrapper，
/// 生成静态库链接到 Rust cdylib。

fn main() {
    let bullet3_dir = "deps/bullet3/src";
    let wrapper_dir = "bullet_wrapper";

    // 收集所有 Bullet3 .cpp 文件
    let mut cpp_files: Vec<String> = Vec::new();

    // LinearMath（排除 TaskScheduler 线程相关文件，我们不需要多线程物理）
    for entry in std::fs::read_dir(format!("{}/LinearMath", bullet3_dir)).unwrap() {
        let path = entry.unwrap().path();
        if path.extension().map_or(false, |e| e == "cpp") {
            cpp_files.push(path.to_string_lossy().into_owned());
        }
    }

    // BulletCollision 子目录
    let collision_subdirs = [
        "BroadphaseCollision",
        "CollisionDispatch",
        "CollisionShapes",
        "NarrowPhaseCollision",
        "Gimpact",
    ];
    for subdir in &collision_subdirs {
        let dir = format!("{}/BulletCollision/{}", bullet3_dir, subdir);
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries {
                let path = entry.unwrap().path();
                if path.extension().map_or(false, |e| e == "cpp") {
                    cpp_files.push(path.to_string_lossy().into_owned());
                }
            }
        }
    }

    // BulletDynamics 子目录
    let dynamics_subdirs = [
        "Character",
        "ConstraintSolver",
        "Dynamics",
        "Featherstone",
        "MLCPSolvers",
        "Vehicle",
    ];
    for subdir in &dynamics_subdirs {
        let dir = format!("{}/BulletDynamics/{}", bullet3_dir, subdir);
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries {
                let path = entry.unwrap().path();
                if path.extension().map_or(false, |e| e == "cpp") {
                    cpp_files.push(path.to_string_lossy().into_owned());
                }
            }
        }
    }

    // 排除依赖缺失头文件的源文件
    let exclude_files: &[&str] = &[
        "btCollisionWorldImporter",
        "btSerializer64",        // 64位序列化，不需要
    ];
    cpp_files.retain(|f| {
        !exclude_files.iter().any(|ex| f.contains(ex))
    });

    // C Wrapper
    cpp_files.push(format!("{}/bw_api.cpp", wrapper_dir));

    // 编译 Bullet3 + C Wrapper
    let mut build = cc::Build::new();
    build
        .cpp(true)
        .include(bullet3_dir)
        .include(wrapper_dir)
        .warnings(false)
        .opt_level(2);

    // 平台特定设置
    let target = std::env::var("TARGET").unwrap_or_default();
    if target.contains("msvc") {
        build.flag("/EHsc");  // C++ 异常处理
        build.flag("/std:c++17");
    } else {
        build.flag("-std=c++17");
        build.flag("-fno-exceptions");
        build.flag("-fno-rtti");
    }

    for file in &cpp_files {
        build.file(file);
    }

    build.compile("bullet3");

    // 告知 cargo 链接静态库
    println!("cargo:rustc-link-lib=static=bullet3");

    // MSVC 需要链接 C++ 运行时
    if target.contains("msvc") {
        // cc crate 自动处理
    } else if target.contains("apple") {
        println!("cargo:rustc-link-lib=c++");
    } else {
        println!("cargo:rustc-link-lib=stdc++");
    }

    // 重新编译条件
    println!("cargo:rerun-if-changed={}", wrapper_dir);
    println!("cargo:rerun-if-changed={}", bullet3_dir);
}
