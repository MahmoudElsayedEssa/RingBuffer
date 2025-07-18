package com.example.ringbuffer.breadcrumbs

data class Breadcrumb(
    val timestamp: Long,
    val eventType: String,
    val extras: List<String>,
    val threadName: String
) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(
            eventType: String,
            extras: List<String> = emptyList(),
            threadName: String = Thread.currentThread().name,
            timestamp: Long = System.currentTimeMillis()
        ): Breadcrumb = Breadcrumb(timestamp, eventType, extras, threadName)

        @JvmStatic
        fun create(eventType: String, vararg extras: String): Breadcrumb =
            create(eventType, extras.toList())

        @JvmStatic
        fun toJson(breadcrumbs: List<Breadcrumb>): String {
            return breadcrumbs.joinToString("\n") { breadcrumb ->
                breadcrumb.toJson()
            }
        }

        @JvmStatic
        fun Breadcrumb.toJson(): String {
            val extrasJson = this.extras.joinToString(",") { "\"$it\"" }
            return """{"timestamp":${this.timestamp},"eventType":"${this.eventType}","extras":[$extrasJson],"threadName":"${this.threadName}"}"""

        }
    }
}
