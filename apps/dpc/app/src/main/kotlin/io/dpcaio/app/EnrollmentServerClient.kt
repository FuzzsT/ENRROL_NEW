package io.dpcaio.app

import io.dpcaio.core.model.EnrollmentSession
import org.json.JSONObject

class EnrollmentServerClient(endpoint: String) {
    private val http = EnrollmentHttpClient(endpoint)

    fun reserve(token: String, session: EnrollmentSession): HttpJsonResponse = http.postJson(
        "/v2/enrollments/reserve",
        JSONObject().apply {
            put("enrollmentToken", token)
            put("requestId", "${session.sessionId}:reserve")
            put("sessionId", session.sessionId)
            put("provisioningMode", session.requestedMode)
            put("policyProfile", session.policyProfile)
        },
    )

    fun validate(session: EnrollmentSession, reservationId: String, deviceFacts: JSONObject): HttpJsonResponse = http.postJson(
        "/v2/enrollments/validate",
        JSONObject().apply {
            put("requestId", "${session.sessionId}:validate")
            put("sessionId", session.sessionId)
            put("reservationId", reservationId)
            put("deviceFacts", deviceFacts)
        },
    )

    fun bootstrap(session: EnrollmentSession, reservationId: String): HttpJsonResponse = http.postJson(
        "/v2/enrollments/bootstrap",
        JSONObject().apply {
            put("requestId", "${session.sessionId}:bootstrap")
            put("sessionId", session.sessionId)
            put("reservationId", reservationId)
        },
    )

    fun commit(session: EnrollmentSession, reservationId: String): HttpJsonResponse = http.postJson(
        "/v2/enrollments/commit",
        JSONObject().apply {
            put("requestId", "${session.sessionId}:commit")
            put("sessionId", session.sessionId)
            put("reservationId", reservationId)
        },
    )

    fun release(session: EnrollmentSession, reservationId: String): HttpJsonResponse = http.postJson(
        "/v2/enrollments/release",
        JSONObject().apply {
            put("requestId", "${session.sessionId}:release")
            put("sessionId", session.sessionId)
            put("reservationId", reservationId)
        },
    )
}
