```java
public class CoreSocketFunctionality{
	private Lock lock = new ReentrantLock();

	public MsgId send(byte[] payload, SocketIdentifier dest){
		... // blocks until space in flow control window
	}

	public MsgId trySend(byte[] payload, SocketIdentifier dest){
		... // checks if there is space in flow control window
	}

	// allowing to check the state of the flow control might be useful also
	// public boolean canSend();
	// public int getSendPermits();


	// What if it is required to store custom information about the linked sockets?
		//	Store information in two different places, in the custom class and in here?
			// Store ongoing link requests here, and use a notification method to notify the
			// custom socket that a new link was created.
	// Link messages should be capable of carrying a custom payload to include custom information
	// that can be used to determine if the linking operation should happen.

	// Linking exchange:
	//	1. Send link request
	//  2.1. If link request is valid, send link ack message which may contain custom payload to carry properties important for the linking negotiation.
	//  2.2. if link request is not valid (compatible), send link refuse message containing the reason.
	//  2.3. If the destination socket of the link does not exist (at least currently) then send link refuse containing as reason (socket does not exist).
	//  3. If the information in the link ack is compatible, send a link ack with empty custom payload (custom payload is not necessary as this information was already sent in the link request message).


	// How will this different messages be distinguished? 
		// They should always pass through the CoreSocketFunctionality before being delivered to the custom socket.

	public link(SocketIdentifier id){
		... // needs custom info
	}

	public unlink(SocketIdentifier id){
		... 
	}

	public handleReceipt(MsgId id){
		... // to update flow control window
	}
}
```