use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::RngCore;
use serde::Serialize;
use std::{
    fs,
    io::{self, Read, Write},
    net::TcpListener,
    path::PathBuf,
    process::Command,
    thread,
};
use tauri::Emitter;

#[derive(Serialize)]
struct EmacsBridge {
    port: u16,
    token: String,
}

#[tauri::command]
fn start_emacs_bridge() -> Result<EmacsBridge, String> {
    let listener = TcpListener::bind(("127.0.0.1", 0)).map_err(|error| error.to_string())?;
    let port = listener
        .local_addr()
        .map_err(|error| error.to_string())?
        .port();
    let mut token_bytes = [0_u8; 128];
    rand::thread_rng().fill_bytes(&mut token_bytes);
    let token = URL_SAFE_NO_PAD.encode(token_bytes);

    thread::spawn(move || {
        for stream in listener.incoming() {
            match stream {
                Ok(mut stream) => {
                    let mut request = [0_u8; 8192];
                    let _ = stream.read(&mut request);
                    let _ = stream.write_all(
                        b"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    );
                }
                Err(error) => eprintln!("Newmacs callback listener failed: {error}"),
            }
        }
    });

    Ok(EmacsBridge { port, token })
}

#[tauri::command]
fn emacs_eval(elisp: String) -> Result<String, String> {
    let output = Command::new("emacsclient")
        .args(["-e", &elisp])
        .output()
        .map_err(|error| format!("could not run emacsclient: {error}"))?;

    if output.status.success() {
        String::from_utf8(output.stdout).map_err(|error| error.to_string())
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

                if let Err(error) = app_handle.emit("cljs", cljs) {
                    eprintln!("Failed to run ClojureScript: {error}");
                    return rouille::Response::text("Internal server error").with_status_code(500);
                }

                rouille::Response::empty_204()
            })
            .map_err(io::Error::other)?;

            let port = server.server_addr().port();
            println!("Newmacs listening on port {port}");

            thread::Builder::new()
                .name("http-server".to_owned())
                .spawn(move || server.run())?;

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            start_emacs_bridge,
            emacs_eval,
            read_user_chrome
        ])
        .run(tauri::generate_context!())
        .expect("Error while starting Tauri");
}
