**AKA System Overview***
# Rationale for this section
- Mentioning all components sequentially may not seem ideal, however, when attempting to describe the components (purpose, key properties, key methods, relationships) in the sections where they feel most connected - for example, mentioning Message Dispatcher in the "Message Lifecycle" section - will actually result in a structure that feels disconnected and with lack of reading flow.
 - ***Should a section with auxiliary entities follow, or should the title be changed so that entities like Message and Payload can be included?***
# Structure
- Write an introduction that informs the structure of the chapter.
- First section should be "System Overview"
- The following sections are based on the components.
- Section Structure:
	1. System Overview
	2. Node / A3MMiddleware
	3. Socket
	4. Link
	5. Discovery Service
	6. Polling Mechanism
	7. ...
- Component structure:
	1. Purpose *(Not actually a subsection. Serves as introduction for the component)*
	2. Key Properties
	3. Key Methods
	4. Relationships
	5. Related entities

## "Architecture Overview" Section - First Writing Attempt

\section{Architecture Overview}

\begin{comment}
    ### 2. **Middleware Architecture Overview*
        - ADDITIONAL IDEAS OF WHAT TO WRITE:
            - **Messages**: Message exactly-once delivery (where it is guaranteed, re-sending, etc), queueing and order (Message lifecycle)
            - **Control Plane vs. Data Plane**: If applicable, separate components handling control logic (e.g., connection setup, monitoring) from those managing data flow (e.g., message transfer, buffering). 

        - CREATE DOMAIN MODEL DIAGRAM TO EXPLAIN THE RELATIONSHIPS BETWEEN THE ELEMENTS?

        - I THINK THAT I PROBABLY SHOULD START ELABORATING AND ADDING CODE RELATED STUFF. REASON BEING THAT I WANT TO ELABORATE ON SOME COMPONENTS BUT DON'T WANT TO BE REPETITIVE, SO I COULD FIND A STRUCTURE THAT LETS ME DO A BRIEF SUMMARY OF HOW THE MIDDLEWARE WORKS AND THEN PROCEED TO ELABORATING ON EACH OF THEM. ANOTHER POSSIBILITY IS TO HAVE A SECTION FOR EACH ELEMENT, BE IT SMALL OR NOT AND HAVE ALL ASSOCIATED INFORMATION IN THAT PLACE. 
            - NEW STRUCTURE:
                - Middleware Architecture : ...
                    - Overview of Middleware Design Goals
                        - ...
                    - Node
                        - 
\end{comment}

The middleware is composed of several elements, each designed to fulfill specific roles in the communication process. These elements work together to ensure reliable and extensible messaging between distributed systems. This section serves as an overview of the middleware's architecture, identifying key elements and their responsibilities, briefly stating how they interact within the system, while also informing on the rationale behind architectural decisions.

\subsection{Node}

Using the middleware starts with the creation of a middleware instance which represents a \textbf{Node}. The main argument required to create a \textbf{Node} is the node identifier (\textbf{NodeId}) which must be unique within the network created using the middleware. 

The middleware instance is just a facade for the middleware functionality which is spread across multiple components. 

While nodes are now addressed by node identifiers, transport layer addresses (IP + port) are still required by the middleware to determine how to route messages. The associations between node identifiers and transport layer addresses can be fed to the middleware in two ways. The first is through a discovery service being the recommended approach as to take advantage of the benefits of using node identifiers. The second approach is to register the associations related to the nodes of interest using the middleware's API. 

\begin{comment}
    Should I have examples here for each case? Saying that a discovery service could be a database, and how the code would look like to register it. And how the code would look like when registering associations manually. 
    THE SOLUTION SHOULD BE:
        1. For the first example, specify it in its own section along with the code example, and make a reference for it.
        2. For the second example, add an image in a "Attachments" section and make a reference for it.
\end{comment}

A \textbf{Node} acts as a container and manager of communication endpoints, enabling interaction with other nodes of the network. 

While the node could manage any kind of communication endpoint, this iteration focused on allowing communication through the use of \textbf{Sockets}, which should be versatile enough to meet any requirements.

\subsection{NodeId}

A node identifier, or NodeId, uniquely identifies a node within the network built over the middleware. The identifier is a crucial part of the routing information used for message routing. 

Addressing communication endpoints with logical identities like "Book-Service", is far more convenient than using transport addresses that are a combination of an IP address and a port. In addition to being much more readable and memorable, it also transcends spatial and temporal locality, making developing and maintaining code much simpler. The node identifier can be used to query a discovery service which contains or will eventually contain the transport layer information of the node of interest. Nodes that are to be made available and visible to other nodes, usually called static nodes of a topology (servers), can have their related transport layer addresses introduced and modified in a discovery service, which the middleware contacts to retrieve the required information to proceed with the routing of messages.

