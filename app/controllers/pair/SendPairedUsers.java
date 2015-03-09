package controllers.pair;

import java.util.concurrent.ArrayBlockingQueue;

public class SendPairedUsers implements Runnable
{
	private ArrayBlockingQueue<PairedTalkData> OutPairQueue;
	public SendPairedUsers(ArrayBlockingQueue<PairedTalkData> OutPairQueue)
	{
		this.OutPairQueue = OutPairQueue;
	}
	
	public void run()
	{
    	    while(true)    	    	
        	{
    	    		try
    	    		{
	        		PairedTalkData pairedData = OutPairQueue.take();
	    	    	    controllers.VideoChatController.pair(pairedData);
    	    		}
    	    		catch (Exception e)
    	    		{
    	    			
    	    		}
    	    }
	}
}