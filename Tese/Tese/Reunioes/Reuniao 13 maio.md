# Questões
- O professor na semana passada falou que os sockets de alto nivel podem ter queues por destino e que se fosse atingido o limite de uma queue entao a operaçao de envio devia bloquear. No entanto, como nós queremos que sejam as threads clientes a executar a operação de envio, não faz sentido armazenar as mensagens em queues como se o envio destas estivesse a ser agendado para mais tarde. No entanto, acho que pode fazer sentido existir algo que controle qual deve ser o próximo destino como uma lista ligada circular.
- Como expor os recibos de receção ao utilizador? 
	![[Reuniao 6 maio#Expor recibos de receção na API]]
- Discutir melhor a parte dos recibos
	 ![[Reuniao 6 maio#Receipt handler para comportamento pós-receção]]
- 