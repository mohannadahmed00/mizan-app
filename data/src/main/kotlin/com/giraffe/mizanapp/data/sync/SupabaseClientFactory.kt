package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Builds the Supabase client, or declines to.
 *
 * Returns null when [BuildConfig.SUPABASE_URL] or [BuildConfig.SUPABASE_ANON_KEY]
 * is blank, which is how a build with no backend configuration stays the
 * offline MVP (FR-003) — the DI wiring falls back to [NoOpRemoteDataSource]
 * rather than crashing at start-up. This file and [SupabaseRemoteDataSource]
 * are the only two files in the repository allowed to import `io.github.jan.*`
 * or `io.ktor.*`.
 *
 * **Memoized.** [createAccountRepository] and [createRemoteDataSource] both
 * call this independently; without caching they would each build their own
 * client, splitting the live Auth session between two objects so a sign-in
 * made through one would be invisible to the other.
 */
fun createSupabaseClient(): SupabaseClient? = cachedClient

private val cachedClient: SupabaseClient? by lazy {
    val url = BuildConfig.SUPABASE_URL
    val key = BuildConfig.SUPABASE_ANON_KEY
    if (url.isBlank() || key.isBlank()) {
        null
    } else {
        createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
        }
    }
}

/**
 * The Koin-facing factory. Its return type names only [RemoteDataSource], so
 * `:app`'s DI wiring can call it without `:data`'s `implementation`-scoped
 * Supabase and Ktor dependencies ever needing to be visible from `:app` — the
 * module boundary the constitution draws around Supabase stays intact one
 * layer further out than the file boundary alone would manage.
 */
fun createRemoteDataSource(): RemoteDataSource =
    createSupabaseClient()?.let { SupabaseRemoteDataSource(it) } ?: NoOpRemoteDataSource()

/**
 * Whether this build carries a Supabase configuration — drives
 * `SignInUiState.configured`. Reads [BuildConfig] directly rather than
 * calling [createSupabaseClient] again, so checking this never constructs a
 * second, redundant client.
 */
fun isSupabaseConfigured(): Boolean =
    BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
