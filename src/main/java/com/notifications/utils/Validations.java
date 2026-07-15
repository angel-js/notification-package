package com.notifications.utils;

import com.notifications.exception.InvalidNotificationException;

import java.util.regex.Pattern;

public final class Validations {

    public static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static final Pattern E164 =
            Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private Validations() {}

    public static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new InvalidNotificationException(field + " no puede estar vacío");
    }

    public static void requireMatch(String value, Pattern pattern, String errorMsg) {
        if (value == null || !pattern.matcher(value).matches())
            throw new InvalidNotificationException(errorMsg);
    }
}
