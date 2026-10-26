package com.chuyen_de_2.foodorder.core.util

import java.util.Locale

data class VnPayResult(
    val success: Boolean,
    val responseCode: String,
    val transactionStatus: String,
    val txnRef: String,
    val transactionNo: String,
    val amountVnd: Long,
    val bankCode: String,
    val payDate: String,
    val message: String,
    val hashValid: Boolean
)

object VnPayHelper {

    fun isReturnUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.US)
        return (lower.contains("vnp_responsecode=") && lower.contains("vnp_securehash=")) ||
            lower.contains("/vnpay/return") ||
            lower.contains("vnpay_return")
    }

    fun messageForCode(code: String): String = when (code) {
        "00" -> "Giao dịch thành công"
        "07" -> "Trừ tiền thành công, giao dịch bị nghi ngờ (liên hệ VNPay)"
        "09" -> "Thẻ/Tài khoản chưa đăng ký InternetBanking"
        "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"
        "11" -> "Hết hạn chờ thanh toán. Vui lòng thực hiện lại"
        "12" -> "Thẻ/Tài khoản bị khóa"
        "13" -> "Sai OTP. Vui lòng thanh toán lại"
        "24" -> "Khách hàng đã hủy giao dịch"
        "51" -> "Tài khoản không đủ số dư"
        "65" -> "Tài khoản vượt hạn mức giao dịch trong ngày"
        "75" -> "Ngân hàng thanh toán đang bảo trì"
        "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định"
        "99" -> "Lỗi không xác định. Vui lòng thử lại"
        else -> "Thanh toán không thành công (mã $code)"
    }
}
