package com.notifications.model;

/**
 * Contrato base de todas las notificaciones de la librería.
 *
 * <p>Es una interfaz {@code sealed}: el conjunto de tipos (email, SMS y push) es
 * cerrado y conocido en tiempo de compilación. Eso permite tratarlos de forma
 * exhaustiva con {@code switch} y garantiza que nadie introduzca tipos no previstos.</p>
 *
 * <p>Cada implementación es un {@code record} inmutable que valida sus datos en el
 * constructor compacto y lanza
 * {@link com.notifications.exception.InvalidNotificationException} si son inválidos.
 * Por tanto, una notificación construida es siempre una notificación válida.</p>
 *
 * <p>Solo transporta lo que cambia <em>por mensaje</em> (destinatario y contenido) y es
 * agnóstica al proveedor: las credenciales y los datos fijos de cuenta viven en la
 * {@code Config} de cada proveedor.</p>
 */
public sealed interface Notification
        permits EmailNotification, PushNotification, SmsNotification {
}
