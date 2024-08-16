# Introduction & Goal
In a messaging middleware, having a polling mechanism is not just a desirable feature, it's a necessity. Polling serves the critical function of determining which events are available for a given object, allowing to efficiently handle incoming data, send data, or other I/O operations.  However, the peak of usefulness is achieved when the polling mechanism has the ability to monitor events of multiple objects simultaneously in a centralized manner.

A mechanism that enables efficient handling of multiple objects is crucial to achieve high performance and scalability. It enables a single thread to handle a large number of concurrent events as opposed to having a dedicated thread for each object of interest, thus avoiding unnecessary resource consumption and latency, in addition to ensuring a higher scalability under heavy loads.

The primary objective behind designing this polling mechanism was to provide users with an efficient way to monitor multiple sockets using a single thread. This would not only provide programming flexibility but also aid in boosting performance and scalability. However, by avoiding tight coupling with sockets, the mechanism can be extended to monitor other entities, such as links or objects related to custom socket implementations, making it versatile and adaptable to a wide range of use cases.

In the following section, we will delve into the specifics of how this polling mechanism works and its architecture.

# Architecture
The polling mechanism is a simplification and adaptation of the system calls: epoll() and poll(). (*include citation to the github and pages of the manual*) 
## List Node
The `ListNode` class implements a circular doubly linked list, which is used in multiple occasions by the poller implementation, particularly in _wait queues_ to store entries and in _pollers_ to form the _ready list_. Before discussing these classes in detail, it’s important to highlight some key characteristics of the `ListNode` class.

The circular nature of this list allows for *O(1)* insertions at both the head and tail. Additionally, because it’s a doubly linked list, nodes can also be deleted in constant time (O(1)) when holding the reference to the node that should be deleted. The combination of these properties make this data structure ideal for efficiency when faced with the requirements of wait queues and pollers, as explained below.
## Wait Queue
In the diagram [[Polling Class Diagram - Wait Queue Side.png]] we observe the foundation of polling mechanism, which relies on *wait queues*. These queues manage _wait queue entries_, each corresponding to an entity interested in an event (or events) related to the queue's owner. This *waiting* system, adapted from the Linux kernel, was designed to prevent unauthorized objects from manipulating *wait queues*.

This adaptation diverges from the kernel's approach by allowing new waiters to register themselves without requiring direct access to the *wait queue* (referred as *wait_queue_head* by the kernel). By providing an initialized *wait queue entry* linked to the *queue* of interest, rather than the queue itself, unauthorized manipulation of the queue is prevented. This design ensures that waiters can only manage their own *wait queue entry* and enables the queue owner to reject a new waiter simply by not providing an initialized entry.

The process of queuing a waiter is the following:
1. The owner of the *wait queue* creates a *wait queue entry*, which remains associated with the queue throughout its lifetime. This entry is then given to the waiter that wishes to register itself in the queue.
2. The waiter, whenever desired, can add itself to the queue using either the `add()` or `addExclusive()` method.
3. The waiter is informed of available events via the `WaitQueueFunc`, which is provided when adding itself to the queue. When registering, the waiter also supplies a private object used by the function to perform actions related to the waiter. For instance, waking the waiter up, or in the case of the poller, marking the object of interest as ready for some event.
4. When the waiter is no longer interested in the events of the queue's owner, it must disassociate itself through the `delete()` method of the *wait queue entry*, upon which the entry is rendered useless and cannot be used for queuing again.

This adaptation supports two types of waiters: *non-exclusive* waiters and *exclusive* waiters, identified by the presence of the *wait flag* `EXCLUSIVE` in the entry's `waitFlags` attribute. Non-exclusive entries are registered using the `add()` method and inserted at the head of the queue. A non-exclusive entry refers to a waiter that does not mind having the event notifications shared with other entries. On the other hand, an exclusive entry refers to waiters that want exclusivity regarding notifications. These kind of entries are registered using the `addExclusive()` method and are inserted at the tail of queue.

To better understand how exclusivity works, delving into the specifics of the notification methods is needed. This implementation includes two notification methods:

The first notification method, adapted from the kernel, has the following signature: `int wakeUp(int mode, int nrExclusive, int wakeFlags, int key)`. The `mode` and `wakeFlags`, if required, are used under the queue owner semantics . The `nrExclusive` parameter specifies the maximum number of exclusive entries that can be woken up, with a value of zero or less indicating that all entries should be woken up[^1]. The `key` is used to indicate available events. This method iterates over the queue's entries, starting from the head, invoking each entry's `WaitQueueFunc` with `mode`, `wakeFlags`, `key` and their associated private object as parameters. The iteration stops when the specified number of exclusive entries have been successfully woken up or when the end of the queue is reached. In short, calling this method wakes up all non-exclusive entries (since they are inserted at the head) and a number of exclusive entries up to the value specified by `nrExclusive`.[^2]

