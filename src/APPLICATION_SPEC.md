# FIXulator — Application Specification

> **Purpose:** Living reference document for AI coding sessions.  Read this
> before touching any code.  Update it whenever a significant feature is added
> or changed.
>
> **Last updated:** 2026-07-20

---

## 1. What is FIXulator?

FIXulator is an embedded-Jetty web application that acts as a **FIX protocol
initiator simulator**.  It lets QA engineers and developers:

- Manage one or more FIX sessions (connect, disconnect, configure).
- Send New Order Single, Amend, and Cancel messages through templates or raw
  compose.
- Monitor inbound/outbound messages in real-time.
- Track the full order lifecycle (New → PartFilled → Filled → Cancelled …).
- Compose and send any arbitrary raw FIX message to the active session.
- Template parameterised FIX messages with placeholders, user inputs,
  value-mapped derived fields, and enumeration dropdowns.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Build | Maven (fat JAR via Maven Shade plugin) |
| HTTP server | Embedded Jetty (started by `Main.java`) |
| Web framework | Apache Wicket 10.3.0 (server-side component model) |
| FIX engine | QuickFIX/J 2.3.1 (initiator mode) |
| Serialisation | Jackson + jackson-dataformat-yaml |
| Password hashing | jBCrypt |
| CSS/JS | Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 (CDN) |
| Default port | 8080 (configurable via CLI argument to `Main`) |

**Build and run:**
```bash
cd src
mvn package -q
java -jar target/fix-simulator.jar          # port 8080
java -jar target/fix-simulator.jar 9090     # custom port
```

**CSP policy:** `script-src 'self' https://cdn.jsdelivr.net` — no inline
`onclick` or `<script>` blocks.  All JavaScript uses `addEventListener` in a
`DOMContentLoaded` IIFE.

---

## 3. Package Layout

Source is grouped by **plugin**, not by technical layer.  `core/` is the platform
that loads plugins and provides the shell; each plugin under `plugins/` owns its
own ports, implementations, and UI.

Inside every plugin:

| Sub-package | Holds | Visible to |
|---|---|---|
| `api/` | Ports and shared types | Other plugins compile against this |
| `internal/` | Implementations | The plugin itself |
| `ui/` | Wicket pages/panels + their `.html`/`.js`/`.css` | The plugin itself |

> Wicket resolves markup by classpath location, so a page's `.html` (and any
> `.js`/`.css` loaded via `PackageResourceReference`) must stay in the same
> package as its class.  Move them together, always.

