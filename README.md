# Plataforma E-Commerce

Plataforma de comercio electronico con dos tipos de cuenta (usuario comprador y empresa vendedora),
gestion de productos e inventario, y un panel de empresa con niveles de acceso personalizables.

El desarrollo avanzo **por etapas**. Este repositorio contiene las **cinco etapas completas**: el
proyecto esta terminado y verificado de punta a punta.

> La documentacion tecnica detallada —modelo de dominio, arquitectura, clases y diagramas— esta en
> **[doc.md](doc.md)**, y se actualiza al cerrar cada fase.

| Etapa | Alcance | Estado |
|---|---|---|
| 1 | Cimientos, autenticacion, RBAC y catalogo | ✅ Entregada |
| 2 | Carrito, checkout, descuentos y factura | ✅ Entregada |
| 3 | Gestion de pedidos por la empresa, estados y reembolsos | ✅ Entregada |
| 4 | Casos de atencion con BERT, comentarios al vendedor y chat | ✅ Entregada |
| 5 | Administracion, departamentos, equipos, metas KPI y graficas | ✅ Entregada |

## Arquitectura

| Componente | Tecnologia | Puerto |
|---|---|---|
| `backend/` | Spring Boot 4.1 · Java 26 · MongoDB | 8080 |
| `frontend/` | Angular 22 · TypeScript · SCSS | 4200 |
| `nlp-service/` | FastAPI · BERT multilingue (transformers) | 8000 |

La base de datos es **MongoDB** (no relacional). El microservicio de NLP clasifica los casos de
atencion de la Etapa 4 (prioridad, sentimiento y SLA), con una heuristica local de reserva si no
responde.

## Que incluye cada etapa

### Etapa 1 — Cuentas, seguridad, RBAC y catalogo

**Cuentas y seguridad**
- Registro de usuario comprador con todos los campos obligatorios de la especificacion.
- Registro de empresa con creacion automatica de su **unico usuario root** (indice parcial unico
  sobre `(companyId, root)`), cargos por defecto e invitacion de usuarios adicionales.
- Verificacion por correo electronico obligatoria antes del primer ingreso, con token de un solo uso.
- Inicio de sesion con JWT (HS256, 15 min) y refresh token opaco, guardado como hash y rotado en cada
  uso.
- **Verificacion en dos pasos (TOTP, RFC 6238)** propia, sin dependencias externas, desactivada por
  defecto, con recordatorio en cada ingreso (en las cuentas de empresa, solo para root).
- Recuperacion y cambio de contrasena, con cierre del resto de sesiones.
- Seccion de cuenta: modificar datos, cambiar contrasena, activar/desactivar 2FA y eliminar cuenta
  (borrado logico con anonimizacion).

**Control de acceso**
- Permisos granulares por proceso de gestion (`Permission`), agrupados por bloque.
- **Cargos** (`roles`): plantillas de permisos que root asigna a los usuarios de su empresa.
- Permisos concedidos o revocados individualmente por encima de los cargos.
- Permisos efectivos = cargos + concedidos − revocados, **recalculados en cada peticion**.

**Catalogo e inventario**
- Catalogo publico con filtros, busqueda por texto en español, paginacion y ordenamiento.
- **Priorizacion personalizada con etiquetas invisibles**: cada producto lleva `hiddenTags` y cada
  cliente acumula afinidad (`tagAffinity`) al consultar fichas. Las etiquetas nunca se exponen al
  navegador: solo alimentan el orden.
- Panel de gestion de productos: alta, modificacion, cambio de stock y baja, cada uno tras su permiso.
- Registro de actividad de los miembros de la empresa y pestana de notificaciones.

### Etapa 2 — Carrito, checkout, descuentos y factura

- **Carrito** que guarda solo `productId` y `quantity`; los precios se leen del catalogo en vivo y se
  congelan al crear el pedido. Alta individual y por lote, cambio de cantidad y sustitucion con fusion.
- **Motor de descuentos** con tres reglas que solo aplican dentro de una ventana de tiempo: **10%**
  por ventana, **50%** por pedido aleatorio (caja sorpresa) y **+5%** por cliente frecuente; se suman,
  con techo del **65%**.
- La marca de **pedido sorpresa vive en el carrito**, no en la peticion de pago: el cliente no puede
  concederse el 50% enviandolo a mano, y cualquier edicion manual la desactiva.
- **Checkout** con impuestos (IVA 19% sobre la base descontada), envio por empresa, envio como regalo
  y pago simulado. Un carrito con varios vendedores se **divide en un pedido por empresa**.
- **Sin sobreventa**: la reserva de stock es una unica operacion atomica (`$inc` condicionado); si el
  pago falla a medias, se compensa el stock reservado.
- **Factura PDF** descargable, generada al vuelo con OpenPDF desde los importes congelados.
- Importes con `BigDecimal` en todo el calculo, nunca `double`.

### Etapa 3 — Gestion de pedidos por la empresa

- **Panel de pedidos** ordenable por fecha, vencimiento, cantidad y estado, con una **prioridad
  calculada** (`VENCIDO`, `POR_VENCER`, `NORMAL`) que responde a que atender primero.
- **Maquina de estados** declarada en el propio enum `OrderStatus`: avance secuencial, cancelar solo
  antes de enviar, reembolsar solo tras entregar; cancelar y reembolsar devuelven la mercancia.
- **Cambio de productos** de un pedido pagado conservando el porcentaje de descuento original, con la
  diferencia registrada como **saldo de ajuste**.
- **Reembolsos**: el cliente los solicita sobre pedidos entregados y dentro de plazo; la empresa los
  aprueba o rechaza. Un pedido no acumula dos solicitudes abiertas.
- Toda modificacion **avisa al cliente por correo y por notificacion**, centralizado en un servicio.

