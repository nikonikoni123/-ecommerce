# Plataforma E-Commerce

Plataforma de comercio electronico con dos tipos de cuenta (usuario comprador y empresa vendedora),
gestion de productos e inventario, y un panel de empresa con niveles de acceso personalizables.

El desarrollo avanza **por etapas**. Este repositorio contiene la **Etapa 1** completa y funcional.

## Arquitectura

| Componente | Tecnologia | Puerto |
|---|---|---|
| `backend/` | Spring Boot 4.1 · Java 26 · MongoDB | 8080 |
| `frontend/` | Angular 22 · TypeScript · SCSS | 4200 |
| `nlp-service/` | FastAPI · BERT multilingue (transformers) | 8000 |

La base de datos es **MongoDB** (no relacional). El microservicio de NLP queda desplegado y con su
contrato fijado desde esta etapa; lo consumira el panel de casos en la Etapa 4.

## Que incluye la Etapa 1

**Cuentas y seguridad**
- Registro de usuario comprador con todos los campos obligatorios de la especificacion.
- Registro de empresa con creacion automatica de su **unico usuario root**, cargos por defecto e
  invitacion de usuarios adicionales.
- Verificacion por correo electronico obligatoria antes del primer ingreso, con token de un solo uso.
- Inicio de sesion con JWT y refresh token rotatorio.
- **Verificacion en dos pasos (TOTP)**, desactivada por defecto, con recordatorio en cada ingreso
  (en las cuentas de empresa el recordatorio corresponde solo a root).
- Recuperacion y cambio de contrasena, con cierre del resto de sesiones.
- Seccion de cuenta: modificar datos, cambiar contrasena, activar/desactivar 2FA y eliminar cuenta
  (borrado logico con anonimizacion).

**Control de acceso**
- Permisos granulares por proceso de gestion (`Permission`).
- **Cargos**: plantillas de permisos que root asigna a los usuarios de su empresa.
- Permisos concedidos o revocados individualmente por encima de los cargos.
- Los permisos se recalculan en cada peticion, de modo que un cambio aplica de inmediato.

**Catalogo e inventario**
- Catalogo publico con filtros, busqueda por texto, paginacion y ordenamiento.
- **Priorizacion personalizada con etiquetas invisibles**: cada producto lleva `hiddenTags` y cada
  cliente acumula afinidad al consultar fichas. Las etiquetas nunca se exponen al navegador.
- Panel de gestion de productos: alta, modificacion, cambio de stock y baja, cada uno protegido por
  su permiso.
- Registro de actividad de los miembros de la empresa.
- Pestana de notificaciones.

## Puesta en marcha

Necesitas **JDK 26**, **Node 24.15+** y **MongoDB**. Maven no hace falta: se usa el wrapper incluido.

### 1. Servicios de apoyo

**Opcion A — Docker (recomendada).** Levanta MongoDB, Mailpit y el microservicio NLP:

```bash
docker compose up -d
```

Mailpit captura los correos de verificacion y los muestra en <http://localhost:8025>. No los reenvia
a internet: los correos de prueba se leen ahi, no en tu bandeja real.

**MongoDB se publica en el 27018**, no en el 27017. Es deliberado: muchas maquinas ya tienen un
MongoDB propio en el 27017 y, si los dos escuchan (uno en IPv4 y otro en IPv6), `localhost` resuelve
de forma impredecible y la aplicacion puede acabar leyendo y escribiendo en la base equivocada.
Con el 27018 ambos conviven sin pisarse.

**Opcion B — MongoDB ya instalado en la maquina.** Usa el perfil `local` del backend, que apunta a
`mongodb://127.0.0.1:27017` sin autenticacion:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Necesitaras de todas formas un SMTP de pruebas en el 1025; lo mas comodo es levantar solo Mailpit
con `docker compose up -d mailpit`.

