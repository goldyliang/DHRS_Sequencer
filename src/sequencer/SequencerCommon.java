package sequencer;


public abstract class SequencerCommon {

    //
    /*
     *
     * 

     
     Request message from FE -> Sequencer(message body)
     <Request message type>
     <property>:<value>
     ....
     
     Where <Request message type> is defined in GeneralMessage.MessageType
     <property> is defined in GeneralMessage.PropertyName
     
     
     Message from Sequence->Server:
     SEQ:<seq#>\t                   <------------ header
     <Request message type>         <--------body message type
     <property>:<value>             <--------body (properties)
     ....                           <--------body
     
     Packet deliver to Server RM
     <Request message type>         <--------- only body
     <proerty>:<value>
     ...
     
     Response from Server RM:
     RESPOND                       <-------- body message type (RESPOND)
     <property>:<value>
     ...
     
     
     Response from server -> Sequencer
     SEQ:<seq#>\tTYPE:RESPOND\t     <-------- header   header message type RESPOND
     RESPOND                        <----------- body message type
     <property>:<value>
     ..
     ..
     
     Response from sequencer -> FE
     RESPOND                        <---------- body message type
     <property>:<value>
     ..
     ..
     
     NACK from server to Sequencer
     SEQ:<seq#>\tTYPE:NACK\t
     
     Control message from Server to Sequencer
     TYPE:RMCTRL\t       <-------------- header with message type RMCTRL
     <ctrl message type>              <------------ body message type 
     SERVERID:<id>
     <other property>:<value>
     ...
     
     where <ctrl message type> = 
       ADD_SERVER, RMV_SERVER, PAUSE ... (defined in GeneralMessage.MessageType)
     
     Control message broadcasted from Sequencer to servers
     SEQ:<seq#>\tTYPE:RMCTRL\t    <-------- header with message type (RMCTRL)
     <ctrl message type>
     SERVERID:<id>
     ...
     
     Control message deliver to server RM
     
     <ctrl message type>
     SERVERID:<id>
     ...
     
     */
 
    public static long getSeqNum (String message) {
       
        int i = message.indexOf(":");
       
        if (i<=0) return -1;
       
        if (!message.substring(0,i).equals("SEQ")) return -1;
       
        int j = message.indexOf("\t", i+1);
       
        if (j<=0) return -1;
       
        long seqNum = Integer.valueOf(message.substring(i+1,j));
       
        return seqNum;
    }
   
    public static String getMessageBody (String message) {
       
        int i = message.indexOf("\n");
       
        if (i<=0) return null;
       
        return message.substring(i+1);
    }
   
/*    public static String getFEAddr (String message) {
       
        int i = message.indexOf("\t");
       
        if (i<=0) return null;
       
        int j = message.indexOf(":", i+1);
        if (j<=0) return null;
       
        if (!message.substring(i+1,j).equals("FEAddr")) return null;
       
        int k = message.indexOf("\n", j+1);
       
        if (k<=0) return null;
       
        String addr = message.substring(j+1,k);
       
        return addr;
    }
   
    public static InetSocketAddress getFESocketAddr (String message) {
        //TODO
        return null;
    }
 */


	public static String getMessageType (String message) {
		String tag = "TYPE:";
		int i = message.indexOf(tag);
		if (i<0) return "";
		
		int j = message.indexOf("\t",i+1);
		if (j<0) return "";
		
		return message.substring(i+ tag.length(),j);
	}
   
/*    public static int getServerID (String message) {
        String tag = "SERVERID:";

        int i = message.indexOf(tag);
        if (i<0) return 0;
       
        int j = message.indexOf("\t",i+1);
        if (j<0) return 0;
       
        return Integer.valueOf(message.substring(i + tag.length(),j));
    }
   
    public static InetSocketAddress getServerSocketAddress (String message) {
        String tag = "SERVERADDR:";
       
        int i = message.indexOf(tag);
        if (i<0) return null;
       
        int j = message.indexOf("\t",i+1);
       
        String addr = message.substring(i + tag.length(), j);
       
        int k = addr.indexOf("-");
       
        InetSocketAddress sockAddr = new InetSocketAddress (
                addr.substring(0, k),
                Integer.valueOf(addr.substring(k+1)) );
               
        return sockAddr;
           
    } */
    
    public static String getBodyMessageType (String message) {
    	String content = message.substring(message.indexOf("\n")+1);

    	int i = content.indexOf("\n");
    	String type = content.substring(0, i);
    	return type;
    }
    
    // whether it is required to send back respond to FE
    // Not ideal solution, though
    public static boolean ackToFERequired (String message) {
    	String bodyMsgType = getBodyMessageType (message);
    	
    	// If the message is "REPORT_SUSPECTED_RESPOND", or "REPORT_NO_RESPOND",
    	// Do not send back to FE
    	if (bodyMsgType.indexOf("REPORT_") >= 0)
    		return false;
    	else
    		return true;
    }

    public static int getBodyServerID (String message) {
    	String content = message.substring(message.indexOf("\n")+1);
    	
    	String tag = "SERVERID:";
    	int i = content.indexOf(tag);
    	if (i<0) return -1;
    	
    	int j = content.indexOf("\n",i+1);
    	if (j<0) return -1;
    	
    	return Integer.valueOf(content.substring(i + tag.length(),j));
    }

/*
public static int getHeaderServerID (String message) {
	String tag = "SERVERID:";

	int i = message.indexOf(tag);
	if (i<0) return -1;
	
	int j = message.indexOf("\t",i+1);
	if (j<0) return -1;
	
	return Integer.valueOf(message.substring(i + tag.length(),j));
} */



/*	public static InetSocketAddress getServerSocketAddress (String message) {
	String tag = "SERVERADDR:";
	
	int i = message.indexOf(tag);
	if (i<0) return null;
	
	int j = message.indexOf("\t",i+1);
	
	String addr = message.substring(i + tag.length(), j);
	
	int k = addr.indexOf("-");
	
	InetSocketAddress sockAddr = new InetSocketAddress (
			addr.substring(0, k),
			Integer.valueOf(addr.substring(k+1)) );
			
	return sockAddr;
		
} */

}