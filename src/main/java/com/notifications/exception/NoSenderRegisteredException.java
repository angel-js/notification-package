package com.notifications.exception;

public class NoSenderRegisteredException extends NotificationException {
    public NoSenderRegisteredException(Class<?> type) {
        super("There is no sender for this Class: " + type.getSimpleName());
    }
}
