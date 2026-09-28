package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // BODY logs full request/response contents — including plaintext
    // passwords sent to /auth/login and /auth/register, and the
    // Authorization header on every other call — to Logcat. That's fine
    // for local debugging, but was previously unconditional, meaning even
    // a release build would do this. Gated to debug builds only now; a
    // release build logs nothing from this interceptor.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApiService: AuthApiService by lazy { retrofit.create(AuthApiService::class.java) }
    val tripApiService: TripApiService by lazy { retrofit.create(TripApiService::class.java) }
    val favoritePlaceApiService: FavoritePlaceApiService by lazy {
        retrofit.create(FavoritePlaceApiService::class.java)
    }
}
