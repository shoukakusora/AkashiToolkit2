package rikka.akashitoolkit.model;

import java.util.List;

/**
 * One entry of Drop.json: a map and the ships that drop on each of its nodes.
 *
 * <p>Example: {"map":"1-1 镇守府正面海域","nodes":[{"name":"A","ships":["吹雪","睦月"]}]}</p>
 */
public class DropStat {

    private String map;
    private List<Node> nodes;

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * One drop node, for example "A".
     */
    public static class Node {

        private String name;
        private List<String> ships;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getShips() {
            return ships;
        }

        public void setShips(List<String> ships) {
            this.ships = ships;
        }
    }
}
