package com.chuyen_de_2.foodorder.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object MomoPaymentGatewayClient {

    private const val VIETQR_GENERATE_API = "https://api.vietqr.io/v2/generate"

    data class MomoQrResult(
        val isSuccess: Boolean,
        val qrCodeData: String,
        val message: String
    )

    suspend fun generateRealMomoQr(
        momoPhone: String,
        momoHolderName: String,
        amount: Long,
        transferSyntax: String,
        bankBin: String
    ): MomoQrResult = withContext(Dispatchers.IO) {
        val cleanPhone = momoPhone.replace(Regex("[^0-9]"), "")
        val cleanName = momoHolderName.trim().uppercase().ifBlank { "OISHI FOOD" }
        val cleanNote = transferSyntax.trim()
        val acqId = bankBin.trim().toIntOrNull()
        if (cleanPhone.isBlank() || cleanNote.isBlank() || amount <= 0L || acqId == null) {
            return@withContext MomoQrResult(
                isSuccess = false,
                qrCodeData = "",
                message = "Thiếu số ví, tên chủ ví, ngân hàng hoặc số tiền hợp lệ."
            )
        }

        try {
            val requestBody = JSONObject().apply {
                put("accountNo", cleanPhone)
                put("accountName", cleanName)
                put("acqId", acqId)
                put("amount", amount)
                put("addInfo", cleanNote)
                put("format", "text")
                put("template", "compact")
            }
            val responseBody = postJson(VIETQR_GENERATE_API, requestBody.toString())
            val response = JSONObject(responseBody)
            val qrCode = response.optJSONObject("data")?.optString("qrCode").orEmpty()
            if (response.optString("code") == "00" && qrCode.isNotBlank()) {
                return@withContext MomoQrResult(true, qrCode, "")
            }
            fallbackQr(cleanPhone, cleanName, amount, cleanNote,
                response.optString("desc").ifBlank { "VietQR không tạo được mã thanh toán." })
        } catch (error: Exception) {
            fallbackQr(
                cleanPhone,
                cleanName,
                amount,
                cleanNote,
                error.message ?: "Không kết nối được dịch vụ tạo mã MoMo."
            )
        }
    }

    private fun fallbackQr(
        phone: String,
        holderName: String,
        amount: Long,
        transferSyntax: String,
        error: String
    ) = MomoQrResult(
        isSuccess = false,
        qrCodeData = QRCodeUtils.buildMomoQrPayload(
            phone = phone,
            receiverName = holderName,
            amount = amount.coerceAtLeast(1000L),
            transferSyntax = transferSyntax
        ),
        message = "$error Dùng mã QR ví MoMo dự phòng; hãy kiểm tra kỹ người nhận trước khi chuyển."
    )

    private fun postJson(endpoint: String, jsonBody: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(jsonBody)
            }
            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            return BufferedReader(InputStreamReader(responseStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }
}
