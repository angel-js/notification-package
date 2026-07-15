package com.notifications.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MaskSecrets")
class MaskSecretsTest {

    @Test
    @DisplayName("nunca devuelve el secreto original")
    void nuncaDevuelveElSecretoOriginal() {
        // given un secreto real
        String secreto = "SG.abc123XYZ-super-secreta";

        // when se enmascara
        String enmascarado = MaskSecrets.mask(secreto);

        // then el resultado no revela nada del original
        assertNotEquals(secreto, enmascarado);
        assertTrue(!enmascarado.contains(secreto),
                "El enmascarado no debe contener el secreto");
        assertEquals("****", enmascarado);
    }

    @ParameterizedTest(name = "secreto: {0}")
    @ValueSource(strings = {
            "a",                                    // secreto de 1 carácter
            "1234",
            "SG.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",  // largo
            "clave con espacios internos"
    })
    @DisplayName("enmascara cualquier secreto con contenido, sin importar su longitud")
    void enmascaraCualquierSecretoConContenido(String secreto) {
        assertEquals("****", MaskSecrets.mask(secreto));
    }

    @ParameterizedTest(name = "ausente: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("distingue un secreto ausente de uno presente")
    void distingueSecretoAusente(String ausente) {
        // given la ausencia de secreto
        // when se enmascara
        // then se marca como <null>, no como '****'
        //      (así al depurar se distingue "no configurado" de "configurado pero oculto")
        assertEquals("<null>", MaskSecrets.mask(ausente));
    }

    @Test
    @DisplayName("no filtra ningún fragmento del secreto, ni siquiera su longitud")
    void noFiltraFragmentosNiLongitud() {
        // given dos secretos de longitudes muy distintas
        String corto = "abc";
        String largo = "abcdefghijklmnopqrstuvwxyz0123456789";

        // when se enmascaran
        // then producen el mismo resultado: ni el contenido ni la longitud se infieren
        assertEquals(MaskSecrets.mask(corto), MaskSecrets.mask(largo));
    }
}
