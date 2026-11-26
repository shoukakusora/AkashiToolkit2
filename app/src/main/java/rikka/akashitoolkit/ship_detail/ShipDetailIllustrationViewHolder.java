package rikka.akashitoolkit.ship_detail;

import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import rikka.akashitoolkit.detail.IllustrationViewHolder;
import rikka.akashitoolkit.model.ExtraIllustration;
import rikka.akashitoolkit.model.Ship;
import rikka.akashitoolkit.staticdata.ExtraIllustrationList;
import rikka.akashitoolkit.utils.Utils;
import rikka.akashitoolkit.viewholder.IBindViewHolder;

/**
 * Created by Rikka on 2016/9/18.
 */
public class ShipDetailIllustrationViewHolder extends IllustrationViewHolder implements IBindViewHolder<Ship> {

    public ShipDetailIllustrationViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    public void bind(Ship data, int position) {
        if (data == null) {
            mAdapter.setUrls(new ArrayList<String>());
            return;
        }

        if (data.isEnemy()) {
            mAdapter.setImageWidth(Utils.dpToPx(150));
            mAdapter.setImageHeight(Utils.dpToPx(150));
            mAdapter.setImageScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            mAdapter.setImageWidth(Utils.dpToPx(150));
            mAdapter.setImageHeight(Utils.dpToPx(300));
            mAdapter.setImageScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mAdapter.setImagePadding(Utils.dpToPx(8));
        }
        mAdapter.setUrls(getIllustrationUrls(data));
    }

    private List<String> getIllustrationUrls(Ship data) {
        List<String> list = new ArrayList<>();

        String wikiId = data.getWikiId() != null ? data.getWikiId() : "";
        if (wikiId.length() == 0) {
            return list;
        }

        if (wikiId.equals("030a")
                || wikiId.equals("026a")
                || wikiId.equals("027a")
                || wikiId.equals("042a")
                || wikiId.equals("065a")
                || wikiId.equals("094a")
                || wikiId.equals("183a")) {
            list.add(Utils.getKCWikiFileUrl(String.format("KanMusu%sIllust.png", wikiId)));
            list.add(Utils.getKCWikiFileUrl(String.format("KanMusu%sDmgIllust.png", wikiId)));

        } else {
            if (!data.isEnemy()) {
                list.add(Utils.getKCWikiFileUrl(String.format("KanMusu%sIllust.png", wikiId.replace("a", ""))));
                list.add(Utils.getKCWikiFileUrl(String.format("KanMusu%sDmgIllust.png", wikiId.replace("a", ""))));
            } else {
                //敌舰立绘：wiki_id 形如 1501，文件名是 ShinkaiSeikan501.png
                String enemyWikiId = wikiId.length() > 1 ? wikiId.substring(1) : wikiId;
                list.add(Utils.getKCWikiFileUrl(String.format("ShinkaiSeikan%s.png", enemyWikiId)));
            }
        }

        ExtraIllustration extraIllustration = ExtraIllustrationList.findItemById(itemView.getContext(), wikiId);
        if (extraIllustration != null && extraIllustration.getImage() != null) {
            for (String name :
                    extraIllustration.getImage()) {
                list.add(Utils.getKCWikiFileUrl(name));
            }
        }

        return list;
    }
}
