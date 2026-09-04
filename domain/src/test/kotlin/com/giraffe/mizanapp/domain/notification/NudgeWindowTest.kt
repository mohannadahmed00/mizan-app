package com.giraffe.mizanapp.domain.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeWindowTest {
 @Test fun `fires twenty minutes after prayer only inside its window`() { val prayer = Instant.parse("2026-09-04T15:30:00Z"); assertEquals(Instant.parse("2026-09-04T15:50:00Z"), NudgeWindow.firesAt(prayer, Instant.parse("2026-09-04T16:30:00Z"))); assertNull(NudgeWindow.firesAt(prayer, Instant.parse("2026-09-04T15:50:00Z"))) }
}
