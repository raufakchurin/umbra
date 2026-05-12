# Xray Android Core

The app uses an official `XTLS/libXray` Android AAR built locally from source.
`libXray` wraps upstream `github.com/xtls/xray-core` and exposes the Android
API needed by this app:

- `setTunFd(int)`
- `runXrayFromJSON(...)`
- `stopXray()`
- `registerDialerController(...)`
- `registerListenerController(...)`
- `initDns(...)`

Build/update the AAR:

```bash
brew install go
/Users/shibzuko/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager 'ndk;27.2.12479018'
tools/xray/build_libxray_android.sh
```

The cloned source lives under `third_party/libXray/` and is intentionally
ignored. The app consumes the generated `app/libs/libXray.aar`.
