package com.notifications.model;

import com.notifications.utils.Validations;

public record SmsNotification(String from, String to, String message) implements Notification {

    public SmsNotification {
        Validations.requireMatch(from, Validations.E164, "Teléfono origen inválido (E.164): " + from);
        Validations.requireMatch(to, Validations.E164, "Teléfono destino inválido (E.164): " + to);
        Validations.requireNotBlank(message, "El mensaje");
    }
}
