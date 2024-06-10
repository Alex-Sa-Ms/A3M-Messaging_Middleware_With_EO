# Problemas
- Link e unlink
	- Permitir cancelamento das operações? Acho que leva a demasiada complexidade.
- Processo para unlink?
	- **Three-way handshake:**
		- Can be important as it allows the flow of messages to terminate between the two sockets before effectively closing the link. Assume that the socket that initiates the unlinking process is called A and the other socket is called B. The first message, sent by A, is used to advertise the intention of closing the link. The second message indicates that B is ready to close the link and won't send anymore messages. The third message, sent by A, confirms that A won't send anymore messages and that B may close the link.
		- However, it does require the high-level socket to determine when it won't be sending more messages.
	- **Two-way handshake:**
		- Should only be invoked when known that the communication flow will not be terminated awkwardly.
		- Socket A sends `UNLINK` message informing that it won't send more messages and socket B responds with an `UNLINK` message confirming that he won't send any more messages either. Both remove the other socket from the linked sockets upon sending the `UNLINK` message.
