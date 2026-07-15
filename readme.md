# notifications-lib

Librería de notificaciones multicanal (email, SMS y push) para **Java 21**, sin dependencias
de frameworks. Toda la configuración se hace **por código**: no hay YAML ni properties.

> El envío es **simulado**: los senders no hacen llamadas HTTP reales, pero registran vía
> SLF4J exactamente los datos que pediría cada proveedor.

---

## Cómo se llama al servicio

La librería tiene **un único punto de entrada**: `NotificationService`. El flujo es siempre
el mismo, en dos tiempos:

1. **Configurar** (una vez, al arrancar tu app): eliges qué proveedor usa cada canal.
2. **Enviar** (tantas veces como quieras): pasas una notificación y el servicio ya sabe
   a quién dársela.

### 1. Construir el servicio

Registra **un sender por canal**. El proveedor concreto se decide aquí y en ningún otro sitio:

```java
NotificationService service = NotificationService.builder()
        // canal EMAIL -> proveedor SendGrid
        .register(new SendGridSender(
                new SendGridConfig(System.getenv("SENDGRID_API_KEY"))))

        // canal SMS -> proveedor Twilio
        .register(new TwilioSender(
                TwilioConfig.builder()
                        .accountSid(System.getenv("TWILIO_ACCOUNT_SID"))
                        .authToken(System.getenv("TWILIO_AUTH_TOKEN"))
                        .build()))

        // canal PUSH -> Android
        .register(new AndroidSender(
                AndroidConfig.builder()
                        .apiKey(System.getenv("FCM_API_KEY"))
                        .serverKey(System.getenv("FCM_SERVER_KEY"))
                        .build()))
        .build();
```

Para **cambiar de proveedor** solo cambias esa línea. El resto de tu código no se entera:

```java
.register(new MailgunSender(mailgunConfig))   // en vez de SendGridSender
```

> ⚠️ **Un solo sender por canal.** Si registras SendGrid *y* Mailgun a la vez, `build()`
> lanza `IllegalStateException`: ambos atienden `EmailNotification` y enviar por los dos
> significaría mandar el mismo email dos veces. El error salta al configurar, no en
> producción.

### 2. Enviar

Creas la notificación y llamas a `send()`. **No eliges el canal**: lo determina el tipo:

```java
// EMAIL
service.send(new EmailNotification(
        "cliente@ejemplo.com",     // to
        "no-reply@miapp.com",      // from
        "Bienvenido",              // subject
        "Gracias por registrarte"  // body
));

// SMS  (teléfonos en formato E.164)
service.send(new SmsNotification(
        "+56911111111",            // from
        "+56922222222",            // to
        "Tu código es 1234"        // message
));

// PUSH
service.send(new PushNotification(
        "device-token-abc123",     // deviceToken
        "Pedido enviado",          // title
        "Tu pedido va en camino"   // body
));
```

### 3. Leer el resultado

`send()` devuelve un `NotificationResult`, que solo puede ser `Success` o `Failure`.
Al ser un tipo sellado, el compilador te obliga a tratar ambos casos (no necesitas `default`):

```java
NotificationResult result = service.send(notification);

switch (result) {
    case Success s -> log.info("Enviado, id={} a las {}", s.messageId(), s.timestamp());
    case Failure f -> log.error("Fallo [{}]: {}", f.errorCode(), f.message());
}
```

### Errores: excepción vs. Failure

Es la regla más importante de la API:

| Situación | Qué obtienes | Cuándo |
|---|---|---|
| Datos inválidos (email mal formado, campo vacío) | `InvalidNotificationException` | **Al construir** la notificación |
| No registraste sender para ese canal | `NoSenderRegisteredException` | Al llamar a `send()` |
| El proveedor rechaza / falla la red | `Failure` (**no** excepción) | Dentro del resultado |

En corto: **los errores de programación lanzan; los fallos de envío se devuelven**.

```java
try {
    var email = new EmailNotification("esto-no-es-un-email", "a@b.com", "Hola", "Texto");
} catch (InvalidNotificationException e) {
    // salta aquí, ANTES de intentar enviar nada
}
```

---

## Proveedores soportados

| Canal | Notificación | Proveedores |
|---|---|---|
| Email | `EmailNotification` | `SendGridSender`, `MailgunSender` |
| SMS | `SmsNotification` | `TwilioSender`, `MovistarSender` |
| Push | `PushNotification` | `AndroidSender`, `IOSSender` |

---

## Seguridad de credenciales

- **Nunca hardcodees credenciales.** Léelas de variables de entorno (`System.getenv(...)`),
  como en los ejemplos de arriba.
- El `toString()` de todas las configs **enmascara los secretos** (`apiKey=****`), así que
  no se filtran ni en logs ni al depurar.
- Los senders también enmascaran las credenciales al registrar la petición simulada.

---

## Cómo extender

¿Necesitas un proveedor o un canal que no está? Está documentado paso a paso, con un caso
real trabajado de punta a punta, en **[EXTENDING.md](knowHowExtending.md)**:

- **Añadir un proveedor** a un canal existente (ej. Amazon SES) → no se toca ni un archivo existente
- **Añadir un canal nuevo** entero (ej. WhatsApp) → solo se toca el `permits` de `Notification`

Incluye checklist y los errores típicos que conviene evitar.

---

<!-- ======================================================================
     PENDIENTE — notas de trabajo, limpiar antes de entregar.
     Faltan aún: Instalación (Maven/Gradle), API Reference, Cómo extender
     (añadir un canal paso a paso), Decisiones de arquitectura y
     Qué faltaría por hacer.
     ====================================================================== -->

## Notas de trabajo (borrador)

Secciones que faltan por escribir:
- Instalación: ¿cómo se usa? (Maven/Gradle)
- API Reference: clases y métodos principales
- Decisiones de arquitectura: por qué sealed + records + strategy + builder
- Qué faltaría por hacer

(Cómo extender ✅ hecho → EXTENDING.md)

Comandos:
- `mvn clean` → borra `target/`
- `mvn compile` → compila `src/main`
- `mvn test` → compila todo y corre los tests
- `mvn package` → genera el `.jar` en `target/`
- `mvn dependency:tree` → ver qué dependencias arrastras

Modelo:

![img.png](img.png)
