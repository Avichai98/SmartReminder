package com.avichai98.smartreminder.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import com.avichai98.smartreminder.R
import com.avichai98.smartreminder.databinding.ActivityLoginBinding
import com.avichai98.smartreminder.models.User
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private lateinit var credentialManager: CredentialManager

    private val TAG = "HybridSignIn"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar.readonly"))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            if (task.isSuccessful) {
                val account: GoogleSignInAccount? = task.result
                account?.let {
                    firebaseAuthWithGoogle(it.idToken!!)
                }
            } else {
                Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Sign-in failed: ${task.exception?.message}")
            }
        }


        binding.btnGoogleSignIn.setOnClickListener {
            attemptCredentialManagerSignIn()
        }

        // Check if the user is already signed in
        startApp()
    }

    private fun attemptCredentialManagerSignIn() {
        val googleCredentialOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleCredentialOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == "google_id_token") {
                    val idToken = credential.data["id_token"] as String
                    Log.d(TAG, "Credential Manager ID Token: $idToken")
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Log.e(TAG, "Credential not Google ID token")
                    startGoogleSignInFallback()
                }

            } catch (e: GetCredentialException) {
                Log.w(TAG, "No credentials found, fallback to GoogleSignInClient")
                startGoogleSignInFallback()
            }
        }
    }

    private fun startGoogleSignInFallback() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {

                    val firebaseUser = FirebaseAuth.getInstance().currentUser
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
                        Log.d(TAG, "Sign-in success: ${firebaseUser.displayName}")

                        val db = MyRealtimeFirebase.getInstance()
                        db.userExists { exists ->
                            if (!exists) {
                                db.saveUser()
                            }
                        }

                        startActivity(Intent(this, AppointmentActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Login failed. User or email is null",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "firebaseUser or email is null: $firebaseUser")
                    }
                }
            }
    }

    private fun startApp() {
        if (firebaseAuth.currentUser != null) {
            val intent = Intent(this, AppointmentActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}