# Newmacs
ClojureScript + Shadow-cljs + Tauri + Reagent

## How to Run

Install Node.js, Rust, and the [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/)
for your platform, then:

```
npm install
npm run dev
```

## Release
```
npm run build
```

On macOS this produces `src-tauri/target/release/bundle/macos/Newmacs.app`.

The renderer is still implemented in ClojureScript. Tauri's Rust backend owns
the native filesystem, process, and callback-listener operations that cannot
run in a sandboxed webview.
