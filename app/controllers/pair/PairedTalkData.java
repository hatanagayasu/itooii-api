package controllers.pair;

import org.bson.types.ObjectId;

@lombok.Getter
public class PairedTalkData
{
    private ObjectId offerId;
    private ObjectId answerId;
    private int lang0, lang1;
    
    PairedTalkData(ObjectId offerId, ObjectId answerId, int lang0, int lang1)
    {
    	    this.offerId = offerId;
    	    this.answerId = answerId;
    	    this.lang0 = lang0;
    	    this.lang1 = lang1;
    }
}