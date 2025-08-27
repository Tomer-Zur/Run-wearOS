package com.example.run_wearos.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import org.json.JSONObject
import android.util.Log
import android.content.Context
import androidx.compose.material3.TextField
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

// These are the API endpoints for your authentication services.
const val LOGIN_URL = "https://runfuncionapp.azurewebsites.net/api/login"
const val VALIDATE_TOKEN_URL = "https://runfuncionapp.azurewebsites.net/api/validate-token"

// Create a trust manager that trusts all certificates (for development only)
private fun createTrustAllCerts(): Array<TrustManager> {
    return arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
}

// A data class to cleanly represent the possible outcomes of a login attempt.
data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val errorMessage: String? = null
)

/**
 * A suspend function to handle the login network request on a background thread.
 * It takes username and password, sends them to the server, and returns a LoginResponse.
 * Using withContext(Dispatchers.IO) ensures this network call doesn't freeze the UI.
 */
suspend fun loginUser(username: String, password: String): LoginResponse = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        
        val url = URL(LOGIN_URL)
        val conn = url.openConnection() as HttpsURLConnection
        
        // Configure SSL to trust all certificates (for development)
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, createTrustAllCerts(), java.security.SecureRandom())
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w("Login", "Failed to configure SSL, using default: ${e.message}")
        }
        
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000 // 10 seconds
        conn.readTimeout = 10000 // 10 seconds
        
        OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
        
        val responseCode = conn.responseCode
        val responseBody = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
        }
        conn.disconnect()
        
        Log.d("Login", "Response code: $responseCode, body: $responseBody")
        
        if (responseCode == 200) {
            val responseJson = JSONObject(responseBody)
            LoginResponse(
                success = true,
                token = responseJson.getString("token"),
                username = responseJson.getString("username"),
                userId = responseJson.getString("user_id")
            )
        } else {
            val errorJson = JSONObject(responseBody)
            LoginResponse(
                success = false,
                errorMessage = errorJson.getString("error")
            )
        }
    } catch (e: Exception) {
        Log.e("Login", "Exception: ${e.localizedMessage}", e)
        val errorMsg = when {
            e.message?.contains("chain", ignoreCase = true) == true -> "SSL Certificate error. Please check your network connection."
            e.message?.contains("timeout", ignoreCase = true) == true -> "Connection timeout. Please try again."
            e.message?.contains("unable to resolve host", ignoreCase = true) == true -> "No internet connection. Please check your network."
            else -> "Network error: ${e.localizedMessage}"
        }
        LoginResponse(
            success = false,
            errorMessage = errorMsg
        )
    }
}

/**
 * A suspend function that checks if a stored authentication token is still valid.
 * It makes a GET request to the server with the token in the Authorization header.
 */
suspend fun validateToken(token: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL(VALIDATE_TOKEN_URL)
        val conn = url.openConnection() as HttpsURLConnection
        
        // Configure SSL to trust all certificates (for development)
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, createTrustAllCerts(), java.security.SecureRandom())
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w("TokenValidation", "Failed to configure SSL, using default: ${e.message}")
        }
        
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10000 // 10 seconds
        conn.readTimeout = 10000 // 10 seconds
        
        val responseCode = conn.responseCode
        conn.disconnect()
        
        Log.d("TokenValidation", "Response code: $responseCode")
        responseCode == 200
    } catch (e: Exception) {
        Log.e("TokenValidation", "Exception: ${e.localizedMessage}", e)
        false
    }
}

/**
 * Utility functions to save and retrieve the authentication token, username, and userId
 * from SharedPreferences, which provides persistent on-device storage.
 */
fun saveCredentials(context: Context, token: String, username: String, userId: String) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("auth_token", token)
        putString("username", username)
        putString("userId", userId)
        apply()
    }
}

fun clearCredentials(context: Context) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    prefs.edit().apply {
        remove("auth_token")
        remove("username")
        remove("userId")
        apply()
    }
}

fun getStoredToken(context: Context): String? {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    return prefs.getString("auth_token", null)
}

/**
 * The Jetpack Compose UI for the Login Screen.
 */
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.title3
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption1
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter both username and password"
                    return@Button
                }
                
                isLoading = true
                errorMessage = null
                
                coroutineScope.launch {
                    val response = loginUser(username, password)
                    isLoading = false
                    
                    if (response.success) {
                        response.token?.let { token ->
                            response.username?.let { username ->
                                response.userId?.let { userId ->
                                    saveCredentials(context, token, username, userId)
                                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                }
                            }
                        }
                    } else {
                        errorMessage = response.errorMessage ?: "Login failed"
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    indicatorColor = MaterialTheme.colors.onPrimary
                )
            } else {
                Text("Login")
            }
        }
    }
}
