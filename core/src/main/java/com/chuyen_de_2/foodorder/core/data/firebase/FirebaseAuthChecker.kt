package com.chuyen_de_2.foodorder.core.data.firebase

import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kiểm tra sự tồn tại của Email trên Firebase Authentication thông qua Google Identity Toolkit REST API.
 * Thực hiện độc lập trên luồng HTTP riêng, tuyệt đối không làm ảnh hưởng hay thay đổi phiên FirebaseAuth
 * hiện tại trên thiết bị người dùng.
 */
@Singleton
class FirebaseAuthChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val FALLBACK_API_KEY = "AIzaSyAUV30gmgYxOCs94sp92g5ax27ihwaaFvE"
    }

    private fun getApiKey(): String {
        return try {
            FirebaseApp.getInstance().options.apiKey
        } catch (_: Exception) {
            null
        }?.takeIf { it.isNotBlank() } ?: FALLBACK_API_KEY
    }

    /**
     * Kiểm tra xem [email] đã được đăng ký tài khoản trên Firebase hay chưa.
     * @return true nếu tài khoản đã tồn tại trên Firebase, false nếu chưa.
     */
    suspend fun isEmailRegistered(email: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val apiKey = getApiKey()
        val signupUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
        val dummyPassword = "CheckProbe_${UUID.randomUUID()}!9aZ"

        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", dummyPassword)
            put("returnSecureToken", true)
        }

        val request = Request.Builder()
            .url(signupUrl)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorObj = try { JSONObject(responseBody).optJSONObject("error") } catch (_: Exception) { null }
                    val errorMsg = errorObj?.optString("message").orEmpty()
                    if (errorMsg.contains("EMAIL_EXISTS", ignoreCase = true)) {
                        return@withContext true
                    }
                    if (errorMsg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true)) {
                        throw IllegalStateException("Quá nhiều yêu cầu từ thiết bị này. Vui lòng thử lại sau ít phút.")
                    }
                    return@withContext false
                } else {
                    // Đăng ký tạm thành công -> Email này CHƯA từng tồn tại trên Firebase!
                    // Phải xóa ngay tài khoản tạm này để trả email về trạng thái chưa đăng ký
                    val json = try { JSONObject(responseBody) } catch (_: Exception) { null }
                    val idToken = json?.optString("idToken").orEmpty()
                    if (idToken.isNotBlank()) {
                        deleteTemporaryAccount(apiKey, idToken)
                    }
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Không thể kiểm tra tài khoản. Vui lòng kiểm tra kết nối mạng.", e)
        }
    }

    private fun deleteTemporaryAccount(apiKey: String, idToken: String) {
        val deleteUrl = "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$apiKey"
        val payload = JSONObject().apply {
            put("idToken", idToken)
        }
        val request = Request.Builder()
            .url(deleteUrl)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { /* cleanup hoàn tất */ }
        } catch (_: Exception) {
            // Thử lại 1 lần nếu gặp lỗi mạng chập chờn
            try {
                client.newCall(request).execute().use { }
            } catch (_: Exception) {}
        }
    }
}
