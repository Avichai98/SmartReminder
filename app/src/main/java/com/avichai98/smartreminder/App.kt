package com.avichai98.smartreminder

import android.app.Application
import android.util.Log
import com.avichai98.smartreminder.models.User
import com.avichai98.smartreminder.utils.MyRealtimeFirebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class App : Application() {
    @Override
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        Log.d("App", "Firebase initialized")

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val email = firebaseUser?.email
            ?: firebaseUser?.providerData?.firstOrNull { it.email != null }?.email
        if (firebaseUser != null && email != null) {
            val user = User(firebaseUser.uid, email)
            MyRealtimeFirebase.init(user)
        }
    }
}