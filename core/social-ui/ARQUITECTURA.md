# Interfaz social común

Biblioteca Android Compose. Depende de `core/social`, `core/perfil` y `core/identidad`; ninguna app es dependencia. El Cajón mantiene fachadas con sus firmas anteriores. No cambia rutas, JSON, almacenamiento, llaves, Workers ni operaciones del controlador.

| Archivo | Qué contiene |
|---|---|
| `ContratosSociales.kt` | `NavegacionSocial`, `ContenidoSocialApp`, `InterfazSocial`, `AdjuntoSocial`, `AjusteSocial` y explicaciones de cuenta aportadas por la app. |
| `PantallasSociales.kt` | Avatar, filas de personas, tarjeta común, detalle/comentarios/respuestas, etiquetas, guardados, avisos/solicitudes, seguidores/seguidos/bloqueados, cuenta/registro con foto, correo/contraseña/borrado, reportes, estado y moderación. |
| `Sesiones.kt` | Lista, fechas, cierre individual/otras sesiones y temporizador de reenvío. |
| `Equipo.kt` | MFA, elevación, códigos, asignación de equipo y registro paginado; conserva FLAG_SECURE y secretos en memoria de composición. |
| `Privacidad.kt` | Hoja obligatoria, pausa y transparencia con servicios, explicaciones, estados y acciones de la app. |
| `Perfiles.kt` | Selector del teléfono, instantáneas de presentación `PerfilLocal`/`PerfilesLocales` y callbacks de selección/alta/retirada/exportación. |
| `GraciaBorrado.kt` | Diálogo de decisión de gracia con fecha y callbacks, sin asumir cómo recupera datos la app. |
| `FeedSocial.kt` | Pestañas Siguiendo/Explorar y presentación de listas: carga, vacío, tarjetas, más y fin. La app aporta lista ordenada y contenido de tarjeta. |
| `TarjetaNeutra.kt` | Tipo desconocido: origen declarado (servidor si no hay origen) y enlace canónico, sin portada ni interpretación cultural. |
| `ReportesSociales.kt` | Códigos y textos de motivos, presentación de faltas y limpieza de cabeceras de mensajes reportados. |
| `PiezasSociales.kt` | Fachada de cabecera que aporta el texto de Atrás al componente de identidad. |
| `FotosPublicacion.kt` | Carrusel y visor: paginación, zoom, doble toque, gesto de cierre, botón y Atrás. |
| `VideosPublicacion.kt` | Póster autenticado sin caché y reproductor creado solo al tocar; foco, pausa en segundo plano y liberación al cerrar. |
| `ControlesMultimedia.kt` | Dibujo de reproducción; el botón común procede de identidad. |

Los dibujos y las piezas visuales extraídos conservan sus trazos, medidas, semántica y colores de `FogonTema.actual` en `core/identidad/IconosSociales.kt` y `PiezasSociales.kt`. Se usan controles Material del mismo modo que antes; los iconos se dibujan con `IconoRiso`.

## Contratos y consumidores

- `NavegacionSocial`: callbacks de perfil (`String`), publicación (`Long`), etiqueta (`String`), cuenta, ajustes (`AjusteSocial`), adjunto (`AdjuntoSocial`), volver y gente (`String`, seguidores). El adaptador convierte a las mismas `Pantalla` de Cajón. El destino de adjuntos queda disponible para compositores de app; el compositor de Cajón conserva su selección local.
- `ContenidoSocialApp`: tipos admitidos, nombre y cuerpo por tipo, decisión de colocar multimedia dentro del cuerpo, compositor con adjunto opcional, secciones y cifras. Cajón admite `diario`, `resena`, `lista`, `foto`. Usa el compositor existente y conserva el cuerpo de reseña (parte de obra, fotos, utilidad), las portadas, puntuaciones y pestañas culturales en la app. Los tipos desconocidos no pasan por su cuerpo cultural.
- `InterfazSocial`: controlador común y acciones que requieren los efectos locales de la app (registro con foto, contraseña/reenvoltura, usuario, vinculación, cierre, vistas, retirada/borrado), cabeceras vigentes de vídeo, confirmación y extensiones de menú/moderación/previa cultural. No es otro almacén ni otro controlador: utiliza la misma instancia `RedSocial` y sus StateFlow.
- `TextosSocialApp`: introducción, qué sube, qué se comparte, recuperación, borrado, creación de contraseña, resumen/pausa de política y explicación del orden. Permanecen en recursos de Cajón porque describen su cuaderno cultural y cifrado, no servicios disponibles en todas las apps.
- Transparencia recibe servicios por línea y opciones con estado/acción. Cajón conserva la consulta al volver de Android y los ajustes de escuchas, Gist, notificaciones y copia cifrada.
- El selector recibe una instantánea nueva al abrir, y solo ofrece retirar el activo. La exportación sigue siendo la del cajón visible. No se cambia el registro ni se habilitan varias cuentas en otras apps.

Los textos genéricos tienen prefijo `social_` y son literalmente los es/en anteriores. Se conservan las claves de Cajón para sus consumidores culturales. La prueba de traducciones compara todos los recursos comunes, incluidos plurales, con los originales de Cajón en ambos idiomas.

`VideoPublicacion` y `ReproductorVideo` leen cabeceras mediante callback en los mismos puntos que antes. No capturan un token de forma permanente. No cambia la política de descargas ni el almacenamiento.

## Matriz del corte y límites

| Área | Común | Permanece en El Cajón y motivo |
|---|---|---|
| Cuenta/registro | Campos, pasos, foto del selector, validaciones UI, correo y contraseña | Hooks de registro/foto, reenvoltura/recuperación y cierre: están ligados a sus llaves y no se reordenan. |
| Sesiones/gracia | Lista, cierre, reenvío y diálogo de gracia | Decisión autenticada diferida y aplicación de identidad: conservan el controlador y sus garantías actuales. |
| Política/transparencia | Hoja, pausa y estructura de transparencia | Servicios/explicaciones/opciones y lectura de ajustes externos. |
| Feed | Pestañas, carga/vacío/fin, más y tarjeta común | `PantallaRed`/`FeedInicio`: cabecera con chat, notas, gustos iniciales, Para ti y orden/filtrado cultural. Se invoca el render común sin cambiar orden ni efectos. |
| Tarjeta/compositor | Autor, fecha, menú, multimedia exterior, texto/etiquetas, spoiler e interacciones; fallback neutro | Cuerpo y compositor de los cuatro tipos, obras, reseñas, portadas y utilidad de reseñas. |
| Comentarios | Detalle, raíces/respuestas, expansión, me gusta, borrar y reportar | Filtro de palabras y retirada local por clave mediante callback, con sus reglas originales. |
| Personas/avisos | Gente, seguidores/seguidos, solicitudes, bloqueos, agrupación y acciones de avisos | Ajustes Android de avisos y chats/Workers. |
| Moderación/equipo | Casos, decisiones, restauración, permisos, MFA/elevación, equipo y registro | Cola de frases y previa de obras reportadas: extensiones culturales. El contenido de reportes de chat se presenta sin mover el chat. |
| Perfil remoto | Avatar/filas/tarjetas y contratos de secciones/cifras | `PerfilRed` sigue componiendo `core/perfil`, con conversión cultural, cuadrícula, música, listas, obras y acción de chat; moverlo entero arrastraría estos acoplamientos. |
| Perfiles del teléfono | Selector y confirmación/exportación | Registro, migración, archivos, cancelación/cierre de recursos y copia JSON. |
| Chat | Ninguna extracción ni activación nueva | Completo por D-030: sesión, llaves, directorio, historial y adjuntos culturales. |

## Comparación en Android

Comparar antes/después con el mismo servidor local, datos y sesión, textos es/en y los cuatro temas:

- Cuenta: correo, contraseña, código/reenvío, registro con foto, cambio de usuario/clave/correo, vinculación, privada, salida, credencial/motivos de borrado y decisiones de gracia.
- Sesiones: carga, fechas, actual, cierre individual y otras; política: aceptar/posponer/reabrir/borrar, pausa y transparencia al regresar de ajustes Android.
- Siguiendo y Explorar: pestañas, orden por gustos, filtros, carga/vacío/más/fin; las cuatro tarjetas, spoiler, etiquetas, autor/menú/fecha, enlaces, contadores, reacciones y guardados. Incluir tipo desconocido con origen/enlace.
- Detalle: texto, raíces/respuestas, expansión/cancelación, borrados, reportes y acceso sin cuenta.
- Gente, seguidores/seguidos, quitar seguidor, solicitudes/aceptar/rechazar y bloqueados; avisos por fecha, leídos y borrar uno/todos.
- Moderación: permisos, expiración de elevación, bloqueo, casos, motivos, previa cultural/multimedia, restauración, decisiones y cola de frases; equipo, filtros/paginación e integridad del registro; MFA/recuperación y protección de capturas.
- Selector: foto/apodo/usuario/activo, añadir/cambiar, retirar activo, cancelar y exportar. Cambiar perfil con peticiones/subidas pendientes, recuperación antes de publicar y actualización instalada conservando llaves/Workers.
- Multimedia: varias fotos/contador, visor/zoom/gestos/Atrás y vídeo desde feed y moderación; sin reproducción antes de tocar y pausa en segundo plano.

La compilación y las pruebas no certifican equivalencia visual ni la actualización instalada. Esas comprobaciones se hacen en el emulador con un servidor local.

## Validación del corte

Pasan 40 pruebas de `core/social` y la prueba de paridad de todos los textos/plurales es/en de `core/social-ui`, además del lint de este último. `./fogon probar cajon` pasa sus 175 pruebas y lint. Se ensamblan en debug Forzudo, Ahorrador, Cucha y Cajón. `./fogon revisar` y `git diff --check` pasan. Kotlin se ejecutó en proceso por las restricciones del entorno. El primer lint de Cajón detectó lectura de recursos mediante LocalContext en el adaptador; se corrigió delegando en `vm.tx`, como antes, y se repitió con éxito la comprobación completa.

La comparación visual antes y después (es y en) y los recorridos de la matriz se hicieron en el emulador con un servidor local.

## Consumidor El Forzudo

`ui/Cuenta.kt` aporta `InterfazSocial` y el tipo `entreno` mediante `ContenidoSocialApp`.
`ui/EntrenosSociales.kt` conserva compositor y conversión deportiva en la app, utiliza tarjeta,
fotos/vídeo, detalle/comentarios/respuestas, reacciones, guardados, avisos, etiquetas, reportes
y moderación comunes. La previa de contenido reportado interpreta la instantánea cerrada.
El acceso Inicio → Tu gente conserva Siguiendo/Explorar. No activa chat ni modifica El Cajón.

`VideoPublicacion` acepta una descripción accesible opcional, con el comportamiento anterior
por defecto. Forzudo la aporta sin duración porque `datos.duracion` mide el entreno, no el clip;
el reproductor obtiene la duración real al abrir el archivo.

`PestanasFeedSocial` permite personalizar el texto de la acción de publicar. El valor por defecto
conserva «Publicar» en El Cajón; Forzudo usa «Compartir» y abre su selector de sesiones terminadas.
La insignia de no leídos y la fila con interruptor se comparten desde `core/identidad`;
las fachadas de El Cajón mantienen sus firmas y presentación.
