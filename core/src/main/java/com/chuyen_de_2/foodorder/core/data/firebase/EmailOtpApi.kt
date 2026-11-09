package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client API giao tiếp với Supabase Auth REST API phục vụ chức năng nhận và xác thực mã OTP qua Email.
 */
@Singleton
class EmailOtpApi @Inject constructor() {

    // Tối ưu hóa OkHttpClient với ConnectionPool dùng lại socket HTTPS, HTTP/2 và timeout linh hoạt
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * BƯỚC 1: Gửi yêu cầu máy chủ Supabase tạo mã OTP 6 số và gửi về Gmail của khách.
     * Endpoint: /auth/v1/otp (Chuẩn REST API Supabase Auth)
     */
    suspend fun requestCode(email: String) {
        // Supabase gửi OTP tới email; create_user cho phép xác minh email trước khi Firebase tạo tài khoản.
        val jsonPayload = JSONObject()
            .put("email", email)
            .put("create_user", true)
            .put("type", "email") // Chọn luồng OTP email thay vì xác thực bằng số điện thoại.
        // Gửi POST tới Supabase Auth; lỗi được trả về caller, không giả thành công.
        post("/auth/v1/otp", jsonPayload)
    }

    /**
     * BƯỚC 2: Kiểm tra mã OTP 6 số người dùng vừa gõ vào hộp thoại.
     * Endpoint: /auth/v1/verify
     */
    suspend fun verifyCode(email: String, code: String): String {
        // Gửi email, OTP người dùng nhập và loại xác thực để Supabase đối chiếu.
        val jsonPayload = JSONObject()
            .put("email", email)
            .put("token", code)
            .put("type", "email")

        // Chỉ coi mã hợp lệ khi Supabase Auth trả về thành công.
        val result = post("/auth/v1/verify", jsonPayload)

        // Đọc email do Supabase xác nhận; không chỉ tin email do app gửi lên.
        val verifiedEmail = result.optJSONObject("user")?.optString("email").orEmpty()
        if (!verifiedEmail.equals(email, ignoreCase = true)) {
            throw IllegalStateException("Email xác nhận không khớp email đăng ký.")
        }
        // Trả email đã xác minh để AuthRepository cho phép tiếp tục đăng ký Firebase.
        return verifiedEmail
    }

    /**
     * Thực hiện gửi HTTP POST request kèm API key đến Supabase endpoint.
     */
    private suspend fun post(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        // Thực hiện HTTP trên dispatcher IO để không chặn main thread Android.
        // Cấu hình dùng URL project và publishable/anon key; tuyệt đối không nhúng service_role key vào app.
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val apiKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        // Dừng sớm nếu URL/key chưa cấu hình hoặc vẫn còn giá trị mẫu.
        if (!baseUrl.startsWith("https://") || baseUrl.contains("<") || apiKey.isBlank()) {
            throw IllegalStateException(
                "Chưa cấu hình Supabase. Thêm supabaseUrl và supabaseAnonKey vào Gradle properties."
            )
        }

        // Supabase yêu cầu header apikey và body JSON cho Auth REST API.
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", apiKey)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        // Đóng response tự động; đọc body một lần để parse response thành công hoặc lỗi.
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            // Supabase trả JSON hoặc rỗng khi 200 OK
            val result = try {
                if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            } catch (error: Exception) {
                throw IllegalStateException("Máy chủ OTP trả về dữ liệu không hợp lệ.", error)
            }
            // Chuyển lỗi HTTP từ Supabase thành thông báo để UI không tiếp tục luồng đăng ký.
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
        // Content-Type chuẩn của payload Supabase Auth.
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
