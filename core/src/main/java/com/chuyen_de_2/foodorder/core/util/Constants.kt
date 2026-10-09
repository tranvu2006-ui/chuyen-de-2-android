package com.chuyen_de_2.foodorder.core.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Constants và Extension functions dùng chung
 */
object Constants {
    // Firebase Paths & URL
    const val FIREBASE_DATABASE_URL = "https://chuyen-de-2-2f0bc-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val PATH_USERS = "users"
    const val PATH_CARTS = "carts"
    const val PATH_ORDERS = "orders"
    const val PATH_TRACKING = "tracking"

    // Intent Extras
    const val EXTRA_SHOP_ID = "extra_shop_id"
    const val EXTRA_SHOP_NAME = "extra_shop_name"
    const val EXTRA_ORDER_ID = "extra_order_id"

    // Debounce timeout (Section 6 - doc-adr)
    const val DEBOUNCE_TIMEOUT_MS = 1500L

    // GPS update interval (Foreground Service)
    const val GPS_UPDATE_INTERVAL_MS = 5000L
    const val GPS_FASTEST_INTERVAL_MS = 3000L

    // Notification Channels
    const val CHANNEL_ID_ORDER = "channel_order_notification"
    const val CHANNEL_ID_TRACKING = "channel_tracking"
    const val CHANNEL_NAME_ORDER = "Thông báo đơn hàng"
    const val CHANNEL_NAME_TRACKING = "Theo dõi vị trí"

    // Foreground Service
    const val FOREGROUND_SERVICE_ID = 1001
}

/** Format tiền VND: 45000 → "45.000₫" */
fun Long.toVndCurrency(): String {
    val format = NumberFormat.getNumberInstance(Locale("vi", "VN"))
    return "${format.format(this)}₫"
}

/** Format timestamp thành chuỗi ngày giờ */
fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    return sdf.format(Date(this))
}

/** Format timestamp thành chuỗi giờ */
fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
    return sdf.format(Date(this))
}
