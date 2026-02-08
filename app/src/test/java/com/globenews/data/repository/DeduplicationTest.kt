package com.globenews.data.repository

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DeduplicationTest {

    private fun createStory(
        id: String = "test",
        title: String = "Test Story",
        url: String = "https://example.com/story",
        sourceName: String = "TestSource",
        providerApi: String = "test",
        publishedAt: Instant = Instant.now()
    ) = NewsStory(
        id = id,
        title = title,
        summary = "A test summary",
        url = url,
        imageUrl = null,
        publishedAt = publishedAt,
        location = StoryLocation(50.0, 10.0, "Berlin", "DE", null),
        scope = EditorialScope.NATIONAL,
        sources = listOf(SourceAttribution(sourceName, providerApi, Instant.now())),
        language = "en",
        sentiment = null,
        categories = listOf("test")
    )

    @Test
    fun `duplicate URLs are merged`() {
        val stories = listOf(
            createStory(id = "1", url = "https://example.com/article", sourceName = "Source A", providerApi = "gdelt"),
            createStory(id = "2", url = "https://example.com/article", sourceName = "Source B", providerApi = "newsapi")
        )

        val result = NewsRepositoryImpl.deduplicateStories(stories)

        assertEquals(1, result.size)
        assertEquals(2, result[0].sources.size)
    }

    @Test
    fun `similar titles within same hour are merged`() {
        val now = Instant.now()
        val stories = listOf(
            createStory(
                id = "1",
                title = "Breaking: Major earthquake strikes California region today",
                url = "https://source-a.com/earthquake",
                sourceName = "Source A",
                publishedAt = now
            ),
            createStory(
                id = "2",
                title = "Breaking: Major earthquake strikes California region today",
                url = "https://source-b.com/earthquake",
                sourceName = "Source B",
                publishedAt = now
            )
        )

        val result = NewsRepositoryImpl.deduplicateStories(stories)

        assertEquals(1, result.size)
    }

    @Test
    fun `different stories are kept separate`() {
        val stories = listOf(
            createStory(id = "1", title = "Climate summit opens in Paris", url = "https://a.com/1"),
            createStory(id = "2", title = "Stock market reaches record high", url = "https://b.com/2"),
            createStory(id = "3", title = "New vaccine approved for children", url = "https://c.com/3")
        )

        val result = NewsRepositoryImpl.deduplicateStories(stories)

        assertEquals(3, result.size)
    }

    @Test
    fun `merged stories retain best data`() {
        val stories = listOf(
            createStory(id = "1", url = "https://example.com/story").copy(summary = null, imageUrl = null),
            createStory(id = "2", url = "https://example.com/story").copy(summary = "A good summary", imageUrl = "https://img.com/photo.jpg")
        )

        val result = NewsRepositoryImpl.deduplicateStories(stories)

        assertEquals(1, result.size)
        assertEquals("A good summary", result[0].summary)
        assertEquals("https://img.com/photo.jpg", result[0].imageUrl)
    }

    @Test
    fun `results are sorted by publish date descending`() {
        val now = Instant.now()
        val stories = listOf(
            createStory(id = "1", title = "Old story", url = "https://a.com/1", publishedAt = now.minusSeconds(3600)),
            createStory(id = "2", title = "New story", url = "https://b.com/2", publishedAt = now),
            createStory(id = "3", title = "Medium story", url = "https://c.com/3", publishedAt = now.minusSeconds(1800))
        )

        val result = NewsRepositoryImpl.deduplicateStories(stories)

        assertEquals("New story", result[0].title)
        assertTrue(result[0].publishedAt >= result[1].publishedAt)
        assertTrue(result[1].publishedAt >= result[2].publishedAt)
    }
}
