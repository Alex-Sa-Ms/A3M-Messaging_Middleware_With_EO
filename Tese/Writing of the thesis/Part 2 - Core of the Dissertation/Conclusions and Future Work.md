***Writing Guidelines:***
- *Summarize the key findings of your dissertation, restating how your work addresses the challenges and goals outlined in Part 1.*
- *Discuss limitations of your current implementation or areas where improvements could be made.*
- *Identify opportunities for future work, such as enhancements in scalability, support for additional protocols, or integration with other systems.*
	- *Multipart messages as an integrated format? Pros: Easier to process messages; Cons: Less versatile as a multipart message format can be employed only on the payload.*
	- *Include a way to easily integrate encryption and authentication mechanisms.*
		- *The three-way handshake may be of help.*
- *Adicionar ideias que não foram implementadas, mas possivelmente pensar um pouco nelas e como poderiam ser implementadas.*
	- *Prioritized Load Balancing*
		- *Basicamente poderia ser feito através da implementação de um socket novo.*
# Conclusions


---
# Future Work
## Sockets
### Contexts and Streams
***Notes:***
- **Idea:** Multiplexing different conversations in the same socket.
	- AMQP links enable different conversations to be held under the same socket. ***(Check the following fact)*** AMQP links are similar to the sockets of our middleware, the difference lies in the sockets having a specific behavior which dictates how the communication happens. For instance, in a REQ-REP pattern, the REQ socket can only send one request at a time. 
	-  Some NNG sockets support creation of contexts. We can think of this as a single socket having multiple processing units that can work at the same time independently from each other. Taking the REQ-REP as an example, a REQ socket could have multiple contexts which enables it to send multiple independent requests at a time instead of being limited to one context which only allows a second request to be sent after the reply to the first request arrives. While creating contexts can be useful for concurrency, their identification is only used locally to identify a context that can process a message. Messages do not carry information regarding the context that sent it or the context it should be delivered to. This means that creating different conversations over the same NNG socket requires the sending application to attach a conversation identifier to the messages and the receiving application to extract such conversation identifier so that it can understand which conversation the message is related to.  
	- Regarding different conversations like the AMQP links, our middleware enables the creation of different conversations by creating multiple sockets. Since these are application sockets and not actually network sockets like UDP and TCP sockets, their creation is much cheaper. Still, linking each of the sockets has an overhead, so if we want to create 3 independent conversations, 3 linking overheads are required. This seems like an acceptable cost if the communication requirements actually require independent sockets, meaning, the communication cannot be multiplexed under the same socket using conversation identifiers.
	- Regarding the NNG contexts, our middleware equivalent solution could probably be the creation of additional sockets, one for each concurrent contexts required, although it may not be the ideal solution due to the linking overhead imposed. If a concurrent version of the pattern is available it should be used instead. For instance, using DEALER sockets instead of REQ sockets when sending multiple independent requests is desired. This brings the point that if the developer desires, if a concurrent version of the pattern is not available, the developer may attempt to create its own socket(s) to meet the requirements. 
	- Summarizing, the creation multiple independent conversations over the same application socket is a potential problem to be better explored in the future. However, as its usefulness was deemed doubtful, its exploration was postponed.
		- Reasons that also led to postponing the exploration of the idea:
			- 2 identifiers are already used to identify an application socket. By adding the possibility to create independent conversations (streams), 3 identifiers would be required for communication.
			- How would the third identifier be generated?
			- How to discover the stream identifier to be use for the destination?
			- The idea is interesting, but if conversations with well-defined identifiers are required, the identifier could simply be attached to the message and sent over the socket. Upon reception, the identifier of the conversation is extracted and used to identify what conversation the message should be delivered to for processing. 
### RAW and COOKED sockets
- *Explain what these are and their purpose (I think their purpose is to facilitate the creation of dispatchers)*
- *Although the middleware was built with these two types of sockets in mind, creating a RAW socket from a COOKED socket should be fairly easy using class hierarchy. **(Should test this to make sure)** By making a RAW socket extend the COOKED socket, we can override any specialized behavior from the COOKED socket that is not desired, making the message pass, just like it arrived, to the incoming queue, making it ready to be receive through the sockets API.*
- *Another reason behind not implementing this as a generic feature is due to the possibility of some sockets semantics not being compatible with such feature. Simply dispatching a received message forward may not make sense.*
## Transport abstraction

***Writing Guidelines:***
- *Check notes on "transport abstraction" and make clear that the solution thought was devised after the prototype.*
- *This is because on the Exon Adaptation chapter we should argue that I was inspired to implement the discovery service as near as possible to the transport layer by Martin Sustrik.*
    - *However, initially, I did think on doing transport abstraction but since it was not the core of the thesis, me and my Professor reached the conclusion that it wasn't an essential feature so it could be postponed for a future iteration of the prototype.*
- *If the notes do not do that already, conclude the "Transport Abstraction" section with a brief summary regarding the modifications that are required.*

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
## Presence Service 
***Writing Guidelines:***
- *Upgrade of the discovery service which can detect when peers change state and asynchronously inform the interested parties about such changes. Benefits:*
	1. *Can help reduce resource usage related to a peer that is offline. For instance to prevent transmitting to a node that is perceived as offline.*
    2. *Prevent busy querying of the presence service.*
## Security
