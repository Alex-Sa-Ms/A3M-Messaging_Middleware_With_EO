# Adaptação e reestruturação do Exon

## Supporting mobility scenarios

Supporting mobility scenarios means that nodes may freely change between different transport addresses and maintain communication. 

In this section, we will talk about the changes required by the Exon protocol library to support mobility scenarios:

- Globally unique identifiers instead of transport addresses for node identification;
- Association of globally unique identifiers with transport addresses;
- Inclusion of sender and receiver identifiers in every message.

### Node states

Before jumping to the explanation of the changes, it is essential to refer some aspects related to nodes’ states.

As explained succinctly before **(reference the Exon’s chapter)**, for each client message (payload) a 4-way exchange is required to deliver it:

1. The sender requests a slot.
2. The receiver allocates the slot and sends it to the sender.
3. The sender stores the slot as an envelope, creates a token using the envelope and the payload, and sends the token to the receiver.
4. After receiving the token, the receiver acknowledges the reception of the message and discards the associated slot.
5. After receiving the acknowledgement, the sender discards the token.

![Untitled](Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20cbcfb8fb630c459a96ce290b6358d259/Untitled.png)

These exchanges require both the receiver and the sender to hold the state related to the exchange. As we can observe in the example above, the state is divided into a *send record* (SR) and a *receive record* (RR), with each record being mapped with the identifier of the remote node as the key (**footnote:** The original implementation of Exon uses transport addresses as node identifiers as seen in the figure.). A send record holds a queue of payloads without an associated envelope, a list of unused envelopes and a set of tokens (with tokens being the combination of an envelope and a payload). The receive record is responsible for keeping track of slots for which a client message has not yet arrived.

It is clear that send records and receive records operate together as pairs. To ensure the correctness of the algorithm (i.e., to ensure exactly-once delivery), it is fundamental for a received message to be matched with the proper record, be it a send record or a receive record. This is because messages are formed having the state of the counterpart record in mind.

To better understand the problems posed by matching a message improperly, let us examine an example similar to the one presented in the illustration above. Considering the existence of a receive record with some allocated slots, *[0..9]*, and its counterpart send record with a token, *0 → “Hi!”*, to be delivered. Let’s assume that node A changes to a different address after receiving the slots from B and before sending the token. Since the original implementation of Exon uses the transport addresses as the identifiers to map the records, the following situations are possible when node B receives the token from A:

- The transport address of the datagram that carries the token does not have a matching receive record.
- The transport address of the datagram that carries the token matches the receive record of another node that had its transport address changed.

Both the situations described above present significant issues. Messages are created with specific attributes tailored to the corresponding record, such as clocks, slots, etc. Consequently, when incoming messages are not matched with the appropriate record, either the message is perceived as incoherent and is thus discarded, or it corrupts the record. With the idea of corruption, I mean that a message not intended for the matched record alters the state of the record. For instance, let’s consider a TOKEN message that is perceived as coherent by the receive record, but it was not intended for that specific record. Let us also assume that the token’s envelope matches a slot of the receive record. Since the message is perceived as valid, an unintended payload will be delivered to the application. Furthermore, the slot will be consumed which obstructs the delivery of the actual payload that should have utilized the slot. (**footnote:** A payload within a token is only delivered to the application when the token’s envelope has a corresponding slot present in the receiving record.)

In conclusion, ensuring that messages are consistently matched with the appropriate records is the foundation for supporting mobility scenarios as it results in the correct behaviour of the Exon algorithm.

For simplicity, the following sections will merge the concept of send records and receive records to only “state”. A state mapped to a node X implies the presence of either a send record, a receive record, or both. Additionally, the type of the messages and the content of the states will not be presented as the examples would become too extensive.

### Globally unique identifiers

The original Exon library uses transport addresses as node identifiers. This is a valid solution when nodes are not expected to switch to different addresses, however, when nodes do change, this solution can no longer ensure exactly-once delivery. Furthermore, the address change may result in the inability to communicate with nodes for which the node already has an associated state.

Transport addresses cannot be used as the node identifiers since switching to a new transport address means being perceived as a new node. Consequently, instead of resorting to the existing state associated with the node, established prior to the address change, a new state is created and employed.

To solve this problem, nodes are now identified with globally unique identifiers. By utilizing these identifiers, there is no overlap in the mapping of node states when address changes happen, while also allowing access to the corresponding communication state regardless of the transport address in use.

