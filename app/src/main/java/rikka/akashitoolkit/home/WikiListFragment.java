package rikka.akashitoolkit.home;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.ui.widget.RecyclerView;

/**
 * One column (tab) of the home page, backed by a section of Home.json.
 */
public class WikiListFragment extends Fragment {

    public static final String ARG_SECTION = "SECTION";

    private String mSection;
    private WikiListAdapter mAdapter;

    public static WikiListFragment newInstance(String section) {
        WikiListFragment fragment = new WikiListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SECTION, section);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mSection = args == null ? null : args.getString(ARG_SECTION);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        mAdapter = new WikiListAdapter();
        recyclerView.setAdapter(mAdapter);
        mAdapter.setItemList(HomeData.get(getContext()).getEntries(mSection));
    }

    /**
     * Re-read Home.json and refresh the list (used by the home page refresh menu item).
     */
    public void reload() {
        if (mAdapter == null || getContext() == null) {
            return;
        }
        mAdapter.setItemList(HomeData.get(getContext()).getEntries(mSection));
    }
}
