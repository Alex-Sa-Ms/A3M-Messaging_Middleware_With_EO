**AKA System Overview***
# Structure

1. System Overview
2. Socket
3. Link
4. Discovery Service
5. Polling Mechanism
	- Mention why it is required and where it is employed.
	- Mention attempts to create a polling mechanism. Refer the need for callbacks to create a versatile mechanism with different kinds of wake-ups (exclusive, non-exclusive, etc).
		- Actually, think a bit more about this, and check if it is possible without callbacks.
	- Explain the Linux Kernel implementation and how it was adapted. Refer that it is a simplified version without the nesting feature.
	- Refer the rationale behind not doing a workaround using already existing polling implementations which use the Linux kernel polling mechanisms.
		- They require system calls which mean user-kernel context switches, which are expensive.
	    - Since the sockets are all on user-level, creating workarounds that access the kernel level would not be a good solution from an efficiency point of view.
	- Mention the adaptations:
		- Peeking feature and why it was implemented.
	    - Fair wake-up call (and how it should have actually been implemented. For instance, implementing a different wake up mode called Fair-Exclusive instead of using exclusive wake ups.)
6. ...

## Writing Guidelines

- Make brief introduction about the purpose of this section.
- Provide an overview of the system
    - Enumerate the entities and components while briefly describing them to provide context of their functionality.
    - Describe the relationships between the entities/components.
    - Elaborate on entities/components that deserve to be elaborated. For instance, their methods, attributes, possible optimizations and new features, rationale behind their architecture, etc.
    - Include domain model diagram to help
- Mention the rationale behind each entity/component.
	- For example, the establishment of links is required due to compatibility and presence reasons. Sending a message using a certain recipient ID is not enough because we could be sending it to an incompatible socket or be sending a message to a socket that does not yet exist which means the message would be discarded defying the principle of exactly-once delivery.
- Mention design decisions

## Entities
- [x] Node 
- [x] Node Identifier
- [x] Socket
- [x] Socket Identifier
- [x] Link
- [ ] Link Socket
- [ ] Protocol
- [ ] Poller
- [ ] Message Dispatcher
- [ ] Message Management System
- [ ] Retry linking event
- [ ] Message Processor
- [ ] Link Manager
- [ ] Socket Manager
- [ ] Wait queue
- [ ] Flow control mechanism
- [ ] Specialized sockets
- [ ] Options (option handlers)
- [ ] Payload
- [ ] Discovery Service (Associations between transport addresses and node identifier)

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
- Reference: https://zguide.zeromq.org/docs/chapter1/#Why-We-Needed-ZeroMQ

**The answer to these questions should be put in their respective sections.**


- *"How do we handle I/O? Does our application block, or do we handle I/O in the background? This is a key design decision. Blocking I/O creates architectures that do not scale well. But background I/O can be very hard to do right."*
	- Elaborate answer in **[[Concurrency and Scalability Design#How to handle I/O]]**
	- **(A proper answer to this should probably be put on the Concurrency and Scalability Design, while if this questions are put somewhere else before that, the answer should be brief, like saying blocking I/O is required due to exactly-once delivery semantics.)**
	- The main requirement of the messaging middleware is to provide Exactly-Once delivery guarantee. Providing this guarantee implies messages cannot be discarded until the delivery is confirmed. However, not discarding messages means messages are accumulated, which can lead to resource exhaustion, with memory being the primary resource but also computational as messages may need to be retransmitted. With that in mind, the middleware was designed to provide a balance between blocking and non-blocking behaviors in regards to I/O operations. Much of I/O related work is done by background threads, with the blocking behavior (of the application threads) depending on the specific operation being performed (send or receive), the semantics of the socket being used and the flow control mechanism.
	- For receive operations, the middleware uses background threads to manage the incoming messages and ensure that messages are not lost. These background threads queue the received messages and validate them before delivering them to the appropriate socket. This queuing mechanism ensures that messages are not discarded unless they fail validation (e.g., a message intended for a non-existent socket). 
	- The key receive operation behaviors are:
		1. **Blocking Receive:** The client thread will block and wait for a message to be available.
		2. **Blocking Receive with Timeout:** The client thread blocks for a specified time waiting for a message. If no message is received within the timeout period, the operation returns, allowing the client to handle the situation without indefinitely blocking.
		3. **Non-Blocking Receive:** This allows the client thread to immediately regain control, regardless of whether or not a message is available.
	- In all cases, messages are never discarded by the background threads unless they fail a pre-delivery validation. This validation ensures that the integrity of message delivery is maintained, and only valid messages are passed to the application.
	- Send operations are also managed by background threads which ensure that messages are delivered according to the Exactly-Once delivery guarantee. The application thread does not directly handle the sending of messages; instead, it submits messages to the background threads. The submission of messages is managed by a flow control mechanism which dictates whether or not the operation is blocking. The purpose of the flow control mechanism is to prevent the sender from overwhelming the receiver but also to prevent resource exhaustion.
	- Similarly to the receive operations, there are 3 solutions available: 
		1. **Blocking Send:** When the client thread calls the send operation, it will block until the middleware allows it to send the message. This occurs when the flow control mechanism grants permission to send. The message is then submitted, resulting in the message being handed off to the background threads for actual transmission.
		2. **Blocking Send with Timeout:** This variant of the blocking send allows the client thread to wait for a permission to send, but only for a specified amount of time. If the timeout expires before the permission is granted, the middleware returns control to the client, which can then handle the situation (e.g., retrying the send operation or logging the failure).
		3. **Non-Blocking Send:** In this case, the client thread attempts to send the message, but the operation returns immediately regardless of whether the message is actually submitted.
	- When the submission of a message fails, the responsibility over the message is returned to the application.

- "*How do we handle dynamic components, i.e., pieces that go away temporarily? Do we formally split components into “clients” and “servers” and mandate that servers cannot disappear? What then if we want to connect servers to servers? Do we try to reconnect every few seconds?*"
    
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