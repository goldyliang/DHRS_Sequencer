package sequencer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.TreeMap;

public class SequencedReceiver implements Runnable {

	PacketHandler handler;
	
	DatagramSocket socketLocal;
	InetSocketAddress addrSequencer;
	
	int localPort;
	
	Object handleLock = new Object();
	
	
	public SequencedReceiver (int port, InetSocketAddress addrSeq, PacketHandler handler) {
		localPort = port;
		addrSequencer = addrSeq;

		this.handler = handler;
	}
	
    private void deliverPacket (String request) {
    	
    	//TODO: could the handling order be changed due to new thread?
    	// maybe for critical transaction, do not create a new thread
    	
		int i = request.indexOf("\n");
		
		if (i>0) {
			final String header = request.substring(0, i);
			final String bodyRequest = request.substring(i+1);
			
			final long seqNum = SequencerCommon.getSeqNum (header);

	        final String resHeader = "SEQ:" + seqNum + "\t" + "TYPE:RESPOND\t";

	        
			// Check if there is a flag of TYPE:ORDERED. If yes, do not create a thread for this
			if (! SequencerCommon.getMessageType(request).equals("ASYNC")) {
				String response = handler.handlePacket (seqNum, bodyRequest);
				sendRespond (resHeader, response);
			} else
				// TODO: pass ASYNC for query requests
			   	new Thread (){
					@Override
					public void run() {
				        String response;
				        
				        // Synchronize handling packet
				        // with the operation of sendRMControlMsg
				        //
				        // so that the handling of RM control message triggered by handlePacket
				        // shall always happen after sending the current respond 
				        // This logic is specially needed for testing
				        synchronized (handleLock) {
				        	response = handler.handlePacket (seqNum, bodyRequest);
					        sendRespond (resHeader, response);
				        }
					}
		    	}.start();
	    	
		}
        
    }
    
    private void sendRespond (String header, String response) {
    	
    	String msg = header + "\tTYPE:RESPOND\t\n" + response;
    	byte[] buf = msg.getBytes();
    	
    	try {
			DatagramPacket p = new DatagramPacket(buf, buf.length, addrSequencer);
			
			socketLocal.send(p);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    }
    
    // send a RM control message (from server=serverID)
    // information is in ctrlMsg, which the Sequencer shall extract and take action
    // this method shall NOT be invoked within handlePacket, otherwise there will be deadlock
    public void sendRMControlMsg (int serverID, String ctrlMsg) {
    	
    	 // Synchronize handling packet
        // with the operation of sendRMControlMsg
        //
        // so that the handling of RM control message triggered by handlePacket
        // shall always happen after sending the current respond 
        // This logic is specially needed for testing
        synchronized (handleLock) {

	    	// The required information is in ctrlMsg
	    	// The header doesn't matter
	    	String header = "TYPE:RMCTRL\t";
	    	String msg = header + "\n" + ctrlMsg;
	    	byte[] buf = msg.getBytes();
	    	
	    	try {
				DatagramPacket p = new DatagramPacket(buf, buf.length, addrSequencer);
				
				socketLocal.send(p);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }
    
	@Override
	public void run() {
		
		System.out.println("SequencedReceiver thread started...");
		
		byte[] buf = new byte[2000];
		
		DatagramPacket p = new DatagramPacket(buf, buf.length);
		
		long lastSeqNum = -1; // very first packet
		
		Map <Long, String> bufferedPackets = new TreeMap <Long, String> ();
		
		while (true) {
			
			try {
				
				if (Thread.interrupted() || socketLocal == null || socketLocal.isClosed())
					return;
				
				socketLocal.receive(p);
				
				if (Thread.interrupted()) return;
				
				String request = new String (p.getData(), 0, p.getLength());
				
				System.out.println ("Sequencer received:" + request);
				
				// Check sequencer number to see if it is continuous
				long seqNum = SequencerCommon.getSeqNum(request);
				
				if (lastSeqNum < 0)
					lastSeqNum = seqNum - 1; //This is the first packet, always right order
				
				if ( seqNum == lastSeqNum + 1 ) {
					// Received a correct order packet
					// deliver received packet		
					deliverPacket(request);
					lastSeqNum ++; // update lastSeqNum to new SeqNum
					
					String buffered = null;
					
					while ( (buffered = bufferedPackets.get(lastSeqNum + 1) ) !=null ) {
						// We have buffered packet which can be deliver now
						
						deliverPacket (buffered);
						
						lastSeqNum ++;
						
						System.out.println("Re-delivered seqNum:" + lastSeqNum +
								"; TYPE: " + 
									SequencerCommon.getMessageType(buffered) + "-" +
									SequencerCommon.getBodyMessageType(buffered));

						
						bufferedPackets.remove(lastSeqNum); // remove from buffer
					}					
				} else if (seqNum <= lastSeqNum){
					// Received a packet which has been handled before, print and ignore it
					System.out.print(
							"Received old packet. seqNum:" + seqNum + "; lastSeqNum:" + lastSeqNum +
							"; packet:" + request);
				} else {
					System.out.println ("Received wrong seqNum: " + seqNum + "; TYPE: " + 
								SequencerCommon.getMessageType(request) + "-" +
								SequencerCommon.getBodyMessageType(request));

					// seqNum > lastSeqNum + 1
					// Received a newer packet, but some previous packets lost
					// Put the packet to buffer first, and send NACK for the missing packets
					bufferedPackets.put(seqNum, request);
					
					for (long seq = lastSeqNum + 1; seq < seqNum; seq ++) {
						String sNack = "SEQ:" + seq + "\tTYPE:NACK\t\n";
						byte[] bytes = sNack.getBytes();
						DatagramPacket nack = new DatagramPacket (bytes, 0, bytes.length);
						
						nack.setSocketAddress(p.getSocketAddress()); // send back
						
						socketLocal.send(nack);
					
						System.out.println ("Sent NACK. seqNum:" + seq);
					}
				}
				
			} catch (SocketException e) {
				if (! e.toString().toLowerCase().contains("closed"))
					e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	

	Thread thReceiver;
	
	public synchronized void startReceiver() {
		
		try {
			if (socketLocal!=null)
				socketLocal.close();
			
			socketLocal = new DatagramSocket(localPort);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		if (thReceiver!=null)
			thReceiver.interrupt();
		
		thReceiver = new Thread (this);
		
		if (thReceiver!=null)
			thReceiver.start();
	}
	
	public synchronized void stopReceiver() {
		
		if (socketLocal!=null)
			socketLocal.close();
		
		socketLocal = null;
		
		if (thReceiver!=null)
			thReceiver.interrupt();
		
		thReceiver = null;
	}

	public boolean isRunning () {
		return thReceiver.isAlive();
	}
}
