# How to discard data messages that go over the flow control limit
## Rationale
Let's say socket A and socket B establish a link, with socket A providing 100 initial credits to socket B. However, after some time of exchanging messages, socket A decides to stop the flow, i.e., remove 100 credits. Let's assume that at that moment, socket A perceived socket B has having 100 credits, because no data messages were queued, however, socket B could have spent 23 credits already, i.e., sent 23 messages. Now, socket A would perceive B as holding 0 credits, therefore, no messages could be received from it, and socket B would have a negative balance of 23 credits, which would not allow it to send more messages, but it would have 23 messages that must be delivered and not discarded.
## Solution(s)
***Ignore this for now.*** We assume non-malicious sockets. If the middleware is used, this problem should not occur as sending more messages than the imposed limit is not allowed. However, when thinking about potential malicious users this situation should be taken into consideration.
### Solution
- Have a `loan` variable:
	- Starts at 0:
	- Does not get below 0 in any circumstance;
	- Increases when the credits are removed by the amount equal to the credits removed.
	- Decreases when credits are given by the minimum between the amount of credits given and the amount that would make the `loan` variable become smaller than 0.
	- When different than 0, it also decreases with each received message.
	- If a received message would lead to the `loan` variable becoming less than 0, then the source of the message should be blacklisted and its associated link must be closed, as it is not respecting the flow control limit imposed.
- Notes:
	- This solution establishes a margin of acceptance, upon which, the reception of messages is not allowed. A non-malicious source will not use more credits than it should, however, it would be possible for malicious sources to explore this "vulnerability" and send more messages than it was supposed to until the `loan` variable reaches 0. However, after the `loan` variable reaches 0, the source would be forced to comply with the limit, otherwise, it would become blacklisted and have its link be terminated.

# API for sending/receiving messages to/from link
## Rationale

## Sub-problems
### How should the Link - Socket visibility be?
- Socket and the message system (MS) need to see each other. The socket needs to see MS to send messages. MS needs to see socket to deliver incoming messages.

# How can a PAIR socket not allow multiple connections?
## Rationale
- A PAIR socket is supposed to be an exclusive communication socket, i.e. it can only link to a single socket. 
## Solution
- `setOption()`/`getOption()` first call the `setCustomOption()`/`getCustomOption()`. If the option is not handled (for instance, blocking the change/retrieval of a parameter should be done by the custom method), then the `setDefaultOption()`/`getDefaultOption()` is called. These defaults methods are implemented by the internal socket implementation.
- Allow setting a limit of simultaneously active links.
- Changing this limit is blocked by the socket's `setCustomOption()`, preventing the establishment of more connections which could result in improper behavior.
- Since the sockets are assumed to not be malicious and to not crash, forcing the closure of the connection after inactivity does not seem to be a requirement for now.
# Exon Cookies can be useful for pinging purposes
## Rationale
- Exon Cookies can be useful for pinging purposes. Require only a 4-way exchange to check if a message is arriving at the destination.
## Solution
- Make Exon allow defining a cookie and returning them.
	- Check if the decision about this was documented:
		- Global queue? Individual queue?
		- Single (merged) retrieving method? Prioritize one using ratios?
		- Retrieving method for each?
## Alternative solution **(Works for now)**
- Send a ping control message, and expect a pong control message or another message, to reset timeout.

# Is RAW socket mode still wanted? 
- Same functionality can be obtained with more work, by creating a new socket with the RAW semantics, maybe even through inheritance it can be done easily.
- Does it ignore the socket protocol as a whole? Are all messages considered data messages and exposed as is?
	- If so, this can be achieved easily by bypassing the custom feed method and adding all messages to the appropriate link's incoming queue. When such mode is active, we must employ another mechanism which checks every message to see if the flow control mechanism should be employed or not.
# Can caching the hashCode() be helpful for efficiency?
--***TODO***--
# How to design links so that they can be exposed to client
## Rationale
Exposing a link can be helpful when the socket semantics has link related functionality such that using the polling mechanism over the links could be desirable.
## Solution 1
Easiest solution is to have a lock for each link
## Solution 2
Sockets that require such functionality can create wrapper objects that use the required locking mechanism to ensure consistency.
# What to do when a link has incoming messages and close() is invoked?
## Solution 1
- Provide the link reference in an event to the `customHandleEvent()` which allows the queue to be drained if required by the socket semantics. 

