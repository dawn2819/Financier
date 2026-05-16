package com.financier.app.ui.auth

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.financier.app.R
import com.financier.app.common.SecurityUtils
import com.financier.app.data.local.AppDatabase
import com.financier.app.data.local.entity.AppSettingsEntity
import com.financier.app.data.local.entity.FinancialAccountEntity
import com.financier.app.data.local.entity.UserEntity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterDialog : BottomSheetDialogFragment() {

    private lateinit var db: AppDatabase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.Theme_Financier)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        val etDisplayName = view.findViewById<TextInputEditText>(R.id.et_display_name)
        val etUsername = view.findViewById<TextInputEditText>(R.id.et_reg_username)
        val etPassword = view.findViewById<TextInputEditText>(R.id.et_reg_password)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_register_save)

        btnSave.setOnClickListener {
            val displayName = etDisplayName.text.toString().trim()
            val username = etUsername.text.toString().trim().lowercase()
            val password = etPassword.text.toString()

            if (displayName.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, R.string.error_empty_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(context, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val existing = db.userDao().getUserByUsername(username)
                if (existing != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.error_username_taken, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val user = UserEntity(
                    username = username,
                    displayName = displayName,
                    passwordHash = SecurityUtils.hashPassword(password),
                    role = "user",
                    avatarColor = "#9ECAFF"
                )
                val userId = db.userDao().insertUser(user)

                // Tạo settings mặc định
                db.settingsDao().insertSettings(AppSettingsEntity(userId = userId, language = "vi", currency = "VND"))

                // Tạo tài khoản tiền mặt mặc định
                val cashAccount = FinancialAccountEntity(
                    userId = userId,
                    name = "Tiền mặt",
                    accountType = "CASH",
                    colorHex = "#78DC77"
                )
                val cashId = db.financialAccountDao().insertAccount(cashAccount)
                db.settingsDao().updateSettings(
                    db.settingsDao().getSettingsByUser(userId)!!.copy(selectedAccountId = cashId)
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Đăng ký thành công! Hãy đăng nhập.", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }
}
