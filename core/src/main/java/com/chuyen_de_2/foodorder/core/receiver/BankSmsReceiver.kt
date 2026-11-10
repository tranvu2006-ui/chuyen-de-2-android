package com.chuyen_de_2.foodorder.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * [Oishi Food] BroadcastReceiver Lắng Nghe & Xử Lý Tin Nhắn SMS Ngân Hàng
 * Tự động đối soát chuyển khoản (VietQR/MoMo) và hoàn tiền (Refund) theo chuẩn thu-10/11 (#ORD_OISHI_XXXXXX)
 */
class BankSmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BankSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            intent.action != "android.provider.Telephony.SMS_RECEIVED"
        ) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBodyBuilder = StringBuilder()
            var sender = ""

            for (sms in messages) {
                sender = sms.displayOriginatingAddress ?: sms.originatingAddress.orEmpty()
                fullBodyBuilder.append(sms.displayMessageBody ?: sms.messageBody.orEmpty())
            }

            val smsContent = fullBodyBuilder.toString()
            Log.d(TAG, "Nhận được SMS từ $sender: $smsContent")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "📩 Nhận SMS từ $sender: ${smsContent.take(40)}...", Toast.LENGTH_SHORT).show()
            }

            // Tiến hành đối soát nội dung SMS với các đơn hàng trên Firebase
            matchAndConfirmOrderPayment(context, smsContent)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xử lý SMS: ${e.message}", e)
        }
    }

    private fun matchAndConfirmOrderPayment(context: Context, smsContent: String) {
        val database = FirebaseDatabase.getInstance()
        val ordersRef = database.getReference("orders")

        // 1. Trích xuất các mã token tiềm năng từ SMS (ORD_OISHI_..., ORD..., OISHI..., OI..., DH..., HT...)
        val extractedTokens = mutableSetOf<String>()
        val patterns = listOf(
            Regex("(?:ORD[-_]?OISHI[-_]?|OISHI[-_]?|ORD[-_]?|DH[-_]?|OI|HT[-_\\s]?)([A-Z0-9]{4,20})", RegexOption.IGNORE_CASE),
            Regex("([A-Z0-9]{6,20})", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.findAll(smsContent).forEach { match ->
                val fullMatch = match.value.trim().uppercase()
                extractedTokens.add(fullMatch)
                if (match.groupValues.size > 1) {
                    val groupVal = match.groupValues[1].trim().uppercase()
                    if (groupVal.isNotBlank()) extractedTokens.add(groupVal)
                }
            }
        }

        // 2. Tra cứu danh sách đơn hàng gần nhất để đối soát chính xác
        queryRecentOrdersAndMatch(context, ordersRef, smsContent, extractedTokens)
    }

    private fun queryRecentOrdersAndMatch(
        context: Context,
        ordersRef: com.google.firebase.database.DatabaseReference,
        smsContent: String,
        extractedTokens: Set<String>
    ) {
        ordersRef.limitToLast(50).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (orderSnap in snapshot.children) {
                    val key = orderSnap.key.orEmpty()
                    val fieldOrderId = orderSnap.child("orderId").getValue(String::class.java).orEmpty()
                    val orderId = if (key.isNotBlank()) key else fieldOrderId
                    if (orderId.isBlank()) continue

                    // Mã chuẩn thu-10/11: 6 ký tự cuối
                    val shortCode = if (orderId.length >= 6) orderId.takeLast(6).uppercase() else orderId.uppercase()
                    val oiShortCode = QRCodeUtils.getShortOrderCode(orderId)
                    val cleanOrderIdNoHyphen = orderId.replace("-", "").trim().uppercase()
                    val rawUpperOrderId = orderId.trim().uppercase()

                    // Kiểm tra xem SMS hoặc các token bóc tách được có khớp với đơn này không
                    val isTokenMatch = extractedTokens.any { token ->
                        token.equals(rawUpperOrderId, ignoreCase = true) ||
                        token.equals(cleanOrderIdNoHyphen, ignoreCase = true) ||
                        token.equals(shortCode, ignoreCase = true) ||
                        token.equals(oiShortCode, ignoreCase = true) ||
                        token.equals("ORD_OISHI_$shortCode", ignoreCase = true) ||
                        token.equals("ORD-$shortCode", ignoreCase = true) ||
                        token.equals("ORD_$shortCode", ignoreCase = true) ||
                        token.equals("OISHI_$shortCode", ignoreCase = true) ||
                        token.equals("DH$shortCode", ignoreCase = true) ||
                        token.equals("HT$shortCode", ignoreCase = true) ||
                        (token.length >= 6 && rawUpperOrderId.endsWith(token)) ||
                        (token.length >= 6 && cleanOrderIdNoHyphen.endsWith(token))
                    }

                    val isSmsTextMatch = smsContent.contains(orderId, ignoreCase = true) ||
                            (fieldOrderId.isNotBlank() && smsContent.contains(fieldOrderId, ignoreCase = true)) ||
                            (cleanOrderIdNoHyphen.length >= 6 && smsContent.contains(cleanOrderIdNoHyphen, ignoreCase = true)) ||
                            (shortCode.length >= 5 && smsContent.contains(shortCode, ignoreCase = true)) ||
                            smsContent.contains(oiShortCode, ignoreCase = true) ||
                            smsContent.contains("ORD_OISHI_$shortCode", ignoreCase = true) ||
                            smsContent.contains("ORD_OISHI $shortCode", ignoreCase = true) ||
                            smsContent.contains("ORD-$shortCode", ignoreCase = true) ||
                            smsContent.contains("ORD_$shortCode", ignoreCase = true) ||
                            smsContent.contains("OISHI_$shortCode", ignoreCase = true) ||
                            smsContent.contains("OISHI $shortCode", ignoreCase = true) ||
                            smsContent.contains("DH$shortCode", ignoreCase = true) ||
                            smsContent.contains("DH $shortCode", ignoreCase = true) ||
                            smsContent.contains("HT $shortCode", ignoreCase = true) ||
                            smsContent.contains("HT$shortCode", ignoreCase = true) ||
                            smsContent.contains("HT_$shortCode", ignoreCase = true) ||
                            smsContent.contains("HT-$shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoàn tiền $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoàn tiền $oiShortCode", ignoreCase = true) ||
                            smsContent.contains("Hoan tien $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoan tien $oiShortCode", ignoreCase = true) ||
                            smsContent.contains("Hoa tien $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoa tien $oiShortCode", ignoreCase = true)

                    if (!isTokenMatch && !isSmsTextMatch) continue

                    processOrderUpdate(context, ordersRef, orderId, orderSnap, smsContent)
                    break
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase query error: ${error.message}")
            }
        })
    }

    private fun processOrderUpdate(
        context: Context,
        ordersRef: com.google.firebase.database.DatabaseReference,
        orderId: String,
        orderSnap: DataSnapshot,
        smsContent: String
    ) {
        val paymentStatus = orderSnap.child("paymentStatus").getValue(String::class.java).orEmpty()
        val orderStatus = orderSnap.child("status").getValue(String::class.java).orEmpty().ifBlank {
            orderSnap.child("orderStatus").getValue(String::class.java).orEmpty()
        }
        val refundStatus = orderSnap.child("refundStatus").getValue(String::class.java).orEmpty()
        val shortCode = if (orderId.length >= 6) orderId.takeLast(6).uppercase() else orderId.uppercase()
        val displayOrderCode = "#ORD_OISHI_$shortCode"

        val isRefund = smsContent.contains("hoàn tiền", ignoreCase = true) ||
                smsContent.contains("hoan tien", ignoreCase = true) ||
                smsContent.contains("hoa tien", ignoreCase = true) ||
                smsContent.contains("refund", ignoreCase = true) ||
                smsContent.contains("HT ", ignoreCase = true) ||
                smsContent.contains("HT$shortCode", ignoreCase = true) ||
                smsContent.contains("HT-$shortCode", ignoreCase = true) ||
                smsContent.contains("HT_$shortCode", ignoreCase = true) ||
                refundStatus == Order.RefundStatus.PENDING_REFUND ||
                refundStatus == Order.RefundStatus.PENDING_INFO ||
                orderStatus.equals(Order.Status.CANCELLED, ignoreCase = true)

        // TRƯỜNG HỢP 1: Hoàn tiền (Đơn đã bị hủy hoặc đang chờ hoàn tiền)
        if (isRefund || (orderStatus.equals(Order.Status.CANCELLED, ignoreCase = true) && refundStatus != Order.RefundStatus.REFUNDED)) {
            Log.d(TAG, "Khớp SMS hoàn tiền cho đơn: $orderId ($displayOrderCode)")
            val refundUpdates = mapOf<String, Any>(
                "refundStatus" to Order.RefundStatus.REFUNDED,
                "refundedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            ordersRef.child(orderId).updateChildren(refundUpdates).addOnSuccessListener {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "✅ Đã nhận SMS hoàn tiền! Đơn hàng $displayOrderCode cập nhật ĐÃ HOÀN TIỀN.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Lỗi cập nhật hoàn tiền: ${error.message}, thử fallback chỉ cập nhật refundStatus")
                ordersRef.child(orderId).child("refundStatus").setValue(Order.RefundStatus.REFUNDED).addOnSuccessListener {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context.applicationContext,
                            "✅ Đơn hàng $displayOrderCode cập nhật ĐÃ HOÀN TIỀN!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            return
        }

        // TRƯỜNG HỢP 2: Khách thanh toán tiền vào (Chuyển khoản VietQR / MoMo)
        if (!paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
            Log.d(TAG, "Khớp SMS thanh toán cho đơn: $orderId ($displayOrderCode)")
            val updates = mutableMapOf<String, Any>(
                "paymentStatus" to Order.PaymentStatus.PAID,
                "isPaid" to true,
                "paidAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            // Nếu đơn đang chờ thanh toán (AWAITING_PAYMENT), chuyển tiếp sang PENDING để quán nhận đơn
            if (orderStatus.equals(Order.Status.AWAITING_PAYMENT, ignoreCase = true) || orderStatus.isBlank()) {
                updates["status"] = Order.Status.PENDING
                updates["orderStatus"] = Order.Status.PENDING
            }

            ordersRef.child(orderId).updateChildren(updates).addOnSuccessListener {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "✅ Đã nhận SMS biến động số dư! Đơn hàng $displayOrderCode tự động xác nhận thành công.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Lỗi cập nhật đầy đủ, thử fallback paymentStatus: ${error.message}")
                val fallback = mapOf<String, Any>(
                    "paymentStatus" to Order.PaymentStatus.PAID,
                    "isPaid" to true,
                    "paidAt" to System.currentTimeMillis()
                )
                ordersRef.child(orderId).updateChildren(fallback).addOnSuccessListener {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context.applicationContext,
                            "✅ Đơn hàng $displayOrderCode đã xác nhận thanh toán thành công!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}