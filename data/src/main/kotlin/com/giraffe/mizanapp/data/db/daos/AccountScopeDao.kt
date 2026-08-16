package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.AccountScopeEntity
import kotlinx.coroutines.flow.Flow

/** The single row naming the account this device currently carries records for. */
@Dao
interface AccountScopeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scope: AccountScopeEntity)

    @Query("SELECT * FROM account_scope WHERE id = 0")
    fun observe(): Flow<AccountScopeEntity?>

    @Query("SELECT * FROM account_scope WHERE id = 0")
    suspend fun get(): AccountScopeEntity?

    @Query("DELETE FROM account_scope")
    suspend fun clear()
}
