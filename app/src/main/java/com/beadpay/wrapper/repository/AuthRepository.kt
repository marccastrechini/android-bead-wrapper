package com.beadpay.wrapper.repository

import com.beadpay.wrapper.network.BeadApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: BeadApi
) {
    suspend fun login(username: String, password: String): String {
        val form = "grant_type=password&client_id=bead-wrapper&username=$username&password=$password"
        return api.fetchToken(body = form).accessToken
    }
}