# Linking process should include incarnation number
## Rationale
If socket A establishes a link with socket B, closes the link and attempts to link again, what prevents messages from the previous link establishment from interfering with the current link? For instance, a flow control message providing/removing credits.

# Cookies with same priority as messages
## Rationale
Cookies are essentially a confirmation of reception message. Since we can consider it a message, then storing them in the same queue should not be a problem. A counter for messages is still required to create the limit of messages that can be in queue.

# Cookies for ordering and correctness
## Rationale
A *Cookie* can be used to determine when a message has arrived at the destination. Since messages are processed by order of arrival, a Cookie can be used to determine when it is safe to send a new message so that it does not arrive first at the destination. This does not work if the messages have different priorities and are reordered by the socket custom logic. 

# Linking algorithm reformulated
## Final Solution
- To establish the link, both sockets must exchange their metadata (such as protocol) and their answer regarding the establishment of link. When a socket receives from the other socket's metadata, it formulates a decision and sends it. This decision can also be influenced by the other socket's decision if it was already received.
- The most common scenario should be a 3-way handshake:
	- `LINK` -> `LINKREPLY` w/metadata -> `LINKREPLY` w/out metadata.
- A predicted less common scenario is a concurrent initiation of the linking process by both sockets, i.e., both sockets send a `LINK` message concurrently.
### Key ideas
- To establish a link, a socket must receive compatible metadata and a positive answer from the peer.
- If a socket detects the peer sent a link request, the socket must catch such link request before establishing or closing the link to prevent unwanted behavior, such as initiating a new linking process after deciding to cancel the linking process.
	- The socket detects the peer sent a link request when it receives a link reply to its own linking request but the link reply does not carry any metadata as it was already sent in a link request.
- `LINK` messages correspond to link requests. These initiate the linking process. The presence of the metadata is mandatory in this messages.
- `LINKREPLY` messages are replies to link requests. The primary goal of these messages is to carry the reply, whether accepting (positive reply) or rejecting (negative reply, which can be fatal or non-fatal). However, since the peer must also receive the socket's metadata, if the metadata has not already been sent during the current linking process, then these messages must also carry the metadata of the socket.
	- Replies can be:
		- *positive*, meaning the socket accepted the establishment of the link on its end.
		- *(negative) non-fatal*, meaning the socket could not accept the establishment of the link at the moment, but may accept in the future. An example is when the defined limit of active links is reached, so until a link is closed, a new link will not be accepted.
		- *(negative) fatal*, meaning the socket cannot accept the establishment of the link due to a condition that most likely will not change, regardless of how many link requests are made. For instance, if the sockets engaging in the linking process talk using incompatible protocols, or if the socket has defined an option to not accept incoming requests.
			- Note, that changing the limit of active links enables preventing new links to be created if such condition is required temporarily. The option to reject incoming link request should only be set if such decision is permanent. 
- `UNLINK` messages are used for the unlinking process. A socket can only begin the unlinking process after the link becomes established.
- *Clock identifiers* are required to prevent messages from different linking processes to interfere with the current linking/unlinking process. 
	- Sockets attribute a new clock identifier to each new linking process. The attribution is done in an ascending order, enabling sockets to distinguish when a message refers to an old process, the current process or a new process. Old messages are discarded. Current messages are handled. And new messages, which should be link requests, are replied with a non-fatal response so that the peer can schedule a link retry and hopefully this link retry arrives after the current linking process is finished.
	- Since messages do not propagate instantaneously and may arrived in an unordered manner, all socket messages carry the clock identifier of the linking process to enable determining which messages correspond to the current linking process and which do not. Determining which messages do not correspond to the current linking process is crucial to prevent unwanted behavior. For instance, receiving a DATA message from a previously established and then closed link is not desirable as it would affect the flow control mechanism.
### Clock identifier
\<explain here>
### Link states
- `LINKING` - When waiting for both the metadata and answer from the other socket to arrive. An exception is when the metadata is enough to determine that linking is not possible, so after sending a negative response to the other peer, the link is closed.
- `ESTABLISHED` - When compatible metadata and a positive answer was received from the other socket. 
- `CANCELLING` - When during the linking process, the establishment of the link is no longer desired. 
- `UNLINKING` - When the closure of the link is desired after the link has been established.
- `CLOSED` - When the link is effectively closed.
### Algorithm
**Note:** Currently, the metadata is very small, so fatal `LINKREPLY` messages carry the metadata. However, in the future, to avoid unnecessary resource consumption, carrying the protocol identifier or a flag informing that the metadata was not sent in a `LINK` message should be implemented.    
#### `link()` method invocation:
- If link exists:
	- If state equals:
		- `UNLINKING`/`CANCELLING`: throw exception informing an unlinking procedure is in progress.
		- Other state: ignore.

