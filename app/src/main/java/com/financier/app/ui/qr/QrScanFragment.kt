package com.financier.app.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.financier.app.R
import com.financier.app.common.CurrencyFormatter
import com.financier.app.data.bank.BankDeepLinkHelper
import com.financier.app.data.bank.VietQrParser
import com.financier.app.databinding.FragmentQrScanBinding
import com.financier.app.databinding.LayoutQrResultBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult

class QrScanFragment : Fragment() {

    private var _binding: FragmentQrScanBinding? = null
    private val binding get() = _binding!!

    private val requestCameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScanning()
        } else {
            Toast.makeText(requireContext(), "Quyền Camera là bắt buộc để quét QR code", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        binding.barcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { rawText ->
                    binding.barcodeScanner.pause()
                    handleQrResult(rawText)
                }
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    private fun handleQrResult(rawText: String) {
        val qrData = VietQrParser.parse(rawText)
        if (qrData == null) {
            Toast.makeText(requireContext(), "Mã QR không đúng định dạng VietQR", Toast.LENGTH_SHORT).show()
            binding.barcodeScanner.postDelayed({
                if (_binding != null) {
                    binding.barcodeScanner.resume()
                }
            }, 2000)
            return
        }

        showResultBottomSheet(qrData)
    }

    private fun showResultBottomSheet(data: VietQrParser.VietQrData) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = LayoutQrResultBottomSheetBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val bankInfo = BankDeepLinkHelper.getBankInfoByBin(data.bankBin)
        val bankNameText = if (bankInfo != null) bankInfo.name else "Không xác định (${data.bankBin})"
        sheetBinding.tvBankName.text = "Ngân hàng: $bankNameText"
        sheetBinding.tvAccountNumber.text = "Số tài khoản: ${data.accountNumber ?: "N/A"}"

        val amountVal = data.amount ?: 0.0
        sheetBinding.tvQrAmount.text = CurrencyFormatter.format(amountVal, "VND")
        sheetBinding.tvQrNote.text = data.description ?: "Không có nội dung"

        sheetBinding.btnOpenBank.setOnClickListener {
            dialog.dismiss()
            BankDeepLinkHelper.openBankApp(requireContext(), data.bankBin)
            findNavController().navigateUp()
        }

        sheetBinding.btnCancelSheet.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (_binding != null) {
                binding.barcodeScanner.resume()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            binding.barcodeScanner.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScanner.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
