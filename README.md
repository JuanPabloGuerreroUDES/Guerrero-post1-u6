# Post-contenido — Unidad 6: Antipatrones de Diseño

**Estudiante:** Juan Pablo Guerrero Hernandez
**Asignatura:** Patrones de Diseño de Software

## Descripción
Proyecto Spring Boot (`pedidos-service`) de un sistema de gestión de pedidos de un comercio
electrónico. La idea es diagnosticar los antipatrones de la clase `GestorPedidos` con evidencia
del propio código y luego refactorizarla sin cambiar el comportamiento que ve el usuario.

## Diagnóstico — Parte 1: `GestorPedidos`

> Los números de línea corresponden a
> [`GestorPedidos.java` antes de refactorizar](src/main/java/com/tienda/pedidos/service/GestorPedidos.java)
> (commit `fix: rechazar el pedido cuando el cliente no existe`).

### Antipatrones identificados: God Object + Spaghetti Code

#### 1. God Object: una sola clase con siete razones para cambiar

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

#### 2. Spaghetti Code: flujo enredado y niveles de abstracción mezclados

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

#### 3. ¿Cuánto cuesta agregar un tipo de cliente nuevo?

Si mercadeo pide, por ejemplo, un cliente `EMPLEADO` con 20%, hay que agregar otra rama
`else if` entre las líneas 87 y 95, en medio de un método que también valida, guarda y envía
correos. Si además ese tipo tuviera alguna validación propia, también habría que tocar el bloque
de las líneas 51–67. No hay forma de agregar la regla sin modificar código existente (se viola
Open/Closed) ni de probar el descuento aislado sin levantar la base de datos completa.

#### Hallazgo extra al escribir las pruebas

La comprobación `if (tipoCliente == null)` del código original **nunca se ejecutaba**:
`jdbcTemplate.queryForObject(...)` lanza `EmptyResultDataAccessException` cuando no encuentra la
fila, así que con un cliente inexistente el método explotaba en vez de responder
"Cliente no registrado". Lo detectó la prueba `clienteInexistente` y se corrigió en un commit
aparte **antes** de refactorizar (solo esa consulta), para que la línea base de comparación ya
tuviera el comportamiento esperado.
