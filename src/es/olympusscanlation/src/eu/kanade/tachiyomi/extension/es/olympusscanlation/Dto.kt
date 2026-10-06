package eu.kanade.tachiyomi.extension.es.olympusscanlation

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class RankingDto(
    val data: List<MangaDto> = emptyList(),
    @SerialName("current_page") private val currentPage: Int = 1,
    @SerialName("last_page") private val lastPage: Int = 1,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class NewChaptersDto(
    val data: List<MangaDto> = emptyList(),
    @SerialName("current_page") private val currentPage: Int = 1,
    @SerialName("last_page") private val lastPage: Int = 1,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class PayloadMangaDto(val data: List<MangaDto> = emptyList())

@Serializable
class MangaDetailDto(val data: MangaDto)

@Serializable
class MangaDto(
    val id: Int,
    val name: String,
    val slug: String,
    private val cover: String? = null,
    val type: String? = null,
    private val summary: String? = null,
    private val status: MangaStatusDto? = null,
    private val genres: List<FilterDto>? = null,
) {
    val isComic: Boolean get() = type == null || type == "comic"

    fun toSManga() = SManga.create().apply {
        title = name
        url = id.toString()
        thumbnail_url = cover
    }

    fun toSMangaDetails() = toSManga().apply {
        description = summary?.trim()?.ifEmpty { null }
        status = parseStatus()
        genre = genres?.joinToString { it.name.trim() }?.ifEmpty { null }
        initialized = true
    }

    private fun parseStatus(): Int = when (status?.id) {
        1 -> SManga.ONGOING
        3 -> SManga.ON_HIATUS
        4 -> SManga.COMPLETED
        5 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class MangaStatusDto(val id: Int)

@Serializable
class FilterDto(val name: String)

@Serializable
class PayloadChapterDto(
    val data: List<ChapterDto> = emptyList(),
    val meta: MetaDto = MetaDto(),
)

@Serializable
class MetaDto(
    @SerialName("last_page") val lastPage: Int = 1,
)

@Serializable
class ChapterDto(
    val id: Int,
    val name: String,
    @SerialName("published_at") private val date: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        name = "Capítulo ${this@ChapterDto.name}"
        chapter_number = this@ChapterDto.name.toFloatOrNull() ?: -1f
        url = "$mangaId/$id"
        date_upload = date?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L
    }
}

@Serializable
class PayloadPagesDto(val chapter: PageDto)

@Serializable
class PageDto(val pages: List<String> = emptyList())
