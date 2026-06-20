package com.giraffe.data.utli

import retrofit2.Response
import java.io.IOException

suspend inline fun <reified T> executeApiSafely(
    apiCall: suspend () -> Response<T>,
): T {
    val response = runCatching { apiCall() }
        .onFailure { e ->
            throw when (e) {
                is IOException -> NoInternetException()
                else -> e
            }
        }
        .getOrThrow()

    return when (response.code()) {
        in 200..299 -> {
            runCatching {
                response.body() ?: run {
                    throw NetworkException()
                }
            }.onFailure { _ ->
                throw NetworkException()
            }.getOrThrow()
        }

        401 -> throw UnauthorizedException()
        in 429..499 -> throw NetworkException()
        in 500..599 -> throw NetworkException()
        else -> throw UnknownException()
    }
}

class NetworkException : Exception()
class UnauthorizedException : Exception()
class UnknownException : Exception()
class NoInternetException : Exception()