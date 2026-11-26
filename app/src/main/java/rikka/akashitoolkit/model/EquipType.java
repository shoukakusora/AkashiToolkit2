package rikka.akashitoolkit.model;

/**
 * Created by Rikka on 2016/3/24.
 */
public class EquipType {
    private int id;
    private MultiLanguageEntry name;
    private int parent;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public MultiLanguageEntry getName() {
        return name;
    }

    public void setName(MultiLanguageEntry name) {
        this.name = name;
    }

    public int getParent() {
        return parent;
    }

    public void setParent(int parent) {
        this.parent = parent;
    }
}
