package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.downloads.EpisodeQueueBuilder.Entry
import org.junit.jupiter.api.Test
import java.util.UUID

class EpisodeQueueBuilderTest {
    private fun ep(season: Int, episode: Int, end: Int = episode) = Entry(UUID.randomUUID(), season, episode, end)

    private fun EpisodeQueueBuilder.Result.positions(all: List<Entry>) = ids.map { id -> all.indexOfFirst { it.itemId == id } }

    @Test
    fun `stops at a missing episode`() {
        val all = listOf(ep(1, 1), ep(1, 2), ep(1, 4), ep(1, 5))
        val r = EpisodeQueueBuilder.build(all, all[1])
        r.positions(all) shouldBe listOf(0, 1)
        r.startIndex shouldBe 1

        val r2 = EpisodeQueueBuilder.build(all, all[2])
        r2.positions(all) shouldBe listOf(2, 3)
        r2.startIndex shouldBe 0
    }

    @Test
    fun `crosses into the next season first episode`() {
        val all = listOf(ep(1, 1), ep(1, 2), ep(2, 1), ep(2, 2))
        val r = EpisodeQueueBuilder.build(all, all[0])
        r.positions(all) shouldBe listOf(0, 1, 2, 3)
        EpisodeQueueBuilder.build(all, all[3]).startIndex shouldBe 3
    }

    @Test
    fun `does not cross seasons over a gap`() {
        val all = listOf(ep(1, 1), ep(1, 3), ep(2, 1))
        EpisodeQueueBuilder.build(all, all[0]).positions(all) shouldBe listOf(0)
    }

    @Test
    fun `next season must start at episode one`() {
        val all = listOf(ep(1, 1), ep(2, 2))
        EpisodeQueueBuilder.build(all, all[0]).positions(all) shouldBe listOf(0)
    }

    @Test
    fun `multi episode files are consecutive`() {
        val all = listOf(ep(1, 1, end = 2), ep(1, 3))
        EpisodeQueueBuilder.build(all, all[0]).positions(all) shouldBe listOf(0, 1)
    }

    @Test
    fun `specials only chain with specials`() {
        val all = listOf(ep(0, 1), ep(0, 2), ep(1, 1))
        EpisodeQueueBuilder.build(all, all[0]).positions(all) shouldBe listOf(0, 1)
        EpisodeQueueBuilder.build(all, all[2]).positions(all) shouldBe listOf(2)
    }
}
