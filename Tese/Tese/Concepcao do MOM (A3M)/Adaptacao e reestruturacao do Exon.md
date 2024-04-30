# Supporting mobility scenarios

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

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled.png)

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

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled%201.png)

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

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled%202.png)

### Exon Messages and Node Identifiers

To support mobility scenarios, another of the changes required to the original library was the addition of the sender and receiver identifiers in every message (***footnote:*** REQSLOTS, SLOTS, TOKEN and ACK messages include these identifiers.). The explanation of why each of these identifiers is required in every message will be provided in the following sections.

### Exon Messages and Sender Identifiers

To better understand the following explanation, it is crucial to highlight the following. Every Exon message carries the necessary information to form the sender’s association, i.e., establish an association between the sender’s transport address and the sender’s identifier. The transport address resides in the header of the UDP datagram transporting the message, while the sender's identifier is located within the message itself.

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled%203.png)

Including the sender identifier in every message has multiple purposes. (1) Avoids the need for registering every node in a discovery service (association source). (2) It ensures that when a message is received, the message can be associated with the proper state. (3) And it enables the communication to be resumed as quickly as possible after a change of transport address.

Including the sender identifier:

1. Enables ephemeral nodes to communicate without needing to register themselves on an association source. By combining unique identifiers to map the state records with the absence of a sender identifier in every message, nodes are forced to discard any messages lacking an association (**footnote:** An association is lacking if using the transport address present on the datagram, an associated node identifier could not be found locally or in the association source as depicted in Section A of the figure above). With the inclusion of the sender identifier, a receiving node has all the information required to form an association between the transport address and the node identifier. Thus, making it possible for “client” nodes to communicate without prior registration in an association source.
2. Ensures that an incoming message is matched with the corresponding state. Since a state record is mapped using the identifier of the node involved in the communication, by including the sender identifier in every message, the messages can be matched with the proper state record. Consequently, the corruption of the state related to another node is avoided. Suppose sender identifiers are not incorporated in every message. When receiving a message, it is necessary to translate the transport address to a node identifier. This is done through a query to the local structure or the association source. The problem is that the result may be outdated. Section B, of the figure above, illustrates a situation where node A switches to the previous address of node C before the association source is notified of the address change that node C experienced. As B is not yet aware of the change, messages received from A are matched with C’s state, potentially resulting in its corruption.
3. Allows transport address changes to be detected early. All messages possess the required information to update the association of a node, therefore, a change of transport address can be detected as soon as a message sent with the new address is received.

### Exon Messages and Receiver Identifiers

The inclusion of a receiver identifier in the messages serves the purpose of enabling nodes to discard messages not intended for them, thereby preventing state corruption. Returning to the scenario presented in figure ??, we can observe that after node A switched to the previous address of C, messages from B addressed to C arrived at A, potentially corrupting A’s state associated with node B. To prevent the processing of messages not directed to the node, the receiver identifier is included in all messages.

### Future work

#### Reducing overhead of sending both identifiers
Including the sender and the receiver identifiers, which are defined as strings, in every message has a significant overhead. With this being said, finding a way to minimize such overhead may be a good optimization to look into in the future.

#### Grow-only data structure
With the addition of associations, a way to prevent constantly querying the association source, but also to allow communication without the need for an association source to exist, a data structure was created to hold associations on the node. Currently, this data structure has a grow-only behavior, meaning that once it registers an association it is not removed at any point in the future. Being grow-only means that associations, no longer required, contribute to the exhaustion of memory resources.  

### Talk briefly about solutions explored before that did not work??

# Close middleware
### Problema
Um mecanismo de encerramento é essencial para qualquer programa conseguir terminar de forma graciosa. Dito isto, um mecanismo que permite tal funcionalidade foi adicionado. 

É importante mencionar que uma instância do middleware Exon não pode ser encerrada a qualquer momento. Para garantir que as mensagens são entregues exactamente uma vez, os nodos que participam na troca de mensagens possuem estados que se complementam. Se um dos nodos decidir eliminar tal estado num momento inadequado, o correto funcionamento do algoritmo já não é garantido. Portanto, a abordagem atual para o encerramento do middleware, passa por esperar por um momento em que não existe qualquer mensagem por entregar ou por receber, e só nesse momento é que o middleware encerra.
### Solução
#### Novos métodos
A API da biblioteca passou a incluir mais dois métodos, ambos responsáveis por acionar o encerramento do middleware Exon. A distinção entre eles reside no facto de um ser bloqueante, esperando que o procedimento de encerramento termine antes de retornar, enquanto que o o outro é não bloqueante, retornando imediatamente após iniciar o procedimento de encerramento.
#### Implementação

A implementação deste mecanismo começou com a criação de uma variável para o estado de encerramento, com o seu próprio *lock* para lidar com concorrência, e na criação de *locks* para certas estruturas de dados, permitindo um controlo mais preciso dos períodos de contenção das estruturas em relação ao uso de variantes thread-safe dessas estruturas.

