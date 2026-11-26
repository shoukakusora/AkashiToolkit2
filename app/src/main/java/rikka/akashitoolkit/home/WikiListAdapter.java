package rikka.akashitoolkit.home;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.BaseRecyclerAdapter;
import rikka.akashitoolkit.utils.IntentUtils;
import rikka.akashitoolkit.utils.Utils;
import rikka.akashitoolkit.utils.WikiTextUtils;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Adapter for one Home.json column (game_info / updates / seasonal).
 */
public class WikiListAdapter extends BaseRecyclerAdapter<WikiListAdapter.ViewHolder, HomeData.WikiEntry> {

    public WikiListAdapter() {
        setHasStableIds(false);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wiki_entry, parent, false);
        return new ViewHolder(itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements IBindViewHolder<HomeData.WikiEntry> {

        public TextView mTitle;
        public TextView mContent;
        public ImageView mImage;

        public ViewHolder(View itemView) {
            super(itemView);

            mTitle = (TextView) itemView.findViewById(R.id.text_wiki_title);
            mContent = (TextView) itemView.findViewById(R.id.text_wiki_content);
            mImage = (ImageView) itemView.findViewById(R.id.image_wiki);
        }

        @Override
        public void bind(final HomeData.WikiEntry data, int position) {
            if (data == null) {
                return;
            }

            mTitle.setText(TextUtils.isEmpty(data.title) ? "" : data.title);

            String content = WikiTextUtils.format(data.text);
            mContent.setText(content);
            mContent.setVisibility(TextUtils.isEmpty(content) ? View.GONE : View.VISIBLE);

            List<String> images = data.getImages();
            String url = images.size() > 0 ? Utils.getKCWikiFileUrl(images.get(0)) : null;
            mImage.setVisibility(View.VISIBLE);
            if (url != null) {
                Glide.with(mImage.getContext())
                        .load(Utils.getGlideUrl(url))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .crossFade()
                        .into(mImage);
            } else {
                // 页面本身没有立绘时用占位图，保证卡片有缩略图区域
                Glide.clear(mImage);
                mImage.setImageResource(R.drawable.home_thumb_placeholder);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String link = data.link;
                    if (TextUtils.isEmpty(link)) {
                        String keyword = TextUtils.isEmpty(data.title) ? "" : data.title;
                        link = "https://zh.kcwiki.cn/wiki/Special:%E6%90%9C%E7%B4%A2?search="
                                + android.net.Uri.encode(keyword);
                    }
                    try {
                        IntentUtils.openUrl(v.getContext(), link);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(v.getContext(), "无法打开链接",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}
