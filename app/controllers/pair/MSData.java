package controllers.pair;
import org.bson.types.ObjectId;


public class MSData
{
	ObjectId MatchId;
	int lang0, lang1;
	
	MSData(ObjectId MatchId, int lang0, int lang1)
	{
		this.MatchId = MatchId;
		this.lang0 = lang0;
		this.lang1 = lang1;
	}
}
