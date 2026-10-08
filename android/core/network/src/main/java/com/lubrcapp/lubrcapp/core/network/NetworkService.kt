package com.lubrcapp.lubrcapp.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun interface ApiEndpointProvider {
    fun getBaseUrl(): String
}

@Singleton
class NetworkService @Inject constructor(
    endpointProvider: ApiEndpointProvider,
) {
    private val baseUrl = endpointProvider.getBaseUrl().also {
        require(it.endsWith("/")) { "Retrofit base URL must end with /" }
        require(it.startsWith("https://") || it.startsWith("http://")) {
            "Retrofit base URL must use http or https"
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> createService(serviceClass: Class<T>): T = retrofit.create(serviceClass)

    fun getRetrofit(): Retrofit = retrofit
    fun getClient(): OkHttpClient = client
    fun getBaseUrl(): String = baseUrl
}
