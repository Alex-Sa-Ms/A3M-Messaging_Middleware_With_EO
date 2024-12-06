# Overall Structure
### Part 1: **Introduction and Background**

1. **Purpose and Goals**
    - Provide a clear problem statement and set the objectives of the middleware and transport protocol.
    - Describe the need for an exactly-once delivery guarantee in messaging systems and the scenarios where this is critical (e.g., financial transactions, distributed systems with state synchronization).
    - Outline the specific design goals for Exon and your messaging middleware (e.g., fault tolerance, latency requirements, resource efficiency).

2. **Challenges and Problem Analysis**
    - Detail the main challenges of creating a reliable messaging middleware, particularly with an exactly-once delivery model. Common issues include message duplication, message ordering, and handling message acknowledgment.
    - Discuss the specific problems your middleware and Exon face, such as network failure handling, idempotency, and protocol synchronization.

3. **State of the Art**    
    - Review existing protocols and systems (e.g., Kafka, RabbitMQ, ZeroMQ, etc.) that provide high reliability and guarantee delivery, highlighting their strengths and limitations in providing exactly-once guarantees.
    - Discuss transport protocols such as TCP and how Exon differs in its approach and performance.
    - If applicable, mention any middleware frameworks or libraries that inspired or informed your design.

---

### Part 2: **Core of the Dissertation**

1. **Contributions**    
    - Clearly list your contributions, both theoretical and practical. If your work includes original designs, algorithms, or performance optimizations, describe each briefly.
    - Mention any innovations in Exon adaptations, middleware design, or message handling mechanisms that address exactly-once guarantees.
    - Emphasize any contributions that distinguish your work from existing approaches.

2. **Middleware Architecture**    
    - Provide an overview of the middleware’s overall architecture, including components and their interactions. Use diagrams to illustrate the system design, message flow, and communication patterns.
    - Detail the message lifecycle, including stages such as queuing, processing, transport, acknowledgment, and any retry mechanisms.
    - Add a section on security to talk about the lack of handling of sensitive data (encryption and authentication). 
	    - data integrity is provided by the transport protocol
	    - middleware was designed to prevent misuse as much as possible, including the misuse of developers when creating their own sockets.

3. **Specialized Sockets**
    - Explain the role of specialized sockets within Exon. Describe how these sockets differ from standard network sockets and why they are necessary for achieving exactly-once semantics.
    - Detail any enhancements, such as custom buffering, error-handling, or data serialization, that are implemented in these sockets to meet the middleware's requirements.

4. **Exon - Adaptations and Modifications**
    - Describe any modifications made to the Exon protocol to align it with the middleware’s goals, particularly to support exactly-once delivery.
    - Explain how these adaptations improve protocol performance, reliability, or resource utilization compared to the original Exon design.
    - Discuss any protocol-level mechanisms you introduced, such as unique message identifiers or checkpointing techniques.

5. **Comparative Analysis**
    - Include a comparative analysis with existing middleware solutions, focusing on aspects like delivery guarantees, throughput, latency, and resource usage.
    - Mention similarity in features
    - Present the performance metrics you used to evaluate the middleware, focusing on throughput, latency, and error handling.
    - Detail your testing environment and scenarios, as well as baseline comparisons with other protocols (if applicable).
    - Analyze the results, emphasizing how your middleware maintains exactly-once delivery under various network conditions and scales effectively.

6. **Applications**
    - Describe real-world use cases or scenarios where your middleware could be applied, such as IoT, finance, distributed databases, or edge computing.
    - Outline any deployments, prototypes, or experiments you have performed to validate the middleware’s effectiveness in real-world applications.
    - If available, share results or feedback from actual users or organizations.

7. **Conclusions and Future Work**
    - Summarize the key findings of your dissertation, restating how your work addresses the challenges and goals outlined in Part 1.
    - Discuss limitations of your current implementation or areas where improvements could be made.
    - Identify opportunities for future work, such as enhancements in scalability, support for additional protocols, or integration with other systems.
	    - Multipart messages as an integrated format? Pros: Easier to process messages; Cons: Less versatile as a multipart message format can be employed only on the payload.
	    - Include a way to easily integrate encryption and authentication mechanisms.
		    - The three-way handshake may be of help.

