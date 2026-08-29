package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.daos.AccountScopeDao
import com.giraffe.mizanapp.data.db.entities.AccountScopeEntity
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The account this device currently carries records for, over the single-row
 * `account_scope` table. `clear()` empties only this table — it never touches
 * a record.
 */
class AccountScope(private val dao: AccountScopeDao, private val time: TimeProvider) {

    fun observe(): Flow<AccountSession> = dao.observe().map { it.toSession() }

    suspend fun current(): AccountSession = dao.get().toSession()

    suspend fun set(userId: String, email: String, displayName: String?) {
        dao.upsert(
            AccountScopeEntity(
                userId = userId,
                email = email,
                displayName = displayName,
                updatedAt = time.now().toEpochMilli(),
            ),
        )
    }

    suspend fun setDisplayName(name: String?) {
        val existing = dao.get() ?: return
        dao.upsert(existing.copy(displayName = name, updatedAt = time.now().toEpochMilli()))
    }

    suspend fun clear() {
        dao.clear()
    }

    private fun AccountScopeEntity?.toSession(): AccountSession =
        if (this?.userId == null) {
            AccountSession.SignedOut
        } else {
            AccountSession.SignedIn(userId = userId, email = email.orEmpty(), displayName = displayName)
        }
}
