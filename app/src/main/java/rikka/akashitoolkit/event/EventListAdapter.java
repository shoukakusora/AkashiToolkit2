package rikka.akashitoolkit.event;

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
 * Adapter for the local Event.json list shown on the "活动" drawer page.
 */
public class EventListAdapter extends BaseRecyclerAdapter<EventListAdapter.ViewHolder, EventEntry> {

    public EventListAdapter() {
        setHasStableIds(false);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new ViewHolder(itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements IBindViewHolder<EventEntry> {

        public TextView mTitle;
        public TextView mDate;
        public TextView mContent;
        public ImageView mImage;

        public ViewHolder(View itemView) {
            super(itemView);

            mTitle = (TextView) itemView.findViewById(R.id.text_event_title);
            mDate = (TextView) itemView.findViewById(R.id.text_event_date);
            mContent = (TextView) itemView.findViewById(R.id.text_event_content);
            mImage = (ImageView) itemView.findViewById(R.id.image_event);
        }

        @Override
        public void bind(final EventEntry data, int position) {
            if (data == null) {
                return;
            }

            mTitle.setText(TextUtils.isEmpty(data.title) ? "" : data.title);

            if (TextUtils.isEmpty(data.date)) {
                mDate.setVisibility(View.GONE);
            } else {
                mDate.setVisibility(View.VISIBLE);
                mDate.setText(data.date);
            }

            String content = WikiTextUtils.format(data.text);
            mContent.setText(content);
            mContent.setVisibility(TextUtils.isEmpty(content) ? View.GONE : View.VISIBLE);

            List<String> images = data.getImages();
            String url = null;
            if (images.size() > 0) {
                String fileName = images.get(0);
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) {
                    fileName = fileName.substring(0, dot);
                }
                // 缩略图随 APK 内置（assets/homeimg），无需联网
                url = rikka.akashitoolkit.support.ImageStore.resolve(
                        mImage.getContext(), "homeimg", fileName + ".jpg");
            }
            if (url != null) {
                mImage.setVisibility(View.VISIBLE);
                Glide.with(mImage.getContext())
                        .load(Utils.getGlideUrl(url))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .crossFade()
                        .into(mImage);
            } else {
                Glide.clear(mImage);
                mImage.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IntentUtils.openUrl(v.getContext(), data.link);
                }
            });
        }
    }
}
