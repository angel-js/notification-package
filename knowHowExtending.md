# Know-how: cómo extender la librería

Guía práctica para añadir cosas nuevas a `notifications-lib` sin romper nada.

Antes de empezar, identifica **cuál de los dos casos** es el tuyo, porque el esfuerzo y el
impacto son muy distintos:

| Quiero… | Ejemplo | ¿Toco código existente? | Dificultad |
|---|---|---|---|
| **A. Un proveedor nuevo** en un canal que ya existe | Amazon SES para email | **No, ninguno** | Baja |
| **B. Un canal nuevo** entero | WhatsApp, Slack | **Sí: una línea** (el `permits`) | Media |

---

## Caso A — Añadir un proveedor a un canal existente

Este es el caso bonito: **no se modifica ni un solo archivo existente**. Solo creas dos
clases y las enchufas. Esto es el principio Open/Closed funcionando de verdad.

Ejemplo: añadir **Amazon SES** al canal de email.

### A.1 · Crea la Config

En `com.notifications.config.email`. Lleva **solo lo que es fijo por cuenta**: credenciales
y datos del proveedor.

```java
package com.notifications.config.email;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record AmazonSesConfig(String accessKeyId, String secretAccessKey, String region) {

    public AmazonSesConfig {
        Objects.requireNonNull(accessKeyId, "accessKeyId");
        Objects.requireNonNull(secretAccessKey, "secretAccessKey");
        Objects.requireNonNull(region, "region");
    }

    @Override
    public String toString() {
        return "AmazonSesConfig{accessKeyId=" + MaskSecrets.mask(accessKeyId)
             + ", secretAccessKey=" + MaskSecrets.mask(secretAccessKey)
             + ", region=" + region                   // no es secreto: se muestra
             + "}";
    }
}
```

> ⚠️ **Obligatorio: sobrescribe `toString()` y enmascara los secretos.** El `toString()`
> que genera un `record` por defecto **imprime las credenciales en claro**. Lombok no te
> salva de esto. Enmascara las credenciales; los datos no sensibles (región, dominio,
> número emisor) déjalos visibles porque ayudan a depurar.

### A.2 · Crea el Sender

En `com.notifications.channel.email`. Implementa `NotificationSender<EmailNotification>`.

```java
package com.notifications.channel.email;

import com.notifications.config.email.AmazonSesConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.Success;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class AmazonSesSender implements NotificationSender<EmailNotification> {

    private final AmazonSesConfig config;

    public AmazonSesSender(AmazonSesConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public NotificationResult send(EmailNotification notification) {
        try {
            log.info("[AmazonSES] POST /v2/email/outbound-emails accessKeyId={} region={} "
                            + "from={} to={} subject='{}' body='{}'",
                    MaskSecrets.mask(config.accessKeyId()),   // ← config: datos del proveedor
                    config.region(),
                    notification.from(), notification.to(),   // ← notification: datos del mensaje
                    notification.subject(), notification.body());

            return Success.of("ses_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("SES_ERROR", "Fallo al enviar vía Amazon SES", e);
        }
    }

    @Override
    public Class<EmailNotification> supports() {
        return EmailNotification.class;
    }
}
```

Fíjate en la separación de la línea del log: **`config.*` para lo del proveedor, `notification.*`
para lo del mensaje**. Si te ves tirando de un dato del proveedor desde la notificación, algo
está mal modelado (ver *Errores típicos*, más abajo).

### A.3 · Regístralo

```java
NotificationService service = NotificationService.builder()
        .register(new AmazonSesSender(sesConfig))   // en vez de SendGridSender
        .build();
```

**Ya está.** No has tocado `NotificationService`, ni `SenderRegistry`, ni `Notification`, ni
ningún otro sender. Eso es Open/Closed.

