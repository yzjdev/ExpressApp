package com.example.expressapp.history

import com.example.expressapp.tracker.ExpressData
import com.example.expressapp.tracker.ProvinceRouteAnalyzer
import com.example.expressapp.tracker.RouteAnalyzer
import com.example.expressapp.tracker.TraceItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class QueryHistoryItem(
    val nu: String,
    val queryTime: Long,
    val data: ExpressData
) {
    val timeFormatted: String
        get() = Instant.ofEpochMilli(queryTime)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    fun toJson(): JSONObject = JSONObject().apply {
        put("nu", nu)
        put("queryTime", queryTime)
        put("data", JSONObject().apply {
            put("companyName", data.companyName)
            put("companyCode", data.companyCode)
            put("tel", data.tel)
            put("status", data.status)
            put("statusCode", data.statusCode)
            put("from", data.from)
            put("to", data.to)
            put("latest", data.latest)
            put("delivered", data.delivered)
            put("statusTag", data.statusTag)
            put("traces", JSONArray().apply {
                data.traces.forEach { t ->
                    put(JSONObject().apply {
                        put("timestamp", t.timestamp)
                        put("desc", t.desc)
                    })
                }
            })
        })
    }

    companion object {
        private val routeAnalyzer: RouteAnalyzer = ProvinceRouteAnalyzer()

        fun fromJson(obj: JSONObject): QueryHistoryItem {
            val d = obj.getJSONObject("data")
            val traces = mutableListOf<TraceItem>()
            val arr = d.optJSONArray("traces")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    traces.add(TraceItem(t.optLong("timestamp", 0), t.optString("desc", "")))
                }
            }
            val statusTag = routeAnalyzer.statusTag(traces)
            val apiStatus = d.optString("status", "")
            val effectiveTag = when {
                apiStatus.contains("签收") || apiStatus.contains("已签收") || apiStatus.contains("本人签收") || apiStatus.contains("代签收") -> {
                    when {
                        apiStatus.contains("本人") -> "本人签收"
                        apiStatus.contains("代签") -> "代签收"
                        apiStatus.contains("代收点") || apiStatus.contains("驿站") -> "已签收（代收点）"
                        else -> "已签收"
                    }
                }
                statusTag.isNotEmpty() -> statusTag
                else -> ""
            }
            return QueryHistoryItem(
                nu = obj.optString("nu", ""),
                queryTime = obj.optLong("queryTime", 0),
                data = ExpressData(
                    companyName = d.optString("companyName", ""),
                    companyCode = d.optString("companyCode", ""),
                    tel = d.optString("tel", ""),
                    status = d.optString("status", ""),
                    statusCode = d.optString("statusCode", ""),
                    from = d.optString("from", ""),
                    to = d.optString("to", ""),
                    latest = d.optString("latest", ""),
                    traces = traces,
                    delivered = effectiveTag == "已签收" || effectiveTag == "本人签收" || effectiveTag == "代签收" || effectiveTag == "已签收（代收点）",
                    statusTag = effectiveTag
                )
            )
        }
    }
}
