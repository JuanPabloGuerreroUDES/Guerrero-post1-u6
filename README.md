# Post-contenido — Unidad 6: Antipatrones de Diseño

**Estudiante:** Juan Pablo Guerrero Hernandez
**Asignatura:** Patrones de Diseño de Software

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software. Es un único
proyecto Spring Boot (`pedidos-service`, en la raíz del repositorio) de un sistema de gestión de
pedidos de un comercio electrónico, trabajado en dos partes:

1. **Parte 1:** diagnosticar y refactorizar `GestorPedidos`, que combinaba God Object y
   Spaghetti Code en un solo método.
2. **Parte 2:** diagnosticar y corregir un segundo antipatrón (Golden Hammer) que apareció al
   agregar tres campañas de descuento al mismo proyecto.

En las dos partes se escribieron primero las pruebas sobre el código "malo" y después se
refactorizó sin tocarlas, para poder demostrar que el comportamiento no cambió.

### Estructura final

```
src/main/java/com/tienda/pedidos/
├── PedidosServiceApplication.java
├── controller/   PedidoController                (POST /api/pedidos)
├── dto/          PedidoRequest, ItemPedido, ResultadoPedido
├── service/      GestorPedidos                   (orquestador)
│                 NotificacionPedidoService, EmailService, ConsolaEmailService
├── validacion/   ContextoPedido, ValidadorPedido (Chain of Responsibility)
│                 ValidadorStock, ValidadorCliente
├── descuento/    EstrategiaDescuento             (Strategy)
│                 DescuentoVip, DescuentoFrecuente, DescuentoEstandar, SelectorEstrategiaDescuento
│                 CampanaDescuento, DescuentoBlackFriday, DescuentoCorporativo, DescuentoVolumen
│                 CalculadorDescuentoFinal
└── repository/   PedidoRepository, ClienteRepository, ProductoRepository
```

## Decisiones de diseño

### Parte 1 — `GestorPedidos`

> Los números de línea corresponden a `GestorPedidos.java` **antes de refactorizar**. Esa versión
> se puede ver con:
> ```
> git show a63cc73:src/main/java/com/tienda/pedidos/service/GestorPedidos.java
> ```

#### Antipatrones identificados: God Object + Spaghetti Code

##### 1. God Object: una sola clase con siete razones para cambiar

`procesarPedido()` ocupa las líneas **28 a 135 (108 líneas)** y es el único método público de la
clase. Dentro de él conviven estas responsabilidades:

| # | Responsabilidad | Líneas | Qué la haría cambiar |
|---|---|---|---|
| 1 | Validar que haya items y stock suficiente | 31–44 | Reglas de inventario (reservas, bodegas) |
| 2 | Validar cliente y mora, con la excepción de horario | 46–67 | Política de cartera o el horario de corte |
| 3 | Calcular el subtotal consultando precios | 69–75 | Cambio en cómo se obtienen los precios |
| 4 | Calcular el descuento según el tipo de cliente | 77–95 | Mercadeo pide un tipo de cliente nuevo |
| 5 | Calcular impuesto y total | 97–98 | Cambio en el IVA |
| 6 | Persistir pedido, detalle y descontar inventario | 100–115 | Cambio en el modelo de datos |
| 7 | Armar y enviar el correo de confirmación | 117–131 | Cambio en el texto o el canal de notificación |

Además, el **registro (logging)** está repartido en 8 líneas distintas (29, 33, 41, 52, 61, 64,
129 y 133), así que tampoco tiene un lugar propio.

La clase depende al mismo tiempo de `JdbcTemplate` y de `EmailService` (líneas 21–25) y ejecuta
**9 sentencias SQL sobre 6 tablas** (`inventario`, `clientes`, `facturas`, `productos`, `pedidos`,
`detalle_pedido`). Es decir, sabe de inventario, de cartera, de catálogo, de facturación y de
correo. Esa concentración de conocimiento es lo que define un God Object: cualquier cambio en
cualquiera de esas áreas obliga a abrir y releer este mismo método.

##### 2. Spaghetti Code: flujo enredado y niveles de abstracción mezclados

- **Anidamiento:** la validación de mora llega a **3 niveles de condicionales** dentro del método
  (`else if MOROSO` en la línea 54 → `if deudaPendiente > 0` en la 58 → `if ahora.isBefore(20:00)`
  en la 60). El bloque de descuento llega a 2 niveles, pero con **5 ramas** de valores fijos
  (0.15, 0.10, 0.05, 0.08, 0.04) mezcladas con una consulta SQL en medio (línea 88).
