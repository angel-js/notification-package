package com.notifications.examples;

import com.notifications.channel.email.SendGridSender;
import com.notifications.channel.push.AndroidSender;
import com.notifications.channel.sms.TwilioSender;
import com.notifications.config.email.SendGridConfig;
import com.notifications.config.push.AndroidConfig;
import com.notifications.config.sms.TwilioConfig;
import com.notifications.core.NotificationSender;
import com.notifications.core.NotificationService;
import com.notifications.exception.InvalidNotificationException;
import com.notifications.exception.NoSenderRegisteredException;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;
import com.notifications.model.PushNotification;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import com.notifications.retry.RetryingSender;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo ejecutable de la librería. Recorre los tres canales, el envío asíncrono,
 * los reintentos y la frontera entre excepción y {@code Failure}.
 *
 * <p>Ejecutar con:</p>
 * <pre>{@code
 * mvn clean package -DskipTests dependency:copy-dependencies
 * java -cp "target/notifications-lib-1.0.0.jar:target/dependency/*" \
 *      com.notifications.examples.NotificationExamples
 * }</pre>
 *
 * <p>O directamente con Docker: {@code docker build -t notifications-lib . && docker run --rm notifications-lib}</p>
 */
public class NotificationExamples {

    public static void main(String[] args) {
        titulo("LIBRERÍA DE NOTIFICACIONES — DEMO");
        System.out.println("El envío es simulado: no hay llamadas HTTP reales.");
        System.out.println("Los logs [SendGrid], [Twilio]... muestran los datos que enviaría cada proveedor.");

        demoEmail();
        demoSms();
        demoPush();
        demoAsync();
        demoRetry();
        demoErrores();
        demoSeguridad();

        titulo("FIN DE LA DEMO");
    }

    // ---------------------------------------------------------------- canales

    /** Canal email vía SendGrid. */
    private static void demoEmail() {
        titulo("1. CANAL EMAIL (SendGrid)");

        NotificationService service = NotificationService.builder()
                .register(new SendGridSender(new SendGridConfig(env("SENDGRID_API_KEY"))))
                .build();

        NotificationResult resultado = service.send(new EmailNotification(
                "cliente@ejemplo.com",      // to
                "no-reply@miapp.com",       // from
                "Bienvenido",               // subject
                "Gracias por registrarte"   // body
        ));

        imprimir(resultado);
    }

    /** Canal SMS vía Twilio. */
    private static void demoSms() {
        titulo("2. CANAL SMS (Twilio)");

        NotificationService service = NotificationService.builder()
                .register(new TwilioSender(TwilioConfig.builder()
                        .accountSid(env("TWILIO_ACCOUNT_SID"))
                        .authToken(env("TWILIO_AUTH_TOKEN"))
                        .build()))
                .build();

        NotificationResult resultado = service.send(new SmsNotification(
                "+56911111111",         // from  (E.164)
                "+56922222222",         // to    (E.164)
                "Tu código es 1234"     // message
        ));

        imprimir(resultado);
    }

    /** Canal push vía Android. */
    private static void demoPush() {
        titulo("3. CANAL PUSH (Android)");

        NotificationService service = NotificationService.builder()
                .register(new AndroidSender(AndroidConfig.builder()
                        .apiKey(env("FCM_API_KEY"))
                        .serverKey(env("FCM_SERVER_KEY"))
                        .build()))
                .build();

        NotificationResult resultado = service.send(new PushNotification(
                "device-token-abc123",     // deviceToken
                "Pedido enviado",          // title
                "Tu pedido va en camino"   // body
        ));

        imprimir(resultado);
    }

    // ----------------------------------------------------------------- async

