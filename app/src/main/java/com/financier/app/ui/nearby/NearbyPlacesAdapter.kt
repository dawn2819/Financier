package com.financier.app.ui.nearby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.financier.app.R
import com.financier.app.databinding.ItemNearbyPlaceBinding
import java.util.Locale

class NearbyPlacesAdapter(
    private val onDirections: (NearbyPlace) -> Unit
) : ListAdapter<NearbyPlace, NearbyPlacesAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemNearbyPlaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaceViewHolder(binding, onDirections)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlaceViewHolder(
        private val binding: ItemNearbyPlaceBinding,
        private val onDirections: (NearbyPlace) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(place: NearbyPlace) {
            val context = binding.root.context
            val type = if (place.kind == NearbyPlace.Kind.ATM) {
                context.getString(R.string.nearby_places_atm)
            } else {
                context.getString(R.string.nearby_places_bank)
            }
            val distance = if (place.distanceMeters < 1_000) {
                context.getString(R.string.nearby_places_distance_m, place.distanceMeters.toInt())
            } else {
                context.getString(
                    R.string.nearby_places_distance_km,
                    place.distanceMeters / 1_000f
                )
            }

            binding.tvPlaceIcon.text = if (place.kind == NearbyPlace.Kind.ATM) "ATM" else "₫"
            binding.tvPlaceName.text = place.name
            binding.tvPlaceKindDistance.text = context.getString(
                R.string.nearby_places_kind_distance,
                type,
                distance
            )
            binding.tvPlaceAddress.text = place.address.ifBlank {
                context.getString(R.string.nearby_places_address_unavailable)
            }
            binding.root.setOnClickListener { onDirections(place) }
            binding.root.contentDescription = String.format(
                Locale.getDefault(),
                "%s, %s, %s",
                place.name,
                type,
                distance
            )
        }
    }

    private class PlaceDiffCallback : DiffUtil.ItemCallback<NearbyPlace>() {
        override fun areItemsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace): Boolean =
            oldItem.latitude == newItem.latitude && oldItem.longitude == newItem.longitude

        override fun areContentsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace): Boolean =
            oldItem == newItem
    }
}
