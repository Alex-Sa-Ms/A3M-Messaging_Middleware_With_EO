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
<h6 style="color:limegreen">Note: The code algorithm here is incomplete, so look at the code to confirm the final and tested algorithm.</h6>
- I think the note below is not valid when a three-way handshake, that makes compatibility checks, is being used.
	**Note:** While this may seem like a non-symmetric procedure due to each side consulting their options such as max limits and block incoming requests, the algorithm is still symmetric. The reason being that this verification (apart from the compatibility one which is always done) is only done when initiating a linking process or when receiving a LINK message. If both sides send a LINK message, then both have agreed on linking if the compatibility is verified. The compatibility check is a symmetric process, so, is symmetry makes the linking process symmetric as well.  If only one side sends a LINK message, than it will receive the answer regarding the compatibility from a LINKACK message that carries the a code informing if the link was accepted or denied. If denied the reason may be fatal or non-fatal, with a fatal reason meaning that retrying must not be performed as it will yield the same result.

### Three-way handshake
**Note:** Well, a three-way handshake, like the TCP's SYN->SYN/ACK->ACK, is required before closing the link is allowed. This handshake is required to prevent data/control messages from arriving before the link is marked as established.

- First, `unlink()` is only allowed after the link establishment process is terminated, i.e., all the required LINK and LINKACK messages are received. This means, that when unlinking is wanted, the link must change to a `WAITING_TO_UNLINK` state, and wait for the required messages before deciding which unlinking behavior is required. 
	- The unlinking behavior may be:
		1. Change to `UNLINKING` and send `UNLINK` message when the link is established.
		2. Send `UNLINK` message and close, when an `UNLINK` message is received before the message containing the metadata that would establish the link.
		3. Just close, when a negative linking response is received in a `LINKACK` msg.
- After sending metadata, the next message (`LINKACK`) that is required to be delivered to the peer, must not contain metadata. This allows determining if the a `LINK` message has been sent before the `LINKACK` message, which means the `LINK` message must be caught, regardless of the success indicated by the `LINKACK` code, as we don't want unwanted links to occur but also we need to wait for the metadata before establishing the link, otherwise we can't verify the compatibility and we won't know how to handle the incoming data/control messages.
- Old `LINKACK` and `UNLINK` messages do not influence behavior, so this might help simply logic. The crucial message that must be caught is the `LINK` message.

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

## Failed ideas to search for problems to write in the thesis
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