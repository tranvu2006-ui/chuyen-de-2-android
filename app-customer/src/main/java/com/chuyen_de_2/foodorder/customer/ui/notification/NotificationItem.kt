package com.chuyen_de_2.foodorder.customer.ui.notification

enum class NotificationType {
    ORDER,
    PROMO,
    SYSTEM
}

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val isUnread: Boolean = true,
    val orderId: String? = null,
    val iconRes: Int = com.chuyen_de_2.foodorder.customer.R.drawable.ic_notification,
    val iconColor: Int = 0xFFEE4D2D.toInt(),
    val iconBgColor: Int = 0xFFFFF1EE.toInt()
)
