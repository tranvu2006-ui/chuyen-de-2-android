package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * OtpSessionManager: Quản lý phiên xác minh mã OTP Email với thời hạn 1 ngày (24 giờ).
 *
 * Yêu cầu:
 * - Xác minh OTP 1 lần từ bất kể login hay register từ 1 gmail đồng nhất thì lưu lại trên thiết bị.
 * - Những lần sau trong vòng 24h sẽ bỏ qua bước OTP đối với email này.
 * - Nếu người dùng nhập 1 gmail khác, hoặc phiên 24h đã hết hạn, bắt buộc phải xác minh lại OTP và tạo session mới cho gmail đó.
 */
object OtpSessionManager {

    private const val PREFS_NAME = "foodorder_otp_session_prefs"
    private const val KEY_PREFIX = "otp_verified_at_"
    const val SESSION_DURATION_MS = 24L * 60L * 60L * 1000L // 24 giờ (1 ngày)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    /**
     * Kiểm tra xem email này có phiên xác minh OTP hợp lệ (chưa quá 24h) trên thiết bị không.
     */
    fun isSessionValid(context: Context, email: String): Boolean {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return false
        val verifiedAt = getPrefs(context).getLong(KEY_PREFIX + cleanEmail, 0L)
        if (verifiedAt <= 0L) return false
        val elapsed = System.currentTimeMillis() - verifiedAt
        return elapsed in 0 until SESSION_DURATION_MS
    }

    /**
     * Lưu lại phiên xác minh OTP thành công cho email này (có hiệu lực 24h).
     */
    fun markEmailVerified(context: Context, email: String) {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return
        getPrefs(context).edit()
            .putLong(KEY_PREFIX + cleanEmail, System.currentTimeMillis())
            .apply()
    }

    /**
     * Lấy số giờ còn lại của phiên xác minh OTP cho email này (từ 1 đến 24 giờ).
     */
    fun getRemainingHours(context: Context, email: String): Int {
        val cleanEmail = normalizeEmail(email)
        val verifiedAt = getPrefs(context).getLong(KEY_PREFIX + cleanEmail, 0L)
        if (verifiedAt <= 0L) return 0
        val remainingMs = SESSION_DURATION_MS - (System.currentTimeMillis() - verifiedAt)
        return if (remainingMs > 0) {
            val hours = (remainingMs / (1000L * 60L * 60L)).toInt()
            if (hours < 1) 1 else hours
        } else 0
    }

    /**
     * Xóa phiên xác minh của email (khi người dùng muốn đổi hoặc xác minh lại bằng OTP mới).
     */
    fun clearSession(context: Context, email: String) {
        val cleanEmail = normalizeEmail(email)
        getPrefs(context).edit()
            .remove(KEY_PREFIX + cleanEmail)
            .apply()
    }
}