Os estados de encerramento possíveis são: RUNNING, CLOSING e CLOSED. O estado RUNNING é atribuído na criação da instância do middleware, e indica que a instância se encontra a funcionar corretamente,  permitindo o envio e receção de mensagens. Após a invocação de um dos métodos de encerramento, o estado passa para CLOSING. A partir deste momento, o middleware recusa novos pedidos de envio de mensagens, e apenas permite que mensagens sejam recebidas. O acionamento do mecanismo de encerramento resulta na criação de um evento de fecho (CloseEvent) que se repetirá periodicamente até que a instância se encontre num estado favorável para o encerramento conclusivo. O estado favorável ao encerramento consiste na ausência de registos de envio e de receção, para além da falta de mensagens para processar. Ao ser processado um evento de fecho, se estas condições estiverem reunidas, o estado é alterado para CLOSED, o socket UDP é fechado, o que resulta na terminação da thread de leitura, e por meio de condições, associadas aos locks das estruturas de dados, todas as threads cliente que se encontrem a aguardar por algum recurso do middleware são acordadas para poderem verificar que a instância está encerrada. Por fim, a thread responsável pela execução do algoritmo termina, permitindo que o método de encerramento bloqueante possa retornar.

Mesmo que a instância se encontre fechada, mensagens podem continuar a ser recebidas até que a queue de mensagens por entregar fique vazia.

### Riscos da solução atual e Trabalho futuro

Embora a implementação atual garanta que apenas é encerrado o middleware após serem enviadas e recebidas todas as mensagens, com a garantia de entrega Exactly-Once, existem alguns riscos acoplados com o encerramento de uma instância do middleware Exon:
1. Se a instância for terminada e iniciada de seguida, existe o risco de mensagens duplicadas lhe serem entregues, possibilitando a criação, não pretendida, de registos localmente, ou até mesmo na corrupção de registos.
2. Embora a instância possa não conter registos localmente, as instâncias com que comunicou podem conter registos que lhe estão associados e que não é possível eliminar.
		`a.` Mensagens que têm a instância como destino, mas cuja tentativa de comunicação apenas foi iniciada após a terminação da instância, não conseguirão ser entregues a não ser que a instância volte a ser iniciada.
		`b.` Registos de receção apenas são esquecidos quando não possuem slots, logo, se uma mensagem responsável por eliminar os slots não utilizados não for recebida (porque se perdeu), então esses registos não serão eliminados até que a instância volte a ser iniciada.

A corrupção de registos, risco 1, é facilmente mitigado através da persistência do relógio local da instância.

A solução para o risco 2.a. consiste em apenas acionar o mecanismo de encerramento em instâncias efémeras e quando é garantido que não existirá mais qualquer tentativa de comunicação com a instância.

A solução para o risco 2.b. pode passar por aguardar um certo intervalo de tempo, período de resfriamento, sem que exista qualquer atividade. Após serem reunidas as condições de encerramento, aguarda-se um período de tempo, antes de encerrar definitivamente a instância, com o objetivo de esperar por mensagens duplicadas e cancelar os seus efeitos. Não existindo a necessidade para um encerramento definitivo, i.e., se o intuito é eventualmente reiniciar a instância, então a persistência do relógio local seria suficiente, e não seria necessário esperar um período de resfriamento. 

<span style="color:red">
Persistência do clock faz sentido.
Tempo de resfriamento no Exon é inútil. No contexto exactly-once não existem assunções de tempo, logo não existe período de resfriamento que se possa aguardar na esperança que o outro lado elimine o registo que já não será eliminado. Para garantir que efetivamente os registos são eliminados, então deve-se fornecer mecanismo de administração que permite eliminar registos. Assim, a camada superior após receber uma mensagem que indique que não haverá mais contacto por parte do nodo transmissor, pode esperar um período de tempo para possíveis duplicados cheguem e só depois eliminar o registo explicitamente.</span>



# Recibos de receção

### Problem
A recurrent problem associated with messaging systems is knowing if a message has arrived at the destination. The solution requires the receiver to send an acknowledgment message (ACK) confirming the arrival of the message. Since the Exon middleware already receives such confirmation, by offering the possibility of requesting **reception receipts**, the Exon middleware avoids additional overhead by preventing the upper layer from the necessity of sending an acknowledgment message over the Exon transport.

### Solution
Essentially, the designed solution for this problem consists in generating a unique message identifier for outgoing messages. These identifiers are provided to the upper layer as the return value of the send() methods. If the upper layer requested a reception receipt, then, after the Exon confirms the reception of the message at the destination, a reception receipt is emitted. A reception receipt is the identifier of the message generated by the send() method. The upper layer can then poll reception receipts from a queue, and verify which messages have been received by the destination.
#### Message identifiers
To provide receipts, the first step required is the creation of locally unique identifiers for the messages. Since the identifiers will only be used locally, there is no need to guarantee that the message identifiers are unique across all the nodes.

