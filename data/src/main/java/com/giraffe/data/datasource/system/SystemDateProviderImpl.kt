package com.giraffe.data.datasource.system

import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.provider.SystemDateProvider
import org.koin.core.annotation.Single
import java.util.Calendar

@Single
class SystemDateProviderImpl : SystemDateProvider {
    override fun getCurrentGregorianDate(): SimpleDate {
        val calendar = Calendar.getInstance()
        return SimpleDate(
            day = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = calendar.get(Calendar.YEAR)
        )
    }
}