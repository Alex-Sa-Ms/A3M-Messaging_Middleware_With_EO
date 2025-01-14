# Structure
1. ...
2. **[[Exon - Adaptations and Modifications]]**
3. ...
## Writing Guidelines

- The writing of the "Middleware Design Challenges and Solutions" chapter should be done in chronological order, somehow trying to tell a story on how the design was done.
- Mention the rationale behind each entity/component.
# Topics to talk about
*(Collection of ideas/topics that may help in writing)*
## Reference Notes
- [[Problems]]
- [[Spur-of-the-moment Problems]]
- [[Creating a custom socket (delete after)]]
- [[Tarefas]]
- [[Concepcao do MOM (A3M)]]
- ...
## General Ideas
- Rationale behind each entity/component
## Architecture Requirements
**(Maybe this should be in the overview of messaging middleware goals, but it can also be used to justify design decisions)**
- Socket-based API
- Allow socket extensibility (i.e. creating/extending sockets)
- Allow new custom sockets to have custom API
- Messages received should be handled in stages, having the socket's core process the message before it is delivered to the custom processing method.
	- Required to process control core messages that shouldn't be received by the socket's custom logic
- Core middleware functionality should be shared across all sockets and not be modifiable to prevent undefined behavior.
- All sockets, regardless of their type, should understand the same linking protocol.
- RAW and COOKED sockets can be created.
	- *Its possible, but doesn't seem to make sense the existence of RAW sockets for the moment. A RAW socket can be implemented as a COOKED socket, so the creation of RAW sockets is not yet permitted.*
## Design Questions
- Does every method need to be implemented from scratch for every socket implementation?
- Could I have something like a pipe upon the establishment of a link? Does it make sense to exist?
	- Pipe is analogous to link socket. More flexible and usable way of managing each link.

### ZeroMQ Problems questions
Reference: https://zguide.zeromq.org/docs/chapter1/#Why-We-Needed-ZeroMQ

**The answer to these questions should be put in their respective sections.**

- *"How do we handle I/O? Does our application block, or do we handle I/O in the background? This is a key design decision. Blocking I/O creates architectures that do not scale well. But background I/O can be very hard to do right."*
	- Elaborate answer in **[[Concurrency and Scalability Design#Handling I/O Operations]]**
	- If a brief answer is to be given, while the complete answer is given elsewhere, then mention something like:
		- Blocking behavior being required as to not discard messages as a consequence of the creation rate being superior to the delivery rate. Discarding would violate the exactly-once delivery guarantee.
		- Background threads for I/O operations such as transmitting and receiving, enabling the client threads to be freed of such responsibility and therefore being able to execute other work.
		- ...

- "*How do we handle dynamic components, i.e., pieces that go away temporarily? Do we formally split components into “clients” and “servers” and mandate that servers cannot disappear? What then if we want to connect servers to servers? Do we try to reconnect every few seconds?*"
	- The approach taken with this middleware does not have "clients" nor "servers". Sockets can both send and receive link establishment requests to any other socket. With exactly-once delivery being guaranteed by the Exon library, a link establishment requests are ensured to be delivered at the destination node. While the request is ensured to arrive at the destination node (if it is a valid destination), the socket in question may not yet have been created. For that reason, the linking protocol ensures the linking process is retried every few moments until a proper response from the socket is received.  
	- Ideas:
		- Failover: connect to multiple sockets which can be used as failovers, for scalability, etc
		- 

- "*How do we represent a message on the wire? How do we frame data so it’s easy to write and read, safe from buffer overflows, efficient for small messages, yet adequate for the very largest videos of dancing cats wearing party hats?*"
    
- "*How do we handle messages that we can’t deliver immediately? Particularly, if we’re waiting for a component to come back online? Do we discard messages, put them into a database, or into a memory queue?*"
    
- "*Where do we store message queues? What happens if the component reading from a queue is very slow and causes our queues to build up? What’s our strategy then?*"
    
- "*How do we handle lost messages? Do we wait for fresh data, request a resend, or do we build some kind of reliability layer that ensures messages cannot be lost? What if that layer itself crashes?*"
    
- "*What if we need to use a different network transport. Say, multicast instead of TCP unicast? Or IPv6? Do we need to rewrite the applications, or is the transport abstracted in some layer?*"
    
- "*How do we route messages? Can we send the same message to multiple peers? Can we send replies back to an original requester?*"
    
- "*How do we write an API for another language? Do we re-implement a wire-level protocol or do we repackage a library? If the former, how can we guarantee efficient and stable stacks? If the latter, how can we guarantee interoperability?*"
    
- "*How do we represent data so that it can be read between different architectures? Do we enforce a particular encoding for data types? How far is this the job of the messaging system rather than a higher layer?*"
    
- "*How do we handle network errors? Do we wait and retry, ignore them silently, or abort?*"
### Redirected Questions
- Can RAW and COOKED sockets be created? Are they implemented as two different "basic" sockets? Or, a single "basic" socket which has the behavior dictated by a "cooked" flag defined at creation time.
	- *This question is to be part of the discussion on "RAW and COOKED sockets" which can be part of the sockets design evolution.*
- How does a reader thread know which socket should receive the receipt?
	- *This question should be answered in the section of the Delivery Receipts.*

## Components
### Node
- With the core idea being to use the Exon library as transport, creating an instance of the Exon library for each middleware socket did not make sense. For each instance of the Exon library, 1 UDP socket and 2 threads are created. Creating 2 threads for each socket is too excessive, when the same 2 threads can surely handle the computational requirements for multiple sockets. One UDP socket is also enough to support multiple sockets since the messages are not ordered which means head-of-line (HOL) blocking would not be a problem, i.e., the middleware sockets traffic would interfere/hinder each other. With that said, this middleware would follow an approach different from ZeroMQ, nanomsg and NNG which are the most similar. Those messaging middleware solutions follow an approach where each middleware socket has its own transport layer socket associated. 
### Link
- The establishment of links is required due to compatibility and presence reasons. Sending a message using a certain recipient ID is not enough because we could be sending it to an incompatible socket or be sending a message to a socket that does not yet exist which means the message would be discarded defying the principle of exactly-once delivery.