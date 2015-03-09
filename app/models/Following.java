package models;

import java.util.Date;

import org.bson.types.ObjectId;

@lombok.Getter
public class Following extends Model
{
    private ObjectId id;
    private Date created;

    public Following()
    {
    }

    public Following(ObjectId id)
    {
        this.id = id;
        created = new Date();
    }
}
