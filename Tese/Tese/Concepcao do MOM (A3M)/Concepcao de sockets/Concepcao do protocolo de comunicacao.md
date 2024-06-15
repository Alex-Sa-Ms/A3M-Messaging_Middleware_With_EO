# Message format
<b style="color:red">Check MQTT and AMQP specifications to organize everything.</b>
## Message structure
- **Fixed Header:**
	- **Version (2 bytes)**: Version of the protocol. Used to allow different protocol versions to talk with each other, and identify if they are compatible.
	- **Message Type (1 byte)**: Specifies the type of message.
	- **Remaining Length (4 bytes)**: Remaining length of the message in bytes.
	- **Source Node Identifier (Omitted**[^1]**)**: identifier of the node that sent the message.
	- **Destination Node Identifier (Omitted**[^1]**)**: identifier of the node that should receive the message.
- **Extended Header:** 
	- Extension of the header which depends on the middleware-level protocol.
- **Payload:** 
	- **Data (variable length)**: Message content, treated as binary blob.

[^1] Omitted because the Exon library messages already carry such identifiers.
### Message Type
The **message type** is essential for determining how to handle each message. To keep it compact and organized, we allocate just 1 byte for this purpose. Since we don't anticipate the need for additional categories beyond the current ones, we group messages into two categories: node messages and socket messages. The first bit of the byte indicates the category: 0 for node messages and 1 for socket messages. The remaining 7 bits specify the exact type of message within that category, allowing for up to 128 unique message types per group. Since it is unlikely that either group will need all 128 types, we have options for future expansion: (1) reassign 1 or 2 of the 7 bits used for message types to increase the number of groups; or (2) we could simply expand the message type field to 2 bytes.
# Socket messages
## Socket messages format
Socket messages are identified by a 1 in the first bit of the message type field.  These messages form a symmetrical protocol used by sockets to communicate with each other. It is symmetrical because peers do not assume different roles, like client and server.
### Extended Header
The extender header of this protocol includes:
- **Source Tag (4 bytes)**: tag of the socket that sent the message.
- **Destination Tag (4 bytes)**: tag of the socket that should receive the message.
### Message Types
This protocol contemplates the following message types:

| **Type** | **Value** | **Description**           |
| -------- | --------- | ------------------------- |
| ERROR    | `0x01`    | Error message             |
| LINK     | `0x02`    | Link request              |
| LINKACK  | `0x03`    | Link acknowledgment       |
| UNLINK   | `0x04`    | Link termination          |
| FLOW     | `0x05`    | Link flow control message |
| CONTROL  | `0x06`    | Control message           |
| DATA     | `0x07`    | Data message              |

### Payload
The payload of a socket message depends on the message type.
## Errors
This section presents errors related to the socket middleware-protocol. 

The possible errors are shown in the table below, along with their corresponding code and whether the error is reported through an ERROR message.

| Type                       | Code   | Reported |
| -------------------------- | ------ | -------- |
| **Socket not found**       | `0x01` | True     |
| **Socket not linked**      | `0x02` | True     |
| **Invalid message format** | `0x03` | False    |
#### Socket Not Found 
A *Socket Not Found Error* must be reported when a received message has a destination tag that does not match any socket registered on the receiving node.
The message must be formed as if the non existent socket was the one sending the error message, i.e., the "source tag" of the extended header must match the tag of the socket that does not exist. The "destination tag" must match the tag of the socket that intended to communicate with the non existent socket.
#### Socket Not Linked
A *Socket Not Linked Error* must be sent when a non-link-related message is received from a socket which is not linked with the destination socket. The received message should be discarded.
#### Invalid Message Format
A *Socket Invalid Message Format Error* happens when a received message does not follow a valid format. The message should be discarded as the socket cannot process the message. 
This error should be logged for debug purposes. Reporting the error to the message sender does not accomplish anything as sockets do not store sent messages. Due to Exon's delivery guarantee, sockets do not need to store sent messages as Exon ensures that messages arrive at the destination. 
## Links
One of the goals of this messaging middleware is to facilitate the creation and use of various messaging protocols. To prevent sockets from trying to communicate with those using a different messaging protocol, a link establishment process is necessary. For two sockets to communicate with each other, they must engage in a link handshake, whose primary purpose is to verify their compatibility.
### Establishing a link

For a link to be established, both sockets must agree to link with each other. A socket establishes the link on its end when it has made its own decision, received the counterpart socket's decision, and both decisions indicate acceptance. To make a decision, a socket requires the metadata of the counterpart socket. Therefore, both sockets must share their metadata, formulate their decisions and exchange these decisions.

To accomplish the above, two types of messages are employed: `LINK` and `LINKACK`. The `LINK` message initiates the linking process[^1] and carries the socket's metadata. The `LINKACK` message is used to send the socket's decision, but it can also include the socket's metadata if it has not been sent yet.

