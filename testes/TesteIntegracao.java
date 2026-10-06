import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Testes com sockets TCP reais, sem bibliotecas externas. */
public final class TesteIntegracao {
    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    private static final class Partida implements AutoCloseable {
        final Servidor servidor;
        final Thread thread;
        final AtomicReference<Throwable> falha = new AtomicReference<>();

        Partida(int segredo) throws IOException {
            servidor = new Servidor(0, segredo);
            thread = new Thread(() -> {
                try {
                    servidor.executar();
                } catch (Throwable e) {
                    falha.set(e);
                }
            }, "servidor-teste");
            thread.start();
        }

        @Override
        public void close() throws IOException {
            servidor.close();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Espera pelo servidor interrompida", e);
            }
            verificar(!thread.isAlive(), "Servidor não encerrou");
            verificar(falha.get() == null, "Falha no servidor: " + falha.get());
        }
    }

    private static final class Conexao implements AutoCloseable {
        final Socket socket;
        final BufferedReader entrada;
        final PrintWriter saida;

        Conexao(int porta) throws IOException {
            socket = new Socket("127.0.0.1", porta);
            socket.setSoTimeout(5000);
            entrada = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void boasVindas() throws IOException {
            String linha = entrada.readLine();
            verificar(linha != null && linha.startsWith("BEM_VINDO "), "Boas-vindas: " + linha);
        }

        void esperar(String esperado) throws IOException {
            String recebido = entrada.readLine();
            verificar(esperado.equals(recebido), "Esperado " + esperado + ", recebido " + recebido);
        }

        List<String> lerAteFim() throws IOException {
            List<String> linhas = new ArrayList<>();
            String linha;
            while ((linha = entrada.readLine()) != null) {
                linhas.add(linha);
            }
            return linhas;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static Thread palpiteSimultaneo(Conexao cliente, int segredo, CountDownLatch inicio) {
        Thread thread = new Thread(() -> {
            try {
                inicio.await();
                cliente.saida.println(segredo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();
        return thread;
    }

    private static void testarProtocolo(int segredo) throws Exception {
        try (Partida partida = new Partida(segredo);
             Conexao a = new Conexao(partida.servidor.getPorta());
             Conexao b = new Conexao(partida.servidor.getPorta());
             Conexao parado = new Conexao(partida.servidor.getPorta())) {
            a.boasVindas();
            b.boasVindas();
            parado.boasVindas();
            for (String invalido : new String[]{"abc", "", "-1", "1001", "2.5", "9999999999999999"}) {
                a.saida.println(invalido);
                a.esperar("ERRO Digite um inteiro entre 0 e 1000");
            }
            if (segredo > 0) {
                a.saida.println(0);
                a.esperar("MAIOR");
            }
            if (segredo < 1000) {
                b.saida.println(1000);
                b.esperar("MENOR");
            }
            try (Conexao sai = new Conexao(partida.servidor.getPorta())) {
                sai.boasVindas();
                sai.saida.println(" sair ");
                sai.esperar("ATE_LOGO");
                verificar(sai.entrada.readLine() == null, "SAIR deve fechar só este cliente");
            }
            try (Conexao desconecta = new Conexao(partida.servidor.getPorta())) {
                desconecta.boasVindas();
            }
            // Após uma desconexão abrupta, outra conexão deve continuar sendo aceita.
            try (Conexao substituto = new Conexao(partida.servidor.getPorta())) {
                substituto.boasVindas();
                substituto.saida.println("SAIR");
                substituto.esperar("ATE_LOGO");
            }

            CountDownLatch inicio = new CountDownLatch(1);
            Thread tentativaA = palpiteSimultaneo(a, segredo, inicio);
            Thread tentativaB = palpiteSimultaneo(b, segredo, inicio);
            inicio.countDown();
            tentativaA.join(5000);
            tentativaB.join(5000);
            verificar(!tentativaA.isAlive() && !tentativaB.isAlive(), "Envio simultâneo travou");
            List<String> linhasA = a.lerAteFim();
            List<String> linhasB = b.lerAteFim();
            List<String> linhasParado = parado.lerAteFim();
            long acertos = linhasA.stream().filter("ACERTOU"::equals).count()
                    + linhasB.stream().filter("ACERTOU"::equals).count();
            verificar(acertos == 1, "Deve existir exatamente um ACERTOU: " + acertos);
            verificar(linhasParado.size() == 1, "Cliente parado deve receber apenas o resultado");
            String resultado = linhasParado.get(0);
            verificar(resultado.startsWith("VENCEDOR IP=127.0.0.1 HOST="), "IP/hostname ausentes");
            verificar(resultado.endsWith(" NUMERO=" + segredo), "Número incorreto");
            verificar(linhasA.get(linhasA.size() - 1).equals(resultado), "Resultado divergente em A");
            verificar(linhasB.get(linhasB.size() - 1).equals(resultado), "Resultado divergente em B");
            int portaVencedor = linhasA.contains("ACERTOU") ? a.socket.getLocalPort() : b.socket.getLocalPort();
            verificar(resultado.contains(" PORTA=" + portaVencedor + " "), "Identidade do vencedor incorreta");
            try (Conexao tardio = new Conexao(partida.servidor.getPorta())) {
                tardio.esperar(resultado);
                verificar(tardio.entrada.readLine() == null, "Conexão tardia deve encerrar");
            }
            System.out.println("PASSOU: 3 clientes, validação, SAIR, desconexão, acerto simultâneo"
                    + ", anúncio ao cliente parado e conexão tardia; segredo=" + segredo);
        }
    }

    private static final class ClienteProcesso implements AutoCloseable {
        final Process processo;
        final BlockingQueue<String> linhas = new LinkedBlockingQueue<>();
        final PrintWriter teclado;

        ClienteProcesso(int porta) throws IOException {
            String executavel = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            processo = new ProcessBuilder(executavel, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                    "Cliente", "127.0.0.1", Integer.toString(porta)).redirectErrorStream(true).start();
            teclado = new PrintWriter(new OutputStreamWriter(
                    processo.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread leitor = new Thread(() -> {
                try (BufferedReader entrada = new BufferedReader(new InputStreamReader(
                        processo.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = entrada.readLine()) != null) {
                        linhas.add(linha);
                    }
                } catch (IOException e) {
                    linhas.add("FALHA " + e.getMessage());
                }
            });
            leitor.setDaemon(true);
            leitor.start();
        }

        String aguardar(String prefixo) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < limite) {
                String linha = linhas.poll(limite - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (linha != null) {
                    System.out.println("Cliente processo: " + linha);
                    if (linha.startsWith(prefixo)) {
                        return linha;
                    }
                }
            }
            throw new AssertionError("Cliente não exibiu " + prefixo);
        }

        @Override
        public void close() {
            processo.destroyForcibly();
            teclado.close();
        }
    }

    private static void testarClientesReais() throws Exception {
        try (Partida partida = new Partida(637);
             ClienteProcesso a = new ClienteProcesso(partida.servidor.getPorta());
             ClienteProcesso b = new ClienteProcesso(partida.servidor.getPorta());
             ClienteProcesso parado = new ClienteProcesso(partida.servidor.getPorta())) {
            a.aguardar("BEM_VINDO");
            b.aguardar("BEM_VINDO");
            parado.aguardar("BEM_VINDO");
            a.teclado.println(636);
            a.aguardar("MAIOR");
            b.teclado.println(638);
            b.aguardar("MENOR");
            a.teclado.println(637);
            a.aguardar("ACERTOU");
            String resultado = a.aguardar("VENCEDOR");
            verificar(resultado.equals(b.aguardar("VENCEDOR")), "Anúncio diferente em B");
            verificar(resultado.equals(parado.aguardar("VENCEDOR")), "Anúncio diferente no cliente parado");
            // Nenhum stdin é fechado e nenhum ENTER é enviado ao cliente parado.
            for (ClienteProcesso cliente : new ClienteProcesso[]{a, b, parado}) {
                verificar(cliente.processo.waitFor(5, TimeUnit.SECONDS), "Cliente bloqueado no teclado");
                verificar(cliente.processo.exitValue() == 0, "Cliente terminou com erro");
            }
            System.out.println("PASSOU: 3 processos Cliente reais; anúncio e saída sem ENTER no cliente parado");
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        testarProtocolo(0);
        testarProtocolo(1000);
        for (int rodada = 0; rodada < 10; rodada++) {
            testarProtocolo(500);
        }
        testarClientesReais();
        System.out.println("TODOS OS TESTES PASSARAM (13 partidas TCP).");
    }
}
