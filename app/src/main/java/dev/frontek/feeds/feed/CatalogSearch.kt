package dev.frontek.feeds.feed

import dev.frontek.feeds.model.CatalogEntry
import java.text.Normalizer

/**
 * Catalog matching for Discover. Accent- and case-insensitive, and aware of
 * multilingual category synonyms so e.g. "tecnologia" surfaces every "Tech" feed,
 * while a plain-name query like "hdblog" still matches on the title.
 */
object CatalogSearch {

    // Each group ties a catalog category to its synonyms across IT/EN/ES/FR.
    private val GROUPS: List<Set<String>> = listOf(
        setOf("tech", "technology", "tecnologia", "tecnologie", "tecnologica", "hitech", "hi-tech",
            "gadget", "gadgets", "informatica", "digitale", "tecnologie", "tecnologia", "tecnologie"),
        setOf("news", "notizie", "notizia", "attualita", "cronaca", "giornale", "noticias",
            "actualites", "actualite", "quotidiano"),
        setOf("dev", "development", "developer", "sviluppo", "programmazione", "programming",
            "coding", "code", "software", "programmation", "desarrollo", "web"),
        setOf("science", "scienza", "scienze", "ciencia", "sciences", "ricerca", "research"),
        setOf("gaming", "game", "games", "gioco", "giochi", "videogioco", "videogiochi",
            "videogames", "juegos", "jeux"),
        setOf("fun", "divertente", "svago", "meme", "humor", "umorismo", "divertimento"),
    )

    fun normalize(s: String?): String =
        Normalizer.normalize(s ?: "", Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()

    fun matches(entry: CatalogEntry, query: String): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return true
        val haystack = buildString {
            append(normalize(entry.title)).append(' ')
            append(normalize(entry.site)).append(' ')
            append(normalize(entry.category))
        }
        val category = normalize(entry.category)
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.all { token -> haystack.contains(token) || synonymMatch(category, token) }
    }

    private fun synonymMatch(category: String, token: String): Boolean {
        if (category.isBlank() || token.length < 2) return false
        return GROUPS.any { group ->
            group.any { it == category || category.contains(it) } &&
                group.any { it.contains(token) || token.contains(it) }
        }
    }

    /** Filtered catalog, ranked: title prefix match, then title contains, then the rest. */
    fun filter(catalog: List<CatalogEntry>, query: String): List<CatalogEntry> {
        val q = normalize(query)
        return catalog
            .filter { matches(it, query) }
            .sortedWith(
                compareBy(
                    { entry ->
                        val title = normalize(entry.title)
                        when {
                            q.isBlank() -> 2
                            title.startsWith(q) -> 0
                            title.contains(q) -> 1
                            else -> 2
                        }
                    },
                    { normalize(it.title) },
                ),
            )
    }
}
