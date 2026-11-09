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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * [Môn Lập Trình Di Động 2] BroadcastReceiver Lắng Nghe & Xử Lý Tin Nhắn SMS Ngân Hàng
 * 
 * Nguyên lý hoạt động:
 * 1. Bắt sự kiện hệ thống `android.provider.Telephony.SMS_RECEIVED` khi máy nhận SMS.
 * 2. Đọc nội dung tin nhắn biến động số dư từ ngân hàng (MB, VCB, Techcombank, Momo...).
 * 3. Bóc tách mã đơn hàng (ví dụ: DH123, ORD_OISHI_88291 hoặc mã ký tự ngắn).
 * 4. Tự động cập nhật trạng thái đơn hàng trên Firebase sang `PAID` (Đã thanh toán).
 * 5. Báo chuông "Ting ting" và hiện thông báo xác nhận thành công.
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

        // 1. Trích xuất trực tiếp mã đơn dạng OIxxxxxx (từ 4 đến 12 ký tự, ví dụ: OI02733 hoặc OI41BD7BEF)
        val regex = Regex("OI[A-Z0-9]{4,12}", RegexOption.IGNORE_CASE)
        val extractedOrderId = regex.find(smsContent)?.value?.uppercase()

        if (!extractedOrderId.isNullOrBlank()) {
            Log.d(TAG, "Trích xuất trực tiếp mã đơn từ SMS: $extractedOrderId")
            ordersRef.child(extractedOrderId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        processOrderUpdate(context, ordersRef, extractedOrderId, snapshot, smsContent)
                    } else {
                        queryRecentOrdersAndMatch(context, ordersRef, smsContent, extractedOrderId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    queryRecentOrdersAndMatch(context, ordersRef, smsContent, extractedOrderId)
                }
            })
        } else {
            queryRecentOrdersAndMatch(context, ordersRef, smsContent, null)
        }
    }

    private fun queryRecentOrdersAndMatch(
        context: Context,
        ordersRef: com.google.firebase.database.DatabaseReference,
        smsContent: String,
        extractedOrderId: String? = null
    ) {
        ordersRef.limitToLast(50).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (orderSnap in snapshot.children) {
                    val key = orderSnap.key.orEmpty()
                    val fieldOrderId = orderSnap.child("orderId").getValue(String::class.java).orEmpty()
                    val orderId = if (key.isNotBlank()) key else fieldOrderId
                    val shortCode = if (orderId.length >= 6) orderId.takeLast(6).uppercase() else orderId.uppercase()
                    val oiShortCode = com.chuyen_de_2.foodorder.core.util.QRCodeUtils.getShortOrderCode(orderId)
                    val cleanOrderIdNoHyphen = orderId.replace("-", "").trim()

                    val isExtractedMatched = !extractedOrderId.isNullOrBlank() && (
                        orderId.contains(extractedOrderId, ignoreCase = true) ||
                        oiShortCode.contains(extractedOrderId, ignoreCase = true) ||
                        extractedOrderId.contains(oiShortCode, ignoreCase = true) ||
                        orderId.endsWith(extractedOrderId.removePrefix("OI"), ignoreCase = true) ||
                        cleanOrderIdNoHyphen.endsWith(extractedOrderId.removePrefix("OI"), ignoreCase = true)
                    )

                    val isMatched = isExtractedMatched ||
                            smsContent.contains(orderId, ignoreCase = true) ||
                            (fieldOrderId.isNotBlank() && smsContent.contains(fieldOrderId, ignoreCase = true)) ||
                            smsContent.contains(oiShortCode, ignoreCase = true) ||
                            smsContent.contains(cleanOrderIdNoHyphen, ignoreCase = true) ||
                            smsContent.contains(shortCode, ignoreCase = true) ||
                            smsContent.contains("DH$shortCode", ignoreCase = true) ||
                            smsContent.contains("ORD_OISHI_$shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoa tien $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoa tien $oiShortCode", ignoreCase = true) ||
                            smsContent.contains("Hoàn tiền $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoàn tiền $oiShortCode", ignoreCase = true) ||
                            smsContent.contains("Hoan tien $shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoan tien $oiShortCode", ignoreCase = true) ||
                            smsContent.contains("HT $shortCode", ignoreCase = true) ||
                            smsContent.contains("HT $oiShortCode", ignoreCase = true)

                    if (!isMatched) continue
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

        val isRefund = smsContent.contains("hoàn tiền", ignoreCase = true) ||
                smsContent.contains("hoan tien", ignoreCase = true) ||
                smsContent.contains("hoa tien", ignoreCase = true) ||
                smsContent.contains("refund", ignoreCase = true) ||
                smsContent.contains("HT ", ignoreCase = true) ||
                smsContent.contains("HT$shortCode", ignoreCase = true) ||
                smsContent.contains("HT", ignoreCase = true) ||
                refundStatus == Order.RefundStatus.PENDING_REFUND ||
                refundStatus == Order.RefundStatus.PENDING_INFO ||
                orderStatus.equals(Order.Status.CANCELLED, ignoreCase = true)

        // TRƯỜNG HỢP 1: Hoàn tiền (Đơn đã bị hủy hoặc đang chờ hoàn tiền)
        if (isRefund || (orderStatus.equals(Order.Status.CANCELLED, ignoreCase = true) && refundStatus != Order.RefundStatus.REFUNDED)) {
            Log.d(TAG, "Khớp SMS hoàn tiền cho đơn: $orderId")
            val refundUpdates = mapOf<String, Any>(
                "refundStatus" to Order.RefundStatus.REFUNDED,
                "refundedAt" to System.currentTimeMillis()
            )
            ordersRef.child(orderId).updateChildren(refundUpdates).addOnSuccessListener {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "✅ Đã nhận SMS hoàn tiền! Đơn hàng #$shortCode cập nhật ĐÃ HOÀN TIỀN.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "Lỗi cập nhật hoàn tiền: ${error.message}, thử fallback chỉ cập nhật refundStatus")
                ordersRef.child(orderId).child("refundStatus").setValue(Order.RefundStatus.REFUNDED).addOnSuccessListener {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context.applicationContext,
                            "✅ Đơn hàng #$shortCode cập nhật ĐÃ HOÀN TIỀN!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            return
        }

        // TRƯỜNG HỢP 2: Khách thanh toán tiền vào
        if (!paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
            Log.d(TAG, "Khớp SMS thanh toán cho đơn: $orderId")
            val updates = mutableMapOf<String, Any>(
                "paymentStatus" to Order.PaymentStatus.PAID,
                "isPaid" to true,
                "paidAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "status" to Order.Status.PENDING,
                "orderStatus" to Order.Status.PENDING
            )
            ordersRef.child(orderId).updateChildren(updates).addOnSuccessListener {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "✅ Đã nhận SMS biến động số dư! Đơn hàng #$orderId tự động xác nhận thành công.",
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
                            "✅ Đơn hàng #$orderId đã xác nhận thanh toán thành công!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