- If link does not exist:
	- Throw exception if max number of links has been reached.
	- Create link with a new clock identifier
	- Set link to `LINKING`
	- Send `LINK` message with the socket's metadata, such as protocol identifier and incoming capacity.
#### Received `LINK` message:
- If link exists:
	- Ignore if carrying an old clock identifier. 
	- If state equals:
		- `LINKING`:
			- If waiting metadata:
				- Check if there is a scheduled link request and cancel it.
					- *If there was a scheduled request, any sent LINKACK must carry the socket's metadata. If there wasn't, the metadata was already sent.*
				- If peer's answer has not yet been received:
					- set peer's metadata and clock identifier
					- Check compatibility.
						- If compatible:
							- Send positive `LINKREPLY`.
						- If not compatible:
							- Send fatal `LINKREPLY`.
							- Close link.
				- If peer's answer has already been received:
					- Assert peer's clock identifier matches.
					- If peer's answer was:
						- Positive:
							- Check compatibility.
								- If compatible:
									- Send positive `LINKREPLY` without metadata
									- Establish link
								- Else:
									- Send fatal `LINKREPLY` without metadata
									- Close link.
						- Non-fatal:
							- Check compatibility:
								- If compatible:
									- Reset peer metadata.
									- Schedule new link request.
								- Else:
									- Close link.
						- Fatal:
							- Close link.
			- If not waiting metadata (new linking process):
				- Assert clock identifier is higher.
				- Send non-fatal `LINKREPLY` w/ metadata (for the peer to schedule a retry)
		- `ESTABLISHED`:
			- Ignore. `LINK` cannot be received in this state, because for the peer to send this message, it must not have a link established. Since the link is established, the peer must received an `UNLINK` message before, sending a `LINK` message. 
		- `CANCELLING`:
			- If waiting for peer's metadata:
				- If peer's `LINKREPLY` has not been received:
					- Send fatal `LINKREPLY`
					- Close link.
				- If peer's `LINKREPLY` has been received:
					- Assert peer's clock identifier matches.
					- If peer's `LINKREPLY` answer was:
						- positive:
							- Send fatal `LINKREPLY`.
							- Close link.
						- fatal / non-fatal:
							- Close link.
			- If not waiting for peer's metadata (new linking process):
				- Assert clock identifier is higher.
				- Send non-fatal `LINKREPLY` w/ metadata (for the peer to schedule a retry)
		- `UNLINKING`:
			- Assert clock identifier is higher.
			- Send non-fatal `LINKREPLY` w/ metadata (for the peer to schedule a retry)

- If link does not exist:
	- Check linking conditions not related to the peer.
		- Is socket closed? 
			- Send *fatal* `LINKREPLY` w/ metadata back and return.
		- Maximum amount of links reached? 
			- Send *non-fatal* `LINKREPLY` w/ metadata back and return.
		- Are incoming linking requests not allowed? 
			- Send *fatal* `LINKREPLY` w/ metadata back and return.
	- Check compatibility with the peer by using the metadata present in the message.
		- If compatible:
			- Create link in `LINKING` state.
			- Set peer's metadata and clock identifier.
			- Send positive `LINKREPLY` w/ metadata.
		- Else:
			- Send fatal `LINKREPLY` w/ metadata.
