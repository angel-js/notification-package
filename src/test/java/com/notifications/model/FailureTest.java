package com.notifications.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Failure")
class FailureTest {

    @Test
    @DisplayName("se construye con código, mensaje y causa")
    void seConstruyeConCausa() {
        // given una excepción que originó el fallo
        IOException causa = new IOException("connection reset");

        // when se construye el Failure envolviéndola
        Failure failure = new Failure("SENDGRID_ERROR", "Fallo al enviar vía SendGrid", causa);

        // then conserva los tres datos
        assertEquals("SENDGRID_ERROR", failure.errorCode());
        assertEquals("Fallo al enviar vía SendGrid", failure.message());
        assertSame(causa, failure.cause());
    }

    @Test
    @DisplayName("es un NotificationResult (participa del tipo sellado)")
    void esUnNotificationResult() {
        assertInstanceOf(NotificationResult.class, new Failure("CODE", "mensaje"));
    }

    @Test
    @DisplayName("el constructor de dos argumentos deja la causa nula")
    void constructorDeDosArgumentosDejaCausaNula() {
        // given un fallo sin excepción subyacente (p. ej. el proveedor devolvió 400)
        // when se usa el atajo de dos argumentos
        Failure failure = new Failure("PROVIDER_REJECTED", "El proveedor rechazó el envío");

        // then la causa queda nula: no todo fallo envuelve una excepción
        assertNull(failure.cause());
        assertEquals("PROVIDER_REJECTED", failure.errorCode());
        assertEquals("El proveedor rechazó el envío", failure.message());
    }

    @Test
    @DisplayName("acepta explícitamente una causa nula")
    void aceptaCausaNulaExplicita() {
        // given una causa nula pasada al constructor canónico
        // when se construye / then NO lanza: la causa es opcional por diseño
        assertDoesNotThrow(() -> new Failure("CODE", "mensaje", null));
    }

    @Test
    @DisplayName("rechaza un errorCode nulo")
    void rechazaErrorCodeNulo() {
        assertThrows(NullPointerException.class, () -> new Failure(null, "mensaje", null));
    }

    @Test
    @DisplayName("rechaza un mensaje nulo")
    void rechazaMensajeNulo() {
        assertThrows(NullPointerException.class, () -> new Failure("CODE", null, null));
    }

    @Test
    @DisplayName("no es una excepción: un fallo de envío es un valor que se devuelve")
    void noEsUnaExcepcionSinoUnValor() {
        // given un fallo de envío
        Failure failure = new Failure("TIMEOUT", "El proveedor no respondió");

        // then es un dato, no algo lanzable: por eso no se puede ignorar por accidente
        //      ni se propaga rompiendo un sendBatch
        assertInstanceOf(NotificationResult.class, failure);
        assertEquals("TIMEOUT", failure.errorCode());
    }

    @Test
    @DisplayName("dos Failure con los mismos datos son iguales (semántica de valor)")
    void dosFailureConLosMismosDatosSonIguales() {
        Failure uno = new Failure("CODE", "mensaje");
        Failure otro = new Failure("CODE", "mensaje");

        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());
    }
}
