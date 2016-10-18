package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.types.ObjectId;

import play.Play;
import models.Coin;
import models.Model;
import models.Pay;
import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.api.payments.Amount;
import com.paypal.api.payments.Details;
import com.paypal.api.payments.Item;
import com.paypal.api.payments.ItemList;
import com.paypal.api.payments.Links;
import com.paypal.api.payments.Payer;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentExecution;
import com.paypal.api.payments.PaymentHistory;
import com.paypal.api.payments.RedirectUrls;
import com.paypal.api.payments.Transaction;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.OAuthTokenCredential;
import com.paypal.base.rest.PayPalRESTException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class PaypalController extends AppController {

    static OAuthTokenCredential tokenCredential;

    static {        
        try {
            tokenCredential = Payment.initConfig(new File("conf/sdk_config.properties"));
        } 
        catch (PayPalRESTException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public static Result create(JsonNode params) {

        String total = params.get("amount").asText();
        String Product = "Speakaa coin";

        Payer payer = new Payer();
        payer.setPaymentMethod("paypal");
        
        Item item = new Item();
        item.setName(Product).setQuantity("1").setCurrency("USD").setPrice(total);
        ItemList itemList = new ItemList();
        List<Item> items = new ArrayList<Item>();
        items.add(item);
        itemList.setItems(items);
        
        Details details = new Details();
        details.setSubtotal(total);

        Amount amount = new Amount();
        amount.setCurrency("USD");
        amount.setTotal(total);
        amount.setDetails(details);
         
        Transaction transaction = new Transaction();
        transaction.setDescription("creating a payment of " + total + " usd for speakaa coins");
        transaction.setAmount(amount);
        transaction.setItemList(itemList);
        //transaction.setInvoiceNumber("12233454323");

        ObjectId myId = params.has("access_token") ? getMe(params).getId() : null;
        Pay pay = new Pay(myId, params.get("amount").asInt());
        RedirectUrls redirectUrls = new RedirectUrls();
        
        String access_token = params.get("access_token").asText();
        String api_server = Model.props.getProperty("api_server").replaceAll("\"",  "");
        redirectUrls.setCancelUrl(api_server + "paypal/" + pay.getId() + "/false?access_token="+access_token);
        redirectUrls.setReturnUrl(api_server + "paypal/" + pay.getId() + "/true?access_token="+access_token);
        
        List<Transaction> transactions = new ArrayList<Transaction>();
        transactions.add(transaction);

        Payment payment = new Payment();
        payment.setIntent("sale");
        payment.setPayer(payer);
        payment.setTransactions(transactions);
        payment.setRedirectUrls(redirectUrls);

        ObjectNode message = mapper.createObjectNode();
        message.put("payId", pay.getId());
        
        try {
            String accessToken = tokenCredential.getAccessToken();
            APIContext apiContext = new APIContext(accessToken);
            Payment createdPayment = payment.create(apiContext);
            
            Iterator<Links> links = createdPayment.getLinks().iterator();
            while (links.hasNext()) {
                Links link = links.next();
                if (link.getRel().equalsIgnoreCase("approval_url")) {
                    String urlpass = link.getHref();
                    
                    message.put("status", "created");
                    message.put("approval_url", urlpass);
                    pay.setApprovalLink(urlpass);
                }
            }
            pay.setPaypalPaymentId(createdPayment.getId());
            pay.setPaypalCreateResponseData( createdPayment.getLastResponse() );//createdPayment.toString() );
            pay.setState("Created");
            
            //return redirect(urlpass);

        } catch (PayPalRESTException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            message.put("status", "error");
            message.put("message", e.toString());
        }
        
        pay.save();
        return Ok(message);
    }

    public static Result pay(JsonNode params) {
        Boolean success = params.get("success").asBoolean();
        String payid = params.get("payid").asText();
        String paymentId = params.get("paymentId").asText();
        String token = params.get("token").asText();
        String PayerID = params.get("PayerID").asText();
        
        //String message="";
        Pay pay = Pay.get( new ObjectId(payid));
        
        ObjectNode message = mapper.createObjectNode();
        message.put("payId", pay.getId());
        
        if(success == false){
            //transaction cancelled?
            message.put("status", "cancelled");
            return Ok(message.toString());
        }
        
        try {
            
            String accessToken = tokenCredential.getAccessToken();
            
            //Payment payment = new Payment();//Payment.get(token, paymentId);// new Payment();
            //payment.setId(map.get(guid));
            Payment payment  = Payment.get(accessToken, paymentId);
            PaymentExecution paymentExecute = new PaymentExecution();
            paymentExecute.setPayerId(PayerID);
            
            APIContext apiContext = new APIContext(accessToken);//, requestId);
            payment = payment.execute(apiContext, paymentExecute);
            
            // get last 10 payment history
            //Map<String, String> containerMap = new HashMap<String, String>();
            //containerMap.put("count", "10");
            //PaymentHistory paymentHistory = Payment.list(accessToken,
            //        containerMap);
            
            String state = payment.getState();
            pay.setState(state);
            pay.setPaypalExecuteResponseData( payment.getLastResponse() );
            
            message.put("status", state);
            
            ObjectId userId = params.has("access_token") ? getMe(params).getId() : null;
            
            Coin coin = new Coin(userId, new ObjectId(pay.getId()), pay.getAmount(), new Date());
            coin.save();
            
        } 
        catch (PayPalRESTException pex) {

            pex.printStackTrace();
            //String req = Payment.getLastRequest();
            message.put("error message", pex.getMessage());
            //message.put("fail reason", payment.getFailureReason());
        }

        pay.save();
        return Ok(message);
    }
    
    public static Result get(JsonNode params) {

        Pay pay = Pay.get( new ObjectId(params.get("payid").asText()) );
        return Ok(pay);
    }
}