```
com.npsoftdev.fixsimulator
├── Main.java                       — entry point, starts Jetty
│
├── core/                           — the platform
│   ├── FixSimulatorApplication.java  — Wicket WebApplication; wires all
│   │                                   services; hosts IRequestCycleListener
│   │                                   for remember-me
│   ├── FixSimulatorSession.java      — WebSession; authenticatedUser,
│   │                                   activeSessionId, activityDirection
│   ├── AppHome.java                  — resolves the runtime home holding
│   │                                   data/, logs/, fix-gateway.cfg
│   ├── plugin/                     — the plugin contract itself
│   │   ├── SimulatorPlugin.java      — id, label, icon, NavSection, pageClass,
│   │   │                               initialize(app)
│   │   ├── PluginRegistry.java
│   │   └── NavSection.java           — OVERVIEW | MONITORING | ADMIN
│   ├── ui/                         — app shell, owned by no plugin
│   │   ├── BasePage.java (+ .html)   — topbar, session switcher, nav, seqno
│   │   │                               modal, userZoneId()
│   │   ├── app.css
│   │   ├── HomePage.java (+ .html)   — dashboard: stats + recent messages
│   │   ├── JsEscape.java             — public; used by pages in every plugin
│   │   └── PagePermissions.java      — page → required Permission mapping
│   └── logging/
│       ├── LogFileService.java       — port: read the active Logback file
│       ├── DefaultLogFileService.java  — 5 MB cap per readFrom; UTF-8 safe
│       │                                 across the read boundary
│       └── SystemLogsPage.java (+ .html/.css/.js)
│
└── plugins/
    ├── connection/                 — 1. FIX connections
    │   ├── api/       ConnectionService, MessageLogService, SessionFacade,
    │   │              FixMessageListener
    │   ├── internal/  GatewayConnectionService, GatewayMessageLogService,
    │   │              LiveSessionFacade
    │   ├── ui/        ConnectionManagementPage, FixActivityPage (+ .js),
    │   │              ComposeMessagePanel (+ .js)
    │   └── DefaultFixGatewayPlugin.java
    │
    ├── order/                      — 2. orders + trades (needs connection)
    │   ├── api/       OrderService, TradeService
    │   ├── internal/  GatewayOrderService, GatewayTradeService,
    │   │              OrderTradeCacheService
    │   ├── ui/        OrdersPage, TradesPage
    │   └── DefaultOrderManagerPlugin.java  — also wires template + user services
    │
    ├── template/                   — 3. templates, dynamic values, mappings
    │   ├── api/       TemplateService, TemplateRepository, ValueMappingService,
    │   │              DynamicValueRegistry, FixMessageBuilder, MessageDispatcher,
    │   │              PlaceholderResolver, FieldSpec, FieldValue,
    │   │              FixMessageTemplate, TemplateScope, PlaceholderType,
    │   │              MessageSnapshot, DynamicValueDefinition, FixHeaderFields
    │   ├── internal/  Default* (builder, resolver, service),
    │   │              InMemory* (repository, registry, mappings),
    │   │              Yaml* (repository, registry, mappings)
    │   └── ui/        FixMessageTemplatesPage, FixMessageTemplateFormPage,
    │                  TemplateFormPanel (+ .js), DynamicValuesPage,
    │                  ValueMappingsPage
    │
    ├── persistence/                — 4. persistence for every other plugin
    │   └── api/       YamlPersistenceService  — atomic write: tmp → rename
    │
    └── user/                       — 5. authentication + authorization
        ├── api/       AuthService, UserRepository, RememberMeService,
        │              UserPreferencesService, User, Permission, RoleRegistry
        ├── internal/  DefaultAuthService, DefaultRememberMeService,
        │              YamlUserRepository, YamlUserPreferencesService
        └── ui/        LoginPage (+ .css), UserManagementPage
```

### Known coupling to resolve

The grouping is done; the decoupling is not.  These are the edges that still
stop a plugin from being independently loadable:

1. `order/api/OrderService` imports `template/api/MessageSnapshot`, so plugin 2
   requires plugin 3 at compile time.  It should be optional.
2. `core/ui/HomePage` calls `FixActivityPage.parseTags/msgTypeName/buildSummary`
   — the dashboard reaches into a plugin page for FIX formatting.  Those helpers
   were made `public` so the split would compile; they belong in a shared
   FIX-format utility.
3. `persistence` exposes the concrete `YamlPersistenceService` as its `api`.
   Extracting a `PersistenceService` port would let a plugin persist elsewhere.
4. `DefaultFixGatewayPlugin` is instantiated nine times as a generic nav-entry
   holder for unrelated pages.  Nav registration and plugin identity should be
   separate concerns.

---

## 4. Data Files (all under `<app home>/data/`)

The **app home** is resolved by `core/AppHome.java`:

1. `-Dfixulator.home=<path>` — explicit override, wins everywhere.
2. `FIXULATOR_HOME` environment variable — same, for service managers.
3. `-Dfixulator.packaged=true` (set by the jpackage launchers) — the per-user
   application-data directory: `%LOCALAPPDATA%\FIXulator`,
   `~/Library/Application Support/FIXulator`, or `$XDG_DATA_HOME/fixulator`.
4. Otherwise the working directory — unchanged development behaviour.

Installed builds must not use the working directory: jpackage installs into
`/Applications`, `C:\Program Files`, or `/opt/fixulator`, none of which a normal
user may write to.

| File | Contents | Written by |
|---|---|---|
| `users.yaml` | User accounts | `YamlUserRepository` |
| `users.yaml.sample` | Starter template (committed) | manual |
| `remember-me-tokens.yaml` | Browser remember-me tokens (30-day TTL) | `DefaultRememberMeService` |
| `user-preferences.yaml` | Per-user last-active-session prefs | `YamlUserPreferencesService` |
| `templates.yaml` | FIX message templates | `YamlTemplateRepository` |
| `dynamic-values.yaml` | Custom dynamic value definitions | `YamlDynamicValueRegistry` |
| `value-mappings.yaml` | Named key→value mapping tables | `YamlValueMappingService` |
| `orders-*.yaml` | Persisted order maps per session | `GatewayOrderService` |
| `trades-*.yaml` | Persisted trade maps per session | `GatewayOrderService` |
| `messages-*.yaml` | Persisted message log per session | `GatewayMessageLogService` |

