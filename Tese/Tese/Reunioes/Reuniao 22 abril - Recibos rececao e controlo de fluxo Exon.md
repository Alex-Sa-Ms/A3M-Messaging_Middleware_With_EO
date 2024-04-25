# Problemas iniciais para a reunião
Aqui serão descritos os problemas que deverão ser esclarecidos na reunião. No entanto, não impede que outros problemas que surjam durante a reunião sejam abordados.
### 1. Como identificar as mensagens do Exon

**Problema:** 
	Para permitir a emissão de recibos de recepção por parte do Exon, é necessário, em primeiro lugar, definir como o formato dos identificadores para as mensagens enviadas.

**Solução:** 
	Como os identificadores das mensagens apenas serão utilizados localmente, para efeitos de verificação da chegada da mensagem ao nodo destino, não existe a necessidade de garantir que são globalmente únicos. A solução mais simples e com menos custo computacional passa pela utilização de um contador circular. Utilizando um long para o contador é possível enviar 2^64 mensagens antes do contador começar a reutilizar identificadores. Dada a garantia de entrega Exactly-Once, apesar de esta solução não oferecer uma garantia de que o mesmo identificador não será utilizado para duas mensagens em simultâneo, a probabilidade de tal acontecer é extremamente reduzida já que o número de mensagens que é necessário enviar para o contador reiniciar é bastante elevado.
	**// Acho que se pode ignorar esta parte ao passar para a tese //** Uma solução que garantiria a unicidade dos identificadores seria a combinação de um contador com um timestamp do momento de queuing da mensagem. Para o contador neste caso, um inteiro já seria satisfatório. Admitindo que o contador é representado em milissegundos, seria necessário enviar mais do que 2^32 mensagens no mesmo milissegundo para existir sobreposição de identificadores. Esta solução, no entanto, é mais custosa já que para além de atualizar o contador, seria necessário obter o timestamp para cada mensagem.

### 2. Como consultar se uma mensagem foi recebida no Exon

**Problema:**
Saber se uma mensagem foi recebida pelo destino é uma funcionalidade bastante útil. No caso do middleware A3M, esta funcionalidade auxiliaria a criação de um mecanismo de controlo de fluxo. A camada superior poderia enviar por cima do Exon mensagens de confirmação, no entanto, isto resultaria num overhead desnecessário. O Exon pode facilmente fornecer tal confirmação sem overhead adicional. 

**Solução:**
- O método send() deve requisitar um parâmetro que indica a vontade de receber um recibo de receção para a mensagem.
- O método send() deve passar a gerar e retornar um identificador de mensagem quando um recibo de receção é desejado. 
- Deve ser criada uma queue, não limitada, para recibos.
- O identificador de mensagem deve ser associado à mensagem (payload) para que após a receção do ACK (mensagem de confirmação de receção), um recibo de receção, com esse identificador, possa ser adicionado à queue de recibos.
- Deve ser criado um método bloqueante que permite aguardar pela emissão de um recibo de receção.
- Deve ser criado um método não bloqueante que tenta obter um recibo de receção. Caso a queue esteja vazia, retorna imediatamente um 'null'.
- Pode ser criado um método de "test and remove" que verifica se existe na queue um recibo com o identificador fornecido como parâmetro e o remove. Retorna 'true' se encontrou o recibo e removeu, ou 'false' se não encontrou o recibo. 

**Notas sobre a solução:**
- O uso de uma queue não limitada possui o risco de consumir muita memória. Por esta razão, o cliente deve mencionar a vontade de receber um recibo, comprometendo-se assim a consumir estes recibos para que não ocorram problemas relacionados com a memória.
- Uma operação "test and remove" bloqueante é possível mas exige mais lógica e mais memória, e não parece existir um caso de uso concreto por parte do middleware A3M, logo não será implementado.
	- Possível solução:
		1. Ao enviar uma mensagem deve ser mencionada a vontade de receber um recibo de receção.
		2. Existindo essa vontade, então é criado o recibo de receção, logo após a invocação do método send(), e colocado num Hash Set de recibos não recebidos.
			- O propósito disto é para quando se fizer um pedido bloqueante não se bloquear uma thread para uma mensagem que já foi recebida ou que nunca será recebida.
		3. Aquando da receção de um ACK para uma mensagem cujo id se encontra no hash set, se existir pedido bloqueante para o id, então atualiza-se o objeto associado ao pedido bloqueante e acordar-se os interessados. Caso não exista, coloca-se o recibo numa queue de recibos emitidos para que sejam verificados de forma sequencial.
		4. Um pedido bloqueante para um id resulta na criação de um objeto com um lock, uma bool, um contador e uma condição para await e signal.
			- O contador indica o nº de interessados no recibo e deve ser atualizado (incrementado/decrementado) por cada um dos interessados quando registam o interesse no recibo e quando verificam que este já foi emitido. O objeto deve ser colocado num hash map, preparado para operações concorrentes, associado ao id da mensagem. O primeiro interessado cria o objeto, e o interessado cujo decremento do contador (após o recibo ser emitido) resulta num contador com valor igual a 0, deve remover o objeto do hash map.