### Etapa 4 — Casos de atencion con BERT, comentarios y chat

- **Casos**: un hilo entre cliente y empresa que resuelve a la vez "comentarios o peticiones al
  vendedor" y la "pestana de atencion" de la empresa.
- **Clasificacion con BERT** al abrir el caso: prioridad (`HIGH`/`MEDIUM`/`LOW`), sentimiento y
  **vencimiento del SLA** (24h/48h/72h, configurable). Se degrada a una heuristica local si el
  microservicio no responde; el caso recuerda quien lo clasifico.
- **Maquina de estados del caso** (`ABIERTO`, `EN_ATENCION`, `ESPERANDO_CLIENTE`, `RESUELTO`,
  `CERRADO`): responder deja el caso a la espera del cliente; que el cliente conteste un caso resuelto
  lo reabre.
- **Chat general** de la empresa: lo lee todo el personal, publican quienes root autoriza
  (`CHAT_GENERAL_POST`); los **comunicados de root** (`BROADCAST_SEND`) destacan y notifican a todos.

### Etapa 5 — Administracion, departamentos, KPI y chat de equipo

- **Administracion por root** (`/empresa/administracion`), cada bloque tras su permiso:
  - **Cargos** (`ROLE_MANAGE`): crear, modificar y eliminar plantillas de permisos; los del sistema no
    se borran.
  - **Usuarios** (`USER_MANAGE`): crear e invitar por correo, asignar cargos, departamento y jefe,
    **ajuste fino de permisos** (concedidos/revocados) por persona, y eliminar. No se puede eliminar al
    root ni eliminarse a uno mismo.
  - **Departamentos** (`DEPARTMENT_MANAGE`): crear, modificar, asignar jefe y eliminar. La pertenencia
    vive en `User.departmentId`; la jerarquia de mando en `User.managerId` ("un jefe puede tener
    jefe"), con guardas anti-ciclo.
- **Metas KPI** (`/empresa/kpi`): por empresa, departamento o persona, con su **progreso calculado**
  sobre pedidos y casos reales (nunca almacenado). Metricas: ventas, pedidos entregados, casos
  resueltos, resueltos a tiempo y atendidos. Un jefe solo gestiona las metas de su departamento.
- **Panel de indicadores** con **graficas SVG propias** (barras, linea, dona y medidor), sin libreria
  externa: ventas por mes, casos por estado, productos mas vendidos, y casos por departamento y por
  persona.
- **Visor de actividad** (`/empresa/actividad`, `ACTIVITY_VIEW`): registro de las acciones de gestion,
  filtrable por usuario.
- **Chat por departamento o equipo**: canal privado por departamento (`dept:<id>`), con acceso por
  pertenencia (miembros, jefe y root).

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
cd backend 
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
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
cd backend 
./mvnw spring-boot:run
```

API en <http://localhost:8080>, documentacion interactiva en <http://localhost:8080/swagger-ui.html>.

Este es el comando de la **Opcion A** (Docker): toma la configuracion del `.env` de la raiz, que
apunta a Mongo en el **27018** y al SMTP que hayas configurado.

Solo si elegiste la **Opcion B** (MongoDB propio en el 27017, sin Docker) añade el perfil `local`:

```bash
cd backend 
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

> **Ojo con el correo:** el perfil `local` fuerza el envio a Mailpit (`127.0.0.1:1025`) y desactiva
> TLS. Si tienes el `.env` configurado para Gmail, arranca **sin perfil**: con `local` los correos
> se quedan en el buzon local en vez de salir a internet.

### 3. Frontend

```bash
cd frontend
npm install
npm start
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

**104 pruebas unitarias** (JUnit 5 + Mockito) cubren la logica critica: TOTP, permisos efectivos,
motor de descuentos y precios, reversion de stock, maquinas de estado de pedido y caso, clasificacion
BERT con su fallback, chat, metas KPI y guardas de administracion.

```bash
cd backend
./mvnw test
```

```bash
cd frontend
npm run build
```

Ademas, cada etapa se verifico recorriendo el flujo completo en el navegador, tanto del cliente como
de la empresa.

## Estructura

```
backend/src/main/java/com/ecommerce/
├─ auth/        registro, verificacion, sesion y contrasena
├─ user/        perfil, 2FA y baja de la cuenta
├─ company/     empresa, cargos, departamentos y administracion por root (Etapa 5)
├─ catalog/     catalogo publico y gestion de productos
├─ cart/        carrito por cliente (Etapa 2)
├─ order/       checkout, descuentos, precios, pedidos, reembolsos y factura (Etapas 2-3)
├─ support/     casos de atencion y su ciclo de estados (Etapa 4)
├─ chat/        chat general, comunicados y chat de departamento (Etapas 4-5)
├─ kpi/         metricas, metas KPI y panel de indicadores (Etapa 5)
├─ security/    JWT, permisos efectivos y TOTP
├─ notification/ activity/ mail/ nlp/ config/ common/

frontend/src/app/
├─ core/        servicios (auth, cart, catalog, support, admin), interceptor, guardas y permisos
├─ shared/      componentes reutilizables, incluidas las graficas SVG (charts.component.ts)
├─ layout/      encabezado y pie
└─ features/    home · catalog · auth · account · cart · orders · support · company
```

En `features/company/` viven las pantallas de empresa: productos, pedidos, reembolsos, casos, chat,
**administracion** (cargos/usuarios/departamentos), **KPI** (panel y metas) y **actividad**.

## Estado del proyecto

Las **cinco etapas** estan entregadas y verificadas de punta a punta. El detalle tecnico completo
—modelo de dominio, funcionamiento del backend, documentacion por clases, diagramas y decisiones de
arquitectura— esta en **[doc.md](doc.md)**.
