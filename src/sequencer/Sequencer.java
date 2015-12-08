package sequencer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.net.SocketAddress;

public class Sequencer{
	
	private int serrverPortID;
	private int fePortID;
	private Thread sequencer;
	private Thread received;
	private DatagramSocket serverSocket;
	private DatagramSocket feSocket;
	private HashMap<Long,BufferedPacket > Buffered_Packets = new HashMap<Long, BufferedPacket>();
	private long sequenceNumber; 
	private HashMap<Integer, InetSocketAddress> SocketAddress = new HashMap<Integer, InetSocketAddress>();
	
	
	private class BufferedPacket{
		
		DatagramPacket fwdPacket; // packet to be forwarded
		long timeStamp;
		int multicasted;
		int received;
		InetSocketAddress addrFE; // The FE address where the response shall be sent to. 
		                          //  Null if no response to FE required
	}

	public Sequencer(int serverPortId, int fePortID){
		
		this.serrverPortID = serverPortId;
		this.fePortID = fePortID;
		
		
	}
	
	// add socket address for multicast
	public void addMulticastSocketAddress(InetSocketAddress addr, int serverID){
		
		SocketAddress.put(serverID, addr);
		
	}
	
	//remove socket address from Multicast
public void removeMulticastSocketAddress(int serverID){
	
	SocketAddress.remove(serverID);
}

public void startSequencer() throws IOException{
	
	serverSocket = new DatagramSocket(serrverPortID);
	
	feSocket = new DatagramSocket(fePortID);
	
      sequencer = new Thread(new requestHandleThread());
      received = new Thread(new repondHandleThread()); 	
	
      sequencer.start();
      received.start();
	
}



private class requestHandleThread implements Runnable {
public void run(){
	while(true){
	byte[] buffer = new byte[1000];
	DatagramPacket request = new DatagramPacket(buffer, buffer.length);
	try {
		feSocket.receive(request);
		
		synchronized (this) {

			String pack = new String(request.getData(), 0, request.getLength());
		    System.out.println(pack);
	
			//InetSocketAddress FEAddr = (InetSocketAddress) request.getSocketAddress();
			String packFormat = "SEQ:"+ sequenceNumber + "\t\n" + pack; 
			
			sendPacket(packFormat, request, SequencerCommon.ackToFERequired(packFormat), null);
		}

	    	
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
   }

}
  }
   private class repondHandleThread implements Runnable{
	
	public void run(){
		byte[] buffer = new byte[1000];
		
		DatagramPacket message = new DatagramPacket(buffer, buffer.length);
		while(true){
		try {
			
			serverSocket.receive(message);

			synchronized (this) {
				String messa = new String(message.getData(), 0, message.getLength());
			    System.out.println("Respond Handle Thread " + messa);
	
				switch(SequencerCommon.getMessageType(messa)){
				
					case "RESPOND" :{
					
						long seqNum = SequencerCommon.getSeqNum(messa);
						
						BufferedPacket bpack = Buffered_Packets.get(seqNum);
						
					    if (bpack.addrFE != null){
					    	
						    String messaBody = SequencerCommon.getMessageBody(messa);
	
						    DatagramPacket messageSend = new DatagramPacket(messaBody.getBytes(), messaBody.getBytes().length);
						    
						    messageSend.setSocketAddress(bpack.addrFE);
						    
						    feSocket.send(messageSend);
						    System.out.println("sending Respond " + messa);
	
					    }
					    
						bpack.received++;
	
						if(bpack.received == bpack.multicasted)
							Buffered_Packets.remove(seqNum);
						break;
					}
					case "NACK": {	
		                long seqNum = SequencerCommon.getSeqNum(messa);
						
						BufferedPacket n1 = Buffered_Packets.get(seqNum);
						
						if (n1!=null) {
						
						    n1.timeStamp = System.currentTimeMillis();
						    
							DatagramPacket forwardRequest = n1.fwdPacket;
						    serverSocket.send(forwardRequest);
						    System.out.println("sending nack!!!" + messa);
						}
					 break;   
					}	
					case "RMCTRL":	{
						// for all RMCTRL messages need to multi-cast to all servers
						
						
						switch (SequencerCommon.getBodyMessageType(messa)) {
						case "ADD_SERVER":
							int serverID = SequencerCommon.getBodyServerID(messa);
							InetSocketAddress serverAddr = (InetSocketAddress) message.getSocketAddress();
							
							SocketAddress.put(serverID, serverAddr);
							
							String seqNum =  "SEQ:" + sequenceNumber + "\t" + messa;
							sendPacket(seqNum, message , false, null);
							break;
							
						case "RMV_SERVER":
							
						  seqNum =  "SEQ:" + sequenceNumber + "\t" + messa;
						  sendPacket(seqNum, message , false, null);
						  
						  int serverID1 = SequencerCommon.getBodyServerID(messa);
						  InetSocketAddress serverAddr1 = (InetSocketAddress) message.getSocketAddress();
						
						  SocketAddress.remove(serverID1);
						  break;
						  
						case "PAUSE":
							break;
							
						default:
							  
							serverAddr = (InetSocketAddress) message.getSocketAddress();

							seqNum =  "SEQ:" + sequenceNumber + "\t" + messa;
							sendPacket(seqNum, message , false, serverAddr);
						
						}
				  		
					   
						break;
					}
				}
			}
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		}	
	 }
	
   }
   