#### Received `LINKREPLY` message:
- if link exists:
	- Ignore if carrying an old clock identifier. 
	- if state is:
		- `LINKING`:
			- if waiting peer's metadata:
				- if `LINKREPLY` carries metadata (if contains protocol identifier):
					- **Note:** If carrying metadata, then the peer has not sent a `LINK` message.
					- If answer is:
						- positive:
							- check compatibility:
								- if compatible:
									- set peer's metadata
									- send positive `LINKREPLY` w/ out metadata.
									- establish link.
								- if not compatible:
									- send fatal `LINKREPLY` w/ out metadata.
									- close link.
						- fatal:
							- close link.
						- non-fatal:
							-  if compatible:
								- schedule a retry, i.e. schedule the sending of a new `LINK` message
							- else:
								- close link
				- if `LINKREPLY` does not carry metadata:
					- set peer's clock identifier
					- save the peer's answer for processing once the `LINK` message is received
			- if not waiting peer's metadata (`LINK` message has been received):
				- Assert peer's clock identifier matches.
				- If answer is:
					- positive:
						- establish link
					- fatal:
						- close link
					- non-fatal:
						- reset peer's metadata
						- schedule link retry
		- `ESTABLISHED`:
			- ignore
		- `CANCELLING`:
			- if link exists:
				- if waiting peer's metadata:
					- if `LINKREPLY` carries metadata:
						- if answer is:
							- positive:
								- send fatal `LINKREPLY`
								- close link
							- fatal/non-fatal:
								- close link
					- if `LINKREPLY` does not carry metadata:
						- set peer's clock identifier
						- save the peer's answer for processing once the `LINK` message is received
				- if not waiting peer's metadata:
					- **Note**: If not waiting for peer's metadata, this means a positive answer was sent to the peer and only after that did the state of the link progress to `CANCELLING`. This means that if the peer's answer is positive, the peer assumes the link is established.
					- assert peer's clock identifier matches
					- If answer is:
						- positive (peer assumes link as established):
							- set state to `UNLINKING`
							- send `UNLINK` msg
						- fatal/non-fatal:
							- close link
			- if link does not exist:
				- ignore
		- `UNLINKING`:
			- ignore message (not possible)
- if link does not exist:
	- ignore message (The `LINK` message must have been received and resulted in a fatal closure.)
