package com.notifications.retry;

import com.notifications.core.NotificationSender;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;
import com.notifications.model.Success;

import java.time.Duration;
import java.util.Objects;

public class RetryingSender<T extends Notification> implements NotificationSender<T> {

    private final NotificationSender<T> delegate;
    private final int maxRetries;
    private final Duration initialBackoff;

    public RetryingSender(NotificationSender<T> delegate, int maxRetries, Duration initialBackoff) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxRetries < 0)
            throw new IllegalArgumentException("maxRetries no puede ser negativo: " + maxRetries);
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff");
        if (initialBackoff.isNegative())
            throw new IllegalArgumentException("initialBackoff no puede ser negativo: " + initialBackoff);
        this.maxRetries = maxRetries;
    }

    @Override
    public NotificationResult send(T notification) {
        NotificationResult resultado = delegate.send(notification); // attempt 1
        if (resultado instanceof Success) {
            return resultado;
        }
        Duration espera = initialBackoff;
        for (int intento = 1; intento <= maxRetries; intento++) { // retry
            if (!dormir(espera)) return resultado;
            espera = espera.multipliedBy(2);        // double for next time
            resultado = delegate.send(notification);
            if (resultado instanceof Success) return resultado;
        }

        return resultado;
    }

    @Override
    public Class<T> supports() {
        return delegate.supports();
    }

    private boolean dormir(Duration espera) {
        if (espera.isZero() || espera.isNegative()) return true;
        try {
            Thread.sleep(espera.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
