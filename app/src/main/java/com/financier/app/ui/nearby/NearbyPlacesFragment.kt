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
import com.financier.app.databinding.FragmentNearbyPlacesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NearbyPlacesFragment : Fragment() {
    private var _binding: FragmentNearbyPlacesBinding? = null
    private val binding get() = _binding!!
    private lateinit var placesAdapter: NearbyPlacesAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) getCurrentLocation()
        else showStatus(R.string.nearby_places_permission_denied)
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

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnFindNearby.setOnClickListener { findNearbyPlaces() }
        binding.tvOsmCredit.setOnClickListener {
            openUrl("https://www.openstreetmap.org/copyright")
        }
    }

    private fun findNearbyPlaces() {
        val permission = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }

    @Suppress("MissingPermission")
    private fun getCurrentLocation() {
        val manager = requireContext().getSystemService(LocationManager::class.java)
        val provider = try {
            when {
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) ->
                    LocationManager.PASSIVE_PROVIDER
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }

        if (provider == null) {
            showStatus(R.string.nearby_places_location_unavailable)
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
                    setLoading(false)
                    showStatus(R.string.nearby_places_location_unavailable)
                } else {
                    loadPlaces(usableLocation)
                }
            }
        } catch (_: SecurityException) {
            setLoading(false)
            showStatus(R.string.nearby_places_permission_denied)
        }
    }

    private fun loadPlaces(location: Location) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NearbyPlacesRepository.findNearby(location.latitude, location.longitude)
                }
            }
            if (_binding == null) return@launch

            setLoading(false)
            result.onSuccess { places ->
                placesAdapter.submitList(places)
                if (places.isEmpty()) {
                    showStatus(R.string.nearby_places_none)
                } else {
                    binding.tvNearbyStatus.text = getString(
                        R.string.nearby_places_found,
                        places.size
                    )
                    binding.tvNearbyStatus.visibility = View.VISIBLE
                }
            }.onFailure {
                showStatus(R.string.nearby_places_error)
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
