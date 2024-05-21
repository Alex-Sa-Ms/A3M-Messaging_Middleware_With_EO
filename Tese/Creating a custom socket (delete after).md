# Delete this after writing the conclusions in the appropriate note
# Checklist
The final architecture should pass this checklist. Open checked boxes to see solution.
- [ ] Can use custom socket API?
- [ ] Received messages are handled by a core object before being delivered to the custom processing method?
	- Required for control core messages such as Link Request, Link Ack, Link Refuse, etc.
- [ ] Link related messages can be understood by all sockets regardless of their type?
- [ ] Does every method need to be implemented from scratch for every socket implementation?
- [ ] Can RAW and COOKED sockets be created? Are they implemented as two different sockets?

- [ ] How to make something like NNG raw sockets?
- [ ] Could I have something like a pipe upon the establishment of a link? Does it make sense to exist? 
- [ ] Can I create a basic RAW version and COOKED version, and implement the custom sockets over them?
- [ ] Can I create a version that can already tip to a RAW version and COOKED version based on a flag defined at creation time?
- [ ] How does a reader thread know which socket should receive the receipt?
	-  Exon permitir o utilizador criar MsgId?
		-  Assim é possível adicionar a etiqueta do socket para ser possível entregar o recibo ao socket sem que seja necessário introduzir mais um ponto de contenção com uma estrutura que mapeia o `MsgId` para o socket fonte. As associações têm de ser criadas pelos próprios sockets e depois acedida pela `ReaderThread` para saber a que socket entregar o recibo. 

# Decisions
- Socket core has own private lock that should be released before entering unknown territory (protocol callbacks).
# Diagram (in progress)
![[Pasted image 20240521154534.png]]
# Future work
- Check if sharing the socket lock with the socket core is mandatory.
	- For the sockets to be thread-safe, they require a lock. However, this lock is required not only by the objects related to the protocol but also by the core of the socket, for example, to make threads idle while the resources they are waiting for are not available.
		-  **Solution:** Inject the socket lock into these objects.
- Linking requests containing custom payload and handling of such custom payloads to decide if the link request should be accepted or refused.
	- Linking operations are handled by the socket core. However, a high-level socket may which to add further requirements for a link to be created. (Example: Can be useful to implement authentication.)
	- Socket core must allow a custom payload to be included in the link request.
	- A handler for the custom payload must be provided to the socket core so that the it can be analysed when the peer is compatible and a custom payload does exist.
	- **Solutions:**
		1. Injecting a protocol specific object in the socket core may be the solution to access both the handler and the custom payload.
		2. Another solution is to use setter methods, to the set the custom payload and the handler.
- Similarly to NNG, maybe later there can be something like link options. 

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

# Solution attempt 2 (outdated)

```java
public abstract class Socket{
	private final ISocketCore core;
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
	public CustomSocket(ISocketCore core, Protocolflag protoFlag){
		this.core = core;
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
```

```java
public interface ISocketCore{
	// Used to get the lock of the socket bridge for early acquisitions.
	// The lock can be required for atomic operations.
	Lock getLock();

	// Must throw exception when a not null handler has been set previously.
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
```

