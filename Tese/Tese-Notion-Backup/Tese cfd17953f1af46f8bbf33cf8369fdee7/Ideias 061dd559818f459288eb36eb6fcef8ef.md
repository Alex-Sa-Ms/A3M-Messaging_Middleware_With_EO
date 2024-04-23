# Ideias

- Escrita tese
    - 1+ capitulo - estado da arte
        - apis, padroes, etc
        - implementacoes especificas, algumas fallhas, o que se faz perante certas situacoes
        - possivel solucao, falar sobre o exon
    - 2 capitulo - falra sobre a solucao
        - padroes a ser implementados
            - o que fazer em certas situacoes tendo em conta o exactly once
            - 
- Este [link](https://zguide.zeromq.org/docs/chapter1/#Why-We-Needed-ZeroMQ) redireciona para um capitulo do *ZeroMQ guide* que indica problemas que um MOM deve resolver.
    - E dá algumas ideias de como potencialmente resolver
- Pensar na API entre Exon e o serviço de presencas `(nao me parece que vá haver tempo para isto)`
    - O servidor de presencas pode informar de forma assincrona quando um peer volta a ficar online, para que nao exista busy querying ao sistema de presencas
    
- Multiplexar diferentes contextos usando o mesmo socket (tipo os links AMQP). `(muito complicado e provavelmente sem utilidade geral)`
    - Se implementado de uma forma geral, utiliza-se apenas uma stream para os que não necessitam de tal funcionalidade, e mais streams para os que podem beneficiar da funcionalidade.
    - Os contextos dos sockets NNG não identificam as coisas como streams. Pode ser encarado como unidades de processamento que possibilitam que múltiplas tarefas sejam executadas em simultâneo sobre o mesmo socket. Ou seja, não é necessário que ambos os sockets tenham contextos que deem match. As mensagens não possuem informação sobre o contexto que criou uma mensagem ou para que contexto a mensagem deve ser enviada.
    - As mensagens enviadas por links sao identificadas sequencialmente dentro da sessão. Preferia que houvesse uma noção de stream em que as streams são independentes entre si. Não acho que faz mt sentido criar diferentes links para separar os dados enviados e no por trás a ordem estar correlacionada entre eles.  Pelos vistos a partilha da sequência de entrega entre os links da mesma sessão serve para possibilitar a confirmação de receção das mensagens de forma eficiente.

# Exon

- Permitir optar por rececao ordenada das mensagens
    - Fornecer opcao para receber as mensagens de forma ordenada, ou usar a vertente não ordenada
    - Para vertente ordenada, possivelmente oferecer duas possibilidades:
        - Uma com head-of-line blocking? Para dispositivos com recursos muito limitados, no entanto parece uma solução com desempenho muito fraco
            - Definir a janela do controlo de fluxo deve ser o suficiente para evitar que os recursos sejam exaustos
        - Uma que permite a persistência de múltiplas mensagens, e a operação de entrega (deliver) é feita através do identificador (token) da mensagem
    - Como implementar ordem das mensagens no Exon? Permitir que a biblioteca crie uma queue em que são armazenadas as mensagens até chegar a mensagem com o token correto? E no caso de dispositivos com recursos muito restritos? Criar outra variante que sacrifica o desempenho fornecido por acumular mensagens?
        - Push-pull deve precisar, mas possivelmente pode ser opcional
        - pensar que o tamanho do buffer pode ser restrito tanto na origem como no destino
- Persistência do estado da biblioteca para permitir que uma conexão seja retomada caso o nodo falhe de forma não expectável. `(plano futuro ou nao)`
- How to avoid memory exhaustion?

### Sistema de diretorias para o Exon

- Sistema de diretoria para ser possível descobrir novamente os peers, e para que estes possam ser identificados por nomes
    - Embora seja algo pouco provável, se ambos os peers mudarem de rede em simultâneo, ambos possuem o endereço antigo do outro peer, e portanto deixa de ser possível “reconectar”, daí ser necessário um sistema centralizado ao qual é possível informar a mudança de endereço e questionar acerca de um possível endereço novo associado ao peer que pretendemos retomar a conexao
    - **Ver como é que isto é feito no QUIC**
    - Assumindo a situação em que ambos não sofrem uma mudança de endereço em aosimultaneo, uma possível optimização que permite uma reconexão mais rápida, é a tentativa de reconexão por parte do peer que sofreu a mudança de rede, já que este ainda conhece o endereço do outro peer.
        - Para esta reconexão ser segura, é necessário existir autenticacao de modo a evitar spoofing.
- Como implementar um serviço de diretorias na camada de transporte (Exon)?
    - O professor deu a ideia de fazer um serviço de presenças. “De presenças” porque para além de ser utilizado para atualizar o endereço dos dispositivos, pode ser utilizado para notificar os interessados sobre quando um dispositivo volta a ficar ativo.
        - A parte de notificar pode ser interessante, já que utilizando um modelo de programacao por eventos, permite evitar consumo desnecessário de bandwidth com retransmissoes que não irão chegar ao destino.
    - Isto é um serviço adicional logo, é necessário pensar na API do Exon e consequentemente a do middleware para que seja possível utilizar ou não utilizar o serviço de diretorias.

---

# Protocolo

- Ver como o MQTT garante os diferentes tipos de quality of service
    - O Exon já garante exactly-once, mas apenas entre nodos adjacentes. Se nos padroes de comunicacao se puder criar brokers, deve ser útil ter uma métrica parecida, por exemplo, em pub-sub, ter uma garantia em que todos os subscribers conhecidos (incluindo os desconectados abruptamente q eventualmente possam reconectar) pelo broker recebem a mensagem, ou simplesmente tenta enviar na hora, se não tiver conectado, então nao  recebe a mensagem
- ~~multiplexing channels com flow control individual como no AMQP~~
    - Demasiada complexidade
- autenticacao e criptografia

---

# MOM

- pensar como é que se pode suportar diferentes protocolos?
- Modelo de programação
    - Usar *actor model?*
        - Como em [https://aosabook.org/en/v2/zeromq.html](https://aosabook.org/en/v2/zeromq.html) ??
            - Ver a secção 24.8.
    - Pensar na parte do threading model
        - No ZeroMQ é usado inprocess comunication para as threads comunicarem entre si
        - É necessário usar **polling** para definir o que fazer quando se recebe uma mensagem de um certo socket, isto pq é necessário utilizar multiplos sockets dentro da mesma aplicação para conseguir distribuir trabalho por multiplas threads.
- Tentar usar algoritmos lock-free
    - procurar solucoes existentes já provadas
- Disconnect timeout
    - é necessário pensar a parte de possibilitar o utilizador definir quanto tempo se deve passar para dispensar um cliente para evitar DoS attacks
- IO assíncrono
    - Usar polling
    - Possivelmente fazer batching
        - idealmente, deve-se permitir que o utilizador escolha qual prefere melhorar: latency ou throughput
            - batching aumenta o throughput mas sacrifica a latency
            - sem batching existe menos latencia mas o throughput é reduzido
- Fornecer níveis de persistência como o MQTT? Será que faz sentido tendo em conta o exactly-once.
- Clean session flag como no MQTT?
    - A recuperacao da conexao é responsabilidade do Exon, e se o Exon implementar persistencia do estado, é necessário implementar no MOM também? Passar este conceito de clean session para o Exon? No entanto, se o MOM souber da existência disto, ele deve poder escolher se quer recuperar o estado ou nao.
- Evitar file descriptors e interlocking state machines, para melhorar extensibilidade como o nng?
- Inspirar no NNG porque é 🔥, ver erros retornados pelas funcoes, etc.
    - Por exemplo, usar uma noção tipo pipes e permitir conectar e dar bind usando o mesmo socket.
- Ver cenas de dar listen nas várias interfaces tipo ao usar endereços como “*:4444” ou “0.0.0.0:4444”
- Ao ser tudo baseado em exactly-once é necessário usar backpressure para evitar esgotamento dos recursos. Fazer as operaçoes *send* bloquear quando se chega a um certo número de mensagens que estão por entregar. (Tunar o control flow do Exon para ser customizável)

---

# Padrão de comunicação

- Padrões que gostaria de implementar:
    
    (pela ordem de preferência de implementação)
    
    - Request-Reply
    - Publish-Subscribe
        - Ver como fazer o match de forma eficiente. Explorar como é que o MQTT, nanomsg, etc fazem.
            - nanomsg supostamente usa o que se chama *Patricia trie:* [https://250bpm.com/blog:19/](https://250bpm.com/blog:19/)
            - PUB/SUB no ZeroMQ: [https://250bpm.com/blog:39/](https://250bpm.com/blog:39/)
        - armazenar uma quantidade de mensagens definidas (por topico ou geral?), para ao efetuar uma subscrição, n ser necessario esperar pela proxima publicacao. Tipo o que é feito pelo MQTT, com a *retain* flag.
        - Backup brokers (ligar a brokers de backup apenas quando o atual principal falhar)peers
            - clustering do mqtt será uma solucao melhor? Perceber como funciona e como é implementada exatamente!
        - Ver como é que o MQTT implementa exactly-once, provavelmente com a persistencia com acknowledgment
        - MQTT usa quality of service declarado pelos clientes quando se ligam ao broker. Isto permite definir o grau de importancia das mensagens, e se é msm necessario garantir que cheguam, e quantas vezes chegam. Permite também que no caso de um subscriber se desconectar de forma imprevista, se saber se é necessário guardar as mensagens numa queue, ou ignorá-las.
        - permitir wildcards para os topicos?
            - como no MQTT, embora como dito nos essentials do MQTT, isto facilmente torna o broker mais lento
        - AMQP e MQTT permitem publish-subscribe usando diferentes criterios, i.e., não só pelo tópico
        - permitir multiplas subscricoes na msm mensagem
        - shared subscriptions como no MQTT
            - pode ser uma feature útil, funciona tipo um dealer do ZeroMQ. Para um certo tópico, os clientes que partilhem a mesma subscrição vão recebendo alternadamente os dados
    - Push-Pull
    - Survey
        - *survey pattern* do nanomsg para service discovery e outros tipos de problemas que requerem votações
            - [https://250bpm.com/blog:5/](https://250bpm.com/blog:5/)
    - 
- Para tentar obter padroes mais versateis, analisar similaridades entre padrões
    - Ex: PUB-SUB e PUSH-PULL funcionam apenas numa direcao, um envia para todos outro envia para apenas um de todos os destinos. Tentar ver como se pode juntar tudo num socket e apenas com a mudanca de uma configuracao ser possivel modificar o comportamento (é mais versatil do que criar sockets novos)
- *bus pattern. (**Não gosto deste pattern**)*
    - *F*unciona do tipo ([https://250bpm.com/blog:17/](https://250bpm.com/blog:17/)):
        - passa-se lista de ips e portas para o socket, e ele trata de conectar-se com todos.
        - possivelmente alguém tem de conhecer alguém da network, e dps os ips e portas dos restantes são partilhados entre eles
            - Tentar ver se é possível fazer a conexão de uma maneira melhor, já que parece ser necessário muito flood de informação
            - Também pode-se fazer simplesmente através de um pub-sub, portanto não sei até que ponto é assim tão útil

---

# Escrita tese/pré-tese

- usar [https://aosabook.org/en/v2/zeromq.html](https://aosabook.org/en/v2/zeromq.html) para inspiração
    - particularmente o capitulo 24.11.