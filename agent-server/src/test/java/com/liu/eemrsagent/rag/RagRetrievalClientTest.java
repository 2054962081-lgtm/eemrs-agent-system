package com.liu.eemrsagent.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsChunksForSuccessfulResponse() throws Exception {
        startServer(0, """
                {"success":true,"query":"q","chunks":[{"chunk_id":"c1","doc_id":"d1","doc_type":"red_flag","title":"胸痛红旗","urgency_level":"急诊","related_departments":"急诊科","score":0.9,"chunk_text":"胸痛伴大汗"}],"error_message":null}
                """);

        List<RagChunk> chunks = new RagRetrievalClient(properties(1000)).retrieve("胸痛", RagRetrievalClient.SCENE_PRE_INQUIRY);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkId()).isEqualTo("c1");
    }

    @Test
    void successFalseFallsBackToEmptyChunks() throws Exception {
        startServer(0, """
                {"success":false,"query":"q","chunks":[],"error_message":"collection missing"}
                """);

        List<RagChunk> chunks = new RagRetrievalClient(properties(1000)).retrieve("胸痛", RagRetrievalClient.SCENE_PRE_INQUIRY);

        assertThat(chunks).isEmpty();
    }

    @Test
    void emptyChunksAreReturnedAsEmptyList() throws Exception {
        startServer(0, """
                {"success":true,"query":"q","chunks":[],"error_message":null}
                """);

        List<RagChunk> chunks = new RagRetrievalClient(properties(1000)).retrieve("胸痛", RagRetrievalClient.SCENE_PRE_INQUIRY);

        assertThat(chunks).isEmpty();
    }

    @Test
    void timeoutFallsBackToEmptyChunks() throws Exception {
        startServer(200, """
                {"success":true,"query":"q","chunks":[],"error_message":null}
                """);

        List<RagChunk> chunks = new RagRetrievalClient(properties(20)).retrieve("胸痛", RagRetrievalClient.SCENE_PRE_INQUIRY);

        assertThat(chunks).isEmpty();
    }

    private RagProperties properties(int timeoutMs) {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setFailOpen(true);
        properties.setDebugLog(false);
        properties.setServiceUrl("http://localhost:" + server.getAddress().getPort());
        properties.setRetrievePath("/rag/retrieve");
        properties.setTimeoutMs(timeoutMs);
        properties.setTopK(8);
        return properties;
    }

    private void startServer(long delayMs, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rag/retrieve", exchange -> {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }
}
