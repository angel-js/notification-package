package com.notifications.core.strategy;

import com.notifications.core.NotificationSender;
import com.notifications.exception.NoSenderRegisteredException;
import com.notifications.exception.NotificationException;
import com.notifications.model.EmailNotification;
import com.notifications.model.NotificationResult;
import com.notifications.model.PushNotification;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SenderRegistry")
class SenderRegistryTest {

    @Mock
    private NotificationSender<EmailNotification> emailSender;

    @Mock
    private NotificationSender<SmsNotification> smsSender;

    private static final EmailNotification EMAIL =
            new EmailNotification("a@b.com", "c@d.com", "Asunto", "Cuerpo");
    private static final SmsNotification SMS =
            new SmsNotification("+56911111111", "+56922222222", "Mensaje");
    private static final PushNotification PUSH =
            new PushNotification("token", "Título", "Cuerpo");

    @Test
    @DisplayName("despacha la notificación al sender que declara su tipo")
    void despachaAlSenderQueDeclaraSuTipo() {
        // given un registry con un sender de email
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        Success esperado = Success.of("sg_1");
        when(emailSender.send(EMAIL)).thenReturn(esperado);
        SenderRegistry registry = new SenderRegistry(List.of(emailSender));

        // when se envía un email
        NotificationResult resultado = registry.send(EMAIL);

        // then se delega en ese sender y se devuelve su resultado sin alterarlo
        verify(emailSender).send(EMAIL);
        assertSame(esperado, resultado);
    }

    @Test
    @DisplayName("no invoca a los senders de otros canales")
    void noInvocaSendersDeOtrosCanales() {
        // given un registry con senders de email y de SMS
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        when(smsSender.supports()).thenReturn(SmsNotification.class);
        when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
        SenderRegistry registry = new SenderRegistry(List.of(emailSender, smsSender));

        // when se envía un email
        registry.send(EMAIL);

        // then el sender de SMS no se toca: el despacho es por tipo, no un broadcast
        verify(emailSender).send(EMAIL);
        verify(smsSender, never()).send(any());
    }

    @Test
    @DisplayName("lanza NoSenderRegisteredException si no hay sender para ese tipo")
    void lanzaSiNoHaySenderParaEseTipo() {
        // given un registry que solo sabe de email
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        SenderRegistry registry = new SenderRegistry(List.of(emailSender));

        // when se intenta enviar un push
        // then falla explícitamente en vez de ignorar el envío en silencio
        assertThrows(NoSenderRegisteredException.class, () -> registry.send(PUSH));
    }

    @Test
    @DisplayName("el error de sender no registrado nombra el tipo que faltaba")
    void elErrorNombraElTipoQueFaltaba() {
        // given un registry vacío
        SenderRegistry registry = new SenderRegistry(List.of());

        // when se intenta enviar un SMS
        NoSenderRegisteredException ex = assertThrows(NoSenderRegisteredException.class,
                () -> registry.send(SMS));

        // then el mensaje dice qué canal falta configurar
        assertTrue(ex.getMessage().contains("SmsNotification"),
                "El mensaje debería nombrar el tipo, pero fue: " + ex.getMessage());
    }

    @Test
    @DisplayName("NoSenderRegisteredException pertenece a la jerarquía de la librería")
    void noSenderRegisteredEsDeLaJerarquiaDeLaLibreria() {
        // given un registry vacío / when no hay sender
        // then el consumidor puede capturarla con el tipo raíz
        SenderRegistry registry = new SenderRegistry(List.of());
        assertThrows(NotificationException.class, () -> registry.send(EMAIL));
    }

    @Test
    @DisplayName("un registry vacío rechaza cualquier envío")
    void registryVacioRechazaCualquierEnvio() {
        // given un registry sin senders (caso borde: nadie llamó a register)
        SenderRegistry registry = new SenderRegistry(List.of());

        // then ningún canal funciona, y falla de forma explícita
        assertThrows(NoSenderRegisteredException.class, () -> registry.send(EMAIL));
        assertThrows(NoSenderRegisteredException.class, () -> registry.send(SMS));
        assertThrows(NoSenderRegisteredException.class, () -> registry.send(PUSH));
    }

    @Test
    @DisplayName("rechaza dos senders que declaran el mismo tipo")
    void rechazaDosSendersDelMismoTipo() {
        // given dos senders de email (p. ej. SendGrid y Mailgun a la vez)
        @SuppressWarnings("unchecked")
        NotificationSender<EmailNotification> otroEmailSender = org.mockito.Mockito.mock(NotificationSender.class);
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        when(otroEmailSender.supports()).thenReturn(EmailNotification.class);

        // when se construye el registry
        // then falla al configurar: enviar por ambos duplicaría el email
        assertThrows(IllegalStateException.class,
                () -> new SenderRegistry(List.of(emailSender, otroEmailSender)));
    }

    @Test
    @DisplayName("el error de duplicado nombra el tipo en conflicto")
    void elErrorDeDuplicadoNombraElTipoEnConflicto() {
        // given dos senders del mismo canal
        @SuppressWarnings("unchecked")
        NotificationSender<EmailNotification> otroEmailSender = org.mockito.Mockito.mock(NotificationSender.class);
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        when(otroEmailSender.supports()).thenReturn(EmailNotification.class);

        // when se construye
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new SenderRegistry(List.of(emailSender, otroEmailSender)));

        // then el mensaje permite localizar el conflicto
        assertTrue(ex.getMessage().contains("EmailNotification"),
                "El mensaje debería nombrar el tipo duplicado, pero fue: " + ex.getMessage());
    }

    @Test
    @DisplayName("acepta un sender por cada tipo distinto")
    void aceptaUnSenderPorCadaTipoDistinto() {
        // given senders de canales distintos
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        when(smsSender.supports()).thenReturn(SmsNotification.class);
        when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
        when(smsSender.send(SMS)).thenReturn(Success.of("tw_1"));

        // when se registran juntos
        SenderRegistry registry = new SenderRegistry(List.of(emailSender, smsSender));

        // then cada tipo llega a su sender
        assertEquals("sg_1", ((Success) registry.send(EMAIL)).messageId());
        assertEquals("tw_1", ((Success) registry.send(SMS)).messageId());
    }

    @Test
    @DisplayName("rechaza una lista de senders nula")
    void rechazaListaNula() {
        assertThrows(NullPointerException.class, () -> new SenderRegistry(null));
    }

    @Test
    @DisplayName("los cambios posteriores en la lista no afectan al registry")
    void losCambiosPosterioresEnLaListaNoAfectanAlRegistry() {
        // given un registry construido a partir de una lista mutable
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        java.util.List<NotificationSender<?>> mutable = new java.util.ArrayList<>();
        mutable.add(emailSender);
        SenderRegistry registry = new SenderRegistry(mutable);

        // when se altera la lista original después de construir
        mutable.clear();

        // then el registry conserva su estado: hizo una copia inmutable al construir
        when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
        assertEquals("sg_1", ((Success) registry.send(EMAIL)).messageId());
    }
}
