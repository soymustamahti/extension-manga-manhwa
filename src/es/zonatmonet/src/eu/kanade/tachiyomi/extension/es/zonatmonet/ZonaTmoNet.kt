package eu.kanade.tachiyomi.extension.es.zonatmonet

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ZonaTmoNet : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-api/api".toHttpUrl()

    private val uploadsUrl get() = "$baseUrl/wp-content/uploads".toHttpUrl()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegments("tops/views/month")
            .addQueryParameter("postType", "any")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("postsPerPage", PER_PAGE.toString())
            .build()

        return client.get(url).parseAs<ListResponseDto>().toMangasPage()
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(listingUrl(page))
        .parseAs<ListResponseDto>()
        .toMangasPage()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listingUrl(page).newBuilder()

        query.trim().takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("search", it)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addCheckedValues("genres[]", filter)
                is TypeFilter -> url.addCheckedValues("type[]", filter)
                is DemographyFilter -> url.addCheckedValues("demography[]", filter)
                is StatusFilter -> url.addCheckedValues("status[]", filter)
                is EroticFilter -> filter.value?.let { url.addQueryParameter("erotic", it) }
                is SortFilter -> {
                    url.setQueryParameter("orderBy", filter.orderBy)
                    url.setQueryParameter("order", filter.order)
                }
                else -> {}
            }
        }

        return client.get(url.build()).parseAs<ListResponseDto>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null

        val slug = url.pathSegments
            .takeIf { it.firstOrNull() == "manga" }
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return getMangaDetails(slug)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        EroticFilter(),
        StatusFilter(),
        TypeFilter(),
        DemographyFilter(),
        GenreFilter(),
    )

    // =============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug

        return coroutineScope {
            val details = async {
                if (fetchDetails) getMangaDetails(slug) else manga
            }
            val chapterList = async {
                if (fetchChapters) getChapterList(slug) else chapters
            }

            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private suspend fun getMangaDetails(slug: String): SManga {
        val url = apiUrl.newBuilder()
            .addPathSegments("single/manga")
            .addPathSegment(slug)
            .build()

        return client.get(url).parseAs<MangaResponseDto>().data
            ?.toSManga()
            ?: throw Exception("No se pudo obtener la información de la obra")
    }

    // =============================== Chapters =============================

    private suspend fun getChapterList(slug: String): List<SChapter> {
        val first = client.get(chapterListUrl(slug, 1)).parseAs<ChapterListResponseDto>()
        val chapters = first.data?.items.orEmpty().toMutableList()
        val totalPages = first.data?.pagination?.totalPages ?: 1

        for (page in 2..totalPages) {
            chapters += client.get(chapterListUrl(slug, page))
                .parseAs<ChapterListResponseDto>()
                .data?.items.orEmpty()
        }

        return chapters.distinctBy { it.id }
            .sortedByDescending { it.number }
            .map { it.toSChapter(slug) }
    }

    // ================================ Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trim('/').split('/')
        val mangaSlug = segments[1]
        val chapterSlug = segments[2]

        val url = apiUrl.newBuilder()
            .addPathSegments("single/manga")
            .addPathSegment(mangaSlug)
            .addPathSegment(chapterSlug)
            .build()

        val dto = client.get(url).parseAs<ReaderResponseDto>().data?.chapter
            ?: throw Exception("No se pudo obtener las páginas del capítulo")

        return dto.images
            .sortedBy { it.pageNumber }
            .mapIndexed { index, image ->
                Page(
                    index,
                    imageUrl = CDN_URL.toHttpUrl().newBuilder()
                        .addPathSegment("manga")
                        .addPathSegments(dto.jit)
                        .addPathSegment(image.imageUrl)
                        .build()
                        .toString(),
                )
            }
    }

    // =============================== Helpers ==============================

    private fun listingUrl(page: Int): HttpUrl = apiUrl.newBuilder()
        .addPathSegments("listing/manga")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("postsPerPage", PER_PAGE.toString())
        .addQueryParameter("orderBy", "manga_id")
        .addQueryParameter("order", "desc")
        .build()

    private fun chapterListUrl(slug: String, page: Int): HttpUrl = apiUrl.newBuilder()
        .addPathSegments("single/manga")
        .addPathSegment(slug)
        .addPathSegment("chapters")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("order", "desc")
        .build()

    private fun HttpUrl.Builder.addCheckedValues(name: String, group: Filter.Group<CheckBoxFilter>) {
        group.state.filter { it.state }.forEach { addQueryParameter(name, it.value) }
    }

    private fun ListResponseDto.toMangasPage(): MangasPage {
        val mangas = data?.items.orEmpty().map { it.toSManga() }

        return MangasPage(mangas, data?.pagination?.hasNext ?: false)
    }

    private fun MangaDto.toSManga() = SManga.create().apply {
        url = "/manga/$slug/"
        title = this@toSManga.title.trim()
        thumbnail_url = cover?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            if (path.startsWith("http")) {
                path
            } else {
                uploadsUrl.newBuilder().addEncodedPathSegments(path.removePrefix("/")).build().toString()
            }
        }
        description = descriptionValue
        author = authorValue
        genre = genreValue
        status = statusValue
    }

    private fun ChapterDto.toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/manga/$mangaSlug/$slug/"
        name = displayName
        chapter_number = number
        date_upload = date
        scanlator = group?.name?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val SManga.slug: String
        get() = url.trim('/').split('/').getOrNull(1)
            ?: throw Exception("No se pudo determinar la obra, migra la entrada de nuevo")

    companion object {
        private const val CDN_URL = "https://cdn.zonatmo.to"
        private const val PER_PAGE = 24
    }
}
