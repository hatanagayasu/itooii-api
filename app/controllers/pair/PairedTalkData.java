package controllers.pair;

import org.bson.types.ObjectId;

@lombok.Getter
public class PairedTalkData implements Comparable<PairedTalkData>
{
	private Double Score;
    private ObjectId offerId;
    private ObjectId answerId;
    private int lang0, lang1;
    
    PairedTalkData(Double Score, ObjectId offerId, ObjectId answerId, int lang0, int lang1)
    {
    		this.Score = Score;
    	    this.offerId = offerId;
    	    this.answerId = answerId;
    	    this.lang0 = lang0;
    	    this.lang1 = lang1;
    }
    
    public int compareTo(PairedTalkData S) 
    {
        return (int) (S.Score - Score);
    }
}