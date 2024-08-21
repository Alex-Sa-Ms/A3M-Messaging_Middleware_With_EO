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