8. **Planned Schedule**
    - Provide a timeline or Gantt chart showing completed, ongoing, and planned tasks to indicate the progress of your project.
    - Include a breakdown of any remaining work, such as additional testing, documentation, or publication of results.

# Documenting the evolution and problem solving

1. **On each chapter/section add a section/subsection to document the challenges, how final solution, etc.**
	- Ideas of names:
		- Challenges and Decision Rationale
		- Design Evolution and Problem Solving
2. Could use a Problem/Solution format on those sections.  

# Middleware Architecture: A Generic Framework for Socket Extensibility 

*(Use diagrams to illustrate the system design, message flow)*

## Ideas to talk about somewhere in the chapter
**(Read *commit logs*, *obsidian notes* and *state of the art* to check for more ideas of what to write)**

- (Have an evolution section mentioning the main problems faced and what were the explored solutions, which was chosen and why.  )
	- Problems:
		1. Poller
			- Extremely useful both to allow the creation of new sockets, but also as a basis of the middleware to allow a reactive programming
				- Talk about its design, the source of inspiration, the problems faced when implementing it (the failed solution that wanted to avoid callbacks; the attempt of simplification with resulted in a deadlock; ), the problems faced when using it (requires careful use: how locking should be done; order of queuing is relevant when the subscribers "collaborate")
				- 

- Programming model / Threading model
- Process a message passes through when being send and when being received
	- Explain each mechanism and why it is relevant


- Talk about the interesting concept of contexts shown by NNG for instance for the Request-Reply pattern. The goal of this feature is to enable concurrency for the Request-Reply pattern when unrelated conversations between two peers are desirable. While such concept is not provided, a similar effect could be achieved achieved by using the Dealer and Router sockets with the payload containing information regarding the context that the message refers to. Another solution is to create a Request/Reply pair of sockets for each context since the creating of the sockets while requiring more resources are not as costly as creating a new TCP/UDP socket. The NNG solution is superior since it uses the same socket and does not require further allocation of resources, so, if such specific behavior is desirable, sockets having such functionality could be implemented. 
## Structure of the chapter
### 1. **Overview of Middleware Design Goals**

- **Purpose**: Briefly restate the main objectives that influenced the architectural decisions: reliability, exactly-once message delivery, mobility, usability, extensibility. Must also mention that this is a prototype, so efficiency, scalability, and network security were not primary issues, but are future work for a following version. 
- **Scope**: Outline which aspects of the middleware architecture will be covered, especially those that are unique or critical to the functionality of your system.
### 2. **System Model**
### 3. **System Overview**
- Brief summary of the main components of the middleware and how they are related to each other.
	- Include domain model diagram to help
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



### 3. **Message Lifecycle and Flow**
(send() is blocking as a consequence of exactly-once delivery and to avoid overwhelming the receiver)
- **Message Processing Pipeline**: Detail the lifecycle of a message from creation through to acknowledgment. This could include stages like:
    - **Reception**: How messages are accepted into the system.
    - **Queueing**: How messages are organized, ordered, and managed in queues.
    - **Transmission**: How messages are prepared and sent through Exon.
    - **Acknowledgment**: How receipt confirmations are handled to ensure exactly-once delivery.
    - **Completion/Deletion**: Conditions for removing a message from the system.
- **Error and Failure Handling**: Describe the flow when errors or failures occur and how the middleware ensures delivery guarantees (e.g., retries, logging, or fallback mechanisms).
(Buffering typically only at Exon)
### 4. **Fault Tolerance Mechanisms**

- **Idempotency and Deduplication**: Describe any mechanisms to ensure that duplicate messages are detected and prevented, a key aspect of exactly-once delivery.
- **Checkpointing and Recovery**: Explain if and how checkpointing is used to restore system state after a failure.
- **Persistence Strategies**: Outline how messages are stored (temporarily or persistently) to prevent data loss during crashes or disconnections.
### 5. **Exon Integration and Protocol Adaptation**

