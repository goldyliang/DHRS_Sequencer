package sequencer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

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
	
    private void deliverPacket (final String header, final String request) {
    	
    	new Thread (){
			@Override
			public void run() {
				long seqNum = SequencerCommon.getSeqNum (header);
		        String response;
		        
		        // Synchronize handling packet
		        // with the operation of sendRMControlMsg
		        //
		        // so that the handling of RM control message triggered by handlePacket
		        // shall always happen after sending the current respond 
		        // This logic is specially needed for testing
		        synchronized (handleLock) {
		        	response = handler.handlePacket (seqNum, request);
			        sendRespond (header, response);
		        }
			}
    	}.start();
        
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
		
		byte[] buf = new byte[1000];
		
		DatagramPacket p = new DatagramPacket(buf, buf.length);
		
		while (true) {
			
			try {
				
				if (Thread.interrupted() || socketLocal == null || socketLocal.isClosed())
					return;
				
				socketLocal.receive(p);
				
				if (Thread.interrupted()) return;
				
				String request = new String (p.getData());
				
				int i = request.indexOf("\n");
				
				if (i>0) {
					String header = request.substring(0, i);
					deliverPacket(header, request.substring(i+1));
				}
				
			} catch (SocketException e) {
				if (! e.toString().toLowerCase().contains("socket closed"))
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
