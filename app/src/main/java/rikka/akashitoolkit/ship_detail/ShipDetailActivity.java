package rikka.akashitoolkit.ship_detail;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.view.menu.ShowableListMenu;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.ForwardingListener;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import rikka.akashitoolkit.R;
import rikka.akashitoolkit.chromecustomtabs.CustomTabActivityHelper;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.model.ShipClass;
import rikka.akashitoolkit.model.ShipVoice;
import rikka.akashitoolkit.network.RetrofitAPI;
import rikka.akashitoolkit.staticdata.ShipClassList;
import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.staticdata.Subtitle;
import rikka.akashitoolkit.support.MusicPlayer;
import rikka.akashitoolkit.support.Settings;
import rikka.akashitoolkit.support.StaticData;
import rikka.akashitoolkit.tools.SendReportActivity;
import rikka.akashitoolkit.ui.BaseItemDisplayActivity;
import rikka.akashitoolkit.ui.widget.LinearLayoutManager;
import rikka.akashitoolkit.utils.FlavorsUtils;
import rikka.akashitoolkit.utils.NetworkUtils;
import rikka.akashitoolkit.utils.Utils;

/**
 * Created by Rikka on 2016/3/30.
 */
public class ShipDetailActivity extends BaseItemDisplayActivity {

    public static final String EXTRA_ITEM_ID = "EXTRA_ITEM_ID";
    /**
     * 从「深海舰娘」列表打开时由 {@link rikka.akashitoolkit.ship.ShipAdapter} 传入。
     * 深海舰娘没有舰级/改造/语音等数据，详情页据此只渲染存在的部分。
     */
    public static final String EXTRA_IS_ENEMY = "EXTRA_IS_ENEMY";

    private static final String TAG = "ShipDetailActivity";

    private Toolbar mToolbar;
    private CoordinatorLayout mCoordinatorLayout;
    private AppBarLayout mAppBarLayout;
    private RecyclerView mRecyclerView;
    private Ship mItem;
    private int mId;
    private boolean mIsEnemy;
    private ShipDetailAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int id = -1;
        final Intent intent = getIntent();
        mIsEnemy = intent.getBooleanExtra(EXTRA_IS_ENEMY, false);
        if (intent.hasExtra(EXTRA_ITEM_ID)) {
            id = intent.getIntExtra(EXTRA_ITEM_ID, 0);
        } else if (intent.getData() != null) {
            try {
                id = Integer.parseInt(intent.getData().toString().split("/")[3]);
            } catch (Exception ignored) {
            }
        }

        if (getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            String extra = getIntent().getStringExtra(EXTRA_EXTRA);
            if (extra != null) {
                try {
                    id = Integer.parseInt(extra);
                } catch (Exception ignored) {
                }
            }
        }

