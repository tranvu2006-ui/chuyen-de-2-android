package com.chuyen_de_2.foodorder.core.util

object PhoneNumberUtils {
    fun toE164Vietnam(phone: String): String {
        val compact = phone.trim().replace(Regex("[\\s()-]"), "")
        val normalized = when {
            compact.startsWith("+") -> compact
            compact.startsWith("00") -> "+${compact.drop(2)}"
            compact.startsWith("84") -> "+$compact"
            compact.startsWith("0") -> "+84${compact.drop(1)}"
            else -> throw IllegalArgumentException("Nhập số điện thoại Việt Nam, ví dụ 0912345678.")
        }
        if (!normalized.matches(Regex("\\+[1-9]\\d{7,14}"))) {
            throw IllegalArgumentException("Số điện thoại không hợp lệ.")
        }
        return normalized
    }
}
