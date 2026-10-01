package com.ivor.openstream.domain.model

/** The curated lists shown on Home: everything by default, with dedicated anime rows. */
enum class AnimeCatalog {
    /** Titles loaded from the user's own JSON manifest. */
    PERSONAL_LIBRARY,

    /** Movies and series trending on TMDB this week. */
    TRENDING,

    /** Series with new episodes airing this week. */
    NEW_EPISODES,

    POPULAR_MOVIES,
    POPULAR_SERIES,

    /** Highly rated films with enough votes to trust the score. */
    TOP_RATED_MOVIES,

    /** Trending series narrowed to Japanese animation. */
    TRENDING_ANIME,

    ANIME_MOVIES
}