        setContentView(R.layout.activity_ship_display);

        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mAppBarLayout = (AppBarLayout) findViewById(R.id.appBarLayout);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, OrientationHelper.VERTICAL, false));
        mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = Utils.dpToPx(16);
                }
            }
        });

        setItem(id, null);

        if (mItem == null) {
            // 找不到舰娘时 setItem 已提示并 finish，这里直接返回，避免 setupAppbar 里 NPE
            return;
        }

        setupAppbar();

        //mCoordinatorLayout.setBackgroundColor(ContextCompat.getColor(ShipDetailActivity.this, R.color.windowBackground));
        mRecyclerView.setBackgroundColor(ContextCompat.getColor(ShipDetailActivity.this, R.color.background));
        mAppBarLayout.setBackgroundColor(ContextCompat.getColor(ShipDetailActivity.this, R.color.background));

    }

    private void setItem(int id, int[] location) {
        if (id == mId) {
            return;
        }

        Ship item = ShipList.findItemById(this, id);

        if (item == null && id > 0) {
            // 静态数据可能因为读取异常被缓存成空列表（BaseGSONList 会吞掉异常），
            // 清掉缓存重新从 assets 读一次再找，避免深海舰娘（id >= 1500）打不开详情。
            Log.w(TAG, "Ship not found in cached list, reload. id=" + id);
            ShipList.clear();
            item = ShipList.findItemById(this, id);
        }

        if (item == null) {
            Log.e(TAG, "Ship not found, id=" + id + ", isEnemy=" + mIsEnemy);
            Toast.makeText(this, "找不到该舰娘数据 (id: " + id + ")", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setItem(item, location);
    }

    @SuppressLint("DefaultLocale")
    private void setToolbarTitle() {
        if (mItem == null || mToolbar == null || mItem.getName() == null) {
            return;
        }

        String name = mItem.getName().get();
        String c = "";

        // 深海舰娘的 ctype 是 0，ShipClassList 里没有 id 0，取不到是正常情况，
        // 不能因此抛异常或结束页面。
        if (mItem.getClassType() != 0) {
            ShipClass shipClass = null;
            try {
                shipClass = ShipClassList.findItemById(this, mItem.getClassType());
            } catch (Exception e) {
                Log.w(TAG, "find ship class failed: " + mItem.getClassType(), e);
            }

            if (shipClass != null && shipClass.getName() != null) {
                c = String.format("%s%s号舰", shipClass.getName(), Utils.getChineseNumberString(mItem.getClassNum()));
            } else {
                Log.d(TAG, "No ship class: " + name);
            }
        }

        TextView title = (TextView) mToolbar.findViewById(android.R.id.title);
        if (title != null) {
            title.setText(name);
        }

        TextView summary = (TextView) mToolbar.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setText(String.format("No.%s %s",
                    mItem.getWikiId() != null ? mItem.getWikiId() : "",
                    c/*,
                    ShipList.shipType[mItem.getType()]*/));
        }
    }

    private static final int FADE_IN = 150;
    private static final int FADE_OUT = 250;

    private void setItem(Ship item, final int[] location) {
        if (item == null) {
            finish();
            return;
        }

        if (mAdapter == null) {
            mAdapter = new ShipDetailAdapter(this, item, new ShipDetailRemodelViewHolder.OnItemClickListener() {
                @Override
                public void onClick(View v, int shipId) {
                    int[] location = new int[2];
                    v.getLocationInWindow(location);
                    location[0] += v.getWidth() / 2;
                    location[1] += v.getHeight() / 2;
                    setItem(shipId, location);
                }
            });

            mRecyclerView.setAdapter(mAdapter);
        }

        if (item.getId() == mId) {
            return;
        }

        mItem = item;
        mId = item.getId();

        downloadShipVoice();

        if (location == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setToolbarTitle();
        }

        if (location != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlphaAnimation animation = new AlphaAnimation(1, 0);
                animation.setDuration(FADE_IN);
                animation.setInterpolator(new FastOutSlowInInterpolator());
                mAppBarLayout.startAnimation(animation);

                if (!StaticData.instance(this).isTablet)
                    getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(ShipDetailActivity.this, R.color.colorItemDisplayStatusBar)));
            }

            AlphaAnimation animation = new AlphaAnimation(1, 0);
            animation.setDuration(FADE_IN);
            animation.setFillAfter(true);
            animation.setInterpolator(new FastOutSlowInInterpolator());
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation _animation) {
                    _animation.cancel();

                    if (mItem.getName() != null) {
                        setToolbarTitle();
                    }

                    mAdapter.setItem(ShipDetailActivity.this, mItem);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        animateRevealColorFromCoordinates(mCoordinatorLayout, R.color.background, location[0], location[1], FADE_OUT, true);

                        AlphaAnimation animation = new AlphaAnimation(0, 1);
                        animation.setFillBefore(true);
                        animation.setDuration(FADE_OUT);
                        animation.setInterpolator(new AccelerateDecelerateInterpolator());
                        mRecyclerView.startAnimation(animation);
                        mAppBarLayout.startAnimation(animation);
                    } else {
                        AlphaAnimation animation = new AlphaAnimation(0, 1);
                        animation.setDuration(FADE_IN);
                        animation.setInterpolator(new AccelerateDecelerateInterpolator());
                        mRecyclerView.startAnimation(animation);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            mRecyclerView.startAnimation(animation);
        }


    }

    private void downloadShipVoice() {
        if (FlavorsUtils.shouldSafeCheck()) {
            return;
        }

        // 深海舰娘没有任何语音数据，直接跳过（以前这里只是提前 return，是安全的，
        // 但 mAdapter 为 null 时后面的遍历会 NPE，一并加保护）
        if (mItem == null || mItem.isEnemy() || mAdapter == null) {
            return;
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://acc.kcwiki.org")
                .client(NetworkUtils.getClient(Subtitle.shouldUseCache()))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        Log.d(TAG, "use cache " + Subtitle.shouldUseCache());

        RetrofitAPI.SubtitleAPI service = retrofit.create(RetrofitAPI.SubtitleAPI.class);
        Call<List<ShipVoice>> call = service.getDetail(mItem.getId());

        call.enqueue(new Callback<List<ShipVoice>>() {
            @Override
            public void onResponse(Call<List<ShipVoice>> call, Response<List<ShipVoice>> response) {
                if (response.code() >= 400) {
                    onFailure(call, new Throwable("" + response.code()));
                    return;
                }

                if (response.body() == null) {
                    return;
                }

                if (isFinishing()) {
                    return;
                }

                int start;
                for (start = 0; start < mAdapter.getItemCount(); start++) {
                    if (mAdapter.getItemViewType(start) == ShipDetailAdapter.TYPE_VOICE) {
                        break;
                    }
                }

                for (int i = start; i < mAdapter.getItemCount(); i++) {
                    mAdapter.removeItem(start);
                    mAdapter.notifyItemRemoved(start);
                }

                start = mAdapter.getItemCount();
                for (ShipVoice item : response.body()) {
                    mAdapter.addItem(ShipDetailAdapter.TYPE_VOICE, item);
                }

                mAdapter.notifyItemRangeChanged(start, response.body().size());
            }

            @Override
            public void onFailure(Call<List<ShipVoice>> call, Throwable t) {
                Toast.makeText(ShipDetailActivity.this, "get voice: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.d(TAG, t.getMessage());
            }
        });
    }

    @Override
    protected void onStop() {
        MusicPlayer.stop();

        super.onStop();
    }

    @Override
    protected String getTaskDescriptionLabel() {
        if (mItem == null || mItem.getName() == null) {
            return super.getTaskDescriptionLabel();
        }
        return mItem.getName().get();
    }

    private PopupMenu mPopupMenu;

    private void setupAppbar() {
        if (mToolbar == null) {
            return;
        }

        setToolBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        if (mItem == null) {
            return;
        }

        // 深海舰娘没有改造链，也没有需要下拉的菜单，只把箭头图标藏起来即可
        if (mItem.isEnemy()) {
            View icon = mToolbar.findViewById(android.R.id.icon);
            if (icon != null) {
                icon.setVisibility(View.GONE);
            }
            return;
        }

        final View anchor = mToolbar.findViewById(R.id.content_container);
        if (anchor == null) {
            return;
        }
        mPopupMenu = new PopupMenu(this, anchor);

        Ship cur = mItem;
        while (cur != null && cur.getRemodel() != null && cur.getRemodel().getFromId() != 0) {
            Ship prev = ShipList.findItemById(this, cur.getRemodel().getFromId());
            if (prev == null) {
                break;
            }
            cur = prev;
        }
        if (cur == null) {
            return;
        }
        mPopupMenu.getMenu().add(0, cur.getId(), 0, cur.getName() != null ? cur.getName().get() : "");

        int i = 1;
        while (cur.getRemodel() != null &&
                cur.getRemodel().getToId() != 0 &&
                cur.getRemodel().getToId() != cur.getRemodel().getFromId()) {
            Ship next = ShipList.findItemById(cur.getRemodel().getToId());
            if (next == null) {
                break;
            }
            cur = next;
            mPopupMenu.getMenu().add(0, cur.getId(), i, cur.getName() != null ? cur.getName().get() : "");
            i++;
        }
        mPopupMenu.setGravity(Gravity.TOP);

        mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int[] position = new int[]{Utils.dpToPx(48 + 4) + anchor.getWidth() / 2, mAppBarLayout.getHeight() / 2 + Utils.dpToPx(24)};
                setItem(item.getItemId(), position);
                return false;
            }
        });

        anchor.setOnTouchListener(mPopupMenu.getDragToOpenListener());
        anchor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mPopupMenu.show();
            }
        });
    }

    Toast mToast;

    @SuppressLint("DefaultLocale")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_bookmark:
                if (mItem == null) {
                    break;
                }
                mItem.setBookmarked(!mItem.isBookmarked());

                Settings.instance(this)
                        .putBoolean(String.format("ship_%d_%d", mItem.getClassType(), mItem.getClassNum()), mItem.isBookmarked());

                if (mToast != null) {
                    mToast.cancel();
                }

                mToast = Toast.makeText(this, mItem.isBookmarked() ? getString(R.string.bookmark_add) : getString(R.string.bookmark_remove), Toast.LENGTH_SHORT);
                mToast.show();

                item.setIcon(
                        AppCompatDrawableManager.get().getDrawable(this, mItem.isBookmarked() ? R.drawable.ic_bookmark_24dp : R.drawable.ic_bookmark_border_24dp));

                break;
            case R.id.action_feedback:
                SendReportActivity.sendEmail(this,
                        "Akashi Toolkit 舰娘数据反馈",
                        String.format("应用版本: %d\n舰娘名称: %s\n\n请写下您的建议或是指出错误的地方。\n\n",
                                StaticData.instance(this).versionCode,
                                getTaskDescriptionLabel()));
                break;
            case R.id.action_kcwiki:
                if (mItem == null || mItem.getName() == null) {
                    break;
                }
                CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();

                int color = ContextCompat.getColor(this, R.color.colorPrimary);
                intentBuilder.setToolbarColor(color);
                intentBuilder.setShowTitle(true);
                intentBuilder.addDefaultShareMenuItem();
                intentBuilder.enableUrlBarHiding();

                CustomTabActivityHelper.openCustomTab(this, intentBuilder.build(), Uri.parse(String.format("https://zh.kcwiki.org/wiki/%s", mItem.getName().getZhCN())), new CustomTabActivityHelper.ExternalBrowserFallback());
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected ViewGroup getRootView() {
        return mCoordinatorLayout;
    }

    @Override
    protected View[] getAnimFadeViews() {
        return new View[] {
                mAppBarLayout,
                mRecyclerView
        };
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Animator animateRevealColorFromCoordinates(ViewGroup viewRoot, @ColorRes int color, int x, int y, long duration, boolean expand) {
        float finalRadius = (float) Math.hypot(viewRoot.getWidth(), viewRoot.getHeight());

        Animator anim;
        if (expand) {
            anim = ViewAnimationUtils.createCircularReveal(viewRoot, x, y, 0, finalRadius);
        } else {
            anim = ViewAnimationUtils.createCircularReveal(viewRoot, x, y, finalRadius, 0);
        }

        viewRoot.setBackgroundColor(ContextCompat.getColor(this, color));
        anim.setDuration(duration);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
        return anim;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ship_detail, menu);

        if (mItem == null) {
            return true;
        }

        MenuItem bookmark = menu.findItem(R.id.action_bookmark);
        if (bookmark != null) {
            if (!mItem.isEnemy()) {
                bookmark.setIcon(
                        AppCompatDrawableManager.get().getDrawable(this, mItem.isBookmarked() ? R.drawable.ic_bookmark_24dp : R.drawable.ic_bookmark_border_24dp));
            } else {
                bookmark.setVisible(false);
            }
        }

        if (mItem.isEnemy()) {
            MenuItem kcwiki = menu.findItem(R.id.action_kcwiki);
            if (kcwiki != null) {
                kcwiki.setVisible(false);
            }
        }

        return true;
    }
}
