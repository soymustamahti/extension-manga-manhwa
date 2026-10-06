package eu.kanade.tachiyomi.extension.es.zonatmonet

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ZonaTmoNet : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .set("Accept", "application/json, text/plain, */*")
        .set("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")

    private val apiUrl get() = "$baseUrl/wp-api/api".toHttpUrl()

    private val uploadsUrl get() = "$baseUrl/wp-content/uploads".toHttpUrl()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.newBuilder()
            .addPathSegments("tops/views/month")
            .addQueryParameter("postType", "any")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("postsPerPage", PER_PAGE.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = response.parseJson<ListResponseDto>().toMangasPage()

    // =============================== Latest ===============================

    // The API has no "recently updated" listing, so this shows the newest entries.
    override fun latestUpdatesRequest(page: Int): Request = GET(listingUrl(page).build(), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        mangaSlugFromUrl(query)?.let { return GET(mangaUrl(it), headers) }

        val url = listingUrl(page)

        query.trim().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("search", it) }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addChecked("genres[]", filter)
                is TypeFilter -> url.addChecked("type[]", filter)
                is DemographyFilter -> url.addChecked("demography[]", filter)
                is StatusFilter -> url.addChecked("status[]", filter)
                is EroticFilter -> filter.value?.let { url.addQueryParameter("erotic", it) }
                is SortFilter -> {
                    url.setQueryParameter("orderBy", filter.orderBy)
                    url.setQueryParameter("order", filter.order)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("single")) {
            val manga = response.parseJson<MangaResponseDto>().data?.toSManga()

            return MangasPage(listOfNotNull(manga), hasNextPage = false)
        }

        return response.parseJson<ListResponseDto>().toMangasPage()
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        EroticFilter(),
        StatusFilter(),
        TypeFilter(),
        DemographyFilter(),
        GenreFilter(),
    )

    // =============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(mangaUrl(manga.slug), headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseJson<MangaResponseDto>().data
        ?.toSManga()
        ?: throw Exception("No se pudo obtener la información de la obra")

    // =============================== Chapters =============================

    override fun chapterListRequest(manga: SManga): Request = GET(chapterListUrl(manga.slug, 1), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.let { it[it.size - 2] }

        val first = response.parseJson<ChapterListResponseDto>()
        val chapters = first.data?.items.orEmpty().toMutableList()
        val totalPages = first.data?.pagination?.totalPages ?: 1

        for (page in 2..totalPages) {
            client.newCall(GET(chapterListUrl(slug, page), headers)).execute().use {
                chapters += it.parseJson<ChapterListResponseDto>().data?.items.orEmpty()
            }
        }

        return chapters.distinctBy { it.id }
            .sortedByDescending { it.number }
            .map { it.toSChapter(slug) }
    }

    // ================================ Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trim('/').split('/')

        val url = apiUrl.newBuilder()
            .addPathSegments("single/manga")
            .addPathSegment(segments[1])
            .addPathSegment(segments[2])
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseJson<ReaderResponseDto>().data?.chapter
            ?: throw Exception("No se pudo obtener las páginas del capítulo")

        return chapter.images
            .sortedBy { it.pageNumber }
            .mapIndexed { index, image ->
                val imageUrl = CDN_URL.toHttpUrl().newBuilder()
                    .addPathSegment("manga")
                    .addPathSegments(chapter.jit)
                    .addPathSegment(image.imageUrl)
                    .build()

                Page(index, imageUrl = imageUrl.toString())
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Helpers ==============================

    /**
     * The API always answers JSON. Anything else is the host interfering — typically a
     * Cloudflare interstitial served with HTTP 200 — and parsing it would surface as an
     * opaque server error, so report what actually happened instead.
     */
    private inline fun <reified T> Response.parseJson(): T {
        val contentType = header("Content-Type").orEmpty()
        if (!contentType.contains("json", ignoreCase = true)) {
            close()
            throw Exception(
                "La fuente no ha devuelto JSON (HTTP $code, $contentType). " +
                    "Suele ser un bloqueo de Cloudflare: abre la fuente en WebView para resolverlo.",
            )
        }

        return parseAs<T>()
    }

    private fun listingUrl(page: Int): HttpUrl.Builder = apiUrl.newBuilder()
        .addPathSegments("listing/manga")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("postsPerPage", PER_PAGE.toString())
        .addQueryParameter("orderBy", "manga_id")
        .addQueryParameter("order", "desc")

    private fun mangaUrl(slug: String): HttpUrl = apiUrl.newBuilder()
        .addPathSegments("single/manga")
        .addPathSegment(slug)
        .build()

    private fun chapterListUrl(slug: String, page: Int): HttpUrl = apiUrl.newBuilder()
        .addPathSegments("single/manga")
        .addPathSegment(slug)
        .addPathSegment("chapters")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("order", "desc")
        .build()

    private fun mangaSlugFromUrl(query: String): String? {
        val url = query.toHttpUrlOrNull() ?: return null
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null

        return url.pathSegments
            .takeIf { it.firstOrNull() == "manga" }
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun HttpUrl.Builder.addChecked(name: String, group: Filter.Group<CheckBoxFilter>) {
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
        initialized = true
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
