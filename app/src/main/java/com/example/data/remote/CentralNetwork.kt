package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object CentralNetwork {

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
        
        authToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: CentralApiService by lazy {
        val baseUrl = "https://api.ronycine.com/api/v1/"
        
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CentralApiService::class.java)
    }
}
