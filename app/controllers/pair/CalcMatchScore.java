package controllers.pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import models.User;
import models.PracticeLanguage;

import org.bson.types.ObjectId;

public class CalcMatchScore implements Runnable 
{
	private ConcurrentHashMap<ObjectId, UserTable> UsrTabMap;
	private ArrayBlockingQueue<ObjectId> InPairQueue;
	// local sim parameters	
	public static final int 		TOTLANGNUM = 5; 	// total language number
	public static final int		MOSTCOMMLANGIDX= -1;		// for simulation illustration
	public static final double	LANGMATCHINC = 20; // language match score increment
	public static final double	COMMPRALANGMATCHINC = 40; // common practice language match score increment (positive)
	public static final double	COMMPRALANGLVLMISMATCHDEC = -20; // common practice language level mismatch decrement (negative)
	public static final double	PFLANGMATCHINC = 120; // perfect language match score increment
	public static final double	PairWaitTime= 0.1;	// (sec) waiting time between two pairing results
	
		
	public CalcMatchScore(ConcurrentHashMap<ObjectId, UserTable> UsrTabMap,ArrayBlockingQueue<ObjectId> InPairQueue)
	{
		this.UsrTabMap = UsrTabMap;
		this.InPairQueue = InPairQueue;
	}

	public void run()
	{
		ObjectId			OldUID, NewUID;
		UserTable 	OldUsrTab, NewUsrTab;
		User OldUserInfo, NewUserInfo;

		ArrayList<Integer> LangList0= new ArrayList<Integer>();
		ArrayList<Integer> LangList1= new ArrayList<Integer>();
		ArrayList<Integer> LangList2= new ArrayList<Integer>();
		ArrayList<Integer> LangList3= new ArrayList<Integer>();
	    int 		MatchCnt[]= new int[3];
	    double 	MatSco= 0.0;	// match score
	    int		PraLangSeq[]= new int[2];	// practice language sequence
	    int		PracLangLvl[]= new int[2];
		
	try
	{
		while (true)
		{
			
			NewUID= InPairQueue.take();
			if (UsrTabMap.containsKey(NewUID))
				continue;

			// new user joining talk queue
			NewUserInfo= User.getById(NewUID);
			NewUsrTab= new UserTable();
			NewUsrTab.MSList= new ConcurrentHashMap<ObjectId, MSData>();
			NewUsrTab.UInfo= NewUserInfo;
			NewUsrTab.JoinTime= System.currentTimeMillis();

			// following: compare old v.s new users
// ctest , check if new user is still available?
			if (NewUserInfo!= null)
//			if (PairTable.PairWaitTable.containsKey(NewUID) && NewUserInfo!= null)
			{
			    Iterator<Map.Entry<ObjectId, UserTable>> UTMIter = UsrTabMap.entrySet().iterator();
		        while (UTMIter.hasNext())
		        {
		        		// existing old user in the queue
		        		Map.Entry<ObjectId, UserTable> UTMEntry = UTMIter.next();
		        		OldUID= UTMEntry.getKey();
		        		OldUsrTab= UTMEntry.getValue();
		        		OldUserInfo= OldUsrTab.UInfo;
		        		
		        		HashMap<Integer,Integer> OldPraLang = new HashMap<Integer,Integer>();
		        		for (PracticeLanguage PraLang : OldUserInfo.getPracticeLanguage())
		        			OldPraLang.put(PraLang.getId(), PraLang.getLevel());
		        		HashMap<Integer,Integer> NewPraLang = new HashMap<Integer,Integer>();
		        		for (PracticeLanguage PraLang : NewUserInfo.getPracticeLanguage())
		        			NewPraLang.put(PraLang.getId(), PraLang.getLevel());
		        	    // old: practice v.s. native
		        	    LangList0.clear();
					LangList0.addAll(OldPraLang.keySet());
					LangList0.removeAll(OldUserInfo.getNativeLanguage());	// remove native languages from practice languages = true practice languages
					LangList0.retainAll(NewUserInfo.getNativeLanguage());	// find intersection of old's practice & new's native
					MatchCnt[0]= LangList0.size();
	        			// new: practice v.s. native
	        	    		LangList1.clear();
					LangList1.addAll(NewPraLang.keySet());
					LangList1.removeAll(NewUserInfo.getNativeLanguage());	// remove native languages from practice languages = true practice languages
					LangList1.retainAll(OldUserInfo.getNativeLanguage());	// find intersection of one's native & partner's practice
					MatchCnt[1]= LangList1.size();
	        			// common practice languages
    		        	    LangList2.clear();
					LangList2.addAll(OldPraLang.keySet());
					LangList2.removeAll(OldUserInfo.getNativeLanguage());	// remove native languages from practice languages = true practice languages
	        	    		LangList3.clear();
					LangList3.addAll(NewPraLang.keySet());
					LangList3.removeAll(NewUserInfo.getNativeLanguage());	// remove native languages from practice languages = true practice languages
					LangList2.retainAll(LangList3);	// common practice languages
					MatchCnt[2]= LangList2.size();
					
					MatSco= 0.0;
					// Set practice sequence
					if ((MatchCnt[0]*MatchCnt[1]) > 0)	// perfect match: two-sided match
					{
						PraLangSeq[0]= LangList0.get(0);
						PraLangSeq[1]= LangList1.get(0);
						MatSco+= MatchCnt[0]*MatchCnt[1]*PFLANGMATCHINC;	// perfect match score
					}
					else if (MatchCnt[0] > 0)	// old's one-sided match
					{
						PraLangSeq[0]= LangList0.get(0);
        					MatSco+= LANGMATCHINC;	// one-sided match score
						if (MatchCnt[2] > 0)	// common practice lang exists
						{
							PraLangSeq[1]= LangList2.get(0);
							MatSco+= COMMPRALANGMATCHINC;	// common practice match score
							PracLangLvl[0]= OldPraLang.get(LangList2.get(0));
							PracLangLvl[1]= NewPraLang.get(LangList2.get(0));
							MatSco+= Math.abs(PracLangLvl[0]-PracLangLvl[1])*COMMPRALANGLVLMISMATCHDEC;	// level mismatch causes deduction
						}
						else if (MatchCnt[0] >= 2) // common practice non-existent, one-sided match more than 2 lang 
						{
							PraLangSeq[1]= LangList0.get(1);
							MatSco+= LANGMATCHINC;	// one-sided match score
						}
						else		// only one-sided, single-lang match exists
							PraLangSeq[1]= PraLangSeq[0];
					}	// else if
					else if (MatchCnt[1] > 0)	// new's one-sided match
					{
						PraLangSeq[0]= LangList1.get(0);
						MatSco+= LANGMATCHINC;	// one-sided match score
						if (MatchCnt[2] > 0)	// common practice lang exists
						{
							PraLangSeq[1]= LangList2.get(0);
							MatSco+= COMMPRALANGMATCHINC;	// common practice match score
							PracLangLvl[0]= OldPraLang.get(LangList2.get(0));
							PracLangLvl[1]= NewPraLang.get(LangList2.get(0));
							MatSco+= Math.abs(PracLangLvl[0]-PracLangLvl[1])*COMMPRALANGLVLMISMATCHDEC;	// level mismatch causes deduction
						}
						else if (MatchCnt[1] >= 2) // common practice non-existent, one-sided match more than 2 lang 
						{
							PraLangSeq[1]= LangList1.get(1);
							MatSco+= LANGMATCHINC;	// one-sided match score
						}
						else		// only one-sided, single-lang match exists
							PraLangSeq[1]= PraLangSeq[0];
					}	// else if
					else		// MatchCnt[0]=MatchCnt[1]=0: no match at all, consider common practice lang
					{
						MatSco+= MatchCnt[2]*COMMPRALANGMATCHINC;	// common practice match score
						if (MatchCnt[2] >= 2)
						{
							PraLangSeq[0]= LangList2.get(0);
							PraLangSeq[1]= LangList2.get(1);
							PracLangLvl[0]= OldPraLang.get(LangList2.get(0));
							PracLangLvl[1]= NewPraLang.get(LangList2.get(0));
							MatSco+= Math.abs(PracLangLvl[0]-PracLangLvl[1])*COMMPRALANGLVLMISMATCHDEC;	// level mismatch causes deduction
							PracLangLvl[0]= OldPraLang.get(LangList2.get(1));
							PracLangLvl[1]= NewPraLang.get(LangList2.get(1));
							MatSco+= Math.abs(PracLangLvl[0]-PracLangLvl[1])*COMMPRALANGLVLMISMATCHDEC;	// level mismatch causes deduction
						}
						else if (MatchCnt[2] == 1)
						{
							PraLangSeq[0]= LangList2.get(0);
							PraLangSeq[1]= PraLangSeq[0];
							PracLangLvl[0]= OldPraLang.get(LangList2.get(0));
							PracLangLvl[1]= NewPraLang.get(LangList2.get(0));
							MatSco+= Math.abs(PracLangLvl[0]-PracLangLvl[1])*COMMPRALANGLVLMISMATCHDEC;	// level mismatch causes deduction
						}
						else		// no match, no common practice
						{
							PraLangSeq[0]= MOSTCOMMLANGIDX;
							PraLangSeq[1]= MOSTCOMMLANGIDX;
						}
					}	// else
	        			MatSco+= 0.01*Math.random() ;	// add negligible random value to avoid duplication
	        			OldUsrTab.MSList.put(NewUID, new MSData(MatSco, PraLangSeq[0], PraLangSeq[1]));
	        			MatSco+= 0.01*Math.random() ;	// add negligible random value to avoid duplication
	        			NewUsrTab.MSList.put(OldUID, new MSData(MatSco, PraLangSeq[0], PraLangSeq[1]));
		        }	// while
			}	// if			
			
			UsrTabMap.put(NewUID, NewUsrTab);
		}	// while
	}	// try
	catch (Exception e)
	{
		System.out.println(e);
	}

	}	// CalcMatchScore
}	