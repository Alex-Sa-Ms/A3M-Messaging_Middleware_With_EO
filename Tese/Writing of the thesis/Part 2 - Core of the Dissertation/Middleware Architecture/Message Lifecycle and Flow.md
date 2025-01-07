# Structure
- Potential sections: 
	- Message Format; 
	- Message Types; 
	- Message Lifecycle; 
	- Message Flow Control 

1. ...
2. Flow Control

## Writing Guidelines
(send() is blocking as a consequence of exactly-once delivery and to avoid overwhelming the receiver)
- **Message Processing Pipeline**: Detail the lifecycle of a message from creation through to acknowledgment. This could include stages like:
    - **Reception**: How messages are accepted into the system.
    - **Queueing**: How messages are organized, ordered, and managed in queues.
    - **Transmission**: How messages are prepared and sent through Exon.
    - **Acknowledgment**: How receipt confirmations are handled to ensure exactly-once delivery.
    - **Completion/Deletion**: Conditions for removing a message from the system.
- **Error and Failure Handling**: Describe the flow when errors or failures occur and how the middleware ensures delivery guarantees (e.g., retries, logging, or fallback mechanisms).
(Buffering typically only at Exon)