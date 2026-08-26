package io.dpcaio.platform

object AndroidUserId {
    private const val PER_USER_RANGE = 100_000

    fun fromUid(uid: Int): Int = uid / PER_USER_RANGE
}
