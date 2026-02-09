package com.globenews.domain.model

/**
 * News categories mapped to GDELT GKG theme prefixes for server-side filtering,
 * and keyword lists for client-side filtering of non-GDELT sources (RSS, GNews, NewsAPI).
 */
enum class NewsCategory(
    val displayName: String,
    val gdeltThemes: List<String>,
    val keywords: List<String>
) {
    ALL("All", emptyList(), emptyList()),
    CONFLICT(
        "Conflict",
        listOf("MILITARY", "ARMED_CONFLICT", "KILL", "TERROR", "PROTEST", "REVOLT"),
        listOf("war", "military", "conflict", "attack", "troops", "bomb", "protest", "revolt", "terror", "shooting", "strike", "invasion")
    ),
    POLITICS(
        "Politics",
        listOf("ELECTION", "GOVERN", "LEGISLATION", "DIPLOMACY", "SUMMIT", "SANCTION"),
        listOf("election", "president", "minister", "parliament", "vote", "legislation", "diplomacy", "summit", "sanction", "government", "policy", "senator", "congress")
    ),
    ECONOMY(
        "Economy",
        listOf("ECON_", "TRADE", "INFLATION", "BANKRUPTCY", "STOCK", "CURRENCY"),
        listOf("economy", "trade", "inflation", "stock", "market", "gdp", "recession", "unemployment", "bank", "currency", "tariff", "bankruptcy", "finance")
    ),
    ENVIRONMENT(
        "Environment",
        listOf("ENV_", "CLIMATE", "EARTHQUAKE", "FLOOD", "WILDFIRE", "DROUGHT", "HURRICANE"),
        listOf("climate", "earthquake", "flood", "wildfire", "drought", "hurricane", "environment", "pollution", "deforestation", "typhoon", "tornado", "emissions", "coral")
    ),
    HEALTH(
        "Health",
        listOf("HEALTH_", "PANDEMIC", "EPIDEMIC", "DISEASE", "VACCINE"),
        listOf("health", "pandemic", "epidemic", "disease", "vaccine", "hospital", "virus", "outbreak", "medical", "WHO", "drug", "treatment", "cancer")
    ),
    TECHNOLOGY(
        "Technology",
        listOf("CYBER", "AI_", "TECH_", "DIGITAL", "HACK"),
        listOf("cyber", "artificial intelligence", "ai", "tech", "digital", "hack", "software", "startup", "robot", "quantum", "chip", "semiconductor", "data breach")
    ),
    CRIME(
        "Crime",
        listOf("CRIME", "ARREST", "FRAUD", "DRUG_TRADE", "CORRUPTION"),
        listOf("crime", "arrest", "fraud", "corruption", "murder", "theft", "drug", "cartel", "trafficking", "sentenced", "prison", "investigation", "indicted")
    ),
    ENERGY(
        "Energy",
        listOf("ENERGY", "OIL", "GAS", "NUCLEAR_POWER", "RENEWABLE"),
        listOf("energy", "oil", "gas", "nuclear", "renewable", "solar", "wind power", "pipeline", "opec", "electricity", "grid", "battery", "hydrogen")
    );

    companion object {
        /** Check if a story's title/summary matches any keywords for this category. */
        fun matchesCategory(category: NewsCategory, title: String, summary: String?): Boolean {
            if (category == ALL) return true
            val text = (title + " " + (summary ?: "")).lowercase()
            return category.keywords.any { text.contains(it) }
        }
    }
}
