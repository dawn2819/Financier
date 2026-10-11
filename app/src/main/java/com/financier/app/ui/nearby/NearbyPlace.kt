package com.financier.app.ui.nearby

data class NearbyPlace(
    val name: String,
    val address: String,
    val kind: Kind,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float
) {
    enum class Kind { ATM, BANK }
}
