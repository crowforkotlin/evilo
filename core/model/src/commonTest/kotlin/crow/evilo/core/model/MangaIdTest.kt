package crow.evilo.core.model

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MangaIdTest {
    @Test
    fun sourceIdsSeparateMatchingMangaIds() {
        assertNotEquals(MangaId("first-source", "42"), MangaId("second-source", "42"))
    }

    @Test
    fun blankSourceIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            MangaId(" ", "42")
        }
    }

    @Test
    fun blankMangaIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            MangaId("first-source", "")
        }
    }
}
