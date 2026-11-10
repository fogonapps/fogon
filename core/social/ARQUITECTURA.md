# Arquitectura social común

Biblioteca Android sin dependencias de apps. Cajón conserva adaptadores de sus DTO y almacenamiento.

| Archivo | Qué contiene |
|---|---|
| `Cifrado.kt` | Primitivas Tink y pares de llaves sin almacenamiento. |
| `Equipo.kt` | DTO MFA/equipo/registro y permisos por valores. |
| `InstantaneaClaves.kt` | Copia tipada de preferencias con JSON compatible. |
| `PrepararVideo.kt` | Preparación privada Media3, recorte y póster. |
| `Perfiles.kt` | Registro de los perfiles locales del teléfono (archivos atómicos, hasta cinco, `idCuenta`) independiente de Android, para que cada app separe su cuaderno por perfil. |
| `Privacidad.kt` | Versión de política, aceptación, rutas y cadencia. |
| `Publicaciones.kt` | Lectura de fotos y árbol parametrizado por selectores. |
| `SeguridadCuenta.kt` | Validación de credenciales, correo y gracia. |
| `SesionCuenta.kt` | Marcas de generación y rechazo de respuestas antiguas. |
| `Videos.kt` | Tramo y decisión de carga explícita. |

## Cliente y sesión local

| Archivo | Qué contiene |
|---|---|
| `ConfiguracionSocial.kt` | Id estable, nombre visible y User-Agent de la app; `JsonSocial` conserva la configuración de JSON heredada. No añade un selector de app al protocolo. |
| `ModelosSociales.kt` | DTO de cuenta, sesiones, perfil, publicaciones, comentarios, relaciones, avisos, reportes y moderación; errores HTTP semánticos. Los descriptores conservan los nombres de Cajón. |
| `ClienteSocial.kt` | Transporte y rutas comunes, incluidos MFA/equipo, multimedia y sobres de chat. Conserva cabeceras y tiempos de espera, también las diferencias históricas entre peticiones normales, fotos y vídeo. Transporte y nombre de dispositivo inyectables para pruebas sin Android ni red real. |
| `AlmacenSesionSocial.kt` | Contrato de token, identidad estable, política aceptada y marcas de generación de la sesión. La app implementa persistencia y confirmación de identidad. |
| `ControladorSocial.kt` | Base con StateFlow para sesiones, feed/Explorar, perfiles remotos, comentarios/respuestas, reacciones/guardados, seguir/bloquear, avisos, reportes y operaciones genéricas de equipo/moderación. `EventoSocial` expresa resultados sin recursos de una app. |

`ControladorSocial` recibe scope, sesión e identidad inicial. El adaptador proporciona cliente, tratamiento de errores, cierre de cuenta, aplicación de cuenta remota, contador Android de avisos y recarga de casos. Los estados mutables protegidos permiten que el adaptador conserve las actualizaciones culturales sobre las mismas listas; las pantallas reciben los mismos StateFlow. La sesión usada por el adaptador y la base es una sola.

La extracción conserva el orden de peticiones, actualizaciones optimistas y reversión, comprobaciones de vigencia y exclusión de equipo. No añade comprobaciones nuevas a operaciones que antes no las tenían. Las pruebas de cliente/controlador cubren contratos HTTP, JSON, política, respuestas tardías, reversión, comentarios, orden de avisos y exclusión de equipo. Las pruebas de Cajón se conservan.

## Acceso, política y publicación

| Archivo | Qué contiene |
|---|---|
| `AccesoCuenta.kt` | Paso, motivo y estado de acceso. `EstadoAcceso` se distingue del DTO HTTP `Acceso`. Cajón conserva alias de los nombres usados por sus pantallas. |
| `ControladorCuentaSocial.kt` | Base sobre `ControladorSocial`: código, reenvío, entrada con contraseña, cambio/confirmación de correo, borrado, privacidad de cuenta y aceptación/pausa de política. |
| `PreparacionPublicacion.kt` | Barrera `PreparacionPublicacion`, aporte de instantánea `AporteSocialApp`, deduplicación confirmada `SubidasPerfil` y envío ordenado de perfil/cambios. |

El cliente sin sesión y el reloj de reenvío son inyectables. El adaptador completa la entrada (`entro`) antes de recuperar datos con contraseña (`llaveConClave`/`llaveNueva`). Estos hooks conservan su implementación cifrada en Cajón; el núcleo no conoce `/cajon`, Estado, Obra, llaves envueltas ni almacenamiento privado. Los eventos nuevos de correo/borrado se traducen a los mismos recursos de Cajón.

La aceptación confirma la generación antes de guardar la versión y permitir red; después llama a la recuperación, sincronización y feed en el mismo orden del adaptador. Posponer deshabilita red antes de cancelar subida y recuperación. `revisionPolitica` sigue impidiendo que una comprobación anterior sobrescriba una aceptación reciente. El cierre local mantiene su secuencia en Cajón.

`PreparacionPublicacion.preparar(vigente)` espera identidad y contenido recuperado antes de leer Estado. El adaptador aplica la barrera de cajón recuperado/desbloqueado. `AporteSocialApp.perfil()` y `publicaciones()` convierten la misma instantánea local y resuelven la portada. `subirAporteSocial` conserva el orden conversión, cliente, vigencia, perfil, vigencia, cambios, vigencia y confirmación local de claves. Solo se retiran claves conocidas por ese teléfono; una petición fallida o generación invalidada no confirma la referencia para deduplicar. La foto de perfil se procesa después, como antes.

## Límite del corte y continuación

La 1c es un corte reducido. Siguen en `RedSocial`: registro con foto, cambio de contraseña entrelazado con reenvoltura de llave, decisión de conservar cuenta durante la gracia, aplicación de identidad y cierre/invalidación local; creación local de publicación y preparación multimedia del compositor; gustos, reseñas, frases y notas. No se mueve `RecuperacionCajon`, sincronización cifrada, autorización de reemplazo ni Workers.

Antes de extraer más orquestación: probar la secuencia completa de cambio de contraseña (lectura de llave, petición, reenvoltura, escritura y recuperación), cancelación durante registro con foto y decisión diferida de gracia. Extraer entonces la orquestación genérica con hooks explícitos; no trasladar la recuperación cifrada al núcleo. Las pantallas genéricas ya extraídas, contratos UI, excepciones culturales y matriz de comparación están en `core/social-ui/ARQUITECTURA.md`. El chat permanece íntegro en Cajón por su acoplamiento de sesión, llaves, directorio e historial; no se activa en otras apps.

Las pruebas añadidas cubren recuperación antes del contenido, invalidación durante preparación/subida, confirmación y reintento, cuenta nueva por código, correo tardío, orden/fallo de borrado y orden/vigencia de aceptación de política. Se mantienen las pruebas de adaptación de Cajón.

Comprobado en Android (emulador y servidor local): actualización conservando sesión y llaves, recuperación antes de publicar, cuenta y política, multimedia, comentarios y reacciones, avisos, elevación y moderación, y chat. El contrato mantiene token/política en CajaFuerte e idCuenta en el registro existente; no introduce una transacción nueva entre esos almacenes ni una migración.

En el corte de UI, `InterfazSocial` utiliza el mismo `ControladorCuentaSocial` y sus StateFlow. Los callbacks de Cajón conservan registro con foto, contraseña/reenvoltura, vinculación, usuario, vistas y retirada/borrado con sus efectos locales. La gracia mueve solo el diálogo, no su decisión autenticada. No se modifica código de `core/social` en este corte.
