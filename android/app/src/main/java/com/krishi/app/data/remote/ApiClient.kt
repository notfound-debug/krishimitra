/*
 * ApiClient.kt — Creates the Retrofit HTTP client.
 *
 * This is a singleton (object in Kotlin = one instance for the whole app).
 * All API calls go through this single client.
 *
 * Features:
 * - Reads BASE_URL from BuildConfig (changes between debug/release)
 * - Adds logging in debug builds (so you can see HTTP requests in Logcat)
 * - 30 second timeout (for slow rural internet)
 * - GsonConverterFactory: auto-converts JSON ↔ Kotlin objects
 */

package com.krishi.app.data.remote

import com.krishi.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Base URL from build.gradle.kts:
    // Debug:   http://10.0.2.2:8000 (emulator → your laptop)
    // Release: https://YOUR-HF-SPACE.hf.space
    private val BASE_URL = BuildConfig.BASE_URL.let {
        // Ensure URL ends with /
        if (it.endsWith("/")) it else "$it/"
    }

    // HTTP client with timeout and logging
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // Slow 2G/3G connections
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // In debug: log full request/response body
                // In release: no logging (for performance + security)
                level = if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    // Retrofit instance — creates the ApiService implementation
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // The actual API service — use this from anywhere in the app:
    //   val digest = ApiClient.service.getDigest()
    val service: ApiService = retrofit.create(ApiService::class.java)
}
