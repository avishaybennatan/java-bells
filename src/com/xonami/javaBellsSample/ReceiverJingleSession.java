package com.xonami.javaBellsSample;

import java.io.IOException;
import java.util.List;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import org.ice4j.TransportAddress;
import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleMediaStream;
import com.xonami.javaBells.NameAndTransportAddress;
import com.xonami.javaBells.StunTurnAddress;

/**
 * Handles jingle packets for the receiver.
 * In this example, we only accept sessionInitiation requests if they
 * come from the expected caller.
 * 
 * @author bjorn
 *
 */
public class ReceiverJingleSession extends DefaultJingleSession {
	private final String callerJid;
	private IceAgent iceAgent;

	public ReceiverJingleSession(JinglePacketHandler jinglePacketHandler, String callerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.callerJid = callerJid;
	}

	/** accepts the call only if it's from the caller want. */
	@Override
	public void handleSessionInitiate(JingleIQ jiq) {
		// acknowledge:
		ack(jiq);
		// set the peerJid
		peerJid = jiq.getFrom();
		// compare it to the expected caller:
		try {
			if (peerJid.equals(callerJid)) {
				System.out.println("Accepting call!");
				// okay, it matched, so accept the call and start negotiating
				
				String name = JingleMediaStream.getContentPacketName(jiq);
				
				StunTurnAddress sta = StunTurnAddress.getAddress( connection );
				
				List<ContentPacketExtension> contentList = JingleMediaStream.createContentList(MediaType.VIDEO, CreatorEnum.initiator, "video", ContentPacketExtension.SendersEnum.both);
				try {
					iceAgent = new IceAgent(false, connection.getUser(), name, sta.getStunAddresses(), sta.getTurnAddresses());
				} catch( IOException ioe ) {
					throw new RuntimeException( ioe );
				}
				iceAgent.addLocalCandidateToContents(contentList);
	
				JingleIQ iq = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, contentList);
				connection.sendPacket(iq);
				state = SessionState.NEGOTIATING_TRANSPORT;
				
				iceAgent.addRemoteCandidates( jiq );
			} else {
				System.out.println("Rejecting call!");
				// it didn't match. Reject the call.
				JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
				connection.sendPacket(iq);
				closeSession();
			}
		} catch( IOException ioe ) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			closeSession();
		}
	}
	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		if( !this.checkAndAck(jiq) )
			return;
		iceAgent.startConnectivityEstablishment();
	}
	@Override
	public void handleTransportInfo(JingleIQ jiq) {
		if( !this.checkAndAck(jiq) )
			return;
		
		//hotness! we should now be able to start talking
		NameAndTransportAddress nta = iceAgent.getTransportAddressFromRemoteCandidate(jiq);
		if( nta == null ) {
			connection.sendPacket(JinglePacketFactory.createCancel(myJid, peerJid, sessionId) );
			closeSession();
		} else {
			state = SessionState.OPEN;
		}
		System.out.println( "=============" );
		System.out.println( "=============" );
		System.out.println( "We can now connect to this remote transport address:" );
		System.out.println( nta );
		System.exit(0);
	}
}
