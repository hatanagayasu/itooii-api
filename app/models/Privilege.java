package models;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Privilege {
    Gold(40),
    Premium(30),
    Member(20),
    Observer(10),
    Anonymous(0);

    private int weight;

    private static Map<Integer,Privilege> mapping = new HashMap<Integer,Privilege>();

    static {
        for(Privilege privilege : values())
            mapping.put(privilege.getWeight(), privilege);
    }

    private Privilege(int weight) {
        this.weight = weight;
    }

    @JsonCreator
    public static Privilege create(int weight) {
        Privilege privilege = mapping.get(weight);

        return privilege == null ? Observer : privilege;
    }

    @JsonValue
    public int getWeight() {
        return weight;
    }
}
