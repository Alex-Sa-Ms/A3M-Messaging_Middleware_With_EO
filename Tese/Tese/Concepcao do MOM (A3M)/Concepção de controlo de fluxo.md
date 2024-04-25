# Concepção de controlo de fluxo

O controlo de fluxo é uma funcionalidade bastante importante para um messaging middleware. Esta funcionalidade permite limitar a quantidade de mensagens enviadas/recebidas, evitando assim o consumo excessivo de recursos que pode levar eventualmente ao mau funcionamento do ambiente ou de certos nodos.

# Paired vs Shared

Para este Middleware, existem duas categorias de controlo de fluxo que podem ser consideradas: Paired Flow Control e Shared Flow Control.

## Em que consistem estas categorias?

Paired Flow Control inclui os mecanismos que envolvem uma relação direta entre duas entidades, onde estas interagem para efeitos de sincronização ou coordenação. Sem esta relação, o mecanismo de controlo de fluxo não consegue funcionar propriamente.

Shared Flow Control abrange os mecanismos que envolvem múltiplas entidades que interagem com o mesmo recurso. Por exemplo, transmissores possuem uma janela de mensagens partilhada por múltiplos destinos.

## Porque é que podem ser consideradas?

Mecanismos do tipo **Paired** devem ser formados já que o Middleware possui a noção de nodos e sockets que são entidades que possibilitam a formação de pares e que beneficiam de mecanismos de controlo de fluxo por pares de modo a evitar que certos nodos ou sockets monopolizem o tráfego.

Mecanismos do tipo **Shared** devem ser considerados porque evitam a necessidade de manter estado para cada entidade. Isto é ainda mais relevante no caso de um Middleware que trabalha com Exactly-Once delivery já que o número de estados tem tendência a crescer apenas, i.e., o consumo de memória aumenta sem nunca decrescer.   

# Nodos e Sockets

No caso deste Middleware, existem dois tipos de entidades sobre as quais o controlo de fluxo é benéfico: Nodos e Sockets.

## Controlo de fluxo no nível dos nodos

Um nodo pode ser composto por múltiplos sockets. Com os vários sockets a gerar tráfego é essencial permitir que o tráfego recebido de um nodo seja limitado de modo a que outros nodos tenham igual oportunidade no processamento das suas mensagens e impedir assim que o poder de processamento seja monopolizado por certos nodos que geram muito tráfego.

O Middleware será construído sobre a biblioteca do protocolo de transporte Exon. Esta biblioteca já emprega um mecanismo de controlo de fluxo simples no nível dos nodos. No entanto, esse mecanismo pode apresentar um problema. O controlo de fluxo do Exon apenas limita o número de mensagens em trânsito para cada nodo, i.e., para cada nodo apenas permite que um certo número de mensagens, cuja confirmação de receção ainda não chegou, existam. *Isto significa, que a fila de mensagens recebidas pelo Exon pode ser preenchida apenas por mensagens do mesmo nodo transmissor se este for mais rápido a gerar a mensagens do que os restantes nodos.* 

**Dito isto, pode ser relevante implementar algum mecanismo sobre o Exon que permita limitar as mensagens recebidas vindas da mesma fonte em função das mensagem que já foram processadas e não em função da receção.**

## Controlo de fluxo no nível dos sockets

Nesta secção serão abordadas duas alternativas que poderão ser implementadas.

### Controlo de fluxo local baseado em janelas

Um tipo de controlo de fluxo, básico mas útil, que pode ser fornecido no nível dos sockets consiste num controlo de fluxo baseado em janelas alterado em função da receção das mensagens. Este mecanismo permite controlar a quantidade de mensagens que cada socket tem por entregar num dado momento. Isto ajuda a assegurar que sockets do mesmo nodo têm igual oportunidade de envio, assim como a limitar o ritmo de envio de mensagens por parte dos sockets.  

Uma possível implementação passa pela criação de uma janela de envio***¹** para cada socket, que pode ser regulada para dar prioridade a certos sockets. A janela de envio diminui com o envio de uma mensagem e aumenta ao ser confirmada a receção de uma mensagem. A receção da mensagem pode ser confirmada pelo próprio Exon que emite um recibo aquando da receção do primeiro ACK da mensagem. Estes recibos podem ser emitidos pelo Exon utilizando o método receive() que passaria a retornar mensagens como recibos de receção. Quando o Middleware envia uma mensagem, o Exon retorna um identificador único criado para essa mensagem. Com esse identificador o Middleware pode criar uma associação (num HashMap) entre o identificador do socket autor e o identificador da mensagem. Com a receção de um recibo de receção, que contém o identificador da mensagem, o Middleware procuraria a associação que permite identificar o socket autor e assim incrementar a sua janela.

Esta solução tem duas faltas que devem ser notadas:

