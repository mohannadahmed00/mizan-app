package com.giraffe.mizanapp.domain.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueJsonTest {

    @Test
    fun `good fixture parses`() {
        val result = parseCatalogue(Fixtures.good())

        assertTrue("expected parse to succeed, was $result", result.isSuccess)

        val catalogue = result.getOrThrow()
        assertEquals("versions", 1, catalogue.versions.size)
        assertEquals("sections", 10, catalogue.sections.size)
        // 32, not 40: the nine Adhkar rows are one task with an occurrence limit of 9.
        assertEquals("tasks", 32, catalogue.tasks.size)
        assertEquals("taskVersions", 32, catalogue.taskVersions.size)
    }
}
