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
| Web framework | Apache Wicket 9.x (server-side component model) |
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

```
com.npsoftdev.fixsimulator
├── Main.java                    — entry point, starts Jetty
├── FixSimulatorApplication.java — Wicket WebApplication; wires all services;
│                                  hosts IRequestCycleListener for remember-me
├── FixSimulatorSession.java     — Wicket WebSession; holds authenticatedUser,
│                                  activeSessionId, activityDirection, etc.
│
├── gateway/
│   ├── GatewayConnectionService.java   — QuickFIX/J initiator management
│   ├── GatewayOrderService.java        — order tracking & snapshot capture
│   └── GatewayMessageLogService.java   — in-memory + restored message log
│
├── service/
│   ├── ConnectionService.java   — port: connect/disconnect/status/seq
│   ├── MessageLogService.java   — port: getMessages(sessionId)
│   ├── OrderService.java        — port: listOrders, sendNewOrder, cancelOrder
│   └── TradeService.java        — port: listTrades
│
├── template/
│   ├── FieldValue.java          — sealed: Literal | UserInput | Placeholder |
│   │                              Derived | Enumeration
│   ├── FieldSpec.java           — record(tag, FieldValue) + factory methods
│   ├── FixMessageTemplate.java  — immutable template with Builder API
│   ├── TemplateScope.java       — sealed: Global | Session(sessionId)
│   ├── PlaceholderType.java     — ORDER_ID | TRANSACT_TIME | SENDING_TIME |
│   │                              UUID | SESSION_SENDER | SESSION_TARGET
│   ├── PlaceholderResolver.java — port
│   ├── DefaultPlaceholderResolver.java
│   ├── ValueMappingService.java — port: named string→string lookup tables
│   ├── InMemoryValueMappingService.java (seeded with symbol-to-isin)
│   ├── TemplateRepository.java  — port: CRUD + findVisibleTo(sessionId)
│   ├── InMemoryTemplateRepository.java  (ConcurrentHashMap, not persisted)
│   ├── MessageDispatcher.java   — port: dispatch(Message, SessionID)
│   ├── FixMessageBuilder.java   — port: build(template, overrides, sessionId)
│   ├── DefaultFixMessageBuilder.java (two-pass resolution)
│   ├── TemplateService.java     — UI-facing façade port
│   ├── DefaultTemplateService.java
│   ├── MessageSnapshot.java     — record: header/body tags captured at send
│   ├── FixHeaderFields.java     — ENGINE_OWNED set: {8,9,10,34,35,49,52,56}
│   ├── DynamicValueRegistry.java
│   └── YamlValueMappingService.java  (persists to value-mappings.yaml)
│
├── user/
│   ├── User.java                — immutable record+Builder: username,
│   │                              displayName, passwordHash, email, roles[],
│   │                              active, maxSessions, timezone
│   ├── UserRepository.java      — port
│   ├── YamlUserRepository.java  — persists to data/users.yaml
│   ├── AuthService.java         — port: authenticate, session tracking
│   ├── DefaultAuthService.java  — BCrypt; in-memory session counters
│   ├── RememberMeService.java   — port: createToken, resolveToken, deleteToken
│   ├── DefaultRememberMeService.java — persists to data/remember-me-tokens.yaml
│   ├── UserPreferencesService.java   — port: getLastActiveSession, set…
│   ├── YamlUserPreferencesService.java — persists to data/user-preferences.yaml
│   ├── RoleRegistry.java
│   └── Permission.java
│
├── persistence/
│   └── YamlPersistenceService.java  — atomic write: tmp → rename; shared by
│                                       all YAML-backed repos
│
├── plugin/
│   ├── SimulatorPlugin.java
│   ├── PluginRegistry.java
│   ├── NavSection.java          — OVERVIEW | MONITORING | ADMIN
│   ├── DefaultFixGatewayPlugin.java
│   └── DefaultOrderManagerPlugin.java — constructs and wires all order/
│                                         template/user services; seeds templates
│
└── pages/
    ├── BasePage.java            — abstract; topbar, session switcher, nav,
    │                              seqno modal, userZoneId() helper
    ├── LoginPage.java           — sets remember-me cookie on login
    ├── HomePage.java            — dashboard: stats + recent messages
    ├── ConnectionManagementPage.java
    ├── OrdersPage.java          — New Order + Amend + Cancel + filter table
    ├── TradesPage.java          — execution reports table
    ├── FixActivityPage.java     — full FIX message log with detail modal
    ├── FixMessageTemplatesPage.java
    ├── FixMessageTemplateFormPage.java (add/edit template)
    │   └── TemplateFormPanel.*  — reusable panel for template field editor
    ├── ComposeMessagePanel.*    — reusable offcanvas panel (raw FIX send)
    ├── DynamicValuesPage.java
    ├── ValueMappingsPage.java
    ├── UserManagementPage.java
    ├── SystemLogsPage.java
    └── PagePermissions.java     — page → required Permission mapping
```

---

## 4. Data Files (all under `./data/` relative to working directory)

| File | Contents | Written by |
|---|---|---|
| `users.yaml` | User accounts | `YamlUserRepository` |
| `users.yaml.sample` | Starter template (committed) | manual |
| `remember-me-tokens.yaml` | Browser remember-me tokens (30-day TTL) | `DefaultRememberMeService` |
| `user-preferences.yaml` | Per-user last-active-session prefs | `YamlUserPreferencesService` |
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
Templates are currently stored in-memory (`InMemoryTemplateRepository`).
Value mappings are persisted to `data/value-mappings.yaml`
(`YamlValueMappingService`).

> **Note for future work:** Implement `YamlTemplateRepository` (or
> JSON-per-file) following the `YamlPersistenceService` atomic-write pattern.

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
