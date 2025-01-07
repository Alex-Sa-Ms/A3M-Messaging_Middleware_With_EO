Designing a middleware is not an easy task. It is not as simple as selecting features and implementing them. One must carefully select features, being aware of compatibility issues between them. Most of the time, trade-offs must happen so that a middleware can surface, otherwise its an endless pit of problems. This section portrays several problems considered when designing the middleware. The problems ahead may have possible solutions which could be implemented in the future but were not defined as essential for this iteration of the middleware. 
# Transport Layer
## Transport Abstraction
*(Moved to "Conclusions and Future Work")*

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

The basic functionalities are:
- Create links (linking) with other sockets that talk a compatible messaging protocol;
- Destroy active links (unlinking) with other sockets.
- Send messages to links.
- Receive messages from links.
- Control the flow of data messages passing through the links. 

<span style="color:orange"> The reason of each basic functionality should be explained in there own sections, i.e., in the section where the method signatures are described. The reason behind the existence of links and a linking protocol should be explained in the links section.
</span>

The core methods mentioned here cannot be overriden to prevent developers from tempering with the middleware's core behavior. While they cannot be overriden, it does not imply that in the future the core behavior cannot be extended. For instance, an idea of an extension of the linking protocol is to enable the linking messages to carry custom metadata which could then be used to extend the compatibility verification the sockets undergo before establishing a link.
### Custom functionality

To facilitate and hasten the creation of specialized sockets, the middleware was designed to prevent sockets from needing to be implemented from scratch. To enable this, an abstract `Socket` class was created which serves as a template. This class contains the basic functionality methods (*not overridable*), several methods that must be implemented (abstract methods) and some others that are *overridable* which enhance customization. Socket specialization hierarchy needs not to stop at the first subclass of the `Socket` class. Each socket specialization may have their own subclasses, if the behavior is closely related. In the "Specialized Sockets" chapter, we will talk about the different implemented sockets and how a good amount of the implemented sockets are a specialization of another specialized socket called `ConfigurableSocket`.

The specialized sockets are the direct instances handled by the client of the middleware. By passing these instances directly to the client, the client can have access to an interface that is specific to the socket in question, i.e., a custom interface/API.

- ***Specify the methods that need to implemented by each custom socket while briefly describing them. If a more in-depth explanation should be given, then it should be done in a specific section***


- **Refer somewhere the need to carefully set the visibility, "final" primitive, etc.**

- Async IO framework could be a good addition for future work regarding usability purposes. While it is possible to use an external async IO framework when designing the sockets, having a framework that facilitates programming such tasks could be benefitial.

- **Refer somewhere the lifecycle of sockets.** 
	- Created -> Started -> Closed.
	- Creating and not starting immediately is allowed to enable the user to configure the socket before messages start flowing (in this case, be received by other sockets attempting to connect to this new socket)
### Design decisions

(***This theme also makes sense to be referred in the "Concurrency/Threading model" section, so how to structure the thesis to talk about this? Maybe not include in none of these sections, and write about this kind of problems in a "Design Decisions" section or "Future Work" section***)
#### Safety

During the design of this messaging middleware, I realized that extensibility and safety often conflict with each other. Making middleware more flexible and capable of being extended usually requires compromising on safety measures. This conclusion emerged after significant effort to create a middleware that was both highly extensible and resilient to errors introduced by developers extending its functionality.

Since the middleware is a library, ensuring complete safety against user errors would be the ideal solution to prevent unintended or undefined behavior. However, achieving this level of safety requires prohibiting any new functionality from being added, which effectively eliminates extensibility. Conversely, an extensible architecture inherently sacrifices some degree of safety. To address this trade-off, comprehensive documentation becomes essential, providing clear guidelines for developers to extend the middleware responsibly without introducing errors or undefined behaviors.

For a better understanding, let's consider an example. To provide extensibility namely in the creation of specialized sockets, each socket has a collection of methods that must be implemented which dictate the socket's specialized behavior. With each specialized socket employing a specific message protocol, one of those methods is responsible for interpreting and processing a received message. By allowing an "external" implementation of that method, which is executed by the middleware thread, a risk of such implementation not following the recommendations surfaces. Not following the recommendations (e.g. avoiding blocking operations) can have adverse effects, such as slowing or stalling the middleware or even resulting in undefined behavior. While there are risks are associated, which result in a middleware less "safe" against undefined/unwanted behavior, prohibiting custom implementations of such methods means not enabling the middleware functionality to be extended.
- Most of the methods can be used to introduce problems in the library functioning, while some only introduce problems related to the custom socket in question, others can affect the overall system. However, we assume the developer is not purposely trying to sabotage a library that he is going to use:
	- Lock-free algorithms should help mitigate some problems regarding deadlocks
	- Developer has access to the sockets and links lock, so it could introduce problems that shouldn't exist, however, we are assuming the developer does not intend to introduce them on purpose, and so he will test it thoroughly to ensure such problems do not occur
	- Socket lock is shared with linking mechanism and what more?

That being said, the middleware was designed with extensibility in mind, particularly for creating specialized sockets. It takes into account the potential risks associated with this flexibility, while also implementing safety measures to minimize any unintended consequences and protect against malicious attacks when extending its functionality.
 ***\[Could enumerate some or all safety measures: 1) Not enabling basic methods to be overridable, therefore preventing against operations the core of the middleware must not allow; 2) Modularity / Visibility of methods, attributes, etc; 3) ... \]***




---
1. Modularity, visibility, etc
2. Socket Core behavior:
	- Create links (linking) with other sockets that talk a compatible messaging protocol;
	- Destroy active links (unlinking) with other sockets.
	- Send messages to links.
	- Receive messages from links.
	- Control the flow of messages passing through the links. 
---


### Design recommendations
<h4 style="color:red">Write this section</h4>
- Talk about how to avoid violations of the concurrency and message processing assumptions
- Talk about the lock of the socket and what are the components it is shared with
- Talk about potential deadlocks that may arise
- Talk about the fact that there is only one reader thread
	- Avoid blocking operations
	- Think on how to enable the reader thread to process the messages as quickly as possible and proceed to processing another message, like using atomic operations
	- Since there is only one reader thread, there isn't concurrency when processing messages. So there are some parts of the middleware that could be optimized having in consideration that only one thread will have access to it. But acknowledge that if the processing system is modified in the future, then it would require a lot of modifications.
### Future work

- Maybe enable the creation of a pool of workers, with the amount of worker threads being user-defined just like ZeroMQ
- A single transport socket feeding all middleware sockets will always throttle the middleware, even when multiple workers have enough work to make concurrency viable.
- Pool of workers along with a batching service could be a good solution for throughput
	- Due to the thread-safe nature of the middleware, if a pool of workers is employed, it is possible that two workers attempt to deliver a message to the same socket, effectively hindering each other. To prevent such scenario, batching messages could be an alternative solution to enhance throughput.

## Threading model
- Talk about how difficult creating a middleware under thread-safe semantics is. 
	- Performance-wise
	- Deadlocks
	- etc
- For comparison, check the veracity of the following:
	- **ZeroMQ**: Uses a pool of threads (typically 1 by default, but can be configured for more).
	- **nanomsg**: Typically uses 1 thread per socket.
	- **NNG**: Uses 1 thread per socket by default, but also supports multi-threading with configuration for worker threads and thread pools.
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