> ⚠️ **Solo un sender por canal.** No registres `SendGridSender` y `AmazonSesSender` a la vez:
> ambos declaran `EmailNotification.class` en `supports()` y `build()` lanzará
> `IllegalStateException`. Los proveedores son **alternativas entre las que eliges**, no cosas
> que se ejecutan juntas (si los ejecutaras todos, mandarías el mismo email dos veces).
>
> ¿Y si de verdad quieres SendGrid con SES de respaldo? Eso es un **decorator** de failover
> (mismo patrón que `RetryingSender`), no una lista en el servicio.

---

## Caso B — Añadir un canal nuevo

Aquí sí tocas **un** archivo existente. Ejemplo: añadir **WhatsApp**.

### B.1 · Crea el record de la notificación

En `com.notifications.model`. Valida **todo** en el constructor compacto:

```java
package com.notifications.model;

import com.notifications.utils.Validations;

/**
 * Notificación de WhatsApp.
 *
 * @param to      teléfono destino en formato E.164 (p. ej. {@code +56912345678})
 * @param message texto del mensaje, no vacío
 */
public record WhatsAppNotification(String to, String message) implements Notification {

    public WhatsAppNotification {
        Validations.requireMatch(to, Validations.E164, "Teléfono destino inválido (E.164): " + to);
        Validations.requireNotBlank(message, "El mensaje");
    }
}
```

Reutiliza `Validations` (`requireNotBlank`, `requireMatch`, y las constantes `EMAIL` y `E164`).
Si necesitas un formato nuevo, añade la constante ahí y no la dupliques en el record.

### B.2 · Añádelo al `permits` ← *el único cambio en código existente*

```java
public sealed interface Notification
        permits EmailNotification, PushNotification, SmsNotification, WhatsAppNotification {
}
```

**Esta línea es el precio del `sealed`, y es un precio que pagamos a propósito.** A cambio de
tener que declarar aquí cada tipo nuevo, obtienes que el compilador conozca el conjunto
completo y pueda verificar exhaustividad.

Efecto secundario **deseable**: si en algún sitio hay un `switch` exhaustivo sobre
`Notification` sin `default`, **dejará de compilar** hasta que trates el caso nuevo. No es un
estorbo: es el compilador enseñándote la lista exacta de sitios que debes revisar. Sin
`sealed`, esos sitios fallarían en runtime y en silencio.

### B.3 · Crea la Config

En `com.notifications.config.whatsapp`. Igual que en el Caso A:

```java
package com.notifications.config.whatsapp;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record MetaWhatsAppConfig(String accessToken, String phoneNumberId) {

    public MetaWhatsAppConfig {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(phoneNumberId, "phoneNumberId");
    }

    @Override
    public String toString() {
        return "MetaWhatsAppConfig{accessToken=" + MaskSecrets.mask(accessToken)
             + ", phoneNumberId=" + phoneNumberId
             + "}";
    }
}
```

### B.4 · Crea el Sender

En `com.notifications.channel.whatsapp`:

```java
package com.notifications.channel.whatsapp;

import com.notifications.config.whatsapp.MetaWhatsAppConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.Success;
import com.notifications.model.WhatsAppNotification;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class MetaWhatsAppSender implements NotificationSender<WhatsAppNotification> {

    private final MetaWhatsAppConfig config;

    public MetaWhatsAppSender(MetaWhatsAppConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public NotificationResult send(WhatsAppNotification notification) {
        try {
            log.info("[WhatsApp] POST /v18.0/{}/messages accessToken={} to={} message='{}'",
                    config.phoneNumberId(),
                    MaskSecrets.mask(config.accessToken()),
                    notification.to(), notification.message());

            return Success.of("wa_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("WHATSAPP_ERROR", "Fallo al enviar vía WhatsApp", e);
        }
    }

    @Override
    public Class<WhatsAppNotification> supports() {
        return WhatsAppNotification.class;
    }
}
```

### B.5 · Regístralo y úsalo

