package com.chuyen_de_2.foodorder.internal.ui.shop

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.ItemBestSellerDishBinding

data class BestSellerItem(
    val rank: Int,
    val dishName: String,
    val soldQuantity: Int,
    val price: Long
)

class BestSellerDishAdapter : ListAdapter<BestSellerItem, BestSellerDishAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBestSellerDishBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemBestSellerDishBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BestSellerItem) {
            binding.tvDishRank.text = item.rank.toString()
            binding.tvDishRankName.text = item.dishName
            binding.tvDishRankSales.text = "${item.soldQuantity} phần đã bán"
            binding.tvDishRankPrice.text = item.price.toVndCurrency()

            when (item.rank) {
                1 -> {
                    binding.tvDishRank.setBackgroundColor(Color.parseColor("#FEF3C7"))
                    binding.tvDishRank.setTextColor(Color.parseColor("#B45309"))
                }
                2 -> {
                    binding.tvDishRank.setBackgroundColor(Color.parseColor("#F1F5F9"))
                    binding.tvDishRank.setTextColor(Color.parseColor("#475569"))
                }
                3 -> {
                    binding.tvDishRank.setBackgroundColor(Color.parseColor("#FFF0ED"))
                    binding.tvDishRank.setTextColor(Color.parseColor("#EE4D2D"))
                }
                else -> {
                    binding.tvDishRank.setBackgroundColor(Color.parseColor("#F8FAFC"))
                    binding.tvDishRank.setTextColor(Color.parseColor("#64748B"))
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BestSellerItem>() {
            override fun areItemsTheSame(oldItem: BestSellerItem, newItem: BestSellerItem): Boolean {
                return oldItem.dishName == newItem.dishName
            }

            override fun areContentsTheSame(oldItem: BestSellerItem, newItem: BestSellerItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
