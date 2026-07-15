package com.notifications.core.strategy;

import com.notifications.core.NotificationSender;
import com.notifications.exception.NoSenderRegisteredException;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Índice de estrategias de envío: sabe qué {@link NotificationSender} atiende cada
 * tipo de notificación y le delega el envío.
 *
 * <p>La lista de senders se recorre una única vez al construir, quedando indexada en
 * un mapa inmutable por {@link NotificationSender#supports()}. A partir de ahí cada
 * resolución es O(1) y segura entre hilos sin necesidad de sincronización.</p>
 */
public final class SenderRegistry {

    private final Map<Class<? extends Notification>, NotificationSender<?>> senders;

    /**
     * @param senderList senders a registrar, como máximo uno por tipo de notificación
     * @throws IllegalStateException si dos senders declaran el mismo tipo en {@code supports()}
     */
    public SenderRegistry(List<NotificationSender<?>> senderList) {
        Objects.requireNonNull(senderList, "senderList");

        Map<Class<? extends Notification>, NotificationSender<?>> map = new HashMap<>();
        for (NotificationSender<?> sender : senderList) {
            Class<? extends Notification> type = sender.supports();
            NotificationSender<?> previous = map.putIfAbsent(type, sender);
            if (previous != null) {
                throw new IllegalStateException("Ya hay un sender para " + type.getSimpleName()
                        + ": " + previous.getClass().getSimpleName());
            }
        }
        this.senders = Map.copyOf(map);
    }

    /**
     * Resuelve el sender que atiende el tipo de {@code notification} y le delega el envío.
     *
     * @param notification notificación a enviar
     * @return el resultado devuelto por el sender correspondiente
     * @throws NoSenderRegisteredException si no hay ningún sender para ese tipo
     */
    @SuppressWarnings("unchecked")
    public NotificationResult send(Notification notification) {
        NotificationSender<?> sender = senders.get(notification.getClass());
        if (sender == null) {
            throw new NoSenderRegisteredException(notification.getClass());
        }
        // Cast seguro por construcción, aunque el compilador no pueda demostrarlo.
        //
        // Motivo: la clave del mapa SIEMPRE proviene de sender.supports(), que por firma
        // devuelve el Class<T> de su propio NotificationSender<T>. Se cumple por tanto la
        // invariante Class<T> -> NotificationSender<T>, imposible de romper desde fuera
        // porque el mapa es privado, inmutable y solo se puebla en el constructor.
        // Como buscamos con notification.getClass(), el sender recuperado acepta
        // forzosamente ESTA notificación.
        return ((NotificationSender<Notification>) sender).send(notification);
    }
}
