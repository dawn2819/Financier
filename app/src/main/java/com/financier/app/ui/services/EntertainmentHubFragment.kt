package com.financier.app.ui.services

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.financier.app.R
import com.financier.app.common.AppAudio
import com.financier.app.common.CurrencyFormatter
import com.financier.app.common.SessionManager
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.TransactionEntity
import com.financier.app.databinding.FragmentEntertainmentHubBinding
import com.financier.app.databinding.ItemCheckinDayBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class EntertainmentHubFragment : Fragment() {

    private var _binding: FragmentEntertainmentHubBinding? = null
    private val binding get() = _binding!!

    private var isSpinning = false
    private var currentWheelRotation = 0f

    // Wheel prizes in order (6 segments)
    private val wheelPrizes = listOf(
        WheelPrize("10.000 ₫", 10000.0, false, "Thưởng tiền mặt 10.000 ₫"),
        WheelPrize("Voucher EVN 50K", 50000.0, true, "Voucher giảm 50K hóa đơn tiền điện"),
        WheelPrize("Chúc bạn may mắn lần sau", 0.0, false, "Chúc bạn may mắn vào ngày mai nhé!"),
        WheelPrize("50.000 ₫", 50000.0, false, "Thưởng tiền mặt 50.000 ₫ vào tài khoản!"),
        WheelPrize("Voucher Highlands 30K", 30000.0, true, "Voucher giảm 30K thanh toán Highlands"),
        WheelPrize("100.000 ₫", 100000.0, false, "Đại thắng 100.000 ₫ vào tài khoản!")
    )

    data class WheelPrize(
        val name: String,
        val amount: Double,
        val isVoucher: Boolean,
        val description: String
    )

    data class VoucherItem(
        val title: String,
        val description: String,
        val code: String
    )

    private val userVouchers = mutableListOf(
        VoucherItem("Giảm 50.000 ₫ Hóa đơn Điện", "Áp dụng hóa đơn EVN từ 200.000 ₫", "EVN50K"),
        VoucherItem("Hoàn 20% Nước sinh hoạt", "Giảm tối đa 30.000 ₫ khi thanh toán nước", "WATER20"),
        VoucherItem("Giảm 30K Nạp điện thoại", "Áp dụng nạp điện thoại mệnh giá từ 100.000 ₫", "PHONE30")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntertainmentHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigateUp()
        }

        setupCheckin()
        setupLuckyWheel()
        setupTarotCards()
        renderVouchers()
    }

    // -------------------------------------------------------------
    // 1. DAILY CHECK-IN
    // -------------------------------------------------------------
    private fun setupCheckin() {
        val prefs = requireContext().getSharedPreferences("financier_entertainment", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckinDate = prefs.getString("last_checkin_date", "")
        val streak = prefs.getInt("checkin_streak", 0)

        val isCheckedToday = lastCheckinDate == todayStr

        updateCheckinUI(streak, isCheckedToday)

        binding.btnCheckinNow.setOnClickListener {
            AppAudio.playClick(requireContext())
            if (isCheckedToday) {
                Toast.makeText(requireContext(), "Bạn đã điểm danh hôm nay rồi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newStreak = if (streak >= 7) 1 else streak + 1
            prefs.edit()
                .putString("last_checkin_date", todayStr)
                .putInt("checkin_streak", newStreak)
                .apply()

            rewardCash(10000.0, "Điểm danh hàng ngày (+10K)")

            updateCheckinUI(newStreak, true)

            AlertDialog.Builder(requireContext())
                .setTitle("🎉 Điểm danh thành công!")
                .setMessage("Chúc mừng bạn đã nhận được +10.000 ₫ vào ví chính.\nChuỗi hiện tại: $newStreak ngày.")
                .setPositiveButton("Tuyệt vời", null)
                .show()
        }
    }

    private fun updateCheckinUI(streak: Int, isCheckedToday: Boolean) {
        val displayStreak = if (streak == 0) 1 else streak
        binding.tvStreakCount.text = "Chuỗi $displayStreak ngày"

        if (isCheckedToday) {
            binding.btnCheckinNow.isEnabled = false
            binding.btnCheckinNow.text = "✓ Đã điểm danh hôm nay (+10.000 ₫)"
        } else {
            binding.btnCheckinNow.isEnabled = true
            binding.btnCheckinNow.text = "Điểm danh hôm nay (+10.000 ₫)"
        }

        // Update day items
        val dayViews = listOf(
            binding.day1.root to 1,
            binding.day2.root to 2,
            binding.day3.root to 3,
            binding.day4.root to 4,
            binding.day5.root to 5,
            binding.day6.root to 6,
            binding.day7.root to 7
        )

        for ((dayRoot, dayIndex) in dayViews) {
            val tvLabel = dayRoot.findViewById<TextView>(R.id.tv_day_label)
            val cardCircle = dayRoot.findViewById<MaterialCardView>(R.id.card_day_circle)
            val ivStatus = dayRoot.findViewById<ImageView>(R.id.iv_day_status)
            val tvReward = dayRoot.findViewById<TextView>(R.id.tv_day_reward)

            tvLabel.text = "N$dayIndex"
            tvReward.text = if (dayIndex == 7) "+50K" else "+10K"

            if (dayIndex <= streak) {
                cardCircle.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                ivStatus.setImageResource(R.drawable.ic_check)
                ivStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
            } else {
                cardCircle.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_container_highest))
                ivStatus.setImageResource(R.drawable.ic_add)
                ivStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            }
        }
    }

    // -------------------------------------------------------------
    // 2. LUCKY WHEEL
    // -------------------------------------------------------------
    private fun setupLuckyWheel() {
        binding.btnSpinWheel.setOnClickListener {
            if (isSpinning) return@setOnClickListener
            AppAudio.playClick(requireContext())
            spinWheel()
        }
    }

    private fun spinWheel() {
        isSpinning = true
        binding.btnSpinWheel.isEnabled = false

        // Pick random prize index
        val targetIndex = Random.nextInt(wheelPrizes.size)
        val selectedPrize = wheelPrizes[targetIndex]

        // Each segment is 60 degrees.
        // Pointer is at the top (270 or 90 depending on coordinate).
        // Let's compute angle offset for segment targetIndex
        val segmentAngle = 360f / wheelPrizes.size
        val extraRounds = 360f * 6
        val targetAngle = extraRounds + (360f - (targetIndex * segmentAngle + segmentAngle / 2f))

        val animator = ObjectAnimator.ofFloat(
            binding.ivLuckyWheel,
            "rotation",
            currentWheelRotation,
            currentWheelRotation + targetAngle
        )
        animator.duration = 3800
        animator.interpolator = DecelerateInterpolator(2.5f)

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentWheelRotation = (currentWheelRotation + targetAngle) % 360f
                isSpinning = false
                if (!isAdded) return
                binding.btnSpinWheel.isEnabled = true

                handlePrizeWon(selectedPrize)
            }
        })

        animator.start()
    }

    private fun handlePrizeWon(prize: WheelPrize) {
        AppAudio.playClick(requireContext())
        binding.tvWheelResult.text = "Kết quả: ${prize.name}"

        if (prize.amount > 0 && !prize.isVoucher) {
            rewardCash(prize.amount, "Thưởng Vòng Quay May Mắn")
        } else if (prize.isVoucher) {
            val newVoucher = VoucherItem(prize.name, prize.description, "WHEEL" + Random.nextInt(1000, 9999))
            userVouchers.add(0, newVoucher)
            renderVouchers()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (prize.amount > 0 || prize.isVoucher) "🎁 Chúc Mừng Bạn!" else "May Mắn Lần Sau")
            .setMessage("${prize.description}\n\nCảm ơn bạn đã tham gia cùng Financier!")
            .setPositiveButton("Tuyệt vời", null)
            .show()
    }

    // -------------------------------------------------------------
    // 3. TAROT CARDS FLIP
    // -------------------------------------------------------------
    private fun setupTarotCards() {
        val tarotCards = listOf(
            binding.cardTarot1 to ("Tài Vận" to "Quẻ Tài Vận: Hôm nay vận may gõ cửa, có lộc ăn uống hoặc nhận tiền bất ngờ từ bạn bè. Số may mắn: 68 - 86."),
            binding.cardTarot2 to ("Công Danh" to "Quẻ Công Danh: Công việc hanh thông, kế hoạch chi tiêu đang đi đúng hướng, sắp có tin vui tăng thu nhập. Số may mắn: 39 - 79."),
            binding.cardTarot3 to ("Bình An" to "Quẻ Bình An: Tiền bạc ổn định, tâm an trí sáng, là thời điểm tốt để tích lũy và đầu tư kiến thức tài chính. Số may mắn: 28 - 99.")
        )

        for ((cardView, pair) in tarotCards) {
            cardView.setOnClickListener {
                AppAudio.playClick(requireContext())
                flipTarotCard(cardView, pair.first, pair.second)
            }
        }
    }

    private fun flipTarotCard(view: View, title: String, message: String) {
        // 3D rotation flip
        val flipOut = ObjectAnimator.ofFloat(view, "rotationY", 0f, 90f).apply { duration = 200 }
        val flipIn = ObjectAnimator.ofFloat(view, "rotationY", -90f, 0f).apply { duration = 200 }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.alpha = 0.85f
                flipIn.start()
            }
        })

        flipIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isAdded) return
                AlertDialog.Builder(requireContext())
                    .setTitle("🔮 $title")
                    .setMessage(message)
                    .setPositiveButton("Đã hiểu", null)
                    .show()
            }
        })

        flipOut.start()
    }

    // -------------------------------------------------------------
    // 4. VOUCHERS LIST
    // -------------------------------------------------------------
    private fun renderVouchers() {
        binding.containerVouchers.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        userVouchers.forEach { voucher ->
            val itemView = inflater.inflate(R.layout.item_voucher_card, binding.containerVouchers, false)
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_voucher_title)
            val tvDesc = itemView.findViewById<TextView>(R.id.tv_voucher_desc)
            val tvCode = itemView.findViewById<TextView>(R.id.tv_voucher_code)
            val btnUse = itemView.findViewById<View>(R.id.btn_use_voucher)

            tvTitle.text = voucher.title
            tvDesc.text = voucher.description
            tvCode.text = "Mã: ${voucher.code} · HSD: 30 ngày"

            btnUse.setOnClickListener {
                AppAudio.playClick(requireContext())
                try {
                    findNavController().navigate(R.id.action_entertainmentHubFragment_to_billsHubFragment)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Mã '${voucher.code}' đã sẵn sàng áp dụng tại Hóa Đơn", Toast.LENGTH_SHORT).show()
                }
            }

            binding.containerVouchers.addView(itemView)
        }
    }

    // Helper: Reward cash into primary account
    private fun rewardCash(amount: Double, note: String) {
        val userId = SessionManager.getUserId(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val accounts = db.financialAccountDao().getAccountsByUserSync(userId)
            val primary = accounts.firstOrNull() ?: return@launch

            db.transactionDao().insertTransaction(
                TransactionEntity(
                    userId = userId,
                    accountId = primary.id,
                    amount = amount,
                    type = "INCOME",
                    category = "income",
                    note = note,
                    dateMs = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}