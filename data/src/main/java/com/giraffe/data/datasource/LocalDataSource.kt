package com.giraffe.data.datasource

import com.giraffe.domain.model.CompactDate

interface LocalDataSource {
    /**
     * Stores the pairs in local storage (e.g., Room database or Realm).
     * This could be an upsert (insert or replace) operation.
     */
    suspend fun saveDates(dates: List<CompactDate>)
}