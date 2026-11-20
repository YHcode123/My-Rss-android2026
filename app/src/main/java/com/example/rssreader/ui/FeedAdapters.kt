package com.example.rssreader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.rssreader.R
import com.example.rssreader.core.data.FeedWithCount
import com.example.rssreader.core.util.TimeUtil

/** 订阅源列表项：点进去看这个源的文章，长按可批量已读或删除。 */
class FeedAdapter(
    private val onClick: (FeedWithCount) -> Unit,
    private val onLongClick: (View, FeedWithCount) -> Unit
) : ListAdapter<FeedWithCount, FeedAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_feed, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.feed_title)
        private val url: TextView = itemView.findViewById(R.id.feed_url)
        private val meta: TextView = itemView.findViewById(R.id.feed_meta)

        fun bind(item: FeedWithCount) {
            title.text = item.title
            url.text = item.url
            val syncText = if (item.lastSync > 0) {
                "最后同步 ${TimeUtil.absolute(item.lastSync)}"
            } else {
                "从未同步"
            }
            meta.text = "${item.total} 篇 · 未读 ${item.unread} · $syncText"

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener { view ->
                onLongClick(view, item)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<FeedWithCount>() {
        override fun areItemsTheSame(oldItem: FeedWithCount, newItem: FeedWithCount): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FeedWithCount, newItem: FeedWithCount): Boolean =
            oldItem == newItem
    }
}

/** 订阅源管理页：多一个删除按钮。 */
class FeedManageAdapter(
    private val onDelete: (FeedWithCount) -> Unit
) : ListAdapter<FeedWithCount, FeedManageAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_feed_manage, parent, false)
        )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.feed_title)
        private val url: TextView = itemView.findViewById(R.id.feed_url)
        private val meta: TextView = itemView.findViewById(R.id.feed_meta)
        private val delete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(item: FeedWithCount) {
            title.text = item.title
            url.text = item.url
            val syncText = if (item.lastSync > 0) {
                "最后同步 ${TimeUtil.absolute(item.lastSync)}"
            } else {
                "从未同步"
            }
            meta.text = "${item.total} 篇 · 未读 ${item.unread} · $syncText"
            delete.setOnClickListener { onDelete(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<FeedWithCount>() {
        override fun areItemsTheSame(oldItem: FeedWithCount, newItem: FeedWithCount): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FeedWithCount, newItem: FeedWithCount): Boolean =
            oldItem == newItem
    }
}
