package io.corebilling.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** A webhook receiver on loopback that records what it is sent and answers with a scripted status. */
public final class TestReceiver implements AutoCloseable {

    public record Received(Map<String, String> headers, String body) {
        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private final HttpServer server;
    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Integer> script = new ConcurrentLinkedQueue<>();
    private volatile int defaultStatus = 200;
    private final AtomicInteger inFlight = new AtomicInteger();

    public TestReceiver() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/hook", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        inFlight.incrementAndGet();
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> e.getValue().get(0), (a, b) -> a));
            requests.add(new Received(headers, body));
            Integer status = script.poll();
            exchange.sendResponseHeaders(status != null ? status : defaultStatus, -1);
        } finally {
            exchange.close();
            inFlight.decrementAndGet();
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    /** Answers the next requests with these statuses, then with the default. */
    public TestReceiver script(Integer... statuses) {
        script.addAll(List.of(statuses));
        return this;
    }

    public TestReceiver answerAlways(int status) {
        this.defaultStatus = status;
        return this;
    }

    public List<Received> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
