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

	public PairResult (ConcurrentHashMap<ObjectId, UserTable> UsrTabMap,ArrayBlockingQueue<PairedTalkData> OutPairQueue)
	{
		this.UsrTabMap = UsrTabMap;
		this.OutPairQueue = OutPairQueue;
	}	

	public void run()
	{
		while(true) 
		{
			try 
			{
				Pair();
	            Thread.sleep(3000L);
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
		TreeMap<Double, PairedTalkData> FinMSList= new TreeMap<Double, PairedTalkData>();	// final match score list
	    TreeMap<Double, MSData> UMSList;
	    UserTable UsrTab;
	    ObjectId UID;
	    double WaitScore;
		long CurrTime= System.currentTimeMillis();

		// store qualified paired users (>= threshold)
//	    System.out.println("--- Start Pairing");
	    MSData FinMatRes;
	    Iterator<Map.Entry<ObjectId, UserTable>> UTMIter = UsrTabMap.entrySet().iterator();
	    while (UTMIter.hasNext())
	    {
	    		Map.Entry<ObjectId, UserTable> UTMEntry = UTMIter.next();
	    		UID= UTMEntry.getKey();
	    		UsrTab= UTMEntry.getValue();
	    		UMSList= UsrTab.MSList;
	    		WaitScore= WaitScoFac*(CurrTime- UsrTab.JoinTime)/1000.0;
	    		SortedMap<Double, MSData> UPairList =UMSList.tailMap(MATCHSCORETHD-WaitScore);	// only get those exceeding threshold after adding WaitScore
	    	    Iterator<Map.Entry<Double, MSData>> UPLIter = UPairList.entrySet().iterator();
	    	    while (UPLIter.hasNext())
	    	    {
	    	    		Map.Entry<Double, MSData> UPLEntry= UPLIter.next();
	    	    		FinMatRes= UPLEntry.getValue();
	    	    		FinMSList.put(UPLEntry.getKey()+WaitScore, new PairedTalkData(UID, FinMatRes.MatchId, FinMatRes.lang0, FinMatRes.lang1));
	    	    }
	    }	// while
	    
	    // extract finally qualified according to match scores
	    // notice: some qualified matches may involve duplicate users (hot users), duplication is resolved below
	    ArrayList<PairedTalkData> PairedUserData= new ArrayList<PairedTalkData>();
		ArrayList<ObjectId> PairedUserList= new ArrayList<ObjectId>();
		PairedTalkData FinPair ;
		NavigableMap<Double, PairedTalkData> FinMSListDes= FinMSList.descendingMap();
	    Iterator<Map.Entry<Double, PairedTalkData>> FMSLIter = FinMSListDes.entrySet().iterator();
	    while (FMSLIter.hasNext())
	    {
    			Map.Entry<Double, PairedTalkData> FMSLEntry= FMSLIter.next();
    			FinPair= FMSLEntry.getValue();
    			if (!PairedUserList.contains(FinPair.getOfferId()) && !PairedUserList.contains(FinPair.getAnswerId()))
    			{
    				PairedUserList.add(FinPair.getOfferId());
    				PairedUserList.add(FinPair.getAnswerId());
    				PairedUserData.add(FinPair);  			
    			}	// if
	    }	// while
	    
	    // remove all paired users
	    Iterator<ObjectId> PULIter0= PairedUserList.iterator();
	    while (PULIter0.hasNext())
	    {
	    		UsrTabMap.remove(PULIter0.next());
	    }
	    
	    // remove all match scores of the paired users in the remaining users
	    Iterator<Map.Entry<ObjectId, UserTable>> UTMIter1 = UsrTabMap.entrySet().iterator();
	    while (UTMIter1.hasNext())
	    {
	    		UsrTab= UTMIter1.next().getValue();
	    		Iterator<Map.Entry<Double, MSData>> UTMSLIter= UsrTab.MSList.entrySet().iterator();
	    		while (UTMSLIter.hasNext())
	    		{
	    			if (PairedUserList.contains(UTMSLIter.next().getValue()))	// pick the match score related to paired users and remove it
	    			{
	    				UTMSLIter.remove();
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
//	    		    System.out.println("****** Pairing Successful");
//			    System.out.println("PInfo= "+PInfo[0]+" "+PInfo[1]+" "+PInfo[2]+" "+PInfo[3]);
//			    UserInfo PUInfo0= PairTable.GetUserInfo(PInfo[0]);
//			    UserInfo PUInfo1= PairTable.GetUserInfo(PInfo[1]);
//			    System.out.println("PUInfo1 Nat-Pra= "+PUInfo0.NatLangList+" "+PUInfo0.PraLangMap);
//			    System.out.println("PUInfo1 Nat-Pra= "+PUInfo1.NatLangList+" "+PUInfo1.PraLangMap);
//	    		}
	    }

	}	// PairResult
	

	
}	// LangPair
