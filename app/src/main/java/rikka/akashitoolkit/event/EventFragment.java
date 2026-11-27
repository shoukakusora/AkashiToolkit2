package rikka.akashitoolkit.event;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.ui.fragments.DrawerFragment;
import rikka.akashitoolkit.ui.widget.RecyclerView;
import rikka.akashitoolkit.sync.EventSyncManager;

/**
 * 活动 / Events page backed by the local {@code Event.json} instead of the old
 * (now unreachable) "api_v2/event.php" endpoint.
 */
public class EventFragment extends DrawerFragment {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private EventListAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.content_twitter_container, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayout);
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        if (mRecyclerView == null || mSwipeRefreshLayout == null) {
            return;
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.windowBackground));

        mAdapter = new EventListAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setItemList(EventData.get(getContext()));

        mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(getContext(), R.color.colorAccent));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (getContext() == null) {
                    return;
                }

                final android.content.Context context = getContext().getApplicationContext();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            rikka.akashitoolkit.sync.EventSyncManager.sync(context);
                        } catch (Exception e) {
                            android.util.Log.w("EventFragment", "sync events failed", e);
                        }
                        if (getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (getContext() == null) {
                                    return;
                                }
                                EventData.clear();
                                mAdapter.setItemList(EventData.get(getContext()));
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    @Override
    public void onShow() {
        super.onShow();

        setToolbarTitle(getString(R.string.event));
    }
}
