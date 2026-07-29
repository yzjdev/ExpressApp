package io.github.yzjdev.expressapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.yzjdev.expressapp.history.HistoryStorage
import io.github.yzjdev.expressapp.history.QueryHistoryItem
import io.github.yzjdev.expressapp.log.LogBuffer
import io.github.yzjdev.expressapp.log.LogLevel
import io.github.yzjdev.expressapp.log.QueryLog
import io.github.yzjdev.expressapp.tracker.ExpressTracker

class SFTrackingActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SFTracking"
        private const val EXTRA_NU = "nu"
        private const val EXTRA_MOBILE = "mobile"

        fun intent(context: Context, nu: String, mobile: String = ""): Intent {
            return Intent(context, SFTrackingActivity::class.java).apply {
                putExtra(EXTRA_NU, nu)
                putExtra(EXTRA_MOBILE, mobile)
            }
        }
    }

    private var wv: WebView? = null
    private var dataExtracted = false
    private var nu: String = ""
    private var mobile: String = ""

        private class JsBridge(private val activity: SFTrackingActivity) {
            @JavascriptInterface
            fun onIframeText(text: String) {
                Log.d(TAG, "iframe text received, length=${text.length}")
                activity.runOnUiThread {
                    if (activity.dataExtracted) return@runOnUiThread
                    activity.dataExtracted = true

                    val tracker = ExpressTracker()
                    val result = tracker.parseLocalSfData(activity.nu, text)
                    if (result.found && result.data != null) {
                        HistoryStorage(activity).save(
                            QueryHistoryItem(
                                nu = activity.nu,
                                queryTime = System.currentTimeMillis(),
                                data = result.data
                            )
                        )
                        Toast.makeText(activity, "已添加到主页", Toast.LENGTH_SHORT).show()
                    } else {
                        LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = activity.nu, message = "顺丰 WebView 解析失败: ${result.msg}"))
                        Toast.makeText(activity, "解析失败: ${result.msg}", Toast.LENGTH_SHORT).show()
                    }
                    activity.setResult(RESULT_OK)
                    activity.finish()
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        CookieManager.getInstance().setAcceptCookie(true)
        nu = intent.getStringExtra(EXTRA_NU) ?: return finish().also {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = "", message = "SFTrackingActivity 缺少单号参数"))
        }
        mobile = intent.getStringExtra(EXTRA_MOBILE) ?: ""
        LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "顺丰 WebView 页面加载中" + if (mobile.isNotEmpty()) "（含手机尾号）" else ""))
        val sfUrl = "https://exiavs.smartapps.cn/pages/common/transfer-entrance/transfer-entrance?channel=baidu_alading&waybillno=$nu" +
            (if (mobile.isNotEmpty()) "&mobileCode=$mobile" else "")

        val density = resources.displayMetrics.density

        val loadingTv = TextView(this).apply {
            text = "正在整理物流轨迹"
            textSize = 17f
            setTextColor(android.graphics.Color.parseColor("#1A1C1E"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val loadingPb = ProgressBar(this, null, android.R.attr.progressBarStyle).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#F4B400")
            )
        }
        val loadingCard = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FFE27A"))
                cornerRadii = floatArrayOf(28f * density, 28f * density, 28f * density, 28f * density, 28f * density, 28f * density, 8f * density, 8f * density)
            }
            addView(loadingPb, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            addView(loadingTv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = (48 * density).toInt()
            })
        }
        val loadingLayout = FrameLayout(this).apply {
            addView(loadingCard, FrameLayout.LayoutParams(
                (260 * density).toInt(),
                (150 * density).toInt()
            ).apply { gravity = Gravity.CENTER })
            setBackgroundColor(android.graphics.Color.parseColor("#F7F8FA"))
            visibility = View.GONE
        }

        val container = FrameLayout(this)

        wv = WebView(this).apply {
            addJavascriptInterface(JsBridge(this@SFTrackingActivity), "Android")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = true
                allowContentAccess = true
                setGeolocationEnabled(true)
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                safeBrowsingEnabled = false
                userAgentString = "Mozilla/5.0 (Linux; Android 13; PHN110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("intent:") || url.startsWith("android-app:")) return true
                    return false
                }
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                    val url = urlStr ?: return false
                    if (url.startsWith("intent:") || url.startsWith("android-app:")) return true
                    return false
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url?.contains("waybill-detail") != true) return
                    LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "顺丰 waybill-detail 已加载"))
                    view?.visibility = View.GONE
                    loadingLayout.visibility = View.VISIBLE
                    view?.postDelayed({
                        view.evaluateJavascript(
                            """(function(){
                                var f=document.querySelectorAll('iframe');
                                if(f.length<2){Android.onIframeText('只有 '+f.length+' 个iframe');return}
                                try{
                                    var d=f[1].contentDocument||f[1].contentWindow.document;
                                    Android.onIframeText(d.body.innerText);
                                }catch(e){Android.onIframeText('iframe 1 访问被拒: '+e.message)}
                            })()"""
                        ) { result ->
                            Log.d(TAG, "eval result: $result")
                        }
                    }, 2000L)
                }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "顺丰 WebView 加载失败: ${error?.description}"))
                        Toast.makeText(this@SFTrackingActivity, "加载失败: ${error?.description}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            webChromeClient = WebChromeClient()
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(wv)
        container.addView(loadingLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0)
            insets
        }
        wv?.loadUrl(sfUrl)
    }

    override fun onDestroy() {
        wv?.destroy()
        super.onDestroy()
    }
}
