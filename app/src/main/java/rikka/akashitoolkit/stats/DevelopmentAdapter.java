package rikka.akashitoolkit.stats;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.BaseRecyclerAdapter;
import rikka.akashitoolkit.model.Development;
import rikka.akashitoolkit.staticdata.DevelopmentList;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Adapter of the "开发公式" tab. One card per Development.json entry.
 */
public class DevelopmentAdapter extends BaseRecyclerAdapter<DevelopmentAdapter.ViewHolder, Development> {

    private final Context mContext;
    private List<Development> mAll = new ArrayList<>();
    private String mQuery = "";

    public DevelopmentAdapter(Context context) {
        mContext = context;
        setHasStableIds(false);
        rebuildDataList();
    }

    @Override
    public void rebuildDataList() {
        mAll = DevelopmentList.get(mContext);
        filter();
    }

    public void setQuery(String query) {
        mQuery = query == null ? "" : query.trim();
        filter();
    }

    private void filter() {
        if (mAll == null) {
            setItemList(new ArrayList<Development>());
            return;
        }
        if (mQuery.isEmpty()) {
            setItemList(new ArrayList<>(mAll));
            return;
        }
        String q = mQuery.toLowerCase();
        List<Development> out = new ArrayList<>();
        for (Development item : mAll) {
            if (item == null) {
                continue;
            }
            String text = (item.getTarget() + " " + item.getShip() + " " + item.getResult()
                    + " " + item.getExtra() + " " + item.getExtraResult() + " "
                    + item.getFuel() + "/" + item.getAmmo() + "/" + item.getSteel() + "/"
                    + item.getBauxite()).toLowerCase();
            if (text.contains(q)) {
                out.add(item);
            }
        }
        setItemList(out);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_development, parent, false);
        return new ViewHolder(itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements IBindViewHolder<Development> {

        public final TextView mTarget;
        public final TextView mShip;
        public final TextView mResource;
        public final TextView mResult;
        public final TextView mExtra;
        public final TextView mExtraResult;

        public ViewHolder(View itemView) {
            super(itemView);

            mTarget = (TextView) itemView.findViewById(R.id.text_target);
            mShip = (TextView) itemView.findViewById(R.id.text_ship);
            mResource = (TextView) itemView.findViewById(R.id.text_resource);
            mResult = (TextView) itemView.findViewById(R.id.text_result);
            mExtra = (TextView) itemView.findViewById(R.id.text_extra);
            mExtraResult = (TextView) itemView.findViewById(R.id.text_extra_result);
        }

        @Override
        public void bind(Development data, int position) {
            if (data == null) {
                return;
            }

            mTarget.setText("目标：" + safe(data.getTarget()));

            bindOptional(mShip, "秘书舰：", data.getShip());

            mResource.setText("资源：燃料 " + data.getFuel()
                    + " / 弹药 " + data.getAmmo()
                    + " / 钢铁 " + data.getSteel()
                    + " / 铝 " + data.getBauxite());

            bindOptional(mResult, "产物：", data.getResult());
            bindOptional(mExtra, "追加条件：", data.getExtra());
            bindOptional(mExtraResult, "追加产物：", data.getExtraResult());
        }

        private static void bindOptional(TextView view, String label, String value) {
            if (TextUtils.isEmpty(value)) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
                view.setText(label + value);
            }
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
