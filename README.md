# FriendNet for Android

An Android client for [FriendNet](https://github.com/termermc/FriendNet), a peer-to-peer
file sharing network developed by [termermc](https://github.com/termermc). This app embeds the FriendNet Go client and wraps it in a native UI.

<img width="550" height="550" alt="fnlog" src="https://github.com/user-attachments/assets/b43cb922-e737-4ed3-98d8-44a0c4caccdd" />

## Features

- **Servers** — add, edit, connect to and disconnect from FriendNet servers
- **Browse** — browse a peer's shared folders and files, search across peers
- **Downloads** — queue, pause, resume, cancel and remove downloads; tap **Open** on a
  completed download to view it in an external app
- **Settings** — transfer directory info, check for app updates
- **Logs** — read the app's logcat output in-app (including the Go backend's logs)
  and save it to a file for easy debugging

## How it works

The app cross-compiles the FriendNet Go client for Android and ships it as a native
library. At runtime a foreground service (`GoBackendService`) spawns the client binary
in headless mode:

- the backend listens on a Unix domain socket
- the app talks to it over gRPC with cleartext HTTP/2
- the RPC bearer token is read from the backend's startup logs

The Go submodule is kept **pristine** — no local patches are applied to `FriendNet/`.

## Download locations

- **Completed** downloads go to the public `Downloads/` folder.
- **In-progress** files are written to `Downloads/.friendnet-incomplete/` (inside the
  same directory tree as the completed files, so the completion rename never crosses
  filesystems).
- Opening a completed file requires **"All files access"**
  (`MANAGE_EXTERNAL_STORAGE`); the app prompts for it on first launch.

## Building

Requirements:

- Android SDK + NDK
- JDK 17+ (for example `JAVA_HOME=/home/afim/.androidstudio/jbr`)
- Go 1.26+ (a matching toolchain is auto-downloaded via `GOTOOLCHAIN` on first build)
- Internet access on the first build (to fetch Go modules and the toolchain)

Build the debug APK:

```sh
./gradlew :app:assembleDebug
```

`assembleDebug` runs the `buildGoBackend` task, which invokes `build_backend.sh` to
cross-compile the Go backend for all three ABIs (`arm64-v8a`, `x86_64`, `armeabi-v7a`)
into `app/src/main/jniLibs/`. The APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

To skip rebuilding the Go backend during UI-only iterations:

```sh
./gradlew :app:assembleDebug -x buildGoBackend
```

To update the Go backend to the latest upstream:

```sh
git -C FriendNet fetch origin master
git -C FriendNet checkout origin/master
git add FriendNet && git commit -m "Update FriendNet to <commit>"
```

## Debugging

The **Logs** tab shows the app's logcat output, which includes everything the Go
backend prints to stdout/stderr. Use the **Save** button to export the logs to a file. However if you're comfortable with ADB, it is recommended to attach your device via USB debugging and access the application logs that way since those logs are more readable.
