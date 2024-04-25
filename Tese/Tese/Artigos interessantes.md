# Artigos interessantes

# Usados no State Of The Art

- 23 - Getting Rid of ZeroMQ-style Contexts [(link)](https://250bpm.com/blog:23/)
    - Usar contextos em bibliotecas de comunicação não é uma aplicação correta, porque a partilha de dados é necessária para que diferentes módulos que utilizem a biblioteca de comunicação consigam ver-se uns aos outros.
    - Não fala como construir a aplicação de forma a eliminar o contexto, apenas refere que não é algo que faz sentido para uma biblioteca de comunicação.

# Lidos

Artigos lidos com potencialmente pequenos resumos.

- 5 - Distributed Computing: The Survey Pattern [(link)](https://250bpm.com/blog:5/)
    - Apenas descreve o padrão survey.
    - Surveyor envia survey message para todos os respondents. Aguarda no máximo um certo tempo (timeout), para que todos os respondents tenham tempo para responder.
- 13 - Location-independent Addressing with Messaging Middleware [(link)](https://250bpm.com/blog:13/)
    - Resumidamente, fala como é que se encontra um serviço através de um nome (ex: MyApplication1), em vez de utilizar o par IP e porta. Solução é um serviço de diretorias, mas que na sua opinião deve ser implementada na network stack, e não na camada de comunicação em que os messaging middlewares se encontram.
- 14 - Priotitised Load Balancing with nanomsg [(link)](https://250bpm.com/blog:14/)
    - Provavelmente, um pouco fora do foco da tese.
    - No *ZeroMQ* é usado fair-balancing, que consiste em atribuir as mensagens sempre na mesma ordem, i.e., se tivermos 2 sockets REP para responder a pedidos, a 1ª mensagem vai para o REP 1, a 2ª vai para o REP 2, a 3ª volta a ser enviada para o REP 1, a 4ª é enviada para o REP 2, …
        - Na minha opinião isto tem um problema, já que se assume que todas as mensagens são de igual trabalho, ou que todos os nodos têm o mesmo poder computacional. Dito isto, talvez seja melhor implementar algum tipo de flow control.
    - No *nanomsg* funciona da seguinte forma: Aos nodos são atribuídas prioridades; As mensagens são sempre enviadas para os nodos com a maior prioridade, se estes não tiverem disconectados ou ocupados; Só num desses casos, é que se usa um nodo com menor prioridade. O exemplo referido é por exemplo se tivermos um data center em Nova Iorque e outro em Londres. Os pedidos feitos em Nova Iorque não devem ser enviados para o data center de Londres caso não seja mesmo necessário, isto porque há um grande aumento na latência da resposta ao pedido.
    - Defende que usar prioridades nas conexões em vez de nas mensagens é superior, já que não existe o risco de uma mensagem de prioridade superior não ser atendida por causa do head-of-line blocking nos buffers TCP.
    - Para se utilizar a prioridade nas mensagens, o *nanomsg* sugere o uso de topologias diferentes, uma para mensagens urgentes e outra para mensagens normais. O servidor faz *poll* de ambos os sockets, e atende primeiro as mensagens do socket urgente. Como são usadas duas portas TCP diferentes, então já não existe o HOL blocking nos buffers TCP.
        
        ![Untitled](Tese/Artigos%20interessantes/Untitled.png)
        
- 18 - Messaging & Multiplexing [(link)](https://250bpm.com/blog:18/)
    - Fala sobre “Single TCP connection with multiplexing” contra “Multiple TCP connections”. Essencialmente, multiplexar não tem as vantagens que se pensava ter, e portanto “Multiple TCP connections” ganha.
- 19 - Optimising Subscriptions in nanomsg [(link)](https://250bpm.com/blog:19/)
    - Fala sobre uma estrutura de dados que pode ser utilizada para guardar as subscrições. Essencialmente é uma árvore cujos nodos são palavras (substrings da string original que define o tópico). Por exemplo, se existessem apenas as duas subscrições seguintes: “carro” e “carruagem”, existiram 3 nodos, o nodo “carr” que corresponde à parte comum entre os dois tópicos e depois dois nodos filhos “o” e “uagem”.
- 24 - The Callback Hell [(link)](https://250bpm.com/blog:24/)
    - Evitar programar com callbacks, como por exemplo, o objeto A executa uma funcao do objeto B que executa uma funcao do objeto A, e dps a funcao inicial (do objeto A) tem o seu estado modificado sem que fosse essa a sua intencao.
    - Sugere o uso de state machines e utilização de eventos (assíncronos).
- 25 - Event-driven architecture, state machines et al. [(link)](https://250bpm.com/blog:25/)
    - Tentar organizar o código num formato de raíz para folhas, em que chama-se funções (métodos) no sentido da raíz para as folhas, e usa-se eventos para fazer a comunicação inversa, i.e., sentido das folhas para a raíz.
    - Obviamente pensar bem quando se quer introduzir mais eventos, i.e., comunicação no sentido das folhas para a raíz.
    - Para os humanos pensar nas coisas na forma de objetos é mais intuitivo (daí ter surgido Object Oriented Programming) e memorizar narrativas é muito mais fácil do que memorizar factos diretamente. Tendo a afirmação anterior em conta, o uso de state machines permite transformar mecanismos de processamento abstratos (os objetos) em narrativas. Criar narrativas ajuda perceber as ideias, e depois expandi-las para algo mais complexo.
    - Combinar eventos com state machines é uma ferramenta bastante poderosa.
- 39 - Design of PUB/SUB subsystem in ØMQ [(link)](https://250bpm.com/blog:39/)
    - É necessário reler. E usar em conjunto com o artigo [https://250bpm.com/blog:19/](https://250bpm.com/blog:19/)
    - Essencialmente, explica como é que no ZeroMQ se tenta diminuir ao máximo o uso da *bandwidth.* Em primeiro, tenta-se enviar/duplicar as mensagens ao máximo, isto tendo em conta as subscrições existentes. Se não existe nenhum subscritor para o tópico, não vale a pena enviar a mensagem para a rede e pode até ser possível não gastar os recursos computacionais (no publisher) necessários para gerar a mensagem.
    - Expande-se também o caso de existirem múltiplos intermediários, em que é necessário construir as árvores para distribuição das mensagens, mas também é necessário considerar como fazer isto sem um uso absurdo de memória.
- The Architecture of Open Source Applications (Volume 2)
ZeroMQ [(link)](https://aosabook.org/en/v2/zeromq.html)
    - Fornece boas optimizações

- [http://wiki.zeromq.org/whitepapers:messaging-enabled-network](http://wiki.zeromq.org/whitepapers:messaging-enabled-network)

# Não lidos

- 28 - Public Key Encryption for Kids [(link)](https://250bpm.com/blog:28/) (*só por curiosidade, não deve haver tempo para implementar isto na tese*)
- [https://250bpm.com/toc/index.html](https://250bpm.com/toc/index.html) (index de todos os artigos)
    - seguem todos o formato [https://250bpm.com/blog:XX](https://250bpm.com/blog:XX), com XX sendo o nº do artigo)
- [https://aosabook.org/](https://aosabook.org/) (Curiosidade, para ler depois, nao é para a tese)
- Sobre interoperabilidade [https://inria.hal.science/inria-00629057/file/InteroperabilityComplexDistributedSystems.pdf](https://inria.hal.science/inria-00629057/file/InteroperabilityComplexDistributedSystems.pdf)