import java.net.InetSocketAddress;

public abstract class SequencerCommon {

    //
    /*
     (3)  Sequencer multi-case the messages to all HotelServers
        Packet Format: “SEQ:<seq#>\tFEAddr:IPAddress-port\n<Original request packet>”

    send the packet to each server... stored the count of  requests = multicasted



    (4)  Sequencer receives a respond from one of the HotelServers.

    Packet Format: “SEQ:<seq#>\tFEAddr:IPAddress-port\tTYPE:<msg_type>\tSERVERID:<id>\n<Original respond packet>”
         SEQ:0\tFEAddr:0-0\tTYPE:ADD_SERVER\tSERVERID:2\tSERVERAddr:localhost-0\n
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
   
    public static String getFEAddr (String message) {
       
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
   
    public static String getMessageType (String message) {
        int i = message.indexOf("TYPE:");
        if (i<0) return "";
       
        int j = message.indexOf("\t",i+1);
        if (j<0) return "";
       
        return message.substring(i+5,j);
    }
   
    public static int getServerID (String message) {
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
           
    }
}