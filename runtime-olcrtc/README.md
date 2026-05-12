# runtime-olcrtc

Optional olcRTC integration module.

The module is intentionally disabled by default and can be excluded from the
application build without affecting the Xray runtime path:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug -PenableOlcRtcRuntime=true
```

Current status: native Android runtime path is wired behind the optional module:

- upstream olcRTC gomobile AAR is packaged as `libs/olcrtc.aar`;
- the AAR is rebuilt with a private Java package prefix and `libolcrtcjni.so`
  to avoid class/native-name conflicts with Xray's gomobile runtime;
- `hev-socks5-tunnel` is built through NDK and receives TUN traffic from
  Android `VpnService`;
- only `arm64-v8a` is packaged.

If this module is removed or the Gradle flag is not passed, the Xray runtime
continues to build and run independently.