    /** Envío asíncrono y por lotes, con Executor propio. */
    private static void demoAsync() {
        titulo("4. ASÍNCRONO (CompletableFuture)");

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            NotificationService service = NotificationService.builder()
                    .register(new SendGridSender(new SendGridConfig(env("SENDGRID_API_KEY"))))
                    .register(new TwilioSender(TwilioConfig.builder()
                            .accountSid(env("TWILIO_ACCOUNT_SID"))
                            .authToken(env("TWILIO_AUTH_TOKEN"))
                            .build()))
                    .register(new AndroidSender(AndroidConfig.builder()
                            .apiKey(env("FCM_API_KEY"))
                            .serverKey(env("FCM_SERVER_KEY"))
                            .build()))
                    .executor(pool)          // el pool lo pone la aplicación, no la librería
                    .build();

            // --- sendAsync: no bloquea el hilo actual
            System.out.println("\n> sendAsync: lanzamos y seguimos trabajando");
            var futuro = service.sendAsync(new EmailNotification(
                    "async@ejemplo.com", "no-reply@miapp.com", "Asíncrono", "Enviado sin bloquear"));

            System.out.println("  el hilo principal sigue libre mientras se envía...");
            imprimir(esperar(futuro));

            // --- sendBatch: los tres canales en paralelo
            System.out.println("\n> sendBatch: 3 canales a la vez, en paralelo");
            List<Notification> lote = List.of(
                    new EmailNotification("lote@ejemplo.com", "no-reply@miapp.com", "Lote", "Email del lote"),
                    new SmsNotification("+56911111111", "+56933333333", "SMS del lote"),
                    new PushNotification("device-token-lote", "Lote", "Push del lote"));

            List<NotificationResult> resultados = esperar(service.sendBatch(lote));
            System.out.println("  los resultados llegan en el MISMO orden que la entrada:");
            for (int i = 0; i < resultados.size(); i++) {
                System.out.printf("  [%d] %s -> ", i, lote.get(i).getClass().getSimpleName());
                imprimir(resultados.get(i));
            }
        } finally {
            pool.shutdown();     // el pool es de la app: la app lo apaga
        }
    }

    // ----------------------------------------------------------------- retry

    /** Reintentos con backoff exponencial, compuestos como decorator. */
    private static void demoRetry() {
        titulo("5. REINTENTOS (RetryingSender)");
        System.out.println("Usamos un sender inestable que falla sus 2 primeros intentos.\n");

        SenderInestable inestable = new SenderInestable(2);

        // el decorator envuelve al sender: el servicio no se entera
        NotificationService service = NotificationService.builder()
                .register(new RetryingSender<>(inestable, 3, Duration.ofMillis(100)))
                .build();

        NotificationResult resultado = service.send(new EmailNotification(
                "retry@ejemplo.com", "no-reply@miapp.com", "Con reintentos", "Debería llegar al 3er intento"));

        System.out.println("\n  intentos ejecutados: " + inestable.intentos());
        imprimir(resultado);

        // --- y si nunca funciona, se agotan los reintentos y devuelve Failure
        System.out.println("\n> Ahora uno que falla SIEMPRE (backoff 50ms, 2 reintentos):");
        SenderInestable siempreFalla = new SenderInestable(Integer.MAX_VALUE);
        NotificationService conFallo = NotificationService.builder()
                .register(new RetryingSender<>(siempreFalla, 2, Duration.ofMillis(50)))
                .build();

        NotificationResult fallo = conFallo.send(new EmailNotification(
                "nunca@ejemplo.com", "no-reply@miapp.com", "Nunca llega", "Se agotarán los reintentos"));

        System.out.println("  intentos ejecutados: " + siempreFalla.intentos() + " (1 inicial + 2 reintentos)");
        imprimir(fallo);
    }

    // --------------------------------------------------------------- errores

    /** La frontera del diseño: qué lanza y qué se devuelve. */
    private static void demoErrores() {
        titulo("6. ERRORES: excepción vs. Failure");

        // --- dato inválido -> EXCEPCIÓN, antes de enviar nada
        System.out.println("> Email mal formado (error de programación):");
        try {
            new EmailNotification("esto-no-es-un-email", "a@b.com", "Asunto", "Cuerpo");
        } catch (InvalidNotificationException e) {
            System.out.println("  ✗ lanzó InvalidNotificationException AL CONSTRUIR: " + e.getMessage());
            System.out.println("    (el sender nunca llegó a verlo)");
        }

        // --- canal sin registrar -> EXCEPCIÓN
        System.out.println("\n> Canal sin sender registrado (error de configuración):");
        NotificationService soloEmail = NotificationService.builder()
                .register(new SendGridSender(new SendGridConfig(env("SENDGRID_API_KEY"))))
                .build();
        try {
            soloEmail.send(new SmsNotification("+56911111111", "+56922222222", "No hay sender de SMS"));
        } catch (NoSenderRegisteredException e) {
            System.out.println("  ✗ lanzó NoSenderRegisteredException: " + e.getMessage());
        }

        // --- fallo de envío -> VALOR, no excepción
        System.out.println("\n> Fallo del proveedor (condición operativa esperable):");
        NotificationService conProveedorCaido = NotificationService.builder()
                .register(new SenderInestable(Integer.MAX_VALUE))
                .build();
        NotificationResult resultado = conProveedorCaido.send(new EmailNotification(
                "cliente@ejemplo.com", "no-reply@miapp.com", "Asunto", "Cuerpo"));
        System.out.println("  → NO lanzó. Devolvió un valor:");
        imprimir(resultado);
        System.out.println("\n  Regla: los errores de programación LANZAN; los fallos de envío se DEVUELVEN.");
    }

    // ------------------------------------------------------------ seguridad

    /** Las credenciales nunca se filtran, ni siquiera al imprimir la config. */
    private static void demoSeguridad() {
        titulo("7. SEGURIDAD: las credenciales no se filtran");

        SendGridConfig config = new SendGridConfig("SG.clave-secretisima-que-no-debe-verse");
        TwilioConfig twilio = TwilioConfig.builder()
                .accountSid("AC-sid-secreto")
                .authToken("token-secretisimo")
                .build();

        System.out.println("Aunque imprimas la config entera, los secretos van enmascarados:\n");
        System.out.println("  " + config);
        System.out.println("  " + twilio);
        System.out.println("\n  El toString() de un record filtraría las credenciales por defecto;");
        System.out.println("  por eso está sobrescrito en todas las configs.");
    }

    // ---------------------------------------------------------------- apoyo

    /**
     * Sender de demo que falla sus primeros {@code fallosIniciales} intentos y luego
     * funciona. Existe solo para que los reintentos se vean en acción: los senders
     * reales simulan siempre un envío correcto.
     */
    private static final class SenderInestable implements NotificationSender<EmailNotification> {

        private final int fallosIniciales;
        private final AtomicInteger intentos = new AtomicInteger();

        SenderInestable(int fallosIniciales) {
            this.fallosIniciales = fallosIniciales;
        }

        @Override
        public NotificationResult send(EmailNotification notification) {
            int intento = intentos.incrementAndGet();
            if (intento <= fallosIniciales) {
                System.out.println("  [proveedor inestable] intento " + intento + " -> 503 Service Unavailable");
                return new Failure("PROVIDER_503", "El proveedor no está disponible");
            }
            System.out.println("  [proveedor inestable] intento " + intento + " -> 200 OK");
            return Success.of("demo_" + UUID.randomUUID());
        }

        @Override
        public Class<EmailNotification> supports() {
            return EmailNotification.class;
        }

        int intentos() {
            return intentos.get();
        }
    }

    /**
     * Lee una credencial del entorno. En una app real NO habría respaldo: las credenciales
     * se inyectan por variable de entorno y nunca se escriben en el código. Aquí se usa un
     * valor de demo para que los ejemplos corran sin configurar nada.
     */
    private static String env(String variable) {
        String valor = System.getenv(variable);
        return (valor != null && !valor.isBlank()) ? valor : "demo-" + variable.toLowerCase();
    }

    /** Imprime el resultado usando pattern matching sobre el tipo sellado. */
    private static void imprimir(NotificationResult resultado) {
        String linea = switch (resultado) {
            case Success s -> "  ✓ Success  id=" + s.messageId() + "  ts=" + s.timestamp();
            case Failure f -> "  ✗ Failure  [" + f.errorCode() + "] " + f.message();
        };
        System.out.println(linea);
    }

    private static <T> T esperar(java.util.concurrent.CompletableFuture<T> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo interrumpida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Fallo en la demo", e.getCause());
        }
    }

    private static void titulo(String texto) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + texto);
        System.out.println("=".repeat(70));
    }
}