**Gitignore rule:** `*.yaml` is ignored; `*.yaml.sample` is tracked.
`src/fix-gateway.cfg` (written during tests) is also ignored.

---

## 5. FIX Session Configuration

Stored in `fix-gateway.cfg` in the working directory (or on the classpath as a
fallback).  Managed through the Connection Management page.  Uses QuickFIX/J
`FileStoreFactory` so sequence numbers survive restarts.

---

## 6. Authentication & Session Persistence

### Login flow
1. `LoginPage` validates credentials via `AuthService.authenticate` (BCrypt).
2. Session limit check via `AuthService.canStartSession`.
3. `session.bind()` → `session.signIn(user)` → `AuthService.registerSession`.
4. `FixSimulatorApplication.restoreActiveSession` restores the last-selected
   FIX session (validates against live connection list; ignores stale IDs).
5. A remember-me token is generated (`DefaultRememberMeService.createToken`),
   persisted to `data/remember-me-tokens.yaml`, and written as an `HttpOnly`
   browser cookie (`FIXSIM_REMEMBER_ME`, 30-day `Max-Age`, path `/`).

### Remember-me auto-login (on every anonymous request)
An `IRequestCycleListener.onBeginRequest` in `FixSimulatorApplication` checks
for the `FIXSIM_REMEMBER_ME` cookie, resolves the token, looks up the user, and
auto-signs in — exactly like a manual login, including calling
`restoreActiveSession`.

### Sign-out
`BasePage` sign-out link: unregisters session, deletes the server-side token,
clears the cookie (Max-Age=0), invalidates the Wicket session.

### FIX session selection
`BasePage` topbar session switcher persists the chosen session ID via
`UserPreferencesService.setLastActiveSession` immediately on switch.

---

## 7. User Record Fields

```java
String       username        // login name, immutable after creation
String       displayName     // shown in topbar
String       passwordHash    // BCrypt, null = no password
String       email
List<String> roles           // "Admin" | "Tester"
boolean      active          // false = cannot log in
int          maxSessions     // 0 = unlimited concurrent sessions
String       timezone        // IANA timezone ID, null = UTC
                             // e.g. "Asia/Bangkok", "America/New_York"
```

**Roles:**
- `Admin` — User Management, System Logs; implies Tester permissions.
- `Tester` — FIX connections, orders, trades, templates, activity, compose.

---

## 8. Timezone Handling

`BasePage.userZoneId()` resolves the authenticated user's `timezone` field to a
`java.time.ZoneId`, falling back to UTC for null/blank/invalid values.

All timestamps in the UI use this zone:
- **Dashboard:** "last updated" clock, recent-message timestamps.
- **FIX Activity:** row timestamps (`HH:mm:ss.SSS`).
- **Orders → Send Time:** FIX tag 60 `TransactTime` parsed as UTC
  (`yyyyMMdd-HH:mm:ss[.SSS]`) then reformatted in user's zone.
- **Trades → TransactTime:** same parsing + reformatting.

Timezone is set per user in the User Management edit modal (dropdown of ~25
curated IANA zones, displayed as `(UTC+07:00) Asia/Bangkok`).

---

## 9. FIX Message Templates

### FieldValue variants (sealed interface)

| Variant | Purpose |
|---|---|
| `Literal(value)` | Fixed wire value |
| `UserInput(name, displayName, defaultValue)` | Free-text field shown in the form |
| `Placeholder(type)` | Auto-generated at send time (ORDER_ID, TRANSACT_TIME, …) |
| `Derived(sourceTag, mappingName)` | Looks up `sourceTag`'s resolved value in a named mapping table |
| `Enumeration(options)` | Dropdown; `options` is `List<String>` of `KEY:LABEL` pairs (e.g. `"1:Buy"`) or plain values (backward compat) |

### Enumeration KEY:LABEL format
Options are stored as comma-separated strings in the template YAML.
`KEY` is the FIX wire value; `LABEL` is shown in the UI.
Plain values (no colon) are treated as key = label.

