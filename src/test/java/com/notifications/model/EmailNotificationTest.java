package com.notifications.model;

import com.notifications.exception.InvalidNotificationException;
import com.notifications.exception.NotificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EmailNotification")
class EmailNotificationTest {

    private static final String TO = "cliente@ejemplo.com";
    private static final String FROM = "no-reply@miapp.com";
    private static final String SUBJECT = "Bienvenido";
    private static final String BODY = "Gracias por registrarte";

    @Test
    @DisplayName("se construye con datos válidos y expone sus campos")
    void seConstruyeConDatosValidos() {
        // given datos válidos / when se construye
        EmailNotification email = new EmailNotification(TO, FROM, SUBJECT, BODY);

        // then los campos quedan accesibles tal cual se pasaron
        assertEquals(TO, email.to());
        assertEquals(FROM, email.from());
        assertEquals(SUBJECT, email.subject());
        assertEquals(BODY, email.body());
    }

    @Test
    @DisplayName("es una Notification (participa del tipo sellado)")
    void esUnaNotification() {
        assertInstanceOf(Notification.class, new EmailNotification(TO, FROM, SUBJECT, BODY));
    }

    @ParameterizedTest(name = "destino inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "sin-arroba", "@dominio.com", "user@", "user@dominio", "a b@c.com"})
    @DisplayName("rechaza un email de destino mal formado")
    void rechazaDestinoInvalido(String destinoInvalido) {
        // given un destino inválido / when se construye / then lanza antes de existir
        assertThrows(InvalidNotificationException.class,
                () -> new EmailNotification(destinoInvalido, FROM, SUBJECT, BODY));
    }

    @ParameterizedTest(name = "origen inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "sin-arroba", "@dominio.com", "user@", "user@dominio"})
    @DisplayName("rechaza un email de origen mal formado")
    void rechazaOrigenInvalido(String origenInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new EmailNotification(TO, origenInvalido, SUBJECT, BODY));
    }

    @ParameterizedTest(name = "asunto inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("rechaza un asunto vacío o en blanco")
    void rechazaAsuntoVacio(String asuntoInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new EmailNotification(TO, FROM, asuntoInvalido, BODY));
    }

    @ParameterizedTest(name = "cuerpo inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\n"})
    @DisplayName("rechaza un cuerpo vacío o en blanco")
    void rechazaCuerpoVacio(String cuerpoInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new EmailNotification(TO, FROM, SUBJECT, cuerpoInvalido));
    }

    @Test
    @DisplayName("el error de validación identifica el campo que falló")
    void elErrorIdentificaElCampoQueFallo() {
        // given un destino inválido
        // when se intenta construir
        InvalidNotificationException ex = assertThrows(InvalidNotificationException.class,
                () -> new EmailNotification("roto", FROM, SUBJECT, BODY));

        // then el mensaje distingue destino de origen (ambos son emails)
        assertTrue(ex.getMessage().contains("destino"),
                "El mensaje debería señalar el destino, pero fue: " + ex.getMessage());
    }

    @Test
    @DisplayName("la excepción de validación es una NotificationException (permite un catch común)")
    void laExcepcionEsDeLaJerarquiaDeLaLibreria() {
        // given un dato inválido / when falla la validación
        // then el consumidor puede capturarla con el tipo raíz de la librería
        assertThrows(NotificationException.class,
                () -> new EmailNotification("roto", FROM, SUBJECT, BODY));
    }

    @Test
    @DisplayName("acepta un cuerpo largo y con saltos de línea")
    void aceptaCuerpoLargoYMultilinea() {
        // given un cuerpo realista de email
        String cuerpo = "Hola,\n\nGracias por registrarte.\n\nUn saludo,\nEl equipo";

        // when se construye / then se acepta sin problema
        EmailNotification email = new EmailNotification(TO, FROM, SUBJECT, cuerpo);
        assertEquals(cuerpo, email.body());
    }

    @Test
    @DisplayName("dos emails con los mismos datos son iguales (semántica de valor)")
    void dosEmailsConLosMismosDatosSonIguales() {
        // given dos instancias construidas con los mismos datos
        EmailNotification uno = new EmailNotification(TO, FROM, SUBJECT, BODY);
        EmailNotification otro = new EmailNotification(TO, FROM, SUBJECT, BODY);

        // then son iguales y comparten hashCode: es un record, tiene semántica de valor
        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());
    }
}
