package com.giraffe.mizanapp.catalogue

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
        assertEquals("tasks", 40, catalogue.tasks.size)
        assertEquals("taskVersions", 40, catalogue.taskVersions.size)
    }
}
