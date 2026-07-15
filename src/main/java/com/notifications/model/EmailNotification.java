package com.notifications.model;

import com.notifications.utils.Validations;

public record EmailNotification(String to, String from, String subject, String body)
        implements Notification {

    public EmailNotification {
        Validations.requireMatch(to, Validations.EMAIL, "Email destino inválido: " + to);
        Validations.requireMatch(from, Validations.EMAIL, "Email origen inválido: " + from);
        Validations.requireNotBlank(subject, "El asunto");
        Validations.requireNotBlank(body, "El cuerpo");
    }
}
