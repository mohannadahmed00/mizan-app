package com.giraffe.mizanapp.domain.week

fun isSummaryDormant(closedWeeksNewestFirst: List<Boolean>): Boolean = closedWeeksNewestFirst.take(3).size == 3 && closedWeeksNewestFirst.take(3).none { it }
