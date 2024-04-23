# Concepção de administração e encrerramento gracioso

Este sistema não é um problema atual para o protótipo, até porque num ambiente de Exactly-Once delivery, assume-se que entidades anunciadas devem existir uma vez que podem ser necessárias por outros nodos.

**No máximo deve pensar-se em mecanismos de administração que permitem eliminar estados para que seja possível reiniciar certos componentes e retomar a comunicação sem problemas inerentes aos estados do Exon ou até mesmo de estados criados pelo Middleware.**

Se for implementada uma funcionalidade de cancelar o envio de mensagens, apenas mensagens que ainda estão em queue podem ser eliminadas. As que estão em token ja podem ter sido recebidas, ou podem estar prestes a ser recebidas.