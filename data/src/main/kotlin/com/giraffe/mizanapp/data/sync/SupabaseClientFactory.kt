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
 */
fun createSupabaseClient(): SupabaseClient? {
    val url = BuildConfig.SUPABASE_URL
    val key = BuildConfig.SUPABASE_ANON_KEY
    if (url.isBlank() || key.isBlank()) return null

    return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
        httpEngine = OkHttp.create()
        install(Auth)
        install(Postgrest)
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
