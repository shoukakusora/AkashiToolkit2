package rikka.akashitoolkit.ship;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.adapter.BaseBookmarkRecyclerAdapter;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.model.ShipClass;
import rikka.akashitoolkit.model.ShipType;
import rikka.akashitoolkit.otto.BusProvider;
import rikka.akashitoolkit.otto.ItemSelectAction;
import rikka.akashitoolkit.ship_detail.ShipDetailActivity;
import rikka.akashitoolkit.staticdata.ShipClassList;
import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.staticdata.ShipTypeList;
import rikka.akashitoolkit.support.Settings;
import rikka.akashitoolkit.support.StaticData;
import rikka.akashitoolkit.ui.BaseItemDisplayActivity;
import rikka.akashitoolkit.ui.widget.LinearLayoutManager;
import rikka.akashitoolkit.utils.FlavorsUtils;
import rikka.akashitoolkit.utils.Utils;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by Rikka on 2016/3/30.
 */
public class ShipAdapter extends BaseBookmarkRecyclerAdapter<RecyclerView.ViewHolder, Object> {
    private Map<Long, Boolean> mExpanded;

    private Activity mActivity;
    private int mShowVersion;
    private boolean mEnemy;
    private int mTypeFlag;
    private int mShowSpeed;
    private int mSort;
    private String mKeyword;
    private boolean mIsSearching;
    private boolean mSelectMode;

    private Toast mToast;

    public ShipAdapter(Activity activity, int showVersion, int typeFlag, int showSpeed, int sort, boolean bookmarked, boolean enemy, boolean select_mode) {
        super(bookmarked);

        mExpanded = new LinkedHashMap<>();
        mActivity = activity;

        setHasStableIds(true);

        mShowVersion = showVersion;
        mTypeFlag = typeFlag;
        mShowSpeed = showSpeed;
        mSort = sort;
        mEnemy = enemy;
        mSelectMode = select_mode;

        mActivity = activity;

        rebuildDataList();
    }

    public void setSearching(boolean searching) {
        mIsSearching = searching;
    }

    public void setSort(int sort) {
        mSort = sort;
        mExpanded.clear();
    }

    public void setShowSpeed(int showSpeed) {
        mShowSpeed = showSpeed;
    }

    public int getTypeFlag() {
        return mTypeFlag;
    }

    public void setTypeFlag(int typeFlag) {
        mTypeFlag = typeFlag;
    }

    public void setKeyword(String keyword) {
        if (mKeyword != null && mKeyword.equals(keyword)) {
            return;
        }

        mKeyword = keyword;
    }

    public void setShowVersion(int showVersion) {
        mShowVersion = showVersion;
    }

    @Override
    public void setBookmarked(boolean bookmarked) {
        /*for (Map.Entry<Long, Boolean> entry :
                mExpanded.entrySet()) {
            entry.setValue(bookmarked);
        }*/
        super.setBookmarked(bookmarked);
    }

