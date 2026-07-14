package com.notifications.model;

import com.notifications.utils.Validations;

public record PushNotification(String deviceToken, String title, String body)
        implements Notification {

    public PushNotification {
        Validations.requireNotBlank(deviceToken, "El device token");
        Validations.requireNotBlank(title, "El título");
        Validations.requireNotBlank(body, "El cuerpo");
    }
}
