# Dúvidas sobre Shutdown do Exon

**Perguntas:**
- Permitir enviar mensagens enquanto estiver no estado "CLOSING"? <span style="color:orange">Not answered.</span>
- Conclusão sobre o mecanismo de *shutdown*? <span style="color:red">Assumir que os nodos não se desligam para já.</span>
	- Persistir o clock antes de terminar
	- Criar métodos de administração que permitem a camada superior eliminar registos.
		- A camada superior responsabiliza-se pelas consequências de invocar estes métodos.
		- Pode ser relevante os registos de envio retornarem a lista de mensagens em queue.
	- Admitindo que o protocolo de mensagens utilizado pela camada superior consegue definir quando já não existirá mais troca de mensagens entre os dois nodos, então, após aguardar um breve período de tempo, para permitir a receção de duplicados e outros tipos de mensagem, é possível eliminar o registo.
		- Se uma nova entidade pertencente ao nodo pretender enviar mensagens para o nodo destino que se disse que não ia haver mais comunicação? Seria possível a camada superior verificar se ativou o mecanismo de shutdown para esse nodo. MUITO CHATO.
	- Usar blacklists com tempos de expiração? Permite rejeitar mensagens vindas de certos nodos durante um certo tempo, assim é possível que mensagens duplicadas ou outro tipo de mensagens que já podiam estar a caminho cancelem a eliminação do registo.
	- <span style="color:red">Já estou a ver que isto ainda dá muito que pensar. E assumir que os nodos não desaparecem para sempre.</span>

# Relatório e conclusões após reunião

### Shutdown do Exon
- Ignorar para já.
### Problemas laterais à tese
- Ignorar para já.
### Comunicação interna do middleware
- Permitir comunicação interna do middleware para efeitos de gestão, controlo, estatísticas, entre outros, pode ser útil.
- Uma solução simples para obter esta funcionalidade, consiste em reservar identificadores de sockets com prefixo "\$" para estes assuntos internos. Ao criar sockets utilizando estes identificadores especiais, pode-se reaproveitar a lógica de encaminhamento dos sockets.
### Protocolo de comunicação
- Um protocolo base para comunicação entre sockets é necessário. Este pode permitir distinguir entre mensagens administrativas dos sockets de mensagens que podem já dizer respeito a implementações de padrões.
	- Por exemplo, é necessário existir um tipo de mensagem administrativa para verificar se o socket presente do outro lado é compatível. Esta é uma mensagem que é obrigatoriamente enviada para todos os padrões de comunicação. 
	- Não deve ser permitido cancelar o envio desta mensagem, já que pode levar a comportamento não definido.
### Funcionalidades e versatilidade de Socket genérico
- Socket genérico deve ser versátil. As suas funcionalidades devem permitir que diferentes funcionalidades sejam implementadas. 
	- Funcionalidades:
		- Enviar mensagens para socket identificado por um par (NodeID,TagID)
		- Receber mensagens cujo destino possui a sua identificação (NodeId, TagID)
		- Controlo de fluxo
		- Selective receive(?) 
		- ...
- Para verificar se é versátil, pode-se fazer o exercício de pensar como é que diferentes implementações de alto nível poderiam ser implementadas por cima deles. Como ZeroMQ, NNG, etc.
- Só depois de ter o socket genérico bem pensado, e de como é que as leituras e escritas para estes serão feitas, é que se pode pensar nas Implementações de sockets de alto nível, incluindo conceitos como o de contextos.
### Processamento de mensagens e recibos

#### Processamento eficiente
- Sockets podem agir como conectores ao middleware, i.e., não passam de objectos que agem de ponte para as funcionalidades fornecidas pelo middleware.
	- Consideremos o envio de uma mensagem pelo socket. O socket não utilizaria directamente o Exon para enviar a mensagem. Este delegaria a mensagem para o middleware para que esta seja enviada assim que possível.
- Sockets complementam a funcionalidade do middleware, mas não realizam operações que podem ser tratadas pelo middleware devido à sua visão geral sobre as operações.
- Como é que as mensagens recebidas podem ser tratadas de forma eficiente?
	- Cada mensagem recebida gera um evento de leitura a ser processado pelo middleware.
	- Desta forma é possível criar alguma noção de ordem no processamento, para além de permitir um processamento reactivo.
- Como tratar de um evento de leitura?
	- O middleware delega o processamento da mensagem para o socket identificado como destino.
	- Sockets devem ser registados no middleware utilizando a sua etiqueta. Através desse registo é que se direccionam as mensagens para que sejam processadas.
	- Como pode ser desejável usar sockets sem restrições deve ser permitido registar sockets genéricos, cujo "processamento" consiste no armazenamento da mensagem numa queue destinada a mensagens de entrada.
	- Para garantir que os sockets são registados 
	- **É possível garantir que os sockets são registados?**
		- Ou deixa-se isso como responsabilidade dos métodos de criação 
- 

#### Arquitectura
##### Reader Thread
- Thread para ler mensagens e processar recibos
- Uma thread destas é suficiente.
- Pode tentar fazer poll não bloqueante de todos os recibos até esvaziar, e depois aguardar um X tempo por uma mensagem antes de voltar a verificar se existem recibos para processar.
##### Event Thread
- Thread para tratar de eventos de envio, receção, processamento.
	- Tipo o que é fornecido pela framework de IO assíncrono do NNG que executa callbacks após ler, escrever ou sleep.
- Pode existir uma pool parametrizável deste tipo de thread.
	- 1 por predefinição (deve ser suficiente)
