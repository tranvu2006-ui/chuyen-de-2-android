package com.chuyen_de_2.foodorder.core.util

/**
 * Tiện ích chuẩn hóa và kiểm tra số điện thoại Việt Nam phục vụ gửi OTP qua SMS Firebase.
 */
object PhoneNumberUtils {

    /**
     * Chuẩn hóa số điện thoại Việt Nam về định dạng E.164 quốc tế (ví dụ: +84912345678).
     * @param phone Số điện thoại nhập từ người dùng.
     * @return Chuỗi E.164 hợp lệ hoặc null nếu không đúng định dạng.
     */
    fun normalizeToE164(phone: String): String? {
        val clean = phone.replace(Regex("[\\s\\-\\.\\(\\)]"), "").trim()
        if (clean.isBlank()) return null

        val e164 = when {
            clean.startsWith("+84") -> clean
            clean.startsWith("84") -> "+$clean"
            clean.startsWith("0") -> "+84${clean.substring(1)}"
            else -> "+84$clean"
        }

        // Kiểm tra đầu số +84 kèm 9 chữ số tiếp theo (tổng 12 ký tự)
        return if (e164.matches(Regex("^\\+84[3|5|7|8|9][0-9]{8}$")) || e164.matches(Regex("^\\+84[0-9]{9,10}$"))) {
            e164
        } else {
            null
        }
    }

    /**
     * Kiểm tra số điện thoại có phải là định dạng hợp lệ tại Việt Nam hay không.
     */
    fun isValidVietnamesePhone(phone: String): Boolean {
        return normalizeToE164(phone) != null
    }
}
