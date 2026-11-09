# La Alcancía · Fogón

<p align="center"><img src="fastlane/metadata/android/en-US/images/icon.png" width="140" alt="La Alcancía"></p>

Gastos, presupuesto y ahorro. Sin anuncios, sin rastreo, sin internet y sin pedirte las claves del banco. Su personaje es una ranita tranquila.

- Descargar: [fogonapps/la-alcancia](https://github.com/fogonapps/la-alcancia/releases/latest) (con [Obtainium](https://github.com/ImranR98/Obtainium) llegan solas las actualizaciones: pegar `https://github.com/fogonapps/la-alcancia`).
- Novedades de cada versión: en las [versiones de La Alcancía](https://github.com/fogonapps/la-alcancia/releases).

## Qué hace

- **Anotar en segundos:** teclado grande propio, categorías con su color, "igual que el último" y fecha. Gasto, ingreso, pasar plata entre cuentas o ahorrar para una meta. Se corrige o se borra tocando el movimiento.
- **Cuentas:** efectivo, billetera digital, banco y tarjeta de crédito. Lo que tienes en total, menos lo que debes en tarjetas.
- **Tarjeta de crédito:**
  - cuánto debes, cupo disponible, día de corte y de pago;
  - pago mínimo e intereses si pagas solo el mínimo (aproximados: el valor real está en el extracto);
  - compras a cuotas, con cuántas van y cuánto falta;
  - "Pagar la tarjeta" es pasarle plata desde otra cuenta, no otro gasto.
- **Cuatro formas de organizarse** (se elige en el tutorial y se cambia en Perfil):
  - **Sobres:** la plata de tus cuentas se reparte en sobres por categoría; lo que se pasa se cubre con otro sobre; lo que sobra vuelve a estar por asignar. Cada sobre se puede editar (nombre, grupo, color e ícono) y se pueden crear nuevos;
  - **Regla 50/30/20:** necesidades, gustos y ahorro, con el reparto que quieras;
  - **Kakeibo:** cuatro grupos, una meta de ahorro del mes y el cierre con sus cuatro preguntas;
  - **Solo anotar.**
- **Resumen del mes:** cuánto entró, salió y guardaste, en qué se fue (una dona por categoría; al tocar una, sus movimientos) y cómo van los últimos meses.
- **Buscar movimientos** por nota, categoría, cuenta o valor, con filtros de categoría y mes.
- **Anotar desde el banco (opcional):** reconoce transferencias y pagos de Bancolombia recibidos por SMS y envíos desde la app de Nu. Enlaza tus cuentas y confirma con Anotar, Editar o No; también puedes activar «Anotar solo» por banco y revisar o deshacer después. Detecta movimientos entre tus cuentas y evita duplicados con el CSV.
- **Extracto del banco:** importa el CSV que descargas del banco; adivina las columnas y la categoría de cada movimiento, salta lo que ya estaba y te deja revisar antes de anotar.
- **Reto de ahorro de 52 semanas** y **recordatorio diario** opcional (solo si ese día no anotaste nada).
- **Widget** con lo que tienes, lo que salió este mes y un botón para anotar.
- **Metas de ahorro:** con fecha te dice cuánto guardar cada quincena; sin fecha, a este paso en qué mes llegas.
- **Pagos fijos** (arriendo, servicios, celular), con su calendario del mes: aviso a las 9 de la mañana cuando vencen en 3 días o menos, y si quieres se anotan solos el día del pago.
- **Me deben y debo:** préstamos entre personas y créditos, con abonos.
- **4×1000:** si lo activas, la app anota sola el impuesto al sacar plata de una cuenta de banco.
- **Candado:** pide la huella o el bloqueo del teléfono al abrir (Android 10 o más, y solo si el teléfono tiene bloqueo).
- **Tus datos son tuyos:** respaldo y recuperación en JSON, exportar los movimientos en CSV para una hoja de cálculo y el respaldo de todas las apps de Fogón en un solo archivo.
- **Apariencia:** vidrio sobre una luz suave, una sola letra y el número grande de puntos. Viene en Grafito y al final del tutorial ("¡Dale vida!") se elige otro tema: el de la app, Claro o Contraste; en cada uno se eligen el papel y las dos tintas (las letras se ajustan solas para que se lean), el vidrio y la luz. El personaje de la app cambia de cara según el momento. Además, tamaño de letra, menos movimiento y más contraste.
- **En español e inglés:** sigue el idioma del teléfono, y se puede cambiar en Perfil → Idioma.
- **Reportar un fallo o sugerir algo** desde Perfil: abre GitHub o el correo con el mensaje listo.

## Permisos (y por qué)

| Permiso | Para qué |
|---------|----------|
| Notificaciones | Avisar de los pagos fijos que están por vencer y el recordatorio diario, si lo activas, además de confirmar las alertas del banco. Se pide al crear el primer pago fijo o al encender el recordatorio. |
| Acceso a notificaciones (opcional) | Android permite ver todas las notificaciones. Solo se procesan las apps y los bancos elegidos: SMS que empiezan por «Bancolombia:» y envíos reconocidos de Nu. Lo demás se ignora y no se guarda. Solo alertas nuevas: no se lee el historial de SMS. Se activa en Perfil → Anotar desde el banco y se puede apagar allí o revocar en Android. Nada sale del teléfono. |
| Huella o bloqueo del teléfono | El candado, si lo activas. |
| Arrancar con el teléfono | Volver a programar el aviso diario después de reiniciar. |
| Datos de las apps de Fogón | Que otra app de Fogón (firmada con la misma llave) pueda guardar o recuperar sus datos en el respaldo de todo Fogón. Ninguna otra app puede usarlo. |

- **Sin internet:** la app no tiene el permiso, así que no puede mandar nada a ningún lado.
- **Sin copias automáticas a la nube de Google:** solo se copian los datos al pasar a un teléfono nuevo.

## Por qué no se conecta al banco

En Colombia, las finanzas abiertas (Decreto 368 de 2026) obligan solo a entidades vigiladas por la Superintendencia Financiera. Una app libre, sin servidor y sin internet no puede conectarse, y así tampoco nadie tiene tus claves. En cambio, la app importa el extracto que descargas del banco (CSV), sin salir del teléfono.

## Preguntas frecuentes

También están dentro de la app (Perfil → Preguntas frecuentes, arreglos `preguntas` y `respuestas` de `strings.xml`). Si cambia una, se cambia en los dos lados.

**¿Se puede conectar con mi banco?**
No. En Colombia las finanzas abiertas solo obligan a entidades vigiladas, y una app libre sin servidor no puede conectarse. Además, así nadie tiene tus claves. Anotar es rápido con el teclado de la app.

**¿Dónde quedan mis datos?**
Solo en tu teléfono. La app no tiene permiso de internet. Guarda respaldos desde Perfil → Respaldo.

**¿Qué sistema elijo?**
Si quieres control, Sobres. Si quieres algo simple, la regla 50/30/20. Si te gusta escribir y pensar el mes, el kakeibo. Si solo quieres ver en qué se va, Solo anotar. Lo puedes cambiar cuando quieras.

**¿Cómo anoto la tarjeta de crédito?**
Las compras con la tarjeta son gastos desde la cuenta de la tarjeta (con sus cuotas). Pagar la tarjeta es pasar plata del banco a la tarjeta: no es otro gasto, ya lo contaste al comprar.

**¿Por qué el pago mínimo no cuadra con mi extracto?**
Porque cada banco lo calcula a su manera. La app lo aproxima con el porcentaje que pongas en la cuenta; el valor real está en tu extracto.

**¿Qué es el 4×1000?**
El gravamen a los movimientos financieros: 4 pesos por cada 1.000 que salen de una cuenta de banco. Si lo activas en Perfil, la app lo anota sola. Muchas cuentas de ahorro tienen una exenta.

**¿En qué se diferencia ahorrar de pasar plata?**
Ahorrar es guardar para una meta: la plata sale de lo disponible del mes y se suma a la meta. Pasar es mover plata entre tus cuentas, sin que cambie nada.

## Contacto

Fallos o ideas: desde la app (Perfil → Reportar un fallo o sugerir algo), en [GitHub](https://github.com/fogonapps/fogon/issues) o a **fogonapps@gmail.com**.

## Créditos

- Ideas de presupuesto de uso libre: el método de sobres, la regla 50/30/20 y el kakeibo.
- Letras: Bricolage Grotesque y Doto (SIL Open Font License).
- Licencia del código: GPL-3.0 o posterior.
