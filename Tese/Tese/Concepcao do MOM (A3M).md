# Nome do Middleware

**Alexandre Martins’ Messaging Middleware →AMMM → A3M**

# Páginas associadas

[Adaptacao e reestruturacao do Exon](Adaptacao%20e%20reestruturacao%20do%20Exon.md)

[Concepção de socket genérico](Concepção%20de%20socket%20genérico.md)

[Concepção de controlo de fluxo](Concepção%20de%20controlo%20de%20fluxo.md)

[Concepção de administração e encerramento gracioso](Concepção%20de%20administração%20e%20encerramento%20gracioso.md)

# Plano de concepção

1. Selecção de funcionalidades
2. Concepção de socket genérico
    - Concepção da arquitectura
        - Deve permitir a implementação de múltiplos padrões sobre o socket
        - Faz sentido pensar numa abordagem tipo actores como referida pelo professor?
    - Definição da API
3. Concepção de protocolo de comunicação a nível da aplicação
4. Selecção e adaptação de padrões de comunicação
5. Identificação das modificações que devem ser feitas à Exon-lib
6. Concepção da arquitectura geral `??`
    - Definição da API
    1. Estrutura de classes, lógica, etc. Para gestão dos sockets, e implementação das funcionalidades seleccionadas.

![Untitled](Tese/Concepcao%20do%20MOM%20(A3M)/Untitled.png)

# Selecção de funcionalidades

