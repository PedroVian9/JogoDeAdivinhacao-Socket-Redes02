# Jogo de Adivinhação — servidor TCP em contêiner
# Estágio 1: compila o código-fonte com o JDK 17
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY Servidor.java .
RUN javac -encoding UTF-8 --release 17 -d out Servidor.java

# Estágio 2: executa apenas as classes compiladas com o JRE 17 (imagem menor)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out

# Porta TCP usada pelo servidor do jogo
EXPOSE 5000

# Inicia o servidor automaticamente quando o contêiner sobe
ENTRYPOINT ["java", "-cp", "out", "Servidor"]
CMD ["5000"]
