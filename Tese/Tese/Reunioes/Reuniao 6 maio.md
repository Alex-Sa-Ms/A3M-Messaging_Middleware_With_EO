# Assuntos discutidos na reunião e por email
### Controlo de fluxo por destino
- Controlo de fluxo por destino é algo que pode ser implementado por um socket de alto nível, logo não deve ser implementado pelo socket genérico.
- Preferível um controlo de fluxo por socket, no envio, em que se define uma janela para a quantidade de mensagens que podem estar em trânsito para um certo socket.
	- Permite controlar/limitar o número de mensagens, em trânsito, enviadas pelo socket. Juntando as janelas de todos os sockets conseguimos obter o valor total para o número máximo de mensagens, em trânsito, enviadas pelo nodo.
	- Permite definir prioridade de tráfego entre os sockets do nodo. Se definirmos a janela de um socket para $X$ e a de outro socket do mesmo nodo para $10X$, é facilmente perceptível que o segundo socket consegue ter um ritmo de envio superior em 10 vezes, sendo portanto, "priorizando" o seu tráfego.  
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

### Ordem nas mensagens
- Diferentes padrões de comunicação podem querer diferentes tipos de ordem.
- Ordenar mensagens, em princípio, apenas deve ser possível para mensagens originadas pela mesma fonte.
- Se for implementado um algoritmo que tenta ser justo no processamento das mensagens das diferentes fontes, então este precisa de dividir as mensagens por fonte. E pode ou não pretender que as mensagens dessa mesma fonte fiquem ordenadas.
- A ordem apenas pode ser feita para mensagens com a mesma fonte, já que não é possível relacionar as diferentes fontes.

# Conclusões minhas
