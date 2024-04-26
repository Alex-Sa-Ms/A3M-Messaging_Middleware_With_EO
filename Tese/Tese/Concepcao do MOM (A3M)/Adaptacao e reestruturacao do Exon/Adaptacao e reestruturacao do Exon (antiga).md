# TO-DOs

- **Simple example with only A and B, where B changes to another address. To show that a new state is created and that the previous will continue to result in retransmissions but those will never reach node B unless he changes to the previous address again.**
- **Complement the previous example with another node changing to the previous address of B.**
- **Example for why the destination address is needed.**
- **Talk briefly about solutions explored before that did not work.**
- **Make diagrams to help understand what is happening (check the diagram in the paper of Exon)**

- Falar sobre os passos para passar a suportar a mobilidade
- Falar sobre o porque de ter de ser esta solucao
    - Para falar sobre o porque de ter de ser esta a solucao, é necessario falar um pouco sobre a implementacao do protocolo.
- Antes de falar sobre os cenários de mobilidade é necessário relembrar como é que o protocolo Exon funciona e em que consiste a implementação base deste protocolo.
- O parte mais importante que é necessário relembrar são as designadas *half-connections*. Existem dois tipos de half-connections: send half-connections e receive half-connections. O estados das half-connections são guardados em registos designados por *SendRecord*s e *ReceiveRecord*s.

# Problemas (Serve para mostrar exemplos de alternativas que não funcionam)

- Como obter os identificadores únicos?
    
    Para obter os identificadores únicos existem várias opções, no entanto, cada uma tem os seus pontos negativos. Obter os identificadores únicos, exige sempre mais lógica do que apenas utilizar o endpoint fonte mencionado no datagrama, logo será sempre menos eficiente.
    
    Soluções possíveis:
    
    - Usar serviço de diretorias externo
        - Necessita que um sistema de diretorias externo exista, seja mantido e seja conhecido por todos os nodos (para que estes possam conhecer os outros nodos e atualizar os endereços em caso de mobilidade).
        - Necessita que os nodos estejam registados antes da “comunicação” ser iniciada através do Exon.
        - Necessário permitir que o Exon aceda ao sistema de diretorias.
        - Para um correto funcionamento do algoritmo (assegurar Exactly-Once delivery), é necessário que o Exon ignore mensagens de nodos não registados no sistema de diretorias ou que implemente um mecanismo de “merge” de registos (SendRecords e ReceiveRecords) para reconciliar o estado após se detetar que múltiplos registos correspondem ao mesmo nodo.
            - **No caso de não estarem registados, uma solução poderia passar por existir um comando especial que requisita a identificação, e um comando especial para enviar a identificação. Neste caso apenas seria necessário atualizar a tabela de associações entre identificadores e endereços.**
    - Todos os frames carregarem o identificador do nodo
        - Não necessita que o estado seja reconciliado já que as mensagens conseguem ser sempre associadas ao registo correto.
        - Obviamente levaria a um consumo excessivo e desnecessário de bandwidth.
        - Ao existir uma solução que permite reconciliar o estado e que garanta que todas as mensagens são entregues exatamente uma vez, independentemente, do tempo que demorem a ser entregues, então essas soluções devem ser ideais, já que não exigem o consumo desnecessário de bandwidth e apenas possuem overhead quando existe a mudança de endereço e de identidade, já que exige a reconciliação do estado e o reenvio das mensagens.
    - Atribuir identificadores únicos locais automaticamente (correspondem ao endpoint do peer remoto) a peers cujo endereço não é conhecido. Permitir que a upper layer identifique os peers e atualize a identidade ou endpoint.
        - A atualização da identidade ou do endpoint, como referido acima, necessita que o estado seja reconciliado, resultando na identificação dos registos que devem ser eliminados (os que não estejam associados ao identificador único mencionado na criação da associação), a cópia das mensagens (tanto as presentes na queue como nos tokens) para o registo associado ao identificador único, no cancelamento (se necessário) de eventos associados ao identificador “errado” e na retransmissão das mensagens.
- Em cenários de mobilidade, como é que é o Exon pode garantir Exactly-Once delivery? **Identificadores únicos VS endereços de transporte**
    
    A implementação base do Exon utiliza os endpoints (par de endereço IP e porta) como os identificadores dos nodos. Se o endpoint mudar devido à mobilidade de um dos peers, já não é possível através do novo endpoint (presente no datagrama recebido) encontrar o estado ao qual o frame se refere. Portanto, o Exon encara esta mensagem como proveniente de um nodo diferente, ou seja, criado um estado novo (SendRecord ou ReceiveRecord, dependendo do tipo de frame recebido) associado a este novo endpoint.
    
    Para que seja possível encontrar o estado pretendido, é óbvio que a noção de uma identidade única e global é necessária. Dito isto, os registos (SendRecords e ReceiveRecords) devem passar a ficar associados a identificadores únicos e não aos endpoints.
    
- Porque é necessário “merge” de registos?
    - Quando um dos nodos tem o seu endpoint mudado, qualquer tentativa de comunicação resultaria na criação de SendRecord e/ou ReceiveRecord para o novo endpoint. Isto porque enquanto a atualização do endpoint não é comunicada ao sistema de diretorias, o Exon não consegue encontrar o identificador associado ao endpoint, e portanto, cria um identificador automaticamente, assumindo que este não mudará. Portanto, é necessário que o Exon seja posteriormente avisado da mudança de endereço, e que prossiga com a reconciliação do estado, i.e., que de alguma forma junte o(s) novo(s) registo(s) criados para o endpoint desconhecido com os registos associados ao identificador único.
        - Também se poderia optar por uma opção em que mensagens de nodos não registados são ignoradas, o que simplifica o processo, e deixa de ser necessário reconciliar estado.
- Como pode ser feito o “merge” de registos