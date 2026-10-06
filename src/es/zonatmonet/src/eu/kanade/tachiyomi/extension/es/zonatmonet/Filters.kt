package eu.kanade.tachiyomi.extension.es.zonatmonet

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

val GENRES = arrayOf(
    "Academia" to "12915",
    "Acción" to "2",
    "Adulto" to "12871",
    "Amigos con derechos" to "12925",
    "Amigos de la infancia" to "12923",
    "Animación" to "6198",
    "Apocalíptico" to "861",
    "Artes Marciales" to "26",
    "Aventura" to "3",
    "AV" to "12928",
    "Boys Love" to "103",
    "Ciberpunk" to "356",
    "Ciencia Ficción" to "21",
    "Comedia" to "4",
    "Crimen" to "41",
    "Cultivo" to "12867",
    "Demonios" to "88",
    "Deporte" to "37",
    "Drama" to "15",
    "Ecchi" to "32",
    "Ejercito" to "12878",
    "Extranjero" to "1168",
    "Familia" to "1027",
    "Fantasia" to "5",
    "Girls Love" to "22",
    "Gore" to "181",
    "Guerra" to "1109",
    "Género Bender" to "183",
    "Harem" to "8",
    "Historia" to "81",
    "Historias cortas" to "12892",
    "Hombres lobo" to "12930",
    "Horror" to "82",
    "Isekai" to "12895",
    "Josei" to "12872",
    "Madrastra" to "12929",
    "Madre e hija" to "12932",
    "Magia" to "6",
    "Mecha" to "144",
    "Milf" to "12860",
    "Militar" to "342",
    "Misterio" to "40",
    "Mujer casada" to "12933",
    "Mujer mayor" to "12926",
    "Musica" to "403",
    "Niños" to "219",
    "NTR" to "12924",
    "Oeste" to "141",
    "Pareja casada" to "12931",
    "Parodia" to "820",
    "Policiaco" to "111",
    "Primer amor" to "12918",
    "Psicológico" to "36",
    "Realidad" to "147",
    "Realidad Virtual" to "27",
    "Recuentos de la vida" to "33",
    "Reencarnación" to "60",
    "Relacion secreta" to "12927",
    "Romance" to "16",
    "Samurái" to "99",
    "Sin Censura" to "12863",
    "Sistema de Niveles" to "12868",
    "Smut" to "12904",
    "Sobrenatural" to "7",
    "Superpoderes" to "116",
    "Supervivencia" to "112",
    "Telenovela" to "470",
    "Thriller" to "49",
    "Tragedia" to "46",
    "Traps" to "1464",
    "Universidad" to "12921",
    "Vampiros" to "345",
    "Venganza" to "12891",
    "Vida Escolar" to "23",
    "Yaoi" to "12876",
    "+18" to "12870",
)

val TYPES = arrayOf(
    "Manga" to "14",
    "Manhwa" to "87",
    "Manhua" to "31",
    "Novela" to "214",
    "Doujinshi" to "207",
    "OEL" to "976",
    "One shot" to "12312",
)

val DEMOGRAPHIES = arrayOf(
    "Shounen" to "13",
    "Shoujo" to "20",
    "Seinen" to "45",
    "Josei" to "55",
    "Kodomo" to "633",
)

val STATUSES = arrayOf(
    "Publicándose" to "12",
    "Finalizado" to "19",
    "Pausado" to "174",
    "Cancelado" to "198",
)

// Extra taxonomy ids that only appear on manga entries, never in the filter UI.
private val EXTRA_TYPES = arrayOf(
    "Novela" to "12920",
    "One shot" to "12919",
)

private val GENRE_NAMES = GENRES.toNameMap()
private val TYPE_NAMES = (TYPES + EXTRA_TYPES).toNameMap()
private val DEMOGRAPHY_NAMES = DEMOGRAPHIES.toNameMap()

private fun Array<Pair<String, String>>.toNameMap(): Map<Int, String> = mapNotNull { (name, id) ->
    id.toIntOrNull()?.let { it to name }
}.toMap()

fun genreName(id: Int): String? = GENRE_NAMES[id]

fun typeName(id: Int): String? = TYPE_NAMES[id]

fun demographyName(id: Int): String? = DEMOGRAPHY_NAMES[id]

class GenreFilter : Filter.Group<CheckBoxFilter>("Géneros", GENRES.map { CheckBoxFilter(it.first, it.second) })

class TypeFilter : Filter.Group<CheckBoxFilter>("Tipo", TYPES.map { CheckBoxFilter(it.first, it.second) })

class DemographyFilter : Filter.Group<CheckBoxFilter>("Demografía", DEMOGRAPHIES.map { CheckBoxFilter(it.first, it.second) })

class StatusFilter : Filter.Group<CheckBoxFilter>("Estado", STATUSES.map { CheckBoxFilter(it.first, it.second) })

private val EROTIC_OPTIONS = arrayOf(
    "Todos" to null,
    "Solo eróticos" to "1",
    "Sin eróticos" to "0",
)

class EroticFilter : Filter.Select<String>("Contenido erótico", EROTIC_OPTIONS.map { it.first }.toTypedArray()) {
    val value: String? get() = EROTIC_OPTIONS[state].second
}

private val SORT_OPTIONS = arrayOf(
    "Añadidos recientemente" to "manga_id",
    "Puntuación" to "score",
    "Nº de capítulos" to "total_chapters",
    "Título" to "title",
)

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
        Selection(0, ascending = false),
    ) {
    val orderBy: String get() = SORT_OPTIONS[state?.index ?: 0].second

    val order: String get() = if (state?.ascending == true) "asc" else "desc"
}
