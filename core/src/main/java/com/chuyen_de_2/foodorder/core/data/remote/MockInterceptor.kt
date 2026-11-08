package com.chuyen_de_2.foodorder.core.data.remote

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor phục vụ giả lập phản hồi rỗng khi chưa có dữ liệu.
 */
@Singleton
class MockInterceptor @Inject constructor() : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val uri = chain.request().url.toUri().toString()

        val responseString = when {
            (uri.contains("restaurants") || uri.contains("stores")) && (uri.contains("menu") || uri.contains("dishes")) -> getMockMenuJson(uri)
            uri.contains("restaurants") || uri.contains("stores") -> getMockRestaurantsJson()
            else -> """{"data": [], "message": "Unknown endpoint", "success": false}"""
        }

        // Giả lập network delay nhẹ 50ms
        Thread.sleep(50)

        return Response.Builder()
            .code(200)
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .message("OK (Empty Clean Slate)")
            .body(responseString.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun getMockRestaurantsJson(): String = "[]"

    private fun getMockMenuJson(uri: String): String = "[]"
}
