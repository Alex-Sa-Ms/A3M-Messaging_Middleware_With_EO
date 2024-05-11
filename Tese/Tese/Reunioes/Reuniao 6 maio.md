# Assuntos discutidos na reunião e por email
### Controlo de fluxo por destino
- Controlo de fluxo por destino é algo que pode ser implementado por um socket de alto nível, logo não deve ser implementado pelo socket genérico.
- Preferível um controlo de fluxo por socket, no envio, em que se define uma janela para a quantidade de mensagens que podem estar em trânsito para um certo socket.
	- Permite controlar/limitar o número de mensagens, em trânsito, enviadas pelo socket. Juntando as janelas de todos os sockets conseguimos obter o valor total para o número máximo de mensagens, em trânsito, enviadas pelo nodo.
	- Permite definir prioridade de tráfego entre os sockets do nodo. Se definirmos a janela de um socket para $X$ e a de outro socket do mesmo nodo para $10X$, é facilmente perceptível que o segundo socket consegue ter um ritmo de envio superior em 10 vezes, sendo portanto, "priorizando" o seu tráfego.  
### Controlo de fluxo global (não pretendido)
- Ponto de contenção, já que todas as threads clientes necessitariam de adquirir a permissão global antes de prosseguir.
- Exon já é um ponto de contenção já que todas as threads ficam bloqueadas a aguardar que a sua mensagem possa ser posta na *queue*.
	- **Necessário:** Remover operações de contenção desnecessárias no Exon, como o mecanismo de fecho. 
### 1 thread em vez de 2+ threads 
- Criar apenas uma thread para o middleware. Esta thread será responsável por ler e processar mensagens e recibos do Exon, e por acordar threads clientes que pretendam enviar ou receber mensagens.
- Threads clientes devem realizar todo o processo necessário para enviar mensagens (até que a mensagem é entregue ao Exon).
- Threads adicionais do middleware apenas devem ser criadas se existir alguma razão específica para isso.
- Se um socket de alto nível precisar de realizar operações bloqueantes ou paralelas, então este deve optar pela criação de uma(s) thread(s) adicionais para esses propósitos.
	- Pode ser boa ideia pensar numa framework de IO assíncrono similar à do NNG.
- Sockets devem ser thread-safe, portanto, possuir um lock é algo necessário. Uma condição associada a esse lock também é importante para acordar threads clientes que se encontrem a aguardar para receber uma mensagem.
	- O socket genérico, na criação, deve permitir definir que operações fazem sentido.
		- Valores permitidos pelo NNG: 
			- Protocol can receive
			- Protocol can send
			- Protocol can send and receive
			- Protocol is raw (used to deliver all messages, both control and payload messages, to the client and skipping the processing)
#### 1 thread vs 2+ threads - Pros and Cons
##### Pros 1 thread
- Não se precisa de estruturas de concorrência.
##### <span style="color:Orange">Cons 1 thread</span>
- Dificulta extensão de comportamento.
- Implementação com espera ativa.
	- Como tornar numa solução reativa?
		- Exon tem de passar a ter um modo em que emite as mensagens e os recibos para uma stream reativa, impossibilitando a receção explícita de mensagens e recibos, no entanto, isto levaria a que o Exon fosse atrasado pelo mecanismo de concorrência.
		- Exon pode permitir que um callback seja fornecido, mas isto seria mais perigoso já que a thread do algoritmo do Exon teria de executar código desconhecido que pode ser bloqueante, impedindo o bom funcionamento da biblioteca.
##### Pros 2+ threads
- Pode-se construir facilmente um sistema reativo.
- Permite separar tarefas facilmente.
- Permite escalar o número de threads para auxiliar o processamento quando necessário.
##### <span style="color:Orange">Cons 2+ threads</span> 
- Necessário estruturas preparadas para concorrência.
- Devido ao controlo de concorrência e context switches pode ter menos throughput do que uma versão single threaded. 

### Envio justo 
- Como ser justo no envio das mensagens para com os diferentes destinos?
	- Socket de alto nível é responsável por definir um algoritmo para escolher o destino.
		- Por exemplo, à medida que novos destinos são descobertos, adicionam-se no fim de uma lista ligada. Cria-se dois apontadores, um para o início da lista e outro para o nodo que deve ser o próximo destino. Assim que a lista acabar, o "seguinte" deve apontar para o início da lista.
### Recepção justa
- Como ser justo, na receção, para com as diferentes fontes?
	- Socket de alto nível deve dividir as mensagens por fonte.
### Mensagens de controlo e controlo de fluxo
- Mensagens de controlo não devem ser influenciadas pelo controlo de fluxo.
- O controlo de fluxo é reservado para mensagens de utilizador.
### Queues & Exactly-once delivery guarantee
- Por causa da garantia de entrega Exactly-once, mensagens aceitadas pelo middleware não podem ser descartadas até serem processadas.
- Como não podem ser descartadas, em situações que resultam no armazenamento das mensagens em queues é necessário garantir que as **queues** são **unbounded** para que não exista perda de mensagens.
### Como processar recibos e mensagens
#### Solução 1
- Socket genérico possui map de handlers. 
- Mapeia tipos de classes para handlers.
- Funciona à base de notificações. Passa-se um objeto para processamento e obtém-se o handler registado para processar esse tipo de objeto.
- Em princípio não deve ser criada uma notificação com um objeto cujo tipo não foi registado. Como caso excepcional, descarta-se o objeto.
#### Solução 2
- Socket genérico deve conhecer todos os tipos de notificação, logo basta criar os métodos de processamento para cada tipo e permitir que sejam overridden.
### Selective Receive
- Mensagens recebidas são guardadas numa queue e apenas são processadas quando passarem por condições definidas.
- A ordem das condições é a ordem pela qual estas são testadas.
- Notas:
	- Pode servir para impor ordem nas mensagens.
