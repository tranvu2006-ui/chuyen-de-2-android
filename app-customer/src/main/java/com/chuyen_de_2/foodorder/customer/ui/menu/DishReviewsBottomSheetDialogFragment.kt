package com.chuyen_de_2.foodorder.customer.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.customer.databinding.DialogDishReviewsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DishReviewsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDishReviewsBinding? = null
    private val binding get() = _binding!!

    private var dishName: String = ""
    private var dishRating: Float = 0.0f
    private var reviewCount: Int = 0

    private lateinit var reviewsAdapter: DishReviewsAdapter

    companion object {
        private const val ARG_DISH_NAME = "dish_name"
        private const val ARG_DISH_RATING = "dish_rating"
        private const val ARG_REVIEW_COUNT = "review_count"

        fun newInstance(dishName: String, dishRating: Float, reviewCount: Int): DishReviewsBottomSheetDialogFragment {
            return DishReviewsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISH_NAME, dishName)
                    putFloat(ARG_DISH_RATING, dishRating)
                    putInt(ARG_REVIEW_COUNT, reviewCount)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dishName = arguments?.getString(ARG_DISH_NAME) ?: "Món ăn"
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
        if (reviewCount > 0 && dishRating > 0f) {
            binding.tvOverviewScoreNum.text = String.format("%.1f", dishRating)
            binding.tvOverviewTotalCount.text = "$reviewCount đánh giá"
        } else {
            binding.tvOverviewScoreNum.text = "--"
            binding.tvOverviewTotalCount.text = "Chưa có đánh giá"
        }

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        val reviewsList = mutableListOf<DishReviewItem>()

        reviewsAdapter = DishReviewsAdapter(reviewsList)
        binding.rvDishReviews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewsAdapter
        }

        binding.rbQuickStar.setOnRatingBarChangeListener { _, rating, _ ->
            val star = rating.toInt()
            binding.tvRatingLabel.text = when (star) {
                5 -> "5★ Tuyệt vời"
                4 -> "4★ Rất ngon"
                3 -> "3★ Tạm ổn"
                2 -> "2★ Chưa hài lòng"
                else -> "1★ Kém"
            }
        }

        binding.btnSubmitReview.setOnClickListener {
            val commentText = binding.etQuickReview.text.toString().trim()
            if (commentText.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập nội dung đánh giá!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRev = DishReviewItem(
                id = System.currentTimeMillis().toString(),
                customerName = "Bạn (Khách hàng)",
                avatarLetter = "B",
                avatarColorHex = "#EE4D2D",
                rating = binding.rbQuickStar.rating.toInt().coerceAtLeast(1),
                dateText = "Vừa xong",
                specText = "Tiêu chuẩn quán",
                comment = commentText
            )
            reviewsList.add(0, newRev)
            reviewsAdapter.updateData(reviewsList)
            binding.etQuickReview.text?.clear()
            Toast.makeText(requireContext(), "Cảm ơn bạn đã gửi đánh giá!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
