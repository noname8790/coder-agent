use serde::Serialize;
use std::fs::OpenOptions;
use std::io::Write;
use std::net::{SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::thread;
use std::time::Duration;
use tauri::{Manager, State};

#[derive(Default)]
struct BackendProcess {
    child: Mutex<Option<Child>>,
}

impl Drop for BackendProcess {
    fn drop(&mut self) {
        if let Ok(child) = self.child.get_mut() {
            if let Some(mut child) = child.take() {
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}

#[derive(Serialize)]
struct BackendProcessStatus {
    running: bool,
    pid: Option<u32>,
    message: String,
    log_path: Option<String>,
}

#[tauri::command]
fn start_backend(
    java_path: String,
    jar_path: String,
    work_dir: String,
    port: u16,
    state: State<BackendProcess>,
) -> Result<BackendProcessStatus, String> {
    let mut guard = state
        .child
        .lock()
        .map_err(|_| "failed to lock backend process".to_string())?;
    if let Some(child) = guard.as_mut() {
        match child.try_wait() {
            Ok(None) => {
                return Ok(BackendProcessStatus {
                    running: true,
                    pid: Some(child.id()),
                    message: "backend is already managed by client".to_string(),
                    log_path: None,
                });
            }
            Ok(Some(_)) => {
                *guard = None;
            }
            Err(error) => {
                return Err(format!("failed to inspect managed backend: {}", error));
            }
        }
    }

    let work_dir_path = resolve_existing_dir(&work_dir)?;
    let jar_path = resolve_file(&jar_path, &work_dir_path)?;
    let java_path = resolve_java(&java_path)?;
    verify_java(&java_path)?;
    let log_path = work_dir_path.join("coder-agent-backend.log");
    let mut stdout = OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(&log_path)
        .map_err(|error| format!("failed to open backend log file: {}", error))?;
    writeln!(
        stdout,
        "coder-agent backend launch\njava={}\njar={}\nworkDir={}\nport={}\njarSize={}",
        java_path.display(),
        jar_path.display(),
        work_dir_path.display(),
        port,
        std::fs::metadata(&jar_path)
            .map_err(|error| format!("failed to inspect backend jar: {}", error))?
            .len()
    )
    .map_err(|error| format!("failed to write backend launch diagnostics: {}", error))?;
    stdout
        .flush()
        .map_err(|error| format!("failed to flush backend launch diagnostics: {}", error))?;
    let stderr = stdout
        .try_clone()
        .map_err(|error| format!("failed to clone backend log file: {}", error))?;

    let child = Command::new(&java_path)
        .arg("-jar")
        .arg(&jar_path)
        .arg(format!("--server.port={}", port))
        .current_dir(&work_dir_path)
        .env("SERVER_PORT", port.to_string())
        .env("SERVER_ADDRESS", "127.0.0.1")
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr))
        .spawn()
        .map_err(|error| format!("failed to start backend: {}", error))?;

    let mut child = child;
    let address = SocketAddr::from(([127, 0, 0, 1], port));
    let mut ready = false;
    for _ in 0..60 {
        match child.try_wait() {
            Ok(Some(status)) => {
                return Err(format!(
                    "backend exited with status {}. log={}",
                    status,
                    log_path.display()
                ));
            }
            Ok(None) => {}
            Err(error) => {
                return Err(format!("failed to inspect backend process: {}", error));
            }
        }
        if TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok() {
            ready = true;
            break;
        }
        thread::sleep(Duration::from_millis(250));
    }
    if !ready {
        let _ = child.kill();
        let _ = child.wait();
        return Err(format!(
            "backend did not listen on 127.0.0.1:{} within 15 seconds. log={}",
            port,
            log_path.display()
        ));
    }

    let pid = child.id();
    *guard = Some(child);
    Ok(BackendProcessStatus {
        running: true,
        pid: Some(pid),
        message: format!(
            "backend started on 127.0.0.1:{}, log={}",
            port,
            log_path.display()
        ),
        log_path: Some(log_path.to_string_lossy().to_string()),
    })
}

#[tauri::command]
fn stop_backend(state: State<BackendProcess>) -> Result<BackendProcessStatus, String> {
    if stop_managed_backend(&state)? {
        return Ok(BackendProcessStatus {
            running: false,
            pid: None,
            message: "backend stopped".to_string(),
            log_path: None,
        });
    }
    Ok(BackendProcessStatus {
        running: false,
        pid: None,
        message: "no backend process managed by client".to_string(),
        log_path: None,
    })
}

#[tauri::command]
fn select_workspace_directory() -> Option<String> {
    rfd::FileDialog::new()
        .set_title("选择代码仓库目录")
        .pick_folder()
        .map(normalize_windows_path)
        .map(|path| path.to_string_lossy().to_string())
}

fn stop_managed_backend(state: &BackendProcess) -> Result<bool, String> {
    let mut guard = state
        .child
        .lock()
        .map_err(|_| "failed to lock backend process".to_string())?;
    if let Some(mut child) = guard.take() {
        let _ = child.kill();
        let _ = child.wait();
        return Ok(true);
    }
    Ok(false)
}

fn resolve_existing_dir(value: &str) -> Result<PathBuf, String> {
    let path = PathBuf::from(value);
    let resolved = if path.is_absolute() {
        path
    } else {
        std::env::current_dir()
            .map_err(|error| format!("failed to resolve current dir: {}", error))?
            .join(path)
    };
    let resolved = resolved
        .canonicalize()
        .map_err(|error| format!("workDir does not exist: {} ({})", value, error))?;
    let resolved = normalize_windows_path(resolved);
    if !resolved.is_dir() {
        return Err(format!(
            "workDir is not a directory: {}",
            resolved.display()
        ));
    }
    Ok(resolved)
}

fn resolve_file(value: &str, work_dir: &Path) -> Result<PathBuf, String> {
    let path = PathBuf::from(value);
    let candidates = if path.is_absolute() {
        vec![path]
    } else {
        vec![
            work_dir.join(&path),
            std::env::current_dir()
                .map_err(|error| format!("failed to resolve current dir: {}", error))?
                .join(&path),
        ]
    };
    for candidate in candidates {
        if candidate.is_file() {
            return candidate
                .canonicalize()
                .map(normalize_windows_path)
                .map_err(|error| format!("failed to resolve jar path: {}", error));
        }
    }
    Err(format!("backend jar not found: {}", value))
}

fn resolve_java(value: &str) -> Result<PathBuf, String> {
    let configured = value.trim();
    if !configured.is_empty() && configured != "java" {
        let path = PathBuf::from(configured);
        if path.is_file() {
            return path
                .canonicalize()
                .map(normalize_windows_path)
                .map_err(|error| format!("failed to resolve java path: {}", error));
        }
        return Err(format!("java executable not found: {}", configured));
    }

    for variable in ["CODER_AGENT_JAVA_HOME", "JAVA_HOME"] {
        if let Ok(home) = std::env::var(variable) {
            let candidate = PathBuf::from(home).join("bin").join("java.exe");
            if candidate.is_file() {
                return candidate
                    .canonicalize()
                    .map(normalize_windows_path)
                    .map_err(|error| format!("failed to resolve {} java: {}", variable, error));
            }
        }
    }

    for root in [
        PathBuf::from(r"C:\Program Files\Java"),
        PathBuf::from(r"C:\Program Files\Eclipse Adoptium"),
        PathBuf::from(r"E:\Java"),
    ] {
        if let Some(candidate) = find_jdk_21_java(&root) {
            return candidate
                .canonicalize()
                .map(normalize_windows_path)
                .map_err(|error| format!("failed to resolve discovered Java 21: {}", error));
        }
    }
    Ok(PathBuf::from("java"))
}

fn normalize_windows_path(path: PathBuf) -> PathBuf {
    let value = path.to_string_lossy();
    if let Some(value) = value.strip_prefix(r"\\?\UNC\") {
        return PathBuf::from(format!(r"\\{}", value));
    }
    if let Some(value) = value.strip_prefix(r"\\?\") {
        return PathBuf::from(value);
    }
    path
}

fn verify_java(java_path: &Path) -> Result<(), String> {
    let output = Command::new(java_path)
        .arg("-version")
        .output()
        .map_err(|error| {
            format!(
                "java executable cannot be started: {} ({})",
                java_path.display(),
                error
            )
        })?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!(
            "java executable validation failed: {} ({})",
            java_path.display(),
            stderr.trim()
        ));
    }
    Ok(())
}