[^1] "Linking process" is an alias of "Link establishment process".

Having that said, the main possible linking flows are the following:
- **Scenario 1 (Only one socket initiates the link establishment process):**
	1. Socket A sends `LINK` message to socket B;
	2. Socket B formulates a decision using the received metadata;
	3. Socket B sends a `LINKACK` message containing its decision and metadata;
	4. Socket A uses the received metadata to formulate its decision and sends it to socket B in a `LINKACK` message[^a];
	5. Both sockets analyze the decisions. If both decisions are positive, they establish the link by registering each other as linked sockets.
- **Scenario 2 (Both sockets initiate the link establishment process simultaneously):**
	1. Socket A and Socket B both send `LINK` messages to each other;
	2. Each socket uses the other's metadata to its decision;
	3. Both sockets share their decision;
	4. Both sockets analyze the decisions. If both decisions are positive, they establish the link by registering each other as linked sockets.
- The remaining possible flows can be verified through the examination of the state machine diagram presented further ahead. 

[^a] Socket A's metadata is not included in the `LINKACK` messages as it was already sent previously in the `LINK` message.

The main goal of establishing links is to ensure compatibility, which can only be determined through the exchange of metadata. Currently, the only required metadata is the socket type. However, extending the linking process to include custom logic, such as messaging protocol logic or authorization/authentication procedures, might be beneficial. This could reduce the number of messages exchanged by embedding custom metadata within the link handshake messages, instead of performing additional handshakes after establishing the link.

In an ideal linking procedure, both sockets could reach the same conclusion regarding the link establishment solely through the exchange of their metadata (sent in `LINK` messages). As the only required metadata, at the moment, is the socket type, such procedure would be possible. However, when considering custom extensions of the linking procedure, assuming that all these extensions can be symmetric[^1] is optimistic and naive. Sockets may have information that should not or cannot be shared[^2], making it impossible for sockets to reach the same conclusion solely through the exchange of metadata. Therefore, after receiving the counterpart's metadata and formulating a decision, each socket must share its decision regarding the link establishment through a `LINKACK` message.

[^1] We can view a symmetric linking procedure as a function that receives the metadata of both sockets and produces the same result. 

[^2] Let's consider an example where a socket is a server and demands authentication, and that the extension of the linking procedure includes the authentication process. The server cannot and must not share all its credentials with the client so that the client can generate the server's decision on its end.

Since the most common scenario involves one socket initiating the link establishment process, the `LINKACK` messages may include metadata along with the socket's decision. This reduces the number of messages needed to reach the final decision.

When a socket has both decisions, it can calculate the final decision. If both decisions are positive, the link is established. If at least one decision is negative, the link is not established.

Depending on which decision is negative, which socket initiated the link establishment, and the reason for the refusal, the information related to the other socket may be discarded. A socket that initiates a link establishment will only discard all information related to the other socket if the reason for refusal is incompatibility[^3]. If the reason differs from incompatibility (e.g., refusal due to reaching the limit of established links), the requester may issue a new link establishment request after a proper timeout[^4]. A socket receiving a link establishment request is not obligated to retain the information of the requester if the refusal reason differs from incompatibility, as it was not its intention to establish the link.

[^3] Incompatibility can only be used as a refusal reason if the rejection factor is a static parameter that will not change during the socket's life, such as the socket type.
[^4] Establishing random timeouts is ideal to avoid link establishment request storms.

In this linking process, one socket (A) always establishes the link first, while the other socket (B) awaits A's decision. Once A has established the link, it can start sending messages to B. However, due to the transport protocol's lack of order guarantees, a message from A that is unrelated to the linking process may reach B before A's `LINKACK` message. Since sockets are prohibited from exchanging messages with a non-linked socket, if B receives a non-link-related message while still awaiting A's decision, B can interpret this message as an implicit positive decision from A. B can then proceed to establish the link and process the received message.

The following state machine diagram is a visual representation of the different states and transitions that a socket may undergo during a link establishment process.
![[Link establishment State Machine.png]]


### Unlinking / Cancelling a linking process

As a link can be established, it can also be severed. For simplification, we will the call the process of severing a link "*unlink*". In addition to severing an established link, the unlink process can also be used to cancel an ongoing linking process.
#### Unlink procedure
Consider the generalization of the unlinking procedure between two sockets: 
- **Socket A (initiator):**
	1. Sets the state of the link to "unlinking";
		- The "unlinking" state discards any message other than an `UNLINK` message.
	2. Send `UNLINK` message to B;
	3. Waits for an `UNLINK` message from B.
	4. Deletes all state related to the link.
- **Socket B:**
	1. Receives `UNLINK` message from A;
	2. Deletes all state related to the link;
	3. Sends `UNLINK` message to A.