- **Cinco puntos de salida:** el método retorna en las líneas 34, 42, 53, 62 y 134. Para saber si
  un pedido llega a guardarse hay que seguir todo el recorrido mentalmente.
- **Niveles de abstracción mezclados en la misma secuencia de líneas:** SQL embebido
  (`"SELECT stock FROM inventario..."`, línea 38), reglas de negocio (`if (subtotal > 1_000_000)`,
  línea 80) y formato de texto del correo (`cuerpo.append("Subtotal: $")`, línea 121) aparecen uno
  tras otro sin ninguna separación.
- **Acoplamiento escondido por variables locales:** el cálculo de descuento (línea 79) usa
  `tipoCliente`, una variable que se leyó 30 líneas antes durante la *validación* (línea 48). La
  validación y el cálculo de precio no se pueden separar sin desenredar esa dependencia.
- **"String typing":** el tipo de cliente se compara con literales en tres lugares diferentes
  (`"MOROSO"` línea 54, `"VIP"` línea 79, `"FRECUENTE"` línea 87).
- **Sin transacción:** las escrituras de las líneas 101–114 (insertar pedido, insertar detalle,
  descontar stock) no están agrupadas. Si falla la actualización de inventario del segundo item,
  el pedido queda guardado a medias.

##### 3. ¿Cuánto cuesta agregar un tipo de cliente nuevo?

Si mercadeo pide, por ejemplo, un cliente `EMPLEADO` con 20%, hay que agregar otra rama
`else if` entre las líneas 87 y 95, en medio de un método que también valida, guarda y envía
correos. Si además ese tipo tuviera alguna validación propia, también habría que tocar el bloque
de las líneas 51–67. No hay forma de agregar la regla sin modificar código existente (se viola
Open/Closed) ni de probar el descuento aislado sin levantar la base de datos completa.

##### Hallazgo extra al escribir las pruebas

La comprobación `if (tipoCliente == null)` del código original **nunca se ejecutaba**:
`jdbcTemplate.queryForObject(...)` lanza `EmptyResultDataAccessException` cuando no encuentra la
fila, así que con un cliente inexistente el método explotaba en vez de responder
"Cliente no registrado". Lo detectó la prueba `clienteInexistente` y se corrigió en un commit
aparte **antes** de refactorizar (solo esa consulta), para que la línea base de comparación ya
tuviera el comportamiento esperado.

#### Patrones aplicados

El método se dividió en cuatro capas, cada una con una sola razón para cambiar:

```
GestorPedidos (orquestador, ~70 líneas en total)
 ├── validacion/   ValidadorStock ──► ValidadorCliente      (Chain of Responsibility)
 ├── descuento/    SelectorEstrategiaDescuento
 │                   ├── DescuentoVip
 │                   ├── DescuentoFrecuente                 (Strategy)
 │                   └── DescuentoEstandar
 ├── repository/   PedidoRepository, ClienteRepository, ProductoRepository
 └── service/      NotificacionPedidoService ──► EmailService
```

| Responsabilidad original (líneas) | Ahora vive en |
|---|---|
| Items y stock (31–44) | `ValidadorStock` |
| Cliente y mora (46–67) | `ValidadorCliente` |
| Subtotal (69–75) | `GestorPedidos.calcularSubtotal()` + `ProductoRepository.obtenerPrecio()` |
| Descuento (77–95) | `EstrategiaDescuento` + `SelectorEstrategiaDescuento` |
| Persistencia (100–115) | `PedidoRepository.guardar()` (con `@Transactional`) |
| Notificación (117–131) | `NotificacionPedidoService` |

Todo el SQL quedó en clases `@Repository`; los validadores y las estrategias solo tienen reglas
de negocio y reciben los datos ya consultados.

**Decisión 1 — Validaciones como Chain of Responsibility.**
Las validaciones tienen un orden real y necesitan corte anticipado: si `ValidadorStock` rechaza
el pedido, no tiene sentido consultar la cartera del cliente. En la cadena, cada eslabón decide
si el pedido sigue (`ValidadorPedido.validar()` solo llama al siguiente si nadie lo ha rechazado).

*Alternativa descartada:* un método `validarTodo()` con una `List<Predicate<ContextoPedido>>`.
Se descartó porque evalúa todos los predicados aunque el primero ya haya fallado (o hay que
escribir el corte a mano con un `for` y un `break`, que es volver al código de antes) y porque un
predicado solo devuelve `true/false`: no puede dejar un motivo de rechazo ni guardar el
`tipoCliente` en el contexto para el cálculo del descuento.

