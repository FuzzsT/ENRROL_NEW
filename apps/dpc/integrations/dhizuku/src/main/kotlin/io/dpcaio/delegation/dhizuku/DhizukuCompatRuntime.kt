package io.dpcaio.delegation.dhizuku

import io.dpcaio.delegation.ClientRegistry
import io.dpcaio.delegation.InMemoryClientRegistry

object DhizukuCompatRuntime {
    @Volatile
    var registry: ClientRegistry = InMemoryClientRegistry(emptyList())
        private set

    fun installRegistry(clientRegistry: ClientRegistry) {
        registry = clientRegistry
    }
}
