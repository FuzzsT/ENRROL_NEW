package io.dpcaio.samsung.settings.android

interface KnoxDeepSettingsGateway {
    fun isAvailable(): Boolean
    fun supports(key: String): Boolean
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
}
