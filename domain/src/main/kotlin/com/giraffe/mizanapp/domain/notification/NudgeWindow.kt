package com.giraffe.mizanapp.domain.notification

import java.time.Duration
import java.time.Instant

object NudgeWindow { val OFFSET_AFTER_PRAYER: Duration = Duration.ofMinutes(20); fun firesAt(prayerAt: Instant, endsAt: Instant): Instant? = prayerAt.plus(OFFSET_AFTER_PRAYER).takeIf { it.isBefore(endsAt) } }
