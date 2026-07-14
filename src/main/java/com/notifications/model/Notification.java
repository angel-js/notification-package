package com.notifications.model;


public sealed interface Notification
        permits EmailNotification, PushNotification, SmsNotification {
}
