package com.chuyen_de_2.foodorder.core.util

/**
 * Hằng số dùng chung toàn hệ thống Oishi Food.
 * Tập trung tên node Firebase, khóa SharedPreferences/Intent, giá trị mặc định và quy tắc nghiệp vụ
 * để tránh rải rác hardcode trên nhiều lớp.
 */
object AppConstants {

    object FirebaseNodes {
        const val USERS = "users"
        const val STORES = "stores"
        const val ORDERS = "orders"
        const val DRIVERS = "drivers"
        const val MERCHANTS = "merchants"
        const val CUSTOMERS = "customers"
        const val CARTS = "carts"
        const val BOSS_STORES = "boss_stores"
        const val TRACKING = "tracking"
        const val COMPLAINTS = "complaints"
        const val CATEGORIES = "categories"
        const val REVIEWS = "reviews"
        const val NOTIFICATIONS = "notifications"
    }

    object Prefs {
        const val BOSS_SESSION = "boss_session"
        const val KEY_SELECTED_BRANCH_ID = "selected_branch_store_id"
        const val KEY_SELECTED_BRANCH_NAME = "selected_branch_store_name"
    }

    object Extras {
        const val STORE_ID = "STORE_ID"
        const val ORDER_ID = "ORDER_ID"
        const val LOGOUT = "LOGOUT"
    }

    object Defaults {
        const val ADMIN_EMAIL = "admin@gmail.com"
        const val CORRUPTED_MOCK_EMAIL = "mai.tran@oishifood.vn"
        const val DEFAULT_BANK_NAME = "MB Bank"
        const val DEFAULT_BANK_BIN = "970422"
        const val DEFAULT_OPEN_HOURS = "08:00 - 22:30"
        const val DEFAULT_PREP_TIME = "15 phút"
        const val DEFAULT_DELIVERY_TIME = "20-30 phút"
        const val DEFAULT_STORE_CATEGORY = "Món chính"
        const val DEFAULT_STORE_IMAGE_URL = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400"
        const val DEFAULT_STORE_NAME = "Quán Ăn Của Tôi"
        const val DEFAULT_DRIVER_NAME = "Tài xế Oishi"
    }

    object ShippingRules {
        const val BASE_DISTANCE_KM = 3.0f
        const val BASE_SHIPPING_FEE = 15_000L
        const val EXTRA_FEE_PER_KM = 5_000L
    }

    object TrustScoreRules {
        const val DEFAULT_SCORE = 100
        const val CANCEL_DELIVERING_COD_PENALTY = 30
    }
}
