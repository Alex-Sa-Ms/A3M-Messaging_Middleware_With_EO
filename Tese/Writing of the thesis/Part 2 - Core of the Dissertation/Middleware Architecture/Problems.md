Designing a middleware is not an easy task. It is not as simple as selecting features and implementing them. One must carefully select features, being aware of compatibility issues between them. Most of the time, trade-offs must happen so that a middleware can surface, otherwise its an endless pit of problems. This section portrays several problems considered when designing the middleware. The problems ahead may have possible solutions which could be implemented in the future but were not defined as essential for this iteration of the middleware. 
# Transport Layer
## Transport abstraction
### Rationale

To enhance flexibility and adaptability, a more complete version of the middleware could allow the integration of different transport protocols. This abstraction would enable developers to select and integrate transport protocols that best fit their specific requirements, potentially offering benefits such as improved efficiency, congestion control, and enhanced data integrity. 

However, it is important to note that transport abstraction was not implemented in the current prototype. Instead, the middleware uses the Exon transport protocol library for communication, as it effectively ensures exactly-once delivery semantics, which is the primary focus of this work.
### Challenges
#### Exactly-Once Guarantee
##### Challenges in Transport Abstraction
Enabling transport abstraction introduces several challenges, particularly with respect to maintaining the middleware's exactly-once delivery guarantees. In the context of designing a more complete middleware, key questions to consider include:
- What characteristics must a transport protocol possess to be compatible with the middleware?
- How can the middleware ensure exactly-once delivery if the chosen transport protocol does not inherently provide this guarantee?
- Can both connection-based and connection-less transport protocols be supported?
- How should the middleware handle fatal disconnections in connection-based protocols?
- Should multiple transport protocols be simultaneously supported within a single middleware instance?
##### Proposed solutions

Although not implemented in the prototype, a theoretical discussion on solutions for supporting transport abstraction is presented below.

Having the 2 characteristics in mind, there are 4 combinations that need to be explored:
1. Connection-based w/ exactly-once delivery
2. Connection-based w/out exactly-once delivery
3. Connection-less w/ exactly-once delivery
4. Connection-less w/out exactly-once delivery

**Supporting Connection-based Transport Protocols**
- Supporting a connection-based would require a dedicated connection management layer to:
	- Initiate and manage connections (e.g., through a `connect()`-like mechanism).
	- Listen for and accept incoming connections.
	- Maintain mappings between node identifiers and transport endpoints.
	- Detect and recover from broken connections, potentially using heartbeats or network interface monitoring.
- Requires an adapted messaging layer that:
	- Listens to every endpoint and retrieve incoming messages.
	- For each message being sent, fetches the endpoint associated with destination node identifier from the connection management layer and uses it to dispatch the message.

**Supporting Connection-less Transport Protocols**
- Does not require a dedicated connection management layer as there is only one endpoint, but does require a discovery service which maps node identifiers to the respective transport addresses;
- Requires an adapted messaging layer that:
	- For each message being sent, fetches the transport address associated with destination node identifier from a discovery service and uses it to dispatch the message.

**Supporting Transport Protocols without Exactly-Once Delivery**
- Regardless of whether the transport protocol is connection-less or connection-based, when the protocol lacks the exactly-once delivery feature, then the middleware is required to provide such guarantee. This can be achieved through the employment of a messaging protocol that ensures exactly-once delivery over the chosen transport protocol. Exon is an example of such messaging protocol which runs over the UDP transport protocol. Hence, a possible solution could be an adaptation of the Exon library that can be paired with any transport protocol. To do so, the Exon library would associate the exactly-once state using node identifiers and would require a layer responsible by routing/transportation to be attached which must use the node identifier associated with each message to route them appropriately.
- The difference between the approach for connection-less transport and connection-based transports lies on connection-based transports requiring an additional layer responsible for managing the connections.