![Untitled](Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20cbcfb8fb630c459a96ce290b6358d259/Untitled%201.png)

The figure above aids in understanding the reason behind the inability of transport addresses to serve as node identifiers. Two nodes, A and B, exchanging messages are presented. For simplicity, the states will be referred to by their associated node identifier, which in this case are transport addresses.

With the change of A’s transport address the following problems arise:

- B perceived A as a new node because a new address was detected, which resulted in the creation of a new state. The new state, 3.3.3.3, as explained in the “Node States” section is not the counterpart of the state 2.2.2.2 present in node A. Hence, exactly-once delivery guarantee cannot be assured.
- Node B is no longer able to deliver the messages it intended to deliver, as the messages are associated with a transport address that is outdated. Even if node A returns to the previous address (1.1.1.1), the correctness of the algorithm may not be assured as the state 2.2.2.2 may have been corrupted by the exchange of messages with the state 3.3.3.3.

### Associations

As the Exon library runs over UDP, transport addresses are still required for communication. To send a message, the transport address of the receiver must be known. Consequently, a structure that creates an association between the identifiers and the transport addresses (IP address + Port) was created. 

The associations’ structure can be populated/updated in multiple ways. New associations may be registered manually, obtained through a “Source of associations”, or acquired after direct contact from another Exon node. 

The new API of the library allows manually registering associations but also allows setting a “Source of Associations”. 

A source of associations can be anything as long as it implements the appropriate interface, “AssociationSource”, which ensures that the source allows querying a transport address through a node identifier, and vice-versa. Optionally, an association source may provide an “Association Notifier”, that follows the Observer pattern and allows subscribing/unsubscribing to events related to all nodes or to specific nodes. By subscribing to the association notifier, the subscribed instance of the Exon Middleware can receive updates related to nodes of interest, without needing to periodically query the associations’ source.

The associations’ structure can also be updated through direct communication with another Exon node. Every Exon message includes the identifier of the source node. (***footnote:*** The reason behind every message carrying the identifier of the source node will be explained later.) Consider that node A knows where node B is located, regardless of whether this information was registered manually or obtained through an association source, and that it sends a message to node B. As all messages contain the identifier of the source, node B will be able to create an association with the IP address and the port present in the header of the UDP datagram, and the node identifier present in the payload. This last approach is also used to update the association of a node that had its transport address changed, such as in mobility scenarios.

![Untitled](Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20cbcfb8fb630c459a96ce290b6358d259/Untitled%202.png)

### Exon Messages and Node Identifiers

To support mobility scenarios, another of the changes required to the original library was the addition of the sender and receiver identifiers in every message (***footnote:*** REQSLOTS, SLOTS, TOKEN and ACK messages include these identifiers.). The explanation of why each of these identifiers is required in every message will be provided in the following sections.

### Exon Messages and Sender Identifiers

To better understand the following explanation, it is crucial to highlight the following. Every Exon message carries the necessary information to form the sender’s association, i.e., establish an association between the sender’s transport address and the sender’s identifier. The transport address resides in the header of the UDP datagram transporting the message, while the sender's identifier is located within the message itself.

![Untitled](Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20cbcfb8fb630c459a96ce290b6358d259/Untitled%203.png)

Including the sender identifier in every message has multiple purposes. (1) Avoids the need for registering every node in a discovery service (association source). (2) It ensures that when a message is received, the message can be associated with the proper state. (3) And it enables the communication to be resumed as quickly as possible after a change of transport address.

Including the sender identifier:

1. Enables ephemeral nodes to communicate without needing to register themselves on an association source. By combining unique identifiers to map the state records with the absence of a sender identifier in every message, nodes are forced to discard any messages lacking an association (**footnote:** An association is lacking if using the transport address present on the datagram, an associated node identifier could not be found locally or in the association source as depicted in Section A of the figure above). With the inclusion of the sender identifier, a receiving node has all the information required to form an association between the transport address and the node identifier. Thus, making it possible for “client” nodes to communicate without prior registration in an association source.
2. Ensures that an incoming message is matched with the corresponding state. Since a state record is mapped using the identifier of the node involved in the communication, by including the sender identifier in every message, the messages can be matched with the proper state record. Consequently, the corruption of the state related to another node is avoided. Suppose sender identifiers are not incorporated in every message. When receiving a message, it is necessary to translate the transport address to a node identifier. This is done through a query to the local structure or the association source. The problem is that the result may be outdated. Section B, of the figure above, illustrates a situation where node A switches to the previous address of node C before the association source is notified of the address change that node C experienced. As B is not yet aware of the change, messages received from A are matched with C’s state, potentially resulting in its corruption.
3. Allows transport address changes to be detected early. All messages possess the required information to update the association of a node, therefore, a change of transport address can be detected as soon as a message sent with the new address is received.

