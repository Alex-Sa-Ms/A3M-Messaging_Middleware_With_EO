<h1 style="color:lightgreen">✅ Already read</h1>
# Calendarização

![Untitled](Tese/Calendarizacao/Untitled.png)

[Thesis Plan](Thesis%20Plan%20e2076edb1db8456eae6af0730ec9a335.csv)

# Tarefas

## Escrita Tese

- [x]  Ler e corrigir ZeroMQ
    - [ ]  Talvez adicionar nas partes das limitações do ZeroMQ, o facto da reliability ser muito fraca.
        - Já é mencionado na parte dos cenários de mobilidade que não é bom porque as mensagens são descartadas ao acontecer a desconexão.
- [ ]  Ler e corrigir nanomsg
- [x]  Ler e corrigir MQTT
    - [ ]  Rever a parte da limitacao relativa à ordem do MQTT. Está dificil de perceber.
- [ ]  Escrever sobre NNG
- [ ]  Escrever sobre AMQP

## Concepção

- [x]  Define the system model
    - Talk about the assumed network faults (check the Exon protocol fault model)
    - Ignore node crashes
    - Ignore security
- [ ]  Check how NNG implemented ZeroTier which is a connectionless protocol.
    - Will help in figuring out how to establish an interface that allows integration of new transports, both connection-oriented and connectionless.

---

### Plano antigo

1. Estudo do estado da arte dos Message-Oriented Middlewares (MOMs), como ZeroMQ [1], AMQP [3], nanomsg [4], MQTT [5] e XMPP [6].
    1.  Identificar MOMs utilizados atualmente
    2. Pesquisar padrões de comunicação já implementados por outros MOMs
    3. Estudar protocolos dos MOMs
2. Seleção de padrões de comunicação a implementar
    1. Pensar em padroes de comunicação mais versateis (i.e. resolver a incompatibilidade entre sockets de diferentes padroes)
        1. Exemplo: Sockets PUB-SUB e PUSH-PULL são unidirecionais. Um envia para apenas um destino, outro envia para todos os destinos. Tentar ver se existe forma de juntar estes padroes usando este tipo de semelhanças.
3. Adaptação dos padrões de comunicação escolhidos em função da garantia de envio Exactly-Once
4. Adaptação e reestruturação do protocolo Exon para suportar os padrões de comunicação escolhidos
    - parametros
    - ordem de entrega?
    - quando as msgs ainda estao na queue (n), permitir cancelar enviar, quando ja estao como token
    ja nao pode ser cancelada pk ja pode ter chegado ao destino
    - sistema de nomes para sobrevivencia a mudanças de IPs
    - ...
5. Concepção do protocolo de comunicação entre nodos
6. Concepção da arquitetura do MOM
    - tentar que os sockets criados pelo MOM sejam multi-thread safe (sockets REQ-REP nao faz mt sentido ser possivel)
    - poder escolher se o socket é sincrono ou assincrono
7. Implementação do MOM
8. Avaliação de desempenho
9. Escrita da dissertação

![Untitled](Tese/Calendarizacao/Untitled%201.png)