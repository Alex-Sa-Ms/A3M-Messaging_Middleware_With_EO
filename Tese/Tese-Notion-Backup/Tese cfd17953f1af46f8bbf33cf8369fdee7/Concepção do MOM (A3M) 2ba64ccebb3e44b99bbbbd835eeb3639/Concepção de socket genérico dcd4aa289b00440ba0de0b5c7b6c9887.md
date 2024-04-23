# Concepção de socket genérico

No ponto de concepção atual, a biblioteca do Exon foi alterada para que sejam utilizados identificadores globalmente únicos para identificar e comunicar com nodos.

A ideia agora é tornar os nodos em contentores de sockets. Os sockets são entidades lógicas que possuem identificadores únicos locais, e que são efetivamente os transmissores e recetores de mensagens. 

# Requisitos funcionais

- Permitir enviar mensagens utilizando como destino um par de identificadores: identificador do nodo e identificador do socket.
- Permitir receber mensagens de qualquer fonte, devolvendo sempre o par que descreve a fonte com a mensagem.
- Permitir enviar mensagens com identificadores de sequência, para que seja possível ordenar as mensagens no destino.
- Ordenar mensagens recebidas caso estas possuam identificadores de sequência.
- Deve permitir que sejam registados sockets (pares idNodo-idSocket).
    - Pode ser util, por exemplo, para padrões como broadcast, push-pull, etc.
    - Ou será que é melhor ser registado numa estrutura própria definida pelo socket especializado? No caso de publish-subscribe é necessário a existência de uma estrutura especializada.
- Deve permitir consultar sockets registados.
- Deve permitir definir opções.
    - No Publish-Subscribe do NNG, usam-se as opções para criar e eliminar subscrições, como é que isto pode ser feito? Sockets especializados devem criar um método de set a opções que é invocado após verificar se a opção não é uma opção default do socket genérico?
- Deve permitir consultar as opções.

# Interfaces

## Private

- Add message to incoming queue

## Public

- Create
- Close `(Pensar mais tarde quando já tiver o prototipo decente)`
- Send message to an socket *(synchronously / asynchronously)*
    - Adds message to the middleware message management system and then returns.
    - Synchronous means that the method will block while the flow control does not allow the message to be sent.
    - Asynchronous means that an error is thrown if the method should have blocked.
    - Synchronous with timeout: if the timeout expires the same error thrown for the asynchronous method is thrown.
- Receive message *(synchronously / asynchronously)*
- Get options
- Set options

***Relembrar Polling***

# Specialized sockets

Assume that a *DestinationID* class exists and has the following as attributes: node identifier and socket identifier.

## Pair

**Example:**

- Two nodes are created, one for each device. Let’s call them A and B. Then, the idea would be to invoke the constructor of a PAIR socket in each node giving them the identifier “pair” (remember that the identifiers of the sockets only need to be locally unique as the identifier of the node is used to distinguish remote sockets). The constructor of the socket would have as arguments: the local identifier of the socket and the pair nodeID-socketID that identifies the peer with which the communication is supposed to occur. The pair nodeID-socketID would be saved in order to detect which messages are being received from the appropriate peer. Any other messages would be discarded and an error message would be sent to those peers indicating that they are not the counterpart of that socket. The methods that provide sending functionality for this type of socket do not require the destination to be specified as there is only one destination and it was specified at the creation of the socket.

**Inteface:**

(Assume DestinationPair)

- PairSocket :: new (identifier : String, destID : DestinationID)
    - *identifier* is the identifier of the new socket. If the identifier is null or exists locally, then an error will be thrown.
    - *destID* is the pair of node id and socket id that identifies the counterpart. If the node ID is null, then it is assumed that the message should be sent to a local socket.
        - If the own node ID is given it should also redirect the value to the local socket.
- receive() : Msg *[can have variants for synchronous and asynchronous calls]*
- send(msg : Msg) *[can have variants for synchronous and asynchronous calls]*

## Request-Reply

**Example:**

- The Request-Reply pattern has two types of sockets: Requester (Client) and Replier (Server).
- The pattern has two variants:
    - Synchronous:
        - Requesters need to send a request before attempting to receive, otherwise an error will be thrown. To clarify, the requester must have a pending request to attempt to receive a response.
        - Requesters can only send one request at a time. Sending a new request means discarding the previous response, but will not cancel the action of sending the previous request if it has not yet been sent.
        - Repliers process and answer requests sequentially.
    - Asynchronous:
        - Requesters may send multiple requests before receiving a response  `(Should this really be a variant? The generic socket can do this.)`
        - Repliers attend to multiple requests simultaneously.
            - This is done through contexts. **Why are the contexts required exactly? (Check NNG)**

## Push-Pull

## Publish-Subscribe