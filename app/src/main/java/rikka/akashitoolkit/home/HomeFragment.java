package rikka.akashitoolkit.home;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.ViewPagerAdapter;
import rikka.akashitoolkit.ui.fragments.DrawerFragment;

/**
 * Created by Rikka on 2016/6/11.
 */
public class HomeFragment extends DrawerFragment {
    private ViewPager mViewPager;

    @Override
    protected boolean onSetTabLayout(TabLayout tabLayout) {
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        tabLayout.setPadding(0, 0, 0, 0);

        if (tabLayout.getTabAt(0).getIcon() == null) {
            tabLayout.getTabAt(0).setIcon(R.drawable.ic_nav_twitter_24dp);
            tabLayout.getTabAt(1).setIcon(R.drawable.ic_explore_black_24dp);
            tabLayout.getTabAt(2).setIcon(R.drawable.ic_nav_new_24dp);
            DrawableCompat.setTintList(tabLayout.getTabAt(0).getIcon(), tabLayout.getTabTextColors());
            DrawableCompat.setTintList(tabLayout.getTabAt(1).getIcon(), tabLayout.getTabTextColors());
            DrawableCompat.setTintList(tabLayout.getTabAt(2).getIcon(), tabLayout.getTabTextColors());
        }
        return true;
    }

    @Override
    public void onShow() {
        super.onShow();

        setToolbarTitle(getString(R.string.app_name));

        // 数据可能刚在「关于」里刷新过，重新读取 Home.json
        try {
            HomeData.clear();
            List<Fragment> fragments = getChildFragmentManager().getFragments();
            if (fragments != null) {
                for (Fragment fragment : fragments) {
                    if (fragment instanceof WikiListFragment) {
                        ((WikiListFragment) fragment).reload();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onHide() {
        super.onHide();
        mActivity.getTabLayout().setTabMode(TabLayout.MODE_SCROLLABLE);
        int padding = getResources().getDimensionPixelSize(R.dimen.tab_layout_padding);
        mActivity.getTabLayout().setPadding(padding, 0, padding, 0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.twitter, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            HomeData.clear();

            List<Fragment> fragments = getChildFragmentManager().getFragments();
            if (fragments != null) {
                for (Fragment fragment : fragments) {
                    if (fragment instanceof WikiListFragment) {
                        ((WikiListFragment) fragment).reload();
                    }
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.content_viewpager, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mViewPager = (ViewPager) view.findViewById(R.id.view_pager);
        mViewPager.setAdapter(getAdapter());
        mViewPager.setOffscreenPageLimit(3);
        mViewPager.setCurrentItem(0);
    }

    private ViewPagerAdapter getAdapter() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getChildFragmentManager()) {
            @Override
            public Bundle getArgs(int position) {
                Bundle bundle = new Bundle();
                bundle.putString(WikiListFragment.ARG_SECTION, getSection(position));
                return bundle;
            }
        };
        adapter.addFragment(WikiListFragment.class, "游戏情报");
        adapter.addFragment(WikiListFragment.class, "最新更新");
        adapter.addFragment(WikiListFragment.class, "季节性");

        return adapter;
    }

    private static String getSection(int position) {
        switch (position) {
            case 1:
                return HomeData.SECTION_UPDATES;
            case 2:
                return HomeData.SECTION_SEASONAL;
            case 0:
            default:
                return HomeData.SECTION_GAME_INFO;
        }
    }
}
