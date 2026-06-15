package com.giraffe.data.provider

import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.provider.SystemDateProvider
import java.util.Calendar

class SystemDateProviderImpl : SystemDateProvider {
    /**
     * Returns the current device date mapped to your custom domain model.
     */
    override fun getCurrentGregorianDate(): SimpleDate {
        val calendar = Calendar.getInstance()
        return SimpleDate(
            day = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-based
            year = calendar.get(Calendar.YEAR)
        )
    }
}
