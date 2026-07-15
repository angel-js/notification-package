package com.notifications.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Success")
class SuccessTest {

    @Test
    @DisplayName("se construye con messageId y timestamp explícitos")
    void seConstruyeConDatosExplicitos() {
        // given un id y un instante concretos
        Instant momento = Instant.parse("2026-07-14T10:15:30Z");

        // when se construye
        Success success = new Success("sg_123", momento);

        // then conserva ambos
        assertEquals("sg_123", success.messageId());
        assertEquals(momento, success.timestamp());
    }

    @Test
    @DisplayName("es un NotificationResult (participa del tipo sellado)")
    void esUnNotificationResult() {
        assertInstanceOf(NotificationResult.class, Success.of("id"));
    }

    @Test
    @DisplayName("of() pone el timestamp automáticamente")
    void ofPoneElTimestampAutomaticamente() {
        // given solo un messageId
        // when se usa la factoría
        Success success = Success.of("sg_123");

        // then el timestamp se rellena solo: el sender no tiene que pasarlo
        assertNotNull(success.timestamp());
        assertEquals("sg_123", success.messageId());
    }

    @Test
    @DisplayName("of() usa el instante actual")
    void ofUsaElInstanteActual() {
        // given el instante justo antes de crear el resultado
        Instant antes = Instant.now();

        // when se crea con la factoría
        Success success = Success.of("sg_123");
        Instant despues = Instant.now();

        // then el timestamp cae dentro de la ventana de ejecución
        assertTrue(!success.timestamp().isBefore(antes),
                "El timestamp no debería ser anterior al inicio de la llamada");
        assertTrue(!success.timestamp().isAfter(despues),
                "El timestamp no debería ser posterior al fin de la llamada");
    }

    @Test
    @DisplayName("rechaza un messageId nulo")
    void rechazaMessageIdNulo() {
        // given un messageId nulo / when se construye / then falla rápido
        assertThrows(NullPointerException.class, () -> new Success(null, Instant.now()));
    }

    @Test
    @DisplayName("rechaza un timestamp nulo")
    void rechazaTimestampNulo() {
        // given un timestamp nulo pasado por el constructor canónico
        // when se construye / then falla rápido, aunque of() nunca produzca este caso
        assertThrows(NullPointerException.class, () -> new Success("sg_123", null));
    }

    @Test
    @DisplayName("dos Success con los mismos datos son iguales (semántica de valor)")
    void dosSuccessConLosMismosDatosSonIguales() {
        Instant momento = Instant.parse("2026-07-14T10:15:30Z");

        Success uno = new Success("sg_123", momento);
        Success otro = new Success("sg_123", momento);

        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());
    }
}
