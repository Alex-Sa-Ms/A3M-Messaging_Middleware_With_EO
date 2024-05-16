# Delete this after writing the conclusions in the appropriate note
# Checklist
The final architecture should pass this checklist.
- [ ] Can use custom socket API?
- [ ] Received messages are handled by a core object before being delivered to the custom processing method?
	- Required for control core messages such as Link Request, Link Ack, Link Refuse, etc.
- [ ] Link related messages can be understood by all sockets regardless of their type?
- [ ] Does every method need to be implemented from scratch for every socket implementation?
- [ ] Can RAW and COOKED sockets be created? Are they implemented as two different sockets?


# Solution attempt 1

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

# Solution attempt 2
```java
public abstract class CustomSocket{
	private final ISocketBridge bridge;
	private final ISocketEventHandler eventHandler;
	private final ProtocolFlag protoFlag;
	
	// copy & paste from NNG
	public static enum ProtocolFlag{
		RCV, // Protocol can receive
		SND, // Protocol can send
		SNDRCV, // Protocol can send & receive
		RAW // Protocol is raw
	}

	// pode-se tentar usar alguma lógica com OR de bits para a flag do protocolo
	private boolean canSend(){...}
	private boolean canReceive(){...}

	/**
	 * @param protocolFlag used to identify which operations make sense for the protocol in question. 	
	**/
	public CustomSocket(ISocketBridge bridge, Protocolflag protoFlag){
		this.bridge = bridge;
		this.eventHandler = createEventHandler();
		this.protoFlag = protoFlag;
	}

	// initialization
	private ISocketEventHandler eventHandler(){
		Consumer<Msg> msgConsumer = this::handleReceivedMessage;
		Consumer<MsgId> receiptConsumer = this::handleReceipt;
		Consumer<SocketIdentifier> linkConsumer; // TODO - falta o handler de novos links
		Consumer<LinkRequest> linkRqstConsumer; // TODO - falta o handler de link requests
	}

	// sending messages
	private abstract Msg preSendProcedure(Msg msg);
	private abstract void postSendProcedure(Msg msg);
	public MsgId send(byte[] payload){
		if(protoFlag == SND || protoFlag == SNDRCV){
			Msg msg = new Msg(payload);
			msg = preSendProcedure(msg);
		}
		else if(protoFlag == RCV)
			throw new NotSupportedException();
		//else if(protoFlag == RAW)
		//	... // what to do here?
	}
	// rest of the send methods...

	// handling message receipts
	private abstract void handleReceipt(MsgId receipt);
	
	// receiving messages
	private abstract void handleReceivedMessage(Msg msg); // procedure to execute immediately after the message arriving at the socket
	private abstract Msg receiveProcedure(Msg msg); // procedure to execute before returning a message. There is only a pre receive procedure as returning the message ends the receive operation. 
	public Msg receive(){
		if(protoFlag == RCV || protoFlag == SNDRCV) // TODO - find a way to check this with only one operation (use bit operations)
			return receiveProcedure();
		else if(protoFlag == SND)
			throw new NotSupportedException();
		// else if (protoFlag == RAW) // is it supposed to do anything fancy with RAW? When not RAW hide some Msg information, while with RAW keep everything?
	}
	// rest of the receive methods...
	
	// linking / unlinking with sockets
	// TODO - linking methods must contemplate receiving requests and issueing requests
	private ...
	
	private abstract boolean preLinkingConditions(SocketIdentifier id);
	private abstract void preLinkingProcedure(SocketIdentifier id);
	private abstract void postLinkingProcedure(SocketIdentifier id);
	private void handleLink(SocketIdentifier id){
		postLinkingProcedure(id);
	}
}

public interface ISocketBridge{
	// Used to get the lock of the socket bridge for early acquisitions.
	// The lock can be required for atomic operations.
	Lock getLock();

	// Must throw exception when a non null handler has been set previously.
	void setSocketEventHandler(ISocketEventHandler eventHandler);

	void setDefaultReceiptOption(boolean emitReceipts);
	boolean getDefaultReceiptOption();

	MsgId send(SocketIdentifier dest, byte[] payload);
	MsgId send(SocketIdentifier dest, byte[] payload, boolean receipt);
	MsgId trySend(SocketIdentifier dest, byte[] payload);
	MsgId trySend(SocketIdentifier dest, byte[] payload, boolean receipt);
	MsgId trySend(SocketIdentifier dest, byte[] payload, long tOut);
	MsgId trySend(SocketIdentifier dest, byte[] payload, long tOut, boolean receipt);

	// missing the method to send custom negociation properties for the linking operation
	void link(SocketIdentifier id); // issue link operation
	void unlink(SocketIdentifier id); // issue unlink operation
	void isLinked(SocketIdentifier id); // check if a socket is linked
}

public interface ISocketEventHandler{
	void handleReceipt(MsgId msgId);
	void handleReceivedMessage(Msg msg);
	LinkResponse.RefuseReason handleLinkRequest(LinkRequest request); // if the link should be accepted return 'null', otherwise return the reason for the refusal
	void handleLink(SocketIdentifier id); // new linked socket
}
```


## Questions
- How to make something like NNG raw sockets?
- Could I have something like a pipe upon the establishment of a link? Does it make sense to exist? 
- Can I create a basic RAW version and COOKED version, and implement the custom sockets over them?
- Can I create a version that can already tip to a RAW version and COOKED version based on a flag defined at creation time?
- How does a reader thread know which socket should receive the receipt?
- Linking operation must contemplate that the higher level socket may also want to verify the request before accepting the link. Another callback it is. 


# Set custom socket options

```java
	// To option name to set the default for the emission of receipts is 'emitReceipts'. 
	// `setOption("emitReceipts", true)` to emit a receipt as default. 
	// `setOption("emitReceipts", false)` to not emit a receipt as default.
	// To handle the receipt a socket handler must be provided.
	
	<T> void setOption(String option, T value);
    <T> T getOption(String option, Class<T> valueType);

	// use clone() for security (both at setOption() and getOption())

    //public <T> T getOption(String optionName, Class<T> valueType) {
    //    Object value = options.get(optionName);
    //    if (value == null) {
    //        return null;
    //    }else if (!valueType.isInstance(value)){
    //		  throw new IllegalArgumentException("The option value is not an instance of the given value type.");	
    //	  }
    //    return valueType.cast(value); 
    //}
    ```