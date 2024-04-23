# NNG

[https://github.com/nanomsg/nng](https://github.com/nanomsg/nng)

[https://nng.nanomsg.org/](https://nng.nanomsg.org/)

[https://nanomsg.org/gettingstarted/nng/index.html](https://nanomsg.org/gettingstarted/nng/index.html)

[https://github.com/nanomsg/nng?tab=readme-ov-file](https://github.com/nanomsg/nng?tab=readme-ov-file)

# Table of contents

# Overview

- NNG é o sucessor de nanomsg
- É uma biblioteca de comunicação leve, broker-less.
- Fornece múltiplos padrões de comunicação: PUB-SUB, RPC, Service discovery **(Survey?), todos os patterns de nanomsg?**

# Features

- **Reliability:** Tenta cobrir todos os casos de erro para evitar crashes.
- **Scalability:** Consegue tirar partidos de múltiplos cores através de uma framework de I/O assíncrono específica. Tira partido de uma pool de threads para dividir o trabalho sem exceder os limites do sistema.
- **Maintainability:** Arquitectura modular para ser facilmente compreendida por outros desenvolvedores. Para além, de uma extensa documentação. (Na minha opinião podia existir um get started para ser mais intuitivo conhecer o middleware.)
- **Extensibility:** Permite adicionar novos protocolos (messaging patterns?) e transportes.
- **Security:** Fornece protocolos de segurança para autenticação, encriptação e proteção contra ataques maliciosos.
- **Usability:** APIs intuitivas. Fornece a opção de separar o contexto e estado do protocolo dos sockets facilitando a criação de aplicação concurrentes.
- O mesmo socket pode conectar e receber conexões.

# Sockets

- Os constituintes de um socket são *dialers* e *listeners*, podendo ter múltiplos dialers e listeners em simultâneo.

### Dialers

- Os *dialers* utilizam um endereço, extraido do URL fornecido na criação, para iniciar uma conexão remota com um listener.
- Quando uma conexão é estabelecida com sucesso, é criado um pipe e adicionado ao socket.
    - Quando o pipe é fechado, o dialer periodicamente tentará reestablecer a conexáo. (**auto-reconnect feature**)
- A conexão inicial é geralmente síncrona (pode incluir *name resolution*), podendo ser assíncrona se a flag NNG_FLAG_NONBLOCK for definida.
    - No caso de ser síncrona, se a conexáo for rejeitada, então o método retorna um erro, e mais nenhuma ação é realizada.
    - Se for assíncrona, então o dialer tentará reconectar automaticamente se a conexão incial não for bem sucedida.
- A existência de dialers permite que certas conexões sejam fechadas quando já não são necessárias, já que o método nng_dialer_close fecha quaisquer pipes associados a este.
- nng_dial inicia o socket imediatamente. Para que não seja iniciado deve-se usar o método nng_dialer_create e depois nng_dialer_start. Isto permite que qualquer configuração seja realizada antes do dialer se tentar conectar. Após ter sido inicializado geralmente não é possível alterar a configuração.

### Listeners

- *Listeners* ficam à escuta (listen) por  pedidos de conexão no endereço especificado pelo URL na sua criação.
- Aceitam conexões iniciadas por *dialers* remotos.
- Ao contrário dos dialers, listeners podem ter criar múltiplos pipes que podem ficar abertos em simultâneo.
- nng_listen para criar e iniciar um listener. nng_listener_create e nng_listener_start para definir as configurações antes de iniciar o listener.

### Pipes

- Pipes podem ser percebidos como conexões únicas.
- Ficam associados a um listener ou dialer, e por transitividade a um socket.
- Muitas aplicações não devem necessitar de se preocupar como pipes individuais, no entanto, através de pipes é possível obter informação concreta sobre a fonte da mensagem, e possibilita um maior controlo relativamente a entrega de mensagens.
- Ao ser fechado um pipe as mensagens são possivelmente flushed ou enviadas dependendo do tipo de transporte. (**É possível definir um lingering period?**)
- Opções de configuração dos pipes são definidas a partir da configuração dos listeners e dialers antes do pipe ser criado.
- Callbacks podem ser associados aos pipes. Estes são chamados quando certos eventos acontecem. Apenas um callback pode ser associado a cada evento. Os eventos existentes são os seguintes:
    1. NNG_PIPE_EV_ADD_PRE: Depois da conexão e negociação ser completada, mas antes de adicionar o pipe ao socket.
    2. NNG_PIPE_EV_ADD_POST: Depois do pipe ser adicionado ao socket. A partir deste evento já é possível comunicar sobre o pipe com o socket.
    3. NNG_PIPE_EV_REM_POST: Este evento acontece depois do pipe ser removido do socket. O transporte pode ser fechado neste ponto, não sendo possível comunicar mais usando este pipe.
    - NOTA: Não se deve tentar aceder ao socket. É usado um lock para prevenir isto, e portanto, tentar aceder ao socket resultará num deadlock.

 

# Message Handling

- Suporta zero-copy
- As mensagens são constituídas por um *header* e um *body*. O *body* é responsável por carregar o payload do user, e o header contém informação específica do protocolo.
- Quando uma mensagem é recebida, um objeto “Mensagem” é criado. Este fica associado ao pipe de onde a mensagem foi recebida. Possivelmente para permitir enviar a resposta pelo pipe correto.
    - No entanto, nem todos os protocolos suportam a “seleção” do pipe destino. Neste casos, é possível que a mensagem não tenha um pipe associado.
- Para se enviar uma mensagem para um pipe específico (se aplicável) usa-se o método “nng_msg_set_pipe” antes de se enviar a mensagem.
- Receber uma mensagem pode ser bloqueante ou não bloqueante (NNG_FLAG_NONBLOCK).
    - Se for bloqueante, espera até uma mensagem ser recebido, ou até o timeout definido expirar.
    - Se for não bloqueante, retorna imediatamente, mesmo se não existir nenhuma mensagem para ser recebida.
    - NOTA: Obviamente, a semântica de receber uma mensagem varia dependendo do padrão de comunicação. No caso de um PUB-SUB, um socket PUB não recebe mensagens. E no caso de um REQ-REP, um REQ só pode receber depois de enviar um pedido. Nos casos, em que ser não bloqueante não é permitido, ou em alguma situação particular, não é possível fazer a operação um error é retornado.
- Ao enviar uma mensagem, o socket pode aceitar a mensagem (se for esse o caso, não se deve mexer mais na estrutura da mensagem e esta fica da responsabilidade do socket) , ou não aceitar, continuando na responsabilidade do “chamador da função” tratar da mensagem (libertar o espaço usado, enviar por outro socket, tentar novamente mais tarde, etc).
- O envio de uma mensagem pode ser bloqueante ou não bloqueante (NNG_FLAG_NONBLOCK).
- NNG permite aceder e modificar os headers das mensagens, embora estes apenas contenham conteúdo específico do protocolo. Isto é permitido, uma vez que para uma maior versatilidade do comportamento, os utilizadores podem usar os sockets no modo RAW.
    - O modo RAW permite aceder diretamente ao protocolo de transporte sem o processamente adicional feito pelo NNG. E desta forma, criar protocolos customizados.

# Asynchronous Operations

- Na maioria das situações, as aplicações só necessitam de interagir com o NNG de forma síncrona. Mesmo no casos de enviar/receber uma mensagem, a thread do cliente é bloqueada pelo menos até que a mensagem esteja na queue para entrega. As operações assíncronas fornecidas pelo NNG, são iniciadas pela *calling thread* mas o controlo é retornado imediatamente.
- Cada operação assíncrona tem uma estrutura nng_aio (única) associada.
- Após a operação assíncrona concluir (independentemente do estado de sucesso da operação), uma *callback function,* definida pelo utilizador ao criar o objeto nng_aio, é executada.
    - A callback function é executada independentemente do sucesso da operação. Portanto, é importante consultar o resultado da operação dentro desta função.
- Suporta que as operações sejam canceladas (mesmo quando já estão a ser executadas
- Permite definir um timeout máximo para a operação ser concluída.
- No momento, as operações assíncronas suportadas são: enviar mensagem, receber mensagem e *sleep.*
    - A operação *sleep* é utilizada para executar um mecanismo de forma assíncrona após um certo delay.
- Necessário ter em consideração a semântica dos sockets. Um PUB socket não pode receber mensagens, portanto tentar criar uma operação de receção de mensagem resultará num erro.

# Protocolos (Padrões de comunicação)

- Suporta uma variedade de padrões de comunicação, e ainda possibilita que os desenvolvedores criem os seus próprios protocolos.
- ***Falar do RAW mode***

## BUS Protocol

- Permite a criação de mesh networks, onde todos os peers se conectam a todos os outros peers.
- Uma mensagem enviada por um nodo é enviada para todos os peers que lhe estão diretamente conectados.
    - Peers conectados indiretamente não recebem a mensagem. Portanto, ao criar uma mesh network, é importante conectar os peers totalmente.
- Fornece best-effort message delivery, ou seja, os peers podem não receber a mensagem.
- O envio de mensagens nunca bloqueia. Se por alguma razão a mensagem não poder ser enviada, então é descartada.
- (Aviso) Não deve ser usado em contextos que necessitam de alto throughput, já que é feito “broadcast” das mensagens, i.e., uma mensagem é enviada tantas vezes quanto o número de peers diretos. Isto resulta num grande uso da largura de banda, podendo resultar em perdas das mensagens.
- Este protocolo apenas fornece um socket (BUS).

## One-way Pipeline Protocol (Push-Pull)

- Neste protocolo, *pushers* distribuem mensagens para os *pullers.*
- Cada mensagem enviada por um *pusher* é enviada para um dos *pullers* conectados. Isto é feito usando um algoritmo round-robin entre os *peers* que estão disponíveis para receber.
- Este protocolo fornece dois sockets: PUSH e PULL.
    - O socket PULL é usado para receber mensagens. Não é capaz de enviar mensagens. As mensagens vão sendo aceitadas à medida que chegam. Quando existem múltiplos peers com mensagens prontas em simultâneo, a ordem de receção das mensagens é indefinida (pode ser qualquer uma? Depende de algum mecanismo que está por baixo?)
    - O socket PUSH é usado para enviar mensagens, não sendo capaz de receber qualquer mensagem. As mensagens são enviadas para um dos *pullers* conectados, usando um algoritmo round-robin. O socket PUSH possui um mecanismo de controlo de fluxo (back-pressure) que impede o envio de mensagens quando não existem *pullers* conectados que podem receber mensagens. É possível também definir um *timeout* ao fim do qual o envio da mensagem irá falhar.
- Best-effort delivery. Não há garantias de que as mensagens vão ser entregues.
- É possível definir o tamanho (0-8192) de um send buffer para o socket PUSH. O tamanho é representado em número de mensagens que podem ser *buffered* num buffer intermédio (do socket) enquanto não existem peers disponíveis para receber as mensagens. Quando estiverem disponíveis estas mensagens serão passadas para os buffers da camada de transporte.

## PAIR Protocol

- Utilizado em situações que existem relações de 1-para-1 que devem ser exclusivas.
- Geralmente, bloqueia quando não existe um peer para receber a mensagem.
- Este protocolo apenas fornece um socket (PAIR).

## Publish-Subscribe Protocol

- Um publisher faz broadcast das mensagens para todos os subscritores. Os subscritores apenas veem as mensagens que subscreveram.
- Os subscritores é que gerem as suas subscrições e filtram as mensagens localmente.
- Este protocolo fornece dois sockets: PUB (lado do publicador) e SUB (lado do subscritor).
- O protocolo é baseado em tópicos. A filtragem das mensagens é feita ao comparar o tópico com o início do corpo da mensagem. Portanto, ao construir as mensagens é necessário ter isto em consideração.
- O socket PUB apenas consegue enviar mensagens.
- O socket SUB apenas consegue receber mensagens.
    - As subscrições são registadas/eliminadas através do método de definir uma opção do socket.
    - Este socket também fornece a opção de escolher que mensagens devem ser descartadas quando a queue ficar cheia. Permite escolher se a mensagem mais antiga é que deve ser descartada ou se as mensagens novas devem ser descartadas.

## Request-Reply Protocol

- Neste protocolo, um *request* envia uma mensagem para um *replier,* do qual espera uma resposta. A *request* é reenviada até uma resposta chegar, ou até o tempo limite para a *request* ser atingido.
- Fornece dois sockets:
    - Socket REQ
        - Usado para enviar *requests* e receber *replies.*
        - Tenta dividir o trabalho (*requests*) **pelos diferentes *repliers* conectados.
        - A não ser que esteja em RAW mode, apenas pode possuir uma request em espera a cada momento, e para receber uma resposta necessita de ter enviado uma request “ativa”.
        - Enviar uma nova *request* antes de receber a resposta, cancela a *request* anterior. O “cancelar” apenas consiste no descarte da resposta à *request* anterior.
        - Permite definir o timeout para reenvio das *requests* (a nível do socket ou contexto)*.*
        - Permite definir a unidade do timeout (Re-send tick). **
        - O protocolo deste socket utiliza um *backtrace* no header. O backtrace é um array de identificadores. Com o último identificador sendo o identificador da *request* (Request ID). Os IDs adicionais são IDs de peers, utilizados pelos devices (que fazem *forward* das mensagens). Os devices dão prepend aos ID do peer de onde receberam a *request.* As *replies* são criadas com o *backtrace* da *request* (no header), para que os devices possam dar *pop* aos IDs dos peers para saberem encaminhar as respostas.
    - Socket REP
        - Usado para receber mensagens (*requests*) e para enviar respostas.
        - Geralmente, uma reply apenas pode ser enviada depois de uma *request* ser recebida.
        - Apenas uma operação de *receive* pode estar ativa num dado momento.
        - O modo RAW do socket ignora as restrições anteriores.
- Suporta a criação de contextos para casos de uso concorrentes. Cada contexto pode ter apenas uma solicitação pendente, e opera de forma independente relativamente aos restantes contextos. As restrições de ordem das mensagens são iguais às dos sockets, com cada contexto sendo tratado como um socket separado.

## Survey Protocol

- Este protocolo permite que um *surveyor* envie um *survey* (broadcast para todos os peers *respondents).* Os *respondents* ao recebê-lo têm a oportunidade de responder, mas não a obrigação. O survey é um evento temporizado, para que respostas enviadas fora do intervalo sejam descartadas.
- Fornece dois sockets:
    - Surveyor socket:
        - Usado para enviar surveys e receber respostas.
        - Uma resposta apenas pode ser recebida depois de enviar um survey.
        - Normalmente, espera-se que apenas uma resposta chegue de cada *respondent.* (As mensagens podem ser duplicadas em algumas topologias portanto não há garantia disto.)
        - Um erro NNG_ETIMEDOUT é retornado quando o *surveyor* está a aguardar por respostas e o tempo limite do *survey* é atingido.
        - Apenas um *survey* pode existir num dado momento. Enviar outro *survey* resulta no cancelamento do anterior (respostas recebidas para os surveys anteriores são descartadas.)
        - O modo RAW do socket ignora todas as restrições mencionadas.
        - Suporta a criação de contextos. Cada contexto pode iniciar os seus próprios *surveys,* e receber apenas as respostas apenas para os seus surveys ativos. Os outros contextos do mesmo socket podem ter surveys a operar em simultâneo.
        - Enviar um survey num contexto apenas cancela o survey ativo do mesmo contexto.
        - O timeout do survey pode ser definido através de uma opção (do socket ou do contexto).
        - Best-effort delivery, ou seja, outgoing surveys e incoming responses podem ser perdidos.
    - Respondent socket:
        - Usado para receber mensagens e enviar respostas. Uma resposta apenas pode ser enviada depois de receber um *survey.* Geralmente, a resposta é enviada para o *surveyor* de onde o último survey for recebido. (O receber aqui deve estar associado ao uso explícito do método *nng_recvmsg().* Ou seja, apesar de vários *surveyors* poderem ter enviado um survey em simultâneo, a resposta vai para o autor do survey recebido na última chamada da função *nng_recvmsg()*).
        - Usando o modo RAW é possível ignorar todas as restrições.
        - Permite a criação de contextos para casos de uso que necessitem de concorrência. Os surveys recebidos são encaminhados e recebidos por apenas um contexto. Outros surveys podem ser recebidos por outros contextos em paralelo. As respostas enviadas com um contexto são direcionadas para o surveyor que enviou o último survey recebido pelo contexto. As restrições relativas a ordens de operação dos sockets aplicam-se de igual forma aos contextos, com os contextos sendo tratados como sockets separados.
- Este protocolo, assim como o protocolo Request-Reply, possui um *backtrace* nos headers das mensagens. A única diferença é que existe um *Survey ID* em vez de *Request ID*. Isto permite o uso de nodos encaminhadores (*devices* ou *forwarding nodes*).

# Transportes

NNG fornece uma variedade de protocolos de transporte base. Para além destes ainda permite que o desenvolvedor integre novos protocolos de transporte. 

Para utilizar os transportes basta especificar o protocolo no prefixo dos URIs. Por exemplo: “tcp://192.168.1.70:5991”.

## Tipos de transportes

### Inproc (in-process or intra process communication)

- Este transporte permite a comunicação entre sockets do mesmo processo.
- Este transporte evita copiar dados ao máximo, e portanto é bastante leve.
- Prefixo: “inproc://”
- Aplicações diferentes a usarem o mesmo endereço não conseguirão comunicar entre si.

### IPC (inter-process communication)

- Para comunicação entre processos dentro do mesmo *host.*
- Para plataformas POSIX, usa sockets do domínio UNIX. Para o Windows, utiliza Windows named pipes. Outras plataformas podem ter diferentes estratégias de implementação.
- Os URIs têm como prefixo “ipc://” e de seguida um *path name* no sistema de ficheiros onde o socket ou *named pipe* deve ser criado.
    - No caso do Windows, os nomes são precedidos de “\\.\pipe\”

### BSD socket

- Transporte experimental. Suporta comunicação entre peers através de BSD sockets arbitrários.
- Prefixo: “socket://”
- Apenas listeners podem ser criados com este transporte. Passa-se o descritor de ficheiro para o listener através de uma opção do socket.
- **Não percebi mt bem tbh.**

### TLS

- Este transporte permite aos peers comunicarem usando TLS v1.2 sobre TCP.
- Prefixo: “tls+tcp://”
- Suporta tanto IPv4 e IPv6

### TCP

- Comunicação sobre TCP/IP
- Suporta tanto IPv4 e IPv6
- Prefixo : “tcp://”

### Websocket

- Comunicação entre peers através de TCP/IP usando Websockets.
- Suporta tanto IPv4 e IPv6.
- ..

### ZeroTier

- …

## Visão geral

- Networking através de sockets. Sockets são construídos através de funções específicas de cada protocolo (padrão de comunicação). Cada socket só pode implementar um único protocolo.
- Um socket pode ser utilizado para enviar/receber mensagens em função da semântica do protocolo.
- Os sockets são message-oriented, ou seja, as mensagens são entregues na totalidade ou não são entregues.
- Não são fornecidas garantias de entrega e ordem, i.e., as mensagens podem ser descartadas ou reordenadas. No entanto, alguns protocolos podem oferecer garantias de entrega mais fortes, como no caso dos sockets REQ que realizam o retries.
- Um socket pode ter vários endpoints. Podem ser tanto listeners ou dialers. O mesmo socket pode ter uma mistura de listeners e dialers. Cada endpoint pode usar um transporte diferente.
    - Cada endpoint tem um URL associado. Para os dialers, o URL é o endereço de serviço a contactar. Para um listener, o endereço é utilizado para aceitar novas conexões.
- Os endpoints não transportam dados, mas são responsáveis pela criação de *pipes* que podem ser consideradas streams conectadas de mensagens.
    - Os endpoints criam pipes quando necessário. Listeners criam pipes por cada novo pedido de conexão de um cliente. Os dialers apenas podem ter um pipe ativo de cada vez, esperando por uma desconexão antes de reconectar.
- Geralmente, os sockets são usados no seu modo normal, ou *cooked,* no entanto estes possuem um modo *raw* que pode ser utilizado para customizar o comportamento do socket em função das necessidades. Este modo *raw* permite ignorar restrições impostas pelo modo normal, sendo o desenvolvedor responsável por fazer as verificações e contruir as mensagens (adicionar os headers necessários, etc.).
    - Estes modos são usados, por exemplo, para a criação de *proxies* em que é necessário dar *bypass* às semânticas do protocolo e simplesmente passar mensagens de e para um socket sem necessidade de tratar das semânticas do protocolo.

# Contextos

- Alguns protocolos permitem que sejam definidos contextos. Os contextos são úteis para separar o processamento do protocolo do socket para um contexto separado. Isto permite que múltiplos contextos sejam criados dentro de um único socket para casos de uso em que seja necessário concorrência.
- Contextos têm identificadores para ser possível distingui-los.
- Os contextos permitem que operações *stateful* (com estado) sejam realizadas de forma independente e concorrente usando o mesmo socket. Por exemplo, dois contextos criados num REP socket podem receber *requests* e enviar respostas sem interferirem um como o outro.
- É possível enviar/receber mensagens assíncronamente utilizando as estruturas nng_aio referidas acima. E obviamente, é possível enviar/receber de forma síncrona.

# Devices, Relays

- NNG permite a criação de *forwarders* e *relayers* que encaminham as mensagens de um socket para outro. Designam-se por *nng_device*’s.
- Apenas sockets em RAW mode podem ser utilizados, já que é suposto ignorar as semânticas do protocolo para que as mensagens possam ser roteadas livremente.
- Fornece ainda nng_device_aio para que este comportamento seja feito totalmente no *background.*
- No caso do protocolo “falado” pelos sockets utilizar um campo *backtrace* (informação de *routing*) nos headers, o *forwarder* utilizará essa informação para encaminhar corretamente as “respostas”.
- Alguns protocolos possuem ainda “time-to-live” para proteger contra loops. ? Os forwarders são responsáveis por atualizar este valor presente no header das mensagens. ?
- Estes *devices* usam best-effort delivery. Mensagens podem ser descartadas quando a queue está cheia, devido a backpressure, etc.
- Permite verificar estatisticas dos sockets, listeners, dialers, etc, de modo a observar o comportamento do programa e ajudar a resolver problemas.
- Permite utilizar byte streams para ler/escrever nos sockets, em vez de usar mensagens.
- Pode ser configurado para suportar HTTP.

# Referências

- [nng.nanomsg.org](http://nng.nanomsg.org/)
- [nng.nanomsg.org/man/v1.7.1](http://nng.nanomsg.org/man/v1.7.1)
    - [https://nng.nanomsg.org/man/v1.7.1/libnng.3.html](https://nng.nanomsg.org/man/v1.7.1/libnng.3.html)
    - [https://nng.nanomsg.org/man/v1.7.1/nng.7.html](https://nng.nanomsg.org/man/v1.7.1/nng.7.html)
- [https://nanomsg.org/gettingstarted/nng/index.html](https://nanomsg.org/gettingstarted/nng/index.html)
- [https://github.com/nanomsg/nng](https://github.com/nanomsg/nng)
- [https://github.com/nanomsg/nanomsg/tree/master/rfc](https://github.com/nanomsg/nanomsg/tree/master/rfc) (não li mas pode ter informação relevante)
- [https://nng.nanomsg.org/RATIONALE.html](https://nng.nanomsg.org/RATIONALE.html)

# Dúvidas

- Os pipes são criados em função de conexões! Seria possível integrar o Exon de alguma forma?