package com.giraffe.data.di

import com.giraffe.data.datasource.remote.api.HijriApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@Configuration
class NetworkModule {

    @Single
    @Named("baseUrl")
    fun provideBaseUrl(): String = "https://api.aladhan.com/v1/"

    @Single
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    @Single
    fun provideOkHttpClient(logger: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

    @Single
    fun provideRetrofit(
        @Named("baseUrl") baseUrl: String,
        client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Single
    fun provideRemoteDataSource(retrofit: Retrofit): HijriApi =
        retrofit.create(HijriApi::class.java)
}