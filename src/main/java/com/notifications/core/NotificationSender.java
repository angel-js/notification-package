package com.notifications.core;

import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;

public interface NotificationSender<T extends Notification> {
    NotificationResult send(T notification);
    Class<T> supports();
}
