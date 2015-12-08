package testSequencer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.Test;

import sequencer.PacketHandler;
import sequencer.SequencedReceiver;
import sequencer.Sequencer;
import sequencer.SequencerCommon;
import sequencer.UDPEmulator;


public class testSequencer {
	
	String serverReceiveAndRespond (UDPEmulator server, String respondParms) throws IOException {
	   String p = server.receivePacket();
	   //System.out.println (p);
	   long seqN = SequencerCommon.getSeqNum(p);
	   String returnString = "SEQ:" + seqN +"\tTYPE:RESPOND\t\nRESPOND\n" + respondParms;
	   server.sendPacket(returnString);
	   return p;
	}
	
	String serverReceiveAndNACK (UDPEmulator server) throws IOException {
	   String p = server.receivePacket();
	   //System.out.println (p);
	   long seqN = SequencerCommon.getSeqNum(p);
	   String returnString = "SEQ:" + seqN +"\tTYPE:NACK\t\n";
	   server.sendPacket(returnString);
	   return p;
	}
		
	String serverReceiveAndIgnore (UDPEmulator server) throws IOException {
		   String p = server.receivePacket();
		   //System.out.println (p);
		   return p;
		}
	
	String feReceive (UDPEmulator FE) throws IOException {
		 String p = FE.receivePacket();
		// System.out.println("FE Received:\n" + p);
		 return p;
	}

	//@Test
	public void testSequencer() throws IOException {
	   //2020 Server, 2018 FE
	   Sequencer s1 = new Sequencer(2020,2018);

	   s1.startSequencer();
	   
	   UDPEmulator FE = new UDPEmulator(2021);
	   UDPEmulator server1 = new UDPEmulator(2022);
	   UDPEmulator server2 = new UDPEmulator(2023);
	   UDPEmulator server3 = new UDPEmulator(2024);
	   
	   SocketAddress seqAddrServer = new InetSocketAddress("localhost",2020);

	   
	   server1.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:1\n");
	   server2.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:2\n");
	   server3.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:3\n");
	   
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   
	   serverReceiveAndRespond( server2, "");
	   serverReceiveAndRespond( server2, "");

	   serverReceiveAndRespond( server3, "");
	     

	   long resID = 100;
	   int cnt = 0;
	   SocketAddress seqAddrFE = new InetSocketAddress("localhost",2018);

	   while  (cnt < 1000) {
		   FE.sendPacket(seqAddrFE,"RESERVE\nHOTEL:H1\nGUESTID:123\nCHECKINDATE:20151201\nCHECKOUTDATE:20151205\n");
	
		   String respond = "RESID:" + (resID++) + "\n";
		   serverReceiveAndRespond (server1, respond + "SERVERID:1\n");
		   serverReceiveAndRespond (server2, respond + "SERVERID:2\n");
		   serverReceiveAndRespond (server3, respond + "SERVERID:3\n");
	
		   String p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   
		   cnt ++;
	   }
	   

	   s1.stopSequencer();
	}
	
	//@Test
	public void testSequencerResend () throws IOException {
		
	   //2020 Server, 2018 FE
	   Sequencer s1 = new Sequencer(2020,2018);

	   s1.startSequencer();
	   
	   UDPEmulator FE = new UDPEmulator(2021);
	   UDPEmulator server1 = new UDPEmulator(2022);
	   UDPEmulator server2 = new UDPEmulator(2023);
	   UDPEmulator server3 = new UDPEmulator(2024);
	   
	   SocketAddress seqAddrServer = new InetSocketAddress("localhost",2020);

	   
	   server1.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:1\n");
	   server2.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:2\n");
	   server3.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:3\n");
	   
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   
	   serverReceiveAndRespond( server2, "");
	   serverReceiveAndRespond( server2, "");

	   serverReceiveAndRespond( server3, "");
	     

	   long resID = 100;
	   int cnt = 0;
	   SocketAddress seqAddrFE = new InetSocketAddress("localhost",2018);

	   while  (cnt < 1000) {
		   FE.sendPacket(seqAddrFE,"RESERVE\nHOTEL:H1\nGUESTID:123\nCHECKINDATE:20151201\nCHECKOUTDATE:20151205\n");
	
		   String respond = "RESID:" + (resID++) + "\n";
		   
		   serverReceiveAndNACK (server1);
		   serverReceiveAndRespond (server1, respond + "SERVERID:1\n");
		   serverReceiveAndRespond (server2, respond + "SERVERID:2\n");
		   serverReceiveAndRespond (server3, respond + "SERVERID:3\n");
	
		   String p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   
		   cnt ++;
	   }
	   

	   s1.stopSequencer();
		   
	}
	
