# El Ahorrador por dentro

Lo común a todas las apps (lenguajes, patrón, compilar) está en [`docs/ARQUITECTURA.md`](../../docs/ARQUITECTURA.md). Aquí va lo propio de El Ahorrador.

## 1. En una frase

Un cuaderno de plata en Kotlin + Compose. Guarda todo en un JSON en el teléfono y calcula saldos, sobres, tarjetas, metas y pagos que vencen. No tiene internet.

## 2. Mapa de archivos

Código en `src/main/java/io/github/fogonapps/ahorrador/`:

| Archivo | Qué hace |
|---------|----------|
| `AhorradorApp.kt` | Arranca una sola vez por proceso: el almacén (DataStore), la `Logica`, los formatos y el canal de avisos en el idioma en uso, y programa el aviso diario. |
| `MainActivity.kt` | La única pantalla de Android. Monta el tema, el candado (huella o bloqueo del teléfono), exportar e importar con el selector de archivos, pide el permiso de avisos al crear el primer pago fijo y guarda al salir. |
| `AhorradorViewModel.kt` | El cerebro. Tiene el estado, la navegación (pestaña + pila de pantallas) y todas las acciones. Todo cambio pasa por `cambiar { }`. |
| `Categorias.kt` | Las 18 categorías con las que empieza la app: nombre, tinta, ícono y grupo. |
| `Avisos.kt` | El aviso de pagos fijos: alarma diaria a las 9:00, el `BroadcastReceiver` que la muestra y el que la vuelve a programar al reiniciar el teléfono. |
| `datos/Modelo.kt` | Las clases de datos (`Estado`, `Cuenta`, `Movimiento`, `Fijo`, `Meta`, `Deuda`, …) y la configuración del JSON (`JsonAhorrador`). |
| `datos/Almacen.kt` | Guardar el estado en disco (`SerializadorEstado`, `crearAlmacen`). |
| `datos/Logica.kt` | Todos los cálculos (ver sección 5). Sin Android: se prueba en la JVM. |
| `datos/Extracto.kt` | Leer el extracto del banco en CSV (`leerExtracto`: separador, columnas y formatos de fecha y monto que adivina) y el reto de ahorro (`montoReto`, `totalReto`). Probado en `ExtractoTest`. |
| `datos/Formato.kt` | Cómo se escriben la plata y las fechas ("$ 842.300", "21 oct") en cada idioma. |
| `ui/App.kt` | El armazón: la barra flotante con el botón de anotar, la pantalla de arriba de la pila (también Apariencia, que es `PantallaApariencia` de `core/identidad`), avisos, confirmaciones y la pantalla con candado. |
| `ui/Tutorial.kt` | Los 4 pasos del primer arranque: nombre, sistema, saldos y "¡Dale vida!" (`PasoDaleVida` de `core/identidad`) para elegir el estilo. |
| `ui/Inicio.kt` | Pestañas Inicio y Resumen, y piezas que usan varias pantallas (fila de movimiento, selector de mes). |
| `ui/Presupuesto.kt` | Pestaña Presupuesto (según el sistema), pestaña Metas, crear o editar una meta y crear o editar un sobre (`EditarSobre`). |
| `ui/Anotar.kt` | Anotar o corregir un movimiento, con su teclado. |
| `ui/Cuentas.kt` | Todas las cuentas, una cuenta (con su tarjeta de crédito) y crear o editar una cuenta. |
| `ui/Fijos.kt` | Pagos fijos, me deben y debo, y el cierre del mes. |
| `ui/Mejoras.kt` | La dona del mes y los últimos meses (Resumen), el calendario de pagos (Fijos), importar el extracto, la tarjeta del reto de ahorro (Metas) y el ajuste del recordatorio diario. |
| `ui/Perfil.kt` | Perfil y ajustes, Acerca de, Licencias, Preguntas frecuentes y Reportar. |
| `ui/Comunes.kt` | Piezas de interfaz: tarjetas de las filas (`vidrio()`, sin sombra), tarjeta destacada (`tarjetaDestacada`), fichas de categoría en una mancha de su tinta, campos de plata, selectores, fecha. |
| `ui/Iconos.kt` | Íconos de contorno de tinta y cuerpo de la segunda tinta (`Dibujo`, se pintan con `IconoRiso`), también los de las categorías (`Iconos.de(nombre)`). Los comunes salen de `IconosFogon`. |

