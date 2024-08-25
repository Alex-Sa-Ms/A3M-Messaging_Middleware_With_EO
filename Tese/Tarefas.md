# Update VP diagram to include new ideas
# How should messages be composed
- Pagina -> [[Concepcao do protocolo de comunicacao]]
- Use Protobuff?
# Optimize locking mechanisms
- Think about optimized locking mechanisms for better efficiency, specially when delivering messages to sockets is required, which might invoke multiple wake-up calls (link waiters and socket waiters)
# I may need to make links nothing more than state
## Rationale
Having bi-directional association between socket and link is not an ideal design. Makes the code less readable and maintenance harder to do, since methods bounce back between the two classes.
## How can we expose link functionality to the user then?
### Rationale
Having link objects has its advantages. For instance, enabling the user to send, receive, poll, among other possible operations related to the link in specific.
### Solution
Separation of concerns: 
1. Have `LinkState` objects which the only purpose is to hold default related information about the links. 
2. The default link functionality must all be implemented on the `Socket` class using these `LinkState` instances. 
3. Have `Link` class be a wrapper of `Socket` but associated with the link in specific through the link identifier. All functionality provided by this class would be nothing more than invoking the already implemented logic on the `Socket` class.

Regarding the polling mechanism:
1. The wait queue would be in the `LinkState` but controlled by the `Socket` class, which would also implement the `poll(pt : PollTable) : int` method as `linkPoll(pt : PollTable) : int`.
2. The `Pollable` interface would be implemented by the `Link` object. For internal socket use, to implement the socket semantics, one must be careful to not hold the lock when waiting for availability.

# Logic executed by the middleware's main thread should not be blocking
## Rationale
Having blocking operations executed by the main thread of the middleware means halting its processing. Consequently, all the operations are delayed.
## Solution
Lockin  

#
--------- TAREFAS -----------

- Check message instead of payload. Link identifier can be relevant.
- Incoming & outgoing message checker instead of global checker method.
- Enable bypassing verification when send is requested by the socket,
        but not when sending directly using the public method.





----------- CONCLUSOES ------------

- isIncomingMessageValid() is not desirable. Such verification is done
by feedMsg() and feedCustomMsg(). When the message is not handled,
the feedCustomMsg() must inform such case by returning "false". The
feedMsg() must then discard the msg and log the event.
-