This procedure was designed considering that the underlying transport protocol (Exon) does not provide ordering guarantees. This means that an `UNLINK` message may arrive before a `LINK` or `LINKACK` message even though it was sent after them.

#### Special scenario

If an `UNLINK` message is received before a `LINK` message, it signifies an intent to sever a link that the receiver has no prior knowledge of. This is because `LINK` messages initiate the link establishment process. Without a `LINK` message, the socket has no information about the other socket attempting to establish the link, hence there is no active link to sever.

Assuming that there aren't byzantine sockets[^1], the `UNLINK` message can be used to anticipate that a `LINK` message will eventually arrive and that it should be rejected. After rejecting the `LINK` message and discarding the state related to the requester, an `UNLINK` message must be sent to confirm that the link was not established.

[^1] This prototype is designed for use in safe environments. Using it in a hostile environment would require additional security features to safeguard against malicious behavior, such as encryption, authentication, authorization, blacklists, etc.
#### Other scenarios

The remaining scenarios are the following:
- `UNLINK` message arrives before the `LINKACK` message;
- `UNLINK` message arrives after the link has been established.

These scenarios follow the generalized procedure described initially.
#### Notes
- Any attempt to link with a socket with an ongoing unlinking process (with the current socket) will result in an exception.
- Invoking `unlink(sid : SocketIdentifier)` means severing the link with the given socket. This results in the immediate removal of the socket from valid message destinations. 

[^3] There can only be a link at a time between two participants. However, during the lifetime of the sockets, multiple links between the same two participants can be established and severed. The two clocks are used to identify the link within all those links. 
### Preventing Out-of-Order Message Issues

`LINKACK` messages that arrive after an `UNLINK` message will be discarded because they will not find an existing link state. Due to the potential out-of-order message delivery, a `LINKACK` message from a previous linking process might arrive after a `LINK` message from a new linking process. To prevent this old `LINKACK` message from being accepted, all link-related messages (including `UNLINK` messages) should include a clock identifier. To generate these identifiers, each node should maintain a grow-only clock that increments with each link establishment attempt, regardless of its success.

Every linking process is uniquely identified[^3] by a pair of clocks, one from each participant. Messages only need to include the sender's clock since each clock value is unique to a single linking process and is paired with the clock of the other participant. It is crucial to track both clocks because either socket can initiate the link establishment. This way, the clocks are paired, effectively treating what might appear as two separate linking processes as a single process.

[^3] While there can only be one active link at a time between two participants, multiple links can be established and severed over the sockets' lifetimes. The two clocks uniquely identify each link within this sequence.

## Flow control
The socket flow control is performed per link. Having in mind that Exon does not provide ordering guarantees, the design of the flow control mechanism must take this into consideration. 

<span style="color:red">Ver processo de controlo de fluxo do AMQP </span>

### Control and data messages 
#### Pros
Distinguishing between control and data messages allows control messages to bypass the flow control mechanism, ensuring that critical operational commands are not delayed and thus maintaining system responsiveness. This also simplifies the design of the flow control mechanism, enabling automatic updates of flow control windows.
#### Potential Concerns
Since control messages are not managed by the flow control mechanism, it's crucial to avoid overusing them in messaging protocols, as they could overwhelm destinations and cause network congestion.
Security is not a primary objective, however, it should not be overlooked. A potential security issue is the misuse of control messages by malicious actors to bypass flow control and disrupt the system by flooding it with control messages.
<b style="color:red">
<p>- Idea: Socket core creates link objects. This object is required to send a message, and is strongly linked with a specific destination socket. The socket core can have a link constructor setter. That way, any custom information related to a specific link can be kept in the same place and deleted by the socket core during an unlink process.</p>
<p>- Receipts may be helpful for a lot of situations <p>

</b>
## Socket messages
<span style="color:red">Criar tabela igual à da figura 2.10 (Frame Dispatch Table) do AMQP mas com "Nodo", "Socket Core" e "Socket".</span>
### LINK message

### LINKACK message
| Return Code | Description                                                                         |
| ----------- | ----------------------------------------------------------------------------------- |
| `0x00`      | Success                                                                             |
| `0x01`      | Incompatibility                                                                     |
| `0x02`      | Temporarily unavailable                                                             |
| `0x03`      | Canceled - When a linking process is canceled \[*unlink()* invoked after *link()*\] |

### CONTROL message

### DATA message


## Otimizações futuras
### Otimização do espaço utilizado por tags
- Para otimizar o espaço ocupado por tags nas mensagens relacionadas com sockets, no estabelecimento de um link, cada nodo envia uma identificação mais curta do seu socket. 
	- Por exemplo, um inteiro.  
- A tag apenas é necessária para encontrar o socket no nodo destino, após ser encontrado o socket pode-se utilizar a identificação curta fornecida pelo nodo destino.
- É necessário ter cuidado ao fazer *reclaim* dos identificadores para novos sockets. Se é que se pode fazer.