fn find_jdk_21_java(root: &Path) -> Option<PathBuf> {
    let entries = std::fs::read_dir(root).ok()?;
    entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| name.to_ascii_lowercase().contains("jdk-21"))
        })
        .map(|path| path.join("bin").join("java.exe"))
        .find(|path| path.is_file())
}

#[cfg(test)]
mod tests {
    use super::normalize_windows_path;
    use std::path::PathBuf;

    #[test]
    fn given_windows_verbatim_drive_path_when_normalizing_then_remove_verbatim_prefix() {
        let path = PathBuf::from(r"\\?\E:\IdeaProjects\coder-agent\coder-agent.jar");

        let normalized = normalize_windows_path(path);

        assert_eq!(
            normalized,
            PathBuf::from(r"E:\IdeaProjects\coder-agent\coder-agent.jar")
        );
    }

    #[test]
    fn given_windows_verbatim_unc_path_when_normalizing_then_keep_unc_path() {
        let path = PathBuf::from(r"\\?\UNC\server\share\coder-agent.jar");

        let normalized = normalize_windows_path(path);

        assert_eq!(normalized, PathBuf::from(r"\\server\share\coder-agent.jar"));
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app = tauri::Builder::default()
        .manage(BackendProcess::default())
        .invoke_handler(tauri::generate_handler![
            start_backend,
            stop_backend,
            select_workspace_directory
        ])
        .build(tauri::generate_context!())
        .expect("failed to run coder-agent client");
    app.run(|app_handle, event| {
        if matches!(
            event,
            tauri::RunEvent::WindowEvent {
                event: tauri::WindowEvent::CloseRequested { .. },
                ..
            } | tauri::RunEvent::ExitRequested { .. }
                | tauri::RunEvent::Exit
        ) {
            let state = app_handle.state::<BackendProcess>();
            let _ = stop_managed_backend(&state);
        }
    });
}
