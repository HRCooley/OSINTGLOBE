package com.globenews.data.source.remote.rss

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.xml.parsers.SAXParserFactory

class RssParser {
    fun parse(xml: String, feedConfig: RssFeedConfig): List<NewsStory> {
        return try {
            val handler = RssSaxHandler(feedConfig)
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newSAXParser()
            parser.parse(InputSource(StringReader(xml)), handler)
            handler.stories
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private class RssSaxHandler(private val feedConfig: RssFeedConfig) :
    org.xml.sax.helpers.DefaultHandler() {

    val stories = mutableListOf<NewsStory>()
    private var insideItem = false
    private var currentElement = ""
    private val buffer = StringBuilder()

    private var title = ""
    private var link = ""
    private var description: String? = null
    private var pubDate: String? = null
    private var imageUrl: String? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attrs: org.xml.sax.Attributes?) {
        val tag = (qName ?: localName ?: "").lowercase(Locale.US)
        if (tag == "item" || tag == "entry") {
            insideItem = true
            title = ""
            link = ""
            description = null
            pubDate = null
            imageUrl = null
        }
        currentElement = tag
        buffer.clear()

        if (insideItem && tag == "enclosure") {
            val type = attrs?.getValue("type") ?: ""
            if (type.startsWith("image/")) {
                imageUrl = attrs?.getValue("url")
            }
        }
        if (insideItem && tag == "media:content") {
            imageUrl = attrs?.getValue("url")
        }
        if (insideItem && (tag == "link") && attrs != null) {
            val href = attrs.getValue("href")
            if (href != null && link.isBlank()) {
                link = href
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        buffer.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val tag = (qName ?: localName ?: "").lowercase(Locale.US)
        val text = buffer.toString().trim()

        if (insideItem) {
            when (tag) {
                "title" -> title = text
                "link" -> if (text.isNotBlank()) link = text
                "description", "summary" -> description = text.takeIf { it.isNotBlank() }
                "pubdate", "published", "updated" -> pubDate = text
                "media:thumbnail" -> if (imageUrl == null) imageUrl = text
            }
        }

        if (tag == "item" || tag == "entry") {
            insideItem = false
            if (title.isNotBlank() && link.isNotBlank()) {
                stories.add(buildStory())
            }
        }

        buffer.clear()
    }

    private fun buildStory(): NewsStory {
        val publishedAt = parseDate(pubDate)

        return NewsStory(
            id = UUID.nameUUIDFromBytes(link.toByteArray()).toString(),
            title = title,
            summary = description?.replace(Regex("<[^>]*>"), "")?.take(500),
            url = link,
            imageUrl = imageUrl,
            publishedAt = publishedAt,
            location = StoryLocation(
                latitude = feedConfig.lat,
                longitude = feedConfig.lon,
                placeName = feedConfig.country,
                countryCode = feedConfig.country.take(2).uppercase(),
                admin1 = null
            ),
            scope = try {
                EditorialScope.valueOf(feedConfig.scope.uppercase())
            } catch (e: Exception) {
                EditorialScope.NATIONAL
            },
            sources = listOf(
                SourceAttribution(
                    name = feedConfig.name,
                    providerApi = "rss",
                    retrievedAt = Instant.now()
                )
            ),
            language = feedConfig.language,
            sentiment = null,
            categories = emptyList()
        )
    }

    private fun parseDate(dateStr: String?): Instant {
        if (dateStr.isNullOrBlank()) return Instant.now()
        return try {
            ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                Instant.now()
            }
        }
    }
}
