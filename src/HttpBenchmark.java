import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class HttpBenchmark {

    static final int BATCH_SIZE = 128;
    static HttpServer server;

    // ======================= SERVER =======================
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/test", exchange ->
                Thread.startVirtualThread(() -> handle(exchange)) // MOD
        );
        server.setExecutor(null); // сервер обслуживает пулом из 50 потоков
        server.start();
        System.out.println("Server started on http://localhost:8080/test");
    }

    static void handle(HttpExchange exchange) {
        try {
            // имитация «работы», можно добавить вычисления вместо sleep
            String body = "ok";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            exchange.close(); // MOD: обязательно закрываем exchange
        }
    }

    static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Thread.sleep(50); // имитация «долгого» ответа
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "ok".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    // ======================= CLIENT BENCH =======================
    static Duration runClient(int numRequests, ExecutorService executor) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:8080/test");

        Instant start = Instant.now();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numRequests; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, numRequests);
            for (int j = i; j < end; j++) {
                futures.add(executor.submit(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(uri).build();
                        client.send(req, HttpResponse.BodyHandlers.ofString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
        }
        return Duration.between(start, Instant.now());
    }

    // ======================= MAIN =======================
    public static void main(String[] args) throws Exception {
        startServer();

        int numRequests = 5_000;

        // Virtual Threads
        try (ExecutorService vexec = Executors.newVirtualThreadPerTaskExecutor()) {
            Duration d = runClient(numRequests, vexec);
            System.out.printf("Virtual threads: %d requests in %d ms%n",
                    numRequests, d.toMillis());
        }

        // Classic Threads (e.g. 200)
        try (ExecutorService cexec = Executors.newFixedThreadPool(16)) {
            Duration d = runClient(numRequests, cexec);
            System.out.printf("Classic threads (16): %d requests in %d ms%n",
                    numRequests, d.toMillis());
        }

        // Classic Threads (10_000)
        ThreadFactory smallStack = r -> new Thread(null, r, "classic-smallstack", 128 * 1024);
        try (ExecutorService cexec = Executors.newFixedThreadPool(64, smallStack)) {
            Duration d = runClient(numRequests, cexec);
            System.out.printf("Classic threads (64): %d requests in %d ms%n",
                    numRequests, d.toMillis());
        }

        server.stop(100);
    }
}