    public void rebuildDataList() {
        Observable
                .create(new Observable.OnSubscribe<List<Ship>>() {
                    @Override
                    public void call(Subscriber<? super List<Ship>> subscriber) {
                        subscriber.onNext(ShipList.get(mActivity));
                        subscriber.onCompleted();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<Ship>>() {
                    @Override
                    public void onNext(List<Ship> o) {
                        clearItemList();

                        if (mSort == 1) {
                            ShipList.sortByClass();
                        } else {
                            ShipList.sortByType();
                        }

                        // 按分组 id 记录已加过的标题：不同舰种可能同名（如 15/22 都叫「补给舰」），
                        // 用名称判断会漏加标题、展开状态取不到而抛异常，导致点击标题无反应。
                        java.util.Set<Long> addedHeaders = new java.util.HashSet<>();

                        try {
                            for (Ship item : o) {
                                if (item == null || !check(item)) {
                                    continue;
                                }

                                long id;
                                String curType = null;
                                if (mSort == 0) {
                                    id = item.getType() * 10L;
                                    ShipType shipType = ShipTypeList.findItemById(mActivity, item.getType());
                                    if (shipType != null && shipType.getName() != null) {
                                        curType = shipType.getName().get();
                                    }
                                } else {
                                    id = -item.getClassType() * 10L;
                                    ShipClass shipClass = ShipClassList.findItemById(mActivity, item.getClassType());
                                    if (shipClass != null) {
                                        curType = shipClass.getName();
                                    }
                                }

                                if (curType == null && mEnemy) {
                                    // 深海舰娘的 stype 在 ShipType.json 里查不到时也要有一个
                                    // 可展开的分组，否则这些船永远显示不出来
                                    curType = "其他";
                                }

                                if (!mExpanded.containsKey(id) && !requireBookmarked()) {
                                    mExpanded.put(id, false);
                                }

                                if (curType != null && addedHeaders.add(id)) {
                                    addItem(id, 1, curType);
                                }

                                Boolean expanded = mExpanded.get(id);
                                if (mIsSearching || requireBookmarked() || (expanded != null && expanded)) {
                                    addItem(item.getId() * 10000L, 0, item);
                                }
                            }
                        } catch (Exception e) {
                            Log.e("ShipAdapter", "rebuildDataList failed", e);
                        } finally {
                            onDataListRebuilt(getItemList());
                            notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("ShipAdapter", "rebuildDataList error", e);
                    }
                });
    }

    private boolean check(Ship item) {
        if (item == null) {
            return false;
        }

        if (mEnemy) {
            // 敌舰页只保留深海舰娘（id 1500..2499），玩家舰娘一条都不要
            return item.isEnemy();
            //return item.getId() >= 500;
        }

        if (item.isEnemy() || item.isHidden()) {
            return false;
        }

        if (item.getRemodel() != null) {
            if (!mIsSearching) {
                switch (mShowVersion) {
                    case 1:
                        if (item.getRemodel().getFromId() != 0) {
                            return false;
                        }
                        break;
                    case 2:
                        if ((item.getRemodel().getToId() != 0 &&
                                item.getRemodel().getToId() != item.getRemodel().getFromId())) {
                            return false;
                        }
                        break;
                }
            }
        }

        if (requireBookmarked() && (!mIsSearching) && !item.isBookmarked()) {
            return false;
        }

        if (mTypeFlag != 0 && (1 << item.getType() & mTypeFlag) == 0) {
            return false;
        }

        if (mShowSpeed != 0 && item.getAttribute() != null &&
                !((mShowSpeed & 1) > 0 && item.getAttribute().getSpeed() == 5 ||
                        (mShowSpeed & 1 << 1) > 0 && item.getAttribute().getSpeed() == 10)) {
            return false;
        }

        if (mIsSearching && mKeyword != null && mKeyword.length() == 0) {
            return false;
        }

        if (mIsSearching && mKeyword != null &&
                (item.getNameForSearch() == null || !item.getNameForSearch().toLowerCase().contains(mKeyword.toLowerCase()))) {
            return false;
        }

        return true;
    }

    /**
     * 舰娘行的点击监听在这一步就安装，而不是等到 bindViewHolder。
     * 这样即使某一行在绑定时装图/取数据抛异常，或 mSelectMode 状态判断出错，
     * 行本身也一定是可点击的（深海舰娘页曾出现点击无反应）。
     */
    private void setItemClickListener(final ShipViewHolder holder) {
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    position = holder.getLayoutPosition();
                }
                if (position < 0) {
                    return;
                }

                Object object = getItem(position);
                if (!(object instanceof Ship)) {
                    return;
                }
                Ship item = (Ship) object;

                if (mSelectMode) {
                    BusProvider.instance().post(new ItemSelectAction.Finish(item.getId()));
                    return;
                }

                Intent intent = new Intent(v.getContext(), ShipDetailActivity.class);
                intent.putExtra(ShipDetailActivity.EXTRA_ITEM_ID, item.getId());
                // 告诉详情页这是深海舰娘，详情页据此跳过舰级/语音等玩家舰娘才有的数据
                intent.putExtra(ShipDetailActivity.EXTRA_IS_ENEMY, mEnemy || item.isEnemy());

                int[] location = new int[2];
                holder.itemView.getLocationOnScreen(location);
                intent.putExtra(ShipDetailActivity.EXTRA_START_Y, location[1]);
                intent.putExtra(ShipDetailActivity.EXTRA_START_HEIGHT, holder.itemView.getHeight());

                try {
                    BaseItemDisplayActivity.start(v.getContext(), intent);
                } catch (Exception e) {
                    Log.e("ShipAdapter", "start ship detail failed, id=" + item.getId(), e);
                    Toast.makeText(v.getContext(), "找不到该舰娘数据 (id: " + item.getId() + ")", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case 0:
                ShipViewHolder holder = new ShipViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ship, parent, false));
                setItemClickListener(holder);
                return holder;
            case 1:
                return new TypeViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ship_subtitle, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case 0:
                bindViewHolder((ShipViewHolder) holder, position);
                break;
            case 1:
                bindViewHolder((TypeViewHolder) holder, position);
                break;
        }
    }

    private void bindViewHolder(final ShipViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        Object object = getItem(position);
        if (!(object instanceof Ship)) {
            return;
        }
        final Ship item = (Ship) object;

        holder.mTitle.setText(String.format(item.isBookmarked() ? "%s ★" : "%s",
                item.getName() != null ? item.getName().get() : ""));

        //敌舰横幅
        @SuppressLint("DefaultLocale")
        // 头像已随 APK 打包（assets/shipimg），无需联网
        String banner = rikka.akashitoolkit.support.ImageStore.resolve(context,
                rikka.akashitoolkit.support.ImageStore.DIR_SHIP,
                rikka.akashitoolkit.support.ImageStore.shipName(mEnemy, item.getWikiId(), item.getId()));
        boolean showBanner = Settings.instance(context).getBoolean(Settings.SHOW_SHIP_BANNER, true);

        if (FlavorsUtils.shouldSafeCheck()) {
            showBanner = false;
        }

        if (!mEnemy) {
            ShipClass shipClass = ShipClassList.findItemById(mActivity, item.getClassType());

            if (shipClass != null) {
                String c;
                if (mSort == 0) {
                    c = String.format("%s%s号舰", shipClass.getName(), Utils.getChineseNumberString(item.getClassNum()));
                } else {
                    c = String.format("%s号舰", Utils.getChineseNumberString(item.getClassNum()));
                }

                if (!showBanner || mSort == 1 || StaticData.instance(context).isTablet) {
                    holder.mContent.setText(c);
                } else {
                    holder.mContent.setText("");
                }
            } else {
                Log.d("ShipAdapter", "No ship class: " + (item.getName() != null ? item.getName().get() : item.getId()));
                holder.mContent.setText("");
            }
                } else {
            // 敌舰没有舰级/舰号，改为显示舰种名，信息展示与舰娘页一致
            ShipType shipType = ShipTypeList.findItemById(mActivity, item.getType());
            String c = shipType != null && shipType.getName() != null ? shipType.getName().get() : "";
            if (!showBanner || StaticData.instance(context).isTablet) {
                holder.mContent.setText(c);
            } else {
                holder.mContent.setText("");
            }
        }

        if (showBanner) {
            holder.mIconContainer.setVisibility(View.VISIBLE);

            // 有官方图才显示；没有则隐藏图标位（不再使用占位立绘）
            String thumbName = mEnemy
                    ? String.format("ShinkaiSeikan%dBanner.jpg", item.getId())
                    : String.format("KanMusu%03dHDBanner.jpg",
                    thumbNumber(item.getWikiId()));
            boolean hasThumb = hasShipThumb(context, thumbName);
            if (!hasThumb) {
                holder.mIconContainer.setVisibility(View.GONE);
            } else {
                holder.mIconContainer.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(banner)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .crossFade()
                        .centerCrop()
                        .into(holder.mIcon);
            }
        } else {
            holder.mIconContainer.setVisibility(View.GONE);
        }

        // 点击事件在 onCreateViewHolder 里统一安装，这里只处理玩家舰娘的长按收藏
        if (!mSelectMode && !mEnemy) {
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public boolean onLongClick(View v) {
                    int position = holder.getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        position = holder.getLayoutPosition();
                    }
                    Object object = position >= 0 ? getItem(position) : null;
                    if (!(object instanceof Ship)) {
                        return false;
                    }
                    Ship item = (Ship) object;

                    item.setBookmarked(!item.isBookmarked());

                    Settings.instance(holder.itemView.getContext())
                            .putBoolean(String.format("ship_%d_%d", item.getClassType(), item.getClassNum()), item.isBookmarked());

                    if (mToast != null) {
                        mToast.cancel();
                    }

                    mToast = Toast.makeText(holder.itemView.getContext(), item.isBookmarked() ? v.getContext().getString(R.string.bookmark_add) : v.getContext().getString(R.string.bookmark_remove), Toast.LENGTH_SHORT);
                    mToast.show();

                    notifyItemChanged(holder.getAdapterPosition());

                    return true;
                }
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
    }

    private TypeViewHolder mLastHolder;

    private int mFirstVisibleItemPosition = -1;

    private void bindViewHolder(final TypeViewHolder holder, int position) {
        // 展开状态一律用分组 id（Stable id）作 key，且允许取不到（null 安全），
        // 否则不同舰种/舰级同名时点击标题会因拆箱 NPE 而无反应。
        final long boundGroupId = getItemId(position);
        Boolean expandedValue = mExpanded.get(boundGroupId);
        boolean expanded = mIsSearching || requireBookmarked() || (expandedValue != null && expandedValue);
        boolean showDivider = position != 0 && expanded || position != 0 && (getItemViewType(position - 1) != getItemViewType(position));

        holder.mDivider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
        Object title = getItem(position);
        holder.mTitle.setText(title instanceof String ? (String) title : "");

        holder.itemView.setSelected(expanded);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    position = holder.getLayoutPosition();
                }

                long groupId = position >= 0 ? getItemId(position) : boundGroupId;
                Boolean current = mExpanded.get(groupId);
                boolean expanded = !(current != null && current);
                mExpanded.put(groupId, expanded);

                if (mRecyclerView != null && mRecyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                    if (expanded) {
                        LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                        mFirstVisibleItemPosition = layoutManager.findFirstCompletelyVisibleItemPosition();
                        //mRecyclerView.scrollBy(0, Utils.dpToPx(9));
                        final int targetPosition = position;
                        mRecyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mRecyclerView == null || !(mRecyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
                                    return;
                                }
                                int position = targetPosition;
                                if (position == RecyclerView.NO_POSITION) {
                                    position = holder.getAdapterPosition();
                                }
                                if (position < 0) {
                                    return;
                                }
                                LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                                layoutManager.smoothScrollToPositionWithOffset(
                                        mRecyclerView,
                                        position,
                                        LinearSmoothScroller.SNAP_TO_START,
                                        position != 0 ? -Utils.dpToPx(5) : 0);
                                mLastHolder = holder;
                            }
                        });
                    } else {
                        mRecyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mRecyclerView == null || !(mRecyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
                                    return;
                                }
                                if (mFirstVisibleItemPosition < 0) {
                                    mFirstVisibleItemPosition = 0;
                                }
                                LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                                layoutManager.smoothScrollToPosition(mRecyclerView, mFirstVisibleItemPosition, LinearSmoothScroller.SNAP_TO_ANY);
                            }
                        });
                    }
                }

                rebuildDataList();
            }
        });
    }

    public boolean collapseLastType() {
        if (requireBookmarked() || mIsSearching || mLastHolder == null) {
            return false;
        }

        if (getItemCount() == 0) {
            return false;
        }

        mLastHolder.itemView.performClick();
        mLastHolder = null;

        return true;
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);

        if (holder instanceof ShipViewHolder) {
            Glide.clear(((ShipViewHolder) holder).mIcon);
        }
    }

    private RecyclerView mRecyclerView;

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        mRecyclerView = null;
        mLastHolder = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private static int thumbNumber(String wikiId) {
        String digits = wikiId == null ? "" : wikiId.replaceAll("\\D", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private static java.util.Set<String> sAssetThumbs;

    private static boolean hasShipThumb(android.content.Context context, String name) {
        if (context == null) {
            return false;
        }
        if (rikka.akashitoolkit.support.ImageStore.localFile(
                context, rikka.akashitoolkit.support.ImageStore.DIR_SHIP, name).exists()) {
            return true;
        }
        if (sAssetThumbs == null) {
            java.util.Set<String> set = new java.util.HashSet<>();
            try {
                String[] list = context.getAssets().list(rikka.akashitoolkit.support.ImageStore.DIR_SHIP);
                if (list != null) {
                    java.util.Collections.addAll(set, list);
                }
            } catch (Exception ignored) {
            }
            sAssetThumbs = set;
        }
        return sAssetThumbs.contains(name);
    }

}