### Template scope
`Global` — visible to all sessions.
`Session(sessionId)` — visible only to that session.
`findVisibleTo(sessionId)` returns the union of Global + matching Session.

### Integration with Orders page
When a template is applied to the New Order or Amend form:
- The Side (tag 54), Order Type (tag 40), and TIF (tag 59) dropdowns are
  populated from the template's `Enumeration` fields for those tags.
- Extra `UserInput` / `Enumeration` fields beyond the standard set are rendered
  below the core fields as "Additional Fields".
- Standard tags handled by the form: `{55, 54, 44, 38, 40, 59, 41}`.

### Message builder two-pass resolution
Pass 1: resolve everything except `Derived`.
Pass 2: resolve `Derived` using the results of Pass 1.
This makes declaration order irrelevant for Derived fields.

### Engine-owned tags
`FixHeaderFields.ENGINE_OWNED = {8, 9, 10, 34, 35, 49, 52, 56}`
These are set by the QuickFIX/J engine and are silently skipped by the builder
and stripped by `captureFromMessage`.

### Persistence
Templates are persisted to `data/templates.yaml` (`YamlTemplateRepository`),
value mappings to `data/value-mappings.yaml` (`YamlValueMappingService`), and
custom dynamic values to `data/dynamic-values.yaml` (`YamlDynamicValueRegistry`).
All three are wired in `DefaultOrderManagerPlugin` and share one
`YamlPersistenceService` instance, so every write is atomic (tmp → rename).

`InMemoryTemplateRepository` and `InMemoryDynamicValueRegistry` remain as the
non-persistent implementations of their ports — neither is wired into the
running application; only the former has a unit test.

### Seeded built-in templates
`DefaultOrderManagerPlugin` seeds:
- `built-in.nos.default` — New Order Single (D): ClOrdID (ORDER_ID), Symbol
  (UserInput), Side/OrdType/TIF (Enumeration defaults), Qty/Price (UserInput),
  SecurityID/IDSource (Derived from Symbol via `symbol-to-isin`).
- `built-in.ocrr.default` — OrderCancelReplaceRequest (G): same fields plus
  OrigClOrdID (UserInput).

---

## 10. Compose Message Panel (`ComposeMessagePanel`)

Reusable Wicket `Panel` (offcanvas drawer) present on FIX Activity, Orders, and
Trades pages.

**Features:**
- Session info strip (auto-refreshes every 5 s via Ajax timer).
- Raw FIX text area + delimiter field.
- **Parse button** — client-side only; shows field-by-field breakdown with tag
  names and decoded values from `window.FIX` namespace (set by
  `ComposeMessagePanel.js`).
- **Send button** — calls `ConnectionService.sendRaw(sessionId, raw, delim)`.
  Engine sets session-level tags (8/49/56/34/52/10); user input for those tags
  is ignored.

**JS architecture:**
- `ComposeMessagePanel.js` — loaded by the panel's `renderHead()`; exports
  `window.FIX = { TAG_NAMES, VALUE_DECODERS, parseFix, escHtml }`.
- `FixActivityPage.js` — consumes `window.FIX` for the message detail modal.

---

## 11. FIX Activity Page

- Shows all inbound/outbound FIX messages plus application events (SYSTEM
  direction) for the active session.
- **Ordering:** newest-first (service returns messages in reverse-insert order).
- **Filters:** Direction (All/Sent/Received), Hide Heartbeats checkbox;
  persisted in `FixSimulatorSession` so they survive page navigations.
- **Detail modal:** click a row to see all parsed tags with names and decoded
  values; also shows raw FIX string.
- **"Create Template from FIX Message":** captures the raw message into a new
  template via `TemplateService.captureFromMessage`, then navigates to the
  template edit form.
- Auto-refreshes every 3 s.

---

## 12. Orders Page

### New Order dialog (`newOrderModal`)
Standard fields: Symbol, Side, Qty, Price, OrdType, TIF (all loaded from
template where available).  ClOrdID is auto-generated.  Extra template fields
rendered below as "Additional Fields".

### Amend Order dialog (`amendOrderModal`)
Pre-filled from the order row's FIX fields.  New ClOrdID is auto-generated.
OrigClOrdID shown as read-only strip.

### Cancel
Direct button on each row; sends OrderCancelRequest (F) immediately.

