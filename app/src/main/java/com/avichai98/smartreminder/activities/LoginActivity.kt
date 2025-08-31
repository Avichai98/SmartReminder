package com.avichai98.smartreminder.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.databinding.ActivityLoginBinding
import com.avichai98.smartreminder.models.User
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private val tag = "GoogleSignIn"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        binding = ActivityLoginBinding.bind(findViewById(R.id.loginContainer))

        firebaseAuth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        binding.btnGoogleSignIn.setOnClickListener {
            signInWithCredentialManager()
        }

        binding.btnTerms.setOnClickListener {
            openExternalLink(getString(R.string.terms_url))
        }
        binding.btnPrivacy.setOnClickListener {
            openExternalLink(getString(R.string.privacy_url))
        }

        // Check if the user is already signed in
        checkExistingUser()
    }

    private fun signInWithCredentialManager() {
        binding.btnGoogleSignIn.isEnabled = false
        //binding.progress.visibility = View.VISIBLE
        showLoading(true)

        // Option for new sign-in
        val signInOption = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                handleSignInResult(result)
            } catch (e: GetCredentialException) {
                handleSignInError(e)
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is GoogleIdTokenCredential -> {
                // Handle Google ID Token credential
                val idToken = credential.idToken
                Log.d(tag, "Google ID Token received")
                firebaseAuthWithGoogle(idToken)
            }
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        Log.d(tag, "Custom Google ID Token received")
                        firebaseAuthWithGoogle(idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(tag, "Received an invalid Google ID token response", e)
                        showError("Invalid Google credential")
                        //binding.progress.visibility = View.GONE
                        showLoading(false)
                        binding.btnGoogleSignIn.isEnabled = true
                    }
                } else {
                    Log.e(tag, "Unexpected credential type: ${credential.type}")
                    showError("Unexpected credential type")
                    //binding.progress.visibility = View.GONE
                    showLoading(false)
                    binding.btnGoogleSignIn.isEnabled = true
                }
            }
            else -> {
                Log.e(tag, "Unexpected credential type")
                showError("Unexpected credential type")
                //binding.progress.visibility = View.GONE
                showLoading(false)
                binding.btnGoogleSignIn.isEnabled = true
            }
        }
    }

    private fun handleSignInError(e: GetCredentialException) {
        when (e) {
            is NoCredentialException -> {
                Log.d(tag, "No credentials available")
                showError("No Google accounts found. Please add a Google account to your device.")
            }
            else -> {
                Log.e(tag, "Sign-in failed", e)
                showError("Sign-in failed: ${e.message}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    handleSuccessfulSignIn()
                } else {
                    Log.e(tag, "Firebase authentication failed", task.exception)
                    showError("Authentication failed")
                    //binding.progress.visibility = View.GONE
                    showLoading(false)
                    binding.btnGoogleSignIn.isEnabled = true
                }
            }
    }

    private fun handleSuccessfulSignIn() {
        val firebaseUser = firebaseAuth.currentUser
        val email = firebaseUser?.email ?: firebaseUser?.providerData
            ?.firstOrNull { it.email != null }?.email

        if (firebaseUser != null && email != null) {
            val user = User(firebaseUser.uid, email)
            MyRealtimeFirebase.init(user)

            Toast.makeText(
                this,
                "Signed in as: ${firebaseUser.displayName}",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(tag, "Sign-in success: ${firebaseUser.displayName}")

            val db = MyRealtimeFirebase.getInstance()
            db.userExists { exists ->
                if (!exists) {
                    db.saveUser()
                }
            }

            navigateToMainActivity()
        } else {
            Log.e(tag, "Firebase user or email is null")
            showError("Login failed. User information is incomplete.")
        }
    }

    private fun checkExistingUser() {
        if (firebaseAuth.currentUser != null) {
            navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, AppointmentActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        //binding.progress.visibility = View.GONE
        showLoading(false)
        binding.btnGoogleSignIn.isEnabled = true
    }

    private fun showLoading(show: Boolean) {
        val v = binding.loadingView.root
        if (show) {
            v.alpha = 0f
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(150).start()
        } else {
            v.animate().alpha(0f).setDuration(150).withEndAction {
                v.visibility = View.GONE
            }.start()
        }
    }

    /** Opens an external URL in the user's default browser */
    private fun openExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
            Log.e("Login", "Failed to open link: $url", e)
        }
    }
}