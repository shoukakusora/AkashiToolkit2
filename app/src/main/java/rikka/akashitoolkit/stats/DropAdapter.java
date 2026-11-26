package rikka.akashitoolkit.stats;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.BaseRecyclerAdapter;
import rikka.akashitoolkit.model.DropStat;
import rikka.akashitoolkit.staticdata.DropList;
import rikka.akashitoolkit.utils.Utils;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Adapter of the "掉落" tab. One card per map; every node becomes one TextView
 * ("A：吹雪、睦月、…") added into the card's container.
 */
public class DropAdapter extends BaseRecyclerAdapter<DropAdapter.ViewHolder, DropStat> {

    private final Context mContext;

    public DropAdapter(Context context) {
        mContext = context;
        setHasStableIds(false);
        rebuildDataList();
    }

    private String mQuery = "";

    public void setQuery(String query) {
        mQuery = query == null ? "" : query.trim();
        rebuildDataList();
    }

    @Override
    public void rebuildDataList() {
        List<DropStat> list = DropList.get(mContext);
        if (list == null || mQuery.isEmpty()) {
            setItemList(list);
            return;
        }
        String q = mQuery.toLowerCase();
        List<DropStat> out = new ArrayList<>();
        for (DropStat stat : list) {
            if (stat == null) {
                continue;
            }
            boolean match = stat.getMap() != null && stat.getMap().toLowerCase().contains(q);
            if (!match && stat.getNodes() != null) {
                for (DropStat.Node node : stat.getNodes()) {
                    if (node == null || node.getShips() == null) {
                        continue;
                    }
                    for (String ship : node.getShips()) {
                        if (ship != null && ship.toLowerCase().contains(q)) {
                            match = true;
                            break;
                        }
                    }
                    if (match) {
                        break;
                    }
                }
            }
            if (match) {
                out.add(stat);
            }
        }
        setItemList(out);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_drop, parent, false);
        return new ViewHolder(itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements IBindViewHolder<DropStat> {

        public final TextView mMap;
        public final LinearLayout mContainer;

        public ViewHolder(View itemView) {
            super(itemView);

            mMap = (TextView) itemView.findViewById(R.id.text_map);
            mContainer = (LinearLayout) itemView.findViewById(R.id.container_nodes);
        }

        @Override
        public void bind(DropStat data, int position) {
            if (data == null) {
                return;
            }

            // This holder is recycled: drop the views created for the previous map.
            mContainer.removeAllViews();

            mMap.setText(TextUtils.isEmpty(data.getMap()) ? "" : data.getMap());

            List<DropStat.Node> nodes = data.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                return;
            }

            Context context = mContainer.getContext();
            for (DropStat.Node node : nodes) {
                if (node == null) {
                    continue;
                }

                TextView textView = new TextView(context);
                textView.setText(safe(node.getName()) + "：" + joinShips(node.getShips()));
                textView.setTextSize(14);
                textView.setLineSpacing(Utils.dpToPx(2), 1);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = Utils.dpToPx(2);
                mContainer.addView(textView, params);
            }
        }

        private static String joinShips(List<String> ships) {
            if (ships == null || ships.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (String ship : ships) {
                if (TextUtils.isEmpty(ship)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("、");
                }
                builder.append(ship);
            }
            return builder.toString();
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
