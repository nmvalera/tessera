package com.quorum.tessera.sync;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.partyinfo.PayloadPublisher;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClientEndpoint(encoders = {SyncRequestMessageCodec.class})
public class WebSocketPayloadPublisher implements PayloadPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketPayloadPublisher.class);

    private final Map<String, Session> cache = new ConcurrentHashMap<>();

    private final java.util.Queue<String> responseQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void publishPayload(EncodedPayload payload, String targetUrl) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        LOGGER.info("Publish payload {} to target url {}", payload, targetUrl);

        URI uri = UriBuilder.fromUri(URI.create(targetUrl)).path("sync").build();

        final Session session =
                cache.computeIfAbsent(
                        targetUrl, k -> WebSocketSessionCallback.execute(() -> container.connectToServer(this, uri)));

        final SyncRequestMessage syncRequestMessage =
                SyncRequestMessage.Builder.create(SyncRequestMessage.Type.TRANSACTION_PUSH)
                        .withTransactions(payload)
                        .withCorrelationId(UUID.randomUUID().toString())
                        .build();

        WebSocketSessionCallback.execute(
                () -> {
                    LOGGER.debug("Sending {} ", syncRequestMessage);

                    CompletableFuture<Void> responseHandler =
                            CompletableFuture.runAsync(
                                    new TransactionPushAckListener(
                                            responseQueue, syncRequestMessage.getCorrelationId()));

                    session.getBasicRemote().sendObject(syncRequestMessage);

                    try {
                        ExecutorCallback.execute(() -> responseHandler.get(30, TimeUnit.SECONDS));
                    } finally {
                        responseQueue.remove(syncRequestMessage.getCorrelationId());
                    }
                    LOGGER.debug("Sent {} ", syncRequestMessage);
                    return null;
                });
    }

    @OnMessage
    public void onResponse(Session session, String correlationId) {
        LOGGER.info("Process response from {} {}", session.getRequestURI(), correlationId);
        responseQueue.offer(correlationId);
    }

    @PreDestroy
    public void clearSessions() {
        cache.values()
                .forEach(
                        s -> {
                            CloseReason reason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "");
                            WebSocketSessionCallback.execute(
                                    () -> {
                                        s.close(reason);
                                        return null;
                                    });
                        });
        cache.clear();
    }

    @OnError
    public void onError(Throwable ex) {
        LOGGER.error(null, ex);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        LOGGER.debug("Session has been closed. Reason {}", reason.getReasonPhrase());
        cache.values().removeIf(value -> value == session);
    }
}