- **Transport Protocol Choice and Integration**: Explain why Exon was chosen and how it integrates with your middleware.
- **Custom Protocol Extensions**: Detail any extensions or modifications to Exon that adapt it for exactly-once delivery, such as unique message identifiers or special acknowledgment handling.
- **Specialized Sockets or Connections**: If the middleware uses custom sockets, describe how they are configured to interact with Exon and any special functions they perform, such as encryption, error correction, or sequence tracking.
### 6. **Concurrency and Scalability Design**

- **Concurrency Model**: Describe how concurrent operations are handled. Include details on threading, asynchronous processing, or task partitioning that enables the system to manage high message volumes.
- **Load Balancing**: If the middleware supports multiple nodes, explain how load balancing is achieved across them.
- **Scalability Features**: Discuss any architectural considerations for scaling horizontally (e.g., adding more nodes) or vertically (e.g., handling increased message size or complexity).

Scalability is a critical consideration for middleware systems, especially as they are scaled up to handle higher message volumes, larger networks, or more complex workloads. Although the current prototype deprioritized scalability, several features and architectural enhancements could be considered in future iterations to make the system more scalable. Here's what could have been addressed:

- Mention that the solution focus on latency and not throughput. To focus on throughput, batching would be required, and such would be ideally implemented at the Exon layer.

1. Horizontal Scalability
Distributed Architecture: Enable the middleware to run across multiple nodes to handle increased workloads by distributing messages and tasks.
Load Balancing: Implement mechanisms to distribute messages evenly across nodes, ensuring no single node is overwhelmed.
Cluster Management: Introduce features for dynamically adding or removing nodes to a cluster, allowing for elastic scaling based on demand.
2. Vertical Scalability
Optimized Resource Utilization: Design the middleware to efficiently use CPU, memory, and storage resources to handle more substantial workloads.
Thread Pool Management: Implement dynamic thread pooling to optimize resource usage under varying loads.
Asynchronous Processing: Use non-blocking I/O and asynchronous operations to handle a higher number of concurrent connections.
3. Message Flow Optimization
Flow Control Mechanisms: Prevent bottlenecks and ensure smooth message delivery by dynamically adapting to network and system conditions.
Backpressure Handling: Introduce mechanisms to signal upstream components to slow down or buffer messages during high load.
Batch Processing: Allow processing of messages in batches for improved throughput and reduced overhead.
4. Scalability in Message Queues
Sharded Queues: Divide message queues into smaller shards distributed across nodes to manage higher message volumes efficiently.
Dynamic Queue Sizing: Adjust queue capacities based on runtime conditions, ensuring they can accommodate spikes in workload without exhausting resources.
5. High Availability and Fault Tolerance
Replication: Maintain multiple replicas of critical components (e.g., message queues, state information) to ensure fault tolerance and scalability.
Partitioning: Partition data and workload across multiple nodes to ensure system components can scale independently.
Failover Mechanisms: Design failover systems for recovering from node failures without service disruption.
6. Monitoring and Metrics for Scaling
Scalable Metrics Collection: Implement lightweight, distributed metrics collection systems that can provide insights without impacting performance.
Dynamic Scaling Triggers: Use real-time metrics (e.g., queue length, message latency) to trigger automatic scaling actions, such as adding resources.
7. Support for Large Payloads and High Throughput
Fragmentation and Reassembly: Enable support for breaking down large messages into smaller chunks for transport, with mechanisms to reassemble them at the destination.
Compression: Provide compression options to reduce the size of payloads and improve throughput.
Protocol Optimizations: Optimize transport protocols for high-throughput scenarios, such as minimizing round-trip times or leveraging multiplexing.
8. Multitenancy Support
Isolation of Workloads: Allow multiple users or systems to use the middleware concurrently without interference.
Namespace Isolation: Ensure separate namespaces for message queues, topics, or other resources.
Resource Quotas: Set resource limits for each tenant to prevent one user from monopolizing system resources.
9. Scalability Testing and Benchmarking
Simulation of Extreme Scenarios: Incorporate tools and testing frameworks to simulate high loads, network failures, and spikes in traffic.
Performance Tuning: Continuously optimize system parameters based on benchmarking results to identify and eliminate bottlenecks.
10. Modular and Extensible Design
Plug-and-Play Components: Allow components to be replaced or extended without re-architecting the entire system, making it easier to integrate advanced scalability features.
Microservices Architecture: Consider breaking the middleware into microservices that can independently scale based on their specific loads.

