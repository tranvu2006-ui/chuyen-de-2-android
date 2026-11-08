package com.chuyen_de_2.foodorder.internal.ui.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.FragmentStoreReportBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Tên chức năng: Báo Cáo Doanh Số & Hiệu Quả Bán Hàng Của Quán (Store Performance & Revenue Analytics)
 * Cách hoạt động:
 *  - Phân tích và tổng hợp toàn bộ đơn hàng của Cửa hàng theo các mốc thời gian: Hôm nay, Tuần này, Tháng này.
 *  - Tính toán tổng doanh thu thực tế, số đơn hoàn tất, số đơn hủy/bùng, giá trị trung bình trên mỗi đơn (AOV).
 *  - Bóc tách danh sách món ăn từ các đơn hoàn tất để xếp hạng Top món bán chạy nhất (Best Sellers) kèm số lượng và đơn giá.
 *  - Hiển thị điểm đánh giá sao uy tín ⭐ trung bình của quán từ phản hồi khách hàng.
 * Luồng hướng tới:
 *  - Cung cấp bức tranh tài chính kinh doanh tức thời cho chủ quán ra quyết định nhập nguyên liệu và điều chỉnh thực đơn.
 */
@AndroidEntryPoint
class StoreReportFragment : Fragment() {

    private var _binding: FragmentStoreReportBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var authRepository: AuthRepository

    private val bestSellerAdapter = BestSellerDishAdapter()
    private var allStoreOrders: List<Order> = emptyList()
    private var currentPeriod = "TODAY"
    private var currentStoreId = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Tên chức năng: Khởi tạo View báo cáo thống kê
     * Cách hoạt động: Thiết lập RecyclerView danh sách món bán chạy, cài đặt bộ lọc thời gian và bắt đầu nạp dữ liệu.
     * Luồng hướng tới: Chuẩn bị hiển thị các chỉ số KPI.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTimeFilterTabs()
        loadStoreAndObserveData()
    }

    /**
     * Tên chức năng: Cấu hình RecyclerView cho Top Món Bán Chạy
     * Cách hoạt động: Gắn [BestSellerDishAdapter] với LayoutManager tuyến tính.
     * Luồng hướng tới: Hiển thị bảng xếp hạng món ăn chạy nhất.
     */
    private fun setupRecyclerView() {
        binding.rvBestSellerDishes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bestSellerAdapter
        }
    }

    /**
     * Tên chức năng: Bộ lọc thời gian thống kê (Hôm nay / Tuần này / Tháng này)
     * Cách hoạt động: Lắng nghe sự kiện chọn trên MaterialButtonToggleGroup, cập nhật biến `currentPeriod` và tính toán lại các chỉ số.
     * Luồng hướng tới: Gọi [computeAndDisplayMetrics].
     */
    private fun setupTimeFilterTabs() {
        binding.toggleGroupTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentPeriod = when (checkedId) {
                    binding.btnFilterToday.id -> "TODAY"
                    binding.btnFilterWeek.id -> "WEEK"
                    binding.btnFilterMonth.id -> "MONTH"
                    else -> "TODAY"
                }
                computeAndDisplayMetrics()
            }
        }
    }

    /**
     * Tên chức năng: Tải định danh Quán và thu thập dữ liệu Firestore theo thời gian thực
     * Cách hoạt động: Lấy UID người dùng, resolve storeId, đồng thời lắng nghe thông tin Store (điểm rating) và toàn bộ Orders của quán.
     * Luồng hướng tới: Đồng bộ dữ liệu mới nhất từ Firebase.
     */
    private fun loadStoreAndObserveData() {
        val uid = authRepository.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)

            // Lắng nghe thông tin quán để hiển thị đánh giá sao ⭐
            if (currentStoreId.isNotBlank()) {
                launch {
                    firebaseRepository.observeStore(currentStoreId).collectLatest { store ->
                        if (store != null) {
                            val rating = store.ratingAvg
                            binding.tvRatingScore.text = if (rating > 0) String.format("%.1f", rating) else "—"
                        }
                    }
                }
            }

            // Lắng nghe toàn bộ đơn hàng của quán
            launch {
                firebaseRepository.observeAllOrders().collectLatest { orders ->
                    allStoreOrders = if (currentStoreId.isNotBlank()) {
                        orders.filter { it.shopId == currentStoreId }
                    } else {
                        orders
                    }
                    computeAndDisplayMetrics()
                }
            }
        }
    }

    /**
     * Tên chức năng: Tính toán số liệu thống kê & Top món bán chạy
     * Cách hoạt động:
     *  - Lọc đơn hàng theo mốc thời gian bắt đầu của ngày/tuần/tháng hiện tại.
     *  - Tính tổng doanh thu từ các đơn hoàn tất (COMPLETED).
     *  - Đếm số đơn thành công và số đơn thất bại/hủy/bùng (CANCELLED, BOOM).
     *  - Gom nhóm các món ăn theo tên và tính tổng số lượng đã bán, sau đó sắp xếp giảm dần.
     * Luồng hướng tới: Cập nhật các trường TextView KPI và danh sách [BestSellerDishAdapter].
     */
    private fun computeAndDisplayMetrics() {
        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filteredOrders = when (currentPeriod) {
            "TODAY" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfToday || t == 0L
            }
            "WEEK" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfWeek || t == 0L
            }
            "MONTH" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfMonth || t == 0L
            }
            else -> allStoreOrders
        }

        val completedOrders = filteredOrders.filter { it.status == Order.Status.COMPLETED }
        val cancelledOrders = filteredOrders.filter {
            it.status == Order.Status.CANCELLED || it.status == Order.Status.BOOM
        }

        // Tính doanh thu
        val totalRevenue = completedOrders.sumOf {
            if (it.totalPayment > 0) it.totalPayment else if (it.total > 0) it.total else 0L
        }
        val avgOrderValue = if (completedOrders.isNotEmpty()) totalRevenue / completedOrders.size else 0L

        binding.tvRevenueAmount.text = totalRevenue.toVndCurrency()
        binding.tvCompletedOrdersCount.text = "${completedOrders.size} đơn"
        binding.tvCancelledOrdersCount.text = "${cancelledOrders.size} đơn"
        binding.tvAverageOrderValue.text = avgOrderValue.toVndCurrency()

        // Tính top món bán chạy nhất
        val dishSalesMap = mutableMapOf<String, Pair<Int, Long>>()
        completedOrders.forEach { order ->
            order.items.values.forEach { item ->
                val existing = dishSalesMap[item.dishName]
                val currentQty = existing?.first ?: 0
                val price = item.price
                dishSalesMap[item.dishName] = Pair(currentQty + item.quantity, price)
            }
        }

        if (dishSalesMap.isNotEmpty()) {
            val sortedList = dishSalesMap.entries
                .sortedByDescending { it.value.first }
                .mapIndexed { index, entry ->
                    BestSellerItem(
                        rank = index + 1,
                        dishName = entry.key,
                        soldQuantity = entry.value.first,
                        price = entry.value.second
                    )
                }

            binding.tvEmptyBestSellers.visibility = View.GONE
            binding.rvBestSellerDishes.visibility = View.VISIBLE
            bestSellerAdapter.submitList(sortedList)
        } else {
            binding.tvEmptyBestSellers.visibility = View.VISIBLE
            binding.rvBestSellerDishes.visibility = View.GONE
            bestSellerAdapter.submitList(emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