	//@Test
	public void testReceiver () throws IOException, InterruptedException {
		
		class Handler implements PacketHandler {

			@Override
			public String handlePacket(long seqNum, String request) {
				System.out.println ("Receiver handle: " + seqNum + "\n" + request);
				//assertTrue ( SequencerCommon.getMessageBody(request).indexOf("Request:" + seqNum)>=0 );
				return "Response:seq:" + seqNum + "\n";
			}
			
		}
		
	   //2020 Server, 2018 FE
	   UDPEmulator s1 = new UDPEmulator (2020);
	   
		Handler handler = new Handler();
		
		InetSocketAddress addrSequencer = new InetSocketAddress ("localhost", 2020);

		SequencedReceiver receiver = new SequencedReceiver(5555, addrSequencer, handler);

		receiver.startReceiver();
		
		receiver.sendRMControlMsg(1, "ADD_SERVER\nSERVERID:1\n");
		
		
	   int cnt = 0;
	   long seqNum = 0;
	   
		InetSocketAddress addrReceiver = new InetSocketAddress ("localhost", 5555);

		
	   while  (cnt < 100) {
		   
		   s1.sendPacket(addrReceiver,"SEQ:"+ seqNum + "\t\n" + "Request:" + seqNum + "\n");
		   Thread.sleep(50); // can not be too fast
		   
			//send seqNum in a pattern on 0 3 1 2 4 7 5 6 8 11 9 10 

		   switch ( (int)(seqNum % 4)) {
		   case 0: seqNum +=3; break;
		   case 1: seqNum ++; break;
		   case 2: seqNum +=2; break;
		   case 3: seqNum -=2; break;
		   }

		   			   
		   cnt ++;
	   }
		
		
		Thread.sleep(500);
		receiver.stopReceiver();
	
		
	}
	
	
	@Test
	public void testSequencerRemoveStale () throws IOException {
		
	   //2020 Server, 2018 FE
	   Sequencer s1 = new Sequencer(2020,2018);

	   s1.startSequencer();
	   
	   UDPEmulator FE = new UDPEmulator(2021);
	   UDPEmulator server1 = new UDPEmulator(2022);
	   UDPEmulator server2 = new UDPEmulator(2023);
	   UDPEmulator server3 = new UDPEmulator(2024);
	   
	   SocketAddress seqAddrServer = new InetSocketAddress("localhost",2020);

	   
	   server1.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:1\n");
	   server2.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:2\n");
	   server3.sendPacket(seqAddrServer, "TYPE:RMCTRL\t\nADD_SERVER\nSERVERID:3\n");
	   
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   serverReceiveAndRespond( server1, "");
	   
	   serverReceiveAndRespond( server2, "");
	   serverReceiveAndRespond( server2, "");

	   serverReceiveAndRespond( server3, "");
	     

	   long resID = 100;
	   int cnt = 0;
	   SocketAddress seqAddrFE = new InetSocketAddress("localhost",2018);

	   while  (cnt < 20000) {
		   FE.sendPacket(seqAddrFE,"RESERVE\nHOTEL:H1\nGUESTID:123\nCHECKINDATE:20151201\nCHECKOUTDATE:20151205\n");
	
		   String respond = "RESID:" + (resID++) + "\n";
		   
		   serverReceiveAndIgnore (server1);
		   serverReceiveAndRespond (server2, respond + "SERVERID:2\n");
		   serverReceiveAndRespond (server3, respond + "SERVERID:3\n");
	
		   String p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   p = feReceive (FE);
		   assertTrue (p.indexOf(respond)>0);
		   //p = feReceive (FE);
		   //assertTrue (p.indexOf(respond)>0);
		   
		   cnt ++;
	   }
	   

	   s1.stopSequencer();
		   
	}
	
	

}
