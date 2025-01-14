# Title Suggestions

- **Middleware Architecture: A Generic Framework for Socket Extensibility**

---
# Writing Guidelines

- Provide an overview of the middleware’s overall architecture, including components and their interactions. Use diagrams to illustrate the system design, message flow, and communication patterns.
- Use diagrams to illustrate the system design, message flow
- Detail the message lifecycle, including stages such as queuing, processing, transport, acknowledgment, and any retry mechanisms.
- Add a section on security to talk about the lack of handling of sensitive data (encryption and authentication). 
	- data integrity is provided by the transport protocol
	- middleware was designed to prevent misuse as much as possible, including the misuse of developers when creating their own sockets.
- Referir o artigo 24 (Callback Hell) em "Artigos interessantes" e como ele influenciou inicialmente o desenvolvimento do middleware, mas acabou por ser ignorado quando mecanismos de polling estavam difíceis de fazer sem usar callbacks.
- O artigo 25 de "Artigos interessantes" influenciou a arquitetura?
- Ver as otimizações faladas em "The Architecture of Open Source Applications (Volume 2)" e identificar que otimizações foram implementadas e quais poderiam ser implementadas.
- Escrever sobre interoperabilidade na parte das mensagens?
- Have a section/subsection to document the challenges, how final solution, etc.
	- [[Problems]]
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

---

# Structure

1. **[[Overview of Middleware Design Goals]]**
2. **[[System Model]]**
3. **[[System Components and Their Roles]]**
4. **[[Message Lifecycle and Flow]]**
5. **[[Link Management Protocol]]**
6. **[[Fault Tolerance Mechanisms]]**
7. **[[Exon Integration and Protocol Adaptation]]**
8. **[[Concurrency and Scalability Design]]**
9. **[[Security and Data Integrity]]**
10. **[[Monitoring and Logging]]**
11. **[[Configuration and Management]]**

## Alternative format suggestion
![[Pasted image 20250111200244.png]]
