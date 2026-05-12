package ru.myit.vlevpn.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dagger.multibindings.IntoSet
import ru.myit.vlevpn.runtime.contract.OlcRtcRuntimeEngine
import ru.myit.vlevpn.runtime.contract.RuntimeProtocolPlugin
import ru.myit.vlevpn.runtime.plugin.XrayProtocolPlugin

@Module
@InstallIn(SingletonComponent::class)
interface RuntimePluginModule {
    @Binds
    @IntoSet
    fun bindXrayProtocolPlugin(impl: XrayProtocolPlugin): RuntimeProtocolPlugin

    @Multibinds
    fun bindOlcRtcRuntimeEngines(): Set<OlcRtcRuntimeEngine>
}
