package com.chuyen_de_2.foodorder.customer.ui.menu

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ItemDishReviewBinding

data class DishReviewItem(
    val id: String = "",
    val customerName: String = "",
    val avatarLetter: String = "",
    val avatarColorHex: String = "#EE4D2D",
    val rating: Int = 5,
    val dateText: String = "",
    val specText: String = "",
    val comment: String = "",
    val imageUrl: String = "",
    val likes: Int = 0,
    val isLiked: Boolean = false
)

class DishReviewsAdapter(
    private var items: List<DishReviewItem>,
    private val onLikeClick: ((DishReviewItem) -> Unit)? = null,
    private val onImageClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<DishReviewsAdapter.ViewHolder>() {

    fun updateData(newItems: List<DishReviewItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemDishReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DishReviewItem) {
            val context = binding.root.context

            binding.tvCustomerName.text = item.customerName
            binding.tvAvatarCircle.text = item.avatarLetter
            binding.tvReviewDate.text = item.dateText
            binding.tvReviewComment.text = item.comment

            if (item.specText.isNotBlank()) {
                binding.tvSpecTag.visibility = View.VISIBLE
                binding.tvSpecTag.text = "🏷️ ${item.specText}"
            } else {
                binding.tvSpecTag.visibility = View.GONE
            }

            // Avatar tròn
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(try { Color.parseColor(item.avatarColorHex) } catch (_: Exception) { Color.parseColor("#EE4D2D") })
            }
            binding.tvAvatarCircle.background = bg

            // Số sao
            val starString = buildString {
                for (i in 1..item.rating.coerceIn(1, 5)) append("★")
            }
            binding.tvStars.text = starString

            // Ảnh đính kèm (nếu có)
            if (item.imageUrl.isNotBlank()) {
                binding.cardReviewImage.visibility = View.VISIBLE
                Glide.with(context)
                    .load(item.imageUrl)
                    .transform(CenterCrop(), RoundedCorners(24))
                    .placeholder(R.drawable.placeholder_food)
                    .error(R.drawable.placeholder_food)
                    .into(binding.ivReviewImage)

                binding.cardReviewImage.setOnClickListener {
                    onImageClick?.invoke(item.imageUrl)
                }
            } else {
                binding.cardReviewImage.visibility = View.GONE
            }

            // Nút Like
            binding.tvLikeCount.text = "(${item.likes})"
            if (item.isLiked) {
                binding.tvLikeText.setTextColor(ContextCompat.getColor(context, R.color.primary))
                binding.tvLikeCount.setTextColor(ContextCompat.getColor(context, R.color.primary))
            } else {
                binding.tvLikeText.setTextColor(Color.parseColor("#64748B"))
                binding.tvLikeCount.setTextColor(Color.parseColor("#64748B"))
            }

            binding.btnLikeReview.setOnClickListener {
                onLikeClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDishReviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
