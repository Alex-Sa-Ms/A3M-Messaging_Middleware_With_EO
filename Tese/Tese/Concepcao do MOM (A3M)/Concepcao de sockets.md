# Paginas relacionas
- [[Concepcao de mecanismo de poll]]
- [[Concepcao do protocolo de comunicacao]]
# Descrição
- Nodos são contentores de sockets.
- Cada socket possui uma etiqueta. Uma etiqueta é um identificador que distingue o socket unicamente dentro do nodo a que pertence. Designemos este por *TagID* ou *LocalSocketID*. 
- O identificador global de um socket, *SocketID* ou *GlobalSocketID*, corresponde à combinação do identificador do nodo a que o socket pertence (NodeID) com o identificador local do socket (TagID).
- A comunicação entre sockets será realizada utilizando *SocketID*s.
- Para que os sockets possam comunicar entre si e de modo a verificar a compatibilidade entre os sockets de alto nível, a existência de um *handshake* é desejável. O método que inicia esta associação pode ser chamado de `link()`. `unlink()`deve cancelar uma associação, mas apenas pode ser invocado após a associação ser criada com sucesso. 
- Com a existência do handshake, é essencial que as mensagens contenham a identificação do socket fonte para que a informação do socket possa ser acedida (permite confirmar se é uma fonte válida). 
- A identificação do socket destino deve ser incluída nas mensagens para permitir que o nodo destino consiga encaminhar a mensagem para o socket correto.
- Essencialmente, um socket genérico é responsável por enviar mensagens para sockets e por servir como uma *message box* ou *incoming queue*, permitindo assim a receção de mensagens.
# Objetivos
O objetivo é fazer a concepção base da arquitetura do middleware que permita criar sockets de alto nível e possibilitar a comunicação entre estes.
Antes de pensar na criação de sockets de alto nível é necessário criar métodos que permitam enviar e receber mensagens, tendo sockets como fontes e destinos, garantindo que as mensagens são entregues exatamente uma vez. Além disso, convém existir um mecanismo de controlo de fluxo para limitar e controlar o fluxo gerado por cada socket. Todas as decisões tomadas na concepção da arquitetura devem ter em mente a garantia de entrega Exactly-once que implica que as mensagens não sejam descartadas até que sejam entregues. Isto implica que o controlo de fluxo não deve resultar no descarte das mensagens, mas sim no bloqueio das threads até que estas recebam permissão para enviar as mensagens.
## Resumo dos objetivos
1. Criar socket genérico
	1. Permitir o envio de mensagens para outros sockets
	2. Permitir a receção de mensagens de outros sockets.
	3. Controlar o fluxo de envio de mensagens.
	4. Garantir que mensagens são entregues exatamente uma vez (não podem ser descartadas em nenhum momento)
# Problemas
## Exactly-Once delivery guarantee
### Descrição
Necessário garantir que as mensagens são entregues exatamente uma vez ao socket destino, e caso aplicável, entregues exatamente uma vez à aplicação pelo socket destino.
### Solução
A solução é:
	1. Utilizar a biblioteca Exon.
		- Isto garante que as mensagens são entregues exatamente uma vez ao nodo destino 
	2. As instância do middleware que interagirem com a mensagem (instância fonte e instância destino) devem ficar bloqueadas até que o controlo de fluxo permita o envio da mensagem.
		- Assim evita-se que mensagens sejam descartadas quando o controlo de fluxo não permite o envio.
		- Threads clientes bloqueadas são desta forma controladas e impedidas de gerar mais mensagens quando o mecanismo de controlo de fluxo não permite o envio de mais mensagens para o dado socket.
## Eficiência
### Descrição
A seguir a garantir *Exactly-once delivery*, o principal problema ao desenvolver o middleware é encontrar uma solução que forneça as funcionalidades necessárias da forma mais eficiente possível.
### Solução
A solução para este problema é evitar sincronização, contenção e trocas de contexto de threads onde for possível. Funcionalidades que possam ser executadas pelas threads clientes devem ser executadas por elas em vez de adicionar mais um ponto de sincronização (em que se transfere a tarefa de uma thread cliente para uma thread do middleware[^1]).

