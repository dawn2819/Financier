package com.financier.app.ui.nearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.financier.app.R
import com.financier.app.common.AppAudio
import com.financier.app.databinding.FragmentNearbyPlacesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NearbyPlacesFragment : Fragment() {
    private var _binding: FragmentNearbyPlacesBinding? = null
    private val binding get() = _binding!!
    private lateinit var placesAdapter: NearbyPlacesAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            getCurrentLocation()
        } else {
            fallbackDefaultLocation(getString(R.string.nearby_places_permission_denied))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyPlacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        placesAdapter = NearbyPlacesAdapter(::openDirections)
        binding.rvNearbyPlaces.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNearbyPlaces.adapter = placesAdapter

        binding.btnBack.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigateUp()
        }
        binding.btnFindNearby.setOnClickListener {
            AppAudio.playClick(requireContext())
            findNearbyPlaces()
        }
        binding.tvOsmCredit.setOnClickListener {
            openUrl("https://www.openstreetmap.org/copyright")
        }

        // Auto find if permission is already granted
        val hasFine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            getCurrentLocation()
        }
    }

    private fun findNearbyPlaces() {
        val finePermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarsePermission = Manifest.permission.ACCESS_COARSE_LOCATION

        val hasFine = ContextCompat.checkSelfPermission(requireContext(), finePermission) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(requireContext(), coarsePermission) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(arrayOf(finePermission, coarsePermission))
        }
    }

    @Suppress("MissingPermission")
    private fun getCurrentLocation() {
        val manager = requireContext().getSystemService(LocationManager::class.java)
        if (manager == null) {
            fallbackDefaultLocation()
            return
        }

        // 1. Try to find the best last known location immediately
        var bestLocation: Location? = null
        try {
            val enabledProviders = manager.getProviders(true)
            for (p in enabledProviders) {
                val loc = manager.getLastKnownLocation(p) ?: continue
                if (bestLocation == null || (loc.hasAccuracy() && loc.accuracy < (bestLocation?.accuracy ?: Float.MAX_VALUE))) {
                    bestLocation = loc
                }
            }
        } catch (_: SecurityException) {
        }

        if (bestLocation != null) {
            loadPlaces(bestLocation!!)
            return
        }

        // 2. If no last known location, select an enabled provider
        val provider = try {
            when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
                else -> manager.getProviders(true).firstOrNull()
            }
        } catch (_: SecurityException) {
            null
        }

        if (provider == null) {
            fallbackDefaultLocation("Vui lòng bật định vị GPS để tìm chính xác hơn quanh bạn")
            return
        }

        setLoading(true, R.string.nearby_places_loading)

        try {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(requireContext())
            ) locationCallback@{ location ->
                if (_binding == null) return@locationCallback
                val usableLocation = location ?: try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }

                if (usableLocation == null) {
                    fallbackDefaultLocation("Đang hiển thị điểm giao dịch lân cận (Chưa nhận tín hiệu GPS)")
                } else {
                    loadPlaces(usableLocation)
                }
            }
        } catch (_: SecurityException) {
            fallbackDefaultLocation()
        }
    }

    private fun fallbackDefaultLocation(noticeText: String? = null) {
        val defaultLoc = Location("default").apply {
            latitude = 21.028511
            longitude = 105.854167
        }
        loadPlaces(defaultLoc, noticeText)
    }

    private fun loadPlaces(location: Location, customNotice: String? = null) {
        setLoading(true, R.string.nearby_places_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) {
                NearbyPlacesRepository.findNearby(location.latitude, location.longitude)
            }
            if (_binding == null) return@launch

            setLoading(false)
            placesAdapter.submitList(places)

            if (places.isEmpty()) {
                showStatus(R.string.nearby_places_none)
            } else {
                val foundText = getString(R.string.nearby_places_found, places.size)
                binding.tvNearbyStatus.text = if (customNotice != null) {
                    "$customNotice\n$foundText"
                } else {
                    foundText
                }
                binding.tvNearbyStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(isLoading: Boolean, status: Int? = null) {
        _binding ?: return
        binding.progressNearby.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnFindNearby.isEnabled = !isLoading
        binding.tvNearbyStatus.visibility = View.VISIBLE
        if (status != null) binding.tvNearbyStatus.setText(status)
    }

    private fun showStatus(message: Int) {
        _binding ?: return
        binding.tvNearbyStatus.setText(message)
        binding.tvNearbyStatus.visibility = View.VISIBLE
    }

    private fun openDirections(place: NearbyPlace) {
        AppAudio.playClick(requireContext())
        val navigationUri = Uri.parse(
            "google.navigation:q=${place.latitude},${place.longitude}"
        )
        val navigationIntent = Intent(Intent.ACTION_VIEW, navigationUri)
        if (navigationIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(navigationIntent)
        } else {
            openUrl(
                "https://www.google.com/maps/dir/?api=1&destination=" +
                    "${place.latitude},${place.longitude}"
            )
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onDestroyView() {
        binding.rvNearbyPlaces.adapter = null
        super.onDestroyView()
        _binding = null
    }
}