```java
public interface ISocketEventHandler{
	void handleReceipt(MsgId msgId);
	void handleReceivedMessage(Msg msg);
	LinkResponse.RefuseReason handleLinkRequest(LinkRequest request); // if the link should be accepted return 'null', otherwise return the reason for the refusal
	void handleLink(SocketIdentifier id); // new linked socket
}

```


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
# Protocolos
## Ficheiros importantes NNG
- *socket.c*
- *protocol.h*
- ficheiros de implementacao de protocolo, tipo *xrep.c*
- pollable.* pode ajudar a fazer a parte do poll
## Algumas estruturas importantes
```c
struct nni_socket {
	nni_list_node s_node;
	nni_mtx       s_mx;
	nni_cv        s_cv; // condition variable
	nni_cv        s_close_cv; // close condition variable

	uint32_t s_id;
	uint32_t s_flags;
	unsigned s_ref;  // protected by global lock
	void    *cs_data; // Protocol private
	size_t   s_size;

	nni_msgq *s_uwq; // Upper write queue
	nni_msgq *s_urq; // Upper read queue

	nni_proto_id s_self_id;
	nni_proto_id s_peer_id;

	nni_proto_pipe_ops s_pipe_ops;
	nni_proto_sock_ops s_sock_ops;
	nni_proto_ctx_ops  s_ctx_ops;

	// options
	nni_duration s_sndtimeo;  // send timeout
	nni_duration s_rcvtimeo;  // receive timeout
	nni_duration s_reconn;    // reconnect time
	nni_duration s_reconnmax; // max reconnect time
	size_t       s_rcvmaxsz;  // max receive size
	nni_list     s_options;   // opts not handled by sock/proto
	char         s_name[64];  // socket name (legacy compat)

	nni_list s_listeners; // active listeners
	nni_list s_dialers;   // active dialers
	nni_list s_pipes;     // active pipes
	nni_list s_ctxs;      // active contexts (protected by global sock_lk)

	bool s_closing; // Socket is closing
	bool s_closed;  // Socket closed, protected by global lock
	bool s_ctxwait; // Waiting for contexts to close.

	nni_mtx          s_pipe_cbs_mtx;
	nni_sock_pipe_cb s_pipe_cbs[NNG_PIPE_EV_NUM];

#ifdef NNG_ENABLE_STATS
	nni_stat_item st_root;      // socket scope
	nni_stat_item st_id;        // socket id
	nni_stat_item st_name;      // socket name
	nni_stat_item st_protocol;  // socket protocol
	nni_stat_item st_dialers;   // number of dialers
	nni_stat_item st_listeners; // number of listeners
	nni_stat_item st_pipes;     // number of pipes
	nni_stat_item st_rx_bytes;  // number of bytes received
	nni_stat_item st_tx_bytes;  // number of bytes received
	nni_stat_item st_rx_msgs;   // number of msgs received
	nni_stat_item st_tx_msgs;   // number of msgs sent
	nni_stat_item st_rejects;   // pipes rejected
#endif
};

typedef struct nni_sockopt {
	nni_list_node node;
	char         *name;
	nni_type      typ;
	size_t        sz;
	void         *data;
} nni_sockopt;

struct nni_ctx {
	nni_list_node     c_node;
	nni_sock         *c_sock;
	nni_proto_ctx_ops c_ops;
	void             *c_data;
	size_t            c_size;
	bool              c_closed;
	unsigned          c_ref; // protected by global lock
	uint32_t          c_id;
	nng_duration      c_sndtimeo;
	nng_duration      c_rcvtimeo;
};

// nni_proto_pipe contains protocol-specific per-pipe operations.
struct nni_proto_pipe_ops {
	// pipe_size is the size of a protocol pipe object.  The common
	// code allocates this memory for the protocol private state.
	size_t pipe_size;

	// pipe_init initializes the protocol-specific pipe data structure.
	// The last argument is the per-socket protocol private data.
	int (*pipe_init)(void *, nni_pipe *, void *);

	// pipe_fini releases any pipe data structures.  This is called after
	// the pipe has been removed from the protocol, and the generic
	// pipe threads have been stopped.
	void (*pipe_fini)(void *);

	// pipe_start is called to register a pipe with the protocol.  The
	// protocol can reject this, for example if another pipe is already
	// active on a 1:1 protocol.  The protocol may not block during this.
	int (*pipe_start)(void *);

	// pipe_close is an idempotent, non-blocking, operation, called
	// when the pipe is being closed.  Any operations pending on the
	// pipe should be canceled with NNG_ECLOSED.  (Best option is to
	// use nng_aio_close() on them)
	void (*pipe_close)(void *);

	// pipe_stop is called during finalization, to ensure that
	// the protocol is absolutely finished with the pipe.  It should
	// wait if necessary to ensure that the pipe is not referenced
	// any more by the protocol.  It should not destroy resources.
	void (*pipe_stop)(void *);
};

struct nni_proto_ctx_ops {
	// ctx_size is the size of a protocol context object.  The common
	// code allocates this memory for the protocol private state.
	size_t ctx_size;

	// ctx_init initializes a new context. The second argument is the
	// protocol specific socket structure.
	void (*ctx_init)(void *, void *);

	// ctx_fini destroys a context.
	void (*ctx_fini)(void *);

	// ctx_recv is an asynchronous recv.
	void (*ctx_recv)(void *, nni_aio *);

	// ctx_send is an asynchronous send.
	void (*ctx_send)(void *, nni_aio *);

	// ctx_options array.
	nni_option *ctx_options;
};

struct nni_proto_sock_ops {
	// ctx_size is the size of a protocol socket object.  The common
	// code allocates this memory for the protocol private state.
	size_t sock_size;

	// sock_init initializes the protocol instance, which will be stored
	// on the socket. This is run without the sock lock held.
	void (*sock_init)(void *, nni_sock *);

	// sock_fini destroys the protocol instance.  This is run without the
	// socket lock held, and is intended to release resources.  It may
	// block as needed.
	void (*sock_fini)(void *);

	// Open the protocol instance.  This is run with the lock held,
	// and intended to allow the protocol to start any asynchronous
	// processing.
	void (*sock_open)(void *);

	// Close the protocol instance.  This is run with the lock held,
	// and intended to initiate closure of the socket.  For example,
	// it can signal the socket worker threads to exit.
	void (*sock_close)(void *);

	// Send a message.
	void (*sock_send)(void *, nni_aio *);

	// Receive a message.
	void (*sock_recv)(void *, nni_aio *);

	// Options. Must not be NULL. Final entry should have NULL name.
	nni_option *sock_options;
};

typedef struct nni_proto_id {
	uint16_t    p_id;
	const char *p_name;
} nni_proto_id;

struct nni_proto {
	uint32_t                  proto_version;  // Ops vector version
	nni_proto_id              proto_self;     // Our identity
	nni_proto_id              proto_peer;     // Peer identity
	uint32_t                  proto_flags;    // Protocol flags
	const nni_proto_sock_ops *proto_sock_ops; // Per-socket operations
	const nni_proto_pipe_ops *proto_pipe_ops; // Per-pipe operations
	const nni_proto_ctx_ops * proto_ctx_ops;  // Context operations
};
```