### Table + filters
Filters: ClOrdID (text), Symbol (text), Side (dropdown), OrdType (dropdown),
TIF (dropdown), Status (dropdown).  All filters trigger AJAX re-render.

### Tooltip on action buttons
Shows the name of the applied template (populated by `DefaultOrderManagerPlugin`
via `findTemplate(msgType)`).

---

## 13. Trades Page

Shows only execution reports with ExecType PartialFill (1), Fill (2), or
Trade (F).  Filters: ExecID, ClOrdID, ExecType, Symbol, Side.  Pagination 20
per page.  Auto-refresh every 3 s.

---

## 14. Key Design Decisions

1. **Sealed `FieldValue`** — new variants require editing only `FieldValue.java`
   and `DefaultFixMessageBuilder.java`.  Do not move resolution logic into
   variants.

2. **Two-pass builder** — Pass 1: all non-Derived; Pass 2: Derived against
   resolved map.  Declaration order is irrelevant.

3. **Engine-owned tag exclusion is centralised** in `FixHeaderFields`.  Both
   `captureFromMessage` and the builder consult it.

4. **`MessageDispatcher` is narrower than `SessionFacade`** — only `dispatch`.
   This keeps the template stack testable with a lambda mock.

5. **FIX model fields hold wire codes** — `NewOrderModel.side` stores `"1"` /
   `"2"`, not `"BUY"` / `"SELL"`.  Display labels come via `IChoiceRenderer`
   backed by `List<FieldChoice>`.  The old `sideCode()` / `ordTypeCode()` /
   `tifCode()` helpers were deleted.

6. **`ComposeMessagePanel` is self-contained** — installs its own Ajax timer
   for the session info strip.  Host pages just `add(new ComposeMessagePanel(...))`
   and optionally pass components to refresh on send.

7. **Remember-me uses an `IRequestCycleListener`** not a filter or
   `AuthorizationStrategy`, so the auto-login always runs early enough for
   subsequent authorization checks to see the authenticated session.

8. **`restoreActiveSession` validates** the saved FIX session ID against the
   live connection list before restoring it.  Stale IDs are silently ignored.

9. **Atomic YAML writes** via `YamlPersistenceService`: write to `.tmp` sibling,
   then rename (POSIX atomic; best-effort on Windows).  All YAML repos share
   one `YamlPersistenceService` instance per data directory.

10. **CSP blocks inline JS.** All JS uses `addEventListener('DOMContentLoaded',
    function() { ... })` inside an IIFE.  Never add `onclick=` attributes or
    `<script>` blocks that aren't loaded via Wicket's `renderHead`.

---

## 15. Conventions

- **Java 17.** Records, sealed types, `instanceof` patterns OK.
  Type-pattern switches are **NOT** available (preview in 17, stable in 21).
  Use `if (x instanceof Foo f)` chains.
- Every port/interface in `template/` and `user/` should implement
  `Serializable` (Wicket serialises page state).
- `BasePage` subclass + paired `.html` file + plugin registration in
  `FixSimulatorApplication.registerBuiltInPlugins()` for new pages.
- YAML files go under `./data/` (relative to working directory =
  `System.getProperty("user.dir")`).
- Comments explain *why*, not *what*.
- Do not add docstrings, comments, or type annotations to code you didn't
  change.

---

## 16. Navigation Structure (sidebar)

```
OVERVIEW
  Dashboard

MONITORING
  Orders
  Trades
  FIX Activity

ADMIN
  FIX Connections
  FIX Message Templates
  Dynamic Values
  Value Mappings
  User Management
  System Logs
```

Permission gating:
- `View FIX Message Templates` — Tester role
- `View Manage FIX Connections` — Admin role
- `View User Management` — Admin role
- `View System Logs` — Admin role

---

## 17. Plugin Architecture

`PluginRegistry` holds `List<SimulatorPlugin>`.  Each plugin has an `id`,
`label`, `iconClass`, `NavSection`, optional `pageClass`, and `initialize(app)`
method.

`DefaultFixGatewayPlugin` — registers pages and wires the QuickFIX/J engine.
`DefaultOrderManagerPlugin` — wires `OrderService`, `TradeService`,
`TemplateService`, `ValueMappingService`, `UserRepository`, `AuthService`,
`LogFileService`; seeds built-in templates; restores persisted orders/trades/messages.
