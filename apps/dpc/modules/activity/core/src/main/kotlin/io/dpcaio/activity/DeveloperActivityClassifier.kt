package io.dpcaio.activity

object DeveloperActivityClassifier {
    private val tokens = listOf(
        "debug",
        "developer",
        "engineering",
        "diagnostic",
        "test",
        "internal",
        "hidden",
        "lab",
    )

    fun isDeveloperLike(activity: DiscoveredActivity): Boolean {
        val haystack = buildString {
            append(activity.packageName)
            append(' ')
            append(activity.className)
            append(' ')
            append(activity.requiredPermission.orEmpty())
        }.lowercase()
        return tokens.any(haystack::contains)
    }
}
