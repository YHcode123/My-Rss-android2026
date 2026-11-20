package com.example.rssreader.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.rssreader.R
import com.example.rssreader.core.data.ArticleWithFeed
import com.example.rssreader.core.util.TimeUtil

/**
 * 列表项渲染规则（一眼能看出已读未读）：
 *  - 未读：左侧蓝点可见 + 标题加粗深色
 *  - 已读：蓝点 INVISIBLE 占位（保持左对齐不跳动）+ 标题常规灰色
 *  - 星标：实心琥珀色图标，否则空心灰色
 */
class ArticleAdapter(
    private val onOpen: (ArticleWithFeed) -> Unit,
    private val onToggleStar: (ArticleWithFeed) -> Unit,
    private val onLongPress: (View, ArticleWithFeed) -> Unit
) : ListAdapter<ArticleWithFeed, ArticleAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val dot: View = itemView.findViewById(R.id.unread_dot)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val summary: TextView = itemView.findViewById(R.id.summary)
        private val meta: TextView = itemView.findViewById(R.id.meta)
        private val star: ImageButton = itemView.findViewById(R.id.star)

        fun bind(item: ArticleWithFeed) {
            val article = item.article
            val context = itemView.context

            title.text = article.title

            val hasSummary = !article.summary.isNullOrBlank()
            summary.visibility = if (hasSummary) View.VISIBLE else View.GONE
            summary.text = article.summary.orEmpty()

            val time = if (article.pubDate > 0) {
                TimeUtil.relative(article.pubDate)
            } else {
                "时间未知"
            }
            meta.text = "${item.feedTitle}　·　$time"

            if (article.isRead) {
                dot.visibility = View.INVISIBLE
                title.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                title.setTypeface(null, Typeface.NORMAL)
            } else {
                dot.visibility = View.VISIBLE
                title.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                title.setTypeface(null, Typeface.BOLD)
            }

            star.setImageResource(
                if (article.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            itemView.setOnClickListener { onOpen(item) }
            itemView.setOnLongClickListener { view ->
                onLongPress(view, item)
                true
            }
            star.setOnClickListener { onToggleStar(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ArticleWithFeed>() {
        override fun areItemsTheSame(oldItem: ArticleWithFeed, newItem: ArticleWithFeed): Boolean =
            oldItem.article.id == newItem.article.id

        override fun areContentsTheSame(oldItem: ArticleWithFeed, newItem: ArticleWithFeed): Boolean =
            oldItem.article == newItem.article && oldItem.feedTitle == newItem.feedTitle
    }
}
