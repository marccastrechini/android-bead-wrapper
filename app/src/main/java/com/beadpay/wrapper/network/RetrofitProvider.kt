package com.beadpay.wrapper.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitProvider {
    val api: BeadApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.test.devs.beadpay.io/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BeadApi::class.java)
    }
}
