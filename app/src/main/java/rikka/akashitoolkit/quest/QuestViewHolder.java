package rikka.akashitoolkit.quest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.model.Equip;
import rikka.akashitoolkit.model.Item;
import rikka.akashitoolkit.model.Quest;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.model.ShipType;
import rikka.akashitoolkit.otto.BusProvider;
import rikka.akashitoolkit.otto.QuestAction;
import rikka.akashitoolkit.staticdata.EquipList;
import rikka.akashitoolkit.staticdata.EquipTypeList;
import rikka.akashitoolkit.staticdata.ItemList;
import rikka.akashitoolkit.staticdata.QuestList;
import rikka.akashitoolkit.staticdata.ShipList;
import rikka.akashitoolkit.staticdata.ShipTypeList;
import rikka.akashitoolkit.support.Settings;
import rikka.akashitoolkit.ui.widget.ExpandableLayout;
import rikka.akashitoolkit.utils.ItemIcons;
import rikka.akashitoolkit.utils.LocaleUtils;
import rikka.akashitoolkit.utils.Utils;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Created by Rikka on 2016/8/7.
 */
public class QuestViewHolder extends RecyclerView.ViewHolder implements IBindViewHolder<Quest> {

    public ExpandableLayout mExpandableLayout;
    public TextView mName;
    public TextView mDetail;
    public TextView mNote;
    public TextView mRewardText[] = new TextView[5];
    public TextView mType[] = new TextView[2];
    public LinearLayout mQuestContainer;
    public LinearLayout mRewardContainer;

    private QuestAdapter mAdapter;

    public QuestViewHolder(View itemView) {
        super(itemView);

        mExpandableLayout = (ExpandableLayout) itemView.findViewById(R.id.expandableLinearLayout);

        mType[0] = (TextView) itemView.findViewById(R.id.text_quest_type);
        mType[1] = (TextView) itemView.findViewById(R.id.text_quest_type2);
        mName = (TextView) itemView.findViewById(R.id.text_card_title);

        mDetail = (TextView) itemView.findViewById(R.id.text_quest_detail);
        mNote = (TextView) itemView.findViewById(R.id.text_quest_note);
        mRewardText[0] = (TextView) itemView.findViewById(R.id.text_number_0);
        mRewardText[1] = (TextView) itemView.findViewById(R.id.text_number_1);
        mRewardText[2] = (TextView) itemView.findViewById(R.id.text_number_2);
        mRewardText[3] = (TextView) itemView.findViewById(R.id.text_number_3);
        mRewardText[4] = (TextView) itemView.findViewById(R.id.text_quest_reward_4);
        mQuestContainer = (LinearLayout) itemView.findViewById(R.id.quest_container);
        mRewardContainer = (LinearLayout) itemView.findViewById(R.id.reward_container);
    }

