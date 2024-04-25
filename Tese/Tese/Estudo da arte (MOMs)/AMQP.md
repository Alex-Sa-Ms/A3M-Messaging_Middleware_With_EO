# AMQP

***Advanced Message Queuing Protocol***

---

**Table of Contents**

# Visão Geral

- Permite o envio e a receção de mensagens. Permite especificar que mensagens serão recebidas e onde.
- Focado no *routing* e entrega de mensagens.
- **Eficiente:** protocolo binário orientado à conexão.
- **Confiável:** permite definir a confiabilidade necessária, desde *fire-and-forget* a confiável com *exactly-once delivery.*
- **Representação dos dados portável:** *Cross-platform*
- **Flexível:** Permite desenvolver diferentes tipos de topologias: *peer-peer*, *client-broker* e *broker-broker*.
- **Independente do modelo de brokers**
- **Desenvolvido para *deployment* à escala da Internet**
- Sistemas não precisam de estar disponíveis em simultâneo.
- Permite que o envio de mensagens (*messaging*) seja um serviço na *cloud.*
- Segurança:
    - Infraestrutura para uma rede global de transações confiável e segura.
    - Mensagens são *tamper proof*, i.e. não podem ser modificadas de forma maliciosa.
    - A durabilidade das mensagens é independente do destino estar conectado.
    - A entrega das mensagens é resiliente contra falhas técnicas.
    - Suporta os requisitos necessários para transportar transações de negócio.
- Fidelidade:
    - Semânticas de entrega bem definidas: *at-most-once*, *at-least-once*, e *once-and-only-once*.
    - Semânticas de ordem bem definidas: descrevem o que um transmissor pode esperar que o recetor observar, e o que o gestor de queue pode observar.
- União:
    - Qualquer cliente AMQP pode comunicar com qualquer broker AMQP sobre TCP/IP.
    - Fornece um conjunto de padrões de comunicação que podem ser configurados através de um único protocolo configurável:
        - *Asynchronous direct messaging;*
        - Request-Reply;
        - Publish-Subscribe;
        - Store and forward.
    - Suporta topologias de comunicação *Hub & Spoke*.
        - Topologia também conhecida como *Star topology.*
- Interoperabilidade
    - Múltiplas implementações de brokers estáveis e que conseguem interagir entre si.
    - Cada broker obedece à especificação do protocolo para todas as trocas de mensagens obrigatórias e funcionalidades de *queuing*, incluindo as semânticas de fidelidade.
    - Base do protocolo (client-broker e broker-broker) estável para suportar atualizações do protocolo, sem resultar em incompatibilidades.
    - Arquitetura em camadas para promover extensibilidade, de forma independente, de funcionalidades e transportes.