#### Received `DATA` or custom control message:
- **Note:** There is always a socket that perceives the link as established or closed first. In this case, the moment a socket perceives the link as established, it may send a data or control message. Since exactly-once does not tolerate discarding of messages, if the socket's link state is `LINKING`, the peer's metadata has been received and is compatible, and only the peer's positive answer is missing for the link to be established, then, the arrival of a data or control message can be assumed to have arrived before the positive `LINKREPLY` message.
- Check if message is a data or custom control message.
- If link exists:
	- Assert clock identifier matches.
	- If link state is:
		- `LINKING`:
			- if waiting peer's metadata:
				- Not possible. Peer cannot be in the `ESTABLISHED` state since the socket has not yet received the peer's metadata which is required to send an answer regarding the establishment of the link.
			- if not waiting peer's metadata:
				- establish link.
		- `ESTABLISHED`:
			- accept message
		- `CANCELLING`:
			- if waiting peer's metadata:
				- Not possible, for the same reason explained above.
			- If not waiting peer's metadata:
				- **Note:** Same process as when receiving a positive `LINKREPLY` message under this same circumstances (`CANCELLING` state and not waiting peer's metadata).
				- set state to `UNLINKING`
				- send `UNLINK` message
		- `UNLINKING`:
			- Let the message be handled by the socket since the link has not yet been closed.
- If link does not exist:
	- ignore.
#### Received `ERROR` message:
- if link exists:
	- if link state is:
		- `LINKING`
			- if error is:
				- `NFOUND` - socket not found:
					- schedule a new retry
- else:
	- ignore

#### `unlink()` method invocation:
-Note:  Cancel any scheduled requests must have been cancelled.
- if link exists:
	- if link state is:
		- `LINKING`:
			- Has scheduled request:
				- Cancel request.
				- Close link.
			- Does not have scheduled request:
				- Set state to `CANCELLING`
		- `ESTABLISHED`:
			- set state to `UNLINKING`
			- send `UNLINK` message
		- `UNLINKING`/`CANCELLING`:
			- ignore
- if link does not exist:
	- ignore
#### Received `UNLINK` message:
- if link exists:
	- if link state is:
		- `LINKING`:
			- **Note:** Sender of `UNLINK` established the link and sent an `UNLINK` message, right after.
			- if waiting for peer's metadata:
				- ignore. not possible.
			- if not waiting peer's metadata (is waiting for answer only):
				- assert peer's clock identifier matches
				- send `UNLINK` message
				- close link.
		- `ESTABLISHED`
			- assert peer's clock identifier matches
			- sent `UNLINK` message
			- close link
		- `CANCELLING`:
			- if waiting peer's metadata:
				- ignore. not possible.
			- if not waiting peer's metadata (is waiting for answer only):
				- assert peer's clock identifier matches
				- send `UNLINK` message
				- close link
		- `UNLINKING`:
			- assert peer's clock identifier matches
			- close link
- if link does not exist:
	- ignore. Not possible under the correct functioning of the algorithm.


### Why is a 3-way handshake required?
- Although the assumptions of this thesis is for the employment in a safe environment, ensuring some security is desirable. It also enables the project to roam to a new phase  of exploring how to make the middleware deployable on the currently called unsafe environments.
- Where is the safety in using 3-way handshake? Well, 3-way handshake was designed to prevent a socket from receiving messages from other socket while not knowing if the other socket is compatible or not.
	- The 2-way handshake allowed data/custom control messages to reach the destination before the `LINK` message. Without receiving the `LINK` message, which contains the sender's metadata, the peer cannot determine if the messages being received can be accepted or not. The messages cannot be discarded since the exactly-once semantics do not allow it. So, when using the 2-way handshake, the only solution was to queue the messages. Data messages could be controlled by the flow control, with abusers being detected if they trespassed the boundaries set by the capacity sent on the `LINK` message. However, since custom control messages are not limited by the flow control mechanism, malicious sockets could use this as an exploit and flood the socket that has not yet received the `LINK` message. 
		- Passing such custom control messages to be verified by the socket's custom logic could be an option, however, that would not work for socket's that talk multiple protocols, nor would it work when the verification require the presence of state which may require the identification of the peer's protocol. 
### Symmetry
- The three-way handshake is a non-symmetric procedure, as it requires both sockets to manifest there final decision regarding the establishment of the link. While not symmetric, it sets a base for more elaborate linking processes.
- The two-way handshake, on the other hand, is symmetric. While it may seem like a non-symmetric procedure due to each side having their own conditions such as max limits and block incoming requests, the algorithm is still symmetric. The reason being that this verification (apart from the compatibility one which is always done) is only done when initiating a linking process or when receiving a LINK message. If both sides send a LINK message, then both have agreed on linking if the compatibility is verified. The compatibility check is a symmetric process, so, is symmetry makes the linking process symmetric as well.  If only one side sends a LINK message, than it will receive the answer regarding the compatibility from a LINKREPLY message that carries the a code informing if the link was accepted or denied. If denied the reason may be fatal or non-fatal, with a fatal reason meaning that retrying must not be performed as it will yield the same result.
### Three-way handshake
#### Rationale 
- Enabling options such as "max links" and "block incoming requests" makes the linking process not symmetric, meaning both sockets need to accept the link establishment.
- This linking protocol has a small additional overhead, of one extra-step, in comparison with the symmetric linking protocol, but enables more complex linking processes to be designed. The LINK and LINKREPLY messages could be used to carry custom metadata providing a way to specify more restrictions for a link to be established or simply for piggybacking information relevant for the links.
- Extract more reasons from following text (related to exploit):
  ```
  what happens when a DATA/CONTROL message is received before the link is established?
          Accumulate messages in the incoming queue and when the link is established, feed them
          again to the socket's handle custom msg?
          - Solution 1:
              1. Link queue should only be initialized using the socket's getInQueueSupplier when
              the link passes to established.
              2. When a message is received before the link is established, the inQueue is initialized
              to a LinkedList<> so that the messages can be stored until the link establishment.
              Problem: Introduces vulnerability. Messages can be bombarded before the arrival of the
              message that accepts the link. Since the exactly-once semantic means not discarding messages,
              the memory could be easily exhausted using this exploit. However, for other solution to
              not be an exploit, we need to have an "abuser detector" on the messages received using the discussed
              "acceptable range" when a change in the window for a lower value is performed.
              Problem 2: Even with such detector mechanism, the control messages could still be used for this purpose,
              as they are not influenced by flow control. To prevent control messages from being used for such purposes,
              these must not be queued and by being handled immediately, one could detect contabilize faulty messages
              and add the socket or link in question to a blacklist. The best solution would actually be adding the socket
              to a firewall to block its traffic.
          - Solution 2:
            1. Do SYN -> SYN/ACK -> ACK, i.e., LINK -> LINKACK -> LINKACK (the last one may carry the clockId and success code only).
                    - Currently, when a socket receives a LINK msg from a compatible socket, it creates a
                    link and immediately sets it to established. This means, that data and control messages
                    can be sent right after, potentially reaching the destination before the LINKACK message
                    that would make the requester establish the link on its side as well. If a data/control
                    message reaches the requester before the link is established, the requester does not know
                    what kind of socket it is talking to, so it can't possibly process the message. Queuing
                    the messages could be a solution until the establishment of the link, but taht would create
                    the exploit mentioned above, so, the most appropriate solution is in fact queuing the messages
                    on the sender (in a outgoing queue) and dispatch them only after receiving a LINKACK message
                    from the other peer.
  ```
#### Solution
**Note:** Well, a three-way handshake, like the TCP's SYN->SYN/ACK->ACK, is required before closing the link is allowed. This handshake is required to prevent data/control messages from arriving before the link is marked as established.

- First, `unlink()` is only allowed after the link establishment process is terminated, i.e., all the required `LINK` and `LINKREPLY` messages are received. This means, that when unlinking is wanted, the link must change to a `CANCELLING` state, and wait for the required messages before deciding which unlinking behavior is required. 
	- The unlinking behavior may be:
		1. When the link is established, change to `UNLINKING` and send `UNLINK` message.
		2. When a negative linking response is received in a `LINKREPLY` msg, just close.
- After sending metadata, the next message (`LINKREPLY`) that is required to be delivered to the peer, must not contain metadata. This allows determining if the a `LINK` message has been sent before the `LINKREPLY` message, which means the `LINK` message must be caught, regardless of the success indicated by the `LINKREPLY` code, as we don't want unwanted links to occur but also we need to wait for the metadata before establishing the link, otherwise we can't verify the compatibility and we won't know how to handle the incoming data/control messages.
- Old `LINKREPLY` and `UNLINK` messages do not influence behavior, so this might help simply logic. The crucial message that must be caught is the `LINK` message.
### Initial 2-way handshake algorithm:
 ```
/*

        *** Linking algorithm ***

        - A clock is required to determine the identifier of the link.
        - Each link is a combination of a clock identifier from each peer.
        - Every link-related message carries the integer identifier of the link (the identifier generated on its own side).

        Link states:
        LINKING
        UNLINKING
        WAITING-TO-UNLINK
        ESTABLISHED
        UNLINKED

        When invoking link():
        1. If link exists:
            1-LINKING/ESTABLISHED:
                1. If link state is LINKING or ESTABLISHED, do nothing and return.
            1-UNLINKING/WAITING-TO-UNLINK:
                1. Throw exception, saying, link is closing (IllegalStateException)
        2. Create link with peer's identifier as -1.
        3. Set link to LINKING state.
        4. Send a LINK message to the peer.
        5. Wait for a message from the peer (rejects any message with an identifier smaller than the currently saved peer's identifier)
            5-LINK:
                1. If either a link or a positive link acknowledgement message is received, set link state
                    to "established", update the peer's identifier using the received value and return.
            5-REFUSAL:
                1. If a negative link acknowledgment message is received,
                   determine if the reason is fatal or non-fatal.
                    5-REFUSAL-FATAL:
                        1. If fatal, close and delete link. The closure must wake up
                           all waiters with a POLLHUP and POLLFREE notification.
                    5-REFUSAL-NON-FATAL:
                        1. If not-fatal, schedule a retry.
                            NOTE: Since there is a possibility of the scheduled retry undoing an unlink from the peer, 
                                  when the peer sends a link and unlink messages after sending the non-fatal refusal,
                                  the scheduled dispatches should return an atomic reference that enables cancelling
                                  the dispatch. To cancel the dispatch, one should set the value to "null". The messaging
                                  system, will also set the value to "null" using getAndSet(null) enabling to verify if
                                  the message was dispatched. 
            5-UNLINK:
                1. If an unlink message is received, send an UNLINK message and close the link.


        When receiving a LINK message:
        1. If link exists (discard if not a newer clock identifier):
            1-LINKING: Link is in a "LINKING" state
                1. Set link state to ESTABLISHED and save peer's identifier.
                2. If there is a scheduled retry, cancel it, and dispatch it immediatelly.
                3. If there isn't a scheduled retry, then, nothing more is required.
                NOTE: If the link state is LINKING, then a link message has already been sent and
                due to the symmetry of the linking procedure, the peer should also establish the LINK on his side.
            1-ESTABLISHED:
                1. Can't happen because a peer cannot send a LINK message unless it does not have a link.
                If the link was established and closed recently, then both peers confirmed the unlink operation,
                meaning, neither peer can be in an ESTABLISHED state.
                <discarded>. However, if it could happen: 
                    Keep ESTABLISHED, DO NOT SAVE peer's clock, and send a LINKACK with a non-fatal negative response
                    "ALREADY_LINKED". This makes sure a link retry can happen in the future, potentially, when the
                    link has already received the  
                    NOTE: Linking symmetry assumes it will always lead to the same result,
                    so there is no need to check the message.
            1-UNLINKING:
                1. Newer link message is being received:
                    Keep the same state, send a LINKACK with a non-fatal negative response
                    "ALREADY_LINKED". This makes sure the link establishment request can be retried until
                     the peer receives the unlink message that closes the link, and enables the link
                     to be established again.
            1-WAITING-TO-UNLINK:
                1. Update peer's identifier, change to UNLINKING and send an UNLINK msg.
        2. If link does not exist, analyze LINK message and link restrictions (maxLinks).
            2-COMPATIBLE:
                1. Create link with peer's clock identifier.
                2. Set link state to "ESTABLISHED".
                3. Send a positive LINKACK message.
            2-NOT_COMPAT:
                1. Send a negative LINKACK informing which reason is behind the refusal.

        When receiving a LINKACK message :
        1. If link exists (discard if not a newer clock identifier):
            1-LINKING: Link state is LINKING
                1. Set link state to "established", update the peer's identifier using
                 the received value and return.
            1-ESTABLISHED: Link state is ESTABLISHED
                1. Ignore message. Peer clock identifier must match.
                NOTE: A no-match is not possible, because for a newer clock identifier to
                be present in the message, an unlink procedure had to be performed which 
                involves both peers agreeing on the unlink.
            1-UNLINKING: Link state is UNLINKING
                1. Ignore message.
            1-WAITING-TO-UNLINK:
                1. If a close flag is set, meaning an UNLINK message has been received before the LINK/LINKACK message,
                then send an UNLINK msg and close the link.
                2. If the close flag is not set: Update peer's identifier, change to UNLINKING and send an UNLINK msg.    
                 
        2. If link does not exist:
            - Ignore message.

        ** Unlinking **

        When invoking unlink():
        1. If does not exist, do nothing and return.
        2. If the link exists:
            2-LINKING:
                1. If has scheduled retry, cancel it and close link.
                2. If there isn't a scheduled retry, then a link message has been sent.
                   Set state to WAITING-TO-UNLINK.
            2-ESTABLISHED:
                1. Set state to UNLINKING and send unlink message.
            2-UNLINKING:
                1. Return.
            2-WAIITING-TO-UNLINK:
                1. Return.

        When receiving UNLINK:
        1. If link does not exists, do nothing and return. This scenarion shouldn't be possible.
        2. If link exists:
            2-LINKING:
                1. Change to state WAITING-TO-UNLINK, update peer's identifier and set close flag to true.
            2-ESTABLISHED:
                1. Send unlink message and close link.
            2-UNLINKING:
                1. Close link.
            2-WAIITING-TO-UNLINK:
                1. Ignore this UNLINK msg. The other peer is in an UNLINKING state because it sent this
                UNLINK message. And since the WAITING-TO-UNLINK state, means waiting for the message that
                would establish the link before sending an UNLINK message, the peer will receive the required
                UNLINK message to close the link when the LINK/LINKACK message is received by this socket. 
                However, this socket does need to have a flag indicating that it can close immediatelly,
                instead of passing to the UNLINKING state.
     */
```

### Failed ideas to search for problems to write in the thesis
```
 /*
    TODO - Linking/Unlink algorithm:

        *** Linking algorithm ***

        When invoking link():
        1. If link exists, do nothing and return.
        2. Create link, set it to "linking" state, and send a link message to the peer.
        3. Wait for a link/link acknowledgment message from the peer to confirm the establishment.
        4-LINK:
            1. If either a link or a positive link acknowledgement message is received, set link state
                to "established" and return.
        4-REFUSAL:
            1. If a negative link acknowledgment message is received,
               determine if the reason is fatal or non-fatal.
                4-REFUSAL-FATAL:
                    1. If fatal, close and delete link. The closure must wake up
                    all waiters with a POLLHUP and POLLFREE notification.
                4-REFUSAL-NON-FATAL:
                    1. If not-fatal, schedule a retry.
        4-UNLINK:
            1. If an unlink message is received, set state to "UNLINKING" and send an UNLINK message.

        NOTE: COOKIES could make solving this easier. One would require the cookie from a link-related
        message sent to that particular link to be received by the destination before sending a new
        link-related message. This would avoid messages cancelling each other.

        When receiving a LINK message:
        1. If link exists and in a "LINKING" state, set link state to ESTABLISHED.
        2. If link does not exist, analyze LINK message and link restrictions (maxLinks) :
            2-COMPATIBLE. Create link, set link state to "ESTABLISHED" and send a positive LINKACK message.
            2-NOT_COMPAT. Send a negative LINKACK informing which the reason behind the refusal

        When receiving a LINKACK message:



        ** Unlinking **

        1. If does not exist, do nothing and return.
        2. If the link is in an "UNLINKING" state, do nothing and return.
        3. If the link is "ESTABLISHED", set state to "UNLINKING" and send an unlink message.
        4.


     */

    /*
    NOTE: COOKIES could make solving this easier. One would require the cookie from a link-related
        message sent to that particular link to be received by the destination before sending a new
        link-related message. This would avoid messages cancelling each other.
     */


    /*
    TODO - Linking/Unlink algorithm with causal consistency clock:

        *** Linking algorithm ***

        - A clock is required for causal consistency.
        - Every link-related message carries the socket's clock and increases the clock.
        - Ignore any message with clock smaller than the last highest saved clock of the peer.

        When invoking link():
        1. If link exists:
            1-LINKING/ESTABLISHED:
                1. If link state is LINKING or ESTABLISHED, do nothing and return.
            1-UNLINKING:
                1. If link state is UNLINKING, go to step 3.
        2. Create link with peer's clock as -1.
        3. Set link to LINKING state.
        4. Send a LINK message to the peer.
        5. Wait for a message from the peer (rejects any message with a clock smaller than the currently saved peer's clock)
            5-LINK:
                1. If either a link or a positive link acknowledgement message is received, set link state
                    to "established", update the clock of the peer to match the received clock and return.
            5-REFUSAL:
                1. If a negative link acknowledgment message is received,
                   determine if the reason is fatal or non-fatal.
                    5-REFUSAL-FATAL:
                        1. If fatal, close and delete link. The closure must wake up
                           all waiters with a POLLHUP and POLLFREE notification.
                    5-REFUSAL-NON-FATAL:
                        1. If not-fatal, schedule a retry.
                            NOTE: Since there is a possibility of the scheduled retry undoing an unlink from the peer,
                                  when the peer sends a link and unlink messages after sending the non-fatal refusal,
                                  the scheduled dispatches should return an atomic reference that enables cancelling
                                  the dispatch. To cancel the dispatch, one should set the value to "null". The messaging
                                  system, will also set the value to "null" using getAndSet(null) enabling to verify if
                                  the message was dispatched.
            5-UNLINK:
                1. If an unlink message is received, send an UNLINK message and close the link.


        When receiving a LINK message (discard if not a newer clock) :
        1. If link exists:
            1-LINKING: Link is in a "LINKING" state
                1. Set link state to ESTABLISHED and save peer's clock.
                NOTE: If the link state is LINKING, then a link message has already been sent and
                due to the symmetry of the linking procedure, the peer should also establish the LINK on his side.
            1-ESTABLISHED:
                1. Keep ESTABLISHED and save peer's clock.
                    NOTE: Linking symmetry assumes it will always lead to the same result,
                    so there is no need to check the message.
            1-UNLINKING:
                1. Change to ESTABLISHED and save peer's clock.
                    NOTE: This changes theoretically should have impact on the state (such as queued incoming messages),
                    since a successfully closed link has its associated state lost, which means
                    that by not having in mind this possible changes, the sockets may have incongruent states.
        2. If link does not exist, analyze LINK message and link restrictions (maxLinks).
            2-COMPATIBLE:
                1. Create link with peer's clock.
                2. Set link state to "ESTABLISHED".
                3. Send a positive LINKACK message.
            2-NOT_COMPAT:
                1. Send a negative LINKACK informing
                which the reason behind the refusal.

        When receiving a LINKACK message (discard if not a newer clock):



        ** Unlinking **

        1. If does not exist, do nothing and return.
        2. If the link is in an "UNLINKING" state, do nothing and return.
        3. If the link is "ESTABLISHED", set state to "UNLINKING" and send an unlink message.
        4.


     */
```

# Think about optimizing contention areas later
- Search lock-free algorithms
- Use atomic operations where they are possible
- Add finer-grained locks if necessary.
- Use read-write-locks.
	- For instance, try acquiring write lock instantaneously, if not possible use read lock with combination of atomic operations.
		- When the middleware thread wants to queue a message in a socket, if it can acquire the lock immediately, then handle the message queuing until it is finished. If not possible, then add it in a temporary queue. Queuing in this temporary queue is assumed to be only done by the middleware thread, so, if we initiate two of these queues initially, the thread that acquires the write lock, can swap this queues atomically. 
			- Is this idea correct?