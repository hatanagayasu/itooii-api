package models;

public enum Privilege {
    Gold(40),
    Premium(30),
    Member(20),
    Observer(10),
    Suspended(5),
    Anonymous(0);

    private int privilege;

    private Privilege(int privilege) {
        this.privilege = privilege;
    }

    public int value() {
        return privilege;
    }
}