### Selectors / Pollers
...
...
...
### Associações dos sockets
- Sockets associam-se entre si através de um handshake.
- O handshake permite verificar se o socket destino existe e se é compatível antes de se passar ao envio de mensagens. 
- O socket que inicia o handshake indica o seu tipo de socket e outras informações que forem pertinentes, como caraterísticas do próprio socket ou caraterísticas esperadas do socket com que se pretende associar.
- O socket destino responderá positivamente i.e. que aceita o pedido de associação, se estiver de acordo com todas as informações presentes no pedido de associação.
- O socket destino deverá responder negativamente se alguma informação não for compatível (por exemplo, o tipo de socket) ou se alguma restrição o exigir (por exemplo, o número limite de associações ter sido atingido).
### O que fazer quando não existem destinos
- Se já existirem destinos registados mas não confirmados, i.e., a aguardar a resposta do handshake, então a operação de envio pode bloquear.
- Se não existirem destinos registados, uma exceção deve ser lançada a indicar que o socket não tem destinos registados. 
### Expor recibos de receção na API
- Permitir o utilizador escolher se quer receber recibos de receção.
	1. Definir default para os sockets, por exemplo, por default não emite recibos.
	2. Permitir, ao criar o socket, alterar o default.
	3. Permitir, em cada operação de envio, definir se é pretendido o recibo ou não para permitir contrariar o default para certas mensagens.
- Como entregar os recibos de receção? 
	- Entregar como se fosse uma mensagem?
	- Acumular os recibos e ter um método que retorna todos os recibos acumulados até ao número fornecido.
		- `getReceipts(n : number of receipts) : Receipt[]`
- Métodos bloqueantes e não bloqueantes para esperar por um recibo
- Método de test e remove (versão não bloqueante)
	- Versão bloqueante é muito trabalhosa e não é algo *core*
### Receipt handler para comportamento pós-receção
- A emissão dos recibos de receção é algo interessante já que permite executar um certo tipo de comportamento quando uma mensagem é dita como recebida pelo destino.
#### Quem deve receber os recibos?
- Entregar os recibos ao **utilizador** e ao **socket** pode ser vantajoso.
	- Tanto o utilizador como o socket podem querer executar um certo comportamento após verificar que uma mensagem foi entregue.
#### Como evitar o armazenamento das mensagens?
- Guardar as mensagens até que o recibo destas seja emitido não é necessário.
- Basta, criar um handler que tenha essa mensagem em mente e associá-lo ao identificador da mensagem para que possa ser executado quando o recibo for emitido.
#### API para os sockets
- Permitir o registo de handlers por parte dos sockets é algo perigoso já que o trabalho será realizado pela thread do middleware. Se o trabalho contiver alguma operação bloqueante então existe o risco de deadlock para além de atrasar substancialmente o processamento por parte do middleware. 
- **A solução ideal para os sockets será existir um único handler, não substituível ou modificável, que em função de alguma informação associada ao recibo executa a lógica devida.**
	- Por exemplo, digamos que um certo tipo de socket executa uma espécie de controlo de fluxo para um certo tipo de mensagens. Ao enviar esse certo tipo de mensagens, este regista os identificadores dessas mensagens num set para saber quantas mensagens desse tipo estão em trânsito. O handler de recibos do socket consegue realizar toda a lógica que precisa sem ter sido necessário associar um handler específico.
- **Quando um recibo de receção é emitido, este invoca o handler do socket, se existente.**
#### API para o utilizador
- Podem ser criados 3 métodos: retorna apenas mensagens, retorna apenas recibos e retorna ambos (ordem pode ser definida, por exemplo, alternar utilizando um certo rácio ou retornar pela ordem que são emitidos).
- A nível do socket, a lógica a executar depois de receber um recibo não tem em conta o conteúdo desta, já que o conteúdo pode ser qualquer coisa.
- Como o conteúdo pode ser muito variável, em vez de criar um handler genérico, pode optar-se por criar um handler específico para essa mensagem e associá-lo ao identificador da mensagem.
- Não existe risco de interferir com o middleware, já que os handlers serão executados por uma thread do utilizador.

# Conclusões minhas
### Ordem nas mensagens
- Identificadores das mensagens podem ser criados em vários níveis, no entanto, para implementação dos protocolos apenas deve ser relevante a nível do socket. Dito isto, o socket de alto nível deve implementar essa lógica se o pretender.<span style="color:red"> (Perguntar ao prof se concorda.)</span>
	- Alguns exemplos de níveis: único globalmente, único localmente (no nodo) e único no socket.
	- Nada impede que não existam outros tipos de ordem criados pelo protocolo do socket. O protocolo pode até apenas necessitar que certos tipos das suas mensagens sejam ordenadas.
- Diferentes protocolos de comunicação podem querer diferentes tipos de ordem.
- A ordem apenas pode ser feita para mensagens com a mesma fonte, já que não é viável relacionar as diferentes fontes. O mesmo se aplica para o destino. Os identificadores das mensagens devem ter em conta o destino para que o destino possa ordenar as mensagens.

### Reformular partes do Exon para mais eficiência
- Exon será um ponto de contenção do middleware, logo, remover operações demoradas que aumentem o tempo de contenção não são desejáveis.
	- Exemplos:
		- Locks do estado
		- AtomicInteger para gerar id da mensagem
			- Não é necessário ser atomic se for utilizado um lock do estado que cobre toda a operação de envio.