[^1] Consideremos o exemplo do envio de uma mensagem. A thread cliente pretende enviar uma mensagem para um socket, no entanto, dada a garantia de entrega Exactly-Once, esta thread tem de ficar bloqueada até que o mecanismo de fluxo permita o envio da mensagem, desse modo a thread cliente não consegue gerar mais mensagens enquanto a atual não tiver sido enviada. Se a thread cliente tem de ficar bloqueada até que exista a confirmação do envio, então não há necessidade de criar mais threads do middleware para tratarem das operações de envio, resultando em menor desempenho e num maior consumo de memória.
## Enviar mensagem para outro socket
### Descrição
Sendo os sockets os intervenientes principais na comunicação fornecida pelo middleware, então é necessário que estes sejam capazes de enviar mensagens para outros sockets, estejam estes num nodo remoto ou no próprio nodo.
### Problemas relacionados
- [[Concepcao de sockets#Exactly-Once delivery guarantee]]
- [[Concepcao de sockets#Eficiência]]
- [[Concepcao de sockets#Controlo de fluxo de envio]]
### Solução
Tendo em conta os problemas relacionados, a solução ideal consiste em:
- Thread cliente deve executar toda a lógica até que a mensagem seja entregue ao Exon.
	- A responsabilidade passa a ser do Exon que a deve entregar ao nodo destino.
- 3 métodos de envio devem ser fornecidos pelo socket genérico:
	- `send(m : Message, destID : SocketIdentifer)` - método bloqueante para envio de uma mensagem
	- `trySend(m : Message, destID : SocketIdentifier) : boolean` - método não bloqueante que tenta enviar a mensagem.
		- Retorna 'true' se a mensagem for enviada com sucesso, ou 'false' se o mecanismo de controlo de fluxo não permite que a mensagem seja enviada de imediato.
	- `trySend(m : Message, destID : SocketIdentifier, tout : long)` - método semelhante ao acima mas que aguarda um intervalo de tempo (em milissegundos) pela permissão para enviar.
		- Não tendo recibo permissão para enviar a mensagem dentro do intervalo de tempo, *tout*, então retorna com 'false' para indicar que a mensagem não foi enviada. Retorna 'true' se a mensagem foi entregue dentro do intervalo de tempo fornecido.
## Controlo de fluxo de envio <span style="color:red;">(TODO)</span>
- Não esquecer de falar que é necessário ter uma thread atenta aos recibos emitidos pelo Exon para atualizar a janela do controlo de fluxo dos sockets. Cada socket guarda os identificadores das suas mensagens enviadas e trata do controlo de fluxo como pretender.
## Como fazer as mensagens chegar ao destino
### Descrição
Mensagens enviadas para um socket precisam de ser entregues a esse socket para que possam ser posteriormente acedidas e processadas.

O percurso percorrido por uma mensagem é o seguinte: 
	**(1) Aplicação Fonte ->  Socket Fonte**
	**(2) Socket Fonte -> Instância Exon Fonte**
	**(3) Instância Exon Fonte -> Instância Exon Destino**
	**(4) Instância Exon Destino -> Socket Destino**
	**(5) Socket Destino -> Aplicação Destino**

Os passos (1) e (2) foram descritos em [[Concepcao de sockets#Enviar mensagem para outro socket]]. O passo (3) é responsabilidade do Exon. Este problema pretende abordar os passos (4) e (5), de forma a definir qual o processo pelo qual uma mensagem deve passar desde que chega ao nodo destino até que é entregue ao seu destino. Podem existir mensagens direcionadas a um socket (mensagens de controlo) e mensagens cujo destino é uma aplicação (mensagens aplicacionais).
### Solução
A primeira tarefa a realizar consiste em receber as mensagens que chegam ao nodo pela instância do Exon. O Exon é um ponto central na arquitetura logo apenas utilizaremos uma thread do middleware, designada de *Reader Thread*, para receber as mensagens do Exon e entregá-las ao socket destino. A entrega das mensagens ao socket destino é acompanhada da lógica de processamento definida por esse socket. A carga computacional destas operações é suposto ser muito baixa e deve evitar operações bloqueantes[^1] que afetem o desempenho da thread do middleware. A entrega da mensagem, no caso de esta ser uma mensagem aplicacional, deve incluir a preparação para a sua receção por parte da aplicação, seja esta feita através de um método `receive()` do socket, ou através de um *poller*.

[^1] Existindo necessidade de implementar lógica bloqueante, o socket ao ser inicializado deve criar os recursos (threads) necessários para tratar da lógica bloqueante que for necessária e assim evitar o bloqueio da thread do middleware.

~~Para além de uma thread do middleware para distribuir as mensagens recebidas, é também preciso que os sockets possuam um mecanismo para controlo de concorrência (*lock* por exemplo). Este mecanismo será utilizado para evitar *race conditions* entre threads que pretendam ~~
- Não é necessário o lock aqui porque a reader thread é responsável pelo processamento da mensagem necessário antes de uma mensagem ficar pronta para ser entregue à aplicação.

## Como é que um socket recebe mensagens (de forma genérica)
### Descrição
Como referido a Reader Thread é responsável por entregar as mensagens recebidas ao socket destino (incluído nas mensagens). A entrega inclui a lógica de processamento do socket para essa mensagem. Como um dos objetivos do middleware é permitir a extensibilidade do middleware no que diz respeito a protocolos de comunicação, é necessário encontrar uma forma genérica de entregar as mensagens aos diferentes tipos de sockets que possam vir a ser criados.
### Solução
Para entregar uma mensagem a um socket e provocar o processamento desta, é necessário o socket possuir um método de notificação, `receivedMessage(m : message)` para que a reader thread possa entregar as mensagens recebidas.
### Notas
Ao promover a extensibilidade existe o risco que o utilizador crie sockets cuja implementação do método `receivedMessage(m)` possua operações bloqueantes. Estas operações bloqueantes vão atrasar a reader thread e até poderão resultar em deadlocks. Apesar de existir este risco, dado que a criação de novos sockets é uma funcionalidade avançada, fica da responsabilidade do utilizador evitar executar esse tipo de tarefas no método `receivedMessage(m)`. Se pretender executar este tipo de tarefas deve optar por encontrar outra solução, como criar threads na inicialização do socket que possam realizar essas tarefas.  
## Forçar comportamento genérico
**Problemas:**
- Forçar comportamento genérico
- Injeção de comportamento customizado pre-send, post-send e post-receive.
- Partilhar o lock do comportamento genérico com o comportamento customizado para realizar operações atómicas, ou adquirir o lock inicialmente e libertar após a execução do comportamento customizado?
- É possível fornecer APIs dos sockets restritas, sem ser como as do ZeroMQ que aparece todos os métodos?
	- Exemplos:
		- Num socket REQ aparece os métodos subscribe mas não são utilizáveis. 
		- Num socket PULL aparece o método send() mas não é utilizável. 
		- Etc...

## Como é que um socket envia mensagens (de forma genérica)
### Descrição
Os sockets, independentemente do protocolo de comunicação, precisa de enviar mensagens (pelo menos as mensagens de controlo), logo, é desejável encontrar uma forma genérica para o envio de mensagens por parte dos sockets.
### Solução


## Aceder a mensagens recebidas por um socket

## Thread-safe sockets
## Exigir handshake
- Ter um handler predefinido é boa ideia, tendo apenas em conta o tipo de socket que fez o contacto, mas permitir a extensibilidade é algo ideal, no entanto, esta parte da compatibilidade deve ser exigida, portanto tentar fazer algo como o padrão de concepção Template?

## Message Box e Selective Receive
- Faz sentido o socket genérico servir como message box e permitir selective receive?
- Como é que o selective receive funcionaria para permitir uma programação reativa?
## Pollers

# Ideias
- Criação de **grupos**
	- Sockets poderem inscrever-se em grupos. 
	- Mensagens passam a poder a ser enviadas para grupos de sockets. 
	- E para o grupo pode ser escolhida a estratégia de entrega: entregar a mensagem a todos os sockets do grupo; entregar apenas a um socket; etc.
# Observações
- Apesar de os sockets serem *thread-safe* não é possível "bloquear" a etiqueta dos sockets remotos[^1] e utilizar apenas o identificador do nodo para diferenciar. Isto porque dependendo do tipo de padrão podem existir diferentes tipos de sockets compatíveis entre si. Para além de que pode se pretender utilizar múltiplos sockets com o mesmo propósito mas que não se pretende que sejam partilhados por múltiplas threads.

[^1] Com bloquear a etiqueta, pretendo passar a ideia de que ao criar-se um socket indicaria-se o nome que os nodos remotos compatíveis deveriam ter para que as suas mensagens fossem aceites.
# Requisitos
## Requisitos para envio de mensagens
### Permitir o envio de mensagens, de qualquer tipo, para outro nodo
- Tem de existir um formato básico das mensagens para que a mensagem seja aceitada no nodo destino. Caso contrário o nodo destino descartará a mensagem por não conseguir processá-la.
	- Criar uma classe responsável pela construção e verificação do formato das mensagens.
	- Esta classe permite assegurar que as mensagens são criadas corretamente, para além de permitir que na receção de mensagens se possa descartar as mensagens que não seguem o formato correto.
- Necessário criar uma classe que converte as mensagens para um array de bytes e as envia através de uma instância do Exon.
### Permitir enviar mensagens com um nodo como destino
- Classe de construção de mensagens deve permitir criar mensagens que indicam que se trata de uma mensagem direcionada ao nodo.
### Permitir enviar mensagens com um socket como destino
- Classe de construção de mensagens deve permitir criar mensagens que indicam que se trata de uma mensagem direcionada a um socket.
- Deve existir uma classe que serve de "porta" para um socket poder enviar as mensagens. Esta porta tem de estar obviamente conectada à instância do middleware com que se criou o socket.
### Permitir definir/alterar limite para o número de mensagens que podem estar em trânsito em simultâneo (limite global)
- Necessário um semáforo global que deve ser adquirido antes de enviar cada mensagem.
- Este método deve ser exposto pela instância do middleware.
### Permitir definir/alterar limite para o número de mensagens que podem estar em trânsito para cada socket
- Necessário associar um semáforo a cada socket que deve ser adquirido antes de enviar cada mensagem que tem esse socket como fonte.
- Este método deve ser exposto pela instância do socket ou pelo middleware?
- (Isto permite definir prioridade do tráfego dos sockets)
### Não podem ser descartadas mensagens (Devido à garantia de entrega Exactly-Once)
- Métodos de envio de mensagens devem ser bloqueantes.
### Mensagens direcionadas a um socket do próprio nodo não devem recorrer ao Exon
- Desnecessário delegar a mensagem para o Exon quando a mensagem é direcionada a um socket do próprio nodo
- Trade-off:
	- Maior overhead a enviar para nodos externos devido a uma condição *if* adicional.
	- Menor overhead a enviar para nodos internos já que não é preciso recorrer ao algoritmo de exactly-once do Exon.
### Controlo de fluxo na receção
- O Exon quando detecta que a queue de entrega está cheia deixa de aceitar mensagens. De modo a aproveitar este mecanismo e impedir que ocorram problemas por falta de memória, deve ser imposto um limite que bloqueie a reader thread de ler mais mensagens quando as event threads já se encontram demasiado ocupadas.

- **Solução:**
	- Definir um tamanho limite na queue dos eventos.
	- Deste modo, pode-se utilizar o método **offer()** para verificar se existe espaço para adicionar um novo evento de leitura.
	- Não existindo espaço, a reader thread guarda o evento de leitura e continua a processar recibos de receção até que exista espaço na queue de eventos.
	- Após ser detectado que já existe espaço, a reader thread entrega o evento guardado e retoma a leitura de mensagens.
	- Para que isto funcione corretamente, é importante criar uma queue que seja capaz de estabelecer níveis de prioridade para os eventos. Já que impedir que novos eventos de leitura sejam adicionados mas continuar a permitir que eventos de envio sejam criados não faz muito sentido. 
		- Facilmente realizado com o estabelecimento de porcentagens por tipo de evento, e utilizando contadores para verificar essas porcentagens antes de permitir que um evento seja adicionado.
		- Isto leva a que um evento de send() também passe pelo controlo de fluxo da queue de eventos e não só pelo controlo de fluxo de envio.
## Requisitos para receção de mensagens
### Permitir a receção de mensagens de qualquer tipo

## Requisitos de sockets
### Permitir criar sockets
- A instância do middleware deve possuir um método para criar sockets.
### Permitir registar sockets customizados
- A operação de registo deve registar uma fábrica de sockets (do tipo customizado) associando esse socket a um identificador que permitirá invocar o método de criação de sockets da instância do middleware.
### Permitir que os sockets sejam notificados de quando as suas mensagem chegaram ao destino.
- Preciso ter em conta que o recibo pode chegar depois de uma mensagem de resposta.
# Problemas
1. Threads clientes que pretendem enviar uma mensagem devem tratar de enviar a mensagem ou apenas submeter uma mensagem para envio?
	1. Se as threads clientes apenas submeterem a mensagem, pelo menos devem esperar que a mensagem passe pelos estágios de verificação da mensagem e de controlo de fluxo (responsável por permitir que a mensagem seja "agendada" para envio).
2. A lógica de um socket especializado pode exigir o envio de mensagens de controlo, ou seja, que não são geradas pela aplicação que utiliza o middleware. Como o envio de mensagens deve seguir a garantia de entrega Exactly-Once, o envio destas pode bloquear devido aos mecanismos de controlo de fluxo seja do próprio middleware ou do Exon. O problema está no facto de não ser uma thread cliente a ser bloqueada mas o facto de ser uma thread do middleware. Admitindo que se alocou apenas uma thread de processamento para o middleware, se a única thread capaz de processar eventos ficar bloqueada significa que o middleware fica essencialmente parado.
3. O padrão "Chain of Responsibility" pode ser utilizado para permitir definir um fluxo de envio/receção expansível aos quais se pode adicionar diferentes estágios quando pretendido.
	1. Por exemplo, consideremos que uma thread cliente invoca um método de envio de um socket. 
4. A ordem pelos quais uma mensagem passa são importantes. Se o método de `trySend()` for invocado, então a mensagem pode não vir a ser enviada, logo, a mensagem não deve passar pelo processamento especial do seu socket, já que exigiria rever as mudanças no estado do socket.
	- Utilizando uma versão do design pattern "Template", criar estágios para os diferentes estágios de envio pode ser uma solução. Pode-se criar um estágio preparatório que é executado logo após a invocação do método mas antes de passar pelo estágio de controlo de fluxo. Após ser confirmado que a mensagem será enviada, pode passar por um estágio que executa lógica que apenas pode ser executada depois de se confirmar que a mensagem será efetivamente enviada.
### Enviar mensagens de qualquer tipo (para socket ou para nodo)
- Reader Thread cria um evento de leitura para que as Event Threads processem a mensagem recebida.
- As Event Threads ao processarem o evento de leitura entregam a mensagem para a entidade responsável.
- No caso de ser uma mensagem direcionada para um socket, então a mensagem é entregue ao socket genérico associado com a etiqueta destino. O socket genérico ao receber a mensagem notifica os interessados (subscritores) que na fase de protótipo deve corresponder unicamente ao socket de alto nível.
	- A invocação do método **notify()** deve despoletar assim a execução da lógica necessária por parte do socket de alto nível.
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

### Como é que se pode criar forwarders/dispatchers? <span style="color:yellow">(Pensar melhor nisto)</span>
- Não sei como funciona exatamente no NNG, mas tenho ideia que existe um objeto especial que utiliza a versão *raw* dos sockets[^1].
- ...
[^1]A versão *raw* dos sockets ignora toda a lógica que a versão *cooked* executa.
### É possível evitar handshake entre sockets para verificação do tipo de socket?
**Objetivo do handshake:**
O principal objetivo de um handshake inicial é certificar que um certo socket existe e que este é do tipo expectável, i.e., compatível.

**Consequência do handshake:**
A consequência de um handshake inicial para verificação do tipo do socket remoto é que esta informação precisa de ser armazenada. Para prevenir que estes dados sejam armazenados permanentemente, o que torna a estrutura de dados *grow-only*, é necessário fornecer um método de eliminar estes dados quando é determinado pelo protocolo que já não haverá mais comunicação entre os dois sockets.

**Objetivo de evitar handshake:**
Evitar armazenamento de dados desnecessários já que o protocolo do padrão de comunicação podem guardar/requisitar estes dados se necessário.

**Soluções:**
1. Indicar o tipo de socket em todas as mensagens.
2. Ou, o tipo de mensagem permitir inferir se o socket fonte é de tipo compatível.

### Necessário exigir certos comportamentos
Por exemplo, um socket está fortemente relacionado com um nodo. Como uma instância do middleware representa um nodo, o socket deve ser criado através da instância do middleware de modo a que este fique registado de forma definitiva no middleware, não podendo ficar associado a outra instância do middleware.
### O que fazer a mensagens cujo socket destino não existe?
- Envia mensagem de erro para o socket fonte.
- O protocolo do socket pode solicitar uma retry assim que receber a mensagem de erro, se tiver a certeza que esse socket virá a existir mesmo que atualmente não esteja disponível.
- Permitir criar os sockets antes de iniciar o middleware. Basicamente retardar o início da reader thread que lê as mensagens do Exon até que o middleware esteja propriamente configurado e o início do seu funcionamento seja explicitamente requisitado. 
	- Ao retardar a leitura das mensagens até que tudo esteja preparado é possível assegurar que mensagens de erro resultantes de um socket ainda não ter sido iniciado não acontecem (a não ser em casos de programação errônea). 
### Delegar o trabalho de enviar mensagens para as worker threads
- Mecanismos de envio bloqueantes devem esperar até que o mecanismo de controlo de fluxo dê permissão para envio da mensagem. Depois apenas colocam a mensagem numa queue para serem processadas pelas worker threads do middleware. 
- Surge, no entanto, um **problema** em que a worker thread que faz o envio pode ficar bloqueada pelo controlo de fluxo do Exon.
	- Possíveis Soluções:
		1. Definir o valor de controlo de fluxo do Exon muito alto para não resultar no bloqueio das worker threads já que isso resultaria no atraso da realização de outras tarefas, só porque o nodo destino da mensagem está a receber demasiadas mensagens.
		2. No momento, o Exon possui duas variáveis associadas a controlo de fluxo por nodo: P e N. P define a janela de mensagens que pode estar em trânsito num dado momento. N é o número de envelopes máximo que um registo de receção pode ter num dado momento. N é definido como um múltiplo de P. Se removermos esta ligação, é possível tornar o P o valor que dita quantas mensagens o Exon pode ter em fila para enviar para um dado nodo, e o valor de N continua com o mesmo significado mas passa a ser este o valor que dita o controlo de fluxo e limita o nº de mensagens em trânsito para cada nodo.
### Event Threads e operações bloqueantes
- Como as event threads serão utilizadas para realizar tarefas intrínsecas ao middleware, como lógica dos sockets após receber ou enviar mensagens e outro tipo de tarefas, é necessário criar uma framework de IO assíncrono que permita adiar as tarefas que seguem uma tarefa bloqueante para quando a tarefa bloqueante terminar. O tipo de tarefas bloqueantes devem ser apenas as contempladas pelo middleware, como esperar pela receção de uma mensagem num socket, esperar pelo envio de uma mensagem, esperar um certo período de tempo antes de executar um callback, etc. Nos casos que for possível evitar o uso de operações bloqueantes, então devem ser evitadas.
# Controlo de fluxo
Para garantir o correto funcionamento da plataforma e para que esta não sofra de problemas de problemas relacionados com memória ou até de recursos computacionais, devem ser implementados mecanismos de controlo de fluxo tanto para controlar o envio como a receção de mensagens.
## Controlo de fluxo no envio
### Controlo de fluxo por socket local no envio 
Implementar um mecanismo de controlo de fluxo de envio nos sockets permite ajustar o tráfego produzido por cada socket. Isto é importante para que exista igual oportunidade no envio de mensagens entre os diferentes sockets mas também permite definir prioridades de tráfego. 
- Admitindo que temos dois sockets: A e B. Se definirmos os tamanhos das janelas dos sockets A e B, respetivamente, como 100 e 1000, é facilmente perceptível que o throughput do socket B pode ser 10 vezes maior do que o socket A, logo este controlo de fluxo permite definir, indiretamente, prioridade no tráfego.
### Controlo de fluxo por socket remoto no envio
Realizar esta tarefa não é de todo ideal já que exige guardar informação para todos os sockets remotos com que existe, existiu ou existirá comunicação. Como se pode perceber, é uma tarefa que resulta num crescente uso de memória e não é viável especialmente para peças estáticas da topologia (servidores) que podem vir a ser contactadas por grandes quantidades de nodos (clientes).
### Controlo de fluxo global no envio
<span style="color:red">Professor diz que não pensa ser necessário já que vai atrasar todos os envios e já existe a possibilidade de configurar cada socket. No entanto, não se contempla mensagens que não têm sockets como origem.</span>
Considerando que os próprios nodos podem querer trocar mensagens ou que podem vir a existir entidades diferentes de sockets que possam necessitar de enviar mensagens, é necessário existir um mecanismo de controlo de fluxo global que permita limitar a quantidade de mensagens que podem estar em trânsito[^1]. Uma janela de controlo de fluxo global configurável é essencial para que o mecanismo seja ajustável aos recursos dos diferentes dispositivos em que poderá ser utilizado e assim permitir a escolha de valores que não resultem na exaustão da memória ou de recursos computacionais.  

[^1] Uma mensagem fica em trânsito a partir do momento que é colocada numa queue para ser enviada para o destino até que é confirmada a sua receção no destino.
## Controlo de fluxo na receção
### Controlo de fluxo por socket remoto na receção
Mesma problema que "Controlo de fluxo por socket remoto no envio".
### Controlo de fluxo por socket local na receção
É possível implementar mas é muito complexo e é super ineficiente já que exige coordenação entre o socket local e todos os sockets remotos. Para além de exigir de que a coordenação exigiria que fosse guardada demasiada informação. 
### Controlo de fluxo global na receção
Apesar de não ser viável implementar uma solução de controlo de fluxo na receção a nível do socket, implementar um controlo de fluxo global para a receção permite salvaguardar a plataforma de problemas de recursos de armazenamento. O Exon aplica um limite para o número de mensagens que pode ter na queue de entrega (*delivery queue*). Atingindo esse limite, o Exon não aceita a chegada de mais mensagens, mas fá-lo de modo a continuar a garantir que as mensagens serão entregues exatamente uma vez. Ao permitir ajustar o tamanho da *delivery queue* do Exon e através da criação de uma janela para o número de mensagens que podem existir por ser processadas ou a ser processadas no middleware consegue-se proteger a plataforma da exaustão de memória (contando também com o mecanismo de controlo de fluxo global no envio).
- Por exemplo, se o tamanho da delivery queue for 100 000 mensagens e a janela de controlo de fluxo tiver um tamanho de 10 000 (10% do tamanho da delivery queue), mantém se um ritmo de leitura aceitável, por parte da reader thread, e assegura-se que apenas 100 000 + 10 000 = 110 000 mensagens podem existir por processar. Se o MTU do middleware for de 1000 bytes, pode-se contar com um uso máximo aproximado de 110 000 * 1000 bytes = 1,1 Gigabytes de memória para as mensagens recebidas (faltam os valores das estruturas em que as mensagens são guardadas).

## Cálculos de memória em função dos valores definidos para o controlo de fluxo

<span style="color:red">TODO: Realizar estas contas quando tiver os mecanismos bem definidos</span>

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