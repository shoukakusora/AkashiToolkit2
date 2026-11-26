package rikka.akashitoolkit.model;

import java.util.List;

/**
 * Created by Rikka on 2016/4/9.
 */
public class Map {

    /**
     * sea : 1
     * area : 1
     * map : 1-1
     * airforce : [[0,0,0]]
     * recommend : ["任意"]
     * condition : ["无"]
     * other : ["任意1~2艘以上舰船。"]
     */

    private int sea;
    private int area;
    private String map;
    private List<List<Integer>> airforce;
    private List<String> recommend;
    private List<String> condition;
    private List<String> other;

    private ImagesEntity images;
    private List<RouteEntity> routes;

    public int getSea() {
        return sea;
    }

    public void setSea(int sea) {
        this.sea = sea;
    }

    public int getArea() {
        return area;
    }

    public void setArea(int area) {
        this.area = area;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public List<List<Integer>> getAirforce() {
        return airforce;
    }

    public void setAirforce(List<List<Integer>> airforce) {
        this.airforce = airforce;
    }

    public List<String> getRecommend() {
        return recommend;
    }

    public void setRecommend(List<String> recommend) {
        this.recommend = recommend;
    }

    public List<String> getCondition() {
        return condition;
    }

    public void setCondition(List<String> condition) {
        this.condition = condition;
    }

    public List<String> getOther() {
        return other;
    }

    public void setOther(List<String> other) {
        this.other = other;
    }

    public ImagesEntity getImages() {
        return images;
    }

    public void setImages(ImagesEntity images) {
        this.images = images;
    }

    public List<RouteEntity> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteEntity> routes) {
        this.routes = routes;
    }

    /**
     * "images": {"banner":"MapBannerHD1-1.png","map":"MapHD1-1.png"}
     */
    public static class ImagesEntity {

        private String banner;
        private String map;

        public String getBanner() {
            return banner;
        }

        public void setBanner(String banner) {
            this.banner = banner;
        }

        public String getMap() {
            return map;
        }

        public void setMap(String map) {
            this.map = map;
        }
    }

    /**
     * "routes": [{"point":"A","notes":["..."],"tables":[{"rows":[["a","b"]]}]}]
     */
    public static class RouteEntity {

        private String point;
        private List<String> notes;
        private List<TableEntity> tables;

        public String getPoint() {
            return point;
        }

        public void setPoint(String point) {
            this.point = point;
        }

        public List<String> getNotes() {
            return notes;
        }

        public void setNotes(List<String> notes) {
            this.notes = notes;
        }

        public List<TableEntity> getTables() {
            return tables;
        }

        public void setTables(List<TableEntity> tables) {
            this.tables = tables;
        }
    }

    /**
     * "tables": [{"rows":[["舰队船数","去B概率"],["1","20%"]]}]
     */
    public static class TableEntity {

        private List<List<String>> rows;

        public List<List<String>> getRows() {
            return rows;
        }

        public void setRows(List<List<String>> rows) {
            this.rows = rows;
        }
    }
}
