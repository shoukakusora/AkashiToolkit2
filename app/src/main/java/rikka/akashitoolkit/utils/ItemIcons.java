package rikka.akashitoolkit.utils;

import android.util.SparseIntArray;

import rikka.akashitoolkit.R;

/**
 * 道具（任务奖励使用的 Item）图标查表。
 * <p>
 * 图标以 item_icon_&lt;Item.json 中的 id&gt;.png 的形式存放在 res/drawable-nodpi/。
 * Item.json 的 id 与游戏 useitem id 并不完全一致（例如 1 = 开发资材、6 = 改修资材、
 * 11 = 高速建造材），所以这里使用显式映射，而不是按 id 拼接资源名。
 */
public final class ItemIcons {

    private static final SparseIntArray ICONS = new SparseIntArray();

    static {
        ICONS.put(0, R.drawable.item_icon_0);
        ICONS.put(1, R.drawable.item_icon_1);
        ICONS.put(2, R.drawable.item_icon_2);
        ICONS.put(3, R.drawable.item_icon_3);
        ICONS.put(4, R.drawable.item_icon_4);
        ICONS.put(5, R.drawable.item_icon_5);
        ICONS.put(6, R.drawable.item_icon_6);
        ICONS.put(7, R.drawable.item_icon_7);
        ICONS.put(8, R.drawable.item_icon_8);
        ICONS.put(9, R.drawable.item_icon_9);
        ICONS.put(10, R.drawable.item_icon_10);
        ICONS.put(11, R.drawable.item_icon_11);
        ICONS.put(12, R.drawable.item_icon_12);
        ICONS.put(13, R.drawable.item_icon_13);
        ICONS.put(14, R.drawable.item_icon_14);
        ICONS.put(15, R.drawable.item_icon_15);
        ICONS.put(16, R.drawable.item_icon_16);
        ICONS.put(17, R.drawable.item_icon_17);
        ICONS.put(18, R.drawable.item_icon_18);
        ICONS.put(19, R.drawable.item_icon_19);
        ICONS.put(20, R.drawable.item_icon_20);
        ICONS.put(21, R.drawable.item_icon_21);
        ICONS.put(22, R.drawable.item_icon_22);
        ICONS.put(23, R.drawable.item_icon_23);
        ICONS.put(24, R.drawable.item_icon_24);
        ICONS.put(25, R.drawable.item_icon_25);
        ICONS.put(26, R.drawable.item_icon_26);
        ICONS.put(27, R.drawable.item_icon_27);
        ICONS.put(28, R.drawable.item_icon_28);
        ICONS.put(31, R.drawable.item_icon_31);
        ICONS.put(32, R.drawable.item_icon_32);
        ICONS.put(33, R.drawable.item_icon_33);
        ICONS.put(34, R.drawable.item_icon_34);
        ICONS.put(44, R.drawable.item_icon_44);
        ICONS.put(49, R.drawable.item_icon_49);
        ICONS.put(50, R.drawable.item_icon_50);
        ICONS.put(51, R.drawable.item_icon_51);
        ICONS.put(52, R.drawable.item_icon_52);
        ICONS.put(53, R.drawable.item_icon_53);
        ICONS.put(54, R.drawable.item_icon_54);
        ICONS.put(55, R.drawable.item_icon_55);
        ICONS.put(56, R.drawable.item_icon_56);
        ICONS.put(57, R.drawable.item_icon_57);
        ICONS.put(58, R.drawable.item_icon_58);
        ICONS.put(59, R.drawable.item_icon_59);
        ICONS.put(60, R.drawable.item_icon_60);
        ICONS.put(61, R.drawable.item_icon_61);
        ICONS.put(62, R.drawable.item_icon_62);
        ICONS.put(63, R.drawable.item_icon_63);
        ICONS.put(64, R.drawable.item_icon_64);
        ICONS.put(65, R.drawable.item_icon_65);
        ICONS.put(66, R.drawable.item_icon_66);
        ICONS.put(67, R.drawable.item_icon_67);
        ICONS.put(68, R.drawable.item_icon_68);
        ICONS.put(69, R.drawable.item_icon_69);
        ICONS.put(70, R.drawable.item_icon_70);
        ICONS.put(71, R.drawable.item_icon_71);
        ICONS.put(72, R.drawable.item_icon_72);
        ICONS.put(73, R.drawable.item_icon_73);
        ICONS.put(74, R.drawable.item_icon_74);
        ICONS.put(75, R.drawable.item_icon_75);
        ICONS.put(76, R.drawable.item_icon_76);
        ICONS.put(77, R.drawable.item_icon_77);
        ICONS.put(78, R.drawable.item_icon_78);
        ICONS.put(80, R.drawable.item_icon_80);
        ICONS.put(85, R.drawable.item_icon_85);
        ICONS.put(86, R.drawable.item_icon_86);
        ICONS.put(87, R.drawable.item_icon_87);
        ICONS.put(88, R.drawable.item_icon_88);
        ICONS.put(89, R.drawable.item_icon_89);
        ICONS.put(90, R.drawable.item_icon_90);
        ICONS.put(91, R.drawable.item_icon_91);
        ICONS.put(92, R.drawable.item_icon_92);
        ICONS.put(93, R.drawable.item_icon_93);
        ICONS.put(94, R.drawable.item_icon_94);
        ICONS.put(95, R.drawable.item_icon_95);
        ICONS.put(96, R.drawable.item_icon_96);
        ICONS.put(97, R.drawable.item_icon_97);
        ICONS.put(98, R.drawable.item_icon_98);
        ICONS.put(99, R.drawable.item_icon_99);
        ICONS.put(100, R.drawable.item_icon_100);
        ICONS.put(101, R.drawable.item_icon_101);
        ICONS.put(102, R.drawable.item_icon_102);
        ICONS.put(104, R.drawable.item_icon_104);
        ICONS.put(105, R.drawable.item_icon_105);
    }

    private ItemIcons() {
    }

    /**
     * @param itemId Item.json 中的 id
     * @return 对应的 drawable 资源 id；没有对应图标时返回 0
     */
    public static int get(int itemId) {
        return ICONS.get(itemId, 0);
    }
}
