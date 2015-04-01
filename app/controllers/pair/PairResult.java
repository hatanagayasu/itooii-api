package controllers.pair;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;

public class PairResult implements Runnable {
    private ConcurrentHashMap<ObjectId, UserTable> UsrTabMap;
    private ArrayBlockingQueue<PairedTalkData> OutPairQueue;
    public static final double WaitScoFac = 2; // (1/sec) waiting factor
    public static final double MATCHSCORETHD = 80; // match score threshold for qualified
    static long CntTotalPaired;

    public PairResult(ConcurrentHashMap<ObjectId, UserTable> UsrTabMap,
        ArrayBlockingQueue<PairedTalkData> OutPairQueue) {
        this.UsrTabMap = UsrTabMap;
        this.OutPairQueue = OutPairQueue;
        CntTotalPaired = 0;
    }

    public void run() {
        while (true) {
            try {
                Pair();
                Thread.sleep(5000L);
            } // try
            catch (InterruptedException iex) {
//		    		logger.error("InterruptedException");
            } // catch
        }//while
    } // run

    public void Pair() {
        long CurrTime = System.currentTimeMillis();

        // store qualified paired users (>= threshold)
        TreeSet<PairedTalkData> FinMSList = new TreeSet<PairedTalkData>();
        UsrTabMap.forEach((UID, UsrTab) -> {
            double WaitScore = WaitScoFac * (CurrTime - UsrTab.JoinTime) / 1000.0;
            UsrTab.MSList.forEach((MatchId, FinMatRes) -> {
                if (FinMatRes.Score > (MATCHSCORETHD - WaitScore)) {
                    FinMSList.add(new PairedTalkData(FinMatRes.Score + WaitScore, UID, MatchId,
                        FinMatRes.lang0, FinMatRes.lang1));
                }
            });
        });

        // extract finally qualified according to match scores
        // notice: some qualified matches may involve duplicate users (hot users), duplication is resolved below
        ArrayList<PairedTalkData> PairedUserData = new ArrayList<PairedTalkData>();
        ArrayList<ObjectId> PairedUserList = new ArrayList<ObjectId>();
        FinMSList.forEach(FinPair -> {
            if (!PairedUserList.contains(FinPair.getOfferId())
                && !PairedUserList.contains(FinPair.getAnswerId())) {
                PairedUserList.add(FinPair.getOfferId());
                PairedUserList.add(FinPair.getAnswerId());
                PairedUserData.add(FinPair);
            }
        });
        // remove all paired users
        PairedUserList.forEach(UserId -> UsrTabMap.remove(UserId));

        // remove all match scores of the paired users in the remaining users
        UsrTabMap.forEach((UserId, UsrTab) ->
            UsrTab.MSList.keySet().forEach(MatchId -> {
                if (PairedUserList.contains(MatchId))
                    UsrTab.MSList.remove(MatchId);
            })
        );

        PairedUserData.forEach(PInfo -> {
            try {
                OutPairQueue.put(PInfo);
            } catch (Exception e) {

            }
            System.out.format("*** PInfo= %.2f", PInfo.getScore());
            System.out.println(" " + PInfo.getOfferId() + " " + PInfo.getAnswerId() + " "
                + PInfo.getLang0() + " " + PInfo.getLang1());
            CntTotalPaired += 2;
        });
        if (PairedUserData.size() > 0) {
            System.out.println("CntTotalPaired = " + CntTotalPaired
                + ", Paired users count this time = " + 2 * PairedUserData.size());
            System.out.println("UsrTabMap size = " + UsrTabMap.size());
            System.out.println("Current Date: "
                + new SimpleDateFormat("E yyyy.MM.dd 'at' hh:mm:ss a zzz")
                    .format(new Date()));
        }

    } // PairResult

} // LangPair
