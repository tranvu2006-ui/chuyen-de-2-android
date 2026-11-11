package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * RegistrationOtpManager: Quản lý trạng thái và thời hạn của mã OTP đang chờ xác thực trong luồng đăng ký.
 *
 * Mục đích:
 * - Khi người dùng bấm "Đăng ký", hệ thống gửi OTP và lưu lại thời điểm gửi.
 * - Nếu người dùng rời khỏi màn hình OTP (ấn quay lại màn đăng ký) rồi bấm "Đăng ký" lại với cùng email,
 *   nếu OTP vừa gửi vẫn còn hiệu lực (trong vòng 10 phút), hệ thống sẽ cho phép chuyển ngay sang màn hình
 *   xác minh OTP mà không cần gửi lại request (tránh bị lỗi rate limit / cooldown 60s của Supabase).
 * - Cung cấp thời gian cooldown còn lại để màn hình OTP tiếp tục đếm ngược chính xác.
 */
object RegistrationOtpManager {

    private const val PREFS_NAME = "foodorder_registration_otp_prefs"
    private const val KEY_PENDING_EMAIL = "pending_reg_email"
    private const val KEY_PENDING_TIME = "pending_reg_time"

    // Thời hạn hiệu lực của mã OTP đăng ký (10 phút)
    const val OTP_VALIDITY_DURATION_MS = 10L * 60L * 1000L

    // Thời gian giãn cách tối thiểu giữa 2 lần gửi mã OTP mới (60 giây)
    const val RESEND_COOLDOWN_MS = 60_000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    /**
     * Ghi nhận đã gửi mã OTP thành công cho email này tại thời điểm timestamp.
     */
    fun recordOtpSent(context: Context, email: String, timestamp: Long = System.currentTimeMillis()) {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return
        getPrefs(context).edit()
            .putString(KEY_PENDING_EMAIL, cleanEmail)
            .putLong(KEY_PENDING_TIME, timestamp)
            .apply()
    }

    /**
     * Kiểm tra xem email này có mã OTP vừa gửi vẫn còn hiệu lực (trong vòng 10 phút) hay không.
     */
    fun hasValidPendingOtp(context: Context, email: String): Boolean {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return false
        val prefs = getPrefs(context)
        val savedEmail = prefs.getString(KEY_PENDING_EMAIL, null) ?: return false
        if (!savedEmail.equals(cleanEmail, ignoreCase = true)) return false

        val sentTime = prefs.getLong(KEY_PENDING_TIME, 0L)
        if (sentTime <= 0L) return false

        val elapsed = System.currentTimeMillis() - sentTime
        return elapsed in 0 until OTP_VALIDITY_DURATION_MS
    }

    /**
     * Lấy thời gian còn lại (mili-giây) của đếm ngược 60s gửi lại mã cho email này.
     * Trả về 0L nếu đã quá 60s hoặc không có mã nào đang chờ.
     */
    fun getRemainingCooldownMs(context: Context, email: String): Long {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return 0L
        val prefs = getPrefs(context)
        val savedEmail = prefs.getString(KEY_PENDING_EMAIL, null) ?: return 0L
        if (!savedEmail.equals(cleanEmail, ignoreCase = true)) return 0L

        val sentTime = prefs.getLong(KEY_PENDING_TIME, 0L)
        if (sentTime <= 0L) return 0L

        val elapsed = System.currentTimeMillis() - sentTime
        val remaining = RESEND_COOLDOWN_MS - elapsed
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Xóa trạng thái OTP đăng ký của email (gọi khi đăng ký thành công hoặc khi muốn hủy mã cũ).
     */
    fun clearPendingOtp(context: Context, email: String? = null) {
        val cleanEmail = email?.let { normalizeEmail(it) }
        val prefs = getPrefs(context)
        if (cleanEmail != null) {
            val savedEmail = prefs.getString(KEY_PENDING_EMAIL, null)
            if (savedEmail != null && !savedEmail.equals(cleanEmail, ignoreCase = true)) {
                return
            }
        }
        prefs.edit()
            .remove(KEY_PENDING_EMAIL)
            .remove(KEY_PENDING_TIME)
            .apply()
    }

    /**
     * Kiểm tra xem thông báo lỗi có phải là lỗi bị giới hạn tần suất gửi (rate limit / cooldown) không.
     */
    fun isRateLimitError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val lower = errorMessage.lowercase()
        return lower.contains("60 second") ||
                lower.contains("rate limit") ||
                lower.contains("over_email_send_rate_limit") ||
                lower.contains("security purposes") ||
                lower.contains("quá nhiều") ||
                lower.contains("chờ")
    }
}