##### Event Queue / Event Stream
- Alternativas para o processamento de eventos
###### Event Queue
- Preparada para efeitos de concorrência
	- Com condição que permite acordar threads apenas quando necessário.
###### Event Stream
- Utilizando uma framework do tipo da ReactiveX, cria-se uma hot stream de eventos em que as threads são observers. É possível?
##### Socket genérico
- Possui etiqueta (tag / identificador único local).
- Etiqueta + identificador do nodo formam o identificador global do socket.
- Possui *delivery queue* para mensagens que a entregar a uma thread cliente.
	- Permite o armazenamento das mensagens até que sejam processadas por uma thread cliente.
	- Não confundir com mensagens que necessitam de ser processadas por threads do middleware.
- Possui semáforo para controlo de fluxo no envio.
	- Controlo de fluxo baseado em janela que limita o número de mensagens que podem estar em trânsito.
- Permite enviar mensagens.
	- Precisa de adquirir uma permissão do semáforo para requisitar que o middleware envie a mensagem.
	- O método bloqueante bloqueia até que o semáforo forneça permissão e retorna imediatamente após submeter a mensagem para envio.
	- Método não bloqueante tenta adquirir o semáforo, se falhar, retorna com excepção ou algum código de retorno para indicar a falha no envio.
	- Socket genérico não envia a mensagem, apenas cria um evento de envio que o middleware deve executar.



### Ideias iniciais (talvez exista algo para implementação futura)
- Preciso pensar como é que depois de serem distribuídas as mensagens para os diferentes sockets, como é que pode ser feito polling eficiente para tratar destas mensagens?
	- Gerar evento de leitura, a ser processado por uma eventThread. 
	- Queue de eventos tem condição para sinalizar thread que esteja à espera de um evento.
	- *Ou, pode ser utilizada **Programação reativa*** em que as eventThreads são observers de uma hot stream de eventos
	- Existindo um evento de leitura, pode-se assumir que existem interessados em receber a mensagem.
		- Podem existir capturadores que pretendem receber todas as mensagens. Exemplo: estatisticas
		- E podem existir consumidores, que consomem a mensagem e precisam de demonstrar interesse em receber novamente. 
		- Sejam consumidores ou capturadores devem registar o interesse sobre ler de um socket previamente, desta forma, um evento de leitura resulta na notificação de observadores desse socket.
			- Utilizando a tipica abordagem com condições resultaria em acordar threads. O que significa a existência de demasiadas threads.
			- Registos de callbacks pode ser uma solução que evita o uso desnecessário de threads
				- E que facilita a integração de programacao reativa
	- Pode ser utilizado o padrão observer? Pode ser utilizado para permitir mais flexibilidade, e não impede que o método notify() seja utilizado como ponte para a programação reativa.
- Contextos dos sockets é algo a se pensar posteriormente.
	- Explorar novamente este conceito presente no NNG.
	- Se não estou enganado, consiste em separar a lógica, relacionada com o padrão de comunicação, do socket em si. Os padrões em que não faz sentido ter vários contextos, possuem um principal.
- Pensar numa solução para fazer polling de vários sockets não genéricos admitindo que podem existir múltiplas threads cuja interseção das seleções de sockets para polling não é vazia.
	- Cada poller possui um lock, uma condição criada para esse lock e uma queue de mensagens.
	- Assuma-se que pollers podem ser individuais ou múltiplos, i.e., dar *listen* em um ou mais sockets não genéricos.
	- Seja um poller individual ou múltiplo, este regista-se como interessado para todos os sockets não genéricos em que tem interesse.
	- Os interessados devem ficar numa queue do tipo FIFO, assim é possível adicionar a mensagem à queue dos interessados e acordá-los com a condição do lock.
		- Apenas usar a condição para acordar não é suficiente, já que o poller precisaria de fazer poll em cada socket que tem interesse podendo remover uma mensagem para a qual não foi notificado.
- Como é que funcionaria um evento de envio? E se o controlo de fluxo não permite enviar mais mensagens porque a janela não tem espaço?
	- Cuidado com deadlocks do controlo de fluxo. A reader thread ao receber um recibo precisa de conseguir devolver o crédito. 
		- Semáforos devem funcionar bem para as janelas do controlo de fluxo e evitar deadlocks.
	- O semáforo pode ser *acquired* no momento em que uma thread client faz send() e *released* no momento em que se recebe o recibo de receção. Desta forma, todas as mensagens que entram para o sistema do middleware é garantido que têm permissão para ser entregues.
- Fair queuing das client threads. Usar a versão *fair* dos mecanismos de concorrência.
- Batching de mensagens no middleware A3M?
- Poller para receber mensagens é uma funcionalidade a implementar. E um poller para escrita faz sentido?
	- Se for feito um poller para escrita, seria para uso por um utilizador mais experiente.
		- Exige consumir o semáforo do socket antes de notificar o poller.
		- O poller teria uma queue FIFO para indicar a ordem dos sockets a utilizar para enviar as mensagens.
		- Ao ser acordado o poller, seria devolvido o identificador do socket que permite o envio de uma mensagem. O semáforo de controlo de fluxo já estaria consumido, logo se não for pretendido enviar mensagem deve ser invocado um método a identificar q n será emitida uma mensagem para libertar a permissão do semáforo.
		- O método de envio e de libertar a permissão não necessitam mencionar o semáforo já que a ordem é definida pela queue.


## Questões relevantes

- Como é feita a ordenação das mensagens? Deve ser fornecida pelo socket genérico? Ou deve ser fornecida pelo socket de nível superior?
- Tendo em conta o fator de Exactly-Once Delivery, como garantir que mensagens não são descartadas antes do socket destino ser criado?