When attributing identifiers to the nodes, one must ensure their uniqueness within the network. The node identifier is part of the routing information used to deliver messages, hence, the lack of uniqueness leads to the incorrectness of the message routing. Multiple nodes with the same identifier are perceived as one, which ultimately results in bad routing. At the transmitter, the transport address associated with such node identifier will switch back and forth across the different nodes associated with the node identifier, meaning messages will most likely not arrive at the intended destination, since messages are only sent to one of the recipients with such identifier, in addition to the potential corruption of delivery state, which leads to failure to provide exactly-once delivery, including possible infinite retransmissions of a message which will never be accepted or the acknowledgment of the message as if it was previously received. 

\subsection{Socket}

Sockets are the communication endpoints made available by the middleware. The sockets are identified by a combination of identifiers: the identifier of the node that owns the endpoint and an identifier that uniquely identifies the endpoint within the node, which we call tag identifier (\textbf{TagId}) or just tag. 

Having the (NodeId, TagId) pairs of the nodes of interest is not enough to start communicating with them. Before doing so, establishing a \textbf{link} (analogous to a connection) is required. The link establishment consists of a handshake where the sockets must both agree on establishing the link, which then enables the exchange of \textbf{messages} between the two sockets. Sockets manage links and the messages that pass through them.

Sockets can be of multiple types. Each socket talks a specific \textbf{Protocol} and is compatible with certain protocols. When the protocols of the sockets are incompatible, the link establishment is not permitted. The sockets provided by default make the creation of topologies including patterns like Request-Reply, Push-Pull (One-Way Pipeline) and Publish-Subscribe easy. While the sockets provided only cover these patterns, the middleware needs not to be limited to these patterns. Developers may create their custom sockets and register them in the middleware instance so that they can be employed. A custom socket can be created by creating a class that extends the \textbf{Socket} class. The \textbf{Socket} class is a template class presenting several methods, with some being unmodifiable to prevent undefined behavior that could disrupt the normal functioning of the middleware, while other methods can be altered to meet certain requirements. 

\subsection{Socket Manager}

Each middleware instance owns a socket manager which manages and contains the sockets. Creating and closing sockets is the responsibility of the socket manager. Creating and closing sockets through the socket manager is a way to safeguard against unwanted actions. For instance, sockets require a \textbf{Message Dispatcher} to dispatch messages. The respective message dispatcher is added to the socket by the socket manager at creation time so that the socket can send messages. When the socket is closed, the message dispatcher is removed to ensure the socket cannot send any more messages after being closed. 

\subsection{Link}

Link is another main entity of the middleware. A link is only established when the two involved sockets agree on its establishment. The process of link establishment is described in the section "Linking process". A link can be thought of as a passage through which messages are authorized to flow between the two sockets that created it.

Since each socket can have multiple links, targeting each link individually is necessary. To do so, each link as a communication endpoint associated that allows sending to and/or receiving messages from. The communication endpoint in question is called \textbf{Link Socket}. 

\subsection{Link Socket}

- flow control mechanism

\subsection{Message}

As perceived already, the middleware is message-based, i.e., the communication occurs through the exchange of messages between the sockets. The messages' structure is described in a following section, "Messages - Structure and Types".

Messages are sent by sockets through a \textbf{Message Dispatcher} associated with the middleware instance, and received by the \textbf{Message Processor} a thread of the \textbf{Message Management System}, which forwards messages to the destination socket mentioned in the message's routing information. More on the lifecycle of the messages in a following section called "Messages - Lifecycle". 

\subsection{Protocol}

## Writing Guidelines

- Make brief introduction about the purpose of this section.
- Provide an overview of the system 
- Enumerate the entities and components while briefly describing them to provide context of their functionality.
    - Describe the relationships between the entities/components.
    - Elaborate on entities/components that deserve to be elaborated. For instance, their methods, attributes, possible optimizations and new features, rationale behind their architecture, etc.
    - Mention the rationale behind each entity/component.
		- For example, the establishment of links is required due to compatibility and presence reasons. Sending a message using a certain recipient ID is not enough because we could be sending it to an incompatible socket or be sending a message to a socket that does not yet exist which means the message would be discarded defying the principle of exactly-once delivery.
- Include domain model diagram to help
# System Overview

