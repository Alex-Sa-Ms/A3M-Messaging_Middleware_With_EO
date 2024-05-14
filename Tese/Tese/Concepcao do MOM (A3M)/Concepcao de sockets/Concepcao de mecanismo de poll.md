# Arquitetura
## Operação de interesse
Um poller não é utilizado para registar sockets de interesse, mas para registar a vontade de realizar um certo tipo de operação sobre um socket específico.
O tipo de operações de interesse que serão suportados são: leitura (*IN*) e escrita (*OUT*).
## Poller
### Variáveis
- Lock + condição
	- **Rationale:** Para evitar espera ativa enquanto não existe um socket disponível para realizar uma operação de interesse.
- Map de etiquetas (dos sockets) para objeto sobre o socket
	- O objeto deve conter:
		- tipo de operação de interesse a realizar sobre o socket;
		- flag que indica se o socket está disponível para realizar a operação;
### Interfaces
#### Poller
- `static +createPoller() : Poller` - cria poller
- `+register(s : Socket, op : PollOperation)` - regista socket no poller
	- "op" pode ser:
		- Poller.**POLLIN** para leitura
		- Poller.**POLLOUT** para escrita
- `+unregister(s : Socket)` - remove socket do poller
- `+poll()` - 
## Socket
### Variáveis
- 

# Lógica
## Problemas
- **Lançar exceção** quando se tenta subscrever uma operação que o socket não consegue realizar.
- Pollers não devem ser thread-safe, até porque não faz sentido mais do que uma thread utilizar o mesmo poller.
- Sockets thread-safe + possibilidade de incluir o mesmo socket em vários pollers
	- Significa que múltiplos pollers e até threads que não utilizem um poller possam estar interessados na mesma operação para o mesmo socket. Logo, como é que consegue acordar threads ou pollers de forma inteligente para que não se estorvem uns aos outros?  
		- Imaginando que dois pollers estão interessados em receber uma mensagem de um socket específico. Se apenas existir uma mensagem para receber e se ambos os pollers forem avisados que o socket está disponível para receber, então o uso do método `receive()` bloqueante pelas threads dos pollers resultará no bloqueio de uma das thread.
		- A solução deve passar por fazer o utilizador assumir a responsabilidade de querer seguir uma programação absurda. Se apenas tiver uma thread interessada em realizar o tipo de operação sobre o socket então não existirá problemas. Pode esperar que o poller informe que o socket está disponível para realizar a operação e utilizar o método bloqueante dessa operação sem que resulte no bloqueio da thread. Se optar por ter múltiplas threads a realizar a mesma operação, dentro das quais algumas utilizam um poller para verificar a disponibilidade da operação, então correm o risco das threads competirem pela execução da operação, e no caso de ser utilizado o método bloqueante, resultar no bloqueio de múltiplas threads. Portanto, deve optar por utilizar o método não bloqueante que tenta realizar a operação, e retorna "falha" se a operação devesse bloquear. 
- Adicionar respostas discutidas no email com o Professor.