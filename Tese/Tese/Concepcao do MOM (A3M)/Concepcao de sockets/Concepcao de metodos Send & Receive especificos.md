To provide extensibility, the messaging middleware must allow different types of sockets to be created. As each socket type may require different implementations of the send and receive methods, the middleware must provide generic methods that enables the development of these implementations.
# Problems
## Blocking & non-blocking
The client application may desire methods that allow blocking the thread until sending/receiving is possible. The opposite, not wanting the thread to block, is also possible. These means that generic methods must be provided that allow the design of type-specific blocking and non-blocking methods.

Regarding **send operations**, the generic socket functionality **can only result in a blocking behavior** when: (1) a socket **does not have credits** to send a message to the intended destination; (2) there are **no available destinations** (i.e. there are no links established). 

## Routing mechanism
Each socket type may want to employ a different routing mechanism: round-robin, first-available, etc. To provide routing mechanisms, generic methods that provide the following functionality may be required: 
- return valid destinations; 
- return destinations available to receive, i.e., that the socket can send a message to immediately;
- etc.

## Listener-only vs Initiator-only vs Listener+Initiator
Although sockets are made to be send and receive link requests, it is up to the application developer to decide if the socket will be an initiator[^1], a listener[^2] or both[^3]. If the topology is designed with the socket being the one responsible by requesting the establishment of links, than the socket is an initiator. If the topology is designed for the socket to not send but to receive link requests, then the socket is a listener. If the topology follows a peer-to-peer model, then the sockets may initiate and receive link requests. 

[^1] An initiator is a socket that sends link establishment requests. An example is an ephemeral socket that requests the establishment of links with sockets that provide services, and after being satisfied, closes the links. An initiator knows the sockets it wants to establish a link with, if it didn't it couldn't establish a link.
[^2] A listener is a socket that waits for link establishment requests, i.e., it expects other sockets to request the establishment of a link. An example of a listener is a socket that acts as a "server", expecting others to link with it, so that it can answer their requests. As it does not know the other sockets, it cannot initiate the link establishment process.  
[^3] A socket that is an initiator and a listener is a socket that both sends and receives link establishment requests. 

The problem is that depending on the role assumed, different types of blocking behavior may be preferred. For instance, let's compare two examples that have a PUSH socket, that sends tasks, perform a blocking send operation. In the first example, the socket is a fixed piece of the topology and acts as a listener, expecting PULL sockets to request the establishment of a link so that it can send the generated tasks. Since the socket is listener-only, and the tasks are not generated based on input, allowing the send operation to block until a link is established is desirable as it facilitates coding the PUSH socket behavior. Now, let's look at the second example, where the PUSH sockets are now the dynamic pieces of the topology and the PULL sockets are the fixed pieces. These means, that a PUSH socket is now responsible for requesting the establishment of links, so it can send tasks to the PULL sockets. If the blocking send method is invoked, it is not desirable for a method to block if there aren't established links and ongoing link establishment processes. This is because, the responsibility of issuing the establishment of links belongs to the PUSH socket. With no established links and ongoing link establishment processes, if the send method blocks forever, unless another thread initiates establishment processes, the sending thread will hang forever.

Providing different blocking behavior depending on whether the client will program the socket as a listener, initiator or both, may be a useful feature. However, the most that can be done is create generic methods that can aid the client and/or the socket developer on getting such behavior. This is because each socket implements their own send and receive methods, hence they have the final decision on whether the feature is included by these methods.  

**Idea 1:** Default behavior as if sockets are listeners, meaning they block even if there aren't links established or on the process of being established. The reason behind this thought is because the sockets are thread-safe, therefore, other threads could potentially discover and fix the problem. The blocking methods should have a flag as parameter indicating if an exception should be thrown in the absence of destinations.

With sockets implementing their own send and receive methods, how can we provide a way for this feature to be achieve in a generic way?  Let's consider a simplified implementation of a blocking send method for a REQ socket:
```java
// SocketInternal corresponds to the AbstractSocket class
// Also, lock acquisitions are missing
public class ReqSocketInt extends SocketInternal {
	private boolean pendingReq = false; // pending request
	private LinkedList<SocketIdentifier> sendOrder = new LinkedList<>();
	private int nextSending = 0;
	

	protected void handleNewLink(SocketIdentifier sid){
		sendOrder.addLast(sid);
	}

	// Returns 0 if the message was sent successfully. Otherwise, returns the error.
	public int send(byte[] payload, boolean initiator){
		if(pendingReq)
			return super.ERR_INV_STATE; // returns the error "invalid state"

		// waits for any link. Always returns true, unless a "true" value is provided as argument and there are no links established and ongoing link establishments. 
		boolean anylink = super.waitAnyLink(initiator);
		if(anylink == false){
			return super.ERR_NOLINKS; // returns the error "no links" 
		}

		// sends msg to a destination
		SocketIdentifier dest = sendOrder.get(nextSending);
		super.sendMsg(dest);
		
		// set pending request flag
		pendingReq = true;
		
		// update next destination
		nextSending++;
		if(nextSending >= sendOrder.size())
			nextSending = 0;

		return 0;
	}
}

```

In the example, above we can see a `"waitAnyLink(initiator : boolean) : boolean"` method that returns immediately if there is any link established (must not mistake with available) or waits for a link to be established. If the "initiator" flag is set, then the method returns immediately if there are no links established and no ongoing link establishment processes. This method could even return a SocketIdentifier, instead of returning a boolean. The "false" value would be substituted by a 'null'. With this method, sockets can easily wait for a link to be established before proceeding with their custom logic.

<span style="color:red">Is wait for available link useful? How will this be influenced by pollers? Public methods such as wait for a specific link establishment process to finish and blocking link methods can be useful.</span> 

**Another note:** waitSpecificLink may be required for writing custom socket logic. Blocking when sending responses should not be a possible scenario, unless the programmer decides on mismatching the credits given by each socket. For example, if a DEALER has 50 credits, and the ROUTER only has 25 credits, the ROUTER won't be able to maintain a fluid flow of communication as the dealer can send more messages than the router can respond to.

Idea 2

**Unrelated Idea:** Allowing the client to set the type of socket could also be a useful functionality. For instance, making the socket an initiator-only could be done by making the socket reject all link establishment requests. Not allowing the link() method to be invoked would be pointless as the application developer could just not invoke the method in its code.

# Requirements

The presented situations require following:
1. Blocking method to send a message to a specific destination
	- Must throw exception if the destination is not linked with the socket;
	- Must block until there is an available credit to send the message or until the timeout expires.
	- Returning means the message was sent.
2. Non-blocking method to send a message to a specific destination
	- Must throw exception if the destination is not linked with the socket;
	- If the socket does not have credits to send a message to the intended destination, the method must return a value that indicates such situation or that the message was not sent.
	- Return value must indicate that the message was sent.
3. Knowing what are the possible destinations (i.e. linked sockets)
	- Can be provided by:
		- generic non-blocking method that returns all possible destinations;
		- abstract methods that should be implemented by every socket, one to inform the establishment of a new link and another to inform the closure.
1. Blocking method to wait for a possible destination
2. Blocking method to get the first available destination
	- If there aren't available destinations, the method blocks until a destination is available and returns it;
	- If a timeout is provided, the method returns *null*.
3. 

If every time a link is established or closed, a callback specific to the socket is called to handle such situations, the socket does not have to execute a method to get all possible destinations, since it already knows which destinations exist. However, existing is not the same as being available. An available destination is a destination that can receive a method, i.e., the socket has credits to send a message to that destination.