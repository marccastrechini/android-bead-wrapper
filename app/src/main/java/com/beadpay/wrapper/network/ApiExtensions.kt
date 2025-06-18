package com.beadpay.wrapper.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
data class ApiValidationError(
    val title:  String?                    = null,
    val status: Int?                       = null,
    val errors: Map<String, List<String>>? = null
)

fun HttpException.readProblemDetail(moshi: Moshi): String {
    val raw = response()?.errorBody()?.string().orEmpty()
    return try {
        val dto = moshi.adapter(ApiValidationError::class.java).fromJson(raw)
        buildString {
            appendLine(dto?.title ?: "Validation error")
            dto?.errors?.forEach { (field, msgs) ->
                msgs.forEach { msg -> appendLine("• $field: $msg") }
            }
        }
    } catch (_: Exception) {
        if (raw.isBlank()) "HTTP ${code()} ${message()}" else raw
    }
}