**Supporting Transport Protocols with Exactly-Once Delivery**
- **Connection-less transport protocol with exactly-once delivery**:
	- Connection-less transport protocols which ensure exactly-once delivery do not require any additional layers. Messages can be directly exchanged between the *Messaging Layer* and the *Transport Layer*, with the transport layer being obviously responsible for routing messages appropriately using the destination node identifier paired with each message.
- **Connection-based transport protocol with exactly-once delivery**:
	- This protocols inserted in this category can be divided into two groups: 1) the ones that can recover from connection failures, and 2) the ones which cannot. The ones in the second group most likely ensure exactly-once delivery within the connection, however, when such connection is broken, exactly-once delivery is not longer assured (e.g. TCP).
	- The protocols in the first group do not have any additional requirements, since the connection management layer upon reestablishing the connection should enable the ongoing exactly-once conversation to continue without any issues.
	- The protocols in the second group, on the other hand, require a layer that ensures exactly-once and is not connection dependent. Once again, employing the Exon algorithm is a possible solution with the adapted messaging layer being connected to Exon making it oblivious to the connection endpoints with each node.


<span style="color:red">**Conclude here while adding some diagrams (sequence diagrams?) for each of the 4 types of transport protocols**</span>

The diagrams below are simplifications. The exactly-once layer, simulated using the Exon algorithm, does not show most of the algorithm required to ensure exactly-once delivery, however, the idea is that the exactly-once layer uses the next *sending handler* to dispatch specific messages of the exactly-once delivery protocol, be it REQSLOTS, SLOTS, TOKEN (wraps the middleware message) or ACK messages. When the exactly-once layer message arrives at another node, that node is assumed to contain an *Exactly-Once Layer* that can understand and process the received message. If the message in question is a TOKEN message, then it is unwrapped, unveiling a middleware message which is then passed to the next *receiving handler*, which in this case is the messaging layer which will deliver the message to the respective destination socket. 

- **Connection-based transport protocol without exactly-once delivery;**
- **Connection-based transport protocol with exactly-once delivery only within connection:**
	- **Sending:**
		- Messaging layer -> Exactly-Once layer -> Connection/Routing layer -> Transport layer
		![[Connection-Based without EO - Send operation.png]]
	- **Receiving:**
		- Transport layer -> Connection/Routing layer -> Exactly-Once layer -> Messaging layer
		![[Connection-Based without EO - Receive operation.png]]
- **Connection-based transport protocol with exactly-once delivery:**
	- Corresponds to the solution for connection-based without (global) exactly-once delivery guarantee but without the exactly-once layer in the middle of both the sending and receiving processes.
	- **Sending:**
		- Messaging layer -> Connection/Routing layer -> Transport layer
		![[Connection-Based with EO - Receive operation.png]]
	- **Receiving:**
		- Transport layer -> Connection/Routing layer -> Messaging layer
		![[Connection-Based with EO - Send operation.png]]
- **Connection-less transport protocol without exactly-once delivery:**
	- For connection-less transport protocols, a Connection Management layer is not required, instead a transport layer is needed to consult a discovery service to identify the destination address required to dispatch the message and add/retrieve routing information (the node identifiers) so that mobility scenarios can be supported.
	- **Sending:**
		- Messaging layer -> Exactly-Once layer -> Transport layer
		![[Connection-less without EO - Send operation.png]]
	- **Receiving:**
		- Transport layer -> Exactly-Once layer -> Messaging layer
		![[Connection-less without EO - Receive operation.png.png]]
- **Connection-less transport protocol with exactly-once delivery**
	- **Sending:**
		- Messaging layer -> Transport layer
		![[Connection-less with EO - Send operation.png]]
	- **Receiving:**
		- Transport layer -> Messaging layer
		![[Connection-less with EO - Receive operation.png.png]]

- **Middleware Instance Design**

To maintain simplicity, a single middleware instance should support only one transport protocol. If multiple transport protocols are needed, developers could instantiate separate middleware instances, each configured with the desired transport protocol.

- **Conclusion**

