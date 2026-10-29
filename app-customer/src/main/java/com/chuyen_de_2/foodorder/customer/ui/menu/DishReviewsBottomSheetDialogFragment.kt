package com.chuyen_de_2.foodorder.customer.ui.menu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.DishReview
import com.chuyen_de_2.foodorder.customer.databinding.DialogDishReviewsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DishReviewsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDishReviewsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var dishId: String = ""
    private var dishName: String = ""
    private var storeId: String = ""
    private var dishRating: Float = 0.0f
    private var reviewCount: Int = 0

    private var pendingImageBase64: String? = null
    private var currentFilter: String = "ALL"
    private var allReviewsList: List<DishReview> = emptyList()
    private val likedReviewIds = mutableSetOf<String>()

    private lateinit var reviewsAdapter: DishReviewsAdapter

    // Launcher chọn ảnh từ thư viện
    private val pickReviewImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processAndPreviewImage(uri)
        }
    }

    companion object {
        private const val ARG_DISH_ID = "dish_id"
        private const val ARG_DISH_NAME = "dish_name"
        private const val ARG_STORE_ID = "store_id"
        private const val ARG_DISH_RATING = "dish_rating"
        private const val ARG_REVIEW_COUNT = "review_count"

        fun newInstance(
            dishId: String,
            dishName: String,
            dishRating: Float,
            reviewCount: Int,
            storeId: String = ""
        ): DishReviewsBottomSheetDialogFragment {
            return DishReviewsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISH_ID, dishId)
                    putString(ARG_DISH_NAME, dishName)
                    putFloat(ARG_DISH_RATING, dishRating)
                    putInt(ARG_REVIEW_COUNT, reviewCount)
                    putString(ARG_STORE_ID, storeId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dishId = arguments?.getString(ARG_DISH_ID) ?: ""
        dishName = arguments?.getString(ARG_DISH_NAME) ?: "Món ăn"
        storeId = arguments?.getString(ARG_STORE_ID) ?: ""
        dishRating = arguments?.getFloat(ARG_DISH_RATING) ?: 0.0f
        reviewCount = arguments?.getInt(ARG_REVIEW_COUNT) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDishReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSheetDishSubtitle.text = dishName

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        setupAdapter()
        setupStarPicker()
        setupImagePicker()
        setupFilterChips()
        setupSubmitButton()

        observeReviewsFromFirebase()
    }

    private fun setupAdapter() {
        reviewsAdapter = DishReviewsAdapter(
            items = emptyList(),
            onLikeClick = { item ->
                handleLikeReview(item)
            },
            onImageClick = { imageUrl ->
                // Hiển thị toast hoặc xem ảnh chi tiết
                Toast.makeText(requireContext(), "📸 Ảnh đánh giá từ khách hàng", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDishReviews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewsAdapter
        }
    }

    private fun setupStarPicker() {
        binding.rbQuickStar.setOnRatingBarChangeListener { _, rating, _ ->
            val star = rating.toInt().coerceIn(1, 5)
            val labels = mapOf(
                5 to "5★ Tuyệt vời",
                4 to "4★ Rất ngon",
                3 to "3★ Tạm ổn",
                2 to "2★ Chưa hài lòng",
                1 to "1★ Kém"
            )
            binding.tvRatingLabel.text = labels[star] ?: "${star}★"
        }
    }

    private fun setupImagePicker() {
        // Nút mở thư viện ảnh
        binding.btnPickReviewImage.setOnClickListener {
            pickReviewImageLauncher.launch("image/*")
        }

        // Nút xóa ảnh đã chọn
        binding.btnRemoveImage.setOnClickListener {
            pendingImageBase64 = null
            binding.layoutImagePreview.visibility = View.GONE
        }
    }

    private fun processAndPreviewImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                // Resize ảnh về tối đa 480px để tối ưu kích thước lưu Firebase
                val maxDim = 480
                val width = originalBitmap.width
                val height = originalBitmap.height
                val ratio = width.toFloat() / height.toFloat()
                val targetWidth = if (width > height) maxDim else (maxDim * ratio).toInt()
                val targetHeight = if (height >= width) maxDim else (maxDim / ratio).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    maxOf(1, targetWidth),
                    maxOf(1, targetHeight),
                    true
                )

                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val byteArray = outputStream.toByteArray()

                val base64String = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
                pendingImageBase64 = base64String

                binding.layoutImagePreview.visibility = View.VISIBLE
                Glide.with(this)
                    .load(scaledBitmap)
                    .into(binding.ivReviewPreview)

                Toast.makeText(requireContext(), "📷 Đã đính kèm ảnh món ăn!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể tải ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFilterChips() {
        binding.chipFilterAll.setOnClickListener {
            currentFilter = "ALL"
            filterAndDisplayReviews()
        }
        binding.chipFilter5Star.setOnClickListener {
            currentFilter = "5"
            filterAndDisplayReviews()
        }
        binding.chipFilter4Star.setOnClickListener {
            currentFilter = "4"
            filterAndDisplayReviews()
        }
        binding.chipFilterWithImage.setOnClickListener {
            currentFilter = "HAS_IMG"
            filterAndDisplayReviews()
        }
    }

    private fun setupSubmitButton() {
        binding.btnSubmitReview.setOnClickListener {
            val commentText = binding.etQuickReview.text.toString().trim()
            if (commentText.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập nội dung đánh giá!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = firebaseAuth.currentUser
            val customerId = user?.uid ?: "guest_${System.currentTimeMillis()}"
            val customerName = user?.displayName?.ifBlank {
                user.email?.substringBefore("@") ?: "Bạn (Khách hàng)"
            } ?: "Bạn (Khách hàng)"

            val ratingValue = binding.rbQuickStar.rating.toInt().coerceIn(1, 5)

            binding.btnSubmitReview.isEnabled = false
            binding.btnSubmitReview.text = "Đang gửi..."

            val colors = listOf("#EE4D2D", "#2563EB", "#10B981", "#7C3AED", "#EA580C")
            val chosenColor = colors.random()

            val newReview = DishReview(
                dishId = dishId,
                dishName = dishName,
                storeId = storeId,
                customerId = customerId,
                customerName = customerName,
                rating = ratingValue,
                comment = commentText,
                imageUrl = pendingImageBase64 ?: "",
                specText = "Tiêu chuẩn quán",
                avatarColorHex = chosenColor,
                createdAt = System.currentTimeMillis()
            )

            lifecycleScope.launch {
                val success = firebaseRepository.submitDishReview(newReview)
                binding.btnSubmitReview.isEnabled = true
                binding.btnSubmitReview.text = "Gửi"

                if (success) {
                    binding.etQuickReview.text?.clear()
                    pendingImageBase64 = null
                    binding.layoutImagePreview.visibility = View.GONE
                    Toast.makeText(requireContext(), "🎉 Cảm ơn bạn đã gửi đánh giá!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Gửi đánh giá thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeReviewsFromFirebase() {
        if (dishId.isBlank()) return

        lifecycleScope.launch {
            firebaseRepository.observeDishReviews(dishId).collectLatest { reviews ->
                allReviewsList = reviews
                updateOverviewCard(reviews)
                filterAndDisplayReviews()
            }
        }
    }

    private fun updateOverviewCard(reviews: List<DishReview>) {
        val total = reviews.size
        if (total > 0) {
            val avg = reviews.map { it.rating }.average().toFloat()
            binding.tvOverviewScoreNum.text = String.format(Locale.US, "%.1f", avg)
            binding.tvOverviewTotalCount.text = "$total đánh giá"

            val count5 = reviews.count { it.rating == 5 }
            val count4 = reviews.count { it.rating == 4 }
            val count3 = reviews.count { it.rating == 3 }
            val countWithImg = reviews.count { it.imageUrl.isNotBlank() }

            binding.pbStar5.progress = (count5 * 100 / total)
            binding.tvStarPct5.text = "${(count5 * 100 / total)}%"

            binding.pbStar4.progress = (count4 * 100 / total)
            binding.tvStarPct4.text = "${(count4 * 100 / total)}%"

            binding.pbStar3.progress = (count3 * 100 / total)
            binding.tvStarPct3.text = "${(count3 * 100 / total)}%"

            binding.chipFilterAll.text = "Tất cả ($total)"
            binding.chipFilter5Star.text = "5★ ($count5)"
            binding.chipFilter4Star.text = "4★ ($count4)"
            binding.chipFilterWithImage.text = "📷 Có ảnh ($countWithImg)"
        } else {
            val displayScore = if (dishRating > 0f) String.format(Locale.US, "%.1f", dishRating) else "5.0"
            binding.tvOverviewScoreNum.text = displayScore
            binding.tvOverviewTotalCount.text = if (reviewCount > 0) "$reviewCount đánh giá" else "Chưa có đánh giá"
            binding.chipFilterAll.text = "Tất cả (0)"
            binding.chipFilter5Star.text = "5★ (0)"
            binding.chipFilter4Star.text = "4★ (0)"
            binding.chipFilterWithImage.text = "📷 Có ảnh (0)"
        }
    }

    private fun filterAndDisplayReviews() {
        val filtered = when (currentFilter) {
            "5" -> allReviewsList.filter { it.rating == 5 }
            "4" -> allReviewsList.filter { it.rating == 4 }
            "HAS_IMG" -> allReviewsList.filter { it.imageUrl.isNotBlank() }
            else -> allReviewsList
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val items = filtered.map { rev ->
            val firstLetter = rev.customerName.trim().take(1).uppercase().ifBlank { "K" }
            DishReviewItem(
                id = rev.reviewId,
                customerName = rev.customerName,
                avatarLetter = firstLetter,
                avatarColorHex = rev.avatarColorHex,
                rating = rev.rating,
                dateText = sdf.format(Date(rev.createdAt)),
                specText = rev.specText,
                comment = rev.comment,
                imageUrl = rev.imageUrl,
                likes = rev.likes,
                isLiked = likedReviewIds.contains(rev.reviewId)
            )
        }

        reviewsAdapter.updateData(items)

        if (items.isEmpty()) {
            binding.rvDishReviews.visibility = View.GONE
            binding.layoutEmptyReviews.visibility = View.VISIBLE
        } else {
            binding.rvDishReviews.visibility = View.VISIBLE
            binding.layoutEmptyReviews.visibility = View.GONE
        }
    }

    private fun handleLikeReview(item: DishReviewItem) {
        val isLiked = likedReviewIds.contains(item.id)
        if (isLiked) {
            likedReviewIds.remove(item.id)
        } else {
            likedReviewIds.add(item.id)
        }

        lifecycleScope.launch {
            firebaseRepository.toggleReviewLike(item.id, item.likes, !isLiked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
