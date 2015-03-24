package models;

import java.util.List;

public class Page extends Model
{
    private List<? extends Model> data;
    private Paging paging;

    public Page(List<? extends Model> data, String previous)
    {
        this(data, previous, null);
    }

    public Page(List<? extends Model> data, String previous, String next)
    {
        this.data = data;
        this.paging = new Paging(previous, next);
    }
}