```java
NotificationService service = NotificationService.builder()
        .register(new SendGridSender(sendGridConfig))
        .register(new MetaWhatsAppSender(whatsAppConfig))   // canal nuevo
        .build();

service.send(new WhatsAppNotification("+56912345678", "Tu pedido va en camino"));
```

**`NotificationService` y `SenderRegistry` no se han tocado.** Despachan el canal nuevo sin
enterarse de que existe.

---

## Checklist

Al añadir un **proveedor** (Caso A):

- [ ] Config: `record` + `@Builder` + `Objects.requireNonNull` de cada campo
- [ ] Config: `toString()` sobrescrito, credenciales enmascaradas con `MaskSecrets.mask(...)`
- [ ] Sender: `implements NotificationSender<TuNotification>` (**`implements`**, no `extends`)
- [ ] Sender: recibe su config por constructor con `Objects.requireNonNull`
- [ ] Sender: `supports()` devuelve `TuNotification.class`
- [ ] Sender: `try/catch` → devuelve `Failure`, **nunca** deja escapar una excepción
- [ ] Sender: enmascara las credenciales **también en los logs**
- [ ] Tests: envío OK devuelve `Success`; fallo devuelve `Failure`; `toString()` no filtra el secreto

Al añadir un **canal** (Caso B), además:

- [ ] Record de notificación con validación en el constructor compacto
- [ ] Añadido al `permits` de `Notification`
- [ ] Revisados los `switch` exhaustivos que el compilador señale
- [ ] Tests de validación del record (cada campo inválido lanza `InvalidNotificationException`)

---

## Errores típicos

**1. `extends` en vez de `implements`.**
`NotificationSender` es una **interfaz**. Si pones `extends`, el error real es
`no interface expected here`… pero además **verás una cascada de errores falsos**:
`cannot find symbol: variable log` en senders que están perfectos. Motivo: un error
estructural aborta la ronda de procesamiento de anotaciones y **Lombok no llega a generar
el campo `log` en ningún archivo**.
> Con Lombok, ante una avalancha de `cannot find symbol`, **no persigas los símbolos**:
> busca el primer error estructural, arréglalo y recompila.

**2. Meter datos del proveedor en la notificación.**
Si tu proveedor necesita un campo raro (`domain` de Mailgun, `region` de SES,
`phoneNumberId` de WhatsApp), **va en su Config, nunca en la Notification**. Dos tests:
- ¿Solo tiene sentido para *un* proveedor? → **Config**
- ¿Es fijo por cuenta en vez de cambiar por mensaje? → **Config**

Si metes `domain` en `EmailNotification`, `SendGridSender` recibe un campo que no sabe usar,
y el modelo se convierte en una bola de nieve de campos que solo le sirven a uno. La
notificación debe seguir siendo **agnóstica al proveedor**.

**3. Lanzar una excepción cuando falla el envío.**
Los fallos de envío se devuelven como `Failure`, **no** se lanzan. Las excepciones están
reservadas para errores de programación (datos inválidos, sender no registrado). Si lanzaras,
romperías `sendBatch` y el `RetryingSender`, que esperan un valor.

**4. Olvidar el `toString()` enmascarado.**
El `toString()` autogenerado de un `record` **filtra las credenciales**. Es el error más caro
de esta lista, porque no rompe nada… hasta que una API key aparece en un log de producción.

**5. Registrar dos proveedores del mismo canal.**
`build()` lanza `IllegalStateException`. No es un bug: es fail-fast. Elige uno, o compón un
decorator de failover.

---

## Y lo que **no** hay que tocar

Si al añadir un canal te ves editando alguno de estos, párate y replantea:

- `NotificationService` — es agnóstica a canales, solo delega
- `SenderRegistry` — indexa por `supports()`, no conoce tipos concretos
- Cualquier sender existente — son independientes entre sí
- `NotificationResult`, `Success`, `Failure` — los desenlaces son siempre dos

La **única** excepción legítima es el `permits` de `Notification` en el Caso B.