- Gerenciamento / Capacidade de gestão:
    - Protocolo binário desenvolvido para poder estar presente em todo o lado, para ser rápido e poder ser integrado (*embedded*), permitindo que gerenciamento seja fornecido ao encapsular os sistemas.
        - `Não entendi o que é que eu escrevi.` [https://www.amqp.org/product/features](https://www.amqp.org/product/features)
    - Escalável, para que possa ser a base de infraestruturas de comunicação de grande desempenho, sem perdas e tolerante a falhas.
- *Transactional messaging*
- *Publish-Subscribe* avançado

![Untitled](Tese/Estudo%20da%20arte%20(MOMs)/AMQP/Untitled.png)

---

# Use cases

- Feed constante de informação em tempo real para atualização de informação.
- Transações garantidas e encriptadas.
- Entrega de mensagens quando o destino fica online.
- Envio de mensagens enormes enquanto se recebe atualizações do estado sobre a mesma conexão.
- Quando é necessário funcionar em todos os sistemas operativos e linguagens populares.

# Arquitectura

- Inclui protocolo de comunicação eficiente que separa o transporte a nível da rede das arquiteturas dos brokers e do gerenciamento. Isto permite que várias e diferentes arquiteturas de brokers sejam suportadas e possam ser utilizadas para receber, *queue, route* e entregar mensagens ou ser usadas em peer-to-peer.
- As principais partes da arquitetura são: **protocolo de comunicação**, **representação dos dados dos envelopes das mensagens**, e **semânticas básicas dos serviços dos brokers**.

## Funcionalidades das camadas

### Protocolo de transporte

- O **protocolo de transporte** utilizado pelo AMQP é o TCP/IP.
- É confiável, transmissão de bytes ordenadas e possui controlo de fluxo
- Permite a subdivisão em *channels.*

### Protocolo de comunicação & Sistema de tipos

- Representação portável das propriedades das mensagens
- Mensagens são transportadas entre peers de transporte
- Responsabilidade sobre as mensagens é transferida entre os peers
- Controlo de fluxo das mensagens

### Message Broker - MOM layer

- Intermediário, separa temporalmente as aplicações
- Armazenamento seguro das mensagens
- Recursos transacionais
- Endereçamento local
- Funcionalidades de distribuição de mensagens; Filas (Queues) partilhadas, tópicos, etc.
- Filas de resposta

## Protocolo de comunicação AMQP

Define:

- Um protocolo peer-to-peer em que numa ponta fica uma aplicação cliente e na outra um broker.
- Como conectar serviços, incluindo como definir serviços alternativos a serem contactados em caso de falha.
- Mecanismo que permite aos peers descobrirem as capacidades do outro peer.
- Mecanismos de segurança para confidencialidade.
- Como multiplexar uma conexão TCP/IP para que múltiplas conversas possam coexistir na mesma conexão TCP/IP. Permite simplificar a gestão da firewall, otimizar a utilização de recursos e evitar a latência inicial de criar uma conexão.
- Como contactar um peer que é uma fonte de mensagens, e especificar as mensagens que pretende receber.
- O ciclo de vida de uma mensagem desde o *fetch,* processamento e confirmação da chegada. Explica sucintamente quando a responsabilidade sobre uma mensagem é transferida entre peers.
- Como aumentar o desempenho através de *pre-fetch* de mensagens.
- Como processar *batches* de mensagens dentro de uma transação.
- Um mecanismo que permite uma transferência completa de uma mensagem, desde o login até ao logout, num único pacote de rede.
- Controlo de fluxo que permite aos consumidores definir a velocidade dos produtores para que não sejam inundados de mensagens, e que permite o procedimento de diferentes *workloads* em paralelo a diferentes ritmos sobre a mesma conexão.
- Mecanismo para retomar a transferência de mensagens após o reestabelecimento da conexão.

### Tipos de codificação

![Untitled](Tese/Estudo%20da%20arte%20(MOMs)/AMQP/Untitled%201.png)

- Estes tipos de codificação são utilizados para adicionar propriedades de “routing” ao “envelope” da mensagem. O conteúdo da mensagem mantém-se inalterado.
- O sistema de tipos e as codificações foram desenvolvidos para que as mensagens sejam portáveis entres sistemas e acessíveis em diferentes linguagens de programação.

### Serviços de Broker

- AMQP utiliza *brokers,* i.e. intermediários, para tratar de processos de “enfileirar” (*queuing*), encaminhar (*routing*) e entregar (*deliver*) mensagens.
- O protocolo de AMQP é feito pare ser interoperável, possibilitando que diferentes implementações interajam entre si. Para isto, AMQP define um conjunto de requisitos mínimos que uma implementação deve cumprir para ser utilizada como um broker AMQP.

---

# Visão geral do funcionamento

- Modelado em camadas:
    - Protocolo de transporte e segurança da conexão.
    - Protocolo de transferência de *frames*
        - Define como é que os *frames* são transferidos entre os participantes.
    - Protocolo de transferência de mensagens
        - Define como é que a transferência de mensagens acontece.

## Protocolo de transferência de *frames*

- Assume que aplicações são *containers,* e que *containers* conectam-se a outros *containers.*
    - Containers são constituídos por nodos (será visto mais tarde).
- Exemplo do estabelecimento de conexão:
    1. Primeiro estabelece-se uma conexão diretamente com o protocolo de transporte, como por exemplo, TCP.
    2. Depois, pode seguir-se o negociamento (*handshake*) do protocolo de segurança, por exemplo para estabelecer um canal TLS.
        - O canal TLS pode ser negociado e estabelecido mais tarde através de um *in-place upgrade* com o AMQP, e desta forma utilizando o mesmo socket é possível ter comunicação segura e não segura. Isto é possível devido à capacidade de *multiplexing* do AMQP. **
    3. Pode ainda seguir-se o estabelecimento de credenciais (SASL).
    4. E, finalmente segue-se o estabelecimento da conexão AMQP. 
- As conexões AMQP servem para gerir a capacidade de transferência. A capacidade de transferência é definida ao iniciar-se a conexão através da especificação do “maximum frame size” (tamanho máximo do *frame*) e “channel count” (número de canais). Cada um dos lados da conexão especifica estes valores.
    - O “maximum frame size” é especialmente importante para o uso em IoT onde os dispositivos têm recursos muito limitados, e não queremos que uma mensagem seja enviada quando o seu tamanho é superior ao tamanho do buffer do receptor.
- Canais são caminhos independentes e unidirecionais por onde mensagens podem ser enviadas.
- Sessões são utilizadas para ligar (dar *bind*) a dois canais com direções opostas, de forma a criar uma ligação bidirecional.
- A nível das sessões é possível definir uma “incoming window” (”outgoing window” para o transmissor) que corresponde ao número de *frames* que conseguem ser tratados num dado momento. Isto é um mecanismo de controlo de fluxo dentro da sessão, logo diferentes sessões podem ter diferentes valores, o que permite priorizar tráfico.
- A criação de conexões é um processo custoso, e portanto, multiplexar o tráfico (através das sessões) de forma a utilizar a mesma conexão permite que esse custo seja “pago” uma vez apenas.
- Conexões e sessões são efêmeras, i.e., no caso da conexão colapsar (haver uma desconexão), estas são perdidas e têm de ser configuradas novamente. Estes conceitos, sozinhos, não conseguem fornecer comunicação confiável, daí existir a camada do protocolo de transferência de mensagens sobre esta camada de transferência de frames.

![Untitled](Tese/Estudo%20da%20arte%20(MOMs)/AMQP/Untitled%202.png)

## Protocolo de transferência de mensagens

- Comunicação confiável é dada pela camada do protocolo de transferência de mensagens, através do que se chamam *“links”.*
- *Links* podem ser feitos duráveis de forma a poderem ser recuperados no caso de uma falha na sessão ou conexão.
- *Containers* (aplicações) gerem nodos.
    - Os nodos são entidades que podem ser acedidas dentro do *container* através do caminho (*path*) correspondente.
    - Podem ser organizados de diversas maneiras: *flat*, hierarquicamente ou em formato de grafo.
    - Nao existe uma definição explícita do que um nodo tem de ser. Nodos podem ser uma *sink* ou uma *source* de mensagens. Podem ser uma *queue* ou **um tópico ou um *relay* ou um *event store* ou …
    - Tanto um broker (server) como um cliente podem criar nodos para comunicação paralela.

![Untitled](Tese/Estudo%20da%20arte%20(MOMs)/AMQP/Untitled%203.png)

- *Links* conectam-se através dos *paths* para os nodos e são unidirecionais.
- Um nodo cria um “link”, anexa-o a si próprio e pede ao outro participante para o anexar também. Ao anexar um “link” é necessário especificar o papel que será cumprido: “sender” ou “receiver”. Obviamente, apenas um lado pode ser o “sender” e apenas um lado pode ser o “receiver”.
- Os *links* são unidirecionais para tráfico de mensagens, mas são bidirecionais no que toca a informação de controlo e estado. Nestes links informação de controlo de fluxo flui em ambas as direções, assim como informação sobre o que acontece com as mensagens (se foram aceitadas, rejeitadas, etc).
    - Se uma mensagem for rejeitada, existe um fluxo de erro específico para que o “sender” possa tratar esse erro de forma apropriada.
- As aplicações dão nomes aos links. Estes são formados sobre sessões, e caso a sessão/conexão falhe, é possível recuperar os links, e assim recuperar o estado entre os dois participantes (por exemplo, estado de entrega).

![Untitled](Untitled%204.png)

# Elementos fundamentais do protocolo de transferência de *frames*

## Conexão (*Connection*)

- Atua sobre um protocolo de transporte confiável, com garantia de ordem e que trabalhe com *streams* (TCP, SCTP, Pipes, etc).
- Uma conexão fornece uma sequência de *frames* ordenada e confiável (características obtidas por transitividade através do protocolo de transporte). Os *frames* não excedem **o **tamanho máximo negociado.
- Para iniciar uma conexão envia-se um “OPEN” frame. Este frame contém informações como identificar do *container*, “hostname”, “maximum frame size”, “max channel count”, o tempo máximo de inatividade antes que a conexão seja descartada, etc.
    - Ao receber este frame, se pretender abrir a conexão, devolve um “OPEN” frame com as suas caraterísticas que pretende para a conexão.
- Os *frames* são enviados sobre canais (*channels*) unidirecionais que são negociados e multiplexados na conexão.

![Untitled](Untitled%205.png)

## Camada de segurança a nível da conexão

- Como referido anteriormente, a segurança pode ser negociada antes de abrir uma conexão AMQP, ou através do AMQP.
- A segurança no nível do transporte é fornecida por TLS/”SSL”.

![Untitled](Untitled%206.png)

## Sessão (*Session*)

- Uma sessão é aberta através de um frame “BEGIN”. Este frame é enviado sobre um canal (*channel*) disponível (no transmissor) e pode indicar o canal remoto ao qual pretende dar bind ou pode simplesmente deixar que o *receiver e*scolha. O *receiver* responde com outro frame “BEGIN” indicando o canal remoto ao qual está responder e será feito o “bind”.
- Uma sessão “junta” dois canais unidirecionais com sentidos opostos para formar uma conversa bidirecional e sequencial.
- Fornece um controlo de fluxo baseado em janelas (*window-based flow control*).
    - Define o número total de *frames* que o *sender* e o *receiver* podem manter no buffer num momento qualquer.
    - Enquanto a janela estiver cheia, toda a comunicação nessa sessão fica parada.
    - Com a criação de múltiplas sessões em simultâneo dentro da mesma conexão e com o ajuste do tamanho das *windows* é possível prioritizar tráfico.

![Untitled](Untitled%207.png)

## Link

- Os *links* têm nomes (dados pela aplicação) e são rotas unidirecionais para mensagens que vão de um nodo “source” para um nodo “target”.
    - Suponho que o “source” e “target” são utilizados para que as mensagens possam ser enviadas/recebidas utilizando diferentes padrões de comunicação, como peer-to-peer, multicast, one-to-many, etc.
- Um *link* é associado a uma e só uma sessão. As sessões permitem acomodar um número arbitrário de *links* concorrentes.
- *Links* podem ser iniciados em qualquer direção (*receiver* ou *sender*) por cada peer.
- Para criar um *link* é enviado um *frame* “ATTACH” para o outro participante, o qual deve responder com outro *frame* “ATTACH” para confirmar o estabelecimento do *link*.
- Um *link* fornece uma rota de transferência de mensagens unidirecional (definido pelas caraterísticas ao criar o link) e uma rota de “confirmação” no sentido oposto ao sentido em que são enviadas as mensagens.
- Os *links* podem ser recuperados numa conexão ou sessão diferente quando a anterior falhou.
- *Links* possuem um controlo de fluxo à base de créditos, distinto do controlo de fluxo da sessão, mais direcionado para o modelo de gestão do fluxo (Não tenho a certeza se é isto, mas por exemplo no caso do Request-Reply, apenas se pode enviar uma mensagem e tem que se aguardar que seja tratada para se receber outro crédito e enviar outra.)

![Untitled](Untitled%208.png)

# Transferência de mensagens

## Settlement

- A transferência de mensagens ocorre sobre *links*.
- Transferências podem ser *settled* ou *unsettleded.*
    - *Settled* pode ser visto como *fire-and-forget.* A mensagem é enviada e eliminada do buffer do *sender* imediatamente a seguir. Usado para quando não é pretendido receber confirmação da entrega ou erro de que não foi possível processar a mensagem. Pode ser também visto como o modelo at-most-once.
        - Envia-se frame “TRANSFER” com a propriedade “settled” a “true”. Um frame “DISPOSITION” não será enviado pelo destino da mensagem.
    - *Unsettled* para quando é necessário “settlement” explícito, e permite que diversos estados de entrega sejam tratados e permite o uso de diferentes modelos de garantia de entrega.
        - *At-least-once*: Envia-se frame “TRANSFER” com a propriedade “settled” a “false” e espera-se por um frame “DISPOSITION” com a propriedade “settled” a “true”.

![Untitled](Untitled%209.png)

## Estado de entrega

- As transferências de mensagens resultam em estados de transferência:
    - ‘null’ quando uma mensagem é enviada e ainda não se recebeu qualquer informação sobre o estado da entrega.
    - Estados intermediários (transferência está a ocorrer):
        - *received:* usado na negociação da recuperação de um *link*, para indicar que uma mensagem tinha sido recebida.
    - Estados terminais (trasnferência já ocorreu):
        - *accepted:* quando a mensagem foi aceitada pelo destino.
        - *rejected:* quando a mensagem foi rejeitada pelo destino (este estado é acompanhado do erro e dos detalhes do diagnóstico).
        - *released:* a mensagem foi abandonada pelo receptor e deve ser reentregue.
        - *modified:* a mensagem foi *released* (ver acima) e deve ser modificada na origem de acordo com os detalhes fornecidos.

## Recuperação de *links*

- O estado *received* mencionado anteriormente é importante para a recuperação de *links.*
- Vamos agora assumir uma situação em que a conexão é interrompida entre o envio de mensagens e a receção do frame “DISPOSITION” para essas mensagens:
    - Quando isto acontece, cria-se uma nova conexão, uma nova sessão e depois faz-se o *attach d*o link com o conhecimento sobre as mensagens que estão ainda em memória.
        - Apresenta-se as “mensagens” que estão em memória e o estado para cada uma dessas mensagens, o outro participante responde indicando o estado que tem para essas mensagens (podendo o estado ser ‘null’ se ainda não tinha recebido a mensagem).
        - Estas informações servem para limpar os buffers de acordo com os estados recebidos, e retomar as transferências. De seguida, As transferências são, de seguida, recuperadas para reenviar, reconciliar estado ou continuar o “settlemente”.
    
    ![Untitled](Untitled%2010.png)
    

## Comunicação bidirecional e Request/Response

- Criar comunicação bidirecional é muito simples. Assumindo um nodo C e outro nodo T. O nodo C inicia dois links, um em que é *receiver* (com a *source* a ser o nodo T) e outro em que é o *sender* (com o nodo T a ser o *target*).

![Untitled](Untitled%2011.png)

- Com estes dois links a permitir comunicação bidirecional conseguimos facilmente implementar o padrão Request/Response. Ao enviar um pedido, o nodo C define a propriedade “reply-to” na mensagem de forma a que o nodo T saiba encaminhar a resposta. O pedido também deve ser acompanhado de um “correlation-id”, propriedade que será igual na resposta para que seja possível identificar o pedido ao qual a resposta corresponde. No caso de não existir um correlation-id pode-se definir a propriedade “message-id”.

# Tipos de *frames*

- Recapitulação dos frames já falados.
- Para perceber o que significa o **“Handle”** e o **“Inspect”**, olhemos para o frame **BEGIN**. Este frame possui “Inspect” na coluna da “Connection” o que significa que este frame é inspecionado pela conexão, e possui "Handle” na coluna da “Session” significando que o este frame é tratado no nível da sessão.

![Untitled](Untitled%2012.png)

# Controlo de fluxo

Como referido anteriormente, existem dois modelos de controlo de fluxo. Um no nível da sessão, e outro no nível do *link*.

## Controlo de fluxo da sessão

- Destinado para a gestão de recursos da plataforma.
- Cada *endpoint* de uma sessáo tem duas janelas de transferência, uma para envio (*outcoming transfer window*) e outra para receção (*incoming transfer window*).
- As janelas de transferências definem o limite máximo de *mensagens* (e não de *frames,* portanto está relacionado com os *frames* do tipo TRANSFER) que podem estar em trânsito. Permitem definir um limite de forma a que os recursos não são sejam esgotados.
    - A quantidade máxima de memória que pode ser utilizada pela sessão pode ser calculada através do *frame size* definido na conexão e do tamanho das janelas da sessão.
- Transferências diminuem o tamanho das janelas. Quando o tamanho da janela de envio é menor ou igual a 0 a transferência (i.e. envio de mais mensagens) é suspendida.
- O tamanho das janelas é atualizado/reiniciado através do *frame* **FLOW***.*

![Untitled](Untitled%2013.png)

 

## Controlo de fluxo do *link*

- Destinado para a gestão do fluxo de mensagens da aplicação.
- O controlo de fluxo dos *links* é baseado em créditos. Os créditos definem o número de mensagens que o receptor está disposto a aceitar. Os créditos diminuem com a receção de mensagens.
- As mensagens apenas podem fluir quando o crédito é positivo. As transferências de mensagens são suspendidas quando o número de créditos é nulo ou negativo.
- O receptor é o único que pode atualizar o número de créditos. A atualização é feita através *de frames* do tipo FLOW.

![Untitled](Untitled%2014.png)

## Para que são necessários dois tipos de controlo de fluxo?

- O controlo de fluxo respetivo às sessões permite proteger a plataforma, nomeadamente da exaustão dos recursos: a nível da memória (tamanho total das mensagens recebidas/armazenadas para envio superam o tamanho dos buffers), e a nível computacional (definir o throughput, para evitar receber/enviar a um ritmo não suportado).
- O controlo de fluxo a nível dos *links* permite proteger a aplicação, de modo a impedir que esta aceite mais mensagens do que consegue tratar, i.e., que fiquem sobrecarregadas e que possam eventualmente *crashar*.

## Alguns padrões da API e como afetam o controlo de fluxo

1. Invocar o método de receive (abordagem imperativa).
    - Resulta no envio de um crédito para o *sender,* permitindo assim que uma mensagem seja enviada.
    
    ![Untitled](Untitled%2015.png)
    
2. Subscrever com uma função *callback* (abordagem reativa).
    - O número de créditos fornecidos é igual ao número de operações concorrentes que o sistema reativo consegue suportar.
    
    ![Untitled](Untitled%2016.png)
    
3. Envio para uma queue.
    - A *queue* envia o número de créditos igual ao número máximo de mensagens que podem ser guardadas em simultâneo na queue.
    
    ![Untitled](Untitled%2017.png)
    
4. Pré-busca de mensagens.
    - Para ter “sempre” mensagens disponíveis para processamento em vez de ter de pedir (enviar crédito) e esperar que a mensagem seja enviada.
    
    ![Untitled](Untitled%2018.png)
    

## Outras opções para controlo de fluxo

- É possível dar “drain” ao transmissor, pedindo-lhe que efetue imediatamente todas as transferências pendentes e que depois reduza o crédito do *link* para 0 (para impedir mais transferências). Isto é possível através de um *frame* FLOW com a propriedade “drain” a “true”.
- É possível, também, parar imediatamente a transferência de mensagens pendentes sobre um *link*. Isto pode ser feito ao enviar um *frame* FLOW com a propriedade “link-credit” a 0.

# Sistema de tipos

## Visão geral do sistema de tipos

- O AMQP tem um sistema de tipos próprio que é portável e independente da linguagem de programação.
- Otimizado para processamento rápido e flexível e com uma representação compacta.
- Define uma variedade de tipos primitivos e compostos, sendo possível definir novos tipos compostos.
- Permite definir referências descritivas para sistemas de tipos externos. Útil para quando pretendemos que o “objeto” seja mapeado para um tipo específico numa certa linguagem de programação.
- Pode ser utilizado para expressar e codificar os payloads das mensagens.
- Suporta dois modos de codificação:
    - *Schema-bound* onde apenas os dados são transmitidos e a interpretação é baseada em metadados partilhados fora do contexto da comunicação. Evita a necessidade de enviar no payload informação descritiva sobre os elementos, especialmente nos casos em que o mesmo esquema é utilizado múltiplas vezes.
    - *Schema-free* (tipo JSON) onde os dados não têm um esquema predefinido. A codificação contém dados descritivos ou é acompanhada pelos metadados da mensagem que permitem a descodificação.
- Os esquemas AMQP são expressos em XML.
- Existem 3 classes de tipos:
    - Tipos primitivos: São tipos pré-definidos (inclui array, list [array polimórfico] e map).
    - Tipos compostos:
        - Tipos multi-valores, essencialmente mapeia nomes de campos (strings apenas) para tipos (podem ser tipos compostos).
        - Todos os tipos compostos são definidos sobre o tipo primitivo *list*.
        - A lista contém os elementos na ordem definida no esquema.
        - Elementos “finais” (*trailing*) podem ser omitidos de forma a tornar a codificação mais compacta. (Por exemplo, um criar um valor do tipo presente na figura abaixo, poderia codificar-se apenas os primeiros 4 campos e ignorar os restantes. Se por exemplo quisessemos enviar o correlation-id para além dos primeiros 4 campos, então seria necessário codificar também o quinto campo.)
        
        ![Untitled](Untitled%2019.png)
        
    - Tipos restritos
        - Herdam tipos já existentes (source type).
        - Podem restringir o domínio de valores, com os nomes escolhidos a poderem servir de *alias* (Exemplo do *sender-settle-mode*)*.*
        - Podem servir apenas para criar um *alias* diretamente sobre o tipo *source* (Exemplo do *seconds*).
        - Podem ser utilizados para substituir o descritor do tipo (Exemplo do *delivery-annotations*).
        
        ![- No primeiro exemplo podemos verificar que foi criado um tipo restrito que apenas permite 3 valores do tipo *unsigned byte*: 0 *(unsettled)*, 1 *(settled)* e 2 *(mixed).
        -* No segundo exemplo apenas é criado um *alias* para o tipo uint com o nome “seconds”.
        - No terceiro exemplo é dado um novo nome ao tipo além da alteração do descritor.](AMQP%20287b741104884897bead75ebda721c31/Untitled%2020.png)
        
        - No primeiro exemplo podemos verificar que foi criado um tipo restrito que apenas permite 3 valores do tipo *unsigned byte*: 0 *(unsettled)*, 1 *(settled)* e 2 *(mixed).
        -* No segundo exemplo apenas é criado um *alias* para o tipo uint com o nome “seconds”.
        - No terceiro exemplo é dado um novo nome ao tipo além da alteração do descritor.
        
- Permite definier *Archetypes* para categorização de tipos.
- Descritores para identificar os tipos *(on the wire)*.

## Dados no fio (*Data on the wire*)

- Os dados no “fio”/”cabo” são todos antecedidos por um construtor que indica o tipo.
    - Para tipos primitivos (built-in) um único byte é utilizado.
        
        ![Exemplo para o tipo primitivo “string”. Começa-se com o byte (construtor) que indica qual é o tipo e segue-se os dados necessários para expressar o valor, que neste caso é um byte que indica o tamanho da string (em bytes) e de seguida a própria string. ](Untitled%2021.png)
        
        Exemplo para o tipo primitivo “string”. Começa-se com o byte (construtor) que indica qual é o tipo e segue-se os dados necessários para expressar o valor, que neste caso é um byte que indica o tamanho da string (em bytes) e de seguida a própria string. 
        
    - Para tipos compostos e restritos utilizam-se os descritores (númerico ou simbólico).
        
        ![Exemplo para um tipo composto. O primeiro byte indica que será apresentado um descritor, depois segue-se um byte a indicar o tipo do descritor (neste caso do tipo simbólico, representado por um byte a indicar o tamanho do tipo simbólico e depois a valor) e para terminar o construtor apresenta-se um byte para informar o tipo dos dados (neste caso corresponde ao tipo primitivo “list”).](Untitled%2022.png)
        
        Exemplo para um tipo composto. O primeiro byte indica que será apresentado um descritor, depois segue-se um byte a indicar o tipo do descritor (neste caso do tipo simbólico, representado por um byte a indicar o tamanho do tipo simbólico e depois a valor) e para terminar o construtor apresenta-se um byte para informar o tipo dos dados (neste caso corresponde ao tipo primitivo “list”).
        

## Tipos primitivos de tamanho fixo

- Na tabela seguinte podemos observar os construtores para os diversos tipos primitivos de tamanho fixo.
    - A coluna *header* apresenta o número de bytes ocupados por cada tipo.

|  | 0 | 1 | 2 | 4 | 8 | 16 |
| --- | --- | --- | --- | --- | --- | --- |
| null | 0x40 |  |  |  |  |  |
| boolean | 0x41: true
0x42: false | 0x56 |  |  |  |  |
| ubyte |  | 0x50 |  |  |  |  |
| ushort |  |  | 0x60 |  |  |  |
| uint | 0x43: v=0 | 0x52: v<256 |  | 0x70 |  |  |
| ulong | 0x44: v=0 | 0x53: v<256 |  |  | 0x80 |  |
| byte |  | 0x51 |  |  |  |  |
| short |  |  | 0x61 |  |  |  |
| int |  | 0x54: -128<v<127 |  |  |  |  |
| long |  | 0x55: -128<v<127 |  |  | 0x81 |  |
| float |  |  |  | 0x72 |  |  |
| double |  |  |  |  | 0x82 |  |
| decimal32 |  |  |  | 0x74 |  |  |
| decimal64 |  |  |  |  | 0x84 |  |
| decimal128 |  |  |  |  |  | 0x94 |
| char |  |  |  | 0x73 |  |  |
| timestamp |  |  |  |  | 0x83 |  |
| uuid |  |  |  |  |  | 0x98 |
- Podemos verificar que alguns tipos têm vários códigos para representação que permitem uma representação mais compacta dos dados, reduzindo assim a quantidade de dados a ser enviada.
    - Se olharmos para o exemplo do “uint” podemos verificar as seguintes representações:
        - 0x43: construtor que indica que o valor é do tipo “uint” e que tem valor 0 (apenas o construtor é enviado, não seguem quaisquer dados)
        - 0x52: construtor que indica que o valor é do tipo “uint” e cujo valor é menor que 256, i.e., pode ser representado utilizando um único byte, e portanto após o construtor apenas um byte o segue em vez de 4 bytes (tamanho de um *uint*)
        - 0x70: construtor que indica que o valor é do tipo “uint” e que seguirão 4 bytes para representar o valor.

## Tipos primitivos de tamanho variável

- Estes tipos possuem um preâmbulo que segue o construtor e que precede os dados variáveis. Este preâmbulo pode ter o tamanho de 1 ou 4 bytes e representa o tamanho em bytes dos dados referentes ao tipo.
    
    ![Untitled](Untitled%2023.png)
    
- Existem ainda:
    - *Arrays*: Um array é uma sequência de elementos monomórfica, i.e., todos os elementos são do mesmo tipo. A codificação começa com o construtor que indica que o tipo é um array, segue-se a quantidade de elementos, depois o construtor dos elementos e depois os valores dos elementos.
        
        ![Untitled](Untitled%2024.png)
        
    - *Lists*: Uma lista é uma sequência de elementos polimórfica, i.e., permite que os elementos sejam de diferentes tipos. Primeiro aparece o construtor que indica que o tipo corresponde a uma lista, segue-se a quantidade de elementos, depois segue-se a codificação dos elementos (primeiro o construtor do elemento e de seguida os dados necessários para o representar).
        
        ![Untitled](Untitled%2025.png)
        
    - Maps: Os mapas são sequências polimórficas de pares chave-valor. Formato similar ao das listas, mas indíces pares correspondem a chaves e índices impares aoos valores associados à chave com o índice imediatamente antes. Como são pares chave-valor, o número de elementos tem de ser sempre um número par.
        
        ![Untitled](Untitled%2026.png)
        

# Mensagens AMQP

- Como já foi referido anteriormente, existe um protocolo de transferência de *frames* (utilizado para enviar os *frames* do tipo TRANSFER) e existe um protocolo de transferência de mensagens que funciona por cima desse. Este último protocolo será abordado nesta secção.

## Formato das mensagens AMQP

- O AMQP possui um formato de mensagem predefinido que pode ser substituído/extendido.
- Tipo do formato e versão da mensagem é selecionado no TRANSFER *frame*.
- Formato predefinido:
    
    ![Untitled](Untitled%2027.png)
    
    - A parte representada como “Bare Message” é imutável desde a origem até ao destino, náo podendo ser modificada por nenhum intermediário.
    - A parte relativa a anotações pode ser modificada.
    - Todas estas secções são definidas como tipos compostos cujo *archetype* é “section”. Um exemplo pode ser verificado aqui [https://www.notion.so/AMQP-287b741104884897bead75ebda721c31?pvs=4#6aed0ad6135b4833bdfc9596c9212da6](AMQP.md).

## Corpo da mensagem

Para o corpo da mensagem (*Message body*), uma das seguintes 3 opções pode ser escolhida:

- Uma ou mais secções de **dados** **binários**
    - Tamanho até atingir o limite máximo definido para os *frames*.
    - Dicas para interpretar os dados podem ser inseridos nas propriedades da mensagem como “content-type” e “content-encoding”.
- Uma ou mais secções do tipo **amqp-sequence**
    - Uma sequência é uma lista de valores AMQP codificados.
- Ou uma única secção do tipo **amqp-value**
    - Corresponde a um único valor AMQP (podendo ser um null).
    - Pode ser um valor do tipo composto.

## Exemplo JSON para AMQP

- Sem esquema:
    - Formato idêntico e é facilmente mapeado de JSON para AMQP e de AMQP para JSON.
    - A diferença de tamanho (”bytes") é idêntica, no entanto, o AMQP possui uma descrição dos tipos mais sofisticada do que a do JSON.
    
    ![Untitled](Untitled%2028.png)
    
- Com esquema:
    - Codificação mais compacta já que existe uma descrição do esquema externa.
    - Mesmo sem o esquema externo presente é possível descodificar os dados, apenas não ficam associadas as “labels” (nomes dos campos).
    - A diferença para o exemplo anterior é que os dados sobre os livros estão codificados como uma lista e não como um mapa, excluindo a necessidade de codificar os “nomes” de cada um dos campos já que estes estão presentes no esquema.
    
    ![Untitled](Untitled%2029.png)
    

# Frames AMQP

- Os frames AMQP possuem 3 partes:
    - **Header** (8 bytes)
        - Contem as seguintes informações:
            - Tamanho total do *frame* (4 bytes)
            - Offset dos dados, desde o ínicio do frame, em grupos de 4 bytes (1 byte)
            - Indicador do tipo de frame (1 byte)
            - Identificador do canal (2 bytes, ou seja, permite até 65536 canais)
    - **Performative** (size - offset * 4 bytes)
        - Tipo composto AMQP que descrebe a operação.
        - Para os *frames* AMQP: Open, Begin, Attach, Flow, Transfer, Disposition, Detach, End, Close
    - **Payload**
        - Depende dos dados presente na parte “Performative”.
        - Neste momento, apenas é utilizado no frame *TRANSFER*, e esta *performative* diz que o que se segue no payload é uma mensagem AMQP.

![Untitled](Untitled%2030.png)

## Wire Footprint

- O overhead “mínimo” (não é bem o mínimo, mas sim o número mínimo no caso geral e não numa exceção) para a transferência de uma mensagem (*frame* do tipo TRANSFER) ronda os 35 bytes.
    - 8 bytes obrigatórios do header
    - 22 bytes, aproximadamente, das performativas do frame TRANSFER
    - 3 bytes para identificar o tipo de payload. ??? verificar pq são necessários 3 bytes
    - 2 bytes, aproximadamente, um para o construtor do tipo primitivo e outro para um valor.

![Untitled](Untitled%2031.png)

---

---

---

# Especificação

(Em principio esta secção é para ser completada com o que está acima, mantendo apenas informação pertinente sem entrar em demasiado detalhe, para que apenas seja necessário fazer a tradução e ficar pronto a incluir na tese.)

---

---

---

# Visão geral

O protocolo AMQP (Advanced Message Queuing Protocol) define um protocolo simétrico a nível do fio (*wire-level*) que permite que dois participantes troquem mensagens entre si de forma confiável. (É um protocolo simétrico já que não faz distinção entre clientes e brokers). A arquitetura deste protocolo está dividida em camadas para que a extensão destas possa evoluir de forma separada. As diferentes camadas da arquitetura são: sistema de tipos e codificação; camada de transporte que define o protocolo para transportar mensagens entre dois processos pela rede;  formato das mensagens AMQP e a sua codificação; transações atómicas para agrupar interações; e, por fim, a camada de segurança.

# Tipos

## Sistema de tipos

Possui um sistema de tipos próprio com um conjunto de tipos primitivos que permitem uma representação de dados interoperável. Os valores podem ainda ser anotados com informações semânticas para além das associadas com o tipo primitivo (exemplo dizer que uma string é um URL). O sistema de tipos permite ainda a definição de tipos compostos para a representação de estruturas arbitrárias de dados.

### Tipos primitivos

O sistema de tipos define um conjunto pré-definido de tipos primitivos para representação de valores escalares (booleans, strings, inteiros, etc)  e de coleções (arrays monomórficos, listas polimórficas e maps). 

### Tipos descritivos

Existem diversos tipos básicos representados nas diversas linguagens de programação que podem ser expressos através dos tipos primitivos definidos pelo protocolo AMQP. Para que estes tipos personalizados sejam facilmente identificados, o sistema de tipos permite anotar os tipos primitivos com um **descritor**.

### Tipos compostos

O sistema de tipos permite a definição de um número de tipos compostos para a codificar estruturas de dados. Os constituintes de um tipo composto são designados por *fields* (campos). Estes campos são definidos de forma ordenada, especificando-se o nome do campo (string), o tipo e a multiplicidade (É importante ter em consideração a ordem já que para tornar as mensagens mais compactas, os campos finais, *trailing fields,* podem não ser codificados se não tiverem um valor definido, no entanto, todos os campos para trás de um campo que tem um valor definido são codificados.). Os tipos compostos são acompanhados de um ou mais descritores (númerico ou simbólico) para identificar a representação.

### Tipos restritos

O sistema de tipos permite a definição de tipos restritos que é essencialmente um tipo primitivo cujo domínio de valores é limitado. Os limites podem ser definidos como um conjunto fixo pré-definido (por exemplo, uma enumeração em que do tipo primitivo inteiro só são permitidos os valores 1, 2 e 3) ou um conjunto *open ended* (por exemplo, um email que é uma string que deve seguir um certo padrão).

## Codificação dos tipos

As streams de dados codificados AMQP consistem em bytes sem tipo (*untyped bytes*) com construtores incluídos. Estes construtores permitem interpretar os bytes que o seguem. Os dados codificados pelo AMQP começam sempre com um construtor. Um construtor pode ter um de dois formatos: *primitive format code* que é um código para um tipo primitivo; ou *described format code*, que consiste num descritor e num código de um tipo primitivo. Tipos compostos são representados por um dos tipos primitivos designados por coleções (*map* ou *list* dependendo se a codificação é feita utilizando um esquema ou sem esquema).  

(Escrever os exemplos na tese por palavras próprias mas mencionar que foram extraídos da especificação, posso sempre criar o meu exemplo utilizando outra string e o segundo fazer com um email)

![Untitled](Untitled%2032.png)

![Untitled](Untitled%2033.png)

## Notação dos tipos

Os tipos são especificados utilizando uma notação XML. É importante notar que a notação XML é apenas utilizada para especificação dos tipos e nunca para codificação dos dados. Todos os dados são codificados em binário como mostrado acima. O AMQP permite que a codificação seja feita recorrendo a esquemas (*schema-bound*) ou sem esquemas (*schemaless*). Os esquemas dos tipos são úteis já que permitem tornar a codificação dos dados mais compacta, e por consequência, reduzir a quantidade de dados enviados. Um exemplo de como a codificação fica mais compacta é o seguinte: 

- Consideremos um tipo composto personalizado. Se não for utilizado um esquema para codificar/descodificar os dados, é necessário codificar o nome dos campos (cada campo tem de ter o seu nome codificado) aos quais os valores ficam associados, através do tipo primitivo *map.* Se for usado um esquema a codificação pode ser feita utilizando o tipo primitivo *list,* codificando-se apenas os valores associados aos campos, não havendo necessidade de codificar o nome dos campos já que estes estão presentes no esquema.

Admitindo que a codificação é feita com um esquema e que o recetor não possui o esquema do seu lado, a descodificação não é impossível. A codificação é feita de forma a que seja possível descodificar mesmo sem o esquema. No entanto, utilizando o exemplo do tipo composto, o recetor apenas não conseguiria associar os valores aos respetivos campos já que não existe essa informação presente na codificação.

# Transporte

Esta secção é referente à camada de transporte do protocolo AMQP. Esta camada é responsável pelo transporte dos *frames*, de forma confiável, entre dois participantes. 

## Modelo conceptual

Uma rede AMQP é constituída por nodos que comunicam através de *links*. Os nodos são responsáveis por armazenar e/ou entregar mensagens. A responsabilidade sobre o armazenamento e entrega das mensagens vai sendo transferida entre os nodos pelos quais passam. 

Os nodos são entidades com nome que existem dentro de um *container.* Possuem nomes para que possam ser identificados dentro do *container*. Exemplos de nodos são produtores (geram mensagens), consumidores (processam mensagens) e filas (entidades também conhecidas por *queues,* que são responsáveis por armazenar e encaminhar mensagens). 

Exemplos de containers são aplicações clientes e *brokers*. Estes nodos são identificados dentro dos containers através do seu nome.

Os links são rotas unidirecionais entre dois nodos, e são definidos por um nodo fonte (source) e um nodo destino (target).

## Pontos de comunicação (communication endpoints)

Esta camada do protocolo AMQP foca-se apenas na transferência de mensagens entre nodos numa rede AMQP. Para que exista a transferência de mensagens é necessário existir primeiro uma *conexão*. Esta conexão é estabelecida entre *containers* e não entre nodos. A comunicação entre nodos de diferentes containers é realizada e multiplexada (mais sobre a multiplexagem à frente) utilizando a conexão estabelecida entre esses dois containers. 

Uma conexão AMQP é bidirecional e consiste numa sequência confiável e ordenada de *frames*. Um *frame* representa uma unidade de trabalho (mensagens são enviadas dentro de um tipo de frame especifico). Aquando do estabelecimento da conexão, ambos os *peers* informam qual o tamanho máximo de frame suportado (útil para IoT para impedir que frames com tamanho superior ao buffer destino sejam enviados). As conexões são consideradas efêmeras, podendo falhar, e portanto não são mantidas persistidas informações relativas a estas.

Uma conexão pode ainda ser dividida num número negociado de canais (channels) unidirecionais, com cada um dos *frames* especificando o canal por onde é enviado. As diferentes sequências de *frames* de cada canal **são multiplexadas na mesma conexão.

Existe ainda a noção de sessão que junta dois canais unidirecionais com sentidos opostos de forma a permitir conversas sequencias e bidirecionais entre dois containers. Cada sessão tem um controlo de fluxo associado baseado no número de *transfer frames* transmitidos (transfer frames são os frames utilizados para enviar mensagens). Utilizando o tamanho máximo dos frames definido ao estabelecer a conexão e o tamanho da janela definido é possível controlar os bytes transmitidos, para optimizar o desempenho ou ainda definir prioridades no tráfico de mensagens.

Uma conexão permite que múltiplas sessões independentes sejam criadas, tendo em conta o nível de canais máximo negociado.

Para que mensagens sejam transferidas entre nodos é ainda necessário o estabelecimento de links  entre nodos. Note-se que as conexões, sessões e canais são definidos entre os containers, com os links sendo necessários para mensagem fluirem entre os nodos que constituem os containers. Os links controlam o estado da sequência de mensagens. Ao contrário das conexões e das sessões, os links podem ser feitos duráveis permitindo a recuperação destes na eventualidade da conexão / sessão que está por baixo falhar (importante para cumpriar as garantias de entrega como exactly-once). Os links fornecem um controlo de fluxo à base de créditos que depende do número de mensagens transmitidas, permitindo que as aplicações controlem quando os nodos devem receber mensagens (utilizando o mecanismo de controlo de fluxo é possível pedir explicitamente que uma mensagem seja enviada, pedir que mensagens sejam enviadas previamente para que quando seja necessário já existam mensagens para processamento, entre outras utilidades permitidas por este mecanismo). 

Os links identificados por nomes são estabelecidos sobre sessões. As sessões permitem o estabelecimento de um número arbitrário de links. 

## Frames

O protocolo de transporte de frames define 9 tipos de frames que serão apresentados mais tarde. A tabela abaixo apresenta os diferentes tipos de frames e define que endpoint (conexão, sessão ou link) é que trata do frame.

![Untitled](Untitled%2034.png)

Os frames são constituídos por 3 partes principais:

- Frame Header, que contém o tamanho do frame, o offset para os dados, o tipo de frame (se é um frame AMQP ou SASL) e no caso de um frame AMQP, os últimos 2 bytes correspondem ao número do canal.
- Extender Header, que é uma extensão variável do header para uso futuro e que dependerá do tipo de frame (frames AMQP não utilizam esta extensão do header).
- E, Frame Body, que contém, as informações especifícas do frame. O corpo de frames AMQP consiste numa performativa e no payload específico desta. A presença e formato do payload depende da performativa.

![Untitled](Untitled%2035.png)

Frames sem body podem ser utilizados como pings de modo a evitar que a conexão seja terminada após o intervalo negociado (timeout).

O corpo dos frames segue esquemas definidos com o sistema de tipos do protocolo AMQP.

## Conexões

Conexões AMQP são formadas por canais unidirecionais. Cada canal possui um *connection endpoint* associado que pode ser do tipo *incoming* ou *outgoing*. Cada *connection endpoint* tem de estar associado a um número de canal único dentro da conexão. 

Uma conexão é aberta através da troca de o*pen frames.* Estes frames são utilizados para descrever as capacidades e limitações do *peer.* Exemplos de informações enviadas são: o tamanho máximo de frame suportado; e o número de canais máximo.

A conexão é fechada através da troca de *close frames.* Um dos peers envia um *close frame* indicando também a razão pela qual está a fechar a conexão, e depois aguarda pela receção de um *close frame* oriundo do outro *peer*, ou por um *timeout* ao fim do qual termina a conexão do mecanismo de transporte.

A conexão pode ser também terminada após ser atingido o *idle timeout* negociado entre os *peers.* Neste caso, o peer deve enviar um *close frame* a indicar a razão de estar a fechar a conexão. Se não receber uma resposta dentro de um tempo limite então pode fechar a conexão.

## Sessões

Sessões são conversas sequenciais e bidirecionais entre containers. As sessões agrupam um número arbitrário de links com qualquer direção. Um link apenas pode estar associado a uma sessão. As mensagens transferidas sobre um link são identificadas sequencialmente dentro da sessão (e não dentro do link), ou seja, os links não são completamente independentes entre si dentro da sessão, os links partilham uma sequência de entrega. Esta partilha serve para possibilitar a confirmação de receção das mensagens de forma eficiente.

Sessões são estabelecidas com a criação de um *session endpoint*. Para criar um session endpoint começa-se por selecionar um número de canal não utilizado. A esse número de canal associa-se um outgoing channel pelo qual se envia um *begin frame.* O outro participante ao receber este frame, procura por um número de canal não utilizado para poder associar à sessão. Após encontrar esse número de canal, cria também um outgoing channel sobre o qual envia um *begin frame* para informar qual foi o outgoing channel ao qual o incoming channel deve ficar associado.

Sessões são terminadas automaticamente quando a conexão é fechada ou interrompida, ou pode também ser terminada com a troca explícita de frames do tipo *end.* Assim como para as conexões, estes frames devem ser acompanhadas da razão pela qual a sessão foi terminada.

As sessões associam a cada *frame* do tipo *transfer* um *transfer-id.* A sequência de transfer-ids é necessária para o mecanismo de controlo de fluxo que as sessões possuem. Este mecanismo é baseado numa janela de *transfer frames.* Através do tamanho atribuído a esta janela e com o tamanho máximo dos frames, negociado ao estabelecer a conexão, é possível controlar os bytes on-the-fly. 

## Links

Um link serve para a transferência de mensagens unidirecional entre um *source* e um *target*. O protocolo AMQP permite dois tipos de entregas das mensagens: *settled* e *unsettled*. Entregas *settled*  são entregas do tipo fire-and-forget, em que a mensagem é enviada e o registo desta é eliminado de seguida, i.e., o link não quer ouvir mais nada sobre esta mensagem. Já as entregas *unsettled* requerem que seja confirmado que a mensagem foi recebida e ainda que seja indicado o que é que foi ou não foi feito com a mensagem.

Os links (o estado) podem ser feitos duráveis e recuperados no caso da falha da conexão ou sessão que está por baixo. A recuperação do estado dos links é essencial para satisfazer as garantias de entrega. São atribuídos nomes aos links de forma a permitir a sua recuperação (Já que os nomes têm tamanho arbitrário, após a operação de *attach* do link à sessão, um *handle,* identificador pequeno, é atribuído ao link).

Links são estabelecidos ou recuperados através da criação de um *link endpoint,* associado a um terminal local (*source* ou *target*), seguida da troca de *attach frames*. Os *attach frames* carregam informação sobre o estado do novo *link endpoint* e o tipo do terminal local (se é uma *source* ou uma *target*). Ao receber um *attach frame* um link endpoint é criado utilizando um terminal oposto (se o transmissor do attach frame criou um *link endpoint* com um terminal do tipo *source*, então o recetor tem de criar um endpoint com um terminal do tipo *target*). O *attach frame* possui a informação necessária para encontrar o terminal local que possui o estado que permite recuperar o *link*. O estado necessário para recuperar o link consiste essencialmente na informação sobre o estado de settlement das mensagens recebidas/enviadas (depende do tipo de terminal).

Um *peer* pode optar por terminar um link (eliminar o estado associado ao endpoint e libertar os recursos atribuídos a este). Esta terminação é feita através da troca de *detach frames* com a propriedade “Closed” a true*.* Se a propriedade “Closed” não for fornecida ou possuir o valor “False”, então o estado do *link endpoint* não será libertado. Dito isto, é possível dar *detach* do link e de seguida efetuar um novo *attach* para comunicar a atualização do estado do *link endpoint*. 

Para além do mecanismo de controlo de fluxo das sessões, existe ainda um mecanismo de controlo de fluxo dos links. Este mecanismo de controlo de fluxo é baseado em créditos, e serve para a aplicação controlar o envio de mensagens. Com este mecanismo é possível pedir explicitamente uma mensagem, permitir que um certo número de mensagens sejam enviadas previamente para que quando o momento de processar mensagens chegar já existirem mensagens para processamento e não ser necessário requisitá-las, pedir para que todas as mensagens disponíveis no transmissor sejam enviadas, entre outros tipos de gestões que podem ser realizados com este mecanismo. Enquanto que o mecanismo de controlo de fluxo das sessões serve essencialmente para proteger a plataforma da exaustão dos seus recursos, este mecanismo é utilizado para a aplicação requisitar apenas as mensagens quando necessita delas. É possível também num dado momento remover todos os créditos de forma a interromper o envio de mensagens.

### Transferência de mensagens

A transferência de mensagens entre as aplicações é iniciada com *transfer frames*. As mensagens têm tags identificadores que permitem acompanhar o estado de entrega das mensagens. Um conceito importante associado às entregas das mensagens é o settlement que permite assegurar diferentes garantias de entrega. Uma mensagem é considerada unsettled no transmissor/recetor até esta ser marcada como *settled* pela aplicação transmissora/recetora. 

Quando uma transferência é iniciada, o transmissor cria uma entrada para a mensagem num mapa respetivo às *unsettled messages* e envia um *transfer frame* que inclui a tag associada á mensagem, o estado inicial da entrega e os dados da mensagem. 

Quando o *transfer frame* é recebido, o recetor cria uma entrada no seu *unsettled map* e disponibiliza a mensagem transferida para processamento por parte da aplicação.

Após ser notificada da receção da mensagem, a aplicação processa a mensagem e atualiza o estado da entrega. O estado de entrega pode ser terminal ou não-terminal dependendo se o processamento da mensagem já foi finalizado ou se o estado poderá ser atualizado mais tarde. Um estado terminal indica se o processamento (já finalizado) foi bem sucedido ou se não, ditando de certa forma o comportamento que o transmissor terá quando receber a atualização do estado. Um estado não terminal é utilizado, por exemplo, no caso de mensagens grandes (divididas em vários transfer frames) ou no caso de transações. A atualização do estado é informada através de *disposition frames*. 

Aquando da receção da atualização do estado enviada pelo recetor, o transmissor atualiza o esatado localmente (se ainda não tiver atigindo um estado terminal, como no caso da entrega da mensagem expirar com um TTL [time-to-live] ) e comunica este à aplicação.

Voltando ao conceito de settlement, quando uma mensagem é marcada como *settled*, seja no transmissor ou no recetor, esta resulta na eliminação do estado referente à mensagem presente no *unsettled map.* O ato de enviar um *frame, por parte do* transmissor/recetor, a indicar que a mensagem foi *settled* resulta no esquecimento total da informação sobre a mensagem, e portanto, caso este *frame* seja perdido devido a uma interrupção na comunicação, o outro participante apenas se aperceberá de que a mensagem foi settled durante a recuperação do link quando ambos os participantes trocarem os estados respetivos às *unsettled messages*. Neste caso, a ausência de estado relativo a uma mensagem no mapa do outro participante indica que esta foi *settled.* Apercebendo-se da ausência o estado pode ser atualizado para representar que a mensagem foi *settled,* segue-se a notificação da aplicação e por fim uma ação final sobre a mensagem que inclui a remoção da mensagem do *unsettled map.*

O conceito de settlement permite a escolha de entre 3 garantias de entrega: at-most-once, at-least-once e exactly-once. 

Para fornecer a garantia at-most-once, o transmissor envia no *transfer frame* a propriedade “settled” como “true” e esquecendo de imediato a mensagem. Não existe a necessidade do envio de um *disposition frame* por parte do recetor já que o transmissor não se lembrará da mensagem.  

A garantia at-least-once é *settled* por parte do recetor. Após a receção do *transfer frame* (com propriedade “settled” com valor “false”) e do processamento da mensagem, o recetor envia um *disposition frame* com a propriedade “settled” a “true”.

Por fim, a garantia exactly-once é *settled* pelo transmissor. A sequência de frames trocados consiste num *transfer frame* enviado pelo transmissor(”settled” = false), de seguida um *disposition frame*  oriundo do recetor (”settled” = false) e finalmente um *disposition frame* enviado pelo transmissor com a propriedade “settled” com valor “true”.

A garantia de entrega pode ser definida por link ou por mensagem. No entanto, é possível que uma mensagem seja *settled* pelo transmissor independentemente de se o recetor chegou a um estado terminal devido ao TTL da mensagem expirar.

# Camada de Messaging

A camada de messaging é contruída sobre o o sistema de tipos e a camada de transporte referidas anteriormente.

## Formato das mensagens

AMQP define *bare message* como a mensagem que é fornecida pelo transmissor. Esta distinção é feita já que no caminho até ao recetor várias anotações podem ser adicionadas à mensagem, e portanto surge a necessidade de distinguir o que é imutável do que não é. O termo *annotated message* é usado para definir como a mensagem é vista no recetor. 

A *bare message* consiste em 3 secções: propriedades pré-definidas (default), propriedades da aplicação e os dados da aplicação (body).

Uma *annotated message* consiste na *bare message* mais as secções com anotações na cabeça e cauda da *bare message*. As anotações podem ser divididas em dois tipos: anotações que são consumidas no próximo nodo e anotações que permanecem com a mensagem indefinidamente.

![Untitled](Untitled%2036.png)

Na figura acima podemos verificar as diferentes secções que podem constituir uma *annotated message* e também as que podem constituir uma *bare message.* Com a exceção da secção application-data, nenhuma das outras secções necessita existir. 

O header carrega detalhes de entrega pré-definidos relativos à transferência da mensagem sobre a rede AMQP, como durabilidade, ttl, prioridade, first-acquirer e delivery-count (nº de tentativas de entregas que não foram bem sucedidas, útil para detetar duplicados). 

A secção *delivery-annotations* é utilizada para carregar anotações não padrão relacionadas com a entrega da mensagem desde um peer transmissor até um peer recetor.

A secção *message-annotations* carrega propriedades da mensagem direcionadas à infraestrutura e portanto devem ser propagadas em todos os passos da transferência da mensagem.

*Properties* é uma secção da *bare message* utilizada para definir um conjunto de propriedades pré-definidas da mensagem. Algumas das propriedades definidas nesta secção são: *message-id (identificador globalmente único), reply-to* (endereço do nodo para onde as respostas devem ser enviadas)*, correlation-id* (identificador de correlação para identificar clientes)*, content-type, content-encoding,* etc*.*

A secção *application-properties* é uma secçção destinada a dados estruturadas da aplicação. Estes dados podem ser lidos por intermediários para efeitos de filtragem e encaminhamento.

A secção “application-data” corresponde ao corpo (body) da mensagem. De todas as secções, esta é a única que tem de existir. O corpo da mensagem pode apresentar um dos formatos seguintes: uma ou mais secções de *data*, uma ou mais secções *amqp-sequence,* ou uma única secção *amqp-value. data* é uma secção de dados binários. *amqp-sequence* é uma sequência (lista) estruturada de elementos de dados. *amqp-value* corresponde a um único valor AMQP.

Por fim, a secção *footer* transporta informação relativa à mensagem ou entrega que apenas podem ser calculados depois da bare message estar completamente construída. Exemplos de informações presentes nesta secção são hashes, assinaturas, detalhes de encriptação, etc. 

## Nodos de distribuição

A camada *Messaging* define a existência de nodos de distribuição e um conjunto de estados para estes. Nodos de distribuição, como o nome sugere, são nodos desenvolvidos para o armazenamento e distribuição de mensagens. Estes nodos são responsáveis por encaminhar as mensagens recebidas de produtores (transmissores) para consumidores (recetores) com base nas propriedades e atributos associados às mensagens (como “to”, “subject” ,”reply-to”, etc). 

O estado das mensagens, para estes nodos, transicioname entre AVAILABLE, ACQUIRED e ARCHIVED. 

Uma mensagem começa inicialmente no estado AVAILABLE. O estado muda para ACQUIRED imediatamente antes de ser iniciada uma transferência de aquisição (acquiring transfer). Com este mudança de estado, a mensagem deixa de poder ser transferida por outros links. 

O estado, no caso geral, transionará de ACQUIRED para outro estado quando a transferência for *settled.* O próximo estado depende do **estado terminal da transferência. Se o estado terminal for RELEASED ou MODIFIED a mensagem voltará a ficar AVAILABLE. Se for ACCEPTED ou REJECTED então a transição será para o estado ARCHIVED. 

Existe ainda a possibilidade de uma transição de estado ocorrer espontaneamente no nodo de distribuição*.* Esta situação ocorre quando o TTL da mensagem expira, resultando na mudança do estado da mensagem para ARCHIVED independentemente do estado atual (AVAILABLE ou ACQUIRED).

## Estados de Entrega

O protocolo de transferência de mensagens define um conjunto de estados de entrega que podem ser enviados através de *disposition frames* para indicar o estado de entrega no recetor. Estes estados podem ser terminais ou não-terminais. O envio de um estado terminal indica que o estado não sofrerá mais alterações.

Os estados terminais ou resultados (outcomes) definidos por esta camada resultam do processamente realizado no recetor, e pode ser um dos seguintes: *accepted, rejected, released, modified.*

O estado *accepted* indica que o recetor processou a mensagem com sucesso.

O estado *rejected* expressa que o recetor não foi capaz de processar a mensagem pelo facto de estar ser inválida.

O estado *released* indica que a mensagem foi libertada, deixando de ser da responsabilidade do recetor o seu processamento, e portanto pode ser transmitida novamente para o mesmo(s) ou para outro(s) destino(s).

O estado *modified* indica que a mensagem foi modificada mas não foi processada. As informações relativas às modificações da mensagem são informadas nos campos respetivos a este estado terminal. Este estado serve para indicar as alterações que o transmissor deve aplicar na mensagem para que esta possa ser processada.

Existe apenas um estado não-terminal, designado por *received,* e que serve para situações de envio de grandes mensagens (que não conseguem ser enviadas num único *transfer frame*). Este estado é essencial para evitar que todos os dados sejam retransmitidos, como em situações de recuperação de links.

## Filtragem

O terminal do tipo *source* pode restringir as mensagens que são transmitidas através da definição de um conjunto de filtros. Se a mensagem passar por todos os filtros estabelecidos, então a mensagem pode ser enviada por o(s) link(s) associados a este terminal. Os filtros podem ser baseados em propriedades, anotações ou até do conteúdo das mensagens.

## Modo de distribuição

Terminais do tipo source têm de definir um modo de distribuição. Estes modos influenciam a maneira como as mensagens são distribuídas sobre o link associado à respetiva *source*. Existem dois tipos de modos de distribuição: *move* e *copy.*

O modo de distribuição *move* consiste na transferência de mensagens de um nodo para outro. Assim que a transferência é realizada com sucesso, a mensagem deixa de estar disponível no nodo transmissor, e portanto a responsabilidade (de entrega) sobre a mensagem é também transferida para o recetor. No caso de um nodo de distribuição, assim que a mensagem tenha sido encaminhada com sucesso, o estado transitaria para ARCHIVED, impedindo que a mensagem seja enviada por outro link.

O modo de distribuição *copy* consiste essencialmente na duplicação da mensagem podendo várias cópias desta serem enviadas para múltiplos destinos (após a transferência ficar settled para um certo destino, a mensagem não volta a ser enviada para esse destino). **Essencialmente, a mensagem permanece inalterada no transmissor, ficando disponivel para ser enviada para outros destinos, mesmo após ter sido efetuada uma transferência bem sucedida para um nodo recetor (target).

# Transações

AMQP possui um mecanismo que permite a declaração de transações para a coordenar o resultado entre múltiplas transferências. Estas transações podem acomodar um número arbitrário de transferências realizadas em qualquer direçãoe sobre múltiplos links.

A realização das transações depende das capacidades das implementações utilizadas. Uma implementação pode não suportar transações, pode suportar transações locais ou até mesmo transações distribuídas.

Para uma transação local, um *container* atua como controlador da transação (transactional controller) e outro atua como um recurso transacional (transactional resource) que realiza trabalho requisitado pelo controlador. Uma transação distribuída possui um único controlador e múltiplos recursos (i.e. múltiplos containers).

Para simplificação a explicação será feita para uma transação local, ou seja, apenas um controlador e um recurso transacional.

Um controlador transacional e um recurso transacional comunicam entre si através de um link controlador (control link) estabelecido pelo controlador. Por este link são enviadas mensagens *declare* (para declarar transações) e *discharge* (para dar commit ou rollback das transações). Cada operação transacional pode ocorrer num link arbitrário da sessão ou conexão controladora, dependendo das capacidades do controlador. Todas as transações que não tiverem sido completadas Quando o link controlador é fechado, deixa de ser possível realizar mais trabalho sobre transações não completadas para além de estas serem rollbacked.

O recurso transacional define um terminal especial do tipo target que irá funcionar como *coordinator.* Para iniciar uma transação, o controlador transacional pede um identificador de transação ao recurso transacional, a partir de um link associado a este *coordinator,* através de uma mensagem do tipo *declare*. Se a declaração for bem sucedida, então o coordinator responde com um frame do tipo disposition que contém o identificador e o outcome “declared”. Caso contrário, o disposition frame deve conter uma secção “trnasaction-error” a informar o erro.

Para finalizar uma unidade de trabalho transacional, o controlar envia uma mensagem *discharge* para o *coordinator.* Nesta mensagem é indicado num campo “fail” se a transação deve ser *commited* ou *rollbacked*. Se a operação for bem sucedida, o coordinator envia um disposition frame a indicar o estado, caso contrário, o erro é informado usando a secção “transaction-error”.  

Os tipos de operações realizadas sobre as transações são:

- posting messages (transfer frames), o que se traduz em fazer uma mensagem ficar disponível no destino
- acquiring messages (flow frames), que se traduz na requisição de mensagens
- retiring messages (disposition frames), que se traduz na aplicação do outcome terminal sobre a mensagem.

As operações são associadas a uma transação através de uma secção “transactional-state” que possui o id da transação e pode ainda conter um estado terminal. Este estado transacional é transportando tanto em *transfer* e *disposition frames.* No caso de *flow frames,* apenas os que são enviados do controlador para o recurso podem possuir a identificador da transação presente nas propriedades. O recurso transacional ao receber um flow frame com um id de transação associado, adiciona ao *outgoing link endpoint* (referido no flow frame) um pedaço adicional de estado, o id de transação, que resulta na associação das mensagens enviadas por esse endpoint com a transação identificada pelo id.  Esta associação é removida quando a transação é discharged.

Mensagens enviadas dentro de uma transação apenas são disponibilizadas para processamento pela aplicação após o *commit* com sucesso *d*a transação. Se o *discharge* resultar num rollback, o estado associado às mensagens volta ao estado anterior (podendo ser inexistente), e portanto, as mensagens não são disponibilizadas para processamento pela aplicação recetora.

# Segurança

Essencialmente, TLS para encriptação e SASL para autenticacao e autorizacao.

# Padrões de Comunicação

Ver que padrões conseguem ser implementados com AMQP.

Falar bem das propriedades predefinidas que permitem a implementacao desses padroes

---

# Referências

[https://www.youtube.com/playlist?list=PLmE4bZU0qx-wAP02i0I7PJWvDWoCytEjD](https://www.youtube.com/playlist?list=PLmE4bZU0qx-wAP02i0I7PJWvDWoCytEjD) (contribuidor principal)

[https://www.amqp.org/](https://www.amqp.org/)

[https://docs.oasis-open.org/amqp/core/v1.0/amqp-core-complete-v1.0.pdf](https://docs.oasis-open.org/amqp/core/v1.0/amqp-core-complete-v1.0.pdf)