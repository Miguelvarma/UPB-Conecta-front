# UPB Conecta — Front (Android, preliminar)

Front-end preliminar de UPB Conecta para Android Studio, construido con **Kotlin + Jetpack Compose (Material 3)**. Este proyecto es independiente del backend (`UPB-CONECTA-main`, Node.js/TypeScript con arquitectura hexagonal) y **no debe fusionarse en la misma carpeta** — ábrelos como dos proyectos separados en Android Studio y tu editor de código, tal como están entregados.

## Por qué está separado del backend

El backend ya sigue arquitectura hexagonal (`domain / application / infrastructure`) y vive en su propio repositorio. Este front sigue el mismo espíritu de separación de capas pero en Android:

```
domain/       modelos de negocio puros (Convocatoria, Practica, Noticia, PostForo, ...)
data/         repositorios + datos de ejemplo (mock). Hoy implementan el "puerto"
              con datos simulados; mañana los reemplaza un adaptador que consuma
              la API real del backend, sin tocar la capa de UI.
ui/           pantallas Compose, navegación, tema y componentes reutilizables.
```

Cuando el backend exponga una API (REST/GraphQL sobre los contextos `ingestion`, `classification`, `feed`, etc.), el cambio en el front es acotado: se agregan implementaciones `Http*Repository` en `data/repository/` y se cambia una línea en `AppContainer` — la UI no se toca.

## Qué incluye este front preliminar

- **Pantalla de bienvenida (splash)**: logotipo "UPB Conecta" en degradado de marca al abrir la app (`ui/screens/splash/SplashScreen.kt`), primera ruta del `NavHost`, avanza sola a Login.
- **Login**: correo institucional + contraseña contra `AuthRepository` — hoy `HttpAuthRepository` por defecto en `AppContainer`, contra `POST /auth/login` del backend real (contexto `identity`); `FakeAuthRepository` sigue disponible (una línea comentada en `AppContainer`) para probar sin backend. El backend devuelve el rol (`student` o `professor`) y la app lo respeta; cuentas de prueba en Railway (contraseña `S3cr3t!UPB`): estudiantes como `estudiante@upb.edu.co` y profesores como `profesor@upb.edu.co`. Credenciales de prueba en la propia pantalla de login.
- **Inicio**: convocatorias y prácticas en pestañas, filtrables por programa académico, con insignia de urgencia según la fecha de cierre (la clasificación que hoy no existe en el correo masivo).
- **Noticias**: el apartado con scrolling pedido explícitamente — carrusel horizontal de destacadas + listado vertical completo (`LazyColumn`/`LazyRow`).
- **Foro**: hilos con identidad verificada (insignia), estado de moderación automática visible (aprobado / en revisión) y respuestas — cualquier usuario logueado puede comentar en un hilo.
- **Mensajería privada profesor↔estudiante**: un profesor puede iniciar una conversación con cualquier estudiante del directorio de ejemplo; el estudiante la ve y puede responder. Cada conversación solo la ven sus dos participantes — ni otros estudiantes ni otros profesores (`domain/model/Mensajeria.kt`, `data/repository/MensajeriaRepository`).
- **Mapa del campus**: plano esquemático preliminar con puntos de interés tocables (ver nota abajo).
- **Chatbot de preguntas frecuentes**: UI de chat con preguntas sugeridas y respuesta simulada por coincidencia de texto.
- **Notificaciones** y **Perfil** (con identidad institucional verificada, y "Cerrar sesión" para volver al login).

Toda la app usa **datos de ejemplo** (`data/mock/MockData.kt`) — nombres, empresas, fechas y contenido son ilustrativos, generados con fechas relativas a "hoy" para que la clasificación por urgencia siempre se vea coherente al abrir la app.

## Cómo abrirlo

1. Abre la carpeta `UPB-Conecta-Frontend` (esta) directamente en Android Studio — **no** la carpeta del backend, y no las dos juntas en un mismo proyecto.
2. Deja que Gradle sincronice. La primera sincronización descarga el Android Gradle Plugin, Kotlin, las librerías de Compose y (desde el login real) Retrofit/OkHttp/Moshi desde Google/Maven — necesita internet (esto no se pudo verificar desde el entorno donde se generó el proyecto, que no tiene salida a esos repositorios; Android Studio sí la tiene).
3. Para que el login funcione, el backend debe estar corriendo (`cd backend && npm run build && npm run start:http`, con MongoDB disponible) — si no, cambia `AppContainer.authRepository` a `FakeAuthRepository` (una línea comentada justo al lado) para seguir probando el resto de la app con datos simulados.
4. Ejecuta en un emulador o dispositivo con Android 8.0 (API 26) o superior.

## Decisiones y pendientes a propósito

- **`minSdk = 26`**: para poder usar `java.time.LocalDate`/`LocalDateTime` nativo (fechas de cierre, timestamps del foro) sin agregar la librería de *core library desugaring*. Si necesitas soportar Android 7 hacia abajo, se puede bajar a `minSdk = 24` agregando esa dependencia.
- **Sin Hilt/Koin**: `AppContainer` (en `data/repository/Repositories.kt`) es un contenedor de dependencias manual y simple, a propósito, para que el proyecto abra y muestre algo en Android Studio sin resolver una librería de inyección de dependencias primero. Es el punto natural para introducir Hilt más adelante.
- **Mapa del campus**: es un plano esquemático dibujado en Compose (`Canvas` + puntos posicionados por coordenadas relativas), no un SDK de mapas real — la Universidad aún no tiene un plano georreferenciado público ni se pidió una API key de Google Maps. El modelo `PuntoInteres` ya está listo para mapearse a coordenadas geográficas reales cuando corresponda.
- **Colores de marca**: la paleta (azul marino + degradado rosa→morado + rojo institucional) se calcó a ojo de capturas de pantalla de las apps institucionales de la UPB (menú y login), no de un manual de marca con códigos exactos. Está centralizada en `ui/theme/Color.kt` (y el degradado `UpbGradienteMarca` en `ui/theme/Theme.kt`) — reemplaza esos valores por los códigos exactos cuando tengas el manual de identidad corporativa a mano.
- **Ícono de lanzador / splash nativo**: una sola marca en degradado rosa→morado (`res/drawable/ic_launcher_foreground.xml`) generada para este preliminar — es también lo que Android 12+ muestra automáticamente como splash nativo antes de que cargue el primer Composable, por eso se simplificó a una sola pieza en vez de anillos. Reemplazable desde Android Studio con *File → New → Image Asset* cuando exista el logo oficial de UPB Conecta.
- **Chatbot**: responde por coincidencia simple de texto sobre la lista de preguntas frecuentes — no hay NLP ni backend real todavía.
- **No se pudo compilar dentro del entorno donde se generó este proyecto** (no tiene el Android SDK ni salida de red a los repositorios de Google/Maven). El código se revisó a mano (paquetes, llaves/paréntesis balanceados, firmas de las pantallas contra su uso en la navegación, imports), pero la primera sincronización real en Android Studio es la validación definitiva — si algo no sincroniza, dime el error y lo ajustamos.

## Próximos pasos sugeridos

1. Sincronizar en Android Studio y correr en un emulador para validar visualmente.
2. Reemplazar `data/mock/MockData.kt` por adaptadores reales contra el backend a medida que se implementen los contextos `classification`, `feed`, `moderation`, etc.
3. Cambiar el mapa esquemático por un mapa real cuando exista el plano/API.
4. Sustituir la paleta de colores por el manual de marca oficial (códigos hexadecimales exactos).
