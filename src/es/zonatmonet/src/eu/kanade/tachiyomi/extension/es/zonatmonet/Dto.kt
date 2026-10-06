package eu.kanade.tachiyomi.extension.es.zonatmonet

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class ListResponseDto(
    val data: ListDataDto? = null,
)

@Serializable
class ListDataDto(
    val items: List<MangaDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class MangaResponseDto(
    val data: MangaDto? = null,
)

@Serializable
class ChapterListResponseDto(
    val data: ChapterListDataDto? = null,
)

@Serializable
class ChapterListDataDto(
    val items: List<ChapterDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class ReaderResponseDto(
    val data: ReaderDataDto? = null,
)

@Serializable
class ReaderDataDto(
    val chapter: ChapterPagesDto? = null,
)

@Serializable
class PaginationDto(
    @SerialName("has_next")
    val hasNext: Boolean = false,
    @SerialName("total_pages")
    val totalPages: Int = 1,
)

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    val overview: String? = null,
    val subtitle: String? = null,
    val cover: String? = null,
    val author: List<AuthorDto> = emptyList(),
    val status: List<Int> = emptyList(),
    val genres: List<Int> = emptyList(),
    val types: List<Int> = emptyList(),
    val demography: List<Int> = emptyList(),
    @SerialName("is_erotic")
    val isErotic: Int = 0,
) {
    val statusValue: Int
        get() = when {
            status.contains(12) || status.contains(12856) || status.contains(12866) || status.contains(12869) -> SManga.ONGOING
            status.contains(19) || status.contains(12874) -> SManga.COMPLETED
            status.contains(174) || status.contains(12922) -> SManga.ON_HIATUS
            status.contains(198) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

    val genreValue: String?
        get() = buildList {
            types.mapNotNullTo(this) { typeName(it) }
            demography.mapNotNullTo(this) { demographyName(it) }
            genres.mapNotNullTo(this) { genreName(it) }
            if (isErotic == 1) add("Erótico")
        }.distinct().joinToString().ifEmpty { null }

    val authorValue: String?
        get() = author.map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
            .ifEmpty { null }

    val descriptionValue: String?
        get() = buildList {
            overview?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            subtitle?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true) }
                ?.let { add("Título alternativo: $it") }
        }.joinToString("\n\n").ifEmpty { null }
}

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val id: Long,
    @SerialName("chapter_number")
    val chapterNumber: String = "",
    val title: String = "",
    val slug: String,
    @SerialName("release_date")
    val releaseDate: String? = null,
    val group: GroupDto? = null,
) {
    val number: Float get() = chapterNumber.toFloatOrNull() ?: -1f

    val displayName: String
        get() {
            val trimmed = chapterNumber.trimEnd('0').trimEnd('.').ifEmpty { chapterNumber }
            val base = "Capítulo $trimmed"
            val extra = title.trim()
                .takeIf { it.isNotEmpty() && !it.endsWith(base, ignoreCase = true) }
            return if (extra != null) "$base - $extra" else base
        }

    val date: Long get() = DATE_FORMAT.tryParseDateTime(releaseDate, SITE_ZONE)
}

@Serializable
class GroupDto(
    val name: String = "",
)

@Serializable
class ChapterPagesDto(
    val jit: String,
    val images: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    @SerialName("image_url")
    val imageUrl: String,
    @SerialName("page_number")
    val pageNumber: Int = 0,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
private val SITE_ZONE: ZoneId = ZoneId.of("UTC")
