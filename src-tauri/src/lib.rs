use std::{
    fs,
    io::{self, Read},
    path::PathBuf,
    process::Command,
    thread,
};
use tauri::Emitter;

#[tauri::command]
fn emacs_eval(elisp: String) -> Result<String, String> {
    let output = Command::new("emacsclient")
        .args(["-e", &elisp])
        .output()
        .map_err(|error| format!("Failed to run Elisp: {error}"))?;

    if output.status.success() {
        String::from_utf8(output.stdout)
            .map_err(|error| format!("Non-UTF-8 string returned from Emacs {error}"))
    } else {
        Err(String::from_utf8_lossy(&output.stderr).trim().to_owned())
    }
}

#[tauri::command]
fn read_user_chrome() -> Result<String, String> {
    let home = std::env::var_os("HOME").ok_or_else(|| "HOME is not set".to_owned())?;
    let path = PathBuf::from(home).join(".newmacs");
    match fs::read_to_string(path) {
        Ok(source) => Ok(source),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(String::new()),
        Err(error) => Err(error.to_string()),
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            let app_handle = app.handle().clone();
            let server = rouille::Server::new("127.0.0.1:0", move |request| {
                let Some(mut data) = request.data() else {
                    return rouille::Response::text("Missing request body").with_status_code(400);
                };
                let mut cljs = String::new();
                if data.read_to_string(&mut cljs).is_err() {
                    return rouille::Response::text("Malformed string").with_status_code(400);
                }
                if let Err(error) = app_handle.emit("newmacs-eval", cljs) {
                    eprintln!("Failed to run ClojureScript: {error}");
                    return rouille::Response::text("Internal server error").with_status_code(500);
                }
                rouille::Response::empty_204()
            })
            .map_err(io::Error::other)?;

            let port = server.server_addr().port();
            thread::Builder::new()
                .name("http-server".to_owned())
                .spawn(move || server.run())?;

            println!("Newmacs listening on port {port}");
            emacs_eval(format!("(defconst newmacs-port {port})"))?;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![emacs_eval, read_user_chrome])
        .run(tauri::generate_context!())
        .expect("Error while starting Tauri");
}