The transport abstraction was deemed non-essential for the prototype, however, the above discussion outlines potential directions for future development of a more versatile middleware. The current prototype focuses on delivering communication under exactly-once semantics using an adaptation of the Exon transport protocol library which directly contacts a discovery service for routing purposes, therefore simplifying the design while ensuring the primary goal of reliability is met.
#### Mobility

- **Problem description:**
	- Mobility must be made compatible with every transport protocol that could be integrated.
- **Solution:**
	- The middleware's messaging layer only talks using node identifiers, hence, a layer that converts the node identifier into an endpoint (for connection-based transport protocols) or a transport address (for connection-less transport protocols) is required.
	- Finding a node's transport address using its node identifier is a functionality required by both connection-based and connection-less transport protocols. A connection-based protocol requires it to determine the transport addresses with which connections must be established so that messages can then be exchanged. While connection-less transport protocols require the transport addresses for routing. Such conversion should be done through a discovery service.
	- For connection-based transport protocols:
		- Static[^1] nodes of the topology that change to a new network should inform the discovery service of the change in address, so that it can notify any interested nodes attempting to initiate a connection. The interested nodes would then be able to connect in a following connection retry attempt.
		- For ephemeral nodes that change to a new network: upon discovering the connection is inactive[^2]/broken a new attempt of connection could be performed which would then replace the current connection with the node in question.
	- For connection-less transport protocols:
		- When a node is detected to be talking from a different transport address, such scenario can be assumed to be a consequence of mobility, and therefore the association between the node identifier and the transport address can be updated locally.

[^1] Static in the sense that they are supposed to be present through the lifetime of the distributed system. The static should not be interpreted as not allowing the node to move between different networks.
[^2] Not sure if moving to a new network breaks the sockets. If the transition does not result in an exception through which the unexpected closure of the connection could be detected, then having some kind of mechanism that can detect such event should be employed. Examples: 1) heartbeat mechanism; or 2) explicit test of the availability of the network interface used to bind the socket.
#### Socket - Transport incompatibility and multiple endpoints
<span style="color:red">**Write this**</span>
- **Problem description:**
	- Sockets made with specific transport characteristics in mind may not present the desired behavior when paired with other transport protocols that were not taken into consideration when designing the socket, so, allowing sockets to be paired with the appropriate transport protocols can be a desired feature. An example, that while not perfect can transmit the idea, is the development of a socket having in mind a transport protocol that ensures exactly-once delivery. Pairing such socket with a transport protocol that does not provide such guarantee would surely present unwanted behavior.
- **Solution:**
	- Assuming each socket is documented appropriately and specifies the assumptions made when developing it, if a developer attempts to use a transport protocol that does not meet such assumptions then it is the developer's fault if undefined behavior occurs.
	- One could argue that enabling different sockets of the same middleware instance to be bound to different transport protocols is a desirable feature, however, such would go against the main goal of the middleware which is usability. Enabling such functionality would make configuring the middleware a more complex task, in addition to making the development of middleware itself more complex too. So, if a "physical" node requires multiple transport protocols, a middleware instance for each of the desired transport protocols could be created to meet those requirements. In theory, the middleware instance can have the same identifier as the different transport protocols cannot interfere with each other, however, the developer must be aware that each discovery service must only have transport addresses that match the underlying transport address that will be used by the middleware instances of the topology described by the service.
---
# Sockets
A characteristic that aligns well with the exactly-once delivery guarantee is *usability*. Usability is therefore one of the main goals of the middleware. With that said, having a socket-based API was deemed a good option to meet the usability requirement due to the sockets' familiarity to developers. 
## Requirements
- **Extensibility**: Have a generic framework that:
	1. Defines the core behavior of sockets, i.e., all the basic functionality shared across all kinds of sockets regardless of their type which are essential to establish communication;
	2. Enables different (custom/specialized) sockets to be built;
	3. Prevents specialized sockets from overriding/disrupting the core behavior - the rationale behind this is to have a safety measure that prevents against undefined/unwanted behavior.
	4. Allows specialized sockets to have a custom interface (API).


