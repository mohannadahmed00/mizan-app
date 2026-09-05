package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.Instant
fun weekCloseInstant(boundary: BoundaryState): Instant? = boundary.expiresAt.takeIf { boundary.resolvedDate == WeekBoundary.weekContaining(boundary.resolvedDate).end }
