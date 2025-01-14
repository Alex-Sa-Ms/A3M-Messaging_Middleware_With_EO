# Structure

1. Programming Model
2. Threading Model
3. Efficiency Considerations
4. ....

**Decide where to put the following sections/discussions?**
- How to handle I/O?
## Writing Guidelines
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

# Programming Model
## Writing Guidelines
- What approach would be ideal? Threading model, reactive programming, actor model, etc.
- When using an asynchronous or event-driven model, efficiency concerns like context switching, load balancing, etc., could fit in here.

# Threading Model
## Writing Guidelines
- Talk about how difficult creating a middleware under thread-safe semantics is. 
	- Performance-wise
	- Deadlocks
	- etc
- For comparison, check the veracity of the following:
	- **ZeroMQ**: Uses a pool of threads (typically 1 by default, but can be configured for more).
	- **nanomsg**: Typically uses 1 thread per socket.
	- **NNG**: Uses 1 thread per socket by default, but also supports multi-threading with configuration for worker threads and thread pools.


# Efficiency Considerations

- Should this section be renamed to "(Other) Efficiency and Scalability Considerations"?

## Writing Guidelines
- Correlated with threading model
- Lock-free algorithms to not disturb the middleware thread
- Etc


# Floating Sections
*(Sections that need to be put somewhere)*
## Handling I/O Operations

***Reference:*** *"How do we handle I/O? Does our application block, or do we handle I/O in the background? This is a key design decision. Blocking I/O creates architectures that do not scale well. But background I/O can be very hard to do right."* (found in https://zguide.zeromq.org/docs/chapter1/#Why-We-Needed-ZeroMQ)

**Questions:**
- Should this be part of the "Efficiency Considerations" section?
---

The design of I/O operations in a messaging middleware is a critical factor in achieving scalable systems. Blocking I/O, while simpler to implement, often limits scalability; on the other hand, non-blocking I/O introduces complexity but enables more efficient resource utilization. To address these challenges, the middleware balances blocking and non-blocking behaviors through a hybrid approach, leveraging background threads[^1] to manage I/O operations.

[^1] The background threads are the combination of the middleware thread with the Exon library threads. For simplicity, we will address them as combination, since the responsibilities of each thread have been clarified previously.

At the core of the middleware design is the requirement to ensure Exactly-Once delivery. This means that messages must neither be discarded until delivery and processing are confirmed nor be duplicated. Focusing on the first requirement, the accumulation of undelivered messages at the sender and unprocessed messages at the receiver can lead to resource exhaustion. Both memory and computational[^2] resources are affected in such cases. The devised solution mitigates this by carefully managing I/O operations via a combination of background threads and flow control mechanisms.

[^2]  Computational resources are required to manage the retained messages at the sender, such as retransmitting them. 
### Receive Operations

For receive operations, the middleware employs background threads to handle incoming messages, ensuring that no messages are discarded unless they fail validation. These threads queue and validate messages before delivering them to the appropriate sockets. Application threads can then receive the messages from the sockets using one of the following behaviors:

1. **Blocking Receive:** The client thread waits until a message is available, ensuring it processes messages as they arrive.
2. **Blocking Receive with Timeout:** The client thread blocks for a specified time waiting for a message. If a message is not received within the timeout period, the operation returns, allowing the client to handle the situation or address other tasks.
3. **Non-Blocking Receive:** The client thread regains control immediately, regardless of message availability.

In all cases, messages are only discarded by the middleware when they fail pre-delivery validation (e.g., directed to a non-existent socket). This validation ensures that the integrity of message delivery is maintained, and only valid messages are passed to the application.
### Send Operations

Message sending is similarly managed by background threads, which ensure Exactly-Once delivery. Application threads do not transmit messages directly but submit them to the middleware. A flow control mechanism regulates the submissions to prevent resource exhaustion and receiver overload. Similarly to receive operations, there are three operation modes:

1. **Blocking Send:** The client thread waits until the message submission is allowed, after which the message is handed off to background threads for transmission.
2. **Blocking Send with Timeout:** The thread waits up to a specified duration for permission to send. If the timeout elapses, control returns to the client, enabling it to retry or handle the failure as needed.
3. **Non-Blocking Send:** The client thread attempts to send the message and immediately regains control, regardless of whether the message is submitted.

If submission fails, the middleware hands back the responsibility for the message to the application, meaning the message is no longer covered under the middleware's Exactly-Once delivery guarantee.

### Key Design Considerations

It is important to note that the three operation modes for both sending and receiving are designed to offer behavior flexibility. The behavior described for these operations serves as a baseline. Each socket has its own semantics and rules, meaning the blocking behavior is not solely determined by the flow control mechanism or the absence of messages to be received. For instance, a socket may include additional queues, allowing messages to be queued on the socket even when the flow control mechanism temporarily prevents their submission. Similarly, when receiving, a socket might already have messages queued for delivery, but due to ordering constraints, application threads may remain blocked while waiting for the correct message to arrive.

By delegating much of the I/O processing to background threads and offering flexible blocking and non-blocking behaviors, the middleware achieves a balance regarding scalability. This approach ensures that applications leveraging the middleware can maintain robust message handling even under high load, while also providing developers the tools to customize their I/O operations for optimal performance.

# Future Work

The middleware has a single thread which must perform all the necessary work regarding processing of received messages. Having such thread significantly increases the difficulty in creating new custom sockets. The developer must be very careful to avoid blocking and dead lock scenarios which can slow down or stop the middleware completely.
Having the above in mind, to ease the development of new sockets, to avoid slowing down the middleware and to prevent unwanted behavior, having an asynchronous IO framework to which the processing tasks can be delegated could be the best choice. The framework must be conceived in a way that enables tasks to be cancelled at any time as to not waste resources on tasks that seem to be deadlocked.
