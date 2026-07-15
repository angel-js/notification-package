package com.notifications.utils;

import com.notifications.exception.InvalidNotificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validations")
class ValidationsTest {

    @Nested
    @DisplayName("requireNotBlank")
    class RequireNotBlank {

        @Test
        @DisplayName("acepta un valor con contenido")
        void aceptaValorConContenido() {
            // given un valor no vacío / when se valida / then no lanza
            assertDoesNotThrow(() -> Validations.requireNotBlank("hola", "El campo"));
        }

        @ParameterizedTest(name = "valor en blanco: \"{0}\"")
        @NullSource
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        @DisplayName("rechaza nulos y cadenas en blanco")
        void rechazaNulosYEnBlanco(String valorInvalido) {
            // given un valor nulo o en blanco / when se valida / then lanza
            assertThrows(InvalidNotificationException.class,
                    () -> Validations.requireNotBlank(valorInvalido, "El campo"));
        }

        @Test
        @DisplayName("incluye el nombre del campo en el mensaje de error")
        void incluyeNombreDelCampoEnElError() {
            // given un campo identificado por su nombre
            // when se valida un valor vacío
            InvalidNotificationException ex = assertThrows(InvalidNotificationException.class,
                    () -> Validations.requireNotBlank("", "El asunto"));

            // then el mensaje permite identificar qué campo falló
            assertTrue(ex.getMessage().contains("El asunto"),
                    "El mensaje debería nombrar el campo, pero fue: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("requireMatch")
    class RequireMatch {

        @Test
        @DisplayName("acepta un valor que casa con el patrón")
        void aceptaValorQueCasa() {
            assertDoesNotThrow(() ->
                    Validations.requireMatch("a@b.com", Validations.EMAIL, "email inválido"));
        }

        @Test
        @DisplayName("rechaza un valor que no casa con el patrón")
        void rechazaValorQueNoCasa() {
            assertThrows(InvalidNotificationException.class,
                    () -> Validations.requireMatch("no-es-email", Validations.EMAIL, "email inválido"));
        }

        @Test
        @DisplayName("rechaza null sin lanzar NullPointerException")
        void rechazaNullComoInvalidNotification() {
            // given un valor nulo
            // when se valida contra un patrón
            // then lanza la excepción de dominio, NO un NPE del motor de regex
            assertThrows(InvalidNotificationException.class,
                    () -> Validations.requireMatch(null, Validations.EMAIL, "email inválido"));
        }

        @Test
        @DisplayName("usa el mensaje de error proporcionado")
        void usaElMensajeProporcionado() {
            InvalidNotificationException ex = assertThrows(InvalidNotificationException.class,
                    () -> Validations.requireMatch("xxx", Validations.E164, "Teléfono inválido: xxx"));

            assertTrue(ex.getMessage().contains("Teléfono inválido"),
                    "Se esperaba el mensaje proporcionado, pero fue: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("patrón EMAIL")
    class PatronEmail {

        @ParameterizedTest(name = "email válido: {0}")
        @ValueSource(strings = {
                "a@b.com",
                "usuario@dominio.cl",
                "nombre.apellido@empresa.co.uk",
                "user+tag@gmail.com",
                "u@d.io"
        })
        @DisplayName("acepta emails bien formados")
        void aceptaEmailsValidos(String email) {
            assertTrue(Validations.EMAIL.matcher(email).matches(),
                    "Debería aceptar: " + email);
        }

        @ParameterizedTest(name = "email inválido: \"{0}\"")
        @ValueSource(strings = {
                "sin-arroba.com",      // falta @
                "@dominio.com",        // falta parte local
                "usuario@",            // falta dominio
                "usuario@dominio",     // falta el punto del TLD
                "usuario@.com",        // dominio vacío antes del punto
                "con espacio@d.com",   // espacios
                "doble@@dominio.com"   // dos arrobas
        })
        @DisplayName("rechaza emails mal formados")
        void rechazaEmailsInvalidos(String email) {
            assertTrue(!Validations.EMAIL.matcher(email).matches(),
                    "Debería rechazar: " + email);
        }
    }

    @Nested
    @DisplayName("patrón E164")
    class PatronE164 {

        @ParameterizedTest(name = "teléfono válido: {0}")
        @ValueSource(strings = {
                "+56912345678",         // Chile
                "+14155552671",         // USA
                "+34600000000",         // España
                "+12",                  // mínimo: '+' y 2 dígitos
                "+123456789012345"      // máximo: 15 dígitos
        })
        @DisplayName("acepta teléfonos en formato E.164")
        void aceptaTelefonosValidos(String telefono) {
            assertTrue(Validations.E164.matcher(telefono).matches(),
                    "Debería aceptar: " + telefono);
        }

        @ParameterizedTest(name = "teléfono inválido: \"{0}\"")
        @ValueSource(strings = {
                "56912345678",          // sin '+'
                "+0912345678",          // no puede empezar en 0
                "+1",                   // demasiado corto
                "+1234567890123456",    // 16 dígitos: excede E.164
                "+56 9 1234 5678",      // con espacios
                "+56-9-12345678",       // con guiones
                "+abc123456",           // letras
                "+"                     // solo el signo
        })
        @DisplayName("rechaza teléfonos fuera del formato E.164")
        void rechazaTelefonosInvalidos(String telefono) {
            assertTrue(!Validations.E164.matcher(telefono).matches(),
                    "Debería rechazar: " + telefono);
        }
    }
}
