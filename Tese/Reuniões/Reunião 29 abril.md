### Shutdown Exon
**Perguntas:**
- Permitir enviar mensagens enquanto estiver no estado "CLOSING"?
- Adicionar um intervalo de cooldown após se verificar que já não existem registos nem mensagens é uma possibilidade que permite aguardar por duplicados e garantir que os registos opostos são eliminados dos nodos com que houve comunicação.  
	- Não permite reiniciar a instância de forma rápida (para reinicio rápido era necessário persistir o estado, mais em concreto, o clock)
	- Para permitir fechos rápidos, seria necessário implementar mecanismos de administração para eliminar registos. No entanto, para eliminar os registos de forma segura, seria necessário esperar um tempo de cooldown para cada registo, pelas mesmas razões expressas acima. Estes mecanismos de administração poderiam ser invocados como consequência de por exemplo uma mensagem enviada pela camada acima a prometer que não iria enviar mais mensagens.