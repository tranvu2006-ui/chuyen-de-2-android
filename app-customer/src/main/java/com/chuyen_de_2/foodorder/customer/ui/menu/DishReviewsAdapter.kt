package com.chuyen_de_2.foodorder.customer.ui.menu

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.customer.databinding.ItemDishReviewBinding

data class DishReviewItem(
    val id: String = "",
    val customerName: String = "",
    val avatarLetter: String = "",
    val avatarColorHex: String = "#EE4D2D",
    val rating: Int = 5,
    val dateText: String = "",
    val specText: String = "",
    val comment: String = ""
)

class DishReviewsAdapter(
    private var items: List<DishReviewItem>
) : RecyclerView.Adapter<DishReviewsAdapter.ViewHolder>() {

    fun updateData(newItems: List<DishReviewItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemDishReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DishReviewItem) {
            binding.tvCustomerName.text = item.customerName
            binding.tvAvatarCircle.text = item.avatarLetter
            binding.tvReviewDate.text = item.dateText
            binding.tvSpecTag.text = item.specText
            binding.tvReviewComment.text = item.comment

            // Vẽ avatar nền tròn màu ngẫu nhiên
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(try { Color.parseColor(item.avatarColorHex) } catch (_: Exception) { Color.parseColor("#EE4D2D") })
            }
            binding.tvAvatarCircle.background = bg

            // Số sao
            val starString = buildString {
                for (i in 1..item.rating) append("★")
            }
            binding.tvStars.text = starString
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
