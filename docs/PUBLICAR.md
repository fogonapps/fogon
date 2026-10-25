# Publicar una app de Fogón

Cómo se compila la versión firmada de una app y cómo llega al teléfono. Aplica a todas las apps (El Forzudo, El Ahorrador, La Cucha): cada una con su etiqueta (`forzudo-v…`, `ahorrador-v…`, `cucha-v…`).

## La llave de firma

- **Está fuera del repo:** `~/.fogon/firma/`, con `fogon-firma.p12` (PKCS12, alias `fogon`) y `clave.txt`, ambos en `chmod 600`.
- **Certificado:** CN=Fogon, SHA-256 `ce5406882e361cf18e3ece18fa348769e34d8fa6cc8dd53f74a77289a993d8e5`.
- **Una sola llave firma todas las apps de Fogón.** Android solo deja instalar una actualización encima si viene firmada con la misma llave.
- **Si se pierde, ninguna app se puede volver a actualizar:** habría que desinstalar y perder los datos. Por eso entra en el backup de restic, y conviene guardar una copia aparte en KeePass.
- **Para usar otra carpeta**, pasar `-PfogonFirma=<carpeta>` a Gradle.
- **Si la llave no está**, `build.gradle.kts` igual compila la versión `release`, pero sin firma (no se puede instalar).

## Compilar

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :apps:forzudo:assembleRelease
```

Antes, subir `versionCode` (+1) y `versionName` en `apps/<app>/build.gradle.kts`.

El APK queda en `apps/forzudo/build/outputs/apk/release/`. Para comprobar la firma:

```
~/Android/Sdk/build-tools/37.0.0/apksigner verify --print-certs <apk>
```

## Probar antes de entregar

1. En el emulador `fogon-prueba`, instalar la versión firmada encima de la anterior. Así se prueba que la actualización no borra datos.
2. Recorrer el flujo completo: la lista de El Forzudo está en `apps/forzudo/README.md` → Versiones.
3. Confirmar que no hubo cierres inesperados en `adb logcat -b crash`.

## Llevarla al teléfono

- **Por cable (lo más directo):** con la depuración USB activada en el teléfono, `adb install -r <apk>`. Actualiza encima sin perder datos.
- **Por Syncthing:** copiar el APK como `~/Fogon/<App>-<versión>.apk`. La carpeta "Fogón" (id `fogon-apps`) solo envía desde el PC y está compartida con el S24. En el teléfono se abre el APK y se instala.

## Publicar en GitHub

**Público desde el 21-10-2026:** [github.com/fogonapps/fogon](https://github.com/fogonapps/fogon). La organización `fogonapps` la creó Angel; él es admin.

El repo de trabajo (`~/Projects/fogon`, rama `main`) **no se sube**: sus commits viejos tienen las fotos que no son libres y la tabla de strengthlevel.com (ver `LEGAL.md` §6). Lo público sale de la rama `publico`, que empezó con un solo commit limpio y no comparte historial con `main`. El remoto `publico` está configurado para subir solo esa rama (`publico` → `main` en GitHub), así que `git push publico` nunca sube `main`.

Los commits públicos van firmados con el correo privado de GitHub de Angel, `257926308+1015Angel@users.noreply.github.com`, para no mostrar su Gmail.

**Subir una versión nueva:**
1. Revisar lo que cambió desde la última vez (`git diff publico main --stat`): sin secretos, datos personales ni material sin licencia libre.
2. Pasar a `publico` el estado de `main`:
   ```
   git checkout publico
   git read-tree -u --reset main
   git -c user.email=257926308+1015Angel@users.noreply.github.com commit -m "El Forzudo <versión>"
   git push publico
   git checkout main
   ```
3. Etiquetar el código de esa versión en el repo público (la etiqueta lleva la app delante porque ahí viven todas): `git tag forzudo-v<versión> publico` y `git push publico forzudo-v<versión>`. F-Droid busca las versiones nuevas por estas etiquetas.
4. Subir el APK firmado al **repo de descargas** de la app: `gh release create v<versión> ~/Fogon/ElForzudo-<versión>.apk --repo fogonapps/el-forzudo --title "El Forzudo <versión>"`, con notas que digan qué cambió, la URL para Obtainium, el enlace al código (`fogonapps/fogon` en la etiqueta del paso 3) y el SHA-256 del APK y del certificado.

**Repos de descargas (desde el 24-10-2026):** cada app tiene uno, solo con un README (copia en `docs/descargas/`) y sus versiones: [`fogonapps/el-forzudo`](https://github.com/fogonapps/el-forzudo), [`fogonapps/el-ahorrador`](https://github.com/fogonapps/el-ahorrador) y, desde el 25-10-2026, [`fogonapps/la-cucha`](https://github.com/fogonapps/la-cucha); sin issues (los problemas van a `fogonapps/fogon`). Así Obtainium no necesita filtros: Añadir aplicación → pegar `https://github.com/fogonapps/el-forzudo` → Añadir. Hasta la 1.8 / 1.2 las versiones se subían a `fogonapps/fogon/releases`, con un filtro por título en Obtainium; esas quedan ahí como historial.

## F-Droid

Revisado contra la documentación de F-Droid el 21-10-2026 (Inclusion Policy, Quick Start Guide, Reproducible Builds, Build Metadata Reference, Anti-Features).

**Cómo va a funcionar:** F-Droid compila la app desde el código del repo público y la compara con nuestro APK del repo de descargas. Si salen idénticos, publica **nuestro APK, con nuestra firma**. Así da igual si la persona instaló desde GitHub, Obtainium o F-Droid: las actualizaciones de cualquiera se instalan encima. Si no salen idénticos, esa versión no se publica en F-Droid.

**Ya está listo:**
- **Compilación reproducible.** Probado el 21-10-2026: dos clones limpios en carpetas distintas dan el mismo APK sin firmar, byte a byte, y `apksigcopier compare` confirma que el firmado con nuestra llave es ese mismo APK más la firma. Para eso el APK no lleva el commit dentro (`vcsInfo.include = false`) ni el bloque de dependencias de Google (`dependenciesInfo`). La 1.4.1 todavía llevaba el commit, así que la primera versión que sirve para F-Droid es la 1.4.2. **Comprobado con la 1.4.2 tal como lo hará F-Droid:** clon del repo público en la etiqueta `forzudo-v1.4.2`, compilado sin llave y comparado con el APK descargado de GitHub Releases → coinciden. Con El Forzudo 1.11, El Ahorrador 1.5 y La Cucha 1.0 (25-10-2026), un clon limpio compilado sin llave coincide con los tres APK firmados, y los descargados de los repos de descargas son esos mismos APK.
- **Cómo comprobarlo** antes de cada versión: clonar el repo en una carpeta temporal, compilar ahí sin la llave y comparar con el APK firmado. `apksigcopier` es la herramienta de F-Droid (`pip install apksigcopier` en un entorno aparte) y necesita `apksigner` en el `PATH`:
  ```
  git clone ~/Projects/fogon /tmp/prueba/fogon
  cd /tmp/prueba/fogon
  ANDROID_HOME=~/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --no-build-cache :apps:forzudo:assembleRelease -PfogonFirma=/nada
  PATH=~/Android/Sdk/build-tools/37.0.0:$PATH apksigcopier compare <apk firmado> --unsigned apps/forzudo/build/outputs/apk/release/forzudo-release-unsigned.apk
  ```
  Si no imprime nada y termina con código 0, coinciden.
- **Ficha de la app** en `apps/<app>/fastlane/metadata/android/`: textos en `es` y `en-US` (el idioma que F-Droid muestra si no hay otro), ícono, capturas en español e inglés (5 de El Forzudo, 6 de El Ahorrador y 7 de La Cucha por idioma, en el diseño Vidrio y puntos, rehechas el 25-10-2026), sacadas del emulador con datos de prueba, no reales y `changelogs/<versionCode>.txt` de máximo 500 caracteres. Para cada versión nueva hay que agregar su changelog en los dos idiomas.
- **Recetas** (borradores): `apps/forzudo/fdroid/io.github.fogonapps.forzudo.yml`, `apps/ahorrador/fdroid/io.github.fogonapps.ahorrador.yml` (categoría `Finance Manager`, etiquetas `ahorrador-v*`, APK de `fogonapps/el-ahorrador`) y `apps/cucha/fdroid/io.github.fogonapps.cucha.yml` (categorías `Diet` y `Health Manager`, etiquetas `cucha-v*`, APK de `fogonapps/la-cucha`; categorías de las tres revisadas contra `config/categories.yml` de fdroiddata el 25-10-2026: `Money` ya no existe). La de El Forzudo: Categorías `Workout` y `Sports & Health`, `Binaries` apuntando al repo de descargas (`fogonapps/el-forzudo`), `AllowedAPKSigningKeys` con nuestro certificado y actualizaciones automáticas por las etiquetas `forzudo-v*`.
- **Java:** el servidor de F-Droid usa Debian trixie, que trae Java 21 por defecto, la misma versión que usamos.

**Falta:**
1. **Tiempo.** F-Droid pide que una app lleve unos 3 meses de desarrollo activo antes de entrar. El Forzudo empezó el 11-10-2026, así que la solicitud se manda **desde el 11-01-2027**.
2. **Una cuenta de GitLab** de Angel, porque las solicitudes se hacen ahí.
3. **El envío:** hacer un fork de `gitlab.com/fdroid/fdroiddata`, crear la rama `io.github.fogonapps.forzudo`, copiar la receta a `metadata/` con el hash completo del commit de la última versión, y pasarla por `fdroid rewritemeta`, `fdroid lint` y `fdroid build` (o por la CI del fork, que los corre sola). Después, commit `New App: io.github.fogonapps.forzudo` y merge request. Hay que responder rápido a las preguntas de quien la revise.

**Antifunciones (avisos de F-Droid):** ninguna debería aplicar. Según su definición, `Tracking` no aplica a funciones opcionales que vienen apagadas, y `NonFreeNet` es para apps que dependen por completo de un servicio privativo. La sincronización con GitHub es opcional, pero quien revise puede verlo distinto.
