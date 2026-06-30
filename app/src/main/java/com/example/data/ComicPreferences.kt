package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ComicItem(val title: String, val url: String, val timestamp: Long = System.currentTimeMillis())

class ComicPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("comic_prefs", Context.MODE_PRIVATE)

    fun getBookmarks(): List<ComicItem> {
        val jsonStr = prefs.getString("bookmarks", "[]") ?: "[]"
        return parseJsonList(jsonStr)
    }

    fun addBookmark(title: String, url: String) {
        val list = getBookmarks().toMutableList()
        // Prevent duplicate URLs
        list.removeAll { it.url == url }
        list.add(0, ComicItem(title, url))
        prefs.edit().putString("bookmarks", toJsonString(list)).apply()
    }

    fun removeBookmark(url: String) {
        val list = getBookmarks().toMutableList()
        list.removeAll { it.url == url }
        prefs.edit().putString("bookmarks", toJsonString(list)).apply()
    }

    fun isBookmarked(url: String): Boolean {
        return getBookmarks().any { it.url == url }
    }

    fun getHistory(): List<ComicItem> {
        val jsonStr = prefs.getString("history", "[]") ?: "[]"
        return parseJsonList(jsonStr)
    }

    fun addHistory(title: String, url: String) {
        val list = getHistory().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, ComicItem(title, url))
        // Limit history to 50 items
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        prefs.edit().putString("history", toJsonString(list)).apply()
    }

    fun clearHistory() {
        prefs.edit().putString("history", "[]").apply()
    }

    fun hasShownVpnNotice(): Boolean {
        return prefs.getBoolean("has_shown_vpn_notice", false)
    }

    fun setVpnNoticeShown() {
        prefs.edit().putBoolean("has_shown_vpn_notice", true).apply()
    }

    private fun parseJsonList(jsonStr: String): List<ComicItem> {
        val list = mutableListOf<ComicItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ComicItem(
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun toJsonString(list: List<ComicItem>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("timestamp", item.timestamp)
            arr.put(obj)
        }
        return arr.toString()
    }
}
