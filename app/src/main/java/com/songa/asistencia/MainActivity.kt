package com.songa.asistencia

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var connectivityManager: ConnectivityManager

    private val cameraRequest = 1001
    private val startUrl = "https://songa-eventos.local/movil/"
    private var pageLoaded = false

    private val wifiRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        // No exigimos Internet: la Wi-Fi de Songa Eventos es una red local.
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                // Esta app es exclusivamente para Songa Eventos local.
                // Asociamos su proceso a la Wi-Fi local aunque Android la
                // marque como "Sin Internet". Los datos móviles de otras
                // aplicaciones continúan siendo la red predeterminada.
                val bound = connectivityManager.bindProcessToNetwork(network)

                if (!bound) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo seleccionar la Wi-Fi local.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                if (!pageLoaded) {
                    pageLoaded = true
                    webView.loadUrl(startUrl)
                }
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                // Libera el enlace para permitir que Android vuelva a su red
                // predeterminada si la Wi-Fi local desaparece.
                connectivityManager.bindProcessToNetwork(null)
                pageLoaded = false

                Toast.makeText(
                    this@MainActivity,
                    "Se perdió la Wi-Fi local de Songa Eventos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onUnavailable() {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Conéctate a la Wi-Fi local de Songa Eventos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectivityManager =
            getSystemService(ConnectivityManager::class.java)

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(241, 245, 249))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (request.resources.contains(
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            )
                        ) {
                            if (
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                request.grant(
                                    arrayOf(
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                    )
                                )
                            } else {
                                request.deny()
                                requestCameraPermission()
                            }
                        } else {
                            request.deny()
                        }
                    }
                }
            }
        }

        setContentView(webView)
        requestCameraPermission()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            pageLoaded = true
        }

        // Solicita específicamente la Wi-Fi local. No requiere que tenga
        // salida a Internet.
        connectivityManager.requestNetwork(wifiRequest, wifiCallback)
    }

    private fun requestCameraPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraRequest
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == cameraRequest &&
            (grantResults.isEmpty() ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED)
        ) {
            Toast.makeText(
                this,
                "Se necesita permiso de cámara para escanear credenciales.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        } catch (_: Exception) {
            // El callback puede no estar registrado si la Activity
            // terminó durante la inicialización.
        }

        connectivityManager.bindProcessToNetwork(null)
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
