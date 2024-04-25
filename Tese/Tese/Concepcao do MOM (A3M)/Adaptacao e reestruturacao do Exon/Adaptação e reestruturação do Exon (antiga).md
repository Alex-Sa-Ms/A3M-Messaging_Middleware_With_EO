# Adaptação e reestruturação do Exon (antiga)

## Supporting mobility scenarios

Supporting mobility scenarios means that nodes may freely change between different networks and maintain communication. In this section, we will talk about the changes required by the Exon protocol library to support mobility scenarios.

### Globally unique identifiers

When changing to a new network, a new transport address is attributed to the node. Since the transport addresses may change frequently, a node cannot be identified by a transport address. Through the use of globally unique identifiers, a node can be distinguished from the others, enabling the related state of communication to be accessed regardless of the underlying transport address in use.

The original Exon library uses the transport addresses as the identifiers of the nodes. As explained above, using the transport addresses as identifiers is not a viable option, therefore, the first change that took place was changing the identifiers of nodes from a combination of an IP address and a port to arbitrary strings.

Let us examine an example to understand why transport addresses cannot be used as the identifiers of the nodes.

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled%201.png)

The figure above presents a situation where two nodes exchange messages, with the exchange being started by node A. For simplicity, the states will be referred to by their key, i.e., the transport address associated. After the change of the transport address, node B can no longer deliver the messages it intended to deliver to node A, as the messages are associated with a transport address that is no longer up-to-date. Furthermore, the change of address resulted in the creation of a new state for node A, at node B, under the address 3.3.3.3. As this new state (3.3.3.3) is not congruent with the state 2.2.2.2 at node A, the exactly-once delivery guarantee cannot be assured. Additionally, all messages that node B intended to deliver to node A through the state 1.1.1.1, can no longer be delivered due to the change of IP address from node A. Even if node A returns to the previous address (1.1.1.1), node A’s state (2.2.2.2) may have been corrupted by the state 3.3.3.3 at node B. Consequently, the exactly-once delivery guarantee cannot be ensured for these messages as well.

### Associations

As the Exon library runs over UDP, transport addresses are still required for communication. To send a message, the transport address of the receiver must be known. Consequently, a structure that creates an association between the identifiers and the transport addresses (IP address + Port) was created. 

The associations’ structure can be populated/updated in multiple ways. New associations may be registered manually, obtained through a “Source of associations”, or acquired after direct contact from another Exon node. 

The new API of the library allows manually registering associations but also allows setting a “Source of Associations”. 

A source of associations can be anything as long as it implements the appropriate interface, “AssociationSource”, which ensures that the source allows querying a transport address through a node identifier, and vice-versa. Optionally, an association source may provide an “Association Notifier”, that follows the Observer pattern and allows subscribing/unsubscribing to events related to all nodes or to specific nodes. By subscribing to the association notifier, the subscribed instance of the Exon Middleware can receive updates related to nodes of interest, without needing to query periodically the associations’ source.

The associations’ structure can also be updated through direct communication with another Exon node. Every Exon message includes the identifier of the source node. (***footnote:*** The reason behind every message carrying the identifier of the source node will be explained later.) Consider that node A knows where node B is located, regardless of whether this information was registered manually or obtained through an association source, and that it sends a message to node B. As all messages contain the identifier of the source, node B will be able to create an association with the IP address and the port present in the header of the UDP datagram, and the node identifier present in the payload. This last approach is also used to update the association of a node that had its transport address changed, such as in mobility scenarios.

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Untitled%202.png)

### Exon Messages and Node Identifiers

To support mobility scenarios, another of the changes required to the original library was the addition of the sender and receiver identifiers in every message (***footnote:*** REQSLOTS, SLOTS, TOKEN and ACK messages include these identifiers.). The explanation of why each of these identifiers is required in every message will be provided in the following sections.

### Exon Messages and Sender Identifiers

The sender identifier ensures that when a message is received, the message can be associated with the proper state instead of risking the corruption of a state related to another node. It also enables the communication to be resumed as quickly as possible after a mobility scenario by updating, without delays, the transport address associated with the node identifier. Furthermore, sending the source identifier avoids the need for registering every node in a discovery service.

