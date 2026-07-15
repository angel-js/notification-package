package com.notifications.model;

import java.util.Objects;

public record Failure(String errorCode, String message, Throwable cause)
        implements NotificationResult {

    public Failure {
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(message, "message");
        // cause SÍ puede ser null: no todos los fallos envuelven una excepción
    }

    // atajo cuando no hay excepción que envolver
    public Failure(String errorCode, String message) {
        this(errorCode, message, null);
    }
}
