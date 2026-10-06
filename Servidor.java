import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Uma partida compartilhada por todos os clientes TCP. */
public final class Servidor implements AutoCloseable {
    private final ServerSocket escuta;
    private final int numeroSecreto;
    private final Object trava = new Object();
    // Todos os acessos ao conjunto e ao estado da partida usam a mesma trava.
    private final Set<Jogador> jogadores = new HashSet<>();
    private boolean ativo = true;
    private String resultado;

    public Servidor(int porta) throws IOException {
        this(porta, ThreadLocalRandom.current().nextInt(1001));
    }

    // Somente os testes usam um segredo conhecido; a execução normal sempre sorteia.
    Servidor(int porta, int segredo) throws IOException {
        if (segredo < 0 || segredo > 1000) {
            throw new IllegalArgumentException("Número secreto fora do intervalo.");
        }
        numeroSecreto = segredo;
        escuta = new ServerSocket(porta);
    }

    public int getPorta() {
        return escuta.getLocalPort();
    }

    public void executar() throws IOException {
        System.out.println("Servidor TCP na porta " + getPorta());
        while (!escuta.isClosed()) {
            try {
                Socket socket = escuta.accept();
                new Thread(new Jogador(socket), "jogador-" + socket.getPort()).start();
            } catch (SocketException e) {
                if (!escuta.isClosed()) {
                    throw e;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (trava) {
            ativo = false;
            for (Jogador jogador : jogadores) {
                jogador.fechar();
            }
            escuta.close();
        }
    }

    private final class Jogador implements Runnable {
        private final Socket socket;
        private BufferedWriter saida;
        private String ip;
        private String hostname;

        Jogador(Socket socket) {
            this.socket = socket;
        }

        // Chamado sob a trava da partida: linhas de threads diferentes não se misturam.
        private void enviar(String mensagem) throws IOException {
            saida.write(mensagem);
            saida.newLine();
            saida.flush();
        }

        private void enviarSemInterromper(String mensagem) {
            try {
                enviar(mensagem);
            } catch (IOException e) {
                fechar();
            }
        }

        private void fechar() {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Não foi possível fechar uma conexão: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try (Socket conexao = socket;
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(
                         conexao.getInputStream(), StandardCharsets.UTF_8))) {
                saida = new BufferedWriter(new OutputStreamWriter(
                        conexao.getOutputStream(), StandardCharsets.UTF_8));
                ip = conexao.getInetAddress().getHostAddress();
                hostname = conexao.getInetAddress().getHostName();
                synchronized (trava) {
                    if (!ativo) {
                        return;
                    }
                    if (resultado != null) {
                        enviar(resultado);
                        return;
                    }
                    jogadores.add(this);
                    enviar("BEM_VINDO Digite um inteiro de 0 a 1000 ou SAIR");
                }

                String linha;
                while ((linha = entrada.readLine()) != null) {
                    synchronized (trava) {
                        if (!ativo || resultado != null) {
                            return;
                        }
                        String texto = linha.trim();
                        if (texto.equalsIgnoreCase("SAIR")) {
                            enviar("ATE_LOGO");
                            return;
                        }
                        int palpite;
                        try {
                            palpite = Integer.parseInt(texto);
                            if (palpite < 0 || palpite > 1000) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException e) {
                            enviar("ERRO Digite um inteiro entre 0 e 1000");
                            continue;
                        }

                        if (palpite < numeroSecreto) {
                            enviar("MAIOR");
                        } else if (palpite > numeroSecreto) {
                            enviar("MENOR");
                        } else {
                            // A definição e o anúncio são atômicos em relação aos palpites
                            // e à entrada de jogadores: apenas esta thread pode vencer.
                            resultado = "VENCEDOR IP=" + ip + " HOST=" + hostname
                                    + " PORTA=" + socket.getPort() + " NUMERO=" + numeroSecreto;
                            enviarSemInterromper("ACERTOU");
                            for (Jogador jogador : jogadores) {
                                jogador.enviarSemInterromper(resultado);
                            }
                            System.out.println(resultado);
                            // Libera inclusive as threads paradas em readLine().
                            for (Jogador jogador : jogadores) {
                                jogador.fechar();
                            }
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão encerrada: " + socket.getRemoteSocketAddress());
            } finally {
                synchronized (trava) {
                    jogadores.remove(this);
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Uso: java Servidor [porta]");
            return;
        }
        try {
            int porta = args.length == 1 ? Integer.parseInt(args[0]) : 5000;
            if (porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("Porta deve estar entre 1 e 65535.");
            }
            try (Servidor servidor = new Servidor(porta)) {
                servidor.executar();
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Não foi possível iniciar o servidor: " + e.getMessage());
        }
    }
}
