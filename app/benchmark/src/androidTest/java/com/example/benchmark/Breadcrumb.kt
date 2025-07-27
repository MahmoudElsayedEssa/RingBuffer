package com.example.benchmark

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Breadcrumb data class representing application events.
 * Uses Kotlin idioms like data classes, default parameters, and extension functions.
 */
data class Breadcrumb(
    val timestamp: Long,
    val eventType: String,
    val launchId: String,
    val extras: List<String> = emptyList(),
    val threadName: String = Thread.currentThread().name,
) {
    init {
        require(eventType.isNotBlank()) { "Event type cannot be blank" }
        require(launchId.isNotBlank()) { "Launch ID cannot be blank" }
        require(timestamp > 0) { "Timestamp must be positive" }
    }

    /**
     * Get formatted timestamp
     */
    val formattedTimestamp: String by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Convert to JSON object
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("eventType", eventType)
        put("launchId", launchId)
        put("extras", JSONArray(extras))
        put("threadName", threadName)
        put("formattedTimestamp", formattedTimestamp)
    }

    /**
     * Convert to compact JSON string
     */
    fun toJsonString(): String = toJson().toString()

    /**
     * Convert to formatted JSON string
     */
    fun toFormattedJsonString(): String = toJson().toString(2)

    companion object {

        /**
         * JSON serializer configuration
         */

        /**
         * Create breadcrumb with current timestamp
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            eventType: String,
            launchId: String,
            extras: List<String> = emptyList(),
            threadName: String = Thread.currentThread().name,
            timestamp: Long = System.currentTimeMillis(),
        ): Breadcrumb = Breadcrumb(
            timestamp = timestamp,
            eventType = eventType,
            launchId = launchId,
            extras = extras,
            threadName = threadName,
        )

        /**
         * Create breadcrumb from varargs extras
         */
        @JvmStatic
        fun create(eventType: String, launchId: String, vararg extras: String): Breadcrumb =
            create(eventType, launchId, extras.toList())

        /**
         * Create breadcrumb from map
         */
        @JvmStatic
        fun create(eventType: String, launchId: String, extras: Map<String, String>): Breadcrumb =
            create(eventType, launchId, extras.map { "${it.key}:${it.value}" })

        /**
         * Create from JSON object
         */
        @JvmStatic
        fun fromJson(json: JSONObject): Breadcrumb = Breadcrumb(
            timestamp = json.getLong("timestamp"),
            eventType = json.getString("eventType"),
            launchId = json.getString("launchId"),
            extras = json.getJSONArray("extras").let { array ->
                (0 until array.length()).map { array.getString(it) }
            },
            threadName = json.optString("threadName", Thread.currentThread().name),
        )
    }
}


fun List<Breadcrumb>.toJsonStringList() = map { it.toJsonString() }