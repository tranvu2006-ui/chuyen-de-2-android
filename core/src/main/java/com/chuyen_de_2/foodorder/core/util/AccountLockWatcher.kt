package com.chuyen_de_2.foodorder.core.util

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.lang.ref.WeakReference

/**
 * AccountLockWatcher: Giám sát trạng thái Khóa / Mở khóa tài khoản thời gian thực qua WebSocket Firebase.
 *
 * Khi Quản trị viên (Admin) nhấn Khóa tài khoản:
 * - WebSocket Firebase lập tức đẩy sự kiện về máy người dùng trong <500ms.
 * - Hiển thị ngay hộp thoại cảnh báo khóa không thể tắt (non-cancelable) trên màn hình đang hiển thị.
 * - Khi người dùng bấm "Đăng xuất & Thoát", tự động đăng xuất và điều hướng về LoginActivity.
 * - Nếu Admin mở khóa lại ngay trong lúc hộp thoại đang hiện, hộp thoại sẽ tự động đóng và thông báo mở khóa.
 */
object AccountLockWatcher {
    private const val TAG = "AccountLockWatcher"

    private var application: Application? = null
    private var loginActivityClass: Class<out Activity>? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    private var activeUserRef: DatabaseReference? = null
    private var activeUserListener: ValueEventListener? = null
    private var observedUid: String? = null

    private var currentDialog: AlertDialog? = null
    private var isAccountLocked = false
    private var lockReason: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(app: Application, loginClass: Class<out Activity>) {
        this.application = app
        this.loginActivityClass = loginClass

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
                if (isAccountLocked && activity::class.java != loginActivityClass) {
                    mainHandler.post { showLockDialog(activity, lockReason) }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                // Giữ lại currentActivityRef khi pause (chia đôi màn hình, chuyển ứng dụng phụ, popup)
                // để khi Admin nhấn khóa, dialog có thể hiển thị ngay lập tức trên Activity đang hiện hữu.
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                // Không xóa ngay, giữ tham chiếu phòng trường hợp activity vẫn hiển thị dạng nổi/split
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        })

        // Lắng nghe trạng thái đăng nhập Firebase Auth
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                startObserving(user.uid)
            } else {
                stopObserving()
                isAccountLocked = false
                lockReason = ""
                dismissLockDialog()
            }
        }
    }

    @Synchronized
    private fun startObserving(uid: String) {
        if (observedUid == uid && activeUserListener != null) return

        stopObserving()
        observedUid = uid

        val db = try {
            FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
        } catch (_: Exception) {
            FirebaseDatabase.getInstance()
        }

        val ref = db.getReference("users").child(uid)
        try {
            ref.keepSynced(true)
        } catch (_: Exception) {}
        activeUserRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val status = snapshot.child("status").getValue(String::class.java)?.uppercase() ?: "ACTIVE"
                val isActive = snapshot.child("isActive").getValue(Boolean::class.java)
                    ?: snapshot.child("active").getValue(Boolean::class.java)
                    ?: true

                val locked = status == "LOCKED" || status == "SUSPENDED" || status == "INACTIVE" || !isActive
                val reason = snapshot.child("lockReason").getValue(String::class.java)
                    ?: snapshot.child("suspendedReason").getValue(String::class.java)
                    ?: "Vi phạm quy định hoặc chính sách hoạt động của hệ thống."

                mainHandler.post {
                    handleAccountStatusChange(locked, reason)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "AccountLockWatcher listener cancelled: ${error.message}")
            }
        }

        activeUserListener = listener
        ref.addValueEventListener(listener)
        Log.d(TAG, "Bắt đầu theo dõi khóa tài khoản thời gian thực cho UID: $uid")
    }

    @Synchronized
    private fun stopObserving() {
        try {
            if (activeUserRef != null && activeUserListener != null) {
                activeUserRef?.removeEventListener(activeUserListener!!)
            }
        } catch (_: Exception) {}
        activeUserRef = null
        activeUserListener = null
        observedUid = null
    }

    fun notifyLockedManually(activity: Activity, reason: String) {
        isAccountLocked = true
        lockReason = reason
        mainHandler.post {
            showLockDialog(activity, reason)
        }
    }

    fun isLocked(): Boolean = isAccountLocked

    private fun handleAccountStatusChange(locked: Boolean, reason: String) {
        val wasLocked = isAccountLocked
        isAccountLocked = locked
        lockReason = reason

        if (locked) {
            val act = currentActivityRef?.get()
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                if (act::class.java != loginActivityClass) {
                    showLockDialog(act, reason)
                }
            }
        } else {
            dismissLockDialog()
            if (wasLocked) {
                currentActivityRef?.get()?.let { act ->
                    if (!act.isFinishing && !act.isDestroyed) {
                        Toast.makeText(act, "Tài khoản của bạn đã được Quản trị viên mở khóa!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showLockDialog(activity: Activity, reason: String) {
        if (currentDialog != null && currentDialog!!.isShowing) return

        try {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("🚫 TÀI KHOẢN ĐÃ BỊ KHÓA")
                .setMessage("Tài khoản của bạn đã bị Quản trị viên khóa ngay lập tức.\n\nLý do: $reason\n\nVui lòng liên hệ Admin qua email admin@gmail.com để được giải quyết.")
                .setCancelable(false)
                .setPositiveButton("Đăng xuất & Thoát") { d, _ ->
                    d.dismiss()
                    currentDialog = null
                    performLogout(activity)
                }
                .create()

            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
            currentDialog = dialog
        } catch (e: Exception) {
            Log.e(TAG, "Không thể hiển thị thông báo khóa", e)
        }
    }

    private fun dismissLockDialog() {
        try {
            if (currentDialog != null && currentDialog!!.isShowing) {
                currentDialog?.dismiss()
                currentDialog = null
                currentActivityRef?.get()?.let { act ->
                    Toast.makeText(act, "Tài khoản của bạn đã được Quản trị viên mở khóa!", Toast.LENGTH_LONG).show()
                }
            }
        } catch (_: Exception) {}
    }

    private fun performLogout(context: Context) {
        stopObserving()
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}

        loginActivityClass?.let { targetClass ->
            val intent = Intent(context, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
        if (context is Activity && !context.isFinishing) {
            context.finish()
        }
    }
}
