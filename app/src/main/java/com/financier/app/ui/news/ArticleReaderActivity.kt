package com.financier.app.ui.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.financier.app.common.AppAudio
import com.financier.app.common.AppThemePreferences
import com.financier.app.common.LocaleHelper
import com.financier.app.databinding.ActivityArticleReaderBinding

class ArticleReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArticleReaderBinding
    private var articleUrl: String = ""
    private var articleTitle: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyCurrentLocale(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemePreferences.apply(this)

        binding = ActivityArticleReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        articleUrl = intent.getStringExtra(EXTRA_URL) ?: "https://vnexpress.net/kinh-doanh"
        articleTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Bản Tin Tài Chính"
        val articleSource = intent.getStringExtra(EXTRA_SOURCE) ?: "Tin tức thị trường"

        binding.toolbar.title = articleSource
        binding.toolbar.subtitle = try {
            Uri.parse(articleUrl).host ?: articleUrl
        } catch (_: Exception) {
            articleUrl
        }

        binding.toolbar.setNavigationOnClickListener {
            AppAudio.playClick(this)
            finish()
        }

        binding.btnShareArticle.setOnClickListener {
            AppAudio.playClick(this)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, articleTitle)
                putExtra(Intent.EXTRA_TEXT, "$articleTitle\n\n$articleUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài viết"))
        }

        binding.btnOpenBrowser.setOnClickListener {
            AppAudio.playClick(this)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl))
                startActivity(intent)
            } catch (_: Exception) {
            }
        }

        setupWebView()
        binding.webView.loadUrl(articleUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress >= 100) {
                    binding.progressBar.visibility = View.GONE
                } else {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_URL = "article_url"
        const val EXTRA_TITLE = "article_title"
        const val EXTRA_SOURCE = "article_source"

        fun open(context: Context, url: String, title: String, source: String = "Tin tức") {
            val intent = Intent(context, ArticleReaderActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SOURCE, source)
            }
            context.startActivity(intent)
        }
    }
}
