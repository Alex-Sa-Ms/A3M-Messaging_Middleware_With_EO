# ZeroMQ

- ***“Limitação”:*** Sockets mutuamente exclusivos
- Noção de contexto. Contexto é depois usado para criar os sockets. Contexto é um *container* para os sockets criados, e é posteriormente utilizado para permitir a conexão *inproc.* No final é possível destruir o contexto, o que fecha todos os sockets criados com o contexto.

# Mensagens

- Mensagens são consideradas conjuntos de dados binários
- A mensagem mais simples do ZeroMQ é considerada um *frame* (single part)*.*
- Mensagens podem ter múltiplas partes (*multi-part message*), i.e., múltiplos *frames.*
- Mensagens devem ter tamanho que cabe em memória.

# Socket API

- Gestão dos *timings* de conexão, desconexão, reconexão e entrega das mensagens é gerida pelo ZeroMQ, sendo transparente ao utilizador
    - Permite sockets assíncronos
- Sockets permitem conexões muitos-para-muitos, com exceção do socket PAIR, que apenas permite um-para-um.
- Noção de *bind* e *connect*
    - Criação e destruição de queues de mensagens em função do tipo de ligação (bind/connect)
- Define um limite máximo de mensagens numa queue para serem enviadas para um determinado destino.
    - Quando atingido, o comportamento pode ir desde eliminar mensagens já enviadas, até passar o socket para um modo bloqueante (depende do tipo de socket)
    
    # Padrões de comunicação
    
    - Os padrões implementados pelo ZeroMQ são baseados em pares de sockets.
    - Padrões:
        - Request-Reply
        - Publish-Subscribe
        - Push-Pull (One-way Pipeline)
        - Exclusive Pair
        - Client-Server
        - Radio-Dish
    
    ## Request-Reply pattern
    
    - Permite conectar um conjunto de clientes a um conjunto de serviços.os
    - Baseado em Remote Procedure Calls.
    - “Distribui” a carga de pedidos (round-robin dos pedidos pelo cliente).
    - Pode ser síncrono (REQ e REP sockets) ou assíncrono (DEALER e ROUTER sockets)
    
    ### REQ socket
    
    - Utilizado por clientes para enviar pedidos a serviços
    - Padrão de Send/Receive: Send, Receive, Send, Receive, …
    - Pode-se conectar a *REP* ou *ROUTER* sockets
    - Round-robin dos pedidos entre todos os serviços conectados
    - *Reliability* com serviços com falhas não é um problema
    - Sockets compatíveis: REP, ROUTER
    
    ### REP socket
    
    - Utilizado para serviços receberem e responderem a pedidos de clientes
    - Padrão de Send/Receive: Receive, Send, Receive, Send, …
- Cada pedido recebido é respondido num formato *fair-queued* entre clientes
- A resposta é enviada para o cliente que enviou o último pedido.
    - Se o cliente já não existir, o pedido e a resposta são simplesmente descartadas.
- Sockets compatíveis: REQ, DEALER

### DEALER socket

- Fala com um conjunto de *peers* anónimos
- Mensagens recebidas são atendidas usando um método *fair-queued*
- Envia mensagens usando um algoritmo *round-robin*
- *Reliable*, porque não dá *drop* a mensagens
- Funciona como substituto assíncrono do REQ
- Padrão de Send/Receive: Não restrito
- Sockets compatíveis: ROUTER, REP, DEALER

### ROUTER socket

- Fala com um conjunto de *peers,* usando *endereçamento explícito* para que cada mensagem possa posteriormente ser retornada ao *peer* específico.
- Substituto assíncrono do REP
- Ao receber uma mensagem, dá *preprend* do *routing id* do peer antes de a passar para a aplicação
- Mensagens recebidas são fair-queued
- Ao enviar uma mensagem, remove a primeira parte da mensagem e usa-a para determinar o *routing id* para onde deve encaminhar a mensagem
- Padrão de Send/Receive: Não restrito
- Sockets compatíveis: ROUTER, REQ, DEALER

