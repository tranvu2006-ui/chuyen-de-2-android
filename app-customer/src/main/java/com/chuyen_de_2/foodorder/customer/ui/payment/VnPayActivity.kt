package com.chuyen_de_2.foodorder.customer.ui.payment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.core.util.VnPayHelper
import com.chuyen_de_2.foodorder.core.util.VnPayResult
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.databinding.ActivityVnpayBinding
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderHistoryActivity
import com.chuyen_de_2.foodorder.customer.ui.order.OrderSuccessActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VnPayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVnpayBinding

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var orderId: String = ""
    private var shopName: String = ""
    private var address: String = ""
    private var amount: Long = 0L
    private var subtotal: Long = 0L
    private var shippingFee: Long = 0L
    private var discount: Long = 0L
    private var orderItems: String = ""
    private var handledReturn = false
    private var bankPopupWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVnpayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.initEdgeToEdge(this, true)
        InsetUtils.applyToolbarInsets(binding.toolbar)
        InsetUtils.applyNavigationBarPadding(binding.root)

        readExtras(intent)
        setupToolbar()
        setupWebView()
        binding.btnConfirmPaymentReported.setOnClickListener { reportPaymentToStore() }
        handleIncomingUri(intent.data)
        if (!handledReturn) {
            binding.layoutOrderSummary.visibility = View.VISIBLE
            binding.webView.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    confirmLeave()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingUri(intent.data)
    }

    private fun readExtras(intent: Intent) {
        orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID).orEmpty()
        shopName = intent.getStringExtra(Constants.EXTRA_SHOP_NAME) ?: "Quán Ăn Oishi"
        address = intent.getStringExtra("extra_address").orEmpty()
        amount = intent.getLongExtra("extra_amount", 0L)
        subtotal = intent.getLongExtra("extra_subtotal", 0L)
        shippingFee = intent.getLongExtra("extra_shipping_fee", 0L)
        discount = intent.getLongExtra("extra_discount", 0L)
        orderItems = intent.getStringExtra("extra_order_items").orEmpty()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { confirmLeave() }
        binding.toolbar.subtitle = if (orderId.isNotBlank()) {
            "Đơn #${orderId.takeLast(6).uppercase()}"
        } else {
            "Sandbox"
        }
        binding.tvOrderSummary.text = buildString {
            appendLine("Mã đơn: ORD_OISHI_${orderId.takeLast(6).uppercase()}")
            appendLine()
            appendLine("Món ăn:")
            appendLine(orderItems.ifBlank { "Chi tiết món chưa có sẵn" })
            appendLine()
            appendLine("Tiền món: ${subtotal.toVndCurrency()}")
            appendLine("Phí ship: ${shippingFee.toVndCurrency()}")
            if (discount > 0L) appendLine("Giảm giá: -${discount.toVndCurrency()}")
            appendLine("Tổng thanh toán: ${amount.toVndCurrency()}")
        }
        binding.btnContinuePayment.setOnClickListener { loadPaymentPage() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        binding.webView.addJavascriptInterface(PaymentMethodBridge(), "OishiPaymentMethod")

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val message = resultMsg ?: return false
                bankPopupWebView?.let(::closeBankPopup)

                val popup = WebView(this@VnPayActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = routePopupUrl(request?.url?.toString().orEmpty(), this@apply)

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                            routePopupUrl(url.orEmpty(), this@apply)
                    }
                }
                bankPopupWebView = popup
                binding.root.addView(popup, ViewGroup.LayoutParams(1, 1))

                val transport = message.obj as? WebView.WebViewTransport ?: run {
                    closeBankPopup(popup)
                    return false
                }
                transport.webView = popup
                message.sendToTarget()
                return true
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return handleUrl(request?.url?.toString().orEmpty())
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handleUrl(url.orEmpty())

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.btnConfirmPaymentReported.visibility = View.GONE
                if (VnPayHelper.isReturnUrl(url)) {
                    handleUrl(url.orEmpty())
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (VnPayHelper.isReturnUrl(url)) {
                    binding.btnConfirmPaymentReported.visibility = View.GONE
                    handleUrl(url.orEmpty())
                } else if (url.isVnPayHost()) {
                    installPaymentMethodListener()
                }
            }
        }
    }

    private fun String?.isVnPayHost(): Boolean {
        val host = Uri.parse(this.orEmpty()).host.orEmpty()
        return host == "vnpayment.vn" || host.endsWith(".vnpayment.vn")
    }

    private fun installPaymentMethodListener() {
        val script = """
        (function() {
            function normalizedText(text) {
                return (text || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
                    .replace(/đ/g, 'd').replace(/\s+/g, ' ').trim().toLowerCase();
            }

            function pageState() {
                var text = normalizedText(document.body && document.body.innerText);
                var hasQrOption = /vnpay[\s-]*qr|qr[\s-]*vnpay/.test(text);
                var hasVnPayAppOption = /app.{0,12}vnpay|vnpay.{0,12}app/.test(text);
                var hasMethodSelectionHeading = /chon phuong thuc thanh toan/.test(text);
                var isSelectionPage = hasMethodSelectionHeading || (hasQrOption && hasVnPayAppOption);
                var hasBankWalletPage = /thanh toan qua app ngan hang\s*\/\s*vi dien tu/.test(text);
                var hasVnPayAppPage = /thanh toan qua app vnpay/.test(text);
                return {
                    isSelectionPage: isSelectionPage,
                    isSupportedPage: !isSelectionPage && (hasBankWalletPage || hasVnPayAppPage)
                };
            }

            function reportPageState() {
                var state = pageState();
                OishiPaymentMethod.onPaymentMethodPageState(
                    state.isSelectionPage,
                    state.isSupportedPage
                );
            }

            var reportTimer;
            function schedulePageStateReport() {
                clearTimeout(reportTimer);
                reportTimer = setTimeout(reportPageState, 100);
            }

            if (!window.__oishiPaymentListenerInstalled) {
                window.__oishiPaymentListenerInstalled = true;
                window.addEventListener('pageshow', schedulePageStateReport);
                window.addEventListener('popstate', schedulePageStateReport);
                new MutationObserver(schedulePageStateReport).observe(document.documentElement, {
                    childList: true,
                    characterData: true,
                    subtree: true
                });
            }

            reportPageState();
        })();
    """.trimIndent()

        binding.webView.evaluateJavascript(script, null)
    }

    private inner class PaymentMethodBridge {
        @JavascriptInterface
        fun onPaymentMethodPageState(
            isSelectionPage: Boolean,
            isSupportedPage: Boolean
        ) {
            runOnUiThread {
                binding.btnConfirmPaymentReported.visibility =
                    if (!isSelectionPage && isSupportedPage) View.VISIBLE else View.GONE
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("intent://")) {
            return try {
                startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))
                true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Không tìm thấy ứng dụng thanh toán. Hãy quét QR bằng ứng dụng ngân hàng.", Toast.LENGTH_LONG).show()
                true
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được ứng dụng thanh toán. Hãy quét QR bằng ứng dụng ngân hàng.", Toast.LENGTH_LONG).show()
                true
            }
        }

        val uri = Uri.parse(url)
        if (uri.scheme !in listOf("http", "https")) {
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Chưa cài ứng dụng ngân hàng tương ứng. Hãy chọn ngân hàng khác hoặc dùng thẻ NCB Sandbox.", Toast.LENGTH_LONG).show()
                true
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được ứng dụng ngân hàng. Hãy quét QR bằng ứng dụng ngân hàng.", Toast.LENGTH_LONG).show()
                true
            }
        }
        if (VnPayHelper.isReturnUrl(url)) {
            completePayment(url)
            return true
        }
        return false
    }

    private fun handleIncomingUri(uri: Uri?) {
        val url = uri?.toString() ?: return
        if (VnPayHelper.isReturnUrl(url)) completePayment(url)
    }

    private fun routePopupUrl(url: String, popup: WebView): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        if (!handleUrl(url)) binding.webView.loadUrl(url)
        closeBankPopup(popup)
        return true
    }

    private fun closeBankPopup(popup: WebView) {
        (popup.parent as? ViewGroup)?.removeView(popup)
        popup.stopLoading()
        popup.destroy()
        if (bankPopupWebView === popup) bankPopupWebView = null
    }

    private fun loadPaymentPage() {
        if (orderId.isBlank() || amount <= 0L) {
            Toast.makeText(this, "Thiếu thông tin đơn hàng để thanh toán VNPay", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        handledReturn = false
        binding.layoutOrderSummary.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.tvSandboxCardInfo.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.btnConfirmPaymentReported.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val url = firebaseRepository.createVnPayPayment(orderId)
                try {
                    firebaseRepository.markVnPayPaymentPending(orderId)
                } catch (_: Exception) {
                    Toast.makeText(
                        this@VnPayActivity,
                        "Chưa đồng bộ được trạng thái thanh toán lên Firebase.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                binding.webView.loadUrl(url)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                showFailDialog(e.message ?: "Không thể tạo liên kết thanh toán VNPay")
            }
        }
    }

    private fun completePayment(returnUrl: String) {
        if (handledReturn) return
        handledReturn = true

        lifecycleScope.launch {
            try {
                val result = firebaseRepository.verifyVnPayReturn(returnUrl)
                try {
                    firebaseRepository.recordVnPayPayment(orderId, result)
                } catch (_: Exception) {
                    Toast.makeText(
                        this@VnPayActivity,
                        "Chưa đồng bộ được chi tiết giao dịch lên Firebase.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (result.success) {
                    goToSuccess(result)
                } else {
                    showFailDialog(result.message)
                }
            } catch (e: Exception) {
                handledReturn = false
                showFailDialog(e.message ?: "Không xác minh được kết quả thanh toán VNPay")
            }
        }
    }

    private fun reportPaymentToStore() {
        if (orderId.isBlank() || amount <= 0L) {
            Toast.makeText(this, "Thiếu thông tin đơn hàng để xác nhận thanh toán.", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnConfirmPaymentReported.isEnabled = false
        lifecycleScope.launch {
            try {
                firebaseRepository.markVnPayPaymentReportedByCustomer(orderId, amount)
                startActivity(Intent(this@VnPayActivity, OrderSuccessActivity::class.java).apply {
                    putExtra(Constants.EXTRA_ORDER_ID, orderId)
                    putExtra(Constants.EXTRA_SHOP_NAME, shopName)
                    putExtra("extra_address", address)
                    putExtra("extra_amount", amount)
                    putExtra("extra_subtotal", subtotal)
                    putExtra("extra_shipping_fee", shippingFee)
                    putExtra("extra_discount", discount)
                    putExtra("extra_payment_method", "VNPay — đã báo chuyển khoản, chờ quán xác nhận")
                })
                finish()
            } catch (e: Exception) {
                binding.btnConfirmPaymentReported.isEnabled = true
                Toast.makeText(
                    this@VnPayActivity,
                    e.message ?: "Không gửi được xác nhận thanh toán lên Firebase.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun goToSuccess(result: VnPayResult) {
        val bankLabel = if (result.bankCode.isNotBlank()) " (${result.bankCode})" else ""
        startActivity(Intent(this, OrderSuccessActivity::class.java).apply {
            putExtra(Constants.EXTRA_ORDER_ID, orderId)
            putExtra(Constants.EXTRA_SHOP_NAME, shopName)
            putExtra("extra_address", address)
            putExtra("extra_amount", amount)
            putExtra("extra_subtotal", subtotal)
            putExtra("extra_shipping_fee", shippingFee)
            putExtra("extra_discount", discount)
            putExtra("extra_payment_method", "VNPay$bankLabel • GD ${result.transactionNo.ifBlank { result.txnRef }}")
        })
        finish()
    }

    private fun showFailDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Thanh toán VNPay chưa hoàn tất")
            .setMessage(message)
            .setPositiveButton("Thử lại") { _, _ ->
                handledReturn = false
                loadPaymentPage()
            }
            .setNegativeButton("Để sau") { _, _ ->
                startActivity(Intent(this, OrderHistoryActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("Hủy thanh toán VNPay?")
            .setMessage("Đơn đã được tạo. Bạn có thể thanh toán lại trong Lịch sử đơn (tab Chờ thanh toán).")
            .setPositiveButton("Ở lại") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Thoát") { _, _ ->
                startActivity(Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        bankPopupWebView?.let(::closeBankPopup)
        super.onDestroy()
    }

    companion object {
        fun createIntent(
            context: Context,
            orderId: String,
            shopName: String,
            address: String,
            amount: Long,
            subtotal: Long,
            shippingFee: Long,
            discount: Long,
            orderItems: String = ""
        ): Intent {
            return Intent(context, VnPayActivity::class.java).apply {
                putExtra(Constants.EXTRA_ORDER_ID, orderId)
                putExtra(Constants.EXTRA_SHOP_NAME, shopName)
                putExtra("extra_address", address)
                putExtra("extra_amount", amount)
                putExtra("extra_subtotal", subtotal)
                putExtra("extra_shipping_fee", shippingFee)
                putExtra("extra_discount", discount)
                putExtra("extra_order_items", orderItems)
            }
        }
    }
}