- Tentar responder aos problemas mencionados [aqui.](https://zguide.zeromq.org/docs/chapter1/#Why-We-Needed-ZeroMQ)

## Basic functionalities

- Broker-less
- Message-based
    - If splitting and merging are done, the protocol must include a flag indicating when more parts of the message follow.
        - Pelo menos incluir esta informação no protocolo para implementação futura.
- Payload agnostic
    - Interoperable data representation if developers use tools like Protobuf.
- BSD sockets-style API
    - I think it is doable. The “connection” notion can be created by the middleware protocol.
- Programming model? `(check)`
    - Event-driven architecture
        - This may be better.
    - Thread-pooling + Event-driven architecture
        - Even when using an event-driven architecture, this may be useful to perform callbacks in parallel.
    - Modelo de programação por actores?
        - Programming in Java, so this may not be good. Actors would be threads, and therefore, having more actors than the number of cores/threads would be counterproductive due to context switches.
        - Check section 24.8 of [https://aosabook.org/en/v2/zeromq.html](https://aosabook.org/en/v2/zeromq.html)
- Global context (Allow only one instance with global state variables - **Singleton pattern**)
- Structured shutdown mechanism

## Communication/Communication Protocol functionalities

- Zero copy
    - Immutable bare message (define what the bare message is)
    - Allow the rest to be mutable: headers, etc.
    - Useful for forwarding messages, allowing headers to be modified and the content to remain unaltered.
- Ordered delivery `(check)`
    - Provided by the transport or the middleware?
    - By being provided by the middleware it is possible to define something like channels for multiplexing of data.
    
- Allow synchronous and asynchronous communication.
    - Synchronous communication:
        - Block when sending/receiving.
        - Allow setting timeouts, upon which the method returns with an error.
    - Asynchronous communication:
        - Allow receive/send operations to return immediately.
        - Perform receive/send on background thread(s)
        - For asynchronous operations allow callbacks to be provided, instead of using select/poll()-like methods. Providing a way to do both can be good as well.
    - How to do this?
        - Use flags/constants that define if the socket supports one or both methods of communication
            - Only if the behavior is similar to all protocols (messaging patterns)
        - Or, allow the methods of non-blocking and blocking send/receive operations to be overridden.
    - Allow the definition of a constant that defines if the socket supports one or both methods of communication.
    - Allow setting the type of action that should be taken when there is no more space available for the given socket (it disrespects the exactly-once semantic, but may be required for example to prevent DoS attacks). Action may be blocking of client thread, drop of message (oldest or newest), or even the execution of a callback function that may perform some filtering of messages.
        - Transport protocol must support the cancelling of messages.
- Supports the creation of communication patterns.
- Routing / Load balancing mechanisms
    - Generic, to be configurable?
    - Or, implemented over some callback that can access the socket information (such as the “connections”) and the message.
        - This option is also configurable and allows different types of routing to be reused and associated with different types of sockets.
        - The routing mechanism can also be locked to avoid misbehaviour.
    - Enable defining priorities to the different connections.
        - Para certos padrões pode ser útil poder definir um destino preferido, e se esse falhar (detetado por exemplo por heartbeats), usar outro(s) como fallback
        - Example of sending: Being in Lisbon and sending work to a server in New York instead of London. Even if the server is not busy, the difference in travel time is significant.
- For IoT
    - Minimal Packet Format
        - Inspire in MQTT and AMQP
        - Allow it to be used by limited devices (IoT).
    - Set of maximum size packet
    - Flow-control (Check socket functionalities)
- Allow the creation of brokers
    - Raw socket modes like in NNG and nanomsg to skip the semantic behaviour of the socket and expose the message structure (headers, data, etc).
        - Or, since everything will work over the same generic socket, possibly just forward messages through a “special” socket that is similar to a ROUTER.
            - Must have in mind different kinds of routing, so this might not be doable. Request-Reply may need messages to be sent using a load balancing method, and retrieved using an identifier, while publish-subscribe requires routing based on the subscription model, and another pattern may require broadcast.
            - However, thinking in a way that allows different types of routing to be implemented over a generic API might be a good idea. Something similar to AMQP 1.0?
    - Create a proxy similar to ZeroMQ’s one. 3 endpoints. The first 2 are used for exchanging the messages directly and the 3rd is for capturing the messages, maybe to perform statistics if needed.
        - Higher-level that does the process automatically (may need to check if the sockets are in RAW mode)
        - Lower-level that uses polling and allows a more custom behaviour.
    - Should messages be discarded by the proxies after a timeout?
- Time-to-live property for proxies may be useful to avoid loops, especially under an exactly-once model where a message is not lost.
- Client Take-Over (Suportar mobilidade dos dispositivos) `(Esta acho importante)`
    - Sistema de presenças?
    - Ou, simplesmente atualizar o endereço quando é detetada a mudança?
        - Usar uma mensagem específica para informar a “reconexão”
        - Implementar autenticação simples para evitar *exploits* (obviamente teria de ter um sistema mais robusto de autenticacao, e poderia ser ativado ou desativado para evitar overhead em redes locais e privadas)
        - Com o Exon é facil pq funciona sobre UDP e se não for feita a restrição do IP remoto é possível receber de múltiplos endereços diferentes. No caso de um protocolo orientado à conexão tipo TCP n seria possível. Seria preciso a exisstência de um sistema de persistência (no mínimo guardar as mensagens em queue) para que o estado seja reestabelecido com a criação de uma nova conexão e para que a conexão anterior fosse descartada.

## Socket functionalities

- Thread-safe sockets
- Flow control `(check)`
    - For backpressure. Is it possible to do something like AMQP? A mix of window-based for platform protection and credit-based for application management of the flow?
    - socket flow control based on window size for platform protection
        - if the window-based flow control is defined for connections and credit-based for contexts, then it may be useful to set a property that limits the number of connections that may be created with a socket. This is necessary to keep track of the amount of data that may be persisted, both in transport and middleware queues.
    - connection flow control based on credits for application protection (backpressure)
        - If socket contexts are used, then the credit-based flow control should be specific to the context and not the connection.
- ~~Sockets can bind to multiple endpoints~~ (Middleware será construído sobre Exon portanto não há necessidade de possuir múltiplos endpoints)
- Sockets can communicate with multiple actors (Sockets can ~~connect~~ ~~to~~ multiple ~~endpoints~~)
    - May be disabled depending on the sockets semantics
- Sockets can initiate “connections” through binding and connecting simultaneously.
    - Similar to NNG pipes.
    - “Connections” have incoming and outgoing queues / properties structures?
        - If so must have a limit established to manage the flow.
- Should it have ways to define when a connection and its state be discarded? `(check)`
    - If not, connections may stick indefinitely and render chunks of memory useless that could be used for another client
- NNG-like Socket Contexts
    - Aims to avoid the need for RAW mode sockets, as multiple independent flows of messages may happen in parallel, and demanding the re-implementation of protocol semantics for the asynchronous version.
        - Not sure if the expression “multiple independent flows of messages may happen in parallel” is correct. However, at least, the contexts can be seen as stateful processing units that can handle certain operations (depending on the protocol). For example, 2 different contexts on a req socket can send requests and receive replies without interfering with each other. However, I’m not sure that the context will receive the reply to the request it issued.
        - Providing a way to define if a specific context should receive a message or where which context should receive the reply can be useful. If none of this is provided, the message is attributed to an arbitrary context. If the context does not exist, maybe reply with an error indicating what issued the error (should be defined in the protocol of the messaging pattern).
    - Performing a ZeroMQ DEALER-like behaviour is not possible using contexts as sending a new request before receiving a reply results in the cancellation of the message. So there are still behaviours that may benefit from the use of RAW mode sockets.
    - Useful for multiplexing data using the same socket.
    - Implemented over the streams made available by the middleware protocol and the generic socket.
    - Create a default context (with the associated state) for the socket, which should be used if no context is mentioned when a message is sent/received.
    - Other contexts may be created/destroyed manually, and the default context may never be destroyed.
    - As mentioned, sockets should have a default context or something similar, even if the creation of new contexts is not supported. When sending/receiving messages the headers may contain the identification of context that should is sending / or the context that should receive the message. The identification should be attached to the context rather than the pipe or socket.
- ~~Can a Stream-like feature, similar to what can be done with AMQP links, be achieved with the use of contexts?~~
    - No. But its too complicated to provide multiple connections using a single socket and provide streams that can prioritize traffic over it.
- Provide callbacks for certain events related to the creation of connections like NNG provides for pipes (Pre-add pipe to socket, Post-add pipe to socket and Post-remove pipe from socket).
- Should the socket/connection be allowed to not accept messages? `(check)`
    - Could be useful for asynchronous sends. If backpressure or socket constraints do not allow the message to be sent, an error is thrown and the caller may send the message through another socket or execute some other routine. Otherwise, i.e, if the message is accepted, the called function is responsible for the message, like discarding it, etc.
    - The acceptance or rejection is local, it does not involve the remote socket.

## Funcionalidades a discutir com o orientador `(check)`

- Suportar integração de novos protocolos de transporte para além do Exon
    - Obviamente que é necessário indicar quais as caraterísticas esperadas do protocolo. Se estas não forem fornecidas, pode levar a comportamento não definido.
    - Tipo nanomsg e NNG. Explorar como é feito, mas em princípio é apenas preciso uma interface que deve ser implementada por classes para adaptar o protocolo de transporte para fornecer a funcionalidade necessária.
    - Auto-reconnect seria necessário para protocolos orientados à conexão. Ignorar esta funcionalidade?
    - Message retries seria uma exigência do protocolo de transporte?
- Interoperabilidade
    - Dados
    - Plataforma
        - Uso de bibliotecas cross-platform no caso de ser necessário alguma.
    - Versão (É um protótipo portanto vale a pena perder tempo com isto?)
    - Linguagem de programação (Procurar sobre bindings)
    - Network
        - Exon trabalha sobre UDP portanto em principio funcionaria sobre diferentes plataformas
- Splitting and merging of large messages
- Persistence of messages **(Not mandatory)**
    - Think about how to implement persistence. Does not need to be done now, but if possible set up a foundation that allows the persistence to be built over. Thinking about the transactions may also be a good idea.
- Auto-reconnect
    - Exon is not connection-oriented.
- Message retries
    - Exon already retries sending messages, and since it does not have time restrictions, the messages will not be deleted until they are received.
    - Linked with message persistence? By providing a mechanism to persist messages, it may be possible to put messages in queues (and possibly write on the disk) and retry sending the messages in the event of a problem.
        - It is mandatory to recover from node failures.
        - It may be required to allow the creation of a connection from an existing state object.
- Toggleable batching of messages
    - May not be allowed by Exon
    - Initially off
    - When message rate increases toogle it to increase throughput

# Concepção da arquitetura geral

## Problemas

- Ordem nas mensagens
    - Alguns sockets podem necessitar que as mensagens sejam ordenadas.
    - Criar abstração de uma queue que pode ou não ser ordenada.
        - Desta forma, diferentes tipos de sockets podem ter diferentes tipos de queues implementadas: sem ordem, ordem FIFO, ordem causal, etc.
        - Uma socket que não precise de ordem pode usa uma classe que pode dar wrap a uma queue normal.
        - Uma socket que precise de ordem pode usar uma classe que dá wrap a uma priority queue em que o comparador utiliza os identificadores das mensagens para as ordenar. Esta classe wrapper apenas permitiria as ações de poll(), take() e peek() se a mensagem que está no inicio da lista corresponde com o próximo identificador de mensagem, garantindo assim ordem.
- Cancelamento de mensagens
    - **Rationale:** Enviar uma mensagem M utilizando a garantia Exactly-Once para um destino que pode nunca vir a estar online implica que não se desistirá de tentar entregar a mensagem M. Ora, se o destino permanecer sempre offline, os recursos computacionais e de memória utilizados para o envio da mensagem não serão libertados. Dito isto, acho que é desejável que a biblioteca do Exon permita que mensagens sejam canceladas.
    - **Solução:**
        1. Exon deve passar a criar identificadores para as mensagens. 
            - Apenas deve ser garantido que são únicos.
            - Não é necessário fornecer uma ideia de ordem já que o Exon não tratará de tais aspetos.
        2. Criar método de cancelamento de uma mensagem.
            - O método de cancelamento de uma mensagem apenas deve permitir que sejam canceladas mensagens presentes na queue. Estas mensagens não fazem parte de um token (não têm um envelope/slot associado).
            - Se uma mensagem já pertence a um token, então já foi enviada pelo menos uma vez, logo, já pode ter sido entregue ou virá a ser entregue. Dito isto, cancelar estas mensagens não faz sentido.
            - Deve retornar *True* se foi possível cancelar, ou *False* se não é possível cancelar.
        3. Criar método de cancelamento de todas as mensagens, endereçadas a um certo nodo, que podem ser canceladas.
            - Usa a mesma lógica do método de cancelamento individual.
- Recuperação de mensagens
    - Pode servir para redirecionar as mensagens para outro destino após a camada superior entender que o nodo recetor está inativo há mt tempo.
    - Criação de um método que devolve todas as mensagens direcionadas para um certo nodo, cuja entrega ainda não foi confirmada.
        - Deve devolver uma lista com as mensagens presentes na queue e as mensagens presentes em tokens.
- ~~Eliminação de registos de envio/receção~~
    - A eliminação de um registo é algo arriscado, já que pode apenas levar a um mau funcionamento do algoritmo acoplado ao surgimento de um novo registo após a elimminação do anterior.
    - Mesmo se o propósito da utilização destes métodos for para o cancelamento de um registo por se pensar que um nodo não está online e que não será iniciada comunicação entre os dois nodos, esta não é uma solução adequada. Consideremos que ao se eliminar o registo de envio, de forma concorrente, o outro nodo recebe um REQSLOTS e aloca os slots. Com a eliminação do registo de envio, o nodo transmissor já não tem um registo compatível para receber os slots e o nodo recetor fica com slots que não serão libertados, ou seja, resultou apenas no mau funcionamento do algoritmo.
    - No máximo deve utilizar-se o método de eliminação das mensagens em fila (que não são tokens). Isto resultará no anulamento do registo de envio já que o protocolo é oblivious e elimina os registos quando estes não são mais necessários. Mas este anulamento é feito seguindo corretamente o algoritmo.
    - ***Não serão criados os seguintes métodos devido ao risco que impõe no funcionamento do algoritmo:***
        - Criação de método para eliminação do registo de envio associado a um nodo específico.
        - Criação de método para eliminação do registo de receção associado a um nodo específico.
        - Criação de método para eliminação dos registos associados a um nodo específico, tanto o registo de envio como de receção.
- Socket generico que permite que outros sockets sejam criados por cima.
    - Necessário criar um socket com diversas funcionalidades e configurável para que possa ser utilizado em diferentes situações.
    - Sockets são criados por composição sobre o socket base.
    - Socket base apenas implementa as funcionalidades básicas que permite que lógica de padrões de comunicação sejam implementadas por cima.
    - Funcionalidades:
        - Envio de mensagens para qualquer combinação nodo-socket.
        - Receção de mensagens de qualquer combinação nodo-socket.
        - Controlo de fluxo à base de janela para receção `(falar com o Prof - Para já não contemplar isto)`
            - Global ao socket
            - Limita o número de mensagens que pode estar pendente de receção em simultâneo (implica que o Exon permita definir tal valor para que não sejam recebidas mensagens e enviados ACKs quando a janela está cheia, i.e., quando o número de mensagens na queue do cliente tem tamanho igual ao valor definido para a janela)
            - Se o limite for atingido, as mensagens são descartadas enquanto não houver mais espaço, por isso é que ser a nível do Exon é necessário, já que as mensagens são eventualmente reenviadas.
        - Controlo de fluxo por socket à base de janela para envio
            - Global ao socket
            - Teria de ser implementado pelo Exon também, já que com a garantia Exactly-Once, não é necessário que o messaging middleware envie mensagens de confirmação de chegada.
            - Basta criar um contador que incrementa quando o método send é invocado e diminui quando um token é eliminado após a receção de um ACK.
        - Controlo de fluxo à base de créditos
            - Para cada socket, i.e., são fornecidos créditos a cada socket e assim controla-se o fluxo de mensagens provenientes de cada socket.
            - Permitir o controlo de fluxo associado
        - Thread-safe sockets
        - 
- Como é que pode ser extraída a lógica de processamento para diferentes contextos parecidos com os do NNG?
- Conexão VS Encaminhamento
    - 
- Como é feito o envio de mensagens, respostas, broadcasts?
- Receber uma mensagem e ter um callback para processar parece boa ideia, no entanto, como é que se tem em consideração padrões que não permitem processamento simultâneo de mensagens?
    - Se forem usadas state machines e uma framework de IO assincrono é possivel.

## Entidades

`Fazer um modelo de dominio`

- **Nodos**
    - A entidade base do middleware designa-se de **nodo**.
    - Nodos são containers de **agentes**.
    - Os nodos são identificados por um **identificador único global**, i.e., o identificador apenas pode ser utilizado por um único nodo da topologia**.**
- **Sockets (Entidades lógicas)**
    - Sockets possuem uma queue de mensagens de entrada.
    - Sockets são entidades autónomas com capacidade de realizar tarefas de forma independente. Estes processam as mensagens de acordo com o tipo de padrão que implementam.
    - Os sockets são identificados por identificadores locais únicos que permitem distinguir os sockets dentro do nodo a que pertencem.
- **Mensagens**
    - Lista de Propriedades
        
        
        | Nome | Obrigatória | Descrição |
        | --- | --- | --- |
        | to | Sim | Identificador do agente destino |
        | reply-to | Não | Identificador do agente que deve receber respostas a esta mensagem |
        | msg-id | Não | Identificador da mensagem definido pela aplicação. Pode ser utilizado como número de sequência para definir ordem nas mensagens. |
        | correlation-id | Não | Identificador criado pela aplicação de modo a permitir que mensagens possam ser associadas. |

## Padrões de comunicação

### Free

- Caraterizado pela versão básica de um socket. Não impõe nenhuma restrição de lógica referente a um padrão de comunicação.
- Possui diversas funcionalidades que permitem a implementação de diferentes tipos de sockets:
    - `Escrever funcionalidades aqui`
    - Controlo de fluxo à base de créditos
    - …
- Permite o envio de mensagens para qualquer destino.
- Permite a receção de mensagens de qualquer fonte.
- Não impõe um padrão de envio e receção.
- As diferentes funcionalidades são ajustáveis de forma a permitir qualquer tipo de comportamento.

### Pair

- Sabendo a identificação do nodo e do agente com que se pretende falar, é apenas necessário usar essas identificações para encaminhar as mensagens.
- O objeto do agente apenas permite o envio de mensagens para o agente definido na criação do agente.
- Mensagens recebidas de outros agentes além do referido na criação são descartadas.

### Request-Reply

### Push-Pull

### Publish-Subscribe

- Como funciona o envio de mensagens para uma entidade lógica?
    - Entidades lógicas têm de ser criadas. Se uma mensagem é enviada para uma entidade lógica que ainda não existe, a mensagem não consegue ser processada. Dito isto existem duas possíveis soluções:
        1. Enviar mensagem de erro indicando que a entidade não existe e descartar a mensagem;
        2. Ou, colocar a mensagem numa lista mapeada com o identificador da entidade destino e esperar um timeout antes de enviar erro e descartar a mensagem.  
- Como implementar o Publish-Subscribe sobre este tipo de arquitetura?
    - Suponha-se um exemplo sobre a publicação periódica da temperatura em diversas regiões.
    - Assuma-se ainda que existem 3 nodos: S1, S2 e P.
    - O nodo P tem uma entidade com identificador “TempPublisher”.
    - Os nodos S1 e S2 têm, cada um, uma entidade designada de “TempSubscriber”.
    - O objeto da entidade TempSubscriber permite que sejam registados, manualmente, pares (nodeID, entityID) referentes a publishers.
        - Uma mensagem para confirmação do tipo de entidade (deve ser do tipo publisher) e da existência de tal entidade é enviada.
    - Para que possa ser feita a escolha entre realizar a filtragem por subscrição no subscriber ou publisher, pode-se fornecer duas versões para cada tipo de entidade. Ou, definir algum mecanismo de negociação. Se o publicador indicar entre as suas capacidades, a capacidade de filtrar então o subscritor não precisa de verificar o tipo de mensagens vindas desse publicador.
- Um grande problema relacionado com o uso de uma biblioteca cuja garantia de entrega é exactly-once é a persistência no envio de uma mensagem. Digamos que se tenta enviar uma mensagem para um nodo que não existe nem existirá. Essa mensagem nunca será entregue, ficando apenas consumindo recursos computacionais e de armazenamento. Portanto, é necessário encontrar um ponto no qual é permitido cancelar o envio de mensagens e descartar assim o estado.
    - Mensagens em queue podem ser eliminadas diretamente, já que ainda não tiveram um envelope associado e portanto ainda não foram enviadas uma única vez.
    - A existência de um token indica que a mensagem já foi enviada pelo menos uma vez, logo, já pode ter chegado ou irá chegar ao destino. A eliminação de tais mensagens não é aconselhada já que não é possível o recetor eliminar o slot acumulado sem que todas as mensagens anteriores tenham sido recebidas também. No máximo substituir-se a mensagem por um payload vazio e incluir um tamanho negativo a servir de flag (nas mensagens do tipo TOKEN) para indicar que apenas o slot deve ser descartado e que a mensagem vazia não deve ser entregue à aplicação.

## Concepção da arquitetura geral (ANTIGA)

Um messaging middleware é uma ferramenta que tem como objetivo facilitar o estabelecimento de comunicação entre processos. 

Para existir comunicação é necessário existir troca de informação, logo a base de um MOM é a capacidade de **enviar** e **receber** informação (por exemplo, mensagens). Para o A3M, estas funcionalidades básicas serão permitidas através da criação de um objeto **Socket**. 

Para existir troca de informação é necessário conhecer os processos com quem essa troca será realizada. Para que os processos se passem a conhecer e possam interagir, o Socket possui duas funcionalidades também essenciais: **bind** e **connect**. O **bind** serve para que um processo deixe que outros processos, que conheçam o seu endereço, o contactem. O **connect** serve para um processo contactar outro processo que efetuou a operação **bind**. 

Para que as mensagens possam ser encaminhadas de forma intuitiva para os processos conhecidos pelo Socket, aquando do estabelecimento de uma ligação entre dois processos (quando um realiza o *bind* e outro o *connect*), o Socket cria o que se designa por **Channel**. Um *Channel* é essencialmente a abstração de um canal de comunicação que permite o envio e receção de mensagens. Para isto, é necessário que um conjunto de métodos sejam implementados utilizando os objetos do protocolo de transporte preferido.

Acima falamos sobre a noção de *connect* e *bind*. Estas operações podem parecer triviais, no entanto, para um sistema de alto desempenho e confiável no que toca aos assuntos relacionados com a rede de comunicação, são operações que necessitam de ser bem pensadas. Isto, porque existem múltiplos protocolos de transporte, e cada um com as suas necessidades. Alguns são connection-oriented, como o TCP, que aquando de uma disconexão não intencional, não é possível recuperar tal conexão, sendo necessária que uma nova conexão seja criada e atribuída ao respetivo *Channel.* Outros transportes são *connectionless* e portanto não possuem uma noção de conexão, e por consequência, de desconexão. Com isto quero dizer que 

Falar de queues, quebras de comunicação,   

**Callback oriented onde for possível parece ideal para uma event-driven architecture. Pode acabar por ser um pouco difícil dar debug, mas se assumirmos tudo como um sistema de tarefas em que a tarefa carrega o callback a ser realizado é possível ter vários produtores de eventos que podem ir criando os eventos usando timers, convertem esse evento para uma certa ação tipo “verificar estado da conexão e reconectar se necessario” e dar flat a essas diferentes streams numa só para que seja possível a coexistência de tantas tarefas sem a utilização de um número absurdo de threads.**

Protocolo deve definir a necessidade de implementacao de metodos para certos frames especificos. Mensagens só são retornadas quando for recebido mesmo um frame do tipo “Mensagem”. Por exemplo, um frame “Reconnect” seria tratado por um método específico. 

# Concepção de socket genérico

## Pontos importantes a considerar

### Métodos da API

- API methods
- Sync and async methods
    - Allow choosing if both or only one of them can be used.
- Thread-safe sockets
- Flow control `(check)`
    - For backpressure. Is it possible to do something like AMQP? A mix of window-based for platform protection and credit-based for application management of the flow?
    - socket flow control based on window size for platform protection
        - if the window-based flow control is defined for connections and credit-based for contexts, then it may be useful to set a property that limits the number of connections that may be created with a socket. This is necessary to keep track of the amount of data that may be persisted, both in transport and middleware queues.
    - connection flow control based on credits for application protection (backpressure)
        - If socket contexts are used, then the credit-based flow control should be specific to the context and not the connection.
- Sockets can bind to multiple endpoints
- Sockets can connect to multiple endpoints
    - May be disabled depending on the sockets semantics
- Sockets can initiate “connections” through binding and connecting simultaneously.
    - Similar to NNG pipes.
    - “Connections” have incoming and outgoing queues / properties structures?
        - If so must have a limit established to manage the flow.
- NNG-like Socket Contexts
    - Aims to avoid the need for RAW mode sockets, as multiple independent flows of messages may happen in parallel, and demanding the re-implementation of protocol semantics for the asynchronous version.
        - Not sure if the expression “multiple independent flows of messages may happen in parallel” is correct. However, at least, the contexts can be seen as stateful processing units that can handle certain operations (depending on the protocol). For example, 2 different contexts on a req socket can send requests and receive replies without interfering with each other. However, I’m not sure that the context will receive the reply to the request it issued.
        - Providing a way to define if a specific context should receive a message or where which context should receive the reply can be useful. If none of this is provided, the message is attributed to an arbitrary context. If the context does not exist, maybe reply with an error indicating what issued the error (should be defined in the protocol of the messaging pattern).
    - Performing a ZeroMQ DEALER-like behaviour is not possible using contexts as sending a new request before receiving a reply results in the cancellation of the message. So there are still behaviours that may benefit from the use of RAW mode sockets.
    - Useful for multiplexing data using the same socket.
    - Implemented over the streams made available by the middleware protocol and the generic socket.
    - Create a default context (with the associated state) for the socket, which should be used if no context is mentioned when a message is sent/received.
    - Other contexts may be created/destroyed manually, and the default context may never be destroyed.
    - As mentioned, sockets should have a default context or something similar, even if the creation of new contexts is not supported. When sending/receiving messages the headers may contain the identification of context that should is sending / or the context that should receive the message. The identification should be attached to the context rather than the pipe or socket.
- Provide callbacks for certain events related to the creation of connections like NNG provides for pipes (Pre-add pipe to socket, Post-add pipe to socket and Post-remove pipe from socket).
- Connections (pipes) inherit the properties of the socket
- Should the socket/connection be allowed to not accept messages?
    - Could be useful for asynchronous sends. If backpressure or socket constraints do not allow the message to be sent, an error is thrown and the caller may send the message through another socket or execute some other routine. Otherwise, i.e, if the message is accepted, the called function is responsible for the message, like discarding it, etc.
    - The acceptance or rejection is local, it does not involve the remote socket.

### Atributos e propriedades

- Attributes and structure
    - In the case of defining a mute state (when limit of messages is achieved), define the socket’s connections as state machines (with locks to update the state).
    - State machines allow defining when the connections are allowed to have a certain behaviour.
    - Conditions can be used, for example, to block a client thread and wake it up when a new message can be sent.
- Associate logical names to connections to prepare for mobility scenarios.
- Allow setting custom properties
    - Helps for example in creating a new pattern. nanomsg and nng set properties related to the pattern, for example, subscribing and unsubscribing (PUB/SUB pattern) is done through properties (I think).
    - Setting of a specific property/option may have a callback associated?
        - The callbacks receive an object or list of object, which only the callback knows which types it should receive.
    - Higher-level APIs may be provided along with the lower-level APIs to aid developers in using the pre-defined sockets (of the different patterns provided).

### Outros

- Disconnection is detected by timeouts, and maintained by pings (empty messages) if needed. `(check)`
    - May be useful to trigger clean-up mechanisms, for will message deliveries, etc.
    - Event-based mechanism to inform of disconnections, etc?
- How to avoid memory exhaustion? `(falar com o prof sobre isto)`
    - Ao ser tudo baseado em exactly-once é necessário usar backpressure para evitar esgotamento dos recursos. Fazer as operaçoes *send* bloquear quando se chega a um certo número de mensagens que estão por entregar. (Tunar o control flow do Exon para ser customizável)
    - Ao definir um limite por socket tanto de envio como de rececao, é possível evitar que o nº de mensagens results numa quantidade de memória não suportada. Pode ser mais complicado limitar a receção.
        - Necessario arranjar uma solução tipo quando chega a um certo threshold todos as conexoes possuem apenas um credit que será retirado (a todas as conexoes) quando o limite for atingido.
- A connection and its state may be discarded explicitly
    - If not, connections may stick indefinitely and render chunks of memory useless that could be used for another client
- ~~Can a Stream-like feature, similar to what can be done with AMQP links, be achieved with the use of contexts?~~
    - No. But its too complicated to provide multiple connections using a single socket and provide streams that can prioritize traffic over it.

## Métodos API

- Method template
    - Signatures
        
        ```jsx
        
        ```
        
    - Description
    - Return Values
    - Errors / Exceptions

### Send / Receive

- Synchronous Receive
    - Signatures
        
         
        
        ```jsx
        // Methods performed over a socket instance *s*
        1. receive() : Message
        2. receive(timeout : long) : Message
        ```
        
    - Description
        
        Receives a message synchronously. The calling thread will block until a message is received or the provided timeout expires. If no timeout is provided, the timeout is “infinite” by default, i.e., the thread will block until a message is received. 
        
    - Return Values
        - A message received by the socket.
    - Errors / Exceptions
        
        
        | Name | Description |
        | --- | --- |
        | SocketClosed | The socket is closed. |
        | MessageSizeTooBig | The message size is bigger than the size negotiated by the peers. |
        | NotSupported | The socket does not support receiving (due to the protocol semantics). |
        | StateProhibited | The socket state does not allow, i.e. prohibits, the receiving operation. |
        | TimedOut | The operation timed out. |
- Asynchronous Receive
    - Signatures
    - Description
    - Return Values
    - Errors / Exceptions
- Synchronous Send
    - Signatures
    - Description
    - Return Values
    - Errors / Exceptions
- Asynchronous Send
    - Signatures
    - Description
    - Return Values
    - Errors / Exceptions

Methods:

- Synchronous Receive
    - Arguments:
        - Timeout: If the timeout expires before a message is received, then an exception should be thrown. If no timeout is provided, the method won't return until a message is received.
        - Timeout unit:
            - Default: milliseconds
- Synchronous Send
    - Arguments:
        - Message object (with headers, body, etc)
        - Timeout: If the timeout expires before the message is sent, then an exception should be thrown. If no timeout is provided, the method won't return until the message is sent.
        - Timeout unit:
            - Default: milliseconds
- Asynchronous Receive
- Asynchronous Send

### 

## Classes

### Class Template

- **Description**
- **Attributes**
- **Constructors**
- **Getters/Setters**
- **Other relevant information**

### Message

- **Description**
    
    Object that represents a message to be sent or a message that has been received.
    
- **Attributes**
    - Headers
    - Body
- **Constructors**
- **Getters/Setters**
- **Other relevant information**

## Exceptions

- SocketClosed
    - Socket is closed, therefore, the operation cannot be performed.
- MessageSizeTooBig
    
    Size of the message is not permitted by the parameters negotiated by the peers.
    
- NotSupported
    
    The operation is not supported.
    
- StateProhibited
    
    The current state of the object, prohibits such operation.
    
- TimedOut
    
    The operation timed out.
    

## Caraterísticas

### Send / Receive

- Must have methods that allow the sending and receiving of messages, both synchronously and asynchronously.
- Sockets must define constants that inform of the capability of sending/receiving.
    - Allowed values:
        - SNDRCV - Can send and receive
        - SND - Can send. Cannot receive.
        - RCV - Can receive. Cannot send.
        - UNLCK - Unlocked. Does not have restrictions regarding the send and receive operations. When a socket is defined with such a mode, it will skip any semantic behaviour that precedes or succeeds the action of sending/receiving a message.

### Thread-safe sockets

- The socket must make use of locks to ensure that erroneous behaviours do not take place when a socket is used by multiple threads.
- Carefully avoid deadlocks.

# Concepção de protocolo de comunicação a nível da aplicação

- Interoperable over-the-wire protocol.
    - Should contain the size of the frame. Useful for stream-oriented transport protocols.
- Should have a handshake.
    - Used to exchange metadata properties like socket-type, identity (a logical and globally unique name for routing purposes and mobility scenarios), etc.
        - The identity may be used when initiating connections with Exon.
    - Verification of the metadata properties dictates if the connection is accepted or closed. Either peer may close the connection with an error indicating why it happened.
        - Check NNG errors. And check ZMTP’s RFC.
- A “Connect” command may be used initially for a handshake to exchange connection properties, but should no longer be required, even after an abrupt disconnection. When the network connectivity is regained, the Exon protocol will automatically resend messages. If the cause of the interruption of internet connectivity is the change to another network, then Exon must resume sending the messages normally. The logical identifier should be enough for identification and to consequently update the IP to which messages should be re-routed.
- “Close” command to close a connection.
    - Flag to force disconnection and cancel ongoing messages?
    - If the flag “force” is not set, the connection should stop accepting new messages and finish sending the queued messages before the clean-up takes place.

# Seleção e adaptação de padrões de comunicação

Padrões selecionados:

- Pair (Multiplos contexts para múltiplos flows independentes de dados)
- Request-Reply
- Pipeline
- Padrão inventado para mostrar a capacidade de criar padrões arbitrários?
- Pub-Sub (só se houver tempo)
    - Topic-based
    - How to match?
        - ZeroMQ matches using prefixes.Which means that subtopics also match. May not be wanted.
        - MQTT uses wildcards to match one-level or multiple levels. This one looks better.

# Identificação das modificações que devem ser feitas à Exon-lib

- Pensar na API do Exon
    - Pelos vistos a receção das mensagens é unificada pelos diversos peers
        - Pode haver problema com ordem, com controlo de fluxo porque um peer pode estar a sobrecarregar e impedir que os outros peers sejam atendidos
        - Permitir criar instâncias diferentes do Exon-lib? Assim pode permite associar uma nova porta, e pode ter uma gestão individual. Porque pode ser necessário fazer desmultiplexação dsa mensagens.
        - E se quisermos receber apenas de um peer especifico?
- Must provide a message identifier when queueing the messages, to allow checking its state and to cancel the messages.
    - Helps the task of cancelling the delivery of specific messages.
        - If they are already in token format they may not be cancelable.
- Must support cancelling of messages. [https://www.notion.so/Concep-o-do-MOM-2ba64ccebb3e44b99bbbbd835eeb3639?pvs=4#db7e6e1d3c5141129f63baefb7ab1934](Concepcao%20do%20MOM%20(A3M).md)
    - Messages may not be able to be removed from the queues, as the slots may need to be removed.
    - Alternatives:
        - Batch slots that should be removed in a special command.
        - Or, somehow update the state so that the REQSLOTS command can result in the slots being discarded.
        - Or, change the TOKEN messages to carry a special property or flag (cannot be an empty message as it should be possible to send empty messages for ping purposes), which will trigger the token to be acknowledged and the slot to be removed.
- Client Take-over. Detect mobility scenarios. Detect and update the IP of remote peers ✅
- Customizable flow control.
    - Allow disabling.
    - Allow customizing the window.
- Force close of a connection with a peer
    - Requires a REQSLOTS that clears all slots.
    - Useful to force the shutdown of a client.
- Uses TCP to calculate the bandwidth and latency of the connection. Is there a way to do this without the extreme initial overhead connection? Also, these values are assumed to be constant. Isn’t it better to attempt to get these values through the Exon protocol only, and keep updating them so that periodically the number of slots can be adjusted to the current state of the network?
- Receive for a specific node?
    - Allows multiplexing using the same socket.
    - Also, if a Exon socket with a specific port already exists, the start method should return an exception indicating that the instance already exists. Also a method that allows the retrieval of the instance should be provided.
- Can interact with multiple peers but does not make bandwidth and latency calculations for each of them.
- Needs method to close the socket
- ~~Event-driven approach to receive messages? Or just periodically test if a receive can be performed?~~
    - Event driven may be dangerous as it does not allow for multiple threads to access the middleware safely.