The **System Overview** section provides a high-level description of the middleware's structure and the primary components that make up the system. It outlines the key entities involved in communication and message handling, such as nodes, sockets, and links, while also introducing the fundamental relationships between these components. This overview serves as the foundation for understanding the more detailed components and their interactions, which will be explored in subsequent sections.

*(Add the paragraph above if "System Overview" becomes a section. If it stays a subsection, the paragraph is not required.)*

---

The core of the middleware system is the **Node**, the central unit that represents an instance of the middleware.

Each **Node** is uniquely identified by a **NodeId** within the network. While nodes are referenced by logical identifiers (such as "Library"), the middleware still requires transport layer addresses to route **messages**. These associations between node identifiers and transport addresses are typically managed by a **discovery service**.

Nodes act as containers for **Sockets**, which serve as the communication endpoints within the system. Each socket is identified by a unique **TagId** within the node. The combination of a node's identifier (**NodeId**) and the socket's **TagId** creates a globally unique identifier known as the **SocketId**. Communication between nodes occurs through these sockets.

To enable communication between two nodes, the developer selects or implements sockets that match the desired communication requirements. Once the appropriate sockets are created, a **Link** must be established between them. A link represents a conversation, and it can only be established when both nodes agree to communicate based on compatible **messaging protocols**. If the protocol of one socket is present in the other socket's list of compatible protocols, a link can be formed.

**Domain Model Diagram:** (present in /Downloads/TesteVP/ directory)
![[DomainModel.png]]
- Specify that the domain model is a simplification which purpose is to ease the reader in grasping the basic concepts of the system.
	- For simplicity, the domain model illustrates nodes as the entities responsible for routing, however, it is not exactly like that. Routing is the responsibility of the **Transport Subsystem** comprised by the **Message Management System** and the **Exon Middleware**, which are both initialized by the node instances.
	- Although the domain model shows the node as the one that routes messages, it is not exactly like that. The node knows the concept of associations between node ids and transport addresses, for the purpose of configuration, enabling developers to manually register associations or register a discovery service. The routing is managed by the MessageManagementSystem component, which is created and owned by the node instance.
	- *Maybe edit the diagram to include a "transport layer" entity?


**Class Diagram (Compact):** (present in /Downloads/TesteVP/ directory)
![[ClassDiagramCompact.png]]


**Class Diagram (Spread):**
![[ClassDiagramSpread.png]]
# Questions to help write each component section

- **Purpose and Role**
    - What is the primary function of this component or entity?
    - Why is it needed within the middleware architecture?
    - How does it contribute to the design goals (e.g., exactly-once delivery, fault tolerance, scalability)?

- **Key Properties**
    - What are the defining attributes of this component?
	    - Examples: identifiers, states, metadata, configuration parameters, or specific features.
	- Any notable constraints or assumptions?

- **Key Methods or Functionalities**
    - What operations or methods does this component expose?
    - How does it interact with other components?
    - How does it achieve its role in the middleware?
    - Examples: initialization, processing, sending/receiving data, state transitions.

- **Inputs and Outputs**
    - What data does this component consume or produce?
    - Include examples of message structures, metadata, or control signals.

- **Relationships with Other Components**
    - Which other components does it depend on or interact with?
    - Describe the nature of these interactions (e.g., "Node interacts with Link Manager for creating links", data producer/consumer, management, communication).

- **Design Considerations**
    - Are there specific design challenges or trade-offs addressed by this component?
    - Examples: performance optimizations, fault tolerance mechanisms, extensibility.

- **Extensibility/Configuration Options**
	- Can this component be extended or configured to support new behaviors or protocols?
	- What are its key configuration parameters?

- **Lifecycle and Behavior/State Management**
    - How is this component initialized, used, and terminated?
    - How does it manage its internal state or behavior?
    - Any key transitions, events, or lifecycle steps to describe?

- **Failure Handling**
    - How does this component behave during failures or unexpected conditions?
    - Does it have built-in fault tolerance or recovery mechanisms?

- **Scalability Considerations**
    - How does this component handle high concurrency or large-scale deployments?
    - Does it have mechanisms to support distributed or concurrent operation?
	    - Examples: load balancing, resource management, or horizontal scaling

- **Security Implications (if applicable)**
    - Are there any security concerns or protections associated with this component?
    - Examples: authentication, data integrity checks, encryption.

- **Examples/Scenarios**
    - Provide a short scenario or example of how this component is used in a typical workflow.
    - Example: “The Message Dispatcher receives a message from the Message Processor, looks up the appropriate Link, and forwards it to the destination node.”


# Node / A3MMiddleware