---

## Publish-subscribe

- Pub-sub filtering is done at the publisher side instead of subscriber side
- Utilizado para distribuição *one-to-many* de dados, de um *publisher* para múltiplos *subscribers.*
- Sockets: PUB, XPUB, SUB, XSUB
- Baseado em tópicos
- É usado o sistema de *multipart messages*, enviando-se no 1º frame, o tópico, i.e., antes da *payload message*
- Um *subscriber* deve subscrever o tópico para receber as mensagens relacionadas com este.
- O sistema de tópicos funciona à base de prefixos. Subscrevendo o tópico “topic”, seriam recebidas mensagens dos seguintes tópicos: “topic”, “topical”, “topic/subtopic”; mas não seriam recebidas mensagens dos tópicos “topi” e “TOPIC”. (As comparações são byte-wise)

### PUB socket

- Usado para um *publisher* distribuir dados
- Mensagens são distribuídas para todos os *peers* conectados
- Este socket não pode receber qualquer mensagem.
- Sockest compatíveis: SUB, XSUB

### SUB socket

- Usado para subscrever dados distribuídos por um *publisher*
- Inicialmente não tem nenhuma subscrição
- Apenas consegue subscrever, não consegue enviar mensagens
- Sockest compatíveis: PUB, XPUB

### XPUB socket

- Mesmo que o PUB mas expõe as subscrições em forma de mensagem
- Sockest compatíveis: SUB, XSUB

### XSUB socket

- Mesmo que o SUB mas a subscrição é feita através do envio de mensagem de subscrição para o socket
- Sockets compatíveis: PUB, XPUB

***Nota:*** usando a combinação de XPUB e XSUB é possível criar um proxy ao qual os *publishers* e *subscribers* se ligam. Desta forma, os *publishers* não precisam de saber quem são os *subscribers* e vice-versa.

---

## Push-Pull (Pipeline) pattern

- Orientado para distribuição de tarefas, onde tarefas (trabalho) é empurrado para vários *workers,* que posteriormente enviam os resultados para um ou mais colectores.
- Não descarta mensagens a não ser que um nodo seja desconectado de forma não expectável

### PUSH socket

- Envia mensagens para um conjunto de PULL *peers*
- Round-robin para envio das mensagens
- Não recebe mensagens

### PULL socket

- Recebe mensagens de um conjunto de PUSH peers
- Atende as mensagens usando um algoritmo de fair-queuing
- Não envia mensagens

---

## Exclusive pair pattern

- Usado quando dois peers são arquituralmente estáveis
    - O que geralmente limita o uso de PAIR para comunicação num único processo, para *inter-thread communication*

### PAIR socket

- Apenas pode estar conectado a um único peer ao mesmo tempo
- Não é realizado nenhum roteamento ou filtro de mensagens
- Operações de envio são bloqueadas até um peer estar conectado, logo as mensagens não são descartadas
- O facto de não conseguir realizar auto-reconnect, acompanhado do facto de não aceitar novas conexões enquanto as anteriores não tiverem sido terminadas, torna este socket impróprio para uso com TCP na maior parte dos casos.

---

## Cliente-Server pattern

- Permite que um SERVER fale com múltiplos CLIENTs

### CLIENT socket

- Comunica com um ou mais servers
- Se conectado a múltiplos peers, distribui as mensagens usando um algoritmo round-robin.
- Lê de forma justa, em turnos por cada peer.
- *Reliable,* não dá drop a mensagens em casos normais.
- Só aceita mensagens quando já houver uma conexão estabelecida.

### SERVER socket

- Fala com um ou mais clientes
- Cada mensagem enviada tem um destino (cliente) específico.
- Apenas pode responder a uma mensagem recebida (o cliente deve ser sempre o que inicia a conversa)
- As mensagens recebidas têm um *routing id*, utilizado para depois responder ao cliente (se não tiver este *routing id,* o envio de mensagem falha)

 

