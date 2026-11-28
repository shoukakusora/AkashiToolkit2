package rikka.akashitoolkit.ship_detail;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.StringRes;
import android.view.View;

import rikka.akashitoolkit.R;
import rikka.akashitoolkit.detail.AttributeViewHolder;
import rikka.akashitoolkit.model.AttributeEntity;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.utils.KCStringFormatter;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Created by Rikka on 2016/9/17.
 */
public class ShipDetailAttributeViewHolder extends AttributeViewHolder implements IBindViewHolder<Ship> {

    public ShipDetailAttributeViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    public void bind(Ship data, int position) {
        if (data == null || data.getAttribute() == null) {
            return;
        }

        Context context = itemView.getContext();
        Adapter adapter = mAdapter;

        AttributeEntity attr = data.getAttribute();
        AttributeEntity attr_max = attr.plus(data.getAttributeMax()).plus(data.getAttribute99());

        if (!data.isEnemy()) {
            adapter.add(R.string.attr_hp, attr.getHP()/*, attr_max.getHP()*/);

            add(adapter, R.string.attr_firepower, attr.getFirepower(), attr_max.getFirepower());
            add(adapter, R.string.attr_aa, attr.getAA(), attr_max.getAA());
            add(adapter, R.string.attr_torpedo, attr.getTorpedo(), attr_max.getTorpedo());
            add(adapter, R.string.attr_armor, attr.getArmor(), attr_max.getArmor());

            add(adapter, R.string.attr_asw, attr.getASW(), attr_max.getASW());
            add(adapter, R.string.attr_evasion, attr.getEvasion(), attr_max.getEvasion());
            add(adapter, R.string.attr_los, attr.getLOS(), attr_max.getLOS());

            adapter.add(R.string.attr_speed, KCStringFormatter.getSpeed(context, attr.getSpeed()));
            adapter.add(R.string.attr_range, KCStringFormatter.getRange(context, attr.getRange()));

            add(adapter, R.string.attr_luck, attr.getLuck(), attr_max.getLuck());
        } else if (data.getForms() != null && data.getForms().length > 1) {
            // 同名深海：耐久 700→700→700、火力 132→162→182
            adapter.add(R.string.attr_hp, join(data, "hp", attr.getHP()));
            adapter.add(R.string.attr_firepower, join(data, "fire", attr.getFirepower()));
            adapter.add(R.string.attr_aa, join(data, "aa", attr.getAA()));
            adapter.add(R.string.attr_torpedo, join(data, "torpedo", attr.getTorpedo()));
            adapter.add(R.string.attr_armor, join(data, "armor", attr.getArmor()));
            adapter.add(R.string.attr_asw, join(data, "asw", attr.getASW()));
            adapter.add(R.string.attr_evasion, join(data, "evasion", attr.getEvasion()));
            adapter.add(R.string.attr_los, join(data, "los", attr.getLOS()));
            adapter.add(R.string.attr_speed, KCStringFormatter.getSpeed(context, attr.getSpeed()));
            adapter.add(R.string.attr_range, KCStringFormatter.getRange(context, attr.getRange()));
        } else {
            adapter.add(R.string.attr_hp, attr.getHP());
            addEnemy(adapter, R.string.attr_firepower, attr.getFirepower(), attr_max.getFirepower());
            addEnemy(adapter, R.string.attr_aa, attr.getAA(), attr_max.getAA());
            addEnemy(adapter, R.string.attr_torpedo, attr.getTorpedo(), attr_max.getTorpedo());
            addEnemy(adapter, R.string.attr_armor, attr.getArmor(), attr_max.getArmor());
            adapter.add(R.string.attr_speed, KCStringFormatter.getSpeed(context, attr.getSpeed()));
            adapter.add(R.string.attr_range, KCStringFormatter.getRange(context, attr.getRange()));
        }
    }

    /** 多形态属性用箭头连接；数值全相同则只显示一次。 */
    private String join(Ship data, String key, int fallback) {
        AttributeEntity[] forms = data.getForms();
        StringBuilder sb = new StringBuilder();
        boolean same = true;
        Integer first = null;
        for (AttributeEntity form : forms) {
            if (form == null) {
                continue;
            }
            int value = valueOf(form, key, fallback);
            if (first == null) {
                first = value;
            } else if (first != value) {
                same = false;
            }
            if (sb.length() > 0) {
                sb.append("\u2192");
            }
            sb.append(value);
        }
        if (sb.length() == 0) {
            return String.format("%d", fallback);
        }
        return same ? String.format("%d", first) : sb.toString();
    }

    private int valueOf(AttributeEntity form, String key, int fallback) {
        if ("hp".equals(key)) {
            return form.getHP();
        } else if ("fire".equals(key)) {
            return form.getFirepower();
        } else if ("aa".equals(key)) {
            return form.getAA();
        } else if ("torpedo".equals(key)) {
            return form.getTorpedo();
        } else if ("armor".equals(key)) {
            return form.getArmor();
        } else if ("asw".equals(key)) {
            return form.getASW();
        } else if ("evasion".equals(key)) {
            return form.getEvasion();
        } else if ("los".equals(key)) {
            return form.getLOS();
        }
        return fallback;
    }

    @SuppressLint("DefaultLocale")
    private void add(Adapter adapter, @StringRes int title, int min, int max) {
        if (min == max) {
            adapter.add(title, String.format("%d", min));
        } else {
            adapter.add(title, String.format("%d → %d", min, max));
        }
    }

    /**
     * 使用来自 https://zh.kcwiki.org/wiki/深海栖舰列表 的数据
     * 原文 "/"前后数值表示："面板火力或雷装/计入装备后火力或雷装"
     *
     * @param adapter Adapter
     * @param title   标题
     * @param min     不带有装备的数值
     * @param max     带有装备的数值
     */
    @SuppressLint("DefaultLocale")
    private void addEnemy(Adapter adapter, @StringRes int title, int min, int max) {
        if (max == 0 || min == max) {
            adapter.add(title, String.format("%d", min));
        } else {
            adapter.add(title, String.format("%d / %d", min, max));
        }
    }
}