The **A3MMiddleware** instance is the entry point for using the middleware, with each instance representing a **Node** in the topology. This class serves as the central component of the system, orchestrating the middleware’s primary subsystems, including the socket manager and the transport layer. Acting as a facade, it centralizes and encapsulates the functionality of these subsystems, particularly for general configuration and socket management.

The encapsulation provided by **A3MMiddleware** enhances the middleware’s usability by allowing clients to perform operations directly through the middleware instance rather than interacting with underlying components. For example, clients can register a new socket type with `middleware.registerSocketProducer(...)` instead of accessing the socket manager directly via `middleware.socketManager().registerProducer(...)`. This approach not only simplifies the API but also acts as a safety measure, restricting direct access to the internal components of the middleware.

In terms of relationships:

- **A3MMiddleware** interacts with the **Socket Manager** for tasks such as registering new sockets and managing their lifecycle.
- It initializes the transport layer of the middleware, the **Exon** library, and interacts with it to apply configuration changes requested by the client.
- It also sets up the **Message Management System**, supplying it with necessary components: the **Exon** instance and the **Socket Manager**.

By centralizing these responsibilities and interactions, **A3MMiddleware** plays a crucial role in ensuring seamless integration and operation of the middleware's subsystems.


---
---

The initialization of sockets preemptively

---

Using the middleware starts with the creation of a middleware instance (**A3MMiddleware**) which represents a **Node**. The main argument required to create a Node is the node identifier (NodeId) which must be unique within the network created using the middleware.

The middleware instance is just a facade for the middleware functionality which is spread across
multiple components.

While nodes are now addressed by node identifiers, transport layer addresses (IP + port) are still
required by the middleware to determine how to route messages. The associations between node identifiers and transport layer addresses can be fed to the middleware in two ways. The first is through a discovery service being the recommended approach as to take advantage of the benefits of using node identifiers. The second approach is to register the associations related to the nodes of interest using the middleware’s API. 

A Node acts as a container and manager of communication endpoints, enabling interaction with other nodes of the network.

While the node could manage any kind of communication endpoint, this iteration focused on allowing communication through the use of Sockets, which should be versatile enough to meet any requirements.

### Key Attributes

In addition to the core components — **socketManager** (Socket Manager), **eom** (Exon Middleware), and **mms** (Message Management System) —, initialized during the creation of the middleware instance, the other key attributes of a middleware instance include:
- **nodeId**:  
    The unique identifier for the node (**NodeId**), used to distinguish this middleware instance within the topology.
