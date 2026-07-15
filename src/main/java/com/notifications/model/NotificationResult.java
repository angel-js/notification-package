package com.notifications.model;

public sealed interface NotificationResult permits Success, Failure {
}
