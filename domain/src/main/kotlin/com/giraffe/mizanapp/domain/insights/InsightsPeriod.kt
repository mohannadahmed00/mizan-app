package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.week.Week
import java.time.YearMonth

/**
 * The scope `GetSectionBreakdown` reads over — always the *current* week or
 * month (spec.md Assumptions; `data-model.md` "Period selection"). There is
 * no past `ForWeek`/`ForMonth` ever constructed: unlike Trend and Month,
 * Sections has no historical previous/next navigation by design.
 */
sealed interface InsightsPeriod {
    data class ForWeek(val week: Week) : InsightsPeriod
    data class ForMonth(val month: YearMonth) : InsightsPeriod
}