Let us examine an example to understand the necessity of this solution. Before diving into the example, it is essential to note that if the sender identifier is not incorporated in every message, the library must assume that a transport address remains associated with the same node identifier until an update of an association arrives having the node identifier or the transport address in question. This assumption is obviously dangerous as we will see in the following example.

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Adaptacao%20e%20reestruturacao%20do%20Exon/Adaptacao%20e%20reestruturacao%20do%20Exon%20(antiga)/Untitled.png)

For the example, consider that node A and C have been exchanging messages with node B, as it can be observed through existence of a state related to each node. 

The section A illustrates that after node C switched to different address, node B no longer accepts its messages as it does not know which node identifier is associated with the address 4.4.4.4. This example proves that the inclusion of the sender’s identifier in each message eliminates the necessity of consulting an association source or waiting for it to discover the new address of node C to resume the communication.

The section B exemplifies a situation where node A switches to the previous address of node C before the association source is notified of the address change that node C experienced. Having the assumption referred above in mind, the messages, sent by B, that have C as destination will arrive in A, potentially corrupting A’s state related to node B. Also, messages sent by A after the change of address, that arrive at B will potentially corrupt B’s state associated with C.

### Exon Messages and Receiver Identifiers

The receiver identifier ensures that a message is only processed by the node that should receive the message, thus, avoiding state corruption.

To prove why these identifiers are required let us examine some examples. 

- Example 1:
- **Simple example with only A and B, where B changes to another address. To show that a new state is created and that the previous will continue to result in retransmissions but those will never reach node B unless he changes to the previous address again.**
- **Complement the previous example with another node changing to the previous address of B.**
- **Example for why the destination address is needed.**
- **Talk briefly about solutions explored before that did not work.**
- **Make diagrams to help understand what is happening (check the diagram in the paper of Exon)**

Assume the following for both examples:

- There are 3 nodes: A, B and C.
- All nodes talk with each other.
- The node A has an initial transport address of 1.1.1.1:11111.
- The node B has an initial transport address of 2.2.2.2:11111.
- The node C has an initial transport address of 3.3.3.3:11111.

With the first example, I intend to prove the reason behind requiring every message to include the sender identifier. It is important to remember that the original Exon library uses the transport addresses to identify and access the states of communication. Considering that nodes B and C are exchanging messages with node A, and at some point in time, node C changes to another network where its new transport address is now 4.4.4.4:11111. At a later moment, node B changes to C’s previous network, and its transport address becomes C’s previous address, i.e. 3.3.3.3:11111. As node A’s address did not change, nodes B and C can still deliver their messages to node A, however, when receiving such messages, node C’s message will result in the creation of a new state associated with the new transport address, 4.4.4.4:11111, also the communication may stall, not be possible as the newly created state may not be compatible with the C’s state. The messages received from node B will be received as if they were being sent by node C, as C’s previous address matches B’s new address.    As the original Exon library uses the transport addresses as identifiers, 

- new transport address results in the creation of another state for node C
- node A would receive messages from node B as if it was node C.

# TO-DOs

- **Simple example with only A and B, where B changes to another address. To show that a new state is created and that the previous will continue to result in retransmissions but those will never reach node B unless he changes to the previous address again.**
- **Complement the previous example with another node changing to the previous address of B.**
- **Example for why the destination address is needed.**
- **Talk briefly about solutions explored before that did not work.**
- **Make diagrams to help understand what is happening (check the diagram in the paper of Exon)**

- Falar sobre os passos para passar a suportar a mobilidade
- Falar sobre o porque de ter de ser esta solucao
    - Para falar sobre o porque de ter de ser esta a solucao, é necessario falar um pouco sobre a implementacao do protocolo.
- Antes de falar sobre os cenários de mobilidade é necessário relembrar como é que o protocolo Exon funciona e em que consiste a implementação base deste protocolo.
- O parte mais importante que é necessário relembrar são as designadas *half-connections*. Existem dois tipos de half-connections: send half-connections e receive half-connections. O estados das half-connections são guardados em registos designados por *SendRecord*s e *ReceiveRecord*s.

# Problemas (Serve para mostrar exemplos de alternativas que não funcionam)

