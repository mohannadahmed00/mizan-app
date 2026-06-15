package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.Date

class GetCurrentDateUseCase {
    operator fun invoke(): CompactDate {
        return CompactDate(
            hijri = Date(1, 1, 1447),
            gregorian = Date(1, 1, 2026),
        )
    }
}