***(To add the content of this section to the thesis, a good idea may be to have a subsection "Requirements" containing all requirements, and then another subsection to specify how those requirements were implemented, their implications when paired with other requirements, etc.)***
## Design to enable socket extensibility
With one of the main goals of the thesis being to explore the design of a messaging middleware under exactly-once delivery semantics, having a middleware that enables extensibility in terms of messaging protocols facilitates exploring and implementing different messaging patterns having exactly-once delivery as a guarantee. Several design iterations were required to achieve an architecture that satisfied the majority of the requirements while balancing the trade-offs in certain areas.
### Core functionality
The design of the architecture started with defining the basic functionalities of the sockets. The  purpose of these core functionalities is to set a foundation for the creation of sockets, which exempts the need of all specialized sockets to re-implement the same functionalities and ensures a controlled interaction between sockets through a set of rules which prevent the violation of each sockets restrictions (such as messaging protocol compatibility and data flow control).

The basic functionalities are below:
- Create links (linking) with other sockets that talk a compatible messaging protocol;
- Destroy active links (unlinking) with other sockets.
- Send messages to links.
- Receive messages from links.
- Control the flow of messages passing through the links. 

\<***Maybe try to elaborate a bit on the reasoning behind each basic functionality, mention the sections that follow where the topics are elaborated and show the methods signatures.***\>

The core methods mentioned here cannot be overriden to prevent developers from tempering with the middleware's core behavior. While they cannot be overriden, it does not imply that in the future the core behavior cannot be extended. For instance, an idea of an extension of the linking protocol is to enable the linking messages to carry custom metadata which could then be used to extend the compatibility verification the sockets need to undergo before establishing a link. 
### Custom functionality

To facilitate and hasten the creation of specialized sockets, the middleware was designed to prevent sockets from needing to be implemented from scratch. To enable this, an abstract `Socket` class was created which serves as a template. This class contains the basic functionality methods (*not overridable*), several methods that must be implemented (abstract methods) and some others that are *overridable* which enhance customization. Socket specialization hierarchy needs not to stop at the first subclass of the `Socket` class. Each socket specialization may have their own subclasses, if the behavior is closely related. In the "Specialized Sockets" chapter, we will talk about the different implemented sockets and how a good amount of the implemented sockets are a specialization of another specialized socket called `ConfigurableSocket`.

The specialized sockets are the direct instances handled by the client of the middleware. By passing these instances directly to the client, the client can have access to an interface that is specific to the socket in question, i.e., a custom interface/API.

- ***Specify the methods that need to implemented by each custom socket while briefly describing them. If a more in-depth explanation should be given, then it should be done in a specific section***


- **Refer somewhere the need to carefully set the visibility, "final" primitive, etc.**


### Design decisions

(***This theme also makes sense to be referred in the "Concurrency/Threading model" section, so how to structure the thesis to talk about this? Maybe not include in none of these sections, and write about this kind of problems in a "Design Decisions" section or "Future Work" section***)
#### Safety

One of the conclusions I withdrew from designing this messaging middleware is that extensibility and safety are a trade-off, i.e. making a middleware more versatile/flexible when it comes to extending its functionality demands relaxing further in regard to safety measures. I reached such conclusion after a long time trying to design a middleware as flexible as possible in the extensibility department while trying to safeguard against all possible undesirable erroneous scenarios that could be injected by a developer extending the middleware's functionality. The middleware is a library, so, while safeguarding against all user errors would theoretically be the ideal solution to prevent unwanted/undefined behavior, such action can only be achieved by prohibiting the addition of new functionality, i.e., inhibiting the extension of middleware functionality. On the other hand, providing an extensible architecture means forsaking safety and providing proper documentation so that developers that intend to extend the middleware behavior can have proper guidelines on how to do so without introducing unwanted/undefined behavior. 

