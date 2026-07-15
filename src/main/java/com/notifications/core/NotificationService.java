package com.notifications.core;

import com.notifications.core.strategy.SenderRegistry;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * NotificationResult result = service.send(
 *         new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com",
 *                               "Bienvenido", "Gracias por registrarte"));
 * }</pre>
 *
 * <p>Esta clase no conoce ningún canal ni proveedor concreto: delega la resolución en
 * {@link SenderRegistry}. Añadir un canal nuevo no requiere modificarla (Open/Closed).</p>
 */
public class NotificationService {

    private final SenderRegistry registry;

    private NotificationService(SenderRegistry registry) {
        this.registry = registry;
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
         * @return el servicio ya inmutable y listo para usar
         * @throws IllegalStateException si hay dos senders registrados para el mismo tipo
         */
        public NotificationService build() {
            return new NotificationService(new SenderRegistry(senders));
        }
    }
}
