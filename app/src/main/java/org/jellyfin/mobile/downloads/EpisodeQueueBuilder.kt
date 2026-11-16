package org.jellyfin.mobile.downloads

import org.jellyfin.mobile.data.entity.DownloadEntity
import java.util.UUID

/**
 * Builds a playback queue of consecutive downloaded episodes.
 *
 * An episode's successor is either the episode with the next higher number in the same season,
 * or the first episode of the next season. Specials (season 0) only chain with other specials.
 * A missing successor ends the queue; episodes are never skipped.
 */
object EpisodeQueueBuilder {
    data class Entry(
        val itemId: UUID,
        val season: Int,
        val episode: Int,
        /** Last episode number covered, differs from [episode] for multi-episode files. */
        val episodeEnd: Int = episode,
    )

    data class Result(val ids: List<UUID>, val startIndex: Int)

    /**
     * @param candidates all fully downloaded episodes of the same series.
     * @param current the episode to start playback with.
     */
    fun build(candidates: List<Entry>, current: Entry): Result {
        val bySeasonEpisode = candidates.groupBy { it.season }
            .mapValues { (_, list) -> list.sortedBy { it.episode } }

        fun nextOf(entry: Entry): Entry? {
            val sameSeason = bySeasonEpisode[entry.season]?.firstOrNull { it.episode == entry.episodeEnd + 1 }
            if (sameSeason != null) return sameSeason
            if (entry.season == SPECIALS_SEASON) return null
            // Only cross into the next season from the last downloaded episode of this one.
            val isLastInSeason = bySeasonEpisode[entry.season]?.none { it.episode > entry.episodeEnd } != false
            if (!isLastInSeason) return null
            return bySeasonEpisode[entry.season + 1]?.firstOrNull { it.episode == FIRST_EPISODE }
        }

        fun previousOf(entry: Entry): Entry? {
            val sameSeason = bySeasonEpisode[entry.season]?.firstOrNull { it.episodeEnd == entry.episode - 1 }
            if (sameSeason != null) return sameSeason
            // Season 1 has no regular predecessor: season 0 holds specials, which don't chain.
            if (entry.season <= FIRST_SEASON || entry.episode != FIRST_EPISODE) return null
            // Mirror of nextOf: the previous season's last downloaded episode links to this one.
            return bySeasonEpisode[entry.season - 1]?.lastOrNull()
        }

        val before = generateSequence(previousOf(current), ::previousOf).toList().reversed()
        val after = generateSequence(nextOf(current), ::nextOf).toList()
        val queue = before + current + after
        return Result(queue.map { it.itemId }, before.size)
    }

    /**
     * Convenience for [DownloadEntity] lists. Entries without season/episode numbers are ignored,
     * in which case the result is a single-item queue.
     */
    fun toEntry(download: DownloadEntity): Entry? {
        val season = download.item.parentIndexNumber ?: return null
        val episode = download.item.indexNumber ?: return null
        return Entry(download.itemId, season, episode, download.item.indexNumberEnd ?: episode)
    }

    private const val SPECIALS_SEASON = 0
    private const val FIRST_SEASON = 1
    private const val FIRST_EPISODE = 1
}
