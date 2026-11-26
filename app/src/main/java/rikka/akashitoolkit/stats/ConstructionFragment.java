package rikka.akashitoolkit.stats;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.ui.widget.RecyclerView;

/** 「建造公式」tab。 */
public class ConstructionFragment extends Fragment implements StatsFilterable {

    private ConstructionAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.content_recycler, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        int padding = getResources().getDimensionPixelSize(R.dimen.list_padding);
        recyclerView.setPadding(padding, padding, padding, padding);
        recyclerView.setClipToPadding(false);

        mAdapter = new ConstructionAdapter(getContext());
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void applyFilter(String query) {
        if (mAdapter != null) {
            mAdapter.setQuery(query);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.rebuildDataList();
        }
    }
}