The simplest and most efficient solution, regarding computational costs, consists in using a circular counter. By using a sufficient amount of bits for the counter, it is possible to use the counter in a circular way, without risking the attribution of an identifier to more than one message simultaneously. A *long* value (64 bits) was chosen to represent the counter. Having in mind the Exactly-Once Delivery, theoretically, a message may never be delivered, therefore, no amount of bits would be enough to ensure local uniqueness. However, the amount of messages that a *long* counter allows to send is huge, consequently, the probability of message identifiers overlapping is immensely low, and thus, was deemed acceptable for the purpose.
#### Reception Receipts
To provide reception receipts the following modifications were required:
- send() methods allow specifying the will to receive a reception receipt for the message;
- send() methods generate and return unique message identifiers which can be used to track the respective reception receipt.
- A limitless queue was created for receipts.
	- As messages can only be delivered one time, the reception receipts can only be generated once. Therefore, the queue must be limitless. Furthermore, the upper layer may choose if receipts should be generated, and so, it has the responsibility of polling the receipts it requested and preventing memory exhaustion.
- The identifiers of the messages must now stick with the payload (client message) until the acknowledgment of reception arrives. This is required to emit the reception receipt.
- Several methods were created to allow polling receipts:
	- `takeReceipt()` : Waits for a receipt to be available and returns it.
	- `pollReceipt()` : Checks if there is a receipt available. If there is, returns it. Otherwise, returns null.
	- `pollReceipt(long timeout)`: Waits a given amount time for a receipt to be available. Returns the available receipt, or 'null' if the time expires.
	- `pollSpecificReceipt(MsgId receipt)`: Checks if the specified receipt is present in the receipts queue. If it is, removes the receipt from the queue and returns 'true'; otherwise, returns 'false'.
# Eliminação de registos <span style="color:red">(É trabalho futuro?)</span>

**As assumpções da tese assumem que não existem crashes e que será utilizado num ambiente seguro (sem nodos maliciosos), portanto, admitindo uma programação correta, não deve ser necessário recorrer a estes métodos para eliminar registos de nodos que despoletam a criação de um registo num nodo remoto e depois desaparecem de forma não expectável.**

Para administração, a possibilidade de eliminar registos forçosamente é algo que pode ser desejável. No entanto, esta funcionalidade possui riscos inerentes. Como explicado previamente, os registos funcionam em pares, um local e um remoto. A eliminação de um registo, num momento inadequado, resulta no mau funcionamento do algoritmo entre o nodo que eliminou o registo e o nodo associado ao registo. Por consequência, esses nodos deixam de conseguir comunicar. Se a camada superior pretender utilizar tais métodos, então deve garantir que os utiliza nas situações devidas, ou assegurar que consegue recuperar das possíveis consequências.

# Controlo de fluxo

A implementação original do protocolo Exon já emprega um mecanismo de controlo de fluxo. O mecanismo tem como base duas variáveis: P e N. **P** representa o número de mensagens que podem estar em trânsito, num dado momento, para um certo nodo. **N** representa o número de envelopes máximo que um registo de envio pode ter num dado momento. Como um envelope é necessário para enviar uma mensagem, a variável N, que limita o número de envelopes que se pode ter em posse, é efectivamente a variável que define o controlo de fluxo. No momento, a variável N é um múltiplo da variável P, com o intuito de prevenir a interrupção da transmissão por falta de novos envelopes.

Agora que a base do mecanismo de controlo de fluxo foi explicada, podemos passar a apresentar as razões pelas quais é necessário alterar este mecanismo:
1. Para calcular o valor P é utilizado o protocolo de transporte TCP, são realizados 100 round-trips para calcular o RTT médio e 10000 one-trips para calcular a bandwidth. Após obter estes valores, o valor de P é calculado através da seguinte fórmula: $$ P = RTT * bandwidth / message\_size $$ Como podemos perceber, isto resulta num overhead inicial extremamente grande.
2. Este cálculo é realizado apenas com o primeiro nodo com que a comunicação é feita. Logo, nodos que se encontrem sobre condições de rede diferentes, não terão um valor de P e N ajustados às suas situações.
3. Os valores de P e N não são atualizados. As condições de rede sobre as quais os nodos atuam podem não se manter iguais ao longo do tempo, logo, ajustar os valores em função das mudanças apresentadas é algo desejável.

**Problema:** É necessário realizar a concepção de um mecanismo de controlo de fluxo adaptável a diferentes condições de rede e às mudanças que estas possam exibir, para além de não exigir um overhead inicial para estimar a largura de banda para cada nodo.

**Solução:** Este problema, embora relevante, não é o principal objetivo desta dissertação. Dito isto, no momento, assume-se que o middleware será utilizado em redes privadas e controladas, em que todos os nodos se encontram sobre as mesmas condições de rede, e deixa-se este problema para exploração futura, já que é essencial para permitir a utilização do middleware numa escala global, i.e., no nível da internet.

<span style="color:red">Ver notas da reunião 22 abril para mais informações</span>
([[Reuniao 22 abril - Recibos rececao e controlo de fluxo Exon]])

# Páginas Relacionadas

[Adaptacao e reestruturacao do Exon (antiga)](Adaptacao%20e%20reestruturacao%20do%20Exon%20(antiga).md)