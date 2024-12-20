# Writing guidelines
- Brief summary of the main components of the middleware and how they are related to each other.
	- Include domain model diagram to help
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

# Architecture Requirements
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
# Design Questions
- Does every method need to be implemented from scratch for every socket implementation?
- Can RAW and COOKED sockets be created? 
	- Are they implemented as two different "basic" sockets? Or, a single "basic" socket which has the behavior dictated by a "cooked" flag defined at creation time.
- Could I have something like a pipe upon the establishment of a link? Does it make sense to exist?
	- Pipe is analogous to link socket. More flexible and usable way of managing each link.
- How does a reader thread know which socket should receive the receipt?
