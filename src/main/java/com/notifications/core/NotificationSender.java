package com.notifications.core;

import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

/**
 * Estrategia de envío para un tipo concreto de notificación: es el patrón Strategy
 * sobre el que se apoya la librería. Cada proveedor (SendGrid, Twilio, etc.) la
 * implementa una vez.
 *
 * <p>Implementar esta interfaz es el <strong>único</strong> paso necesario para añadir
 * un canal o proveedor nuevo: el {@link NotificationService} lo indexa automáticamente
 * por {@link #supports()}, sin modificar código existente (Open/Closed).</p>
 *
 * <pre>{@code
 * public class SendGridSender implements NotificationSender<EmailNotification> {
 *     public NotificationResult send(EmailNotification n) { ... }
 *     public Class<EmailNotification> supports() { return EmailNotification.class; }
 * }
 * }</pre>
 *
 * @param <T> tipo de notificación que este sender sabe enviar
 */
public interface NotificationSender<T extends Notification> {

    /**
     * Envía la notificación al proveedor.
     *
     * <p>Un fallo de envío <strong>no</strong> se propaga como excepción: se devuelve
     * como {@code Failure} dentro del resultado, porque es una condición operativa
     * esperable y no un error de programación.</p>
     *
     * @param notification notificación ya validada, no nula
     * @return {@code Success} con el id del mensaje, o {@code Failure} con el motivo
     */
    NotificationResult send(T notification);

    /**
     * Tipo de notificación que atiende este sender. Es la clave con la que el servicio
     * lo indexa y despacha.
     *
     * @return la clase del tipo soportado, p. ej. {@code EmailNotification.class}
     */
    Class<T> supports();
}
