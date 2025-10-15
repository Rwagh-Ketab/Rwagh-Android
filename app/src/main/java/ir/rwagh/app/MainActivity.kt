package ir.rwagh.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.rwagh.app.ui.theme.RwaghTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


enum class ConnectionState {
    Available, Unavailable
}


@SuppressLint("MissingPermission")
fun Context.observeConnectivityAsFlow(): Flow<ConnectionState> = callbackFlow @RequiresPermission(
    Manifest.permission.ACCESS_NETWORK_STATE
) {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(ConnectionState.Available)
        }
        override fun onLost(network: Network) {
            trySend(ConnectionState.Unavailable)
        }
    }
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}


@Composable
fun connectivityState(): State<ConnectionState> {
    val context = LocalContext.current
    return produceState(initialValue = context.currentConnectivityState) {
        context.observeConnectivityAsFlow().collect { value = it }
    }
}

// یک property کمکی برای گرفتن وضعیت اولیه اتصال
val Context.currentConnectivityState: ConnectionState
    get() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (connectivityManager.activeNetwork != null) ConnectionState.Available else ConnectionState.Unavailable
    }

// ^^^^^^ پایان بخش جدید ^^^^^^


private class MyWebViewClient(
    private val context: Context
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        if (url.startsWith("rwagh://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "امکان باز کردن لینک اپ وجود ندارد.", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        val hostname = Uri.parse(url).host ?: ""

        if (hostname == "rwagh.ir" || hostname.endsWith(".rwagh.ir")) {
            return false
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "برنامه‌ای برای باز کردن این لینک یافت نشد.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }
}

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null  // برای دسترسی
    private var pendingUrl: String? = null

    fun getWebView(): WebView? = webView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RwaghTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebViewScreen(
                        url = "https://rwagh.ir/",
                        modifier = Modifier.padding(innerPadding),
                        onWebViewCreated = { wv ->
                            webView = wv
                            // اگر قبلاً URLی منتظر بود، حالا لودش کن
                            pendingUrl?.let {
                                wv.loadUrl(it)
                                pendingUrl = null
                            }
                        }
                    )
                }
            }
        }

        // اگر اپ از طریق DeepLink باز شده
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return

        if (data.scheme == "rwagh" && data.host == "payment-result") {
            val status = data.getQueryParameter("status") ?: ""
            val refId = data.getQueryParameter("ref_id") ?: ""
            val already = data.getQueryParameter("already") ?: ""
            val invoice = data.getQueryParameter("invoice") ?: ""

            // آدرس صفحه فرانت معادل
            val finalUrl = buildString {
                append("https://rwagh.ir/payment-result?")
                append("status=$status")
                if (refId.isNotEmpty()) append("&ref_id=$refId")
                if (already.isNotEmpty()) append("&already=$already")
                if (invoice.isNotEmpty()) append("&invoice=$invoice")
            }

            // WebView لود کن
            getWebView()?.loadUrl(finalUrl)
                ?: run {
                    pendingUrl = finalUrl
                }
        }
    }

}


@Composable
fun ErrorScreen() { // دکمه تلاش مجدد دیگر لازم نیست
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = painterResource(id = R.drawable.baseline_cloud_off_24), contentDescription = "No Connection", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "اتصال اینترنت برقرار نیست", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "لطفاً اتصال اینترنت خود را بررسی کنید. برنامه به صورت خودکار راه اندازی میشود", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        }
    }
}

/**
 * کامپوننت اصلی که WebView و تمام منطق‌های مربوط به آن را مدیریت می‌کند.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    val connection by connectivityState()
    val isOnline = connection == ConnectionState.Available
    val context = LocalContext.current
    var localWebView by remember { mutableStateOf<WebView?>(null) }

    // ---- برای File Chooser ----
    val activity = context as? ComponentActivity
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = when {
            result.resultCode != Activity.RESULT_OK -> null
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    // ----------------------------

    Box(modifier = modifier.fillMaxSize()) {
        if (isOnline) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = MyWebViewClient(ctx)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooserCallback?.onReceiveValue(null)
                                fileChooserCallback = filePathCallback

                                val intent: Intent = fileChooserParams?.createIntent()!!
                                try {
                                    filePickerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    fileChooserCallback = null
                                    Toast.makeText(
                                        ctx,
                                        "امکان باز کردن گالری وجود ندارد.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return false
                                }
                                return true
                            }
                        }

                        loadUrl(url)
                        onWebViewCreated(this)
                        localWebView = this
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            ErrorScreen()
        }
    }

    // ---- دکمه بازگشت ----
    DisposableEffect(activity, localWebView) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (localWebView?.canGoBack() == true) {
                    localWebView?.goBack()
                } else {
                    isEnabled = false
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        }
        activity?.onBackPressedDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}
