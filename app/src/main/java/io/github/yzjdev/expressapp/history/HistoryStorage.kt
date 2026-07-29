package io.github.yzjdev.expressapp.history

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class HistoryStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("query_history", Context.MODE_PRIVATE)

    fun load(): List<QueryHistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<QueryHistoryItem>()
        for (i in 0 until arr.length()) {
            list.add(QueryHistoryItem.fromJson(arr.getJSONObject(i)))
        }
        return list.sortedByDescending { it.queryTime }
    }

    fun save(item: QueryHistoryItem) {
        val list = load().toMutableList()
        val existing = list.indexOfFirst { it.nu == item.nu }
        if (existing >= 0) list.removeAt(existing)
        list.add(0, item)
        if (list.size > MAX_COUNT) list.removeAt(list.lastIndex)
        val arr = JSONArray()
        for (h in list) {
            arr.put(h.toJson())
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun delete(nu: String) {
        val list = load().toMutableList()
        list.removeAll { it.nu == nu }
        val arr = JSONArray()
        for (h in list) {
            arr.put(h.toJson())
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history2"
        private const val MAX_COUNT = 20
    }
}
