package com.giraffe.data.repository

import com.giraffe.data.datasource.RemoteDataSource
import com.giraffe.data.utli.executeApiSafely
import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.Task
import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.Repository
import org.koin.core.annotation.Singleton

@Singleton
class RepositoryImpl(
    private val remoteDataSource: RemoteDataSource,
    private val systemDateProvider: SystemDateProvider
) : Repository {
    override suspend fun getCurrentDate(): CompactDate {
        return CompactDate(
            hijri = SimpleDate(
                day = 5,
                month = 1,
                year = 1447
            ),
            gregorian = SimpleDate(
                day = 20,
                month = 6,
                year = 2026
            )
        )
    }

    override suspend fun getTodayTasks(): List<Task> {
        return emptyList()
    }

    override suspend fun syncMonthlyHijriDates() {
        executeApiSafely {
            val currentDate = systemDateProvider.getCurrentGregorianDate()
            remoteDataSource.fetchHijriDatesForMonth(
                month = currentDate.month,
                year = currentDate.year
            )
        }
    }
}