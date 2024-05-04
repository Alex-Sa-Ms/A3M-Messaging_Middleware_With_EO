# Requisitos e Problemas
## Requisitos para envio de mensagens
### Permitir o envio de mensagens, de qualquer tipo, para outro nodo
- Tem de existir um formato básico das mensagens para que a mensagem seja aceitada no nodo destino. Caso contrário o nodo destino descartará a mensagem por não conseguir processá-la.
	- Criar uma classe responsável pela construção e verificação do formato das mensagens.
	- Esta classe permite assegurar que as mensagens são criadas corretamente, para além de permitir que na receção de mensagens se possa descartar as mensagens que não seguem o formato correto.
- Necessário criar uma classe que converte as mensagens para um array de bytes e as envia através de uma instância do Exon.

### Permitir enviar mensagens com um socket como destino
### Permitir enviar mensagens com um nodo como destino
### Permitir definir/alterar limite para o número de mensagens que podem estar em trânsito em simultâneo (limite global)
### Permitir definir/alterar limite para o número de mensagens que podem estar em trânsito para cada socket
### Devido à garantia de entrega Exactly-Once não podem ser descartadas mensagens
#### Problemas
1. Envio deve ser bloqueante para que as mensagens não sejam descartadas
#### Soluções
### ... 
## Requisitos para receção de mensagens
### Permitir a receção de mensagens de qualquer tipo

## Requisitos de sockets
### Permitir criar sockets
- A instância do middleware deve possuir um método para criar sockets.
### Permitir registar sockets customizados
- A operação de registo deve registar uma fábrica de sockets (do tipo customizado) associando esse socket a um identificador que permitirá invocar o método de criação de sockets da instância do middleware.

### Enviar mensagens de qualquer tipo (para socket ou para nodo)
### Enviar mensagens por um socket
- Como é que um socket pode enviar mensagens?
	- Necessário contactar o sistema de gestão de mensagens (SGM) para enviar uma mensagem.
- Como é que se pode contactar o SGM para enviar uma mensagem?
	- Contactar directamente o SGM ou utilizar qualquer classe que sirva de ponte para o SGM.
	- O SGM deve ter métodos que permitam submeter mensagens para envio.
- Como é que se exige que o socket utilize um desses métodos de contacto e que não possa alterá-lo?
	- Exigir não dá, mas dá para pelos menos garantir que o objeto existe e que não pode ser removido.
	- Através de hierarquia de classes. Definindo uma superclasse com variáveis finais para os atributos/objetos obrigatórios.
### Receber mensagens
- Como é que um socket pode receber mensagens?
	1. ~~Precisa requisitar uma mensagem ao SGM~~
		- Exige uma thread por cada socket para que o sistema consiga ser reactivo, logo não é uma boa solução para atingir eficiência.
	1. **Ou** ser informado pelo SGM da chegada de uma mensagem.
		- Boa solução. Não exige uma thread por cada socket à espera que uma mensagem seja recebida. 
		- Uma única thread pode notificar o socket da chegada e executar a lógica específica do socket para cada mensagem recebida.  
- Como é que o socket pode ser informado da chegada da mensagem?
	- O socket precisa de ter um método que permite notificar a chegada de mensagens direcionadas para esse socket.
- Como é que se exige que um socket possua esta método para notificação?
	1. Deve implementar uma interface e ser registado no SGM.
	2. **Ou,** definindo uma classe Socket abstrata e definir esse método como abstrato.
### Criação do socket ficar conectada à instância do middleware correta
### Como é que se pode exigir um comportamento inicial por parte dos sockets?

### Como é que se pode criar forwarders/dispatchers?
### É possível evitar handshake entre sockets?
### Como exigir certos comportamentos?
Definir esses comportamentos em camadas responsáveis e que não podem ser expandidas em runtime. Por exemplo, para criar um socket. Criar uma instância e iniciar as suas variáveis não é suficiente. O middleware precisa que este socket seja registado. Logo, o middleware deve possuir um método que permite criar qualquer tipo de socket.
### O que fazer a mensagens cujo socket destino não existe?



---

Separador entre problemas e o resto do documento

---

# Descrição
- Nodos são contentores de sockets.
- Cada socket possui um identificador que o distingue unicamente dentro do nodo a que pertence. Designemos este por *LocalSocketID*. 
- O identificador global de um socket, *GlobalSocketID* ou *SocketID*, corresponde à combinação do identificador do nodo a que o socket pertence (NodeID) com o identificador local do socket (LocalSocketID).
- A comunicação entre sockets será realizada utilizando *SocketID*s.
- Como o protocolo de transporte utilizado é do tipo *unicast*, todas as mensagens devem possuir a identificação do socket emissor, para efeitos de backtrace, e a identificação do socket destino, para que a mensagem possa ser entregue corretamente quando chegar ao nodo destino.
- Essencialmente, um socket genérico é responsável por enviar mensagens para sockets e por servir como uma *message box* ou *incoming queue*, permitindo assim a receção de mensagens.


# Ideias
## Especialização do socket

### Ideia 1 - Por composição 
- Os sockets genéricos podem ser utilizados para construir sockets especializados que se focam em fornecer lógica associada a padrões de comunicação.
### Ideia 2 - Com gestor de contextos
#### Descrição da ideia
- Os sockets genéricos podem permitir a associação de um gestor de contextos (ContextManager).
- Cada socket especializado deve ter o seu próprio gestor de contextos, que no fundo será uma fábrica de contextos, e que serve para direcionar as mensagens recebidas para os respetivos contextos.
#### Problemas
- Como modificar/restringir a API? Por exemplo, impedir sends, ou impedir receives, ou permitir efetuar subscricoes, etc.



- Utilizar contextos de comportamento à parte.
- Também deve ser possível que nodos troquem informação entre si, para efeitos de controlo, mas não sendo disponibilizada tal funcionalidade para a API. Isto pode ser facilmente realizado ao criar sockets com identificadores especiais. Por exemplo, no MQTT, tópicos começados com '$' são reservados a uso interno.


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