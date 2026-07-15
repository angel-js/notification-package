package com.notifications.model;

/**
 * Resultado de un intento de envío: o {@link Success} o {@link Failure}.
 *
 * <p>Es una interfaz {@code sealed} porque solo existen esos dos desenlaces. Al ser
 * cerrado, el compilador obliga al consumidor a tratar ambos casos:</p>
 *
 * <pre>{@code
 * switch (service.send(notification)) {
 *     case Success s -> log.info("Enviado, id={}", s.messageId());
 *     case Failure f -> log.error("Fallo [{}]: {}", f.errorCode(), f.message());
 * }
 * }</pre>
 *
 * <p>Los fallos de <em>envío</em> viajan aquí como {@link Failure}, es decir como un
 * valor y no como excepción: son una condición operativa esperable (el proveedor
 * rechaza, la red falla) y un valor no se puede ignorar por accidente. Las excepciones
 * quedan reservadas para los errores de programación, como construir una notificación
 * inválida o no registrar un sender.</p>
 */
public sealed interface NotificationResult permits Success, Failure {
}
