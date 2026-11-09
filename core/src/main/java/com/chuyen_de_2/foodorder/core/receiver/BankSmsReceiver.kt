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

            // Tiến hành đối soát nội dung SMS với các đơn hàng trên Firebase
            matchAndConfirmOrderPayment(context, smsContent)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi xử lý SMS: ${e.message}", e)
        }
    }

    private fun matchAndConfirmOrderPayment(context: Context, smsContent: String) {
        val database = FirebaseDatabase.getInstance()
        val ordersRef = database.getReference("orders")

        // Lấy danh sách các đơn hàng gần nhất để đối soát
        ordersRef.limitToLast(30).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (orderSnap in snapshot.children) {
                    val orderId = orderSnap.key.orEmpty()
                    val paymentStatus = orderSnap.child("paymentStatus").getValue(String::class.java).orEmpty()
                    val orderStatus = orderSnap.child("status").getValue(String::class.java).orEmpty()
                    val refundStatus = orderSnap.child("refundStatus").getValue(String::class.java).orEmpty()

                    val shortCode = if (orderId.length >= 6) {
                        orderId.takeLast(6).uppercase()
                    } else {
                        orderId.uppercase()
                    }

                    // Kiểm tra xem nội dung SMS có chứa mã đơn hàng không
                    val isMatched = smsContent.contains(orderId, ignoreCase = true) ||
                            smsContent.contains(shortCode, ignoreCase = true) ||
                            smsContent.contains("DH$shortCode", ignoreCase = true) ||
                            smsContent.contains("ORD_OISHI_$shortCode", ignoreCase = true) ||
                            smsContent.contains("Hoàn tiền $shortCode", ignoreCase = true)

                    if (!isMatched) continue

                    // TRƯỜNG HỢP 1: Hoàn tiền (Đơn bị hủy đang chờ hoàn tiền)
                    if (orderStatus == Order.Status.CANCELLED && refundStatus != Order.RefundStatus.REFUNDED) {
                        Log.d(TAG, "Khớp SMS hoàn tiền cho đơn: $orderId")
                        val refundUpdates = mapOf<String, Any>(
                            "refundStatus" to Order.RefundStatus.REFUNDED,
                            "refundedAt" to System.currentTimeMillis()
                        )
                        ordersRef.child(orderId).updateChildren(refundUpdates).addOnSuccessListener {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context.applicationContext,
                                    "Đã nhận tin nhắn SMS hoàn tiền! Đơn hàng #$shortCode đã được cập nhật ĐÃ HOÀN TIỀN.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        break
                    }

                    // TRƯỜNG HỢP 2: Khách thanh toán tiền vào (Phía Quán nhận tiền)
                    if (!paymentStatus.equals(Order.PaymentStatus.PAID, ignoreCase = true)) {
                        Log.d(TAG, "Khớp SMS thanh toán cho đơn: $orderId")
                        val updates = mapOf<String, Any>(
                            "paymentStatus" to Order.PaymentStatus.PAID,
                            "isPaid" to true,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        ordersRef.child(orderId).updateChildren(updates).addOnSuccessListener {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context.applicationContext,
                                    "Đã nhận tin nhắn biến động số dư! Đơn hàng #$shortCode tự động xác nhận thành công.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase query error: ${error.message}")
            }
        })
    }
}
