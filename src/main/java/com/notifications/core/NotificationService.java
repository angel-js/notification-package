package com.notifications.core;

import com.notifications.async.AsyncDispatcher;
import com.notifications.core.strategy.SenderRegistry;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Fachada y único punto de entrada de la librería.
 *
 * <p>Se construye con su {@link Builder}, registrando un sender por canal. Después,
 * cada envío se despacha automáticamente al proveedor que corresponda según el tipo
 * de notificación: el llamador nunca elige el sender.</p>
 *
 * <pre>{@code
 * NotificationService service = NotificationService.builder()
 *         .register(new SendGridSender(sendGridConfig))   // canal email
 *         .register(new TwilioSender(twilioConfig))       // canal sms
 *         .register(new AndroidSender(androidConfig))     // canal push
 *         .build();
 *
 * // síncrono
 * NotificationResult result = service.send(
 *         new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com",
 *                               "Bienvenido", "Gracias por registrarte"));
 *
 * // asíncrono
 * CompletableFuture<NotificationResult> futuro = service.sendAsync(notification);
 *
 * // por lotes, en paralelo
 * CompletableFuture<List<NotificationResult>> lote = service.sendBatch(List.of(n1, n2, n3));
 * }</pre>
 *
 * <p>Esta clase no conoce ningún canal ni proveedor concreto: delega la resolución en
 * {@link SenderRegistry} y la concurrencia en {@link AsyncDispatcher}. Añadir un canal
 * nuevo no requiere modificarla (Open/Closed).</p>
 *
 * <p>Una vez construida es <strong>inmutable y segura entre hilos</strong>: los senders
 * quedan congelados en un mapa inmutable, por lo que varios hilos pueden enviar a la vez
 * sin sincronización alguna.</p>
 */
public class NotificationService {

    private final SenderRegistry registry;
    private final AsyncDispatcher asyncDispatcher;

    private NotificationService(SenderRegistry registry, AsyncDispatcher asyncDispatcher) {
        this.registry = registry;
        this.asyncDispatcher = asyncDispatcher;
    }

    /**
     * Envía una notificación por el canal que corresponda a su tipo.
     *
     * <p>Los fallos de envío se devuelven como {@code Failure} dentro del resultado,
     * no como excepción.</p>
     *
     * @param notification notificación a enviar, no nula
     * @return {@code Success} con el id del mensaje, o {@code Failure} con el motivo
     * @throws com.notifications.exception.NoSenderRegisteredException
     *         si no hay ningún sender registrado para ese tipo de notificación
     */
    public NotificationResult send(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        return registry.send(notification);
    }

    /**
     * Envía una notificación sin bloquear el hilo llamante.
     *
     * <p>El futuro se completa con {@code Success} o {@code Failure}, igual que
     * {@link #send(Notification)}. Solo se completa excepcionalmente ante un error de
     * programación, como enviar por un canal sin sender registrado.</p>
     *
     * @param notification notificación a enviar, no nula
     * @return futuro con el resultado del envío
     */
    public CompletableFuture<NotificationResult> sendAsync(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        return asyncDispatcher.sendAsync(notification, this::send);
    }

    /**
     * Envía varias notificaciones en paralelo, de cualquier canal mezclado.
     *
     * <p>Los resultados llegan <strong>en el mismo orden que la lista de entrada</strong>,
     * no en el de finalización, para poder correlacionarlos con lo enviado.</p>
     *
     * @param notifications notificaciones a enviar, no nula
     * @return futuro con la lista de resultados, en el orden de entrada
     */
    public CompletableFuture<List<NotificationResult>> sendBatch(
            List<? extends Notification> notifications) {
        Objects.requireNonNull(notifications, "notifications");
        return asyncDispatcher.sendBatch(notifications, this::send);
    }

    /** @return un builder para configurar y crear el servicio */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructor fluido del servicio. Registra un sender por cada canal que quieras
     * habilitar; el proveedor concreto se elige aquí, en tiempo de configuración.
     */
    public static final class Builder {

        private final List<NotificationSender<?>> senders = new ArrayList<>();
        private Executor executor = ForkJoinPool.commonPool();

        /**
         * Registra un sender. Solo puede haber uno por tipo de notificación: si
         * registras dos del mismo canal (p. ej. SendGrid y Mailgun), {@link #build()}
         * fallará para que el error salte al configurar y no en producción.
         *
         * @param sender sender a registrar, no nulo
         * @return este builder, para encadenar
         */
        public Builder register(NotificationSender<?> sender) {
            senders.add(Objects.requireNonNull(sender, "sender"));
            return this;
        }

        /**
         * Define el {@link Executor} sobre el que corren {@code sendAsync} y
         * {@code sendBatch}.
         *
         * <p>Por defecto se usa {@link ForkJoinPool#commonPool()}, para que la librería
         * no cree hilos propios ni obligue a nadie a apagarlos. Si tu aplicación ya tiene
         * su pool, pásalo aquí: los envíos son de E/S, así que un pool dedicado suele ser
         * mejor idea que el común.</p>
         *
         * @param executor pool a usar, no nulo
         * @return este builder, para encadenar
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * @return el servicio ya inmutable y listo para usar
         * @throws IllegalStateException si hay dos senders registrados para el mismo tipo
         */
        public NotificationService build() {
            return new NotificationService(new SenderRegistry(senders), new AsyncDispatcher(executor));
        }
    }
}
