package com.beadpay.wrapper.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class ApiValidationError(
    val title:  String? = null,
    val status: Int?    = null,
    val errors: Map<String, List<String>>? = null
)

/**
 * Parses the raw JSON from the Bead API error body into a
 * human-readable multiline string (returns null if parsing fails).
 */
fun String.parseApiError(): String? = runCatching {
    val moshi   = Moshi.Builder().build()
    val adapter = moshi.adapter(ApiValidationError::class.java)
    val parsed  = adapter.fromJson(this) ?: return null

    buildString {
        appendLine(parsed.title ?: "Validation error")
        parsed.errors?.forEach { (field, msgs) ->
            msgs.forEach { appendLine(" • $field: $it") }
        }
        parsed.status?.let { appendLine("status: $it") }
    }
}.getOrNull()