### 3. Controlo de fluxo do Exon

O controlo de fluxo do Exon é determinado por uma variável P. Esta variável P indica o nº de mensagens que podem estar em trânsito para um certo nodo. Para calcular o valor de P, a implementação original do Exon utiliza o protocolo de transporte TCP. Através do TCP por meio de múltiplas iterações calcula-se uma estimativa da *bandwidth* da ligação e o RTT médio. Utilizando estes valores passa-se então a calcular o valor de P através da fórmula: $$ P = RTT * bandwidth / message\_size $$ Esta solução embora satisfatória para provar o conceito do algoritmo, não é a solução mais indicada como podemos verificar pelos seguintes problemas: 
	1. O cálculo de P é feito apenas uma vez, para o primeiro nodo com que a comunicação é feita, utilizando-se o resultado para os restantes nodos. Como é perceptível, esta solução não é justa para nodos com situações de rede diferentes. 
	2. Para o cálculo de P realizam-se 100 round-trips para calcular o RTT médio e 10000 one-trips para calcular a bandwidth. Esta solução é aceitável para casos de uso com grande largura de banda, um baixo RTT e para situações em que é sabido que todos os nodos operam sobre as mesmas condições de rede, para que apenas seja necessário executar esta operação uma única vez.
	3. O valor P não é atualizado com o tempo. Não é garantido que as condições de rede se mantenham iguais ao longo do tempo. Podem mudar para melhor ou pior. Portanto, existir um ajuste que acompanha as mudanças do ambiente em que opera é algo desejável. 

**Problema:** É necessário um mecanismo de controlo de fluxo adaptável a nodos em diferentes ambientes de rede e às constantes mudanças na rede, e sem o overhead inicial utilizado para estimar a bandwidth, já que fazer este cálculo para todos os nodos não é uma solução viável.

**Solução:** 
- Utilizar a seguinte fórmula (ou uma variante) RTT * bandwidth / MTU para calcular o valor de N. 
- Como o valor da bandwidth não será calculado, deve se encontrar um valor conservativo que sirva para a maioria das cenários. 
	- Este valor deve ser uma variável de forma a permitir que através de testes se possa tentar encontrar a solução mais satisfatória.
- O valor do RTT não existe de início logo também deve ser definido um valor conservativo inicial para este.
- Para atualizar o RTT será utilizada uma fórmula que favorece o valor mais baixo, entre a média dos RTTs e o valor atual (com *alpha* = 0,8):  $$ new\_RTT\_avg = min(RTT\_avg, new\_RTT) * alpha + max(RTT\_avg, new\_RTT) * (1 - alpha) $$
	- Esta fórmula favorece o mínimo, de modo a que fornecendo um RTT base relativamente alto, seja possível a média dos RTTs convergir rapidamente para o valor correto.
	- Se os valores reais de RTT forem superiores ao RTT base, a convergência demorará mais tempo para ter atingida, no entanto, apenas a velocidade é sacrificada ligeiramente no início.
- Como o valor de P pode diminuir e aumentar, é necessário que os semáforos usados para o controlo de fluxo permitam aplicar a diferença retirando permissões (permits) adicionais caso necessário. O ajuste do número de permissões totais pode resultar num saldo negativo, operação que não é suportada pela implementação base do semáforo.
- O valor N - número de envelopes a requisitar a um receptor - é calculado em função do P, logo deve ser também atualizado.
- Também se poderia fazer uma média móvel do tamanho das mensagens e substituir pelo MTU.



- ***Ver quais são as consequências de alterar o N e quais as consequências de alterar o P***
- É possível alterar o P, como é que o algoritmo reage ao pedido de slots?

<b style="color:red">Acho que é um problema futuro. Basta eliminar o TCP, e meter o P como um parâmetro que pode ser definido ao criar o middleware. Já que o protótipo não estará em condições de ser utilizado em redes públicas, nos testes, os nodos pertencerão todos à mesma rede. Isto chega para provar a utilidade do Middleware. Como os nodos pertencerão todos à mesma rede, irão trabalhar sobre as mesmas condições, logo, não há problema em utilizar o mesmo valor de P para todos os nodos.</b>

