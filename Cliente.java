import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cliente de terminal com recepção independente da digitação. */
public final class Cliente {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length > 2) {
            System.err.println("Uso: java Cliente [servidor] [porta]");
            return;
        }
        String endereco = args.length > 0 ? args[0] : "localhost";
        try {
            int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
            if (porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("Porta deve estar entre 1 e 65535.");
            }
            jogar(endereco, porta);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Falha na conexão: " + e.getMessage());
        }
    }

    // Fechar o socket na thread de teclado é intencional para liberar o receptor.
    @SuppressWarnings("try")
    private static void jogar(String endereco, int porta) throws IOException {
        AtomicBoolean encerrado = new AtomicBoolean(false);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endereco, porta), 5000);
            try (BufferedReader entrada = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter saida = new PrintWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                // A thread de teclado é daemon: uma leitura de System.in não mantém
                // o processo aberto depois que a thread receptora anuncia o vencedor.
                Thread teclado = new Thread(() -> {
                    BufferedReader console = new BufferedReader(new InputStreamReader(
                            System.in, StandardCharsets.UTF_8));
                    try {
                        String linha;
                        while (!encerrado.get() && (linha = console.readLine()) != null) {
                            if (encerrado.get()) {
                                return;
                            }
                            saida.println(linha);
                            if (saida.checkError() || linha.trim().equalsIgnoreCase("SAIR")) {
                                socket.shutdownOutput();
                                return;
                            }
                        }
                        if (!encerrado.get()) {
                            socket.shutdownOutput();
                        }
                    } catch (IOException e) {
                        if (!encerrado.get()) {
                            System.err.println("Entrada encerrada: " + e.getMessage());
                            try {
                                socket.close();
                            } catch (IOException ignorada) {
                                // A recepção também será liberada pelo fechamento.
                            }
                        }
                    }
                }, "teclado");
                teclado.setDaemon(true);

                Thread receptor = new Thread(() -> {
                    try {
                        String mensagem;
                        while ((mensagem = entrada.readLine()) != null) {
                            System.out.println(mensagem);
                            if (mensagem.startsWith("VENCEDOR ")) {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Recepção interrompida: " + e.getMessage());
                    } finally {
                        encerrado.set(true);
                        System.out.println("Conexão encerrada.");
                    }
                }, "receptor");

                System.out.println("Conectado. Digite um número de 0 a 1000 ou SAIR.");
                receptor.start();
                teclado.start();
                try {
                    receptor.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    encerrado.set(true);
                }
            }
        }
    }
}
