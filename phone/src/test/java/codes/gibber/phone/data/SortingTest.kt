package codes.gibber.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    @Test
    fun partsStayUnderTheHubsLimitAndKeepTheOrder() {
        val parts = Sorting.partsBySize(listOf(30, 30, 30, 10, 90, 5), max = 64)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4), listOf(5)), parts)
    }

    @Test
    fun nothingToSendIsNoParts() {
        assertEquals(emptyList<List<Int>>(), Sorting.partsBySize(emptyList(), max = 64))
    }
}