### 4. Cache de associações do Exon não pode ser grow-only
Problema lateral ao *core* da tese, logo o Professor recomendou ignorar.

Para servidores é um grande problema já que um grande número de clientes os contacta.
- *Solução 1:* Ser oblivious e ser removida quando os registos são removidos.
	- Overhead grande a cada vez que se pretender comunicar.
- *Solução 2:* Criar cache com tamanho limite do tipo LRU
	- Mais espaço utilizado
- *Solucao 3:* Criar cache com tamanho limite com remoção aleatória

### 5. Shutdown gracioso da instância do Exon

A solução atual está descrita no Notion (https://www.notion.so/Adapta-o-e-reestrutura-o-do-Exon-cbcfb8fb630c459a96ce290b6358d259). Essencialmente, após invocar o método de close() deixa de ser possível enviar mensagens. Mensagens podem continuar a ser recebidas até a queue de entrega ficar vazia e o estado da instância ficar "CLOSED". Para a instância ficar "CLOSED" é necessário que não existam registos e que já não existam mensagens para processar na *algoQueue*. A operação de *close* resulta no fecho do socket e na terminação da thread que executa a lógica do algoritmo, *algoThread*, e na terminação da thread responsável por ler do socket, *readerThread*. 
##### Problemas atuais resultantes da invocação deste mecanismo
- Mensagens recebidas depois da operação close() não podem necessitar de resposta, já que a operação de envio é proibida após despoletar o mecanismo de shutdown.
- Embora não existam registos é possível que os nodos parceiros possam ainda não ter eliminado os registos opostos (counterparts) do lado deles. Os registos de envio são eliminados quando não existem mensagens nem tokens por enviar. Já os registos de receção são eliminados quando não possuem slots. Antes da eliminação do registo de envio, é enviado para o nodo receptor um REQSLOTS que inutiliza o slots e assim resulta na eliminação no registo de receção. Se este REQSLOTS não chegar ao destino, então existe um intervalo de tempo em que existe um registo de receção e não existe um registo de envio. O registo de receção é apenas eliminado, após um *timeout* despoletar o envio de uma mensagem SLOTS que é respondida com uma mensagem REQSLOTS que permite que o registo seja eliminado. Este situação também seria resolvida se a instância voltasse a ser iniciada.
- Após ser fechado, mesmo não existindo registos nem mensagens por processar, persistir o *ck* (clock) é algo importante para garantir que ao reiniciar-se a instância, não existe a possibilidade de corromper registos.

##### Conclusão
Este mecanismo tem como alvo nodos efémeros que podem entrar e desaparecer sem comprometer o bom funcionamento da topologia. No entanto, antes de invocar o mecanismo de shutdown devem ser tomados alguns cuidados:
	1. Garantir que não irá ser necessário enviar mais mensagens, por exemplo, em resposta a mensagens recebidas. ***(O próprio Exon poderia permitir enviar mensagens enquanto o estado está em "CLOSING")***
	2. Dar um tempo de *cooldown*, ou seja, só após passar um certo tempo sem atividade (desde a última operação, seja de receção ou de envio) para que os nodos com que se comunicou eliminem todos os registos e para que possíveis mensagens duplicadas cheguem.  ***(Até poderia ser implementado pelo próprio Exon)***
		- Este período de cooldown até que é perigoso para casos em que se pretende reiniciar o cliente, já que requer esperar muito tempo antes de poder criar uma nova instância com o mesmo identificador de nodo.
		- Mas sem ele, também não é possível desaparecer por completo, sem que seja possível deixar registos noutros nodos por serem eliminados. Mesmo com mecanismos de administração que permitem eliminar registos é necessário esperar por um tempo de cooldown antes de ser seguro eliminar o registo. 
			- Por exemplo, digamos que um peer manda uma mensagem sobre o Exon a indicar que não enviará mais mensagens. Ainda é possível que devido a eventos agendados ou a mensagens duplicadas que cheguem mensagens que voltam a criar um registo para esse nodo. Dito isto, existir métodos de administração para eliminar os registos é algo desejável, no entanto, estes mecanismos apenas devem realizar o seu trabalho após um intervalo apropriado de cooldown.
A implementação da persistência do estado (apenas o clock seria necessário se não existirem registos nem mensagens) permitiria terminar a instância e reiniciá-la a qualquer momento. Possibilitaria até com a transferência de estado para outro dispositivo, retomar a comunicação num outro dispositivo.  
### Ideias faladas na reunião
- 
