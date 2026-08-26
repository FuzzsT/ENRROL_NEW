package io.dpcaio.policy

enum class PolicyStatus {
    SUCCESS,
    UNSUPPORTED,
    NOT_DEVICE_OWNER,
    NOT_PROFILE_OWNER,
    NOT_AUTHORIZED,
    PACKAGE_NOT_FOUND,
    USER_NOT_FOUND,
    OEM_REQUIRED,
    KNOX_LICENSE_REQUIRED,
    PLATFORM_REJECTED,
    SECURITY_EXCEPTION,
    FAILED
}

data class PolicyResult<T>(
    val status: PolicyStatus,
    val value: T? = null,
    val message: String? = null,
    val errorType: String? = null
) {
    val isSuccess: Boolean get() = status == PolicyStatus.SUCCESS

    companion object {
        fun <T> success(value: T? = null, message: String? = null): PolicyResult<T> =
            PolicyResult(status = PolicyStatus.SUCCESS, value = value, message = message)

        fun <T> failure(
            status: PolicyStatus,
            message: String,
            errorType: String? = null
        ): PolicyResult<T> {
            require(status != PolicyStatus.SUCCESS) { "failure status cannot be SUCCESS" }
            return PolicyResult(status = status, message = message, errorType = errorType)
        }
    }
}
