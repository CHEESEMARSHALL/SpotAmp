package com.example

import com.example.data.CachedTrack
import com.example.data.SmartSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchServiceTest {
    private val tracks = listOf(
        CachedTrack("1", "Battle Theme", "Game Composer", "Final Fantasy OST", "/one", "", 1000, 2006, genres = "soundtrack", collections = "Final Fantasy"),
        CachedTrack("2", "Quiet Song", "Other Artist", "Acoustic Album", "/two", "", 1000, 2019, genres = "acoustic")
    )

    @Test
    fun `search intent filters by artist album genre and decade`() {
        val service = SmartSearchService()
        val intent = service.parse("Final Fantasy soundtrack from the 2000s", tracks)
        val result = service.filter(intent, tracks)
        assertEquals(listOf("1"), result.map { it.ratingKey })
    }

    @Test
    fun `downloaded only excludes tracks not in download set`() {
        val service = SmartSearchService()
        val intent = service.parse("downloaded soundtrack", tracks)
        val result = service.filter(intent, tracks, setOf("2"))
        assertTrue(result.isEmpty())
    }
}
