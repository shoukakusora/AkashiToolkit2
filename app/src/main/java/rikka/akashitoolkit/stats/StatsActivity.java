package rikka.akashitoolkit.stats;

import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.ViewPagerAdapter;
import rikka.akashitoolkit.ui.BaseActivity;

/** 「统计」页：开发公式 / 建造公式 / 掉落。 */
public class StatsActivity extends BaseActivity implements SearchView.OnQueryTextListener {

    private ViewPager mViewPager;
    private String mQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setTitle("统计");
        }

        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        ViewPager viewPager = mViewPager;
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager()) {
            @Override
            public Bundle getArgs(int position) {
                return null;
            }
        };
        adapter.addFragment(DevelopmentFragment.class, "开发公式");
        adapter.addFragment(ConstructionFragment.class, "建造公式");
        adapter.addFragment(DropFragment.class, "掉落");
        viewPager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                applyFilter();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.stats, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        if (item != null) {
            SearchView searchView = (SearchView) item.getActionView();
            if (searchView != null) {
                searchView.setOnQueryTextListener(this);
            }
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query == null ? "" : query;
        applyFilter();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mQuery = newText == null ? "" : newText;
        applyFilter();
        return true;
    }

    private void applyFilter() {
        if (mViewPager == null) {
            return;
        }
        android.support.v4.app.Fragment fragment = getSupportFragmentManager()
                .findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + mViewPager.getCurrentItem());
        if (fragment == null) {
            fragment = getSupportFragmentManager().getFragments().isEmpty()
                    ? null : getSupportFragmentManager().getFragments().get(0);
        }
        if (fragment instanceof StatsFilterable) {
            ((StatsFilterable) fragment).applyFilter(mQuery);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
