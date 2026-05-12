package ru.myit.vlevpn.runtime.olcrtc

internal object OlcRtcTun2Socks {
    private var loadError: Throwable? = null
    private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        loadError?.let { return false }
        return synchronized(this) {
            if (loaded) {
                true
            } else {
                try {
                    System.loadLibrary("hev-socks5-tunnel")
                    System.loadLibrary("vle_olcrtc_tun2socks")
                    loaded = true
                    true
                } catch (error: UnsatisfiedLinkError) {
                    loadError = error
                    false
                }
            }
        }
    }

    external fun startNative(configPath: String, fd: Int): Int
    external fun stopNative()
    external fun statsNative(): LongArray?
}
