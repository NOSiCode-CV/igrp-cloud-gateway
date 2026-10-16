package cv.igrp.platform.igrpcloudgateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP server standing in for a proxied service; records what the gateway sent it.
 */
public final class RecordingBackend {

    private final HttpServer server;
    private final AtomicReference<Headers> lastHeaders = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();

    private RecordingBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            lastHeaders.set(exchange.getRequestHeaders());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    public static RecordingBackend start() {
        try {
            return new RecordingBackend();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Registers two routes to this server (indexed properties are bound from a single property
     * source, so all routes are registered here):
     * <ul>
     *     <li>{@code /backend/**} — forwarded as is;</li>
     *     <li>{@code /prefixed/**} — mirrors the production discovery routes: sets
     *     {@code X-Forwarded-Prefix: /prefixed} and strips the prefix from the path.</li>
     * </ul>
     */
    public void registerRoute(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + server.getAddress().getPort();
        String plain = "spring.cloud.gateway.server.webflux.routes[0].";
        registry.add(plain + "id", () -> "backend");
        registry.add(plain + "uri", () -> uri);
        registry.add(plain + "predicates[0]", () -> "Path=/backend/**");
        String prefixed = "spring.cloud.gateway.server.webflux.routes[1].";
        registry.add(prefixed + "id", () -> "prefixed");
        registry.add(prefixed + "uri", () -> uri);
        registry.add(prefixed + "predicates[0]", () -> "Path=/prefixed/**");
        registry.add(prefixed + "filters[0]", () -> "SetRequestHeader=X-Forwarded-Prefix, /prefixed");
        registry.add(prefixed + "filters[1]", () -> "StripPrefix=1");
    }

    public String lastHeader(String name) {
        Headers headers = lastHeaders.get();
        return headers == null ? null : headers.getFirst(name);
    }

    public int hits() {
        return hits.get();
    }

    public void reset() {
        lastHeaders.set(null);
        hits.set(0);
    }

    public void stop() {
        server.stop(0);
    }
}