Why These Features Matter:
These scalability features would ensure the middleware remains performant under increased demand, adapts dynamically to varying workloads, and provides a robust foundation for real-world deployments. While the prototype's focus was on extensibility and exactly-once delivery, these scalability considerations would align the architecture with production-level expectations in future iterations.

#### Design enhancement for the future:

The middleware has a single thread which must perform all the necessary work regarding processing of received messages. Having such thread significantly increases the difficulty in creating new custom sockets. The developer must be very careful to avoid blocking and dead lock scenarios which can slow down or stop the middleware completely.
Having the above in mind, to ease the development of new sockets, to avoid slowing down the middleware and to prevent unwanted behavior, having an asynchronous IO framework to which the processing tasks can be delegated could be the best choice. The framework must be conceived in a way that enables tasks to be cancelled at any time as to not waste resources on tasks that seem to be deadlocked.

### 7. **Security and Data Integrity**

- **Authentication and Authorization**: Explain any security mechanisms for ensuring that only authorized users or systems can access the middleware.
	- Not implemented (future work)
	- Three-way handshake for linking can be used to provide such functionality
- **Data Integrity**: Detail measures taken to ensure data integrity, such as checksum verification, cryptographic signing, or encryption.
	- Data integrity is provided by the Exon/UDP transport protocol
- **Misuse Prevention and Access Control**:
	- Discuss the specific access modifiers (`protected`, `private`, `default`) that you applied to critical components or methods in your middleware. Describe how encapsulation contributes to a controlled environment where only allowed operations can be performed, preventing unauthorized actions by developers. This reinforces both security (by limiting potential attack vectors) and integrity (by reducing the risk of accidental or malicious changes to data or state).
	- Explain that by restricting access to certain methods, you ensure that developers using your middleware cannot inadvertently or intentionally bypass core functionality, perform unauthorized actions, or create unpredictable behaviors. This can include preventing direct manipulation of sockets or bypassing essential validation steps.
	- Emphasize how the design reflects a principle of layered security, where code visibility acts as a first layer of control before any runtime security checks. This design minimizes risks by enforcing rules at both compile-time (through visibility modifiers) and runtime, helping prevent misuse even in the development phase.
	- Describe how these design choices enhance the developer experience by guiding proper usage and reducing potential points of failure or misuse. This can contribute to overall data integrity since developers will have limited ways to deviate from intended functionality.
	- Consider adding a brief statement on how following object-oriented best practices, like proper use of visibility modifiers, aligns with broader principles of secure middleware design. This shows reviewers that your approach follows industry standards for security and integrity, making the system robust and well-architected.
- **Privacy and Confidentiality**: Describe how sensitive data is protected in transit and at rest, particularly if Exon supports encrypted transport.
	- Neither the middleware, nor Exon provide encrypted transport.
	- Maybe research a bit on security to know how some mechanisms could potentially be implemented.

	
### 8. **Monitoring and Logging**

- **System Health and Diagnostics**: Describe any components that monitor system health, including logging, alerting, or self-healing mechanisms.
- **Event and Error Logging**: Explain how events, errors, and state changes are logged, both for debugging and for ensuring transparency in message flow.
- **Metrics Collection**: Outline metrics tracked (e.g., message latency, delivery success rate) and how these are used for performance analysis.
### 9. **Configuration and Management**

- **Configuration Options**: Describe configurable parameters, such as message timeout values, retry limits, buffer sizes, or priority settings.
- **Administrative Interface**: If the middleware includes a user interface or API for management, describe how administrators interact with it to configure, monitor, or control middleware functions.

