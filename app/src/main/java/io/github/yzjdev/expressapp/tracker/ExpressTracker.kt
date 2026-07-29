package io.github.yzjdev.expressapp.tracker

import io.github.yzjdev.expressapp.log.LogBuffer
import io.github.yzjdev.expressapp.log.LogLevel
import io.github.yzjdev.expressapp.log.QueryLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

data class TraceItem(
    val timestamp: Long,
    val desc: String
) {
    val timeFormatted: String
        get() = Instant.ofEpochSecond(timestamp)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}

data class ExpressData(
    val companyName: String,
    val companyCode: String,
    val tel: String,
    val status: String,
    val statusCode: String,
    val from: String,
    val to: String,
    val latest: String,
    val traces: List<TraceItem>,
    val delivered: Boolean = false,
    val statusTag: String = ""
) {
    val isRefreshable: Boolean
        get() = !delivered && statusTag !in setOf(
            "已签收", "本人签收", "代签收", "已签收（代收点）", "已送达"
        )
}

data class ExpressResult(
    val nu: String,
    val found: Boolean,
    val msg: String,
    val data: ExpressData?
)

class ExpressTracker(
    okHttpClient: OkHttpClient? = null,
    private val routeAnalyzer: RouteAnalyzer = ProvinceRouteAnalyzer()
) {

    val okClient: OkHttpClient
    private val client: OkHttpClient

    init {
        val c = okHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        this.client = c
        this.okClient = c
    }

    companion object {
        private const val BAIDU_URL = "https://www.baidu.com/s"
        private const val UA = "Mozilla/5.0 (Linux; Android 13; PHN110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val SF_DETAIL_URL = "https://exiavs.smartapps.cn/pages/query/order-detail/waybill-detail/waybill-detail"

        private fun isSF(nu: String) = nu.uppercase().startsWith("SF")

        private val COMPANY_NAMES = mapOf(
            "yuantong" to "圆通速递",
            "zhongtong" to "中通快递",
            "yunda" to "韵达快递",
            "shentong" to "申通快递",
            "huitongkuaidi" to "百世快递",
            "tiantian" to "天天快递",
            "shunfeng" to "顺丰速运",
            "jd" to "京东快递",
            "debang" to "德邦快递",
            "youzheng" to "中国邮政",
            "ems" to "EMS",
            "zhaijisong" to "宅急送",
            "yousu" to "优速快递",
            "suer" to "速尔快递",
            "guotong" to "国通快递",
            "kuayue" to "跨越速运",
            "anneng" to "安能物流",
            "jtexpress" to "极兔速递",
            "jt" to "极兔速递",
            "fengwang" to "丰网速运",
            "zhima" to "芝麻开门",
            "rufeng" to "如风达",
        )
    }

    fun query(nu: String): ExpressResult = query(nu, "")

    fun query(nu: String, mobile: String): ExpressResult {
        val dataUrl = extractDataUrl(nu)
        if (dataUrl == null) {
            if (isSF(nu)) {
                val url = "$SF_DETAIL_URL?waybillno=${URLEncoder.encode(nu, "UTF-8")}&channel=baidu_alading" +
                    (if (mobile.isNotEmpty()) "&mobileCode=${URLEncoder.encode(mobile, "UTF-8")}" else "")
                LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "顺丰单号，跳转 WebView"))
                return ExpressResult(nu, false, url, null)
            }
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "百度搜索页未识别该运单号"))
            return ExpressResult(nu, false, "百度搜索页未识别该运单号", null)
        }
        if (!dataUrl.startsWith("http")) {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = dataUrl))
            return ExpressResult(nu, false, dataUrl, null)
        }
        val apiUrl = "$dataUrl&nu=${URLEncoder.encode(nu, "UTF-8")}" +
            (if (mobile.isNotEmpty()) "&phone=${URLEncoder.encode(mobile, "UTF-8")}" else "")
        val req = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", UA)
            .header("Referer", "$BAIDU_URL?wd=${URLEncoder.encode(nu, "UTF-8")}")
            .get()
            .build()
        return try {
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return ExpressResult(nu, false, "API无响应", null).also {
                LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "API 响应为空"))
            }
            parseBaiduJson(nu, body)
        } catch (e: Exception) {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "API请求失败: ${e.message}"))
            ExpressResult(nu, false, "API请求失败: ${e.message}", null)
        }
    }



    private fun extractDataUrl(nu: String): String? {
        return try {
            val url = URL("$BAIDU_URL?wd=${URLEncoder.encode(nu, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            conn.setRequestProperty("Upgrade-Insecure-Requests", "1")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val code = conn.responseCode
            if (code != 200) throw Exception("HTTP $code")
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val body = reader.readText()
            reader.close()
            val key = "\"dataUrl\":\""
            val i = body.indexOf(key)
            if (i < 0) throw Exception("no dataUrl, body[0..200]=${body.take(200)}")
            val start = i + key.length
            val end = body.indexOf('"', start)
            if (end < 0) throw Exception("no closing quote after dataUrl")
            body.substring(start, end)
                .replace("\\/", "/")
                .replace("&amp;", "&")
        } catch (e: Exception) {
            e.message
        }
    }

    fun parseBaiduJson(nu: String, json: String): ExpressResult {
        return try {
            val root = org.json.JSONObject(json)
            val code = root.optInt("status", -1)
            if (code != 0) {
                val msg = root.optString("msg", "查询失败").ifEmpty { "查询失败" }
                LogBuffer.add(QueryLog(level = LogLevel.WARN, nu = nu, message = "API 返回错误: $msg"))
                return ExpressResult(nu, false, msg, null)
            }
            val data = root.getJSONObject("data")
            val info = data.optJSONObject("info") ?: org.json.JSONObject()
            val com = data.optJSONObject("company")
            val traces = mutableListOf<TraceItem>()
            val ctx = info.optJSONArray("context")
            if (ctx != null) {
                for (i in 0 until ctx.length()) {
                    val item = ctx.getJSONObject(i)
                    traces.add(TraceItem(item.optLong("time", 0), item.optString("desc", "")))
                }
            }
            val from = routeAnalyzer.deriveFrom(traces)
            val to = routeAnalyzer.deriveTo(traces)
            val statusTag = routeAnalyzer.statusTag(traces)
            val apiCurrent = info.optString("current", "")
            val effectiveTag = when {
                apiCurrent.contains("签收") || apiCurrent.contains("已签收") || apiCurrent.contains("本人签收") || apiCurrent.contains("代签收") -> {
                    when {
                        apiCurrent.contains("本人") -> "本人签收"
                        apiCurrent.contains("代签") -> "代签收"
                        apiCurrent.contains("代收点") || apiCurrent.contains("驿站") -> "已签收（代收点）"
                        else -> "已签收"
                    }
                }
                statusTag.isNotEmpty() -> statusTag
                else -> ""
            }
            val delivered = effectiveTag == "已签收" || effectiveTag == "本人签收" || effectiveTag == "代签收" || effectiveTag == "已签收（代收点）"
            LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "查询成功: ${traces.size} 条轨迹, 状态=$effectiveTag"))
            ExpressResult(nu, true, "", ExpressData(
                companyName = COMPANY_NAMES[data.optString("com", "")] ?: com?.optString("fullname", "") ?: data.optString("fullname", ""),
                companyCode = data.optString("com", ""),
                tel = com?.optString("tel", "") ?: data.optString("tel", ""),
                status = info.optString("current", ""),
                statusCode = info.optString("currentStatus", ""),
                from = from,
                to = to,
                latest = info.optString("latest_progress", ""),
                traces = traces,
                delivered = delivered,
                statusTag = effectiveTag
            ))
        } catch (e: Exception) {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "JSON解析失败: ${e::class.simpleName}: ${e.message}"))
            ExpressResult(nu, false, "JSON解析失败: ${e::class.simpleName}: ${e.message}", null)
        }
    }

    fun parseLocalSfData(nu: String, text: String): ExpressResult {
        return try {
            val lines = text.replace("\\n", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val header = setOf("原始内容:", "***", "去寄件", "")
            val clean = lines.filter { it !in header }
            if (clean.size < 2) {
                LogBuffer.add(QueryLog(level = LogLevel.WARN, nu = nu, message = "顺丰本地数据格式无效: 行数不足"))
                return ExpressResult(nu, false, "本地数据格式无效，至少需要时间和日期", null)
            }
            val routeAnalyzer = ProvinceRouteAnalyzer()
            val traces = mutableListOf<TraceItem>()
            var currentDate = ""
            var currentTime = ""
            var pendingDesc: String? = null
            val timeRegex = Regex("""^(\d{2}:\d{2})$""")
            val dateRegex = Regex("""^(\d{4}[-/]\d{2}[-/]\d{2})$""")
            val singleCharStatus = Regex("^[\u4e00-\u9fa5]$")
            val sfStatusCodes = setOf("收", "快", "发", "停", "取", "签", "到")
            var i = 0
            while (i < clean.size) {
                val line = clean[i]
                val timeMatch = timeRegex.find(line)
                if (timeMatch != null) {
                    if (currentTime.isNotEmpty() && currentDate.isNotEmpty() && pendingDesc != null) {
                        traces.add(makeTraceItem(currentDate, currentTime, pendingDesc))
                    }
                    currentTime = timeMatch.groupValues[1]
                    pendingDesc = null
                    i++
                    continue
                }
                val dateMatch = dateRegex.find(line)
                if (dateMatch != null) {
                    currentDate = dateMatch.groupValues[1].replace("/", "-")
                    i++
                    continue
                }
                if (currentTime.isNotEmpty() && currentDate.isNotEmpty()) {
                    val isStatus = singleCharStatus.matches(line) && sfStatusCodes.contains(line) &&
                        i + 1 < clean.size && clean[i + 1].length > 1
                    if (isStatus) {
                        pendingDesc = "$line ${clean[i + 1]}"
                        i += 2
                        continue
                    }
                    if (pendingDesc == null) {
                        pendingDesc = line
                    } else {
                        pendingDesc += "\n" + line
                    }
                }
                i++
            }
            if (currentTime.isNotEmpty() && currentDate.isNotEmpty() && pendingDesc != null) {
                traces.add(makeTraceItem(currentDate, currentTime, pendingDesc))
            }
            if (traces.isEmpty()) {
                LogBuffer.add(QueryLog(level = LogLevel.WARN, nu = nu, message = "顺丰本地数据未提取到轨迹"))
                return ExpressResult(nu, false, "未能从本地数据中提取到任何物流轨迹", null)
            }
            val from = routeAnalyzer.deriveFrom(traces)
            val to = routeAnalyzer.deriveTo(traces)
            val statusTag = routeAnalyzer.statusTag(traces)
            val effectiveTag = when {
                statusTag.contains("签收") -> statusTag
                statusTag.isNotEmpty() -> statusTag
                traces.firstOrNull()?.desc?.contains("已派送成功") == true -> "已签收"
                traces.firstOrNull()?.desc?.startsWith("收 ") == true -> "已签收"
                traces.firstOrNull()?.desc?.startsWith("取 ") == true -> "已取件"
                traces.firstOrNull()?.desc?.startsWith("到 ") == true -> "已到达"
                traces.firstOrNull()?.desc?.startsWith("停 ") == true -> "已暂停"
                else -> "在途中"
            }
            val delivered = effectiveTag.contains("签收") || effectiveTag == "已送达"
            LogBuffer.add(QueryLog(level = LogLevel.INFO, nu = nu, message = "顺丰本地解析成功: ${traces.size} 条轨迹, 状态=$effectiveTag"))
            ExpressResult(nu, true, "", ExpressData(
                companyName = "顺丰速运",
                companyCode = "shunfeng",
                tel = "95338",
                status = effectiveTag,
                statusCode = "",
                from = from,
                to = to,
                latest = traces.firstOrNull()?.desc ?: "",
                traces = traces,
                delivered = delivered,
                statusTag = effectiveTag
            ))
        } catch (e: Exception) {
            LogBuffer.add(QueryLog(level = LogLevel.ERROR, nu = nu, message = "顺丰本地解析异常: ${e::class.simpleName}: ${e.message}"))
            ExpressResult(nu, false, "本地数据解析失败: ${e::class.simpleName}: ${e.message}", null)
        }
    }

    private fun makeTraceItem(date: String, time: String, desc: String): TraceItem {
        val epoch = try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            sdf.parse("$date $time")?.time?.div(1000) ?: 0L
        } catch (_: Exception) {
            0L
        }
        return TraceItem(epoch, desc)
    }
}
