package com.techvibedev.triptrace.data.network

import com.techvibedev.triptrace.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

    // The API runs on Render's free plan, which puts it to sleep after 15
    // minutes without requests. The first call after that waits about a
    // minute while it boots, so OkHttp's 10s defaults would fail it with a
    // timeout even though the server is on its way. These leave room for a
    // cold start; SlowRequestTracker lets the UI explain the wait.
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    private val okHttpClient = baseClient.newBuilder()
        .addInterceptor(SlowRequestTracker.interceptor)
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

    // Fire-and-forget GET /health, so a sleeping API starts booting as soon
    // as the app opens instead of when the user's first real action needs
    // it. Goes through baseClient on purpose: nobody is waiting on this
    // call, so it shouldn't trigger the "server is waking up" notice.
    fun warmUp() {
        val request = Request.Builder().url(ApiConfig.BASE_URL + "health").build()
        baseClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = response.close()

            override fun onFailure(call: Call, e: IOException) = Unit
        })
    }
}
