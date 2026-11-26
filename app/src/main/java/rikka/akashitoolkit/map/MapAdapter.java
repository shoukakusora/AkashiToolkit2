package rikka.akashitoolkit.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.BaseRecyclerAdapter;
import rikka.akashitoolkit.model.Map;
import rikka.akashitoolkit.staticdata.MapList;
import rikka.akashitoolkit.ui.BaseItemDisplayActivity;
import rikka.akashitoolkit.utils.Utils;

/**
 * Created by Rikka on 2016/4/9.
 */
public class MapAdapter extends BaseRecyclerAdapter<MapViewHolder, Map> {

    private static final String ROUTE_HEADER_FORMAT = "分歧点 %s";

    private Context mContext;
    private int mType;

    /**
     * Ids (sea * 10 + area) of the maps which are currently expanded.
     * Default is collapsed, so nothing is in the set until the user taps a card.
     */
    private final Set<Integer> mExpandedItemIds = new HashSet<>();

    public MapAdapter(final Context context, final int type) {
        mContext = context;
        mType = type;

        rebuildDataList();
    }

    @Override
    public MapViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_map, parent, false);
        return new MapViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final MapViewHolder holder, int position) {
        final Map item = getItem(position);
        if (item == null) {
            return;
        }

        final int itemId = item.getSea() * 10 + item.getArea();
        final boolean expanded = mExpandedItemIds.contains(itemId);

        holder.mTitle.setText(item.getMap());

        Spanned htmlDescription = Html.fromHtml(format(item));
        String descriptionWithOutExtraSpace = htmlDescription.toString().trim();
        holder.mTextView.setText(htmlDescription.subSequence(0, descriptionWithOutExtraSpace.length()));

        // Content is built lazily: only expanded cards load images and build tables.
        if (expanded) {
            bindDetails(holder, item);
        } else {
            clearDetails(holder);
        }

        // 用显隐代替高度动画：内容（图片/表格）是动态添加的，动画时测不到高度会显示为 0
        if (holder.mDetailContainer != null) {
            holder.mDetailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        holder.mTitle.setTypeface(Typeface.defaultFromStyle(expanded ? Typeface.BOLD : Typeface.NORMAL));

        holder.mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map mapItem = getItem(holder.getAdapterPosition());
                if (mapItem == null) {
                    return;
                }

                Intent intent = new Intent(mContext, MapActivity.class);
                intent.putExtra(MapActivity.EXTRA_ITEM_ID, mapItem.getSea() * 10 + mapItem.getArea());
                intent.putExtra(MapActivity.EXTRA_ITEM_NAME, mapItem.getMap());

                BaseItemDisplayActivity.start(mContext, intent);
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean expand = !mExpandedItemIds.contains(itemId);

                if (expand) {
                    mExpandedItemIds.add(itemId);
                    bindDetails(holder, item);
                } else {
                    mExpandedItemIds.remove(itemId);
                }

                if (holder.mDetailContainer != null) {
                    holder.mDetailContainer.setVisibility(expand ? View.VISIBLE : View.GONE);
                }
                holder.mTitle.setTypeface(Typeface.defaultFromStyle(expand ? Typeface.BOLD : Typeface.NORMAL));
            }
        });
    }

    /* ------------------------------------------------------------------
     * Expanded card content
     * ------------------------------------------------------------------ */

    private void bindDetails(MapViewHolder holder, Map item) {
        View itemView = holder.itemView;
        Context context = itemView.getContext();

        Map.ImagesEntity images = item.getImages();

        // 图号（如 1-1），images 缺失时按 wiki 命名规则兜底
        String mapNo = item.getMap() == null ? "" : item.getMap().split(" ")[0];
        String bannerName = images == null || TextUtils.isEmpty(images.getBanner())
                ? (mapNo.isEmpty() ? null : "MapBannerHD" + mapNo + ".png")
                : images.getBanner();
        String mapName = images == null || TextUtils.isEmpty(images.getMap())
                ? (mapNo.isEmpty() ? null : "MapHD" + mapNo + ".png")
                : images.getMap();

        bindImage(context, (ImageView) itemView.findViewById(R.id.image_map_banner), bannerName);
        bindImage(context, (ImageView) itemView.findViewById(R.id.image_map), mapName);

        LinearLayout routeContainer = (LinearLayout) itemView.findViewById(R.id.route_container);
        if (routeContainer == null) {
            return;
        }

        routeContainer.removeAllViews();
        bindRoutes(context, routeContainer, item.getRoutes());
    }

    private void clearDetails(MapViewHolder holder) {
        View itemView = holder.itemView;

        clearImage((ImageView) itemView.findViewById(R.id.image_map_banner));
        clearImage((ImageView) itemView.findViewById(R.id.image_map));

        // Keeping stale tables on a collapsed card would still cost a measure pass.
        LinearLayout routeContainer = (LinearLayout) itemView.findViewById(R.id.route_container);
        if (routeContainer != null) {
            routeContainer.removeAllViews();
        }
    }

    private void bindImage(Context context, ImageView imageView, String fileName) {
        if (imageView == null) {
            return;
        }

        String url = TextUtils.isEmpty(fileName) ? null : Utils.getKCWikiFileUrl(fileName);
        if (url == null) {
            clearImage(imageView);
            return;
        }

        imageView.setVisibility(View.VISIBLE);
        Glide.with(context)
                .load(Utils.getGlideUrl(url))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                .crossFade()
                .into(imageView);
    }

    private void clearImage(ImageView imageView) {
        if (imageView == null) {
            return;
        }

        Glide.clear(imageView);
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.GONE);
    }

    private void bindRoutes(Context context, LinearLayout container, List<Map.RouteEntity> routes) {
        if (routes == null || routes.isEmpty()) {
            return;
        }

        for (Map.RouteEntity route : routes) {
            if (route == null) {
                continue;
            }

            TextView header = new TextView(context);
            header.setText(String.format(ROUTE_HEADER_FORMAT,
                    route.getPoint() == null ? "" : route.getPoint()));
            header.setTextSize(16);
            header.setTypeface(Typeface.DEFAULT_BOLD);

            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = Utils.dpToPx(8);
            container.addView(header, headerParams);

            if (route.getNotes() != null) {
                for (String note : route.getNotes()) {
                    if (TextUtils.isEmpty(note)) {
                        continue;
                    }

                    TextView noteView = new TextView(context);
                    noteView.setText(note);
                    noteView.setTextSize(14);
                    noteView.setLineSpacing(Utils.dpToPx(2), 1);

                    LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    container.addView(noteView, noteParams);
                }
            }

            if (route.getTables() != null) {
                for (Map.TableEntity table : route.getTables()) {
                    if (table == null) {
                        continue;
                    }
                    addTable(context, container, table);
                }
            }
        }
    }

    private void addTable(Context context, LinearLayout container, Map.TableEntity table) {
        List<List<String>> rows = table.getRows();
        if (rows == null || rows.isEmpty()) {
            return;
        }

        int padding = Utils.dpToPx(4);

        TableLayout tableLayout = new TableLayout(context);
        tableLayout.setStretchAllColumns(true);

        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }

            TableRow tableRow = new TableRow(context);
            for (String cell : row) {
                TextView cellView = new TextView(context);
                cellView.setText(cell == null ? "" : cell);
                cellView.setTextSize(14);
                cellView.setPadding(padding, padding, padding, padding);
                cellView.setSingleLine(false);
                tableRow.addView(cellView);
            }
            tableLayout.addView(tableRow);
        }

        if (tableLayout.getChildCount() == 0) {
            return;
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Utils.dpToPx(4);
        container.addView(tableLayout, params);
    }

    /* ------------------------------------------------------------------
     * Plain text description (shown below the images / route tables)
     * ------------------------------------------------------------------ */

    @SuppressLint("DefaultLocale")
    private String format(Map item) {
        StringBuilder sb = new StringBuilder();

        List<List<Integer>> airforce = item.getAirforce() == null
                ? Collections.<List<Integer>>emptyList() : item.getAirforce();
        List<String> recommend = item.getRecommend() == null
                ? Collections.<String>emptyList() : item.getRecommend();
        List<String> condition = item.getCondition() == null
                ? Collections.<String>emptyList() : item.getCondition();
        List<String> other = item.getOther() == null
                ? Collections.<String>emptyList() : item.getOther();

        boolean otherAtLast = airforce.size() > 1 && other.size() == 1;
        for (int id = 0; id < airforce.size(); id++) {
            List<Integer> air = airforce.get(id);
            boolean haveAir = air != null && air.size() > 2
                    && air.get(0) != null && air.get(1) != null && air.get(2) != null
                    && air.get(0) != 0 && air.get(1) != 0 && air.get(2) != 0;

            if (haveAir) {
                sb.append(String.format("<b>敌制空 / 优势 / 确保</b><br>%d / %d / %d<br/>",
                        air.get(0),
                        air.get(1),
                        air.get(2)));
            }

            sb.append(String.format("<b>推荐配置</b><br>%s<br/><b>带路条件</b><br/>%s",
                    getOrEmpty(recommend, id),
                    getOrEmpty(condition, id)));

            if (!otherAtLast)
                sb.append(String.format("<br/><b>说明</b><br/>%s", getOrEmpty(other, id)));

            if (id < airforce.size() - 1)
                sb.append("<p/>");
        }

        if (otherAtLast)
            sb.append(String.format("<p/><b>说明</b><br/>%s", other.get(0)));

        return sb.toString();
    }

    private static String getOrEmpty(List<String> list, int index) {
        if (list == null || index < 0 || index >= list.size() || list.get(index) == null) {
            return "";
        }
        return list.get(index);
    }

    @Override
    public void rebuildDataList() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                List<Map> data = MapList.get(mContext);

                for (Map item :
                        data) {
                    if (item.getSea() == mType) {
                        addItem(item.getSea() * 10 + item.getArea(), 0, item);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                notifyDataSetChanged();
            }
        }.execute();
    }
}
