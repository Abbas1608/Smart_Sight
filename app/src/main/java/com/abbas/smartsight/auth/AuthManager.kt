package com.abbas.smartsight.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class AuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)
    companion object {
        private const val TAG = "AuthManager"
        private const val WEB_CLIENT_ID = "474154110482-9oht3a457887lb3h0mo8m9p5sonrhpcg.apps.googleusercontent.com" // TODO: Replace
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        return try {
            val ranNonce = UUID.randomUUID().toString()
            val bytes = ranNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleSignIn(result)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun handleSignIn(result: GetCredentialResponse): Result<FirebaseUser> {
        return try {
            when (val credential = result.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        val idToken = googleIdTokenCredential.idToken
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(firebaseCredential).await()

                        val user = authResult.user
                        if (user != null) {
                            Result.success(user)
                        } else {
                            Result.failure(Exception("User is null"))
                        }
                    } else {
                        Result.failure(Exception("Unexpected credential type"))
                    }
                }
                else -> Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sign-Out failed: ${e.message}")
        }
    }

    fun getUserInitial(): String {
        val email = auth.currentUser?.email ?: return "?"
        return email.first().uppercase()
    }

    fun getUserEmail(): String = auth.currentUser?.email ?: "No email"

    fun getUserDisplayName(): String {
        return auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "User"
    }
}
