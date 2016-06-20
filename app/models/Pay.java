package models;

import java.util.Date;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

//@lombok.Getter
public class Pay  extends Model {
    @Id
    private ObjectId _id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private int amount;
    private String state;
    private String approvalLink;
    private String paypalPaymentId;
    
    protected String paypalCreateResponseData;
    protected String paypalExecuteResponseData;

    
    public Pay() {
    }
    
    public Pay(ObjectId userId, int amount){//, String paypal_paymentID) {
        this._id = new ObjectId();
        //this.paypal_paymentID = paypal_paymentID;
        this.userId = userId;
        this.amount = amount;
    }
    
    public void save() {
        MongoCollection payCol = jongo.getCollection("pay");
        payCol.save(this);
    }

    /*public static Pay get(ObjectId obj_id) {
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
    
    public static Pay get(ObjectId obj_id) {
        MongoCollection payCol = jongo.getCollection("pay");
        Pay pay = payCol.findOne(obj_id).as(Pay.class);

        return pay;
    }
    
    public String getId(){
        return _id.toString();
    }
    
    public void setPaypalPaymentId(String paypal_id){
        this.paypalPaymentId = paypal_id;
    }
    
    public void setState(String state){
        this.state = state;
    }
    
    public int getAmount(){
        return amount;
    }
    
    public void setPaypalCreateResponseData(String responseData){
        this.paypalCreateResponseData = responseData;
    }
    
    public void setPaypalExecuteResponseData(String responseData){
        this.paypalExecuteResponseData = responseData;
    }
    
    public void setApprovalLink(String link){
        this.approvalLink = link;
    }
}
