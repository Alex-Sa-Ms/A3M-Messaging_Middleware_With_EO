# Problemas

- Enviar/receber mensagens sem ter nenhum link estabelecido. Quando é que estas operações devem resultar numa exceção? Era fácil definir isto se existisse noção de peça fixa ou efémera.
	- Definir flag do socket através de *setOption()*?
	- Ao enviar uma mensagem sem invocar link() alguma vez, assumir que é uma peça fixa?
	- Definir flag ao enviar ou receber que permite ignorar a exceção?