@file:Suppress("DEPRECATION")

package com.Atom2Universe.app.music.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Manages Google Sign-In for cloud sync.
 *
 * Requests only the drive.appdata scope, which provides:
 * - A hidden folder visible only to this app
 * - No access to user's regular Drive files
 * - No consent prompt about Drive access
 */
class GoogleSignInManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInManager"

        /**
         * Scope for App Data folder access.
         * This is a special hidden folder that only this app can see.
         */
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }

    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(SCOPE_APPDATA))
        .build()

    private val googleSignInClient: GoogleSignInClient =
        GoogleSignIn.getClient(context, googleSignInOptions)

    /**
     * Returns the currently signed-in Google account, or null if not signed in.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Checks if the user is signed in with the required App Data scope.
     */
    fun isSignedIn(): Boolean {
        val account = getSignedInAccount()
        return account != null &&
                GoogleSignIn.hasPermissions(account, Scope(SCOPE_APPDATA))
    }

    /**
     * Returns the signed-in user's email address, or null.
     */
    fun getSignedInEmail(): String? {
        return getSignedInAccount()?.email
    }

    /**
     * Returns an Intent to launch the Google Sign-In flow.
     *
     * Usage in Activity:
     * ```
     * val signInLauncher = registerForActivityResult(
     *     ActivityResultContracts.StartActivityForResult()
     * ) { result ->
     *     if (result.resultCode == RESULT_OK) {
     *         val account = googleSignInManager.handleSignInResult(result.data)
     *         // Handle success
     *     }
     * }
     *
     * signInLauncher.launch(googleSignInManager.getSignInIntent())
     * ```
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Handles the result from the sign-in Activity.
     *
     * @param data The Intent data from onActivityResult
     * @return The signed-in account, or null if sign-in failed
     */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Sign-in successful: ${account.email}")
            account
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with status code: ${e.statusCode}", e)
            null
        }
    }

    /**
     * Signs out the current user.
     *
     * This clears the cached account but doesn't revoke app permissions.
     * Use revokeAccess() to fully disconnect.
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            Log.d(TAG, "Sign-out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
        }
    }

    /**
     * Revokes access and disconnects the app from the user's Google account.
     *
     * This removes all app data from Google Drive and clears permissions.
     * Use signOut() for a softer disconnect that preserves data.
     */
    @Suppress("unused")
    suspend fun revokeAccess() {
        try {
            googleSignInClient.revokeAccess().await()
            Log.d(TAG, "Access revoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Revoke access failed", e)
        }
    }

    /**
     * Attempts silent sign-in to restore a previous session.
     *
     * @return The signed-in account, or null if silent sign-in failed
     */
    @Suppress("unused")
    suspend fun silentSignIn(): GoogleSignInAccount? {
        return try {
            val account = googleSignInClient.silentSignIn().await()
            Log.d(TAG, "Silent sign-in successful: ${account.email}")
            account
        } catch (_: ApiException) {
            Log.d(TAG, "Silent sign-in failed, user interaction required")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Silent sign-in error", e)
            null
        }
    }
}
