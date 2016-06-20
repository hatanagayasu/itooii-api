package models;

import java.util.Date;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

//@lombok.Getter
public class Coin  extends Model {
    @Id
    private ObjectId _id;
    private ObjectId payId;
    private ObjectId userId;
    private int amount;
    private Date transationDate;
    
    public Coin() {
    }
    
    public Coin(ObjectId userId, ObjectId payId, int amount, Date transactionDate){
        this._id = new ObjectId();
        
        this.userId = userId;
        this.payId = payId;
        this.amount = amount;
        this.transationDate = transactionDate;
    }
    
    public void save() {
        MongoCollection coinCol = jongo.getCollection("coin");
        coinCol.save(this);
    }

    /*public static Coin get(ObjectId obj_id) {
        String key = "payment:" + obj_id;

        Pay pay = cache(key, Pay.class, new Callable<Pay>() {
            public Pay call() {
                MongoCollection payCol = jongo.getCollection("pay");

                Pay pay = payCol.findOne(obj_id).as(Pay.class);

                return pay;
            }
        });

        return pay;
    }*/
    

}