1. A janela é partilhada por todos os destinatários, logo o socket pode ficar limitado mesmo sem estar a sobrecarregar qualquer socket destino.
2. A janela é alterada em função da confirmação de receção das mensagens. Como a confirmação não está relacionada com receção ou fim de processamento da mensagem, um socket pode continuar a enviar mensagens assim que mensagens vão sendo recebidas pelo destinatários.

***¹** A janela de envio pode ser representada por um contador. Isto porque o protocolo de transporte Exon assegura que as mensagens são entregues exatamente uma vez e portanto uma mesma mensagem não consegue decrementar o contador múltiplas vezes.

`Problema:`  A solução que emite os recibos de receção agrupados com as mensagens levanta alguns problemas:

1. Para que tamanho deve ser aumentada a queue?
    1. *Dobro?*
    2. *Ver solução presente no ponto 2.*
2. Misturar ambos significa que tanto as mensagens recebidas como recibos podem ocupar a maior parte do espaço da queue. Por exemplo, se muitas mensagens são enviadas é possível que a queue encha com recibos e não seja possivel receber mensagens porque a queue está cheia de recibos.
    1. *Isto pode ser resolvido com counters.*
    2. *Ou duas queues do mesmo tamanho e no receive usa-se uma flag para alternar entre o tipo a receber. Obviamente que se a flag diz que é para receber uma mensagem e não existem mensagens, então é verificado se existem recibos para retornar. A flag só deve trocar quando é recebido algo do tipo indicado pela flag.*
3. O utilizador do Exon pode não querer receber os recibos.
    1. Ignorar este caso para já? Não é dificil de fazer, é só usar condicionais para não se criar recibos.
4. O que fazer quando não há espaço na queue para meter mais recibos?
    1. Fazer a queue unbounded? Pode ser perigoso.
    2. Não aceitar o ACK, para que seja possível criar o recibo na próxima receção do ACK num momento em que a queue ja pode ter espaço? Mas isto significa que largura de banda é desperdiçada desnecessariamente mas parece ser a única solução viável.
        1. Será que pode levar a deadlocks? Deadlocks não me parece que existam. Se o método receive() alternar entre retornar recibos e mensagens.
5. Permitir escolher o que receber, recibo ou mensagem parece ser algo necessário também. A camada superior pode não querer receber mensagens propositadamente só para receber recibos, já que isto leva a receber mensagens e colocá-las numa outra queue para ficarem à espera de serem processadas, o que libera mais espaço para mensagens serem recebidas pelo Exon e consequentemente no aumento da memória utilizada.

### Controlo de fluxo baseado em créditos

Um outro tipo de controlo de fluxo que pode ser útil no nível dos sockets é um controlo de fluxo baseado em créditos cujo objetivo seria possibilitar o controlo do ritmo de envio em função do processamento das mensagens. Este mecanismo para funcionar corretamente requer que dois intervenientes trabalhem em conjunto para efeitos de sincronização e coordenação. Necessitar que os sockets trabalhem em pares resulta na criação de estado para cada um dos sockets com que se comunica. Para além de resultar num maior consumo de memória, num ambiente de exactly-once delivery, a probabilidade de que este estado nunca possa ser libertado é muito grande, ou seja, estamos perante uma situação de *grow-only state*. Para minimizar o impacto na memória, é necessário utilizar o conceito de conexão em que os sockets se dão a conhecer um ao outro (mensagem CONNECT e CONNACK)***²** e após se verificar que não irão enviar mais mensagens para um certo socket devem enviar-lhe uma mensagem de encerramento da conexão para liberar o estado associado.

Este mecanismo pode ser implementado como funcionalidade do socket genérico ou adicionado no comportamento de um socket especializado***³**.

***²** É possível ignorar a troca de CONNECT e CONNACK iniciais. Para isto assume-se que todos os sockets que não possuem um estado associado têm 1 (um) crédito disponível para envio. Um socket, ao receber uma mensagem, verifica o estado associado a esse nodo. Ao aperceber-se que não existe um estado associado, cria-o e marca o nodo como possuindo 0 créditos. Assim que pretendido, provavelmente em função do padrão de comunicação implementado pelo socket, é enviada uma mensagem com o montante de créditos que se pretende fornecer ao socket transmissor.

***³** Um socket especializado é um socket que implementa comportamento específico de um padrão de comunicação. 

Para além do socket base proporciado pelo Middleware, o objetivo dos sockets é proporcionar comportamento relativo a padrões de comunicação de modo a facilitar a criação de topologias e estabelecer comunicação entre aplicações. Para certos padrões de comunicação, um controlo de fluxo à base de créditos que permite controlar o ritmo de processamento pode ser benéfico ou até mesmo obrigatório. **Este tipo de controlo de fluxo pode ser incluído no comportamento específico dos sockets, ou pode ser implementado como uma funcionalidade do socket genérico.**

# Páginas associadas

[Concepção do controlo de fluxo (antigo)](Concepção%20do%20controlo%20de%20fluxo%20(antigo).md)