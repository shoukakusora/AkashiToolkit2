package rikka.akashitoolkit.model;

/**
 * One entry of Development.json.
 *
 * <p>Example: {"target":"九一式彻甲弹:4%","ship":"炮战系","fuel":10,"ammo":30,
 * "steel":90,"bauxite":10,"result":"20.3cm(2号)连装炮:2%","extra":"弹药提高到90，铝提高到30",
 * "extraResult":"三式弹:4%"}</p>
 *
 * <p>Gson maps the JSON keys to these fields by name. Any String field may be empty.</p>
 */
public class Development {

    private String target;
    private String ship;
    private int fuel;
    private int ammo;
    private int steel;
    private int bauxite;
    private String result;
    private String extra;
    private String extraResult;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getShip() {
        return ship;
    }

    public void setShip(String ship) {
        this.ship = ship;
    }

    public int getFuel() {
        return fuel;
    }

    public void setFuel(int fuel) {
        this.fuel = fuel;
    }

    public int getAmmo() {
        return ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = ammo;
    }

    public int getSteel() {
        return steel;
    }

    public void setSteel(int steel) {
        this.steel = steel;
    }

    public int getBauxite() {
        return bauxite;
    }

    public void setBauxite(int bauxite) {
        this.bauxite = bauxite;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getExtraResult() {
        return extraResult;
    }

    public void setExtraResult(String extraResult) {
        this.extraResult = extraResult;
    }
}