[^1] Global wake-up calls are typically executed when the object of interest is closed. The global "close" notifications are commonly triggered by a combination of the `HUP` flag (indicating that the peer has hung up or the object has been closed) and the `FREE` flag (indicating that the object wishes to be released, requiring waiters to remove themselves from the queue so the object can close gracefully).
[^2] To ensure true exclusivity, it is crucial to avoid using the queues (or their owners) in a way that both non-exclusive and exclusive entries coexist in the queues simultaneously. This is essential to prevent non-exclusive entries from draining the available events before the exclusive entries have an opportunity to process them.

The second notification method, which I developed, corresponds to a fair version of the first method. The objective of this method is providing an equal opportunity in being notified to all exclusive entries. In the first notification method, exclusive entries are not relocated after being woken, which can lead to monopolization by the first few entries in the queue. For instance, if we assume the most common scenario, characterized by `nrExclusive` equal to 1, we can verify that the entries that follow the first exclusive entry do not get a chance of being notified unless the `WaitQueueFunc` of the exclusive entries removes the entry from the queue or the return value indicates an unsuccessful wake up.  For scenarios where the monopolization of events is not desired, this second notification method addresses this issue by moving successfully woken up entries to the tail of the queue when their `WaitQueueFunc` does not result in their removal (deletion), and thus ensuring that all exclusive entries have an equal opportunity to be notified.
## Park State


***Talk about the park state and the list node (used by the kernel for O(1) insert and remove operations) to end this part.***

![[Polling Class Diagram - Wait Queue Side.png]]
![[Polling Class Diagram - Poller Side.png]]
- Duas vertentes: poller instance (epoll())e no-poller instance (poll()).
- Ambas simplificacoes e adaptacoes das system calls
- poller instance nao suporta nesting
- falar de fair wake ups que nao existem no kernel

The designed polling mechanism is simplification and adaptation of the epoll() and poll() system calls.

