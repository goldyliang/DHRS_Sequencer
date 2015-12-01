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
		
		DatagramPacket packet;
		long timeStamp;
		int multicasted;
		int received;
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
		String pack = new String(request.getData());
		InetSocketAddress FEAddr = (InetSocketAddress) request.getSocketAddress();
		String packFormat = "SEQ:"+ sequenceNumber + "\t FEAddr:" + FEAddr.getHostString() + "-" + FEAddr.getPort() + "\n" + pack;  
		sendPacket(packFormat, request);

	    	
		
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
			
			String messa = new String(message.getData());
			
			switch(SequencerCommon.getMessageType(messa)){
			
			case "RESPOND" :{
			
				long seqNum = SequencerCommon.getSeqNum(messa);
				
				BufferedPacket bpack = Buffered_Packets.get(seqNum);
				
				bpack.received++;
			    String messaBody = SequencerCommon.getMessageBody(messa);
			    if(bpack.packet != null){
			    	
			    	
			    
			    DatagramPacket messageSend = new DatagramPacket(messaBody.getBytes(), messaBody.getBytes().length);
			    
			    InetSocketAddress feAddr = (InetSocketAddress) bpack.packet.getSocketAddress();
			    messageSend.setSocketAddress(feAddr);
			    
			    feSocket.send(messageSend);
			    }
				if(bpack.received == bpack.multicasted)
					Buffered_Packets.remove(seqNum);
			break;
			}
			case "NACK":
			{	
                long seqNum = SequencerCommon.getSeqNum(messa);
				
				BufferedPacket n1 = Buffered_Packets.get(seqNum);
				
			    n1.timeStamp = System.currentTimeMillis();
			    
			    String pack = new String(n1.packet.getData());
				InetSocketAddress FEAddr = (InetSocketAddress) n1.packet.getSocketAddress();
				String packFormat = "SEQ:"+ seqNum + "\t FEAddr:" + FEAddr.getHostString() + "-" + FEAddr.getPort() + "\n" + pack;  
				DatagramPacket forwardRequest = new DatagramPacket(packFormat.getBytes(),packFormat.getBytes().length);
			    forwardRequest.setSocketAddress(message.getSocketAddress());
			    serverSocket.send(forwardRequest);
			 break;   
			}	
			case "ADD_SERVER":
				int serverID = SequencerCommon.getServerID(messa);
				InetSocketAddress serverAddr = SequencerCommon.getServerSocketAddress(messa);
				
				SocketAddress.put(serverID, serverAddr);
				
				String seqNum =  "SEQ:" + sequenceNumber + "\t" + messa;
				sendPacket(seqNum, null);
				
			}
			
			;
			
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		}	
	 }
	
   }
   
   public void sendPacket(String packet, DatagramPacket pack) throws IOException{
	   
		DatagramPacket forwardRequest = new DatagramPacket(packet.getBytes(),packet.getBytes().length);
		BufferedPacket n1 = new BufferedPacket();
		n1.packet = pack;
	    n1.timeStamp = System.currentTimeMillis();
	    n1.received =0;
	    n1.multicasted = 0;
	    Buffered_Packets.put(sequenceNumber, n1);
	    sequenceNumber++;
	    for(InetSocketAddress i : SocketAddress.values()){
	    	
	        forwardRequest.setSocketAddress(i);
	        serverSocket.send(forwardRequest);
	        n1.multicasted++;
	    }
   }
   public static void main(String[] args) throws IOException{
	   
	   Sequencer s1 = new Sequencer(2020,2018);
	   UDPEmulator FE = new UDPEmulator(2021);
	   UDPEmulator server1 = new UDPEmulator(2022);
	   UDPEmulator server2 = new UDPEmulator(2023);
	   UDPEmulator server3 = new UDPEmulator(2024);

	   s1.startSequencer();
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2022),1);
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2023),2);
	   s1.addMulticastSocketAddress(new InetSocketAddress("localhost",2024),3);


	   SocketAddress A1 = new InetSocketAddress("localhost",2018);
	   FE.sendPacket(A1,"Hello");
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
	   
	   String returnString = "SEQ:" + 0 +"\tFEAddr:IPAddress-port\tTYPE:RESPOND\tSERVERID:1\n FE Received!!!!";
	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnString);
	   
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   
	   String returnStr = "SEQ:" + 1 +"\tFEAddr:IPAddress-port\tTYPE:NACK\tSERVERID:1\n!!!!";
	   returnString = "SEQ:" + 1 +"\tFEAddr:IPAddress-port\tTYPE:RESPOND\tSERVERID:1\n FE Received!!!!";

	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnStr);
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("FE Received" + FE.receivePacket());
	   System.out.println("Server Received" + server3.receivePacket());
	   
	   String addaddr = "FEAddr:localhost-5544\tTYPE:ADD_SERVER\tSERVERID:3\tSERVERADDR:127.0.0.1-3333\t\n";
	   server3 = new UDPEmulator(3333);

	   server1.sendPacket(addaddr);
	   System.out.println("Server Received" + server1.receivePacket());
	   System.out.println("Server Received" + server2.receivePacket());

	   System.out.println("Server Received" + server3.receivePacket());
	   returnString = "SEQ:" + 2 +"\tFEAddr:IPAddress-port\tTYPE:RESPOND\tSERVERID:1\n FE Received!!!!";
	   server1.sendPacket(returnString);
	   server2.sendPacket(returnString);
	   server3.sendPacket(returnString);

   }


}