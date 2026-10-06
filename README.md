# Jogo de adivinhação — Redes de Computadores II

Aplicação cliente-servidor em Java, com sockets TCP, interface de terminal e uma thread por conexão. Todos os jogadores disputam o mesmo número secreto, sorteado entre **0 e 1000**, inclusive.

## Integrantes

- Pedro Macedo Paronetto Faria Viana
- Otávio Oliveira Neto
- Guilherme Maia Facchini

## Requisitos e compilação

Instale um **JDK 17 ou superior**, com `java` e `javac` no PATH. Não são necessárias bibliotecas externas, Maven ou Gradle.

No diretório do projeto:

```powershell
javac -encoding UTF-8 --release 17 -d out Servidor.java Cliente.java
```

## Execução

Inicie o servidor em um terminal:

```powershell
java -cp out Servidor
```

Abra **pelo menos três outros terminais** e execute, em cada um:

```powershell
java -cp out Cliente localhost
```

Digite um inteiro de 0 a 1000 e pressione ENTER para enviar o palpite. O servidor responde `MAIOR`, `MENOR` ou `ACERTOU`. Digite `SAIR` para sair individualmente.

Em máquinas diferentes, substitua `localhost` pelo IP da máquina do servidor:

```powershell
java -cp out Cliente 192.168.1.10
```

A porta TCP padrão é **5000** e deve estar acessível entre as máquinas. Uma porta alternativa pode ser informada aos dois programas:

```powershell
java -cp out Servidor 6000
java -cp out Cliente localhost 6000
```

O primeiro acerto encerra a partida e fecha as conexões dos participantes. O servidor continua ativo para informar o resultado a novas conexões. Para começar outra partida, pare o servidor com **Ctrl+C** e execute-o novamente. O número secreto não é exibido antes do fim da partida.

## Protocolo e funcionamento

As mensagens são linhas de texto em UTF-8.

| Mensagem | Significado |
| --- | --- |
| `BEM_VINDO ...` | Conexão registrada na partida |
| `MAIOR` | O número secreto é maior que o palpite |
| `MENOR` | O número secreto é menor que o palpite |
| `ERRO ...` | Entrada não inteira ou fora de 0 a 1000; pode tentar novamente |
| `ACERTOU` | Confirmação exclusiva ao vencedor |
| `VENCEDOR IP=... HOST=... PORTA=... NUMERO=...` | Resultado enviado a todos, incluindo o vencedor |
| `ATE_LOGO` | Confirmação do comando `SAIR` |

O servidor cria uma thread para cada socket aceito. O registro dos jogadores, a comparação dos palpites, a definição do vencedor e o anúncio usam a mesma trava (`synchronized`). Assim, dois acertos simultâneos não podem definir vencedores diferentes, e uma nova conexão não pode perder o resultado durante o encerramento.

O cliente usa uma thread receptora independente da thread de teclado. O anúncio aparece mesmo com a digitação bloqueada. A thread de teclado é daemon; após receber o resultado, o programa termina sem exigir ENTER. Entradas inválidas recebem uma mensagem de erro; fim de fluxo e falhas de conexão encerram somente a participação afetada.

O IP vem de `getHostAddress()` e o hostname de `getHostName()`. Se não houver resolução de nome na rede, o hostname pode ser o próprio IP. A porta remota permite distinguir jogadores executados na mesma máquina.

## Testes automatizados

Os testes usam sockets reais em portas locais livres e segredos conhecidos apenas pela classe de teste. **A execução normal do servidor sempre usa sorteio aleatório.**

```powershell
javac -encoding UTF-8 --release 17 -d out Servidor.java Cliente.java testes/TesteIntegracao.java
java -cp out TesteIntegracao
```

A suíte verifica:

- Três clientes simultâneos, respostas `MAIOR` e `MENOR` e extremos 0 e 1000.
- Texto inválido, linha vazia, números fora do intervalo, decimal e overflow.
- `SAIR`, desconexão abrupta e aceitação de outros jogadores em seguida.
- Dois acertos simultâneos, exatamente um `ACERTOU` e resultado idêntico para todos.
- Anúncio a um cliente sem palpites e fechamento das conexões.
- Conexão após o fim recebendo o mesmo resultado.
- Três processos reais de `Cliente`, incluindo um parado no teclado, recebendo o anúncio e terminando sem ENTER.

O registro da execução está em [testes/resultado-testes.txt](testes/resultado-testes.txt). Os testes são locais; a demonstração em máquinas distintas depende da rede do laboratório.

## Roteiro de demonstração manual

1. Inicie o servidor e três clientes nos comandos acima.
2. Nos dois primeiros clientes, envie palpites diferentes e observe as respostas. No terceiro, não digite nada.
3. Envie `abc`, uma linha vazia, `-1` e `1001`: a conexão deve continuar ativa após os erros.
4. Em um cliente, envie `SAIR`. Abra outro cliente e continue jogando.
5. Use as dicas para chegar ao número correto. Observe `ACERTOU` no vencedor e o mesmo anúncio com IP, hostname e número em todos, inclusive no cliente parado.
6. Abra um cliente depois do fim: ele deve receber o resultado e encerrar.
7. Para verificar a disputa simultânea de forma reproduzível, execute a suíte automatizada.

Para a apresentação, capture os terminais do servidor e dos três clientes durante a demonstração.

## Arquivos e entrega

- `Servidor.java`: servidor e sincronização da partida.
- `Cliente.java`: conexão, envio pelo teclado e recepção independente.
- `testes/TesteIntegracao.java`: testes de integração sem dependências.
- `testes/resultado-testes.txt`: registro dos testes executados.
- `README.md`: integrantes e instruções.

Entrega pelo repositório GitHub: [JogoDeAdivinhacao-Socket-Redes02](https://github.com/PedroVian9/JogoDeAdivinhacao-Socket-Redes02). O repositório deve estar acessível ao professor. Os arquivos compilados em `out` não são necessários na entrega.

Referência da atividade: [rakratz/redes2](https://github.com/rakratz/redes2). A implementação deste projeto foi escrita para atender ao enunciado e às orientações da referência.
