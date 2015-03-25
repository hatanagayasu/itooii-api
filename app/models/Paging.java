package models;

@lombok.Getter
public class Paging extends Model {
    private static final long serialVersionUID = -1;
    private String previous;
    private String next;

    public Paging(String previous, String next) {
        this.previous = previous;
        this.next = next;
    }
};
