# Concepção do controlo de fluxo (antigo)

O AMQP emprega dois tipos de controlo de fluxo: controlo de fluxo à base de janela, por sessão, para impedir que sejam recebidas demasiadas mensagens e assim perturbar ou até levar ao crash da plataforma por falta de recursos (nomeadamente de memória); e o controlo de fluxo à base de créditos, por link, para se poder controlar o ritmo de processamento.

É necessário ter em atenção que a maioria das soluções para implementar o controlo de fluxo necessitam de uma noção de conexão entre o transmissor e o recetor. A única excepção é a implementação de um controlo de fluxo à base de janela no lado do recetor. 

## Questões

**`Pensar nisto como o Professor depois de decidir se de facto vão existir contextos.`**

### Devo implementar estes dois tipos de controlo de fluxo?

- O controlo de fluxo à base de janela permite proteger a plataforma da exaustão de recursos.
- O controlo de fluxo à base de créditos permite controlar o ritmo de processamento.
- Logo ambos apresentam vantagens.

### Que entidade (nodo, ator ou contexto) deve fornecer o controlo de fluxo e em que nível (Exon ou Middleware) deve ser implementado?

- **Controlo de fluxo à base de janela**
    - Essencialmente este tipo de controlo de fluxo é vantajoso para limitar o número de mensagens que uma entidade (seja ela um nodo, um ator ou um contexto) pode ter em posse (à espera de serem processadas) num dado momento.
    - Lado de execução do mecanismo: Transmissor vs Recetor
        - Se feito no lado do transmissor
            - Exige noção de acoplamento.
            - A proteção da plataforma é possível através de cálculos. A multiplicação do limite de entidades com que se pode interagir pelo tamanho da janela para cada entidade indica-nos o limite de mensagens por processar num dado momento.
            - Se forem os nodos a possuir este mecanismo, então **certos atores do mesmo nodo podem monopolizar a janela desse nodo**, impossibilitando um funcionamento aceitável dos atores. Este problema não deve existir entre atores e contextos, já que os atores podem fazer uma distribuição justa das mensagens pelos contextos. No entanto, se os atores utilizarem um controlo de fluxo à base de créditos é possível controlar a monopolização da janela.
            - É necessário que:
                1. O recetor indique ao transmissor o número de mensagens que está disposto a receber desse transmissor. Assim, o transmissor pode bloquear a transmissão de novas mensagens quando o limite é atingido, i.e., quando a janela está cheia. 
                2. O recetor tem de enviar mensagens a confirmar que as mensagens foram processadas para libertar espaço na janela e assim permitir ao transmissor enviar novas mensagens.
            - Implementar este mecanismo no **Exon**:
                - Significa que a janela se refere a mensagens entregues à aplicação e não a mensagens processadas. No entanto, a aplicação apenas pedirá ao Exon mensagens quando as puder processar, portanto uma pequena diminuição desta janela tem o mesmo efeito do que confirmar que mensagens foram processadas.
                - Implica usar os nodos como a entidade foco do mecanismo (Exon trabalha a nível do nodo e não conhece o conceito de atores e contextos do Middleware);
                - Implica modificar o protocolo para conseguir informar o tamanho das janelas e informar que o limite de nodos com que a comunicação é permitida já foi alcançado;
                - Permite utilizar os ACKs para atualizar a janela;
            - Implementar este mecanismo no **Middleware**:
                - Significa que a janela se refere a mensagens processadas pelo Middleware;
                - Implica que mensagens de confirmação de processamento sejam enviadas;
                - Para que as confirmações não utilizem largura de banda desnecessária mas sem comprometer o throughput, múltiplas confirmações podem ser agrupadas numa única mensagem, mas devem ser enviadas assim que o número de confirmações acumuladas atingir um patamar aceitável (deve ser testado, mas digamos 20% do tamanho da janela).
                - A troca de informações como o tamanho da janela e de quando o limite de nodos foi atingido é facilmente enviado e processado pelo Middleware utilizando o Exon por baixo.
            - **Conclusão:**
                - Utilizar o **nodo** com a entidade controladora do fluxo à base de janela pode ser viável se acompanhado do controlo de fluxo à base de créditos para controlar o ritmo de envio dos atores.
                - Utilizado de forma isolada não é recomendável já que certos atores do nodo podem monopolizar a janela desse nodo, para além de que um nodo que possua múltiplos atores pode ser prejudicado em relação a um nodo que não possui tantos atores.
                    - `Os contextos de um ator trabalham todos para o mesmo propósito logo dividir o trabalho é simples e se um envia mais mensagens do que o outro não é propriamente relevante. No entanto, garantir que os atores do mesmo nodo têm todos as mesmas oportunidades é algo essencial e que provavelmente deve ser gerido pelo nodo em si e não pelos controlos de fluxo que agrupam nodos e atores remotos como referido aqui. Como é que se pode implementar um algoritmo justo que permite os atores terem as mesmas oportunidades?`
                        - `Estabelecer proporções iguais e cada ator tem um contador ou uma estrutura que permite identificar quantas mensagens desse ator estão a ocupar a janela?`
                            - `Será que é mesmo necessário controlar se os nodos monopolizam ou não a janela?`
                                - `Desde que se garanta que as mensagens são enviadas pela ordem em que se invoca o método de envio, então, nodos que trabalham mais devem ter a possibilidade de enviar mais mensagens.`
                                - `Permitir definir proporções e permitir que o envio seja livre também pode ser uma opção.`
                            - `Seria ainda possível depois ajustar as proporções em função de prioridades fornecidas aos atores.`
                            - `Prioridades nas mensagens também?` Já parece ser trabalho a mais.
        - Se feito no lado do recetor `(não é solução desejável)`
            - Não exige noção de acoplamento.
            - O próprio nodo controla as mensagens que são colocadas em fila para serem processadas.
            - Enquanto a janela se encontrar cheia, as mensagens são descartadas.
            - Como as mensagens são descartadas, é necessário que a retransmissão ocorra até que eventualmente a mensagem é aceitada.
            - Aplicação a nível do Middleware não é viavél porque exigiria que o Middleware guardasse as mensagens, retransmiti-se e garantisse Exactly-once delivery. A garantia Exactly-Once do Exon não serviria já que as mensagens seriam entregues ao Middleware, mas acabariam descartadas e necessitando retransmissão se a janela estivesse cheia. No entanto, como a mensagem já tinha sido entregue pelo Exon, esta teria de ser reenviada novamente pelo Middleware transmissor, levantando novamente o problema de garantir Exactly-Once delivery.
            - Esta implementação teria de ser feita pelo Exon, já que o Exon poderia descartar as mensagens do tipo TOKEN enquanto a janela estivesse cheia (i.e. enquanto a *delivery queue* está cheia), e uma vez que as mensagens são retransmitidas até serem entregues o algoritmo continuaria correto.*(provavelmente seria necessário atualizar os valores dos registos associados aos RTTs para não deixar que os descartes de mensagens influenciem esses valores)*
            - Esta abordagem apresenta ainda um grave problema que é o facto da janela poder ser monopolizada por certos transmissores.
            - `Concluindo, o controlo de fluxo à base de janela no lado do recetor não é uma solução desejável.`