- Como obter os identificadores únicos?
    
    Para obter os identificadores únicos existem várias opções, no entanto, cada uma tem os seus pontos negativos. Obter os identificadores únicos, exige sempre mais lógica do que apenas utilizar o endpoint fonte mencionado no datagrama, logo será sempre menos eficiente.
    
    Soluções possíveis:
    
    - Usar serviço de diretorias externo
        - Necessita que um sistema de diretorias externo exista, seja mantido e seja conhecido por todos os nodos (para que estes possam conhecer os outros nodos e atualizar os endereços em caso de mobilidade).
        - Necessita que os nodos estejam registados antes da “comunicação” ser iniciada através do Exon.
        - Necessário permitir que o Exon aceda ao sistema de diretorias.
        - Para um correto funcionamento do algoritmo (assegurar Exactly-Once delivery), é necessário que o Exon ignore mensagens de nodos não registados no sistema de diretorias ou que implemente um mecanismo de “merge” de registos (SendRecords e ReceiveRecords) para reconciliar o estado após se detetar que múltiplos registos correspondem ao mesmo nodo.
            - **No caso de não estarem registados, uma solução poderia passar por existir um comando especial que requisita a identificação, e um comando especial para enviar a identificação. Neste caso apenas seria necessário atualizar a tabela de associações entre identificadores e endereços.**
    - Todos os frames carregarem o identificador do nodo
        - Não necessita que o estado seja reconciliado já que as mensagens conseguem ser sempre associadas ao registo correto.
        - Obviamente levaria a um consumo excessivo e desnecessário de bandwidth.
        - Ao existir uma solução que permite reconciliar o estado e que garanta que todas as mensagens são entregues exatamente uma vez, independentemente, do tempo que demorem a ser entregues, então essas soluções devem ser ideais, já que não exigem o consumo desnecessário de bandwidth e apenas possuem overhead quando existe a mudança de endereço e de identidade, já que exige a reconciliação do estado e o reenvio das mensagens.
    - Atribuir identificadores únicos locais automaticamente (correspondem ao endpoint do peer remoto) a peers cujo endereço não é conhecido. Permitir que a upper layer identifique os peers e atualize a identidade ou endpoint.
        - A atualização da identidade ou do endpoint, como referido acima, necessita que o estado seja reconciliado, resultando na identificação dos registos que devem ser eliminados (os que não estejam associados ao identificador único mencionado na criação da associação), a cópia das mensagens (tanto as presentes na queue como nos tokens) para o registo associado ao identificador único, no cancelamento (se necessário) de eventos associados ao identificador “errado” e na retransmissão das mensagens.
- Em cenários de mobilidade, como é que é o Exon pode garantir Exactly-Once delivery? **Identificadores únicos VS endereços de transporte**
    
    A implementação base do Exon utiliza os endpoints (par de endereço IP e porta) como os identificadores dos nodos. Se o endpoint mudar devido à mobilidade de um dos peers, já não é possível através do novo endpoint (presente no datagrama recebido) encontrar o estado ao qual o frame se refere. Portanto, o Exon encara esta mensagem como proveniente de um nodo diferente, ou seja, criado um estado novo (SendRecord ou ReceiveRecord, dependendo do tipo de frame recebido) associado a este novo endpoint.
    
    Para que seja possível encontrar o estado pretendido, é óbvio que a noção de uma identidade única e global é necessária. Dito isto, os registos (SendRecords e ReceiveRecords) devem passar a ficar associados a identificadores únicos e não aos endpoints.
    
- Porque é necessário “merge” de registos?
    - Quando um dos nodos tem o seu endpoint mudado, qualquer tentativa de comunicação resultaria na criação de SendRecord e/ou ReceiveRecord para o novo endpoint. Isto porque enquanto a atualização do endpoint não é comunicada ao sistema de diretorias, o Exon não consegue encontrar o identificador associado ao endpoint, e portanto, cria um identificador automaticamente, assumindo que este não mudará. Portanto, é necessário que o Exon seja posteriormente avisado da mudança de endereço, e que prossiga com a reconciliação do estado, i.e., que de alguma forma junte o(s) novo(s) registo(s) criados para o endpoint desconhecido com os registos associados ao identificador único.
        - Também se poderia optar por uma opção em que mensagens de nodos não registados são ignoradas, o que simplifica o processo, e deixa de ser necessário reconciliar estado.
- Como pode ser feito o “merge” de registos