package com.example.trafficmonitor

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.trafficmonitor.databinding.ItemAppUsageBinding
import kotlin.math.roundToInt

/**
 * Список приложений: стеклянные карточки, градиентный прогресс
 * и мягкое появление элементов (staggered animation).
 */
class AppUsageAdapter : RecyclerView.Adapter<AppUsageAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppUsage>()
    private var totalBytes: Long = 0L

    /** Нажатие на карточку — показать детали приложения. */
    var onAppClick: ((AppUsage) -> Unit)? = null

    fun setItems(newItems: List<AppUsage>, total: Long = 0L) {
        items.clear()
        items.addAll(newItems)
        totalBytes = maxOf(total, newItems.sumOf { it.bytes })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position], position, totalBytes, onAppClick)
    }

    override fun getItemCount(): Int = items.size

    class AppViewHolder(
        private val binding: ItemAppUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: AppUsage,
            position: Int,
            total: Long,
            onAppClick: ((AppUsage) -> Unit)?
        ) {
            binding.tvAppName.text = item.label
            binding.tvPackageName.text = item.packageName
            binding.tvAppUsage.text = Format.size(item.bytes)

            val percent = if (total > 0) {
                ((item.bytes * 100.0) / total).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            binding.tvPercent.text = "$percent%"
            binding.root.contentDescription = "${item.label}, ${Format.size(item.bytes)}, $percent%"

            if (item.icon != null) {
                binding.ivIcon.setImageDrawable(item.icon)
            } else {
                binding.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.pbUsage.setProgress(percent, false)
            } else {
                binding.pbUsage.progress = percent
            }

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onAppClick?.invoke(item)
            }

            // Появление: сдвиг + fade, с небольшой задержкой по позиции.
            val view = binding.root
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = view.context.resources.displayMetrics.density * 14f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position % 10) * 28L)
                .setDuration(320L)
                .start()
        }
    }
}
