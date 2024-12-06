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
- **Connection-less transport protocol without exactly-once delivery**:
	- In addition to the requirements referred above to integrate a connection-less transport protocol, when the protocol lacks exactly-once delivery, the middleware is required to ensure exactly-once delivery guarantee. This can be achieved through the employment of a messaging protocol that ensures exactly-once delivery over the chosen transport protocol. Exon is an example of that which runs over UDP. Hence, a possible solution could be an adaptation of the Exon library to allow the attachment of different connection-less transport protocols.
- **Connection-based transport protocol with exactly-once delivery**:
	- <span style="color:red">**Write this**</span>

**Supporting Transport Protocols with Exactly-Once Delivery**
- **Connection-less transport protocol with exactly-once delivery**:
	- <span style="color:red">**Write this**</span>
- **Connection-based transport protocol with exactly-once delivery**:
	- This protocols inserted in this category can be divided into two groups: 1) the ones that can recover from connection failures, and 2) the ones which cannot. The ones in the second group most likely ensure exactly-once delivery within the connection, however, when such connection is broken, exactly-once delivery is not longer assured (e.g. TCP).
	- The protocols in the first group do not have any additional requirements, since the connection management layer upon reestablishing the connection should enable the ongoing exactly-once conversation to continue without any issues.
	- The protocols in the second group, on the other hand, require a layer that ensures exactly-once and is not connection dependent. Once again, employing the Exon algorithm is a possible solution with the adapted messaging layer being connected to Exon making it oblivious to the connection endpoints with each node.


<span style="color:red">**Conclude here while adding some diagrams (sequence diagrams?) for each of the 4 types of transport protocols**</span>

The diagrams below are simplifications. The exactly-once layer, simulated using the Exon algorithm, does not show most of the algorithm required to ensure exactly-once delivery, however, the idea is that the exactly-once layer uses the next *sending handler* to dispatch specific messages of the exactly-once delivery protocol, be it REQSLOTS, SLOTS, TOKEN (wraps the middleware message) or ACK messages. When the exactly-once layer message arrives at another node, that node is assumed to contain an Exactly-Once Layer that can understand and process the received message. If the message in question is a TOKEN message, then it is unwrapped, unveiling a middleware message which is then passed to the next *receiving handler*, which in this case is the messaging layer which will deliver the message to the respective destination socket. 

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

---
<span style="color:red">**Check what can be added above from the text below**</span>

**Supporting Transport Protocols with and without Exactly-Once Delivery**
- **Protocols with exactly-once delivery:** At first glance, these require only an integration layer to interface with the middleware, enabling message send/receive operations. Ahead is an explanation of why such layer is not the only requirement.
- **Protocols without exactly-once delivery:** An additional *exactly-once layer* would be necessary. This layer could employ a messaging protocol (e.g., an adaptation of the *Exon* library) to ensure exactly-once semantics over the chosen transport protocol.

**Supporting Connection-Based Protocols**
- A dedicated connection management layer would be required to:
	- Initiate and manage connections (e.g., through a `connect()`-like mechanism).
	- Listen for and accept incoming connections.
	- Maintain mappings between node identifiers and transport endpoints.
	- Detect and recover from broken connections, potentially using heartbeats or network interface monitoring.
- Even if the transport protocol guarantees exactly-once delivery within a connection (e.g., TCP), a middleware-level exactly-once layer would still be required to handle unexpected connection failures, ensuring message state recovery or retransmission.

**Supporting Connection-Less Protocols**
- Protocols guaranteeing exactly-once delivery require only a basic integration layer.
- For those lacking exactly-once delivery, an adapted *Exon* library could be used to achieve this, leveraging mechanisms such as transport endpoint switching.

\paragraph{Adaptation of the Exon Library}

The \textit{Exon} library could be adapted to provide exactly-once semantics for transport protocols that do not inherently support it. Key modifications could include:

\begin{itemize}
    \item Shifting from a discovery service to an endpoint service, enabling seamless support for connection-based and connection-less transports.
    \item Efficient endpoint retrieval and connection establishment where required.
    \item Custom adaptations based on the transport protocol type (connection-based vs. connection-less) to minimize unnecessary overhead.
