package com.financier.app.data.news

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class NewsItem(
    val title: String,
    val description: String,
    val link: String,
    val source: String,
    val pubDate: String,
    val timestamp: Long = System.currentTimeMillis()
)

object FinancialNewsManager {
    private const val PREFS_NAME = "financier_financial_news"
    private const val KEY_NEWS_CACHE = "cached_news_json"
    private const val KEY_LAST_SYNC = "last_news_sync_timestamp"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val RSS_FEEDS = listOf(
        Pair("VnExpress Kinh Doanh", "https://vnexpress.net/rss/kinh-doanh.rss"),
        Pair("Dân Trí Kinh Doanh", "https://dantri.com.vn/rss/kinh-doanh.rss")
    )

    private val FALLBACK_NEWS = listOf(
        NewsItem(
            title = "VN-Index hồi phục mạnh mẽ, dòng tiền ngoại đổ vào cổ phiếu ngân hàng",
            description = "Thị trường chứng khoán Việt Nam ghi nhận thanh khoản tích cực khi các nhóm ngành tài chính, ngân hàng và bán lẻ đồng loạt khởi sắc.",
            link = "https://vnexpress.net/kinh-doanh",
            source = "VnExpress",
            pubDate = "8:00 AM Hôm nay"
        ),
        NewsItem(
            title = "Giá vàng và tỷ giá USD sáng nay: Xu hướng bình ổn nhờ chính sách tiền tệ linh hoạt",
            description = "Ngân hàng Nhà nước tiếp tục theo dõi sát sao diễn biến tỷ giá và thị trường kim loại quý để duy trì ổn định kinh tế vĩ mô.",
            link = "https://dantri.com.vn/kinh-doanh.htm",
            source = "Dân Trí",
            pubDate = "8:00 AM Hôm nay"
        ),
        NewsItem(
            title = "EVN đẩy mạnh thanh toán điện tử không dùng tiền mặt và tối ưu hóa chi phí điện sinh hoạt",
            description = "Tập đoàn Điện lực Việt Nam khuyến khích người dân theo dõi chỉ số điện qua ứng dụng tài chính để chủ động kế hoạch chi tiêu gia đình.",
            link = "https://dantri.com.vn/kinh-doanh.htm",
            source = "Dân Trí",
            pubDate = "8:00 AM Hôm nay"
        ),
        NewsItem(
            title = "Xu hướng ví điện tử và thanh toán số tại Việt Nam bùng nổ trong quý này",
            description = "Thanh toán quét mã QR và chuyển khoản P2P nhanh 24/7 tiếp tục chiếm tỷ trọng áp đảo trong các giao dịch thường nhật của người tiêu dùng.",
            link = "https://vnexpress.net/kinh-doanh",
            source = "VnExpress",
            pubDate = "8:00 AM Hôm nay"
        )
    )

    suspend fun getNews(context: Context, forceRefresh: Boolean = false): List<NewsItem> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()
        val cacheValid = (now - lastSync) < 2 * 60 * 60 * 1000L // 2 hours freshness

        if (!forceRefresh && cacheValid) {
            val cached = loadFromCache(prefs)
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }

        try {
            val fetched = fetchLiveNews()
            if (fetched.isNotEmpty()) {
                saveToCache(prefs, fetched)
                return@withContext fetched
            }
        } catch (_: Exception) {
            // Network failure: fallback to cache or defaults
        }

        val cached = loadFromCache(prefs)
        if (cached.isNotEmpty()) cached else FALLBACK_NEWS
    }

    fun getCachedHeadlineSync(context: Context): NewsItem {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = loadFromCache(prefs)
        return if (cached.isNotEmpty()) cached.first() else FALLBACK_NEWS.first()
    }

    private fun fetchLiveNews(): List<NewsItem> {
        val results = mutableListOf<NewsItem>()
        for ((sourceName, url) in RSS_FEEDS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "FinancierApp/1.0 (Android; Mobile)")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val xmlBody = response.body?.string().orEmpty()
                    val parsed = parseRssXml(xmlBody, sourceName)
                    results.addAll(parsed)
                }
            } catch (_: Exception) {
                // Continue to next feed if one fails
            }
        }
        return if (results.isNotEmpty()) results.take(15) else emptyList()
    }

    private fun parseRssXml(xml: String, sourceName: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        if (xml.isBlank()) return items

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType

            var insideItem = false
            var title = ""
            var description = ""
            var link = ""
            var pubDate = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            insideItem = true
                            title = ""
                            description = ""
                            link = ""
                            pubDate = ""
                        } else if (insideItem) {
                            when {
                                tagName.equals("title", ignoreCase = true) -> {
                                    title = parser.nextText().cleanHtml()
                                }
                                tagName.equals("description", ignoreCase = true) -> {
                                    description = parser.nextText().cleanHtml()
                                }
                                tagName.equals("link", ignoreCase = true) -> {
                                    link = parser.nextText().trim()
                                }
                                tagName.equals("pubDate", ignoreCase = true) -> {
                                    pubDate = parser.nextText().trim()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            if (title.isNotBlank()) {
                                items.add(
                                    NewsItem(
                                        title = title,
                                        description = description,
                                        link = link,
                                        source = sourceName,
                                        pubDate = formatPubDate(pubDate)
                                    )
                                )
                            }
                            insideItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Ignore parse errors, return what was parsed
        }
        return items
    }

    private fun String.cleanHtml(): String {
        return this.replace(Regex("<.*?>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .trim()
    }

    private fun formatPubDate(rawDate: String): String {
        if (rawDate.isBlank()) {
            val nowStr = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date())
            return "Cập nhật lúc $nowStr"
        }
        return try {
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val date = inputFormat.parse(rawDate)
            if (date != null) {
                SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(date)
            } else {
                rawDate
            }
        } catch (_: Exception) {
            rawDate
        }
    }

    private fun saveToCache(prefs: android.content.SharedPreferences, items: List<NewsItem>) {
        try {
            val array = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("title", item.title)
                    put("description", item.description)
                    put("link", item.link)
                    put("source", item.source)
                    put("pubDate", item.pubDate)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            prefs.edit()
                .putString(KEY_NEWS_CACHE, array.toString())
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadFromCache(prefs: android.content.SharedPreferences): List<NewsItem> {
        val raw = prefs.getString(KEY_NEWS_CACHE, null) ?: return emptyList()
        val list = mutableListOf<NewsItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NewsItem(
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        link = obj.optString("link"),
                        source = obj.optString("source"),
                        pubDate = obj.optString("pubDate"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }
}
