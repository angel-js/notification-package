package com.notifications.async;

import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Orquesta los envíos asíncronos sobre un {@link Executor} configurable.
 *
 * <p>Esta clase sabe <em>cómo ejecutar</em> envíos fuera del hilo llamante, pero no sabe
 * <em>cómo enviar</em>: recibe la operación de envío como una función. Gracias a eso no
 * conoce el registry, ni los senders, ni los canales — solo la concurrencia.</p>
 *
 * <p>Es segura entre hilos porque no guarda estado mutable: únicamente el {@code Executor},
 * que es inmutable desde su punto de vista.</p>
 */
public final class AsyncDispatcher {

    private final Executor executor;

    /**
     * @param executor pool sobre el que correrán los envíos, no nulo
     */
    public AsyncDispatcher(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Ejecuta un envío en el {@code Executor} configurado.
     *
     * <p>El futuro se completa con un {@code Success} o un {@code Failure} igual que el
     * envío síncrono. Solo se completa <em>excepcionalmente</em> si el envío lanza, es
     * decir ante un error de programación (p. ej. no hay sender para ese tipo).</p>
     *
     * @param notification notificación a enviar
     * @param envio        operación de envío a ejecutar (típicamente {@code service::send})
     * @return futuro con el resultado del envío
     */
    public CompletableFuture<NotificationResult> sendAsync(
            Notification notification,
            Function<Notification, NotificationResult> envio) {

        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(envio, "envio");
        return CompletableFuture.supplyAsync(() -> envio.apply(notification), executor);
    }

    /**
     * Ejecuta varios envíos en paralelo y agrupa sus resultados.
     *
     * <p>Todos los envíos se lanzan a la vez sobre el {@code Executor}; el futuro devuelto
     * se completa cuando terminan todos. <strong>Los resultados conservan el orden de la
     * lista de entrada</strong>, no el de finalización, para que se puedan correlacionar
     * con las notificaciones enviadas.</p>
     *
     * <p>Si algún envío lanza (p. ej. un canal sin sender registrado), el futuro agrupado
     * se completa excepcionalmente: un error de configuración debe verse, no perderse
     * entre los resultados correctos.</p>
     *
     * @param notifications notificaciones a enviar
     * @param envio         operación de envío a ejecutar sobre cada una
     * @return futuro con la lista de resultados, en el mismo orden que la entrada
     */
    public CompletableFuture<List<NotificationResult>> sendBatch(
            List<? extends Notification> notifications,
            Function<Notification, NotificationResult> envio) {

        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(envio, "envio");

        List<CompletableFuture<NotificationResult>> futuros = notifications.stream()
                .map(notification -> sendAsync(notification, envio))
                .toList();

        // allOf espera a que terminen todos; el join() posterior no bloquea de verdad
        // porque para entonces cada futuro ya está completo.
        return CompletableFuture
                .allOf(futuros.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futuros.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }
}
