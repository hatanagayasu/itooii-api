package controllers.pair;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;

public class PairResult implements Runnable 
{
	private ConcurrentHashMap<ObjectId, UserTable> UsrTabMap;
	private ArrayBlockingQueue<PairedTalkData> OutPairQueue;
	public static final double	WaitScoFac= 5;	// (1/sec) waiting factor
	public static final double	MATCHSCORETHD = 80; // match score threshold for qualified
	static long CntTotalPaired;

	public PairResult (ConcurrentHashMap<ObjectId, UserTable> UsrTabMap,ArrayBlockingQueue<PairedTalkData> OutPairQueue)
	{
		this.UsrTabMap = UsrTabMap;
		this.OutPairQueue = OutPairQueue;
		CntTotalPaired = 0;
	}	

	public void run()
	{
		while(true) 
		{
			try 
			{
				Pair();
	            Thread.sleep(5000L);
			} 	// try
			catch (InterruptedException iex) 
			{
//		    		logger.error("InterruptedException");
//		    		Running = !PairingLocalSimEnable;
			}	// catch
		}//while
	}	// run 	
	

	public void Pair()
	{
	    ConcurrentHashMap<ObjectId, MSData> UMSList;
	    UserTable UsrTab;
	    ObjectId UID;
	    double WaitScore;
		long CurrTime= System.currentTimeMillis();

		// store qualified paired users (>= threshold)
//	    System.out.println("--- Start Pairing");
	    MSData FinMatRes;
	    TreeSet<PairedTalkData> FinMSList = new TreeSet<PairedTalkData>();
	    Iterator<Map.Entry<ObjectId, UserTable>> UTMIter = UsrTabMap.entrySet().iterator();
	    while (UTMIter.hasNext())
	    {
	    		Map.Entry<ObjectId, UserTable> UTMEntry = UTMIter.next();
	    		UID= UTMEntry.getKey();
	    		UsrTab= UTMEntry.getValue();
	    		UMSList= UsrTab.MSList;
	    		WaitScore= WaitScoFac*(CurrTime- UsrTab.JoinTime)/1000.0;
	    		for (ObjectId MatchId : UMSList.keySet())
	    		{
	    			FinMatRes = UMSList.get(MatchId);
	    			if (FinMatRes.Score > MATCHSCORETHD-WaitScore)
	    			{
	    				FinMSList.add(new PairedTalkData(FinMatRes.Score+WaitScore, UID, MatchId, FinMatRes.lang0, FinMatRes.lang1));
	    			}
	    		}
	    }	// while
	    
	    // extract finally qualified according to match scores
	    // notice: some qualified matches may involve duplicate users (hot users), duplication is resolved below
	    ArrayList<PairedTalkData> PairedUserData= new ArrayList<PairedTalkData>();
		ArrayList<ObjectId> PairedUserList= new ArrayList<ObjectId>();
		for (PairedTalkData FinPair : FinMSList)
		{
			if (!PairedUserList.contains(FinPair.getOfferId()) && !PairedUserList.contains(FinPair.getAnswerId()))
			{
				PairedUserList.add(FinPair.getOfferId());
				PairedUserList.add(FinPair.getAnswerId());
				PairedUserData.add(FinPair);
			}	// if
		}
	    
	    // remove all paired users
	    Iterator<ObjectId> PULIter0= PairedUserList.iterator();
	    while (PULIter0.hasNext())
	    {
	    		UsrTabMap.remove(PULIter0.next());
	    }
	    
	    // remove all match scores of the paired users in the remaining users
	    	for (ObjectId UserId : UsrTabMap.keySet())
	    	{
	    		UsrTab = UsrTabMap.get(UserId);
	    		for (ObjectId MatchId : UsrTab.MSList.keySet())
	    		{
	    			if (PairedUserList.contains(MatchId))	// pick the match score related to paired users and remove it
	    			{
	    				UsrTab.MSList.remove(MatchId);
	    			}
	    		}
	    }	// while
	    
//	    System.out.println("PairedTalkQueue= "+PairedTalkQueue.UID);
	    	Iterator<PairedTalkData> PILIter= PairedUserData.iterator();
	    while (PILIter.hasNext())
	    {
	    		PairedTalkData PInfo= PILIter.next();
	    		try
	    		{
	    			OutPairQueue.put(PInfo);
	    		}
	    		catch(Exception e)
	    		{
	    			
	    		}
//	    		if (PairingLocalSimEnable)
//	    		{
	    		    System.out.println("****** Pairing Successful");
			    System.out.println("PInfo= "+PInfo.getScore()+" "+PInfo.getOfferId()+" "+PInfo.getAnswerId()+" "+PInfo.getLang0()+" "+PInfo.getLang1());
				CntTotalPaired += 2;
				System.out.println("CntTotalPaired = "+CntTotalPaired);
//	    		}
	    }
	    System.out.println("UsrTabMap size = "+UsrTabMap.size());

	}	// PairResult	
	
}	// LangPair