    public void setAdapter(QuestAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void bind(final Quest data, int position) {
        Context context = itemView.getContext();

        if (!data.isHighlight()) {
            //holder.mExpandableLayout.setExpanded(false, false);
            mName.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        } else {
            setViewDetail(context, data);
            mExpandableLayout.setExpanded(true, false);
            mName.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }

        mName.setText(
                String.format(data.isBookmarked() ? "%s %s ★" : "%s %s",
                        data.getCode(),
                        data.getTitle().get(true)));

        mType[0].setVisibility(View.INVISIBLE);
        mType[1].setVisibility(View.INVISIBLE);

        int viewType = 0;

        if (data.getPeriod() > 0) {
            mType[0].setVisibility(View.VISIBLE);
            mType[0].setText(PERIOD[data.getPeriod()]);
            viewType++;
        }

        if (data.isNewMission()) {
            mType[viewType].setVisibility(View.VISIBLE);
            mType[viewType].setText(context.getString(R.string.quest_new));
        }

        if (!LocaleUtils.isDataLanguageJapanese(context) && data.getNote() != null && data.getNote().length() > 0) {
            mNote.setVisibility(View.VISIBLE);

            Spanned htmlDescription = Html.fromHtml(data.getNote());
            String descriptionWithOutExtraSpace = htmlDescription.toString().trim();
            mNote.setText(descriptionWithOutExtraSpace);
        } else {
            mNote.setVisibility(View.GONE);
        }

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpandableLayout.isExpanded()) {
                    mExpandableLayout.setExpanded(false);
                    mName.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
                } else {
                    setViewDetail(v.getContext(), data);
                    mExpandableLayout.setExpanded(true);
                    mName.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                }
            }
        });

        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @SuppressLint("DefaultLocale")
            @Override
            public boolean onLongClick(View v) {
                data.setBookmarked(!data.isBookmarked());

                Settings.instance(v.getContext())
                        .putBoolean(String.format("quest_%d", data.getId()), data.isBookmarked());

                mAdapter.showToast(v.getContext(), data.isBookmarked());

                mAdapter.notifyItemChanged(getAdapterPosition());

                return true;
            }
        });
    }

    private static final String[] PERIOD = {"", "日", "周", "月"};

    private void setViewDetail(Context context, Quest data) {

        mDetail.setText(Html.fromHtml(data.getContent().get()));

        setRewardText(data, 0);
        setRewardText(data, 1);
        setRewardText(data, 2);
        setRewardText(data, 3);

        setRewardEntries(context, data);

        mQuestContainer.removeAllViews();

        if (data.getUnlock() != null && data.getUnlock().size() > 0) {
            View view = LayoutInflater.from(context).inflate(R.layout.card_quest_next_header, mQuestContainer, false);
            ((TextView) view).setText(context.getString(R.string.quest_before));
            mQuestContainer.addView(view);
            addUnlockQuestViews(context, data.getUnlock(), "%s %s");
        }

        if (data.getAfter() != null && data.getAfter().size() > 0) {
            View view = LayoutInflater.from(context).inflate(R.layout.card_quest_next_header, mQuestContainer, false);
            ((TextView) view).setText(context.getString(R.string.quest_after));
            mQuestContainer.addView(view);
            addUnlockQuestViews(context, data.getAfter(), "%s %s");
        }
    }

    private void addUnlockQuestViews(Context context, List<String> ids, String format) {
        for (String code : ids) {
            if (code.length() == 0) {
                continue;
            }

            final Quest unlockQuest = QuestList.findItemByCode(context, code);
            if (unlockQuest == null) {
                Log.d("QuestAdapter", "Quest not found: " + code);
                continue;
            }

            View view = LayoutInflater.from(context).inflate(R.layout.card_quest_next, mQuestContainer, false);
            ((TextView) view).setText(
                    String.format(format, unlockQuest.getCode(), unlockQuest.getTitle().get(true)));

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (Quest q : QuestList.get(v.getContext())) {
                        q.setHighlight(false);
                    }

                    unlockQuest.setHighlight(true);
                    BusProvider.instance().post(new QuestAction.JumpToQuest(unlockQuest.getType(), unlockQuest.getId()));
                }
            });

            mQuestContainer.addView(view);
        }
    }

    /**
     * Render the new reward.entries list. When entries is null/empty the old behaviour
     * (reward.str / ship / equip / item arrays) is kept as a fallback.
     */
    private void setRewardEntries(Context context, Quest quest) {
        List<Quest.RewardEntry> entries = quest.getReward() == null ? null : quest.getReward().getEntries();

        if (entries == null || entries.isEmpty()) {
            mRewardContainer.setVisibility(View.GONE);
            mRewardContainer.removeAllViews();

            mRewardText[4].setVisibility(View.VISIBLE);
            setRewardText(quest, mRewardText[4]);
            return;
        }

        mRewardText[4].setVisibility(View.GONE);
        mRewardContainer.setVisibility(View.VISIBLE);
        mRewardContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(context);
        int i = 0;
        while (i < entries.size()) {
            Quest.RewardEntry entry = entries.get(i);
            if (entry == null || TextUtils.isEmpty(entry.type)) {
                i++;
                continue;
            }

            // 「以下奖励三选一：」：标题行 + 选项合并一行，用 " or " 连接
            if ("text".equals(entry.type) && entry.text != null && entry.text.contains("选一")) {
                View headerRow = inflater.inflate(R.layout.item_quest_reward, mRewardContainer, false);
                applyRowMargin(headerRow);
                ImageView headerIcon = (ImageView) headerRow.findViewById(R.id.reward_icon);
                TextView headerName = (TextView) headerRow.findViewById(R.id.reward_name);
                headerIcon.setVisibility(View.GONE);
                headerName.setTypeface(Typeface.DEFAULT_BOLD);
                headerName.setText(entry.text);
                mRewardContainer.addView(headerRow);

                List<String> options = new java.util.ArrayList<>();
                int j = i + 1;
                while (j < entries.size()) {
                    Quest.RewardEntry option = entries.get(j);
                    if (option == null) {
                        j++;
                        continue;
                    }
                    // 下一组「…选一」标题：结束当前组
                    if ("text".equals(option.type) && option.text != null && option.text.contains("选一")) {
                        break;
                    }
                    String label = resolveRewardLabel(context, option);
                    if (!TextUtils.isEmpty(label)) {
                        options.add(label);
                    }
                    j++;
                }
                if (!options.isEmpty()) {
                    View optionRow = inflater.inflate(R.layout.item_quest_reward, mRewardContainer, false);
                    applyRowMargin(optionRow);
                    ImageView optionIcon = (ImageView) optionRow.findViewById(R.id.reward_icon);
                    TextView optionName = (TextView) optionRow.findViewById(R.id.reward_name);
                    optionIcon.setVisibility(View.GONE);
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < options.size(); k++) {
                        if (k > 0) {
                            sb.append(" or ");
                        }
                        sb.append(options.get(k));
                    }
                    optionName.setText(sb.toString());
                    mRewardContainer.addView(optionRow);
                }
                i = j;
                continue;
            }

            View row = inflater.inflate(R.layout.item_quest_reward, mRewardContainer, false);
            applyRowMargin(row);

            ImageView icon = (ImageView) row.findViewById(R.id.reward_icon);
            TextView name = (TextView) row.findViewById(R.id.reward_name);

            if ("text".equals(entry.type)) {
                if (TextUtils.isEmpty(entry.text)) {
                    i++;
                    continue;
                }
                icon.setVisibility(View.GONE);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setText(entry.text);
            } else if ("equip".equals(entry.type)) {
                Equip equip = findEquip(context, entry.id);
                name.setText(resolveRewardLabel(context, entry));
                if (equip != null) {
                    icon.setVisibility(View.VISIBLE);
                    try {
                        EquipTypeList.setIntoImageView(icon, equip.getIcon());
                    } catch (Exception e) {
                        icon.setVisibility(View.GONE);
                    }
                } else {
                    icon.setVisibility(View.GONE);
                }
            } else if ("item".equals(entry.type)) {
                name.setText(resolveRewardLabel(context, entry));
                int iconRes = getItemIconResource(entry.id);
                if (iconRes != 0) {
                    icon.setVisibility(View.VISIBLE);
                    icon.setImageResource(iconRes);
                } else {
                    icon.setVisibility(View.GONE);
                }
            } else if ("ship".equals(entry.type)) {
                if (TextUtils.isEmpty(entry.name)) {
                    i++;
                    continue;
                }
                name.setText(resolveRewardLabel(context, entry));

                Ship rewardShip = findShip(entry.id);
                String bannerName = null;
                if (rewardShip != null && rewardShip.getWikiId() != null) {
                    bannerName = String.format("KanMusu%sBanner.jpg", rewardShip.getWikiId());
                }
                // 头像：files/img（刷新补下载）优先，其次 assets
                String bannerUrl = bannerName == null ? null
                        : rikka.akashitoolkit.support.ImageStore.resolve(context,
                        rikka.akashitoolkit.support.ImageStore.DIR_SHIP,
                        bannerName.replace(".png", ".jpg"));
                if (bannerUrl != null) {
                    icon.setVisibility(View.VISIBLE);
                    Glide.with(icon.getContext())
                            .load(Utils.getGlideUrl(bannerUrl))
               .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .centerCrop()
                            .into(icon);
                } else {
                    icon.setVisibility(View.GONE);
                }
            } else {
                i++;
                continue;
            }

            mRewardContainer.addView(row);
            i++;
        }
    }

    private void applyRowMargin(View row) {
        ViewGroup.LayoutParams layoutParams = row.getLayoutParams();
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) layoutParams).topMargin =
                    Utils.dpToPx(mRewardContainer.getChildCount() == 0 ? 6 : 2);
        }
    }

    /** 奖励条目文本：装备/道具为「名称xN」，舰娘为「舰种 名称xN」 */
    private String resolveRewardLabel(Context context, Quest.RewardEntry entry) {
        if (entry == null) {
            return "";
        }
        if ("equip".equals(entry.type)) {
            Equip equip = findEquip(context, entry.id);
            String equipName = entry.name;
            if (TextUtils.isEmpty(equipName) && equip != null && equip.getName() != null) {
                equipName = equip.getName().get();
            }
            if (TextUtils.isEmpty(equipName)) {
                equipName = "ID " + entry.id;
            }
            return formatRewardName(equipName, entry.count);
        }
        if ("item".equals(entry.type)) {
            String itemName = entry.name;
            if (TextUtils.isEmpty(itemName)) {
                Item item = findItem(context, entry.id);
                if (item != null && item.getName() != null) {
                    itemName = item.getName().get();
                }
            }
            if (TextUtils.isEmpty(itemName)) {
                itemName = "ID " + entry.id;
            }
            return formatRewardName(itemName, entry.count);
        }
        if ("ship".equals(entry.type)) {
            String shipName = entry.name;
            if (TextUtils.isEmpty(shipName)) {
                return "";
            }
            Ship ship = findShip(entry.id);
            if (ship != null) {
                ShipType shipType = ShipTypeList.findItemById(context, ship.getType());
                if (shipType != null && shipType.getName() != null
                        && !TextUtils.isEmpty(shipType.getName().get())) {
                    shipName = shipType.getName().get() + " " + shipName;
                }
            }
            return formatRewardName(shipName, entry.count);
        }
        if (TextUtils.isEmpty(entry.text)) {
            return "";
        }
        // 文字选项里的 xN / XN 统一成 ×N
        return entry.text.replaceAll("(?i)x(\\d+)", "×$1");
    }

    private static Ship findShip(int id) {
        try {
            return ShipList.findItemById(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static Equip findEquip(Context context, int id) {
        try {
            if (EquipList.get() == null) {
                EquipList.get(context);
            }
            return EquipList.findItemById(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static Item findItem(Context context, int id) {
        try {
            return ItemList.findItemById(context, id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Item icons now come from {@link ItemIcons}; item 1 (开发资材) and 6 (改修资材) keep
     * the previous local resources as a fallback for builds/resources that lack the new icons.
     */
    private static int getItemIconResource(int id) {
        int icon = ItemIcons.get(id);
        if (icon != 0) {
            return icon;
        }
        switch (id) {
            case 1:
                return R.drawable.res_dev;
            case 6:
                return R.drawable.res_imp;
            default:
                return 0;
        }
    }

    private static String formatRewardName(String name, int count) {
        if (name == null) {
            name = "";
        }
        if (count > 0) {
            // 例：12.7cm连装炮D型改二★+2×1
            return name + "×" + count;
        }
        return name;
    }

    @SuppressLint("SetTextI18n")
    private void setRewardText(Quest data, int id) {
        int res = data.getReward().getResource().get(id);
        if (res > 0) {
            ((LinearLayout) mRewardText[id].getParent()).setVisibility(View.VISIBLE);
            mRewardText[id].setText(Integer.toString(res));
        } else {
            ((LinearLayout) mRewardText[id].getParent()).setVisibility(View.GONE);
        }
    }

    private void setRewardText(Quest quest, TextView textView) {
        StringBuilder sb = new StringBuilder();

        for (Integer shipId :
                quest.getReward().getShip()) {
            Ship ship = ShipList.findItemById(shipId);
            if (ship != null) {
                sb.append(ShipTypeList.findItemById(ship.getType()).getName().get()).append(" ").append(ship.getName().get()).append("<br>");
            }
        }

        boolean isNumber = false;
        for (Integer equipId :
                quest.getReward().getEquip()) {
            if (!isNumber) {
                Equip equip = EquipList.findItemById(equipId);
                if (equip != null) {
                    sb.append(equip.getName().get());
                }
            } else {
                sb.append(" * ").append(equipId).append("<br>");
            }
            isNumber = !isNumber;
        }

        isNumber = false;
        for (Integer itemId :
                quest.getReward().getItem()) {
            if (!isNumber) {
                Item item = ItemList.findItemById(itemId);
                if (item != null) {
                    sb.append(item.getName().get());
                }
            } else {
                sb.append(" * ").append(itemId).append("<br>");
            }
            isNumber = !isNumber;
        }

        if (quest.getReward().getStr().length() > 0) {
            sb.append(quest.getReward().getStr());
        }

        if (sb.length() >= 4) {
            sb.delete(sb.length() - 4, sb.length());
        }

        if (sb.length() > 0) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(Html.fromHtml(sb.toString()));
        } else {
            textView.setVisibility(View.GONE);
        }
    }
}
