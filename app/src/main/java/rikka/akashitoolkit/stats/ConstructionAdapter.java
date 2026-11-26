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
import rikka.akashitoolkit.model.Construction;
import rikka.akashitoolkit.staticdata.ConstructionList;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/** 「建造公式」tab 的适配器。 */
public class ConstructionAdapter extends BaseRecyclerAdapter<ConstructionAdapter.ViewHolder, Construction> {

    private final Context mContext;
    private List<Construction> mAll = new ArrayList<>();
    private String mQuery = "";

    public ConstructionAdapter(Context context) {
        mContext = context;
        setHasStableIds(false);
        rebuildDataList();
    }

    @Override
    public void rebuildDataList() {
        mAll = ConstructionList.get(mContext);
        filter();
    }

    public void setQuery(String query) {
        mQuery = query == null ? "" : query.trim();
        filter();
    }

    private void filter() {
        if (mAll == null) {
            setItemList(new ArrayList<Construction>());
            return;
        }
        if (mQuery.isEmpty()) {
            setItemList(new ArrayList<>(mAll));
            return;
        }
        String q = mQuery.toLowerCase();
        List<Construction> out = new ArrayList<>();
        for (Construction item : mAll) {
            if (item == null) {
                continue;
            }
            String text = (item.getTarget() + " " + item.getNote() + " "
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
                .inflate(R.layout.card_construction, parent, false);
        return new ViewHolder(itemView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements IBindViewHolder<Construction> {

        public final TextView mTarget;
        public final TextView mResource;
        public final TextView mNote;

        public ViewHolder(View itemView) {
            super(itemView);
            mTarget = (TextView) itemView.findViewById(R.id.text_construction_target);
            mResource = (TextView) itemView.findViewById(R.id.text_construction_resource);
            mNote = (TextView) itemView.findViewById(R.id.text_construction_note);
        }

        @Override
        public void bind(Construction data, int position) {
            if (data == null) {
                return;
            }

            String target = data.getTarget();
            if (TextUtils.isEmpty(target)) {
                target = data.isLarge() ? "大型舰建造" : "普通建造";
            } else if (data.isLarge()) {
                target = "目标：" + target;
            }
            mTarget.setText(target);

            if (data.getFuel() == 0 && data.getAmmo() == 0
                    && data.getSteel() == 0 && data.getBauxite() == 0) {
                mResource.setVisibility(View.GONE);
            } else {
                mResource.setVisibility(View.VISIBLE);
                mResource.setText(String.format("资源：燃料 %d / 弹药 %d / 钢铁 %d / 铝 %d",
                        data.getFuel(), data.getAmmo(), data.getSteel(), data.getBauxite()));
            }

            String note = data.getNote();
            mNote.setText(note);
            mNote.setVisibility(TextUtils.isEmpty(note) ? View.GONE : View.VISIBLE);
        }
    }
}
