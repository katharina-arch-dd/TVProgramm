package kat.android.tvprogramm.data.model

import com.google.gson.annotations.SerializedName
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class TvCategory(val displayName: String) {
    ALL("Alle"),
    MOVIE("Filme"),
    SERIES("Serien"),
    DOCUMENTARY("Dokumentation"),
    NEWS("Nachrichten"),
    SPORT("Sport"),
    ENTERTAINMENT("Unterhaltung"),
    KIDS("Kinder")
}

data class TvChannel(
    val id: String,
    val name: String,
    val logoText: String,
    val category: String = "Hauptsender",
    val channelNumber: Int = 1
)

data class TvShow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val channelId: String,
    val channelName: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val description: String,
    val category: TvCategory,
    val genre: String? = null,
    val ageRating: String? = "FSK 12",
    val year: String? = null,
    val country: String? = null,
    val director: String? = null,
    val cast: List<String> = emptyList(),
    val isHighlight: Boolean = false,
    val imageUrl: String? = null
) {
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes().coerceAtLeast(1)

    fun isLive(now: LocalDateTime = LocalDateTime.now()): Boolean {
        return now.isAfter(startTime) && now.isBefore(endTime)
    }

    fun getProgressPercent(now: LocalDateTime = LocalDateTime.now()): Float {
        if (!isLive(now)) return if (now.isAfter(endTime)) 1.0f else 0.0f
        val total = Duration.between(startTime, endTime).toMillis().toFloat()
        if (total <= 0f) return 0f
        val elapsed = Duration.between(startTime, now).toMillis().toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    fun getRemainingMinutes(now: LocalDateTime = LocalDateTime.now()): Long {
        if (!isLive(now)) return 0
        return Duration.between(now, endTime).toMinutes().coerceAtLeast(0)
    }

    val formattedTimeSpan: String
        get() {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            return "${startTime.format(formatter)} - ${endTime.format(formatter)}"
        }
}

// Network DTOs for remote API integration
data class ApiTvResponse(
    @SerializedName("status") val status: String? = "success",
    @SerializedName("date") val date: String? = null,
    @SerializedName("channels") val channels: List<ApiChannelDto>? = emptyList(),
    @SerializedName("shows") val shows: List<ApiShowDto>? = emptyList()
)

data class ApiChannelDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("logo_text") val logoText: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("number") val number: Int? = 1
)

data class ApiShowDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String? = null,
    @SerializedName("channel_id") val channelId: String,
    @SerializedName("channel_name") val channelName: String,
    @SerializedName("start_time") val startTime: String, // ISO String e.g. "2025-03-30T20:15:00"
    @SerializedName("end_time") val endTime: String,
    @SerializedName("description") val description: String? = "",
    @SerializedName("category") val category: String? = "ENTERTAINMENT",
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("age_rating") val ageRating: String? = "FSK 12",
    @SerializedName("year") val year: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("cast") val cast: List<String>? = emptyList(),
    @SerializedName("is_highlight") val isHighlight: Boolean? = false,
    @SerializedName("image_url") val imageUrl: String? = null
)
