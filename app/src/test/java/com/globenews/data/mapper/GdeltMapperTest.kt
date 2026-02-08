package com.globenews.data.mapper

import com.globenews.data.source.remote.gdelt.GdeltArticle
import com.globenews.domain.model.EditorialScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GdeltMapperTest {

    @Test
    fun `maps GDELT article to domain model`() {
        val article = GdeltArticle(
            url = "https://reuters.com/article/test",
            urlMobile = null,
            title = "Test headline from GDELT",
            seenDate = "20250115T120000Z",
            socialImage = "https://img.com/test.jpg",
            domain = "reuters.com",
            language = "English",
            sourceCountry = "US",
            tone = "-2.5,1.0"
        )

        val story = GdeltMapper.toDomain(article, 40.7128, -74.0060, "New York", "US")

        assertEquals("Test headline from GDELT", story.title)
        assertEquals("https://reuters.com/article/test", story.url)
        assertEquals("https://img.com/test.jpg", story.imageUrl)
        assertEquals(40.7128, story.location.latitude, 0.001)
        assertEquals(-74.0060, story.location.longitude, 0.001)
        assertEquals("New York", story.location.placeName)
        assertEquals("US", story.location.countryCode)
        assertEquals("gdelt", story.sources[0].providerApi)
        assertEquals("reuters.com", story.sources[0].name)
        assertNotNull(story.sentiment)
    }

    @Test
    fun `handles missing optional fields gracefully`() {
        val article = GdeltArticle(
            url = "https://example.com/article",
            urlMobile = null,
            title = "Minimal article",
            seenDate = "20250115T120000Z",
            socialImage = null,
            domain = null,
            language = null,
            sourceCountry = null,
            tone = null
        )

        val story = GdeltMapper.toDomain(article, 0.0, 0.0, null, null)

        assertEquals("Minimal article", story.title)
        assertNull(story.imageUrl)
        assertNull(story.sentiment)
        assertEquals("en", story.language)
        assertEquals(EditorialScope.INTERNATIONAL, story.scope)
        assertEquals("GDELT", story.sources[0].name)
    }

    @Test
    fun `NATIONAL scope inferred when country code present`() {
        val article = GdeltArticle(
            url = "https://example.com",
            title = "Test",
            seenDate = "20250115T120000Z",
            sourceCountry = "DE",
            tone = null,
            domain = null,
            language = null,
            socialImage = null,
            urlMobile = null
        )

        val story = GdeltMapper.toDomain(article, 52.52, 13.405, "Berlin", "DE")

        assertEquals(EditorialScope.NATIONAL, story.scope)
    }
}
