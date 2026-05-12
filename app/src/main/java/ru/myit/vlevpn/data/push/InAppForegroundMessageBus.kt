package ru.myit.vlevpn.data.push

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.myit.vlevpn.domain.model.InAppForegroundMessage

@Singleton
class InAppForegroundMessageBus @Inject constructor() {
    private val _message = MutableStateFlow<InAppForegroundMessage?>(null)
    private val queuedMessages = ArrayDeque<InAppForegroundMessage>()

    val message: StateFlow<InAppForegroundMessage?> = _message.asStateFlow()

    fun publish(message: InAppForegroundMessage): Boolean {
        synchronized(queuedMessages) {
            if (_message.value?.deliveryId == message.deliveryId || queuedMessages.any { it.deliveryId == message.deliveryId }) {
                return true
            }
            if (_message.value == null) {
                _message.value = message
            } else {
                queuedMessages.addLast(message)
            }
        }
        return true
    }

    fun clear(deliveryId: String) {
        synchronized(queuedMessages) {
            if (_message.value?.deliveryId == deliveryId) {
                _message.value = queuedMessages.pollFirst()
            }
        }
    }
}
