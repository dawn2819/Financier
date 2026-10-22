package com.financier.app.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.financier.app.R
import com.financier.app.common.AppAudio
import com.financier.app.data.news.FinancialNewsManager
import com.financier.app.data.news.NewsItem
import com.financier.app.databinding.FragmentFinancialNewsFeedBinding
import kotlinx.coroutines.launch

class FinancialNewsFeedFragment : Fragment() {

    private var _binding: FragmentFinancialNewsFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var newsAdapter: NewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFinancialNewsFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            AppAudio.playClick(requireContext())
            findNavController().navigateUp()
        }

        setupRecyclerView()
        setupSwipeRefresh()
        loadNews(forceRefresh = false)
    }

    private fun setupRecyclerView() {
        newsAdapter = NewsAdapter { item ->
            AppAudio.playClick(requireContext())
            ArticleReaderActivity.open(
                context = requireContext(),
                url = item.link,
                title = item.title,
                source = item.source
            )
        }

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadNews(forceRefresh = true)
        }
    }

    private fun loadNews(forceRefresh: Boolean) {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.progressLoading.visibility = View.VISIBLE
        }
        binding.tvEmptyNews.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val newsList = FinancialNewsManager.getNews(requireContext(), forceRefresh = forceRefresh)
                newsAdapter.submitList(newsList)

                binding.tvEmptyNews.visibility = if (newsList.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class NewsAdapter(
        private val onItemClick: (NewsItem) -> Unit
    ) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

        private val items = mutableListOf<NewsItem>()

        fun submitList(newItems: List<NewsItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news_card, parent, false)
            return NewsViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class NewsViewHolder(
            itemView: View,
            private val onItemClick: (NewsItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val tvSource: TextView = itemView.findViewById(R.id.tv_news_source)
            private val tvTime: TextView = itemView.findViewById(R.id.tv_news_time)
            private val tvTitle: TextView = itemView.findViewById(R.id.tv_news_title)
            private val tvDesc: TextView = itemView.findViewById(R.id.tv_news_desc)

            fun bind(item: NewsItem) {
                tvSource.text = item.source
                tvTime.text = item.pubDate
                tvTitle.text = item.title
                tvDesc.text = item.description

                itemView.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }
}