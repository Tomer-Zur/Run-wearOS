package com.example.run_wearos

import android.app.Service
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class TokenListenerService : LifecycleService(), MessageClient.OnMessageReceivedListener {
    override fun onCreate() {
        super.onCreate()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(this)
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/user_auth") {
            val data = String(event.data)
            try {
                val json = JSONObject(data)
                val token = json.optString("token")
                val username = json.optString("username")
                val userId = json.optString("userId")
                // Store in SharedPreferences
                getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("auth_token", token)
                    .putString("username", username)
                    .putString("userId", userId)
                    .apply()
                Log.d("WearToken", "Received and stored: $token, $username, $userId")
            } catch (e: Exception) {
                Log.e("WearToken", "Failed to parse user_auth message", e)
            }
        }
    }
}
