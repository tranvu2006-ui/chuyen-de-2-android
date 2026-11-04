package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailOtpApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun requestCode(email: String) {
        post("/auth/v1/otp", JSONObject().put("email", email).put("create_user", true))
    }

    suspend fun verifyCode(email: String, code: String): String {
        val result = post(
            "/auth/v1/verify",
            JSONObject().put("email", email).put("token", code).put("type", "email")
        )
        val verifiedEmail = result.optJSONObject("user")?.optString("email").orEmpty()
        if (!verifiedEmail.equals(email, ignoreCase = true)) {
            throw IllegalStateException("Email xác nhận không khớp email đăng ký.")
        }
        return verifiedEmail
    }

    private suspend fun post(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (!baseUrl.startsWith("https://") || baseUrl.contains("<") || apiKey.isBlank()) {
            throw IllegalStateException(
                "Chưa cấu hình Supabase. Thêm supabaseUrl và supabaseAnonKey vào Gradle properties."
            )
        }

        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            val result = try {
                JSONObject(responseText)
            } catch (error: Exception) {
                throw IllegalStateException("Máy chủ OTP trả về dữ liệu không hợp lệ.", error)
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    result.optString("msg").ifBlank {
                        result.optString("message").ifBlank {
                            result.optString("error_description").ifBlank {
                                result.optString("error").ifBlank { "Yêu cầu OTP thất bại (${response.code})." }
                            }
                        }
                    }
                )
            }
            result
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