- **state**:  
    Represents the current state of the middleware instance, which is essential for managing its lifecycle. The state ensures orderly transitions which are explained further in the section [[#Node / A3MMiddleware#State Management|State Management]].
- **lock**:  
    A reentrant lock that prevents race conditions between client threads. For example, it ensures that two client threads cannot simultaneously create sockets with different types but with the same identifier (**TagId**), maintaining consistency and preventing conflicts.
    
### Key Methods
- What operations or methods does this component expose?
- How does it interact with other components?
- How does it achieve its role in the middleware?
- Examples: initialization, processing, sending/receiving data, state transitions.
- Talk about the defaultN when mentioning the N parameter of the constructors.
---

The key methods of the middleware instance can be divided into X sections:
- Initialization;
- Node discovery;
- Socket Management.

The implementation of the following methods basically corresponds to the delegation of the parameters received to the respective components method with the same signature, as demonstrated in the example given in this component's introduction. Some of the methods also include concurrency control and state verification (i.e. if the node is in a state that allows the operation such as the creation of a socket requiring the instance's state to be `RUNNING`).
#### Initialization Methods

The initialization methods consist of several constructors and "start" methods. 

The available constructors are the following:
- `A3MMiddleware(String nodeId, String address, int port, Integer N, List<SocketProducer> socketProducers)`
- `A3MMiddleware(String nodeId, String address, int port, int N)`
- `A3MMiddleware(String nodeId, int port, int N)`
- `A3MMiddleware(String nodeId, int port)`

Different constructors are provided to enable different levels of configuration. The parameters found in these constructors are:
- **nodeId:** a string identifier to uniquely identify the node in the topology;
- **address:** a string representing the local IP address for the bind operation of the Exon library UDP socket;
- **port:** an integer between 0 and 65536 (inclusive) representing the local port for the bind operation of the Exon library UDP socket;
- **N:** an Exon library configuration value that determines the maximum number of slots to be requested to a node at a time. A message can be in transit for each slot reserved at the destination node. If the used constructor does not possess this attribute or if `null` is provided, the middleware sets as the default value 100 slots.
- **socketProducers:** list of socket producers. If the used constructor does not possess this attribute or if `null` is provided or if the list is empty, a default collection of producers is used.

The "start" methods below are used to provide "green light" to the message management system, enabling it to effectively start receiving and processing messages from the transport layer.   

#### Node Discovery Methods

The middleware instance does not handle routing directly, however, this component is aware of the concept of association between node identifiers and transport addresses, enabling it to serve as a bridge for configuration of the transport layer. The methods provide for this context are:
- `setDiscoveryService(DiscoveryService discoveryService)`: enables setting a discovery service.
- `registerNode(String nodeId, TransportAddress taddr)`: enables manual registration of an association.
- `unregisterNode(String nodeId)`: enables manual removal of an association.

#### Socket Management

Finally, a set of methods for the management of sockets are provided:
- `registerSocketProducers(List<SocketProducer> producers)`: Enables the registration of new socket producers, one for each new socket type that the middleware should allow to create.
- `Socket createSocket(String tagId, int protocolId)`: Method that allow a socket to be created. **tagId** corresponds to the locally unique identifier for the socket. **protocolId** is an integer representing the protocol identifier that identifies the type of socket to be created.
- `<T extends Socket> T createSocket(String tagId, int protocolId, Class<T> socketClass)`: Similar to the above but with an extra parameter (**socketClass**) that enables casting the created socket to the referred class. However, if the socket defined by the **protocolId** does not correspond to an instance of the provided class, the creation of the socket will be aborted and an exception will be thrown. This method is particularly useful when the sockets have custom APIs, such as the `PubSocket` from the Publish-Subscribe messaging pattern which includes methods related to subscriptions. This variant is for usability purposes, enabling the code to look like
  `PubSocket pubSocket = middleware.createSocket("news", PubSocket.protocol.id(), PubSocket..class);`
  , as opposed to
```
	Socket socket = middleware.createSocket("news", PubSocket.protocol.id());
	PubSocket pubSocket = (PubSocket) socket;	
```
In addition to these methods, the default sockets have a static method that enables their creation from their own classes as long as their producers have been registered in the middleware. Using the own class static method, the PubSocket can also be created as follows:
`PubSocket pubSocket = PubSocket.createSocket(middleware, "news")`.
- `Socket startSocket(String tagId, int protocolId)` and `<T extends Socket> T startSocket(String tagId, int protocolId, Class<T> socketClass)`: 
	- These `startSocket()` methods are similar to the `createSocket()` methods explained above, but with one key difference which is starting the sockets. The effect of starting a socket will be explained ahead in the Socket component section.
- `void closeSocket(Socket s)` and `void closeSocket(String tagId)` are two methods for closing a socket. One uses the socket instance while the other only requires the local identifier.
- `Socket getSocket(String tagId)` and `<T extends Socket> T getSocket(String tagId, Class<T> socketClass)` , while these methods are not related to socket management, they enable retrieving a reference to existing sockets. 


### State Management
- How is this component initialized, used, and terminated?
- How does it manage its internal state or behavior?
- Any key transitions, events, or lifecycle steps to describe?
- Why is each state needed?
--- 

- While the CREATED state makes sense for configuration purposes such as registering the associations   be it through a discovery service or manually insertion, initially it was thought as to prevent  linking retries. Could the code be modified to do this as well? For instance, enabling sockets to be created but not started while the middleware is in a created state, and when the middleware is started, before starting the Message Management System, all the created sockets could be started.

---


## Node Identifier
- 
## Discovery Service
- Associations between transport addresses and node identifier
## Exon Middleware
- mention the P value, its purpose, the value attributed by the middleware instance by default and why such value is used
	- **P:** a configuration value of the Exon library that defines the number of messages that can be in transit for each destination node.
## Message Management System
- Retry linking event
### Message Processor
- Thread of the message management system

## Message Dispatcher
- Passed to all socket instances
- Message Management System is the current dispatcher
- But could probably be used as a chain, to execute multiple actions over the messages: for instance, to in addition to the messaging layer have a persistence layer, a logging/metrics layer, etc. 
## Socket Manager
### Socket Producer
## Socket
- Can be divided into two parts: 
	1. *Generic Socket* 
		- Mention methods that provide the generic functionality
		- Talk about link/link socket and their methods which also provide generic functionality
	2. *Socket Specialization*
		- 
## Socket Identifier
## Protocol
## Link Manager
## Link
## Link Socket

## Poller
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
## Wait queue



## Supporting entities
Supporting entities which are not to be elaborated as components, but must be further explained in other sections.
### Msg
### SocketMsg
### Payload
### Options (option handlers)
### Flow Control Mechanism