Otros lugares:

| Lugar | Qué hay |
|-------|---------|
| `src/main/res/` | Ícono, temas, textos (`values` en inglés, `values-es` en español), ícono de la notificación y reglas de respaldo. |
| `src/test/` | `LogicaTest` (las cuentas de la plata) y `TraduccionTest` (los dos idiomas completos). |
| `fastlane/metadata/android/` | La ficha de la app para F-Droid. |

## 3. Los datos

**Todo lo de la persona** es un objeto `Estado` guardado como JSON en `files/datastore/estado.json`. La plata va en **pesos enteros** (`Long`): en Colombia no se usan centavos. Las fechas van como `"AAAA-MM-DD"` y los meses como `"AAAA-MM"`.

| Campo | Qué es |
|-------|--------|
| `v`, `updatedAt` | Versión del formato (1) y hora del último cambio. |
| `nombre`, `sistema`, `listo` | Cómo se llama la persona, cómo se organiza (`SOBRES`, `REGLA_50_30_20`, `KAKEIBO`, `SOLO_ANOTAR`) y si ya pasó el tutorial. |
| `cuentas` | Cada una con su tipo y su **saldo inicial**; el saldo de hoy se calcula con los movimientos. Las tarjetas guardan la deuda como saldo negativo y traen `credito` (cupo, día de corte y de pago, tasa mensual y % del mínimo). Archivar una cuenta no la borra. |
| `categorias` | Copiadas de `Categorias.kt` al terminar el tutorial, en el idioma de ese momento. Después son de la persona: no cambian si cambia el idioma. |
| `movimientos` | Gasto, ingreso, traspaso (con `aCuenta`) o ahorro (con `meta`). Pueden traer `cuotas` (tarjeta) o `fijo` (si los creó un pago fijo). |
| `sobres` | Mes → (categoría → plata asignada). Solo el sistema Sobres lo usa. |
| `reparto`, `metaAhorroMes`, `cierres` | El % del 50/30/20, la meta de ahorro del kakeibo y la nota de cada cierre de mes. |
| `fijos`, `metas`, `deudas` | Pagos fijos, metas de ahorro, y préstamos o créditos con sus abonos. |
| `gmf` | Si se anota el 4×1000. |

- **Si el archivo se daña,** se guarda una copia y se empieza de cero, sin cerrar la app.
- **La Apariencia** (estilo, colores, comodidad) va en las preferencias `apariencia`, la maneja `core/identidad` y no viaja en el respaldo.
- **El candado y el idioma** van en las preferencias `ui`, no en el `Estado`: no viajan en el respaldo.

## 4. Cómo fluye todo

Ejemplo: la persona anota un gasto de $ 45.000 en Mercado.

1. En `Anotar` escribe el monto con el teclado, toca la categoría y Guardar.
2. `vm.guardarMovimiento(m)` revisa que tenga monto (y otra cuenta si es un traspaso). Si el 4×1000 está activado y la plata sale de una cuenta de banco, agrega también el gasto del impuesto.
3. `cambiar { }` copia el estado con el movimiento y pone `updatedAt` en ahora. Las pantallas se redibujan, porque escuchan `vm.estado`, y 300 ms después se guarda en disco.
4. Todos los números (saldos, sobres, resumen) se calculan de nuevo a partir de los movimientos: no se guarda ningún total.

## 5. La lógica

Todo está en `datos/Logica.kt`, recibe el estado y la fecha de hoy, y no toca la pantalla ni el disco.

- **Saldo de una cuenta:** saldo inicial + ingresos − gastos − ahorros ± traspasos. En una tarjeta, la deuda es el saldo en negativo.
- **Tienes** (`patrimonio`): la suma de todas las cuentas activas, o sea lo que hay menos lo que se debe en tarjetas. Lo ahorrado en metas ya salió de las cuentas, así que no está ahí (en Cuentas se muestra aparte).
- **El mes:** entró, salió y guardaste son las sumas de ingresos, gastos y ahorros con fecha de ese mes.
- **Sobres:** cada categoría de gasto tiene lo asignado en el mes y lo gastado. **Por asignar** = lo que tienes − lo que les queda a los sobres del mes (solo lo positivo). Así, los saldos con que se empieza también se pueden repartir, lo que sobra de un mes vuelve a estar por asignar, y una compra con tarjeta no cambia nada (baja el sobre y sube la deuda). Cubrir un sobre pasado mueve asignación de otro.
- **50/30/20:** necesidades (necesidad + imprevisto), gustos (gusto + cultura) y ahorro (ahorros del mes + categorías de grupo ahorro). Lo asignado es el % de lo que entró en el mes.
- **Kakeibo:** cuatro grupos solo con lo gastado; el cierre compara lo que quedó (entró − salió) con lo que se quería ahorrar.
- **Tarjeta:** mínimo = % de la deuda, redondeado a la centena; intereses si se paga solo el mínimo = (deuda − mínimo) × tasa mensual. Las compras a cuotas cuentan una cuota pagada por cada mes que pasa desde la compra (la primera, al mes siguiente). Es una aproximación: cada banco lo calcula distinto, y la app lo dice.
- **Metas:** por quincena = lo que falta ÷ quincenas hasta la fecha, redondeado a la centena. "A este paso llegas en" usa lo abonado en los últimos 3 meses.
- **Pagos fijos:** vencen el día elegido de cada mes, o el último día si el mes es más corto (un pago del 31 cae el 28 de febrero). Están pagados si hay un movimiento del mes con su `fijo`. Los automáticos se anotan al abrir la app si ya vencieron.
- **4×1000:** 4 pesos por cada 1.000, redondeado al peso.

## 6. Funciones especiales

- **Avisos:** `AlarmManager.setInexactRepeating` a las 9:00 cada día (no necesita el permiso de alarmas exactas). El receptor lee el estado del disco y muestra los fijos que vencen en 3 días o menos (o ya vencieron). Se vuelve a programar al reiniciar el teléfono (`ArranqueTelefono`).
- **Candado:** `BiometricPrompt` del sistema con huella, cara o el bloqueo del teléfono (Android 10 o más). Solo se ofrece si el teléfono tiene un bloqueo puesto; si se quita después, la app se abre sin pedir nada, para que nadie quede por fuera de sus datos. Se vuelve a pedir al volver de otra app.
- **Widget:** Glance (`Widget.kt`: `WidgetPlata`): lo que tienes (patrimonio), lo que salió este mes y un botón "Anotar" que abre la app en el formulario (`MainActivity.EXTRA_ANOTAR`). Se actualiza al guardar y cada hora. Perfil tiene un botón para ponerlo.
- **Respaldo de todo Fogón:** `Datos.kt`: `DatosAhorrador` (un `ProveedorDatosFogon`) da y restaura el `Estado` completo. Si la app está abierta, el ViewModel ve el cambio en el disco y lo toma. Ojo: el candado no protege de otra app de Fogón, porque comparten la firma.
- **Sin internet:** el manifiesto no pide el permiso `INTERNET`. Reportar abre GitHub o el correo en otra app.
- **Respaldo:** JSON completo (el mismo `Estado`) con tope de 10 MB al leerlo, y CSV de movimientos (fecha, tipo, monto, cuenta, a cuenta, categoría, nota).

## 7. Pruebas y herramientas

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:ahorrador:testDebugUnitTest :apps:ahorrador:lintDebug
```

- `LogicaTest`: saldos y traspasos, el mes, 4×1000, tarjeta (mínimo, intereses, cuotas), fechas a fin de mes, los cuatro sistemas, metas, deudas y fijos, respaldo de ida y vuelta, formato de pesos.
- `TraduccionTest`: los dos idiomas con los mismos textos y datos variables, también los de `core/identidad`.

## 8. Trampas conocidas

- **Los nombres de cuentas y categorías son datos de la persona.** Se crean en el idioma del tutorial y no se traducen al cambiar de idioma (igual que un nombre que ella misma escribió).
- **Un texto con `%` y sin datos variables** va con `formatted="false"` en `strings.xml`; uno con datos variables escribe `%%`.
- **No cambiar el tipo de una cuenta con movimientos:** una tarjeta cuenta la plata al revés. La pantalla de editar ya no lo deja.
- **Al editar el saldo de una cuenta** se ajusta su saldo inicial para que el saldo de hoy quede en lo que se escribió; los movimientos no se tocan.
- **Las categorías `impuestos` y `otros` tienen id fijo:** el 4×1000 se anota en `impuestos` y un gasto sin categoría va a `otros`.
