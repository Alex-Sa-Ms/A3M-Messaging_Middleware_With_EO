# AMQP 1.0 Specification

# Types

## Sistema de tipos

- Define um conjunto de tipos primitivos para representação interoperável de dados
- Valores AMQP podem ser anotados com informação semântica adicional
    - Permite associar um valor AMQP com um tipo externo não presente no AMQP
    - Ex: URL é uma string, mas nem todas as strings são URLs
- **Tipos primitivos:**
    - Tipos escalares: booleans, nºs inteiros, nºs de vírgula flutuante, strings, caractéres, timestamps, UUIDs, dados binários e símbolos
    - Tipos de coleção: arrays (monomorficas), listas (polimorficas) e maps
- **Tipos descritos:**
    - Para descrever tipos *custom,* é possível anotar os tipos AMQP com um *descriptor.*
- **Tipos compostos:**
    - Define tipos compostos para permitir codificar dados estruturados
    - Cada valor constituinte do tipo composto é identificado por um *field*.
    - Cada tipo composto define um sequência ordenada de *fields*, cada *field* com um nome específico, tipo e multiplicidade.  Definições de tipos compostos podem também incluir um ou mais *descriptors*.
    - Definidos usando notação XML.
- **Tipos restritos:**
    - Consiste num tipo existente cujos valores permitidos são um subset dos valores do tipo existente.
    - Restrições podem ser do tipo: conjunto fixo pré-definido, ou um conjunto *open-ended.* Restrição é definida na definição formal do tipo.

## Codificação de tipos

- As streams de dados codificados AMQP consiste em bytes não tipados (untyped) com construtores embutidos.
- Os construtores embutidos definem como interpretar os bytes não tipados que seguem.
- Uma stream começa sempre com um construtor.
- Um construtor AMQP consiste num código de formato primitivo ou num código de formato descrito. Um código de formato primitivo é um construtor para um tipo primitivo. Um código de formato descrito consiste num *descriptor* e um código de formato primitivo. O *descriptor* define como produzir um tipo de um domínio específico a partir de um valor primitivo.

# Transport

### Modelo conceptual

- Uma rede AMQP é constituída por nodos que são conectados por *links*.
- Nodos são entidades com nome que são responsáveis por armazenar e/ou entregar mensagens. Esta responsabilidade muda de nodo à medida que esta (mensagem) percorre a rede.
    - Mensagens podem ser criadas, entregues (terminar) ou ser retransmitidas por nodos.
- Um *link* é uma rota unidirecional entre dois nodos. Um *link liga*-se a um nodo no que se designa por *terminus.*
    - Dois tipos de *terminus*: *sources* e *targets.*
    - *Terminus* é responsável por acompanhar o estado de uma stream particular seja de *incoming* ou *outcoming messages.*
    - Mensagens só percorrem um *link* se obedecerem ao critério de entrada da fonte (*source*)
- Nodos existem dentro de um *container.* Exemplo de containers são: brokers e aplicações clientes.
    - Cada *container* pode conter múltiplos nodos.
    - Exemplos de nodos:
        - *producers* geram mensagens;
        - *consumers* processam mensagens;
        - *queues* armazenam e encaminham mensagens.

### Transporte

- Define que para dois nodos em *containers* diferentes poderem comunicar é necessário estabelecer uma conexão
- Uma conexão AMQP é uma sequência de *frames* full-duplex e ordenada de forma confiável.
- Um *frame* é uma unidade de trabalho.
- Conexões têm um tamanho máximo de *frame* negociado que permite que as streams de bytes sejam defragmentadas facilmente em *frame bodies* completos que representam unidades independentes analisáveis.
- Se o *n*-ésimo *frame* chegar, então todos os frames anteriores a *n*, já devem ter chegado.
    - Assume-se que as conexões podem ser efêmeras, podendo falhar resultando na perda de um nº desconhecido de *frames,* mas é necessário cumprir o critério de entrega ordenada e confiável.
- Uma conexão é dividida num nº *negociado* de *channels* independentes e unidirecionais.
    - Cada *frame* é marcado com um número que indica o *parent channel*.
    - A sequência de *frames* é multiplexada numa única sequência de *frames* por conexão*.*
- Uma **sessão** (*session*) ****correlaciona dois *channels* unidirecionais numa conversa sequencial e bidirecional entre *containers.*
    - Sessões fornecem um esquema de controlo de fluxo baseado no nº de *transfer frames* transmitidos.
    - Uma vez que os *frames* têm um tamanho máximo para uma dada conexão, isto fornece controlo de fluxo baseado no nº de bytes transmitidos e pode ser utilizado para otimizar o desempenho.
- Uma única conexão pode ter múltiplas sessões independentes ativas em simultâneo, até ao limite de *channels* negociado.
- Tanto as conexões como as sessões são modeladas por cada *peer* como *endpoints* que guardam estado local e o último estado remoto conhecido dependendo da conexão ou sessão em questão. ***(??? wtf is this ???)***
- *Links* fornecem um controlo de fluxo baseado em créditos relacionado com o número de mensagens transmitidas
    - Ajuda a controlar quando receber explicitamente as mensagens e quantas mensagens receber num certo momento
- O *link protocol* é utilizado, dentro de uma sessão, para estabelecer *links* entre *sources* e *targets* e para transferir mensagens entre eles.
    - Uma única sessão pode estar associada simultaneamente a múltiplos links.
- Links têm nome, e o estado no *termini* pode viver mais tempo que a conexão em que foram estabelecidos. O estado mantido no *termini* pode ser utilizado para reestabelecer o *link* numa nova conexão (e sessão) com controlo preciso sobre as garantias de entrega.
- A imagem abaixo mostra a relação entre Conexão, Sessão e Link:
    
    ![Relação entre Conexão, Sessão e Link](AMQP%201%200%20Specification%20a0c5461a076c457a922762bd26ea16b0/Untitled.png)
    
    Relação entre Conexão, Sessão e Link
    
- O protocolo consiste em 9 tipos de *frame bodies.*
    - A imagem seguinte lista os diferentes *frame bodies* e define que *endpoints* é que os processam:
        
        ![Untitled](AMQP%201%200%20Specification%20a0c5461a076c457a922762bd26ea16b0/Untitled%201.png)
        
    

### Framing

- *Frames* estão divididos em 3 partes distintas:
    - **header de tamanho fixo** (8 bytes): Inclui informação obrigatória para realizar o *parse* do resto do *frame.* Inclui tamanho do frame, offset dentro do frame para o corpo do frame e tipo de informação.
    - **extensão do header:** parte de tamanho variável. Com o intuito de expansão futura. Área que depende do tipo de *frame.*
    - **corpo do *frame*:** sequência de bytes de tamanho variável, cujo formato depende do tipo de *frame.*

### Conexões

- Cada peer declara o período máximo entre frames antes que seja feita uma tentativa de fechar a conexão (envio de um *close frame*). Se não for recebida resposta dentro do timeout de resposta ao close frame, então a conexão é fechada.
    - Para evitar que a conexão seja fechada, pode ser enviado um *frame* constituído apenas pelo *header, i.e.,* sem corpo (payload).
- 

***muita cena, e é relativamente complexo***