package rikka.akashitoolkit.map;

import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import rikka.akashitoolkit.R;
import android.widget.LinearLayout;

/**
 * Created by Rikka on 2016/8/7.
 */
public class MapViewHolder extends RecyclerView.ViewHolder {

    public LinearLayout mDetailContainer;
    public TextView mTitle;
    public TextView mTextView;
    public Button mButton;

    public MapViewHolder(View itemView) {
        super(itemView);

        mDetailContainer = (LinearLayout) itemView.findViewById(R.id.detail_container);
        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mTextView = (TextView) itemView.findViewById(android.R.id.content);
        mButton = (Button) itemView.findViewById(android.R.id.button1);
    }
}
