package rikka.akashitoolkit.model;

/** 建造公式（来源：zh.kcwiki.cn/wiki/建造）。 */
public class Construction {

    private int fuel;
    private int ammo;
    private int steel;
    private int bauxite;
    private String target;
    private String note;
    private boolean large;

    public int getFuel() {
        return fuel;
    }

    public int getAmmo() {
        return ammo;
    }

    public int getSteel() {
        return steel;
    }

    public int getBauxite() {
        return bauxite;
    }

    public String getTarget() {
        return target == null ? "" : target;
    }

    public String getNote() {
        return note == null ? "" : note;
    }

    public boolean isLarge() {
        return large;
    }
}