   // String: packet to send
   // pack: original received packet
   // rspToFE: whether respond to FE is required
   public void sendPacket(String packet, DatagramPacket pack, boolean rspToFE, InetSocketAddress additionalAddr) throws IOException{
	   
		DatagramPacket forwardRequest = new DatagramPacket(packet.getBytes(),packet.getBytes().length);
		BufferedPacket n1 = new BufferedPacket();
		n1.fwdPacket = forwardRequest;
	    n1.timeStamp = System.currentTimeMillis();
	    n1.received =0;
	    n1.multicasted = 0;
	    
	    if (rspToFE)
	    	n1.addrFE = (InetSocketAddress)pack.getSocketAddress();
	    else
	    	n1.addrFE = null;
	    
	    Buffered_Packets.put(sequenceNumber, n1);
	    sequenceNumber++;
	    
	    for(InetSocketAddress i : SocketAddress.values()){
	    	
	        forwardRequest.setSocketAddress(i);
	        serverSocket.send(forwardRequest);
	        n1.multicasted++;
	        System.out.println(" sending " + forwardRequest + " to: " + i.toString());
	    }
	    
	    if (additionalAddr != null) {
	        forwardRequest.setSocketAddress(additionalAddr);
	        serverSocket.send(forwardRequest);
	        n1.multicasted++;
	        System.out.println(" sending " + forwardRequest + " to: " + additionalAddr.toString());
	    }
	    
   }
   public static void main(String[] args) throws IOException{
	   //2020 Server, 2018 FE
	   Sequencer s1 = new Sequencer(2020,2018);


	   s1.startSequencer();
	   
/*	   UDPEmulator FE = new UDPEmulator(2021);
	   UDPEmulator server1 = new UDPEmulator(2022);
	   UDPEmulator server2 = new UDPEmulator(2023);
	   UDPEmulator server3 = new UDPEmulator(2024);
	   
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2022),1);
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2023),2);
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2024),3);


	   SocketAddress A1 = new InetSocketAddress("localhost",2018);
	   FE.sendPacket(A1,"RESERVE\nHOTEL:H1\nGUESTID:123\nCHECKINDATE:20151201\nCHECKOUTDATE:20151205");
	   String received1 = server1.receivePacket();
	   String received2 = server2.receivePacket();
	   String received3 = server3.receivePacket();


	   System.out.println(received1);
	   System.out.println(received2);
	   System.out.println(received3);
	   FE.sendPacket(A1,"Hello2");
	   
	    received1 = server1.receivePacket();
	    received2 = server2.receivePacket();
	    received3 = server3.receivePacket();


	   System.out.println("Server received" + received1);
	   System.out.println("Server received" + received2);
	   System.out.println("Server received" + received3);
	   
	   String returnString = "SEQ:" + 0 +"\tTYPE:RESPOND\t\nRESPOND\nRESID:225\n";
	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnString);
	   
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   
	   String returnStr = "SEQ:" + 1 + "\tTYPE:NACK\t\n";
	   returnString = "SEQ:" + 1 +"\tTYPE:RESPOND\t\nRESPOND\nRESID:225\n";

	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnStr);
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("Server Received" + server3.receivePacket());
	   
	   String addaddr = "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:3\n";
	   server3 = new UDPEmulator(3333);

	   server3.sendPacket( new InetSocketAddress ("localhost",2020), addaddr);
	   System.out.println("Server Received" + server1.receivePacket());
	   System.out.println("Server Received" + server2.receivePacket());

	   System.out.println("Server Received" + server3.receivePacket());
	   returnString = "SEQ:" + 2 +"\tTYPE:RESPOND\t\n";
	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnString); */

   }


}