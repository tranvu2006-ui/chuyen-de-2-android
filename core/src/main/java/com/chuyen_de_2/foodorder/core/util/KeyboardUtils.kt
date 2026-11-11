package com.chuyen_de_2.foodorder.core.util

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * Tiện ích hỗ trợ quản lý bàn phím ảo (Soft Keyboard / IME).
 * Cung cấp chức năng ẩn bàn phím khi người dùng chạm ra ngoài ô nhập liệu (EditText).
 */
object KeyboardUtils {

    /**
     * Ẩn bàn phím ảo ngay lập tức từ Activity
     */
    fun hideKeyboard(activity: Activity) {
        val view = activity.currentFocus ?: activity.window.decorView
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Ẩn bàn phím ảo từ View cụ thể
     */
    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Hiển thị bàn phím ảo cho ô nhập
     */
    fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Xử lý ẩn bàn phím khi chạm ra ngoài ô nhập liệu.
     * Gọi hàm này bên trong override fun dispatchTouchEvent(ev: MotionEvent) của Activity:
     *
     * ```kotlin
     * override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
     *     KeyboardUtils.handleTouchOutsideToHideKeyboard(this, ev)
     *     return super.dispatchTouchEvent(ev)
     * }
     * ```
     */
    fun handleTouchOutsideToHideKeyboard(activity: Activity, ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focusedView = activity.currentFocus
            if (focusedView is EditText) {
                val outRect = Rect()
                focusedView.getGlobalVisibleRect(outRect)
                val x = ev.rawX.toInt()
                val y = ev.rawY.toInt()

                // Nếu chạm bên ngoài vùng EditText đang focus
                if (!outRect.contains(x, y)) {
                    val decorView = activity.window.decorView
                    val touchedView = findViewAtCoordinates(decorView, x, y)

                    // Nếu chạm vào một EditText khác hoặc icon thuộc cùng TextInputLayout (như icon con mắt ẩn/hiện mật khẩu)
                    // thì không ẩn bàn phím để tránh nhấp nháy hoặc ngắt quãng nhập liệu
                    if (touchedView !is EditText && !isPartOfSameInputContainer(focusedView, touchedView)) {
                        focusedView.clearFocus()
                        hideKeyboard(activity)
                    }
                }
            }
        }
    }

    /**
     * Kiểm tra xem view được chạm có thuộc cùng một TextInputLayout với EditText đang focus không
     * (Ví dụ: nút bật/tắt hiển thị mật khẩu password_toggle, nút xóa chữ clear_text)
     */
    private fun isPartOfSameInputContainer(focusedView: View, touchedView: View?): Boolean {
        if (touchedView == null) return false
        var p = focusedView.parent
        while (p is View) {
            if (p.javaClass.name.contains("TextInputLayout")) {
                var tp: Any? = touchedView
                while (tp is View) {
                    if (tp == p) return true
                    tp = tp.parent
                }
                break
            }
            p = p.parent
        }
        return false
    }

    /**
     * Tìm View sâu nhất tại toạ độ màn hình (x, y)
     */
    private fun findViewAtCoordinates(parent: View, x: Int, y: Int): View? {
        if (parent is ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility == View.VISIBLE) {
                    val rect = Rect()
                    child.getGlobalVisibleRect(rect)
                    if (rect.contains(x, y)) {
                        val nested = findViewAtCoordinates(child, x, y)
                        return nested ?: child
                    }
                }
            }
        }
        return parent
    }
}
