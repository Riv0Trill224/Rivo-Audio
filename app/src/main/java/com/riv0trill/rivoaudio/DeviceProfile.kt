package com.riv0trill.rivoaudio

import android.os.Build

object DeviceProfile {

    enum class Type {
        STANDARD_ANDROID,
        RGDS
    }

    fun detect(): Type {

        val manufacturer =
            Build.MANUFACTURER
                .orEmpty()
                .lowercase()

        val brand =
            Build.BRAND
                .orEmpty()
                .lowercase()

        val model =
            Build.MODEL
                .orEmpty()
                .lowercase()

        val device =
            Build.DEVICE
                .orEmpty()
                .lowercase()

        val product =
            Build.PRODUCT
                .orEmpty()
                .lowercase()

        val rgdsModel =
            model.contains("rg ds") ||
            model.contains("rgds")

        val rgdsDevice =
            device.contains("rgds") ||
            product.contains("rgds")

        val anbernic =
            manufacturer.contains("anbernic") ||
            brand.contains("anbernic")

        return if (
            rgdsModel ||
            rgdsDevice ||
            (
                anbernic &&
                (
                    model.contains("ds") ||
                    product.contains("ds")
                )
            )
        ) {

            Type.RGDS

        } else {

            Type.STANDARD_ANDROID
        }
    }
}