Having all that said, the middleware was design to enable some extensibility, mainly in the creation of specialized sockets, while also implementing some safety measures to minimize unwanted consequences that may arise when extending the middleware's behavior and to safeguard against some malicious attacks. ***\[Could enumerate some or all safety measures: 1) Not enabling basic methods to be overridable, therefore preventing against operations the core of the middleware must not allow; 2) Modularity / Visibility of methods, attributes, etc; 3) ... \]***

- For instance, each specialized socket talks a specific message protocol, this means it must have a custom implementation of the method that interprets and processes the received messages. By allowing custom implementation of these methods, a risk of the implementation not following the recommendations surfaces. Not following the recommendations can have adverse effects, such as slowing or stalling the middleware or even resulting in undefined behavior. While there are risks associated, making the middleware less "safe" against undefined/unwanted, not allowing custom implementation of such methods means not enabling the middleware's functionality to be extended.
	- Summarizing, extensibility and safety are a trade-off. For some parts of the middleware, extensibility may not be allowed to safeguard against undefined/unwanted behavior, while in other parts, extensibility is allowed while acknowledging the potential consequences that such capability may introduce.
	- ***Conversation with chatGPT to gather conclusions: (safeguard against PC deciding to die on its own)***
###### ChatGPT conversation
```
I'm struggling to understand if I failed in the design of my messaging middleware. I wanted it to be extensible in the sense that it allowed custom sockets to be created. Each custom socket must extend a "basic" socket that contains generic behavior that is shared across all sockets. Since each socket can follow different messaging protocols, with each having their own messages with different formats, the custom sockets must implement a method that interprets and processes each received message. The middleware has a single thread, that is responsible for reading from the sockets and processing such messages using the method referred previously. As you can understand that method, if not implemented properly, can slow or even stall the middleware performance. While fully aware of such possibility, I deemed it as a necessary risk for extensibility, and left the responsibility to avoid such scenario for the developer that creates the custom socket that he intends to deploy. What do you think about that?

###### ChatGPT said:

ChatGPT

Your design concept for the messaging middleware, with a single thread reading from sockets and delegating message interpretation and processing to custom socket implementations, is definitely a reasonable approach for creating an extensible system. However, there are a few important trade-offs and potential risks that you need to consider, especially with regard to performance, reliability, and maintainability. Let me break down the situation and some areas where you might want to reassess or refine your design.

### 1. **Performance Risk Due to Custom Message Processing**:

You've identified that the custom `processMessage()` implementation by the developer can potentially slow down or stall the entire middleware. This is a significant risk because:

- **Single Thread Limitation**: Since you're using a single thread to handle all socket reads and message processing, the time spent in `processMessage()` could block subsequent message reads. This would lead to a bottleneck where the middleware could become unresponsive, or fail to scale under higher load.
- **Custom Implementations**: Given that custom sockets must implement `processMessage()`, the quality and performance of these implementations will vary. While developers may be aware of the potential performance impact, they might still make mistakes or unintentionally introduce inefficiencies in their custom code.

**Recommendation**: If you want to maintain extensibility, one way to mitigate this risk is by enforcing a performance contract on custom `processMessage()` implementations. For instance:

- You could define strict time constraints on how long `processMessage()` should take (e.g., no more than a certain number of milliseconds). If a message processing takes too long, you could log a warning or even fail the message.
- Alternatively, you could allow developers to specify processing time limits for their implementations. This way, the middleware can enforce them.
- You could also allow a separate worker pool (using threads or asynchronous tasks) for handling message processing, keeping the main thread focused on socket reading.

### 2. **Error Handling and Resilience**:

When the message processing is delegated to the custom implementation, there's a chance that poor error handling in a custom `processMessage()` method could cause the entire system to fail or become unreliable. For example, if the developer forgets to handle certain exceptions or edge cases, this might break the middleware's ability to continue processing messages.

**Recommendation**: Provide strong mechanisms for error handling and isolation:

- Use try-catch blocks around the `processMessage()` call to prevent any exceptions from propagating up and crashing the system.
- Consider introducing an isolation mechanism where failures in custom implementations don’t affect the entire middleware. This could be achieved using separate threads or processes for message handling, or sandboxing the custom logic to ensure that failures in one implementation don’t bring down the whole system.

### 3. **Scalability Concerns**:

While the design is flexible and extensible, single-threaded operation may become a bottleneck as the system scales. The single thread is responsible for both reading from all sockets and processing messages, which could result in poor scalability and limited throughput, especially if message processing is resource-intensive or if there are many sockets to handle.

**Recommendation**: Consider adding scalability features to your design:

- **Multi-threading or Asynchronous Processing**: Move to a multi-threaded or event-driven (asynchronous) model, where each socket’s message processing can be done independently, or at least allow the socket read operations to be asynchronous.
- **Worker Pools**: As mentioned, introduce a worker pool for message processing, which could be dynamically scaled depending on the workload.

### 4. **Developer Responsibility**:

You've intentionally placed the responsibility for performance and message processing in the hands of the developers creating custom sockets. This is a trade-off between flexibility and safety. While this allows for great extensibility, it also places a heavy burden on developers to get things right, which might not always happen.

**Recommendation**: Provide clear guidelines, documentation, and tools for developers:

- Document performance expectations and best practices for implementing `processMessage()`.
- Provide testing tools or benchmarks that developers can use to verify that their custom implementations won’t slow down or stall the middleware.
- Include a robust logging and monitoring framework to help detect performance issues early in development and production.

### 5. **Extensibility and Maintainability**:

The design seems to favor extensibility over strict constraints, which is fine as long as you make sure it's easy for developers to follow the performance rules. However, it’s important to think about long-term maintenance and usability. If custom socket developers are given too much freedom, they might create unmaintainable code or code that’s incompatible with future changes to the middleware.

**Recommendation**: To strike a balance between extensibility and maintainability:

- Define clear interfaces and abstract classes for the custom sockets, ensuring consistency in how they interact with the middleware.
- Provide an abstraction layer for common socket behaviors so that developers only need to focus on the specific protocol-related aspects, rather than worrying about the basic socket mechanics.
- Consider introducing a set of useful libraries, templates, or examples to help developers implement new sockets efficiently and in a standardized way.

### Conclusion:

Your design certainly supports flexibility and extensibility, but it introduces potential risks in terms of performance and reliability. To address these risks, it would be wise to put additional safety measures in place, such as enforcing performance constraints, introducing multi-threading or worker pools, and providing good error handling and monitoring. This way, you can maintain the balance between extensibility and system stability.

##### You said:

Some comments: 1. The middleware was conceived under exactly-once delivery semantics, so, discarding messages without processing them first is not acceptable. While a limit could be imposed to the processing of each message, discarding the messages without processing them must not occur. 2. The processing made by each socket is assumed to not be something too complex or resource expensive, so having a single thread receive all messages and do such processing, should, in theory, be sufficient. It is assumed that a simple processing is done for each message, such as parsing the received message in a byte format to an object that facilitates the processing when the client performs `receive()` on the socket, or in the case of a control message, quickly processing such message and replying if necessary. If processing the message requires more resources, the developer could choose to implement the `processMessage()` as a method that simply delegates the processing of the message to another thread. The `processMessage()` may not perform costly processing of message, it could opt to simply queue the message, signal the socket as readable, and let a client thread poll the message and process it.

###### ChatGPT said:

ChatGPT

Thank you for the additional comments! They provide more clarity on your design goals and constraints. Based on this, here’s a more tailored analysis and suggestions:

### 1. **Exactly-Once Delivery Semantics**:

Since discarding messages without processing them first is unacceptable, this introduces a few additional considerations:

- **Message Processing Guarantees**: To meet the exactly-once delivery semantics, the system must ensure that every message is processed at least once. In other words, the middleware cannot skip over messages, even if the processing thread is temporarily busy or slowed down.
    
- **Handling Delays in Message Processing**: Given that each `processMessage()` could potentially be quick but might also opt for deferred processing (as in queuing the message for later), you'll need to design the system to guarantee that no messages are lost in the process. It’s essential that messages are always passed to a queue or buffer as soon as they are received. Any delays should not cause messages to be lost.
    

**Recommendation**:

- **Persistent Queuing**: Ensure that messages are always written to a persistent queue (or memory buffer) as soon as they are received, so they can be processed even if `processMessage()` defers them. This will help ensure no message is ever lost, even if there’s a temporary backlog in message handling.
- **Atomicity**: Ensure the atomicity of the `processMessage()` method—either the message is fully processed, or it's left in a state that can be retried without loss. If you're using a queue or buffer, consider implementing a way to track message states to avoid reprocessing messages or losing them due to failures.

### 2. **Single Thread Processing**:

Your assumption is that message processing is light and simple (e.g., converting a byte stream into an object or handling control messages), and that the single thread should be enough for handling this.

- **Message Processing Simplicity**: If the operations are simple and quick (e.g., parsing or minor transformations), it’s feasible that a single thread could handle this without significant performance degradation. However, even light operations could accumulate over time, leading to message backlog under high load, especially if there are many sockets.
    
- **Backlog Handling**: A single thread can eventually become a bottleneck if the system experiences high traffic. While you might assume that the processing is simple, a peak in traffic (e.g., many simultaneous incoming messages) could still cause delays.
    

**Recommendation**:

- **Non-blocking Operations**: Ensure that the reading of messages from sockets is non-blocking, and that each `processMessage()` is optimized for speed. Even if each message is processed quickly, you want to ensure the thread can move on to the next socket read as soon as possible.
- **Graceful Handling of Backlogs**: If you expect potential spikes in traffic, consider implementing an adaptive backlog handling mechanism, where incoming messages are immediately queued for processing, but the queue itself could be processed in batches or intervals, depending on load.

### 3. **Deferring Expensive Processing**:

Since you mentioned that if processing is resource-intensive, the developer can delegate work to another thread, this gives some flexibility in handling more complex message types. This approach seems reasonable to allow a balance between simplicity and extensibility.

- **Threading for Expensive Tasks**: Delegating expensive tasks (like message processing) to worker threads helps avoid blocking the main thread. However, you’ll need to ensure that the worker threads can handle the load efficiently and that the system doesn’t overwhelm itself with too many concurrent threads.
    
- **Thread Management**: Careful thread management is necessary here, especially to avoid creating too many threads. Threads can add overhead, so you'll need a way to manage the creation and destruction of threads efficiently (e.g., through a thread pool).
    

**Recommendation**:

- **Worker Threads or Thread Pools**: Implement a worker thread pool to handle expensive tasks. This will prevent the system from creating an unmanageable number of threads while allowing for the concurrent processing of messages that require more resources. The thread pool can scale dynamically based on the number of tasks in the queue, which will keep resource usage under control.
- **Non-blocking Queues**: To decouple the processing from the main thread, use non-blocking queues to pass messages to worker threads. The worker thread can then process the messages independently without affecting the throughput of the main loop that handles socket reads.

### 4. **Developer Flexibility and Responsibility**:

You’ve given developers the ability to implement `processMessage()` in a flexible way, including the option to defer processing to a separate thread. This flexibility is great for extensibility, but it also introduces the potential for inconsistency in how developers handle message processing, especially around performance optimization.

**Recommendation**:

- **Guidelines for Custom Sockets**: While you provide flexibility, ensure that developers follow best practices when designing custom `processMessage()` implementations. This includes:
    
    - Avoiding excessive blocking operations in the main thread.
    - Properly handling exceptions and ensuring that messages are processed reliably, even if they are deferred to worker threads.
    - Using non-blocking I/O or queuing mechanisms to avoid stalling the main thread.
- **Monitoring and Debugging Tools**: Provide built-in logging and debugging tools that help developers ensure that their custom message processing is efficient and adheres to the required performance constraints. For example, track message processing times, queue lengths, and worker thread utilization to help diagnose any issues that might arise in production.
    

### Conclusion:

Your approach for handling exactly-once delivery semantics, simple message processing, and developer flexibility seems solid. The key points are:

1. **Ensuring message persistence** so that no message is lost, especially under load.
2. **Avoiding performance bottlenecks** with strategies like non-blocking reads and thread pooling for expensive processing.
3. **Enabling developer extensibility** while providing clear guidelines and tools to avoid performance pitfalls.

If you implement mechanisms like non-blocking queues, worker thread pools, and clear developer guidance, you should be able to balance flexibility and performance, and maintain the integrity of the exactly-once delivery guarantee.
```
###### End of conversation
1. Modularity, visibility, etc
2. Socket Core behavior:
	- Create links (linking) with other sockets that talk a compatible messaging protocol;
	- Destroy active links (unlinking) with other sockets.
	- Send messages to links.
	- Receive messages from links.
	- Control the flow of messages passing through the links. 

## RAW and COOKED sockets
- description of what they are and what their purpose is
	- One of the purposes is to aid the creation of forwarders, right? 
		- Forwarders are intermediary devices that can help with system scaling. For instance, let's say we have a stream we wan't to feed to a great amount of clients. Instead of requiring all clients to connect to the transmitter, which can lead to the transmitter being overwhelmed, in addition to being a single point of failure, having multiple forwarders can help distribute clients among the different devices (the forwarders) which will receive the data from the transmitter and proceed to simply forwarding it, without performing the custom processing a COOKED socket requires. The processing would be done by the client socket which typically is a COOKED socket.
- how it could be implemented
- why it was not implemented
	- 
---
# Discovery Service
- Description
- How it is integrated
- Which component employs it
- Why it was implemented in the Exon layer
	- Use the article that says discovery services should be implemented in the transport layer and not in the middleware, so I decided to implemented as near as possible of the transport layer.
	- If transport abstraction is not desired, then it is not wrong to have implemented it and the Exon layer. With the teacher saying transport abstraction was not essential to the thesis, the decision to implement it at the Exon layer was made.
- Where I think it should be implemented in a future iteration
	- It should be implemented in the middleware so that it could be reused in conjunction with different transports. 
---
# Receipts

- Mention evolution of the idea, including why it was initially designed for, etc.
	1. Started as simple message id generated by Exon
		- Initial goals: 
			1. Efficiently identifying when a message was received by the destination
				- A receipt could be provided by sending an application-level acknowledgment message, however, that would be less efficient as it requires employing the exactly-once protocol two times, as opposed to one. 
			2. Flow control purposes
		- This meant every message would have a message id generated and associated even when the receipts were not required by the application.
	2. Message id could be provided by application
		- If a message id was not provided, then a receipt is assumed to not be required.
		- If a message id was provided, then a receipt (the given message id) would be emitted by Exon once the ACK message was received indicating the destination node had received the message.
	3. Then, evolved to enabling an arbitrary object to be associated with each message.
		- If a message id is desired, the object associated could be an application level generated message id.
		- The idea of not providing a receipt object, i.e. passing null as the receipt, meant a receipt was not required to be emitted. 
		- The main idea behind allowing objects to be associated is that it provides more flexibility and for some cases may prevent a concurrency contention point where custom objects would be mapped to each message id. Allowing custom objects to be associated, means that callback objects could be associated to facilitate the execution of tasks when a message was perceived as received, which in theory results in more efficient processing of the receipt events.

## Socket Message Receipts
- One of the motivators of said evolution is requiring a custom object that could contain the required information to deliver the receipt to the appropriate socket while also being able to include a user's custom object.
	- *Future Work feature*
- The custom receipt object would enable a receipt to be delivered to the socket without adding another contention point which would map a MsgId to the source socket.
---
# 