### Exon Messages and Receiver Identifiers

The inclusion of a receiver identifier in the messages serves the purpose of enabling nodes to discard messages not intended for them, thereby preventing state corruption. Returning to the scenario presented in figure ??, we can observe that after node A switched to the previous address of C, messages from B addressed to C arrived at A, potentially corrupting A’s state associated with node B. To prevent the processing of messages not directed to the node, the receiver identifier is included in all messages.

### Future work

Including the sender and the receiver identifiers, which are defined as strings, in every message has a significant overhead. With this being said, finding a way to minimize such overhead may be a good optimization to look into in the future. A potential solution could consist of the use of locally unique identifiers. When a node A sends a message to a node B for the first time, node B generates a locally unique identifier and assigns it to node A. This new identifier would then be shared with A, allowing both nodes to use this identifier to identify node A. The same process would be executed to define the identifier for node B. These identifiers do not need to resemble the globally unique identifier, thus, a minimal amount of bytes could be used to represent them, such as an unsigned integer of 4 bytes.

<b style="color:red">EMBORA IMPROVÁVEL, ESTA SOLUÇÃO NÃO FUNCIONA JÁ QUE SERIA POSSÍVEL POR COINCIDÊNCIA UMA MENSAGEM CHEGAR COM IDENTIFICADORES LOCAIS VÁLIDOS, TANTO O SENDER ID COMO O RECEIVER ID.</b>

### Talk briefly about solutions explored before that did not work??

## Close middleware

A implementação original do Exon tem como foco a prova do correto funcionamento do algoritmo com garantia de entrega exactly-once, e portanto não contempla um mecanismo de fecho. No entanto, como um mecanismo de fecho é essencial para qualquer programa consiga terminar de forma graciosa, este mecanismo foi adicionado.

A API da biblioteca inclui agora mais dois métodos. Ambos despoletam o encerramento do middleware Exon. A diferença está em um ser bloqueante, logo espera que o procedimento de encerramento acabe antes de retornar, e o outro ser não bloqueante, retornando imediatamente após iniciar o procedimento de fecho.

Para implementar este mecanismo foi necessário substituir algumas estruturas de dados preparadas para efeitos de concorrência pelas suas variantes normais, de modo a possuir um controlo maior sobre o período de contenção das estruturas. Foi também necessário criar uma variável relativa ao estado de fecho com o seu próprio *lock*. Os estados possíveis são: RUNNING, CLOSING e CLOSED. Uma instância do middleware Exon é criada com o estado RUNNING indicando que a instância está a funcionar normalmente, permitindo o envio e receção de mensagens. Quando um dos métodos de fecho é invocado, o estado é mudado para CLOSING, deixando de ser possível enviar mensagens e sendo apenas permitido receber mensagens. O método de fecho também resulta na criação de um evento de fecho (CloseEvent) por parte da thread que executa o algoritmo (algoThread). O evento de fecho é reagendado até a algoThread verificar que o estado da instância é favorável ao fecho conclusivo. Para o estado ser favorável ao fecho, não podem existir registos de envio nem de receção e não podem haver mensagens por processar. Estando estas condições reunidas, o estado é mudado para CLOSED, a algoThread fecha o socket UDP permitindo à thread responsável por ler do socket (readerThread) que pode terminar e acorda todas as threads bloqueadas à espera de receber uma mensagem, por meio de uma condição associada ao lock da estrutura de entrega de mensagens. Com a terminação das threads do Exon, o método de fecho bloqueante pode agora retornar.

# Páginas Relacionadas

[Adaptação e reestruturação do Exon (antiga)](Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20cbcfb8fb630c459a96ce290b6358d259/Adaptac%CC%A7a%CC%83o%20e%20reestruturac%CC%A7a%CC%83o%20do%20Exon%20(antiga)%201f376f30e26f40a697d6dceae380b32a.md)