**Decisión 2 — Descuento como Strategy y no como otro eslabón de la cadena.**
Para el descuento siempre aplica *exactamente una* regla, determinada por el tipo de cliente, y
el orden no importa. Eso es lo que resuelve Strategy: `SelectorEstrategiaDescuento` tiene un
`Map<String, EstrategiaDescuento>` y elige la regla en una sola línea, sin `if/else`.
Agregar un tipo de cliente nuevo ahora es crear una clase y registrarla en el mapa, sin tocar
las otras reglas ni el orquestador.

*Alternativa descartada:* meter el descuento como un eslabón más de la cadena. Habría que
inventar un mecanismo para que solo un eslabón escriba el descuento y los demás lo ignoren, que
es justo lo que la cadena no está pensada para hacer. También se descartó dejar un `switch` sobre
el tipo de cliente dentro de `GestorPedidos`: es más corto, pero mantiene el problema de que cada
regla nueva obliga a modificar el mismo método.

**Decisión 3 — Persistencia y notificación fuera del gestor.**
`PedidoRepository` agrupa los tres `INSERT/UPDATE` en una transacción (`@Transactional`), cosa
que antes no existía. `NotificacionPedidoService` arma el texto del correo y captura el error de
envío igual que el original, para que un fallo de correo no deshaga un pedido ya guardado.

#### Comparación antes / después

Las 11 pruebas de [`GestorPedidosTest`](src/test/java/com/tienda/pedidos/service/GestorPedidosTest.java)
se escribieron contra el código original (commit `701d715`) y **no se modificaron** al
refactorizar. Resultados en ambas versiones:

| Caso de prueba | Cliente | Resultado original | Resultado refactorizado |
|---|---|---|---|
| Pedido sin items | 4 ESTANDAR | Rechazado: "El pedido no contiene items" | Igual |
| 5 monitores (hay 2) | 4 ESTANDAR | Rechazado: "Stock insuficiente: producto 3" | Igual |
| Cliente 99 | no existe | Rechazado: "Cliente no registrado" * | Igual |
| Moroso a las 10:00 | 3 MOROSO | Rechazado: "Cliente con deuda pendiente: $150000.0" | Igual |
| Moroso a las 21:30 | 3 MOROSO | Confirmado, total $59.500 (sin descuento) | Igual |
| 1 portátil ($1.200.000) | 1 VIP | Confirmado, 15%, total $1.213.800 | Igual |
| Monitor + 3 mouse ($550.000) | 1 VIP | Confirmado, 10%, total $589.050 | Igual |
| 2 mouse ($100.000) | 1 VIP | Confirmado, 5%, total $113.050 | Igual |
| 2 mouse, 12 pedidos previos | 2 FRECUENTE | Confirmado, 8%, total $109.480 | Igual |
| 2 mouse, 5 pedidos previos | 5 FRECUENTE | Confirmado, 4%, total $114.240 | Igual |
| 1 mouse | 4 ESTANDAR | Confirmado, 0%, total $59.500 | Igual |

\* Después del commit `fix`, ver el hallazgo de arriba.

Además de los totales, las pruebas verifican el stock que queda en inventario, el descuento
guardado en la tabla `pedidos` y el texto exacto del correo (asunto y cuerpo). La hora del
cliente moroso se controla en las pruebas con `Mockito.mockStatic(LocalTime.class)`.

Salida de `mvn test` en las dos versiones:

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Parte 2 — El sistema crece: tres campañas de descuento

Mercadeo pidió tres campañas: **BLACK_FRIDAY** (25% mientras esté activa), **CORPORATIVO** (10%
si el cliente tiene NIT) y **VOLUMEN** (12% si el pedido supera 20 unidades). Se resolvieron
agregando `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen` como tres eslabones
más de la cadena de validación. Funciona (las 20 pruebas pasan), pero el diseño tiene un problema.

> Los números de línea corresponden al commit `4bfa885`, antes de la corrección:
> ```
> git show 4bfa885:src/main/java/com/tienda/pedidos/service/GestorPedidos.java
> ```

#### Antipatrón identificado: Golden Hammer

En la Parte 1, Chain of Responsibility funcionó bien para `ValidadorStock` y `ValidadorCliente`.
Para las campañas se usó otra vez **porque ya estaba ahí y había funcionado**, sin revisar si el
problema nuevo tenía la misma forma. Esta es la evidencia de que no la tenía:

**1. No hay dependencia de orden entre las campañas.**
`ContextoPedido.aplicarDescuentoCampana()` (líneas 33–34) se queda con el mayor valor recibido.
Tomar el máximo es conmutativo, así que encadenar `blackFriday → corporativo → volumen`
(`GestorPedidos`, línea 42) da exactamente lo mismo que `volumen → blackFriday → corporativo`.
En cambio, `ValidadorStock` **sí** tiene que ir antes que `ValidadorCliente`: si no hay stock no
tiene sentido consultar la cartera. El orden, que es lo que justificaba la cadena, aquí no aporta
nada.

**2. Nunca hay corte anticipado.**
`grep -rn "rechazar(" validacion/` devuelve 4 llamadas, todas en `ValidadorStock` (líneas 28 y 34)
y `ValidadorCliente` (líneas 31 y 48). Ninguno de los tres eslabones nuevos rechaza nada, y el
propio código lo admite: `PromocionBlackFriday`, línea 18:
*"nunca rechaza -- este eslabon no valida nada"*. Son clases que heredan de `ValidadorPedido`
cuyo contrato es "decidir si el pedido continúa o se rechaza", pero no validan: se aprovechan de
que la cadena ya recorre el pedido para escribir en un campo compartido. Eso rompe el principio
de sustitución de Liskov: un `ValidadorPedido` que no valida.

**3. La única dependencia de orden que existe es accidental.**
Las campañas solo funcionan si van *después* de `ValidadorCliente`. Si alguien pusiera
`PromocionCorporativo` primero, el `queryForObject` de la línea 14 lanzaría
`EmptyResultDataAccessException` con un cliente inexistente (el mismo bug que se corrigió en la
Parte 1). No es una regla de negocio, es una casualidad de la implementación.

**4. La regla del descuento quedó repartida en tres lugares.**
Para saber qué descuento recibe un pedido ahora hay que leer:
`SelectorEstrategiaDescuento` (tipo de cliente), `ContextoPedido.aplicarDescuentoCampana()`
(línea 34, "el mayor descuento gana" entre campañas) y `GestorPedidos`, línea 63
(`Math.max(descuentoTipoCliente, contexto.getDescuentoCampana())`). Si mercadeo pidiera que dos
campañas se **sumen** en vez de competir, habría que cambiar `ContextoPedido`, una clase que
también usan los validadores, y no hay forma de expresarlo en la cadena sin ambigüedad: cada
eslabón solo ve el valor que dejó el anterior, no sabe de qué campaña vino.

**5. Las campañas corren antes de conocer el subtotal.**
La cadena se ejecuta en la línea 54 y el subtotal se calcula después, en la línea 59. Una campaña
del tipo "10% en compras mayores a $1.000.000" **no se podría implementar** como eslabón. Por eso
`PromocionVolumen` tiene que recalcular las unidades por su cuenta desde el request (línea 10, y
su comentario de la línea 4 lo dice).

**6. El gestor vuelve a crecer con cada campaña.**
El constructor de `GestorPedidos` pasó de 6 a **9 parámetros** (líneas 36–40) y cada campaña nueva
obliga a modificarlo para engancharla en la cadena (línea 42): otra vez se viola Open/Closed, que
era justo lo que la Parte 1 había arreglado.

**Conclusión del diagnóstico:** las tres campañas tienen la misma forma que `DescuentoVip` y
`DescuentoFrecuente` (calculan un porcentaje a partir del pedido o del cliente, sin orden y sin
cortar el flujo). El problema era de Strategy, no de Chain of Responsibility.

#### Patrón aplicado: extender el Strategy que ya existía

```
GestorPedidos
 ├── ValidadorStock ──► ValidadorCliente          (la cadena vuelve a tener solo 2 eslabones)
 └── CalculadorDescuentoFinal
       ├── SelectorEstrategiaDescuento ──► DescuentoVip | DescuentoFrecuente | DescuentoEstandar
       └── List<CampanaDescuento>       ──► DescuentoBlackFriday, DescuentoCorporativo, DescuentoVolumen
                                             descuento final = max(tipo de cliente, mejor campaña)
```

- `DescuentoBlackFriday`, `DescuentoCorporativo` y `DescuentoVolumen` implementan
  `CampanaDescuento`, que extiende `EstrategiaDescuento`. Son estrategias igual que `DescuentoVip`:
  reciben el contexto y devuelven un porcentaje.
- `CalculadorDescuentoFinal` es ahora el **único** lugar donde está la regla
  "gana el mayor descuento". `GestorPedidos` solo llama a `calculadorDescuento.calcular(contexto)`.
- Como el cálculo corre **después** del subtotal, una campaña futura por monto ya se puede hacer.
- `DescuentoCorporativo` consulta el NIT a través de `ClienteRepository.buscarNit()`, igual que
  el resto del proyecto, en vez de usar `JdbcTemplate` directo.

**Decisión 1 — Strategy en vez de más eslabones de cadena.**
Las campañas calculan un porcentaje sin depender del orden y sin necesidad de cortar el flujo
del pedido, igual que `DescuentoVip` y `DescuentoFrecuente`. La propiedad que justifica la cadena
(orden + corte anticipado) solo la tienen `ValidadorStock` y `ValidadorCliente`.

*Alternativa descartada:* dejarlas en la cadena. Es la causa del antipatrón diagnosticado:
reutilizar una herramienta conocida sin comprobar que el problema nuevo tenía su misma forma.

*Otra alternativa considerada:* agregar las campañas al `Map` de `SelectorEstrategiaDescuento`.
Se descartó porque el selector elige **una** estrategia según el tipo de cliente, y las campañas
no dependen del tipo de cliente: un cliente VIP también puede tener NIT o comprar más de 20
unidades. Por eso se evalúan *junto con* la selección por tipo de cliente, no *dentro* de ella.

**Decisión 2 — Interfaz `CampanaDescuento` e inyección de la lista.**
En vez de recibir las tres campañas una por una en el constructor, `CalculadorDescuentoFinal`
recibe `List<CampanaDescuento>` y Spring inyecta todas las que existan. Así, una cuarta campaña
es una clase nueva con `@Component` y nada más: no hay que tocar el calculador ni el gestor, que
era el problema 6 del diagnóstico.

**Decisión 3 — Eliminar, no comentar, el código descartado.**
`PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen` y el campo `descuentoCampana`
se borraron por completo (`git rm`). Dejarlos comentados "por si acaso" es como nace un
**Lava Flow**: después nadie se atreve a borrarlos porque no está claro si todavía sirven. Si se
necesita consultarlos, están en el historial de Git (commit `4bfa885`).

#### Comparación antes / después

Las pruebas de [`CampanasDescuentoTest`](src/test/java/com/tienda/pedidos/service/CampanasDescuentoTest.java)
y [`BlackFridayTest`](src/test/java/com/tienda/pedidos/service/BlackFridayTest.java) se escribieron
con la versión de los eslabones (commit `4bfa885`) y **no se modificaron** en la corrección.
`BlackFridayTest` levanta la aplicación con `promo.black-friday.activa=true`.

| Caso de prueba | Cliente | Con eslabones (Golden Hammer) | Con `CalculadorDescuentoFinal` |
|---|---|---|---|
| 1 mouse, NIT registrado | 6 ESTANDAR + NIT | Corporativo 10%, total $53.550 | Igual |
| 25 cables ($125.000) | 4 ESTANDAR | Volumen 12%, total $130.900 | Igual |
| 20 unidades exactas ($325.000) | 4 ESTANDAR | Sin descuento, total $386.750 | Igual |
| 25 cables | 1 VIP (5%) | Gana volumen 12%, total $130.900 | Igual |
| Portátil + 21 cables ($1.305.000) | 1 VIP (15%) | Gana VIP 15%, total $1.320.007,5 | Igual |
| 25 cables | 6 corporativo (10%) | Gana volumen 12%, total $130.900 | Igual |
| 1 mouse, Black Friday activo | 4 ESTANDAR | 25%, total $44.625 | Igual |
| Portátil, Black Friday activo | 1 VIP (15%) | Gana Black Friday 25%, total $1.071.000 | Igual |
| 5 monitores, Black Friday activo | 4 ESTANDAR | Rechazado: "Stock insuficiente: producto 3" | Igual |
| Los 11 casos de la Parte 1 | — | Mismos resultados de la tabla anterior | Igual |

Salida de `mvn test` en las dos versiones:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.tienda.pedidos.service.BlackFridayTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.tienda.pedidos.service.CampanasDescuentoTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in com.tienda.pedidos.service.GestorPedidosTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Historial de commits

El historial refleja el proceso y no solo el resultado (`git log --oneline`):

| Parte | Commit | Qué se hizo |
|---|---|---|
| 1 | `eb8d9b7` | Proyecto base con H2 y datos de ejemplo |
| 1 | `3c38ed7` | `GestorPedidos` original copiado sin cambios |
| 1 | `701d715` | Pruebas sobre el código original (línea base) |
| 1 | `a63cc73` | Arreglo del caso "cliente no registrado" encontrado por las pruebas |
| 1 | `757a586` | Diagnóstico God Object + Spaghetti Code en el README |
| 1 | `ca1feaf` | Cadena de validación y estrategias de descuento |
| 1 | `8d2d823` | `GestorPedidos` como orquestador, repositorio y notificación aparte |
| 1 | `9d48287` | Endpoint REST para probar con `spring-boot:run` |
| 1 | `11d744f` | Decisiones de diseño y comparación de la Parte 1 |
| 2 | `4bfa885` | Campañas agregadas como eslabones (versión con Golden Hammer) |
| 2 | `5078368` | Diagnóstico Golden Hammer en el README |
| 2 | `7e6ed7e` | Campañas movidas a `EstrategiaDescuento`, eslabones eliminados |
| 2 | — | README final con ambas partes y conclusiones |

## Cómo ejecutar

```
mvn test
mvn spring-boot:run
```

Con la aplicación arriba se pueden enviar pedidos a `POST http://localhost:8080/api/pedidos`.
El correo de confirmación se imprime en la consola. Ejemplo:

```
curl -X POST http://localhost:8080/api/pedidos -H "Content-Type: application/json" \
     -d '{"clienteId":1,"clienteEmail":"laura.rincon@correo.com","items":[{"productoId":1,"cantidad":1}]}'

{"confirmado":true,"pedidoId":18,"total":1213800.0,"motivoRechazo":null}
```

Para activar Black Friday se cambia `promo.black-friday.activa=true` en
`src/main/resources/application.properties`, o al arrancar:

```
mvn spring-boot:run -Dspring-boot.run.arguments=--promo.black-friday.activa=true
```

Datos de ejemplo (en `src/main/resources/data.sql`):

| Cliente | Tipo |
|---|---|
| 1 Laura Rincón | VIP |
| 2 Andrés Pabón | FRECUENTE (12 pedidos previos) |
| 3 Carlos Duarte | MOROSO (debe $150.000) |
| 4 Sofía Mantilla | ESTANDAR |
| 5 Diego Serrano | FRECUENTE (5 pedidos previos) |
| 6 Distribuidora Andina SAS | ESTANDAR con NIT 900123456-7 |

| Producto | Precio | Stock |
|---|---|---|
| 1 Portátil | $1.200.000 | 10 |
| 2 Mouse inalámbrico | $50.000 | 100 |
| 3 Monitor 27" | $400.000 | 2 |
| 4 Cable USB-C | $5.000 | 200 |

La consola de H2 queda en `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:tienda`).

**Nota sobre H2:** el código original obtiene el id del pedido con `CALL IDENTITY()`, que en
H2 2.x solo existe en modo de compatibilidad, por eso la URL usa `MODE=LEGACY`. Se mantuvo así
para no cambiar el código de partida.

## Herramientas utilizadas
- Java 17, Spring Boot 3.3, Spring JDBC (`JdbcTemplate`), Maven, H2 Database
- JUnit 5 y Mockito (incluido `mockStatic` para controlar la hora del cliente moroso)
- VS Code, Git, GitHub

## Conclusiones
Lo más importante de la Parte 1 fue que el diagnóstico no se puede quedar en el nombre del
antipatrón: contar las responsabilidades por líneas, los niveles de anidamiento y las sentencias
SQL fue lo que mostró dónde cortar el método y qué patrón usar en cada parte. Escribir las pruebas
antes de refactorizar no solo sirvió para comparar resultados, también sacó a la luz un error
real del código original (el cliente inexistente lanzaba una excepción). La Parte 2 dejó la
lección más incómoda: el Golden Hammer apareció justamente porque Chain of Responsibility había
funcionado bien, y la pregunta que lo evita es si el problema nuevo tiene las mismas
propiedades que el anterior (orden y corte anticipado), no si el patrón "ya está hecho". Al
final, la corrección fue más simple que el error, porque el Strategy de la Parte 1 ya tenía la
forma que necesitaban las campañas, y borrar el código descartado en vez de comentarlo dejó el
proyecto sin restos que alguien tenga que adivinar después.