\end{itemize}

\paragraph{Middleware Instance Design}

To maintain simplicity, a single middleware instance should support only one transport protocol. If multiple transport protocols are needed, developers could instantiate separate middleware instances, each configured with the desired transport protocol.

\subsubsection{Conclusion}

While the transport abstraction was deemed too complex and non-essential for the prototype, the above discussion outlines potential directions for future development of a more versatile middleware. The current prototype focuses on delivering communication under exactly-once semantics using the \textit{Exon} transport protocol library, which simplifies the design while ensuring the primary goal of reliability is met.



---

 <span style="color:red">**Check what can be added above from the text below**</span>

- **Solution:**
	- Having the 2 characteristics in mind, there are 4 combinations that need to be explored:
		 a. Connection-based w/ exactly-once delivery
		 b. Connection-based w/out exactly-once delivery
		 c. Connection-less w/ exactly-once delivery
		 d. Connection-less w/out exactly-once delivery
	- Supporting both protocols that ensure exactly-once delivery and those who do not should be possible. 
		- Transport protocols that natively support exactly-once delivery should only require an integration layer that enables sending/receiving messages by the middleware.
		- Transport protocols that do not feature exactly-once delivery need an additional layer over the integration layer that enables sending/receiving messages. This additional layer should make use of the messaging layer to employ a messaging protocol that ensures exactly-once delivery.
	- Supporting both connection-less and connection-based protocols should also be possible:
		- Integration of connection-based transport protocols substantially increase the complexity of the middleware:
			- Requires a layer responsible for connections to:
				- Enable connections to be initiated, analogous to connect();
				- Listen for connections;
				- Establish a relation between node identifiers and endpoints.
				- Retry connecting with a node when the associated connection is detected as broken.
				- Broken connection detection mechanisms may need to be designed. Maybe based on heartbeat messages or possibly through the verification of network interface used to bind the node.
			- Regardless of the connection-based transport featuring exactly-once delivery or not, a layer that ensures exactly-once delivery and supports multiple endpoints is required. 
				- For transport that do not feature exactly-once delivery, the exactly-once layer seems logic, but for those transports that do provide the guarantee it may seem strange. The reason behind such transports also needing this exactly-once layer is the fact that the transport may only support exactly-once delivery within the connection, like TCP, this means that if the connection breaks unexpectedly, exactly-once is not assured. While some transport protocols could potentially allow the state to be recovered, others do not, so, in order to avoid losing messages, wrapping these transport protocols with the adaptation of the Exon library could be the solution.
			- More resources, both computational and storage, would be required:
				- Destination endpoint needs to be fetched each time a message needs to be sent;
				- More objects and/or low-level sockets need to be created for each connection.
				- Managing and polling multiple endpoints is required.
		- Integration of connection-less transport protocols would be rather simple:
			- For those that ensure exactly-once delivery, then only a simple integration layer would be required.
			- For those that do not ensure exactly-once delivery, an adaptation of Exon that enables switching the transport endpoint is a simple solution, as the library currently uses UDP for the transport layer.
	- The exactly-once layer to support transport protocols who do not provide such guarantee can be provided through an adaptation of the Exon library.
		- The Exon library would stop having access to the discovery service, and instead have access to an endpoint service, which comprises the connection layer. This endpoint service would return the endpoint required for the sending/receiving operation and if the transport protocol in use is connection-based and a connection is not yet established with the node in question, such connection would be created.
	- A middleware instance supports only one transport protocol, since, using a transport protocol for each socket of the middleware defeats the purpose of usability. When different transport protocols are required, the developer should create a different middleware instance for each transport protocol.
	- Depending on whether the transport is connection-based or connection-less, a different adaptation of the Exon could be used, as to avoid the overhead introduced by requiring the endpoint to be fetched each time a message is sent/received.
##### Previous revision of this section
- **Problem description:**
	- The main purpose of the middleware is providing a framework for communication which ensures exactly-once delivery. Having that in mind, allowing the integration of different transport protocols has several questions surrounding it:
		1. Should protocols that do not ensure exactly-once delivery be supported?
			- If not, then:
				- Should both connection-based and connection-less protocols be supported?
				-  If connection-based is supported, how to handle fatal disconnections?
			-  Else, how should the exactly-once delivery be handled?
		2. How should exactly-once delivery transport protocols (other than Exon) be handled?
