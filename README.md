# GoCuotas — clientes Java (multi-módulo)

Proyecto **Gradle** y **Maven** bajo `java/`, con artefactos separados por API.

## Módulos

| Módulo | `artifactId` | Paquete principal | Descripción |
|--------|----------------|-------------------|-------------|
| `gocuotas-api-common` | `gocuotas-api-common` | `io.humen.gocuotas.api` | `GoCuotasApiException` y tipos compartidos entre clientes. |
| `gocuotas-redirect` | `gocuotas-redirect` | `io.humen.gocuotas.redirect` | **API Redirect V1** (`/api_redirect/v1/...`): checkout, authenticate, órdenes, reembolso. |
| `gocuotas-client` | `gocuotas-client` | `io.humen.gocuotas.client` | **API Client V1**: comercio, liquidaciones JSON, CSV resumen y **CSV por id** (`expense_settlements_csvs`, `expense_settlements_csvs/{id}`). |

Los errores HTTP genéricos se exponen como `io.humen.gocuotas.api.GoCuotasApiException`. En Redirect V1, un **404** al consultar o reembolsar una orden por id se mapea a `io.humen.gocuotas.redirect.OrderNotFoundException` (subclase de `GoCuotasApiException`).

---

## API Redirect V1 (`gocuotas-redirect`)

Cliente HTTP para la [API Redirect V1 de GoCuotas](https://gocuotas-api.stoplight.io/docs/gocuotas/dae8814842a1e-api-redirect-v1) documentada en Stoplight.

### Requisitos

- Java 17 o superior
- Construcción con **Gradle** (`./gradlew`) o **Maven** (`mvn`) desde el directorio `java/`

### Flujo de integración

1. **Variables de entorno:** `GOCUOTAS_API_KEY` (clave del panel: Sucursales → Ver Apikeys). Para **órdenes** sin Bearer explícito: si definís `GOCUOTAS_JWT`, el cliente lo usa tal cual; si no, con `GOCUOTAS_EMAIL` + `GOCUOTAS_API_KEY` como contraseña llama a `authenticate` y guarda el JWT en la instancia del cliente para reutilizarlo en siguientes llamadas (listado, detalle, reembolso).
2. **Checkout:** `createCheckout(request)` envía `Authorization: Bearer` con el valor de `GOCUOTAS_API_KEY` directamente (sin pasar por authenticate).
3. **Autenticación manual:** `authenticate(email, password)` — la contraseña puede ser la del comercio o, según tu integración, la misma API key del panel.
4. **Redirección:** la respuesta del checkout incluye `url_init`; redirigí al usuario a esa URL.
5. **Órdenes (listado):** `GET /api_redirect/v1/orders` requiere query `delivered_start` y `delivered_end` (formato `YYYY-MM-DD HH:mm`). Con el cliente sin Bearer explícito: `GOCUOTAS_JWT` o `GOCUOTAS_EMAIL` + `GOCUOTAS_API_KEY` → JWT, y `GOCUOTAS_DELIVERED_START` / `GOCUOTAS_DELIVERED_END` para el rango.
6. **Órdenes (detalle):** `GET /api_redirect/v1/orders/{id}` con el mismo Bearer que el listado (`buscarOrden(id)` / `getOrder(id)` sin token en la firma).
7. **Reembolso:** `DELETE /api_redirect/v1/orders/{id}` (`reembolsarOrden(id)` o `refundOrder(id)` para el JSON en bruto).

Los métodos `listOrders` / `getOrder` / `refundOrder` siguen disponibles si preferís el cuerpo `String` crudo.

Ante discrepancias con la documentación oficial, priorizá [Stoplight](https://gocuotas-api.stoplight.io/docs/gocuotas/dae8814842a1e-api-redirect-v1).

### Variables de entorno (Redirect)

| Variable | Obligatoriedad | Uso |
|----------|----------------|-----|
| `GOCUOTAS_API_KEY` | Sí (típico) | Bearer en `createCheckout(request)`. **Contraseña** en `authenticate` para JWT de órdenes. |
| `GOCUOTAS_EMAIL` | Sí para órdenes sin Bearer explícito (salvo `createCheckout`) | Email del comercio; con `GOCUOTAS_API_KEY` como contraseña se obtiene el JWT. |
| `GOCUOTAS_DELIVERED_START` | Sí para `listarOrdenes()` / `listOrders()` sin fechas en la firma | Inicio del rango → query `delivered_start`. |
| `GOCUOTAS_DELIVERED_END` | Sí (mismo caso) | Fin del rango → query `delivered_end`. |
| `GOCUOTAS_JWT` | No | Bearer en órdenes sin token en la firma (evita `authenticate`). |

### Código de ejemplo (Redirect)

```bash
export GOCUOTAS_API_KEY="tu_api_key_del_panel"
export GOCUOTAS_EMAIL="tu-email-comercio@gocuotas.com"
export GOCUOTAS_DELIVERED_START="2021-01-19 10:00"
export GOCUOTAS_DELIVERED_END="2024-12-19 20:00"
```

```java
import io.humen.gocuotas.redirect.GoCuotasRedirectClient;
import io.humen.gocuotas.redirect.GoCuotasRedirectConfig;
import io.humen.gocuotas.redirect.model.CreateCheckoutRequest;

var config = GoCuotasRedirectConfig.defaultConfig();
var client = new GoCuotasRedirectClient(config);

var checkout = CreateCheckoutRequest.builder()
    .amountInCents(150000)
    .email("comprador@example.com")
    .orderReferenceId("PEDIDO-123")
    .phoneNumber("1144440000")
    .urlSuccess("https://mitienda.example/pago/ok")
    .urlFailure("https://mitienda.example/pago/error")
    .webhookUrl("https://mitienda.example/webhooks/gocuotas")
    .build();

var response = client.createCheckout(checkout);
String urlDePago = response.getUrlInit();

var ordenes = client.listarOrdenes();
var unaOrden = client.buscarOrden("80001001");
var trasReembolso = client.reembolsarOrden("80001001");
```

### Base URL personalizada (Redirect)

```java
import java.net.URI;
import java.time.Duration;

var config = new GoCuotasRedirectConfig(
    URI.create("https://www.gocuotas.com"),
    Duration.ofSeconds(60));
```

---

## API Client V1 (`gocuotas-client`)

Cliente para **`GET /api_client/v1/client`**, listado y detalle JSON de **`expense_settlements`**, export CSV resumen **`expense_settlements_csvs`** y CSV detallado por id **`expense_settlements_csvs/{id}`** (`Accept: text/plain`), siempre con **API key de comercio** en `Authorization: Bearer`. No usa el flujo JWT de Redirect V1.

### Variable de entorno

| Variable | Obligatoriedad | Uso |
|----------|----------------|-----|
| `GOCUOTAS_COMMERCE_API_KEY` | Sí para métodos sin clave en la firma | API key de comercio; se envía como `Authorization: Bearer …`. |
| `GOCUOTAS_LIQUIDACION_ID` | Sí para `obtenerInformacionLiquidacion()` y `obtenerLiquidacionTextoPlano()` sin id en la firma | Id en `expense_settlements/{id}` (JSON) o `expense_settlements_csvs/{id}` (texto plano). |

### Código de ejemplo (Client V1)

```bash
export GOCUOTAS_COMMERCE_API_KEY="tu_api_key_de_comercio"
```

```java
import io.humen.gocuotas.client.GoCuotasClientV1;
import io.humen.gocuotas.client.GoCuotasClientV1Config;

var config = GoCuotasClientV1Config.defaultConfig();
var client = new GoCuotasClientV1(config);

// Lee GOCUOTAS_COMMERCE_API_KEY del entorno
var comercio = client.obtenerInformacionComercio();
System.out.println(comercio.getName() + " — CUIT " + comercio.getCuit());

var liquidaciones = client.listarLiquidaciones();
for (var liq : liquidaciones) {
    System.out.println(liq.getId() + " " + liq.getPaymentExpenseMethod() + " " + liq.getPaymentExpenseAmountInCents());
}

var detalle = client.obtenerInformacionLiquidacion("9001001");
// o: client.obtenerInformacionLiquidacion(); // GOCUOTAS_COMMERCE_API_KEY + GOCUOTAS_LIQUIDACION_ID en entorno
System.out.println(detalle.getDetails().get(0).getDescription());

String csvLiquidaciones = client.obtenerInformacionLiquidacionesTextoPlano();
System.out.print(csvLiquidaciones);

String csvUnaLiquidacion = client.obtenerLiquidacionTextoPlano("9001001");
// o: client.obtenerLiquidacionTextoPlano(); // GOCUOTAS_COMMERCE_API_KEY + GOCUOTAS_LIQUIDACION_ID
System.out.print(csvUnaLiquidacion);

// O con la clave explícita:
// var comercio = client.obtenerInformacionComercio("tu_api_key_de_comercio");
// var liquidaciones = client.listarLiquidaciones("tu_api_key_de_comercio");
// var detalle = client.obtenerInformacionLiquidacion("tu_api_key", "9001001");
// var csv = client.obtenerInformacionLiquidacionesTextoPlano("tu_api_key");
// var csvDet = client.obtenerLiquidacionTextoPlano("tu_api_key", "9001001");
```

Equivalente con `curl` (reemplazá la clave):

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_client/v1/client \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer API_KEY_COMERCE'
```

Respuesta típica (JSON):

```json
{
  "id": 1000001,
  "name": "Comercio de ejemplo S.R.L.",
  "cuit": "20987654321",
  "surcharge_percentage_to_online_orders": "0.0",
  "max_number_of_installments": 3
}
```

El modelo del comercio es `io.humen.gocuotas.client.model.ComercioResponse`. Cada ítem del listado es `Liquidacion`. El detalle por id es `InformacionLiquidacion` (incluye `details` como `List<LiquidacionDetalleItem>`; las colecciones `normal_retention_retain_paid_orders` y `tax_retention_retain_paid_orders` son `List<JsonNode>` para tolerar `{}` u objetos extensibles).

### Liquidaciones (`GET /api_client/v1/expense_settlements`)

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_client/v1/expense_settlements \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer GOCUOTAS_COMMERCE_API_KEY'
```

Respuesta típica (array JSON):

```json
[
  {
    "id": 9001001,
    "payment_expense_method": "transferencia",
    "payment_expense_at": "2026-05-12",
    "due_expense_at": "2026-05-12",
    "payment_expense_retained_amount_in_cents": 0,
    "payment_expense_amount_in_cents": 2274953
  }
]
```

### Una liquidación (`GET /api_client/v1/expense_settlements/{id}`)

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_client/v1/expense_settlements/{id} \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer GOCUOTAS_COMMERCE_API_KEY'
```

Respuesta típica (forma general según API Client V1):

```json
{
  "id": 0,
  "payment_expense_method": "string",
  "payment_expense_at": "string",
  "due_expense_at": "string",
  "payment_expense_retained_amount_in_cents": 0,
  "payment_expense_amount_in_cents": 0,
  "details": [
    {
      "id": 0,
      "description": "string",
      "delivered_at": "string",
      "due_expense_at": "string",
      "amount_in_cents": 0,
      "commission_amount_in_cents": 0,
      "tax_amount_in_cents": 0,
      "expense_amount_in_cents": 0,
      "discarded_at": "string",
      "status": "string",
      "number_of_installments": 0,
      "order_reference_id": "string",
      "payment": {
        "card": {
          "number": "string",
          "name": "string"
        }
      }
    }
  ],
  "normal_retention_retain_paid_orders": [{}],
  "tax_retention_retain_paid_orders": [{}]
}
```

### Listado de liquidaciones en texto plano (`GET /api_client/v1/expense_settlements_csvs`)

Misma API key de comercio; la API devuelve cuerpo **`text/plain`** (resumen CSV: encabezado corto y filas).

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_client/v1/expense_settlements_csvs \
  --header 'Accept: text/plain' \
  --header 'Authorization: Bearer GOCUOTAS_COMMERCE_API_KEY'
```

Respuesta típica (resumen):

```text
ID,Método de Pago,Fecha de Pago,Fecha de Vencimiento,Monto Retenido,Monto Total
9001001,transferencia,12/05/2026,12/05/2026,0.0,22749.53
```

En Java: `obtenerInformacionLiquidacionesTextoPlano()` o `obtenerInformacionLiquidacionesTextoPlano(String commerceApiKey)`.

### Una liquidación en texto plano (`GET /api_client/v1/expense_settlements_csvs/{id}`)

Detalle ampliado en CSV (`Accept: text/plain`).

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_client/v1/expense_settlements_csvs/9001001 \
  --header 'Accept: text/plain' \
  --header 'Authorization: Bearer GOCUOTAS_COMMERCE_API_KEY'
```

Respuesta típica (fragmento):

```text
Descripcion,Fecha Origen,Fecha Pago,Número de Orden,Descripción,Comprobante,ApellidoNombre,Plan,Cuotas,Moneda,Importe,Comisiones,IVA sobre Comisiones,Retenciones (Impuestos),Total ventas,Total órdenes a retener,Total a cobrar,Sucursal ID,Sucursal Nombre,Sucursal Dirección,Referencia Externa,Fecha de Devolución
Ventas,30/01/2026,12/05/2026,80001002,GOcuotas,80001002,Cliente Ejemplo,0,3,pesos,23000.0,207.0,43.47,0.0,22749.53,-,22749.53,50001,Comercio de ejemplo S.R.L.,Calle Ficticia 1000,,-
Totales,"","","","","","","","","",23000.0,207.0,43.47,0.0,22749.53,0.0,22749.53
```

En Java: `obtenerLiquidacionTextoPlano(String liquidacionId)`, `obtenerLiquidacionTextoPlano(String commerceApiKey, String liquidacionId)` o `obtenerLiquidacionTextoPlano()` con `GOCUOTAS_LIQUIDACION_ID` y `GOCUOTAS_COMMERCE_API_KEY`.

---

## Makefile

En `java/`, con `jq` en los objetivos que devuelven JSON (p. ej. Redirect y listados Client). **`liquidaciones-texto-plano`** e **`informacion-liquidacion-texto-plano`** no usan `jq` (imprimen texto plano). **Client V1:** `GOCUOTAS_COMMERCE_API_KEY`; para JSON o CSV por id también **`GOCUOTAS_LIQUIDACION_ID`** (`informacion-liquidacion`, `informacion-liquidacion-texto-plano`, `obtenerLiquidacionTextoPlano()` / `obtenerInformacionLiquidacion()`). Opcional: `GOCUOTAS_API_URL`.

Objetivos en vivo contra la API real: `reembolsar-orden` ejecuta un reembolso si las credenciales son válidas.

Por **defecto** el rango de entrega de `listar-ordenes` es **los últimos 30d** (`GOCUOTAS_DELIVERED_RANGE`, formato `Nd`), calculado con GNU `date`.

### Redirect — listar órdenes

```bash
make listar-ordenes GOCUOTAS_DELIVERED_RANGE=60d
```

Respuesta típica (fragmento):

```json
[
  {
    "id": 80001001,
    "amount_in_cents": 1600000,
    "status": "approved",
    "delivered_at": "2026-04-18T12:55:17.368-03:00",
    "discarded_at": null,
    "number_of_installments": 3,
    "order_reference_id": null,
    "payment": {
      "card": {
        "number": "406651******6008",
        "name": "Visa Débito"
      }
    }
  }
]
```

### Redirect — buscar / reembolsar

```bash
make buscar-orden GOCUOTAS_ORDER_ID=80001001
make reembolsar-orden GOCUOTAS_ORDER_ID=80001001
```

`curl` de referencia para detalle:

```bash
curl --request GET \
  --url https://www.gocuotas.com/api_redirect/v1/orders/{id} \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer 123'
```

### Client V1 — información del comercio y liquidaciones

```bash
make informacion-comercio GOCUOTAS_COMMERCE_API_KEY=tu_clave
make listar-liquidaciones GOCUOTAS_COMMERCE_API_KEY=tu_clave
make informacion-liquidacion GOCUOTAS_LIQUIDACION_ID=9001001 GOCUOTAS_COMMERCE_API_KEY=tu_clave
make liquidaciones-texto-plano GOCUOTAS_COMMERCE_API_KEY=tu_clave
make informacion-liquidacion-texto-plano GOCUOTAS_LIQUIDACION_ID=9001001 GOCUOTAS_COMMERCE_API_KEY=tu_clave
```

---

## Gradle

Desde `java/`:

```bash
./gradlew build
./gradlew test
```

Dependencias en tu proyecto (publicá los JAR o usá `maven-publish` / `includeBuild`):

```kotlin
dependencies {
    implementation("io.humen.gocuotas:gocuotas-redirect:1.0.0-SNAPSHOT")
    implementation("io.humen.gocuotas:gocuotas-client:1.0.0-SNAPSHOT")
}
```

No hace falta declarar `gocuotas-api-common` si ya traés redirect o client (viene como dependencia transitiva).

---

## Maven

```bash
mvn -f pom.xml test
mvn -f pom.xml package
```

Dependencias:

```xml
<dependency>
    <groupId>io.humen.gocuotas</groupId>
    <artifactId>gocuotas-redirect</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.humen.gocuotas</groupId>
    <artifactId>gocuotas-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Artefactos

| `artifactId` | Paquete API principal |
|--------------|------------------------|
| `gocuotas-api-common` | `io.humen.gocuotas.api` |
| `gocuotas-redirect` | `io.humen.gocuotas.redirect` |
| `gocuotas-client` | `io.humen.gocuotas.client` |

`groupId` común: `io.humen.gocuotas`.

## Licencia

Uso interno del monorepo; ajustá la licencia según tu organización.
