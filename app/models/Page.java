package models;

import java.util.List;

@lombok.Getter
public class Page extends Model {
    private List<? extends Model> data;
    private Paging paging;

    public Page(List<? extends Model> data) {
        this.data = data;
        this.paging = new Paging(null, null);
    }

    public Page(List<? extends Model> data, String previous) {
        this.data = data;
        this.paging = new Paging(previous, null);
    }

    public Page(List<? extends Model> data, String previous, String next) {
        this.data = data;
        this.paging = new Paging(previous, next);
    }
}
