# Jogo de adivinhação — Redes de Computadores II

Aplicação cliente-servidor em Java, com sockets TCP, interface de terminal e uma thread por conexão. Todos os jogadores disputam o mesmo número secreto, sorteado entre **0 e 1000**, inclusive.

## Integrantes

- Pedro Macedo Paronetto Faria Viana
- Otávio Oliveira Neto
- Guilherme Maia Facchini

## Execução com Docker (avaliação prática)

O servidor roda em um contêiner Docker; o cliente roda fora dele e se conecta pela porta TCP publicada. Funciona no Linux ou no Windows com WSL 2 e Docker (comandos abaixo em um terminal Bash do Ubuntu/WSL ou Linux, na raiz do projeto).

### Dockerfile

O [Dockerfile](Dockerfile) fica na raiz do projeto e usa dois estágios:

| Instrução | Função |
| --- | --- |
| `FROM eclipse-temurin:17-jdk AS build` | Imagem base Java 17 com compilador (estágio de build) |
| `COPY Servidor.java .` | Copia o código-fonte do servidor para o contêiner |
| `RUN javac ... -d out Servidor.java` | Compila a aplicação dentro da imagem |
| `FROM eclipse-temurin:17-jre` | Imagem final só com o runtime Java, menor |
| `COPY --from=build /app/out ./out` | Copia as classes compiladas do estágio de build |
| `EXPOSE 5000` | Documenta a porta TCP usada pelo servidor |
| `ENTRYPOINT ["java", "-cp", "out", "Servidor"]` + `CMD ["5000"]` | Inicia o servidor automaticamente na porta 5000 |

O `.dockerignore` evita enviar para o build arquivos desnecessários (`out/`, testes, `.git`, capturas).

### 1. Construir a imagem

```bash
docker build -t jogo-adivinhacao-grupo-pedro-otavio-guilherme:1.0 .
```

- `-t` dá nome e versão (tag) à imagem, identificando o jogo e o grupo.
- `.` indica que o contexto de build (e o Dockerfile) é o diretório atual.

Conferir a imagem criada:

```bash
docker images jogo-adivinhacao-grupo-pedro-otavio-guilherme
```

### 2. Executar o servidor no contêiner

```bash
docker run -d --name jogo-adivinhacao-servidor -p 5000:5000 jogo-adivinhacao-grupo-pedro-otavio-guilherme:1.0
```

- `-d` executa em segundo plano.
- `--name` dá um nome ao contêiner.
- `-p 5000:5000` publica a porta 5000 do contêiner na porta 5000 da máquina hospedeira.

Conferir se está em execução e ver a saída do servidor:

```bash
docker ps
docker logs -f jogo-adivinhacao-servidor
```

O log deve mostrar `Servidor TCP na porta 5000`. Ao fim da partida, mostra a linha `VENCEDOR ...`.

### 3. Executar o cliente (fora do contêiner)

Com o JDK 17 instalado na máquina hospedeira:

```bash
javac -encoding UTF-8 --release 17 -d out Cliente.java
java -cp out Cliente localhost 5000
```

Abra quantos terminais quiser com clientes. Para jogar a partir de outra máquina, troque `localhost` pelo IP da máquina que roda o Docker.

Sem JDK na máquina hospedeira, o cliente também pode rodar em um contêiner separado, conectando ao servidor pela porta publicada no host:

```bash
docker run --rm -it -v "$PWD":/src -w /src eclipse-temurin:17-jdk \
  sh -c "javac -d /tmp/out Cliente.java && java -cp /tmp/out Cliente host.docker.internal 5000"
```

(No Linux sem Docker Desktop, acrescente `--add-host=host.docker.internal:host-gateway`.)

Como o Docker faz NAT da porta publicada, o IP do vencedor mostrado pelo servidor é o do gateway da rede Docker (por exemplo, `172.17.0.1`), e não o IP real do cliente.

### 4. Nova partida e encerramento

O servidor sorteia o número ao iniciar. Para uma nova partida, reinicie o contêiner:

```bash
docker restart jogo-adivinhacao-servidor
```

Para parar e remover o contêiner (e, se quiser, a imagem):

```bash
docker stop jogo-adivinhacao-servidor
docker rm jogo-adivinhacao-servidor
docker rmi jogo-adivinhacao-grupo-pedro-otavio-guilherme:1.0
```

### Exemplo de execução verificada

Partida jogada contra o servidor no contêiner, com palpites por busca binária:

```text
<- BEM_VINDO Digite um inteiro de 0 a 1000 ou SAIR
-> 500  <- MAIOR
-> 750  <- MENOR
-> 625  <- MAIOR
-> 687  <- MENOR
-> 656  <- MAIOR
-> 671  <- MAIOR
-> 679  <- MAIOR
-> 683  <- MENOR
-> 681  <- ACERTOU
<- VENCEDOR IP=172.17.0.1 HOST=172.17.0.1 PORTA=38130 NUMERO=681
```

`docker logs jogo-adivinhacao-servidor`:

```text
Servidor TCP na porta 5000
VENCEDOR IP=172.17.0.1 HOST=172.17.0.1 PORTA=38130 NUMERO=681
```

### Capturas de tela

As capturas ficam na pasta [capturas/](capturas/):

1. [01-docker-build-e-images.png](capturas/01-docker-build-e-images.png): `docker build` concluído (com o `javac` rodando na imagem) e `docker images` mostrando a imagem.
2. [02-docker-run-e-ps.png](capturas/02-docker-run-e-ps.png): `docker run` e `docker ps` com o contêiner em execução e a porta `0.0.0.0:5000->5000/tcp`.
3. [03-cliente-jogando.png](capturas/03-cliente-jogando.png): `Cliente` Java fora do contêiner do servidor, enviando palpites e recebendo `MAIOR`/`MENOR` até `ACERTOU` e o anúncio `VENCEDOR`. Os palpites foram digitados por um programa auxiliar que faz busca binária.
4. [04-docker-logs-servidor.png](capturas/04-docker-logs-servidor.png): `docker logs` do servidor com o vencedor (duas partidas, pois o contêiner foi reiniciado entre elas).

## Execução sem Docker

### Requisitos e compilação

Instale um **JDK 17 ou superior**, com `java` e `javac` no PATH. Não são necessárias bibliotecas externas, Maven ou Gradle.

No diretório do projeto:

```powershell
javac -encoding UTF-8 --release 17 -d out Servidor.java Cliente.java
```

### Execução

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
- `Dockerfile` e `.dockerignore`: imagem Docker do servidor.
- `capturas/`: capturas de tela da execução no Docker.
- `README.md`: integrantes e instruções.

Entrega pelo repositório GitHub: [JogoDeAdivinhacao-Socket-Redes02](https://github.com/PedroVian9/JogoDeAdivinhacao-Socket-Redes02). O repositório deve estar acessível ao professor. Os arquivos compilados em `out` não são necessários na entrega.

Referência da atividade: [rakratz/redes2](https://github.com/rakratz/redes2). A implementação deste projeto foi escrita para atender ao enunciado e às orientações da referência.