# Design Walk-through
- Diagrama está no ficheiro de concepção **(Polling Model -> Polling Class Diagram)**
- Falar no percurso até encontrar este mecanismo.
	- 1º tentei desenvolver um mecanismo destes com o intuito de servir apenas os links dos sockets, para se poder fazer `waitLink(sid : SocketIdentifier)` e `waitAnyLink() : Socket Identifier`. Na primeira tentativa pensei em criar queue's de pedidos para cada link, e em registar-me em todas as queues, no entanto, surgiram alguns problemas que me levaram a desistir dessa abordagem. O primeiro era não saber como acordar uma thread registada em múltiplos locais sem ser através da criação de uma condição (de lock) criada especialmente para a thread a manifestar interesse em algum link. A criação destas condições a cada pedido não me pareceu razoável, logo ponderei a possibilidade de na API se poder "registar" a thread, ficando uma condição associada e que mais tarde poderia ser eliminada quando o utilizador decidisse que a thread não seria mais utilizada para esse propósito. Para além desse problema, pensei que quando fosse demonstrado o interesse em todos os links (`waitAnyLink()`), registar a thread em todas as queues seria muito pouco eficiente, além de ser necessário posteriormente remover todos esses registos após receber a resposta. Dados esses problemas, e não me apercebendo da utilidade de esperar por uma combinação específica de links para o desenvolvimento de sockets, que por consequência indica uma baixa probabilidade de alguém usar tal funcionalidade, decidi optar por uma outra abordagem.
	- Falar da abordagem que desenvolvi por inteiro, como uma queue global para pedidos que tivessem interesse em todos os links. Falar também que era um pouco rígido demais, consumia créditos para garantir reservas, etc.
	- Depois, surgindo a necessidade de um poller, aqui não se poderia escapar à demonstração de interesse em combinações distintas de elementos, logo decidi explorar os mecanismos mais conhecidos para estas tarefas: select(), poll() e epoll(). De alguma forma tenho de incluir o que descobri sobre estes, e as minhas conclusões sobre as suas utilidades.
	- Falar então que decidi implementar uma versão mais simples do epoll(), que não permite "nesting", e que essencialmente tem como base a ideia que tive inicialmente e descartei. Sendo esta uma versão bem mais sofisticada para além de ser genérica e muito menos rígida que a minha. O formato de registar callbacks que até lá me pareceu perigoso, passou a parecer-me extremamente útil desde que o acesso fosse reservado a partes internas do middleware já que estas, se forem bem testadas não exibirão comportamento malicioso e contraprodutivo. O facto de o interesse ser registado e mantido, até ser explicitamente eliminado, seja pelo callback de espera ou quando já não houver interesse, resolveu uma das minhas preocupações que era a cada chamada registar o interesse em cada pedido só para em breve o ter de remover. A possibilidade de usar em modo level-trigger ou edge-trigger, poder registar-se o interesse como exclusivo ou não, e ainda poder remover o interesse momentaneamente (EPOLLONESHOT) fazem este mecanismo bastante desejável.
	- Relativamente ao problema referido inicialmente de não conhecer uma solução para além de criar uma condição, decidi optar por uma solução que não escapa da criação de um objeto, chama-se ParkState e inclui a referência da thread associada a este e uma variável booleana atómica para determinar quando a thread está à espera e quando é acordada. Apesar de exigir a criação de um objeto, ao combinar o facto de poder manter o registo do interesse, logo não sendo necessário criar o objeto múltiplas vezes, com o facto de não exigir a existência e a aquisição de um lock, esta solução pareceu-me satisfatória. Para uma thread esperar e posteriormente ser acordada, recorri ao mecanismo LockSupport que vim a descobrir posteriormente.
	- Falar de como é evitado o uso de mecanismos de sincronizacao como Locks+Condicao para acordar as threads e usa-se LockSupport que permite acordar a thread específica que pretendemos. Usa-se o park state e define-se preventivamente a thread como "parked". Esta atribuição é colocada em todos os momentos na qual se pretende que a entrada seja dada como válida para ser acordada, mesmo que a seguir se sigam múltiplas ações antes de ser esperar ("dormir" até ser sinalizado ou ficar sem tempo, i.e., *times out*). 
		- Por exemplo, um waiter de um poller marca o seu estado como "parked" antes de adicionar a sua entrada na queue de waiters do poller. Isto porque a partir do momento em que a entrada é adicionada e o lock seja libertado (após a adição) é possível que o poller queira acordar a thread, mas se ela não estiver marcada como "estacionada" então não o vai ser, podendo outra thread vir a ser sinalizada no seu lugar, ou até mesmo ser necessário esperar por um próximo evento para tal acontecer. 
		- Outro exemplo, é no poll imediato (sem instancia de poller) em que se define o estado de "estacionamento" como "parked" antes de se começar a tentar pescar eventos. Isto é necessário porque é possível que após passar por um pollable que não mostra qualquer evento como disponível, que este passe a ter eventos disponíveis. Se o estado não estiver receptível a wake-up calls, então se não for encontrado qualquer evento nos restantes pollables, a thread irá "esperar" desnecessariamente por uma nova notificação quando uma notificação tinha sido emitida anteriormente mas não foi aceitada porque o estado não estava receptível a tal, i.e., não estava como "parked".
	- 
- tentei pesquisar por implementações em Java que simulassem o epoll. Como é o caso do Selector que permite este tipo de funcionalidade. No entanto, este mecanismo assim como as system calls, utiliza por base descritores de ficheiros, e então acede ao kernel. As trocas entre user e kernel são custosas. Logo pareceu-me ineficiente optar por um "workaround" em que criava descritores de ficheiros associados a sockets virtuais (que existem apenas no espaço do utilizado) só para reaproveitar um mecanismo de polling. A utilização desse mecanismo, de certa forma, removeria a vantagem de multiplexar todas as mensagens sobre o mesmo socket UDP. Dito isto, optei por estudar a implementação de um mecanismo open-source que seguramente seria boa já que está disponível numa grande quantidade de dispositivos, nos dispositivos que têm por base linux. Este estudo permitiria-me entender como especialistas da área abordaram este problema, e desta forma seria-me possível fazer uma adaptação do mecanismo para Java para ser utilizado unicamente no nível de utilizador (sem recorrer ao kernel) e desta forma conseguir obter um desempenho superior por não serem necessárias trocas com o kernel mas também que não necessitaria de consumir recursos desnecessários com a criação de pipes (ou outro tipo de ficheiros) para permitir reaproveitar um mecanismo já desenvolvido.
# Notas
- Destacar o mecanismo das wait queues que será amplamente utilizado.
- O poller será utilizado não só pelo utilizador para esperar por eventos nos sockets de interesse, mas também a nível interno para fornecer funcionalidades de esperar pela disponibilidade de um link qualquer. 
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
- Reuniao 6 Maio - Ideias sobre pollers 
	![[Reuniao 2024-05-06#Selectors / Pollers]]
- 