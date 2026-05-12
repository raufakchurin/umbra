package ru.myit.vlevpn.runtime.olcrtc

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeEngine
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin

@Module
@InstallIn(SingletonComponent::class)
interface OlcRtcRuntimeModule {
    @Binds
    @IntoSet
    fun bindOlcRtcProtocolPlugin(impl: OlcRtcProtocolPlugin): RuntimeProtocolPlugin

    @Binds
    @IntoSet
    fun bindOlcRtcRuntimeEngine(impl: OlcRtcAndroidRuntime): OlcRtcRuntimeEngine
}
