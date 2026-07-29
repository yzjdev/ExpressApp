package io.github.yzjdev.expressapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.yzjdev.expressapp.history.HistoryStorage
import io.github.yzjdev.expressapp.history.QueryHistoryItem
import io.github.yzjdev.expressapp.log.LogBuffer
import io.github.yzjdev.expressapp.log.LogLevel
import io.github.yzjdev.expressapp.log.QueryLog
import io.github.yzjdev.expressapp.tracker.ExpressResult
import io.github.yzjdev.expressapp.tracker.ExpressTracker
import io.github.yzjdev.expressapp.ui.ExpressTheme
import io.github.yzjdev.expressapp.ui.LogScreen
import io.github.yzjdev.expressapp.ui.MainScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREF_KEY_MOBILE = "mobile_code"
    }

    private var hiddenWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExpressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExpressApp()
                }
            }
        }
    }

    override fun onDestroy() {
        hiddenWebView?.destroy()
        super.onDestroy()
    }

    @Composable
    private fun ExpressApp() {
        val scope = rememberCoroutineScope()
        val tracker = remember { ExpressTracker() }
        var isLoading by remember { mutableStateOf(false) }
        var showLog by remember { mutableStateOf(false) }
        val prefs = remember { getSharedPreferences("express", Context.MODE_PRIVATE) }
        var savedNu by remember { mutableStateOf(prefs.getString("nu", "") ?: "") }
        val historyStorage = remember { HistoryStorage(this@MainActivity) }
        var records by remember { mutableStateOf(historyStorage.load()) }
        val sfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            records = historyStorage.load()
        }

        Box(modifier = Modifier.size(360.dp, 640.dp).graphicsLayer(alpha = 0f, translationX = -10000f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; PHN110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        hiddenWebView = this
                    }
                }
            )
        }

        if (showLog) {
            LogScreen(onClose = { showLog = false })
            return
        }

        MainScreen(
            initNu = savedNu,
            onOpenLog = { showLog = true },
            records = records,
            isLoading = isLoading,
            onQuery = { nu ->
                prefs.edit().putString("nu", nu).apply()
                savedNu = nu
                if (nu.uppercase().startsWith("SF")) {
                    LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "发起顺丰查询"))
                    sfLauncher.launch(SFTrackingActivity.intent(this@MainActivity, nu))
                } else {
                    scope.launch {
                        isLoading = true
                        LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "发起百度查询"))
                        val result = withContext(Dispatchers.IO) { queryBaiduWebView(nu, "", tracker) }
                        if (result.found && result.data != null) {
                            historyStorage.save(QueryHistoryItem(nu = nu, queryTime = System.currentTimeMillis(), data = result.data))
                        } else {
                            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "查询失败: ${result.msg}"))
                        }
                        records = historyStorage.load()
                        isLoading = false
                    }
                }
            },
            onQueryWithMobile = { nu, mobile ->
                prefs.edit().putString("nu", nu).putString(PREF_KEY_MOBILE, mobile).apply()
                savedNu = nu
                if (nu.uppercase().startsWith("SF")) {
                    LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "发起顺丰查询（含手机尾号）"))
                    sfLauncher.launch(SFTrackingActivity.intent(this@MainActivity, nu, mobile))
                } else {
                    scope.launch {
                        isLoading = true
                        LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "发起百度查询（含手机尾号）"))
                        val result = withContext(Dispatchers.IO) { queryBaiduWebView(nu, mobile, tracker) }
                        if (result.found && result.data != null) {
                            historyStorage.save(QueryHistoryItem(nu = nu, queryTime = System.currentTimeMillis(), data = result.data))
                        } else {
                            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "查询失败: ${result.msg}"))
                        }
                        records = historyStorage.load()
                        isLoading = false
                    }
                }
            },
            onRefresh = { nu, done ->
                if (nu.uppercase().startsWith("SF")) {
                    LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "刷新: 顺丰单号"))
                    sfLauncher.launch(SFTrackingActivity.intent(this@MainActivity, nu))
                    done()
                } else {
                    scope.launch {
                        LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "刷新: 发起百度查询"))
                        val result = withContext(Dispatchers.IO) { queryBaiduWebView(nu, "", tracker) }
                        if (result.found && result.data != null) {
                            historyStorage.save(QueryHistoryItem(nu = nu, queryTime = System.currentTimeMillis(), data = result.data))
                        } else {
                            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "刷新失败: ${result.msg}"))
                        }
                        records = historyStorage.load()
                        done()
                    }
                }
            },
            onRefreshAll = { done ->
                LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = "", message = "刷新全部未签收运单"))
                scope.launch {
                    val toRefresh = records.filter { it.data.statusTag != "已签收" }
                    toRefresh.forEach { item ->
                        val result = withContext(Dispatchers.IO) { queryBaiduWebView(item.nu, "", tracker) }
                        if (result.found && result.data != null) {
                            historyStorage.save(QueryHistoryItem(nu = item.nu, queryTime = System.currentTimeMillis(), data = result.data))
                        }
                    }
                    records = historyStorage.load()
                    done()
                }
            },
            onExport = {
                val json = org.json.JSONArray()
                records.forEach { r ->
                    val d = r.data
                    json.put(org.json.JSONObject().apply {
                        put("nu", r.nu)
                        put("queryTime", r.queryTime)
                        put("companyName", d.companyName)
                        put("companyCode", d.companyCode)
                        put("status", d.status)
                        put("statusTag", d.statusTag)
                        put("delivered", d.delivered)
                        put("from", d.from)
                        put("to", d.to)
                        put("tel", d.tel)
                        put("traces", org.json.JSONArray().apply {
                            d.traces.forEach { t ->
                                put(org.json.JSONObject().apply {
                                    put("timestamp", t.timestamp)
                                    put("desc", t.desc)
                                })
                            }
                        })
                    })
                }
                val text = json.toString(2)
                val file = File(cacheDir, "快递记录.json")
                file.writeText(text)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            },
            onImport = { items ->
                LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = "", message = "导入 ${items.size} 条记录"))
                items.forEach { historyStorage.save(it) }
                records = historyStorage.load()
            },
            onImportSfLocal = { nu, content ->
                LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "导入顺丰本地数据"))
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        tracker.parseLocalSfData(nu.trim(), content)
                    }
                    if (result.found && result.data != null) {
                        historyStorage.save(
                            QueryHistoryItem(
                                nu = nu.trim(),
                                queryTime = System.currentTimeMillis(),
                                data = result.data
                            )
                        )
                    } else {
                        LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "顺丰本地数据导入解析失败: ${result.msg}"))
                    }
                    records = historyStorage.load()
                }
            },
            onDelete = { nu ->
                LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "删除运单"))
                historyStorage.delete(nu)
                records = historyStorage.load()
            }
        )
    }

    private suspend fun queryBaiduWebView(nu: String, mobile: String = "", tracker: ExpressTracker = ExpressTracker()): ExpressResult {
        val wv = hiddenWebView ?: return ExpressResult(nu, false, "WebView初始化失败", null).also {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "WebView 未初始化"))
        }
        val deferred = CompletableDeferred<String>()
        val baiduUrl = "https://www.baidu.com/s?wd=${URLEncoder.encode(nu, "UTF-8")}" +
            (if (mobile.isNotEmpty()) "&phone=$mobile" else "")
        withContext(Dispatchers.Main) {
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.postDelayed({
                        view.evaluateJavascript(
                            "(function(){ var m = document.documentElement.outerHTML.match(/\"dataUrl\":\\s*\"([^\"]+)\"/); return m ? m[1] : ''; })()"
                        ) { result -> deferred.complete(result ?: "") }
                    }, 500L)
                }
            }
            wv.loadUrl(baiduUrl)
        }
        val jsResult = withContext(Dispatchers.IO) { withTimeout(15000) { deferred.await() } }
        if (jsResult.isEmpty() || jsResult == "\"\"") {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "WebView 中未找到 dataUrl"))
            return ExpressResult(nu, false, "WebView页面中未找到dataUrl", null)
        }
        val dataUrl = jsResult.replace("\\/", "/").replace("&amp;", "&").trim('"')
        LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "dataUrl 提取成功"))
        return try {
            val apiUrl = "$dataUrl&nu=${URLEncoder.encode(nu, "UTF-8")}" +
                (if (mobile.isNotEmpty()) "&phone=${URLEncoder.encode(mobile, "UTF-8")}" else "")
            val req = okhttp3.Request.Builder().url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; PHN110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", baiduUrl).get().build()
            val resp = tracker.okClient.newCall(req).execute()
            val body = resp.body?.string() ?: return ExpressResult(nu, false, "API响应为空", null).also {
                LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "API 响应为空"))
            }
            if (!resp.isSuccessful) {
                LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "API HTTP ${resp.code}: ${body.take(200)}"))
                return ExpressResult(nu, false, "API HTTP ${resp.code}: ${body.take(200)}", null)
            }
            tracker.parseBaiduJson(nu, body)
        } catch (e: Exception) {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "API请求异常: ${e::class.simpleName}: ${e.message}"))
            ExpressResult(nu, false, "API请求失败: ${e::class.simpleName}: ${e.message}", null)
        }
    }
}