- **Solution:**
	1. Support for transport protocols which do not ensure exactly-once delivery:
		- To do so, the middleware would need to implement a message protocol with such guarantee. Such protocol is already provided by the Exon library, so an adaptation of the library could be performed to enable integration of different underlying transport protocols.
		- To support both connection-based and connection-less protocols:
			- If only one connection-less transport protocol was to be used for the middleware instance, then the adaptation of Exon to support connection-less transport protocols would be rather simple, as the library currently uses UDP for the transport layer. 
			- If using multiple connection-less transport protocols simultaneously and/or using connection-based transport protocols is desirable, then the adaptation would require a way to bind the multiple endpoints.
				- Regarding connection-based transport protocols and the possibility of unexpected fatal disconnections, since the Exon instance would be responsible for releasing the messages (only when their delivery is confirmed), then the endpoints associated with the different nodes could be swapped at any time to handle unexpected fatal disconnections.
	2. Support transport protocols which ensure exactly-once delivery (other than Exon):
		- For connection-less transport protocols, which means having a single endpoint to send and receive messages, the adaptation would be straightforward.
		- For connection-based transport protocols, persisting the messages to avoid losing them in case of unexpected fatal disconnections is crucial. While some transport protocols could potentially allow the state to be recovered, others like TCP do not provide such possibility, meaning the messages would be lost. Therefore, wrapping these transport protocols with the adaptation of the Exon library could be the solution.
	3. For connection-based transport protocols, regardless of providing exactly-once delivery or not, a connection retry mechanism to re-establish the connections after unexpected fatal disconnections would re required.
#### Mobility

<span style="color:red">**Check what can be added above from the text below, and how this can be merged to the ideas above**</span>

- **Problem description:**
	- Mobility must be made compatible with every transport protocol that could be integrated.
- **Solution:**
	- Support for transport protocols which do not ensure exactly-once delivery:
		- ad
	- Support transport protocols which ensure exactly-once delivery (other than Exon):
		- This problem arises since the new transport protocols do not integrate with t

	- Discovery service should now allow multiple transport addresses per node.
		- One for each type of transport protocol?
	- For connection-less:
		- 
	- For connection-based:
		- For static[^1] nodes of the topology that change to a new network: informing the discovery service of the change in address is required, so that it can notify any interested nodes attempting to initiate a connection. The interested nodes would then be able to connect in a following connection retry attempt.
		- For ephemeral nodes that change to a new network: upon discovering the connection is inactive[^2]/broken new attempts of connection could be handled

	- **Having multiple bound endpoints would be against usability. Having a single bind endpoint makes sense, however, multiple endpoints defeat the purpose.**
	- Middleware instance must be bound to one and only one endpoint.

[^1] Static in the sense that they are supposed to be present through the lifetime of the distributed system. The static should not be interpreted as not allowing the node to move between different networks.
[^2] Not sure if moving to a new network breaks the sockets. If the transition does not result in an exception through which the unexpected closure of the connection could be detected, then having some kind of mechanism that can detect such event should be employed. Examples: 1) heartbeat mechanism; or 2) explicit test of the availability of the network interface used to bind the socket.
Compatibility with polling
#### Polling compatibility for efficiency

<span style="color:red">**Write this**</span>
#### Socket - Transport incompatibility
<span style="color:red">**Write this**</span>
- **Problem description:**
	- Sockets made with specific transport characteristics in mind may not present the desired behavior when paired with other transport protocols that were not taken into consideration when designing the socket. An example is developing a socket having in mind a transport protocol that ensures exactly-once delivery. Pairing such socket with a transport protocol that does not provide such guarantee will most likely present unwanted behavior.
- **Solution:**
	- For a socket type allow locking which transport protocols can be used. Or just have documentation specifying such information. If the developer uses a protocol that was not verified and said to be compatible with the socket's behavior, then its the developer's fault if undefined behavior occurs.
### Solution