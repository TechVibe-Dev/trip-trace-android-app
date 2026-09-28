package com.techvibedev.triptrace.data.network

import java.io.IOException
import retrofit2.HttpException

// Coarse classification of a failed API call — enough for a screen to pick
// the right message, without every screen re-deriving it from a raw
// exception. Repositories already hand the original exception back inside
// Result.failure(e), so this just reads it.
sealed class ApiFailure {
    // The server answered with an error status (400, 422, 5xx...).
    data class Http(val code: Int) : ApiFailure()

    // 429 from the API's rate limiter (trip-trace-api#67). The wait comes from
    // the standard Retry-After header the API sends alongside the limit; null
    // if it's missing or not a plain number of seconds.
    data class RateLimited(val retryAfterSeconds: Int?) : ApiFailure()

    // No response at all: offline, DNS failure, timeout.
    data object NoConnection : ApiFailure()

    data object Unknown : ApiFailure()
}

fun Throwable.toApiFailure(): ApiFailure = when (this) {
    is HttpException -> if (code() == 429) {
        ApiFailure.RateLimited(response()?.headers()?.get("Retry-After")?.toIntOrNull())
    } else {
        ApiFailure.Http(code())
    }
    is IOException -> ApiFailure.NoConnection
    else -> ApiFailure.Unknown
}
