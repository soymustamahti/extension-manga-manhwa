package eu.kanade.tachiyomi.extension.es.olympusscanlation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class OlympusScanlation : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "application/json, text/plain, */*")
        .set("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")

    // Chapters live on a separate host.
    private val panelUrl get() = baseUrl.replace("https://", "https://panel.")

    override val client = network.client.newBuilder()
        .rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
        .rateLimit(2, 1.seconds) { it.host == panelUrl.toHttpUrl().host }
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/rankings?page=$page&period=total_ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseJson<RankingDto>()

        return MangasPage(result.data.toMangaList(), result.hasNextPage())
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/new-chapters?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseJson<NewChaptersDto>()

        return MangasPage(result.data.toMangaList(), result.hasNextPage())
    }

    // =============================== Search ===============================

    // The API ignores its own `search` parameter, so the whole catalogue is
    // fetched once (and cached) and filtered here.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/series/list", headers)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = rx.Observable.fromCallable {
        val series = seriesList()
        val matches = if (query.isBlank()) series else series.filter { it.name.contains(query, ignoreCase = true) }

        MangasPage(
            matches.drop((page - 1) * SEARCH_PAGE_SIZE).take(SEARCH_PAGE_SIZE).map { it.toSManga() },
            hasNextPage = page * SEARCH_PAGE_SIZE < matches.size,
        )
    }!!

    // =============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        val slug = slugOrNull(manga.url) ?: return baseUrl

        return "$baseUrl/series/comic-$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/series/${slugOf(manga.url)}?type=comic", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseJson<MangaDetailDto>().data.toSMangaDetails()

    // =============================== Chapters =============================

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, chapterId) = chapter.url.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val slug = slugOrNull(mangaId) ?: return baseUrl

        return "$baseUrl/capitulo/$chapterId/comic-$slug"
    }

    override fun chapterListRequest(manga: SManga): Request = GET(chapterListUrl(slugOf(manga.url), 1), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments.let { it[it.size - 2] }
        val mangaId = idOf(slug) ?: throw Exception("No se pudo determinar la obra, migra la entrada de nuevo")

        val first = response.parseJson<PayloadChapterDto>()
        val chapters = first.data.toMutableList()

        for (page in 2..first.meta.lastPage) {
            client.newCall(GET(chapterListUrl(slug, page), headers)).execute().use {
                chapters += it.parseJson<PayloadChapterDto>().data
            }
        }

        return chapters.distinctBy { it.id }.map { it.toSChapter(mangaId) }
    }

    // ================================ Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val (mangaId, chapterId) = chapter.url.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }

        // This endpoint accepts the numeric id, so it needs no slug lookup.
        return GET("$baseUrl/api/capitulo/comic-$mangaId/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseJson<PayloadPagesDto>()
        .chapter.pages
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

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

    private fun chapterListUrl(slug: String, page: Int): String = "$panelUrl/api/series/$slug/chapters?page=$page&direction=desc&type=comic"

    @Volatile
    private var cachedSeries: List<MangaDto> = emptyList()

    @Volatile
    private var cachedAt: Long = 0L

    private val seriesLock = Any()

    /**
     * Slugs on this source expire — the API answers a stale one with
     * `/api/__stale_slug_404` — so they are always resolved from the ids kept in
     * [SManga.url] through this periodically refreshed catalogue.
     */
    private fun seriesList(forceRefresh: Boolean = false): List<MangaDto> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSeries.isNotEmpty() && now - cachedAt < CACHE_DURATION_MS) {
            return cachedSeries
        }

        return synchronized(seriesLock) {
            if (!forceRefresh && cachedSeries.isNotEmpty() && System.currentTimeMillis() - cachedAt < CACHE_DURATION_MS) {
                return@synchronized cachedSeries
            }

            val series = client.newCall(GET("$baseUrl/api/series/list", headers)).execute().use {
                it.parseJson<PayloadMangaDto>().data
            }.filter { it.isComic }

            cachedSeries = series
            cachedAt = System.currentTimeMillis()
            series
        }
    }

    private fun slugOrNull(mangaId: String): String? {
        seriesList().firstOrNull { it.id.toString() == mangaId }?.let { return it.slug }

        // Entry added after the catalogue was cached, or a slug that just rotated.
        return seriesList(forceRefresh = true).firstOrNull { it.id.toString() == mangaId }?.slug
    }

    private fun slugOf(mangaId: String): String = slugOrNull(mangaId)
        ?: throw Exception("La obra ya no está disponible en la fuente")

    private fun idOf(slug: String): String? = seriesList().firstOrNull { it.slug == slug }?.id?.toString()

    private fun List<MangaDto>.toMangaList(): List<SManga> = filter { it.isComic }.map { it.toSManga() }

    companion object {
        private const val SEARCH_PAGE_SIZE = 20
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }
}