---

## Radio-dish pattern

- Usado para distribuição de dados do tipo *one-to-many,* i.e., de um único *publisher* para múltiplos *subscribers* num formato *fan out.*
- Este padrão utiliza grupos (em vez dos tópicos de Pub-Sub)
- Dish sockets juntam-se a um grupo e cada um recebe uma mensagem dos Radio sockets que se juntam ao mesmo grupo
- Os grupos são definidos por strings
- A correspondência aos grupos é feita utilizando comparação a nível dos bytes (*byte-wise*)

### RADIO socket

- Usado por um *publisher* para distribuir dados
- Cada mensagem pertence a um grupo
- Mensagens são distribuídas por todos os membros de um grupo
- Este socket não implementa a operação de *receive*

### DISH socket

- Usado por um *subscriber* para subscrever grupos e receber as mensagens distribuídas por um RADIO
- Inicialmente não está subscrito em nenhum grupo
- Este socket não implementa a operação *send*

# Boas funcionalidades

Specifically:

- It handles I/O asynchronously, in background threads. These communicate with application threads using lock-free data structures, so concurrent ZeroMQ applications need no locks, semaphores, or other wait states.
- Components can come and go dynamically and ZeroMQ will automatically reconnect. This means you can start components in any order. You can create “service-oriented architectures” (SOAs) where services can join and leave the network at any time.
- It queues messages automatically when needed. It does this intelligently, pushing messages as close as possible to the receiver before queuing them.
- It has ways of dealing with over-full queues (called “high water mark”). When a queue is full, ZeroMQ automatically blocks senders, or throws away messages, depending on the kind of messaging you are doing (the so-called “pattern”).
- It lets your applications talk to each other over arbitrary transports: TCP, multicast, in-process, inter-process. You don’t need to change your code to use a different transport.
- It handles slow/blocked readers safely, using different strategies that depend on the messaging pattern.
- It lets you route messages using a variety of patterns such as request-reply and pub-sub. These patterns are how you create the topology, the structure of your network.
- It lets you create proxies to queue, forward, or capture messages with a single call. Proxies can reduce the interconnection complexity of a network.
- It delivers whole messages exactly as they were sent, using a simple framing on the wire. If you write a 10k message, you will receive a 10k message.
- It does not impose any format on messages. They are blobs from zero to gigabytes large. When you want to represent data you choose some other product on top, such as msgpack, Google’s protocol buffers, and others.
- It handles network errors intelligently, by retrying automatically in cases where it makes sense.

# Padrões avançados (por fazer)

# Referências

[https://zeromq.org/](https://zeromq.org/socket-api/)

[https://zguide.zeromq.org/docs/](https://zguide.zeromq.org/docs/)

[https://libzmq.readthedocs.io/en/latest/zmq.html](https://libzmq.readthedocs.io/en/latest/zmq.html)

[https://libzmq.readthedocs.io/en/latest](https://libzmq.readthedocs.io/en/latest)

[https://rfc.zeromq.org](https://rfc.zeromq.org/)

[http://wiki.zeromq.org/whitepapers:messaging-enabled-network](http://wiki.zeromq.org/whitepapers:messaging-enabled-network)

[https://250bpm.com/blog:39/](https://250bpm.com/blog:39/)

[https://250bpm.com/blog:23/](https://250bpm.com/blog:23/)

[https://aosabook.org/en/v2/zeromq.html](https://aosabook.org/en/v2/zeromq.html)

# TODOs para a tese

- Ler capitulos 3 a 8 do ZeroMQ guide.
- Falar sobre a arquitetura ([https://aosabook.org/en/v2/zeromq.html](https://aosabook.org/en/v2/zeromq.html), seccao 24.7) e ([http://wiki.zeromq.org/whitepapers:architecture](http://wiki.zeromq.org/whitepapers:architecture)), procurar mais sites sobre a arquitetura
-