> **Los correos no salen a internet.** Mailpit intercepta todos los mensajes. Si te registras con tu
> direccion real, el correo **no** llegara a tu bandeja: abrelo en <http://localhost:8025>.

### Enviar correo de verdad

El backend lee el `.env` de la raiz del repositorio, el mismo que usa docker-compose. Para salir a
un SMTP real con Gmail:

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=tu-cuenta@gmail.com
MAIL_PASSWORD=abcdefghijklmnop
MAIL_FROM=tu-cuenta@gmail.com
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

Cuatro detalles que hacen fallar el envio con un escueto `Authentication failed`:

- **La contrasena de tu cuenta de Google no sirve.** Google la rechaza para SMTP desde 2022. Hay que
  generar una **contrasena de aplicacion** en <https://myaccount.google.com/apppasswords>.
- Esa pagina **no existe** mientras la cuenta no tenga activada la verificacion en dos pasos: si
  entras antes, Google responde "la opcion de configuracion que buscas no esta disponible". Activala
  primero en <https://myaccount.google.com/signinoptions/twosv>.
- **Google muestra la contrasena en cuatro grupos separados por espacios** (`abcd efgh ijkl mnop`),
  pero hay que pegarla **sin espacios**: son 16 letras minusculas seguidas. Con los espacios la
  autenticacion falla exactamente igual que con una contrasena incorrecta.
- `MAIL_FROM` debe ser la misma direccion autenticada; Gmail no permite remitentes arbitrarios.

Para comprobar el formato sin abrir el archivo:

```bash
grep MAIL_PASSWORD .env | cut -d= -f2 | tr -d '\n' | wc -c
```

Debe devolver 16.

### 2. Backend

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

API en <http://localhost:8080>, documentacion interactiva en <http://localhost:8080/swagger-ui.html>.

Sin el perfil `local` toma la configuracion de `.env` (copia `.env.example` y ajusta los valores).

### 3. Frontend

```bash
cd frontend && npm install && npm start
```

Aplicacion en <http://localhost:4200>.

### Cuentas de demostracion

Al arrancar con la base vacia se cargan datos de ejemplo. Contrasena: `Demo1234!`

| Correo | Rol |
|---|---|
| `empresa@demo.local` | Usuario **root** de la empresa. Todos los permisos. |
| `gestor@demo.local` | Miembro con el cargo "Gestor de productos". Sin permiso para eliminar. |
| `cliente@demo.local` | Usuario comprador. |

Compara las dos primeras cuentas en `/empresa/productos`: el boton de eliminar solo aparece para
root, y la API responde 403 si el gestor lo intenta de todos modos.

Para desactivar la carga de ejemplo: `APP_SEED_DEMO_DATA=false`.

## Pruebas

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run build
```

## Estructura

```
backend/src/main/java/com/ecommerce/
├─ auth/        registro, verificacion, sesion y contrasena
├─ user/        perfil, 2FA y baja de la cuenta
├─ company/     empresa y cargos
├─ catalog/     catalogo publico y gestion de productos
├─ security/    JWT, permisos efectivos y TOTP
├─ notification/ activity/ mail/ nlp/ config/ common/

frontend/src/app/
├─ core/        servicios, interceptor, guardas y directiva de permisos
├─ shared/      componentes reutilizables
├─ layout/      encabezado y pie
└─ features/    home · catalog · auth · account · company
```

## Siguientes etapas

- **Etapa 2** — Carrito y checkout: cantidades, envio e impuestos, motor de descuentos (10% por
  ventana de tiempo, 50% por pedido aleatorio, +5% cliente frecuente), pago simulado, envio como
  regalo y factura descargable.
- **Etapa 3** — Ordenes: estados, panel por prioridad, notificaciones por correo y en la app,
  historial y reembolsos.
- **Etapa 4** — Casos priorizados con BERT, comentarios al vendedor y chats por departamento.
- **Etapa 5** — Departamentos, jefes y equipos, metas KPI y panel de graficas.