- **Controlo de fluxo à base de créditos**
    - A nível do nodo
    - A nível do ator
    - A nível do contexto de um ator

### Em que lado deve ser o controlo de fluxo à base de janela implementado? Do lado do transmissor? Do lado do recetor?

- Implementar no lado do transmissor permite evitar envia mensagens desnecessariamente.

### Como implementar o controlo de fluxo à base de janela?

### Como implementar o controlo de fluxo à base de créditos?

1. Faz sentido implementar controlo de fluxo à base de janela tanto para envio como para a receção?
      3. A implementar o controlo de fluxo à base da janela acho que apenas faz sentido a nível do nodo. Para envio consistia em limitar o número
            de mensagens enviadas que podem estar em trânsito. Para receção, consistiria em limitar o número de mensagens recebidas por processar.
            3.a) Para limitar no envio, se implementado a nível do middleware, seria necessário receber mensagens de confirmação de forma a atualizar o valor da janela, ou seria necessário que o Exon permitisse verificar se uma mensagem ainda está a ser enviada ou não. Ou, implementar a nível do Exon mesmo, que seria uma solução mais simples já que bastaria utilizar um contador que aumenta quando o método send() é invocado e diminui quando um ACK é recebido.
            3.b) Para receção, se implementado, acho que apenas deveria ser implementado localmente a nível do Exon. Esta solução consistiria em descartar as mensagens do tipo TOKEN até que exista espaço na queue de entrega de mensagens à aplicação. A nível do middleware acho que não faria sentido já que mensagens não podem ser descartadas porque não existe retransmissão a nível do middleware.
      4. O controlo de fluxo por créditos parece fazer bastante sentido ficar associado aos atores. E não existe a necessidade de mensagens de confirmação. Portanto, acho que no mínimo este deveria ser implementado.