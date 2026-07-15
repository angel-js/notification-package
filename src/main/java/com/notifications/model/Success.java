package com.notifications.model;

import java.time.Instant;
import java.util.Objects;

public record Success(String messageId, Instant timestamp) implements NotificationResult {

        public Success {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(timestamp, "timestamp");
        }

        public static Success of(String messageId) {
            return new Success(messageId, Instant.now());
        }
}
