# FIX Message Template Feature — Implementation Spec

> **Status:** Engine layer landed. UI layer not started.
>
> **Audience:** Claude Code session that will continue this work in the terminal.

This document describes the FIX message template feature for FIXulator: what it
is, why it exists, the architecture that's already in place, and the work that
remains. Read this end-to-end before touching code.

---

## 1. Product intent

FIX testers need to send New Order Single (D), OrderCancelReplaceRequest (G),
and OrderCancelRequest (F) repeatedly with small variations. The previous form
hard-coded a single message shape and forced the tester to retype dynamic
values (ClOrdID, timestamps, ISIN) on every send.

The template feature gives the tester:

1. **Auto-filled dynamic values** — ClOrdID, TransactTime, SendingTime, UUID,
   SenderCompID/TargetCompID — supplied at send-time by a placeholder resolver.
2. **Mapped values** — derive one tag's value from another (canonical example:
   tag 48 SecurityID = ISIN for the tag 55 Symbol the user typed).
3. **Reusable templates** for any FIX message type, persisted across the
   lifetime of the JVM (and later, across restarts).
4. **Per-template + per-request customisation** — edit the template once,
   override individual fields per send.
5. **Scoping** — a template is either visible to every session (Global) or
   only to one named session.
6. **"Save as template" from history** — capture a previously-sent message
   and promote it into a new template.

---

## 2. Architecture overview

```
┌────────────────────────────────────────────────────────────────────┐
│                       Wicket page (OrdersPage)                     │
│                                                                    │
│  template picker  ──►  dynamic form  ──►  send                     │
│        │                                    │                      │
│        │           ┌────────────────────────┴──────────┐           │
│        ▼           ▼                                   ▼           │
│  TemplateService.findVisibleTo   TemplateService.send              │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│                       TemplateService (port)                       │
│        composes Repository · Builder · Dispatcher · sessionIDs     │
└────────────────────────────────────────────────────────────────────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌─────────────┐ ┌───────────────┐ ┌───────────┐ ┌───────────────────┐
│ Template-   │ │ FixMessage-   │ │ Message-  │ │ Placeholder-      │
│ Repository  │ │ Builder       │ │ Dispatcher│ │ Resolver +        │
│             │ │               │ │           │ │ ValueMapping-     │
│             │ │ resolves      │ │ → wire    │ │ Service           │
│ persists    │ │ FieldSpecs    │ │           │ │                   │
│ templates   │ │ in 2 passes   │ │ (Session- │ │ ORDER_ID,         │
│             │ │ (deferred     │ │ Facade::  │ │ TRANSACT_TIME,    │
│             │ │ Derived)      │ │ sendTo-   │ │ symbol→ISIN, …    │
│             │ │               │ │ Target)   │ │                   │
└─────────────┘ └───────────────┘ └───────────┘ └───────────────────┘
```

Every box is a Java interface in `com.npsoftdev.fixsimulator.template` with a
single default implementation. Swap any one for an alternative without
touching the rest.

---

## 3. What's already implemented (DO NOT REWRITE)

All under `src/src/main/java/com/npsoftdev/fixsimulator/template/`:

| File | Role |
| --- | --- |
| `TemplateScope.java` | Sealed: `Global` singleton + `Session(sessionId)` record. Owns `appliesTo(SessionID)`. |
| `PlaceholderType.java` | Enum: `ORDER_ID`, `TRANSACT_TIME`, `SENDING_TIME`, `UUID`, `SESSION_SENDER`, `SESSION_TARGET`. |
| `FieldValue.java` | Sealed: `Literal`, `UserInput(name, defaultValue)`, `Placeholder(type)`, `Derived(sourceTag, mappingName)`. |
| `FieldSpec.java` | Record `(tag, FieldValue)` with static convenience factories: `literal`, `userInput`, `placeholder`, `derived`. |
| `FixMessageTemplate.java` | Immutable. `Builder` API. Holds `id`, `name`, `description`, `beginString`, `msgType`, `scope`, ordered `List<FieldSpec>`. |
| `MessageSnapshot.java` | Record. `headerFields` + `bodyFields` + `msgType` + `beginString` + `capturedAt`. `capture(Message)` static factory. `flatFields()` view for legacy callers. |
| `FixHeaderFields.java` | Centralised set of engine-owned tags (8, 9, 10, 34, 35, 49, 52, 56). `isEngineOwned(tag)` is the only public method. |
| `PlaceholderResolver.java` | Port. `resolve(type, ResolutionContext)`. Context exposes `sessionID` and fields resolved so far. |
| `DefaultPlaceholderResolver.java` | Default impl. `AtomicLong` order ID counter seeded from epoch ms. UTC timestamp format `yyyyMMdd-HH:mm:ss.SSS`. |
| `ValueMappingService.java` | Port. Named string→string tables. `lookup`, `put`, `remove`, `mappingNames`. |
| `InMemoryValueMappingService.java` | Default impl. Seeded with `symbol-to-isin` for AAPL, MSFT, GOOG, TSLA, AMZN. |
| `TemplateRepository.java` | Port. CRUD + `findVisibleTo(SessionID)` (union of Global + matching Session scope). |
| `InMemoryTemplateRepository.java` | Default impl. `ConcurrentHashMap` keyed by template id. |
| `MessageDispatcher.java` | Narrow port. Single method `dispatch(Message, SessionID) throws SessionNotFound`. Wired to `LiveSessionFacade::sendToTarget`. |
| `FixMessageBuilder.java` | Port. `build(template, overrides, sessionID) → Message`. |
| `DefaultFixMessageBuilder.java` | Default impl. Two-pass resolution (Derived deferred until upstream tags are resolved). Routes header vs body via `Message.isHeaderField`. Skips engine-owned tags. Uses `instanceof` patterns (Java 16+) — **do not switch to type-pattern switch; project targets Java 17 and that feature is preview-only there**. |
| `TemplateService.java` | UI-facing facade port. `send`, `captureFromMessage`, repository pass-throughs. |
| `DefaultTemplateService.java` | Default impl. Composes Repository / Builder / Dispatcher / sessionIDs. |

### Integrated changes

- **`OrderService.java`** — added `Optional<MessageSnapshot> findSnapshot(String sessionId, String clOrdId)`. The interface comment explicitly directs new callers to `TemplateService.send(...)` rather than `sendNewOrder(...)`.
- **`GatewayOrderService.java`** — parallel `Map<String, Map<String, MessageSnapshot>> snapshots` populated in `onOutboundMessage`. `sendNewOrder` and `cancelOrder` left unchanged for backward compatibility with the existing UI form and tests.
- **`DefaultOrderManagerPlugin.initialize`** — constructs the full template stack, seeds a `built-in.nos.default` New Order Single template that exercises every `FieldValue` variant, and publishes `TemplateService` + `ValueMappingService` on the application.
- **`FixSimulatorApplication`** — added `getTemplateService()` / `setTemplateService(...)` and `getValueMappingService()` / `setValueMappingService(...)`.

### Backward compatibility

All existing tests under `src/test/java/com/npsoftdev/fixsimulator/gateway/GatewayOrderServiceTest.java` should still pass. The old `sendNewOrder`/`cancelOrder` paths are untouched and continue to write into the same `orders` map; the snapshot capture is additive.

---

## 4. What's NOT implemented (work for this Claude Code session)

Roughly ordered by user value. Each item is sized to one focused PR.

### 4.1 — Template-driven dynamic form on `OrdersPage` (high priority)

**Goal:** Replace the hard-coded New Order modal with a template picker plus a
form that's generated from the selected template's `FieldSpec` list.

**Files:**
- `src/main/java/com/npsoftdev/fixsimulator/pages/OrdersPage.java`
- `src/main/java/com/npsoftdev/fixsimulator/pages/OrdersPage.html`

**Design:**
1. Add a `DropDownChoice<FixMessageTemplate>` populated from
   `templateSvc().findVisibleTo(activeSessionId)`. Default to `built-in.nos.default`.
2. When the selected template changes, render a `ListView` of form fields,
   one per `FieldSpec` whose `FieldValue instanceof FieldValue.UserInput`.
   Each row shows the field label (use tag-number lookup for now — there's
   no dictionary integration yet), a text input pre-filled from the
   `UserInput.defaultValue()`, and a small icon indicating the value source
   (placeholder / derived / literal — those are read-only previews).
3. On submit, build `Map<String,String>` of overrides from the form fields
   and call `templateSvc().send(activeSessionId, selectedTemplate.id(), overrides)`.
4. Remove the magic-number FIX-code translation helpers (`sideCode`,
   `ordTypeCode`, `tifCode`) — those become template concerns now.
5. The old `sendNewOrder` path can be deleted once you're sure no tests
   depend on it. (The interface method stays for now; only the form should
   stop calling it.)

**Service accessor (mirror existing pattern):**
```java
private static TemplateService templateSvc() {
    return ((FixSimulatorApplication) Application.get()).getTemplateService();
}
```

**Validation:** keep using QuickFIX/J types where possible. For the demo
template above, `UserInput("quantity")` needs to coerce to integer-ish and
`UserInput("price")` to decimal-ish at the UI layer — though the wire format
is string so it's not strictly required.

### 4.2 — Template manager page (medium priority)

**Goal:** Let the tester author, edit, scope, and delete templates without
restarting.

**Files (new):**
- `src/main/java/com/npsoftdev/fixsimulator/pages/TemplatesPage.java`
- `src/main/java/com/npsoftdev/fixsimulator/pages/TemplatesPage.html`
- Register in `FixSimulatorApplication.registerBuiltInPlugins()` as a new
  `DefaultFixGatewayPlugin("templates", "Templates", "bi-files", NavSection.MONITORING, TemplatesPage.class)`.

**Design:**
- Table of all templates from `templateSvc().findVisibleTo(...)` (or
  `templateRepo().findAll()` if you want admins to see everything).
- Per row: edit / clone / delete actions.
- Edit form for one template: name, description, msgType dropdown
  (D/F/G/8/…), scope (Global vs Session-picker), and an editable table of
  `FieldSpec` rows. Each row picks tag (int input) and `FieldValue` kind
  (radio: Literal | UserInput | Placeholder | Derived) plus the kind-specific
  inputs.
- "Add field" / "Remove field" / drag-reorder buttons.

This is the biggest UI piece — break it into small commits.

### 4.3 — "Save as template" from the orders table (medium priority)

**Goal:** Right-click (or button) on any row in the orders table to capture
that exact message as a starting-point template.

**Steps:**
1. On the orders table row, add a "Save as template" icon button.
2. Click handler reads `orderSvc().findSnapshot(sessionId, clOrdId)`.
3. Open a modal asking for template name + scope.
4. On confirm, call `templateSvc().captureFromMessage(generatedId, name, snapshot, scope)`
   and save it via `templateSvc().save(template)`.
5. Capture builds a template with every retained field as `FieldValue.Literal`.
   Engine-owned fields are already stripped by `captureFromMessage`.
6. Direct the user to the template editor (4.2) so they can promote
   individual fields to UserInput / Placeholder / Derived.

### 4.4 — OrderCancelReplaceRequest (G) seed template (small)

**Goal:** Wire amend support. Currently the amend button in `OrdersPage` is a
`TODO`.

**Steps:**
1. Add a second seeded template in `DefaultOrderManagerPlugin.seedBuiltInTemplates`:
   id `built-in.ocrr.default`, msgType `MsgType.ORDER_CANCEL_REPLACE_REQUEST` ("G").
2. Fields: OrigClOrdID (UserInput "origClOrdId"), ClOrdID (Placeholder ORDER_ID),
   Symbol / Side / OrderQty / Price / OrdType (UserInput, defaults from the
   original order — the UI populates the overrides map from the row).
3. Hook the amend button in `OrdersPage` to open a modal pre-filled from the
   selected order's snapshot, then call `templateSvc().send(...)`.

### 4.5 — Value-mapping admin UI (low priority)

**Goal:** Edit the `symbol-to-isin` (and future) lookup tables from the UI
instead of restarting the JVM.

**Files (new):**
- `src/main/java/com/npsoftdev/fixsimulator/pages/MappingsPage.java`
- `src/main/java/com/npsoftdev/fixsimulator/pages/MappingsPage.html`

**Design:** dropdown for mapping name, table of key/value rows with add /
edit / delete. Backed by `app.getValueMappingService()`.

### 4.6 — Persistence (medium priority, blocking for production use)

**Goal:** Templates and mapping tables survive restart.

**Recommended approach:** JSON-per-template on disk (one file per template
in a configurable directory), because testers can then version-control
their templates outside the app.

**Files:**
- New `FileTemplateRepository implements TemplateRepository` under `template/`.
  Use Jackson (add to `pom.xml`) or `openjson` (already a transitive dep —
  see `target/fix-simulator/WEB-INF/lib/openjson-*.jar`).
- New `FileValueMappingService implements ValueMappingService`.
- Swap construction in `DefaultOrderManagerPlugin.initialize`. No other
  code changes — that's the point of the port/adapter split.
- Templates directory: `~/.fixsimulator/templates/` (read from system
  property or config file).

**Serialisation shape (proposed JSON):**
```json
{
  "id": "built-in.nos.default",
  "name": "New Order Single — default",
  "description": "...",
  "beginString": "FIX.4.4",
  "msgType": "D",
  "scope": {"type": "Global"},
  "fields": [
    {"tag": 11, "value": {"kind": "Placeholder", "type": "ORDER_ID"}},
    {"tag": 55, "value": {"kind": "UserInput", "name": "symbol"}},
    {"tag": 48, "value": {"kind": "Derived", "sourceTag": 55, "mappingName": "symbol-to-isin"}},
    {"tag": 22, "value": {"kind": "Literal", "value": "4"}}
  ]
}
```

### 4.7 — Repeating-group support (defer until first use case)

The current `FixMessageBuilder` and `MessageSnapshot` ignore repeating groups
(NoPartyIDs, NoAllocs, etc.). Add `Map<Integer, List<GroupSnapshot>>` to
`MessageSnapshot` and a `GroupSpec` variant of `FieldSpec` when the first
template needs it. Don't speculate — wait for a test case.

### 4.8 — Tests (do alongside each piece above)

Existing tests stay green. New tests to add under
`src/test/java/com/npsoftdev/fixsimulator/template/`:

- `DefaultFixMessageBuilderTest`:
  - resolves every `FieldValue` variant correctly
  - Derived sees the resolved Symbol value regardless of declaration order
  - engine-owned tags in a template are silently skipped
  - missing UserInput falls through to defaultValue
  - missing UserInput with no default produces no field
- `DefaultPlaceholderResolverTest`: ORDER_ID monotonic; timestamps formatted
  correctly; SESSION_SENDER/TARGET extracted from SessionID.
- `InMemoryTemplateRepositoryTest`: `findVisibleTo` correctly merges Global +
  matching Session and excludes non-matching Session.
- `DefaultTemplateServiceTest`: end-to-end with a mock `MessageDispatcher`.

---

## 5. Design decisions worth preserving

1. **Sealed interface for `FieldValue`.** Adding a new variant should require
   editing exactly two files: `FieldValue.java` (add the record) and
   `DefaultFixMessageBuilder.java` (handle it in the resolution loop). Don't
   move resolution logic into the variants themselves — keeping it in the
   builder lets the same variant data drive both rendering (UI) and
   resolution (engine) without inheritance gymnastics.

2. **Two-pass resolution.** Pass 1 resolves everything except Derived, Pass 2
   resolves Derived against the working map. This decouples template
   author intent from declaration order — Derived(SecurityID from Symbol)
   works whether SecurityID appears before or after Symbol in the field list.

3. **Engine-owned tag exclusion is centralised.** `FixHeaderFields.ENGINE_OWNED`
   is the only place that lists session/transport-level tags. Both
   `captureFromMessage` (strip on the way in) and `DefaultFixMessageBuilder`
   (skip on the way out) consult it. If you add an exclusion later, edit
   once.

4. **`MessageDispatcher` is narrower than `SessionFacade`.** The full façade
   has logon/logout/seqnum methods. The template engine needs none of that —
   just the send. The narrow port makes the template stack trivially testable
   (pass a lambda that captures the outgoing message).

5. **The template carries `msgType` as metadata, not as a `FieldSpec`.** Tag 35
   is engine-owned (the builder writes it from `template.msgType()`) and
   `FixHeaderFields.ENGINE_OWNED` enforces that you can't accidentally put
   it in the field list.

6. **Scope is enforced at lookup time, not at storage time.** `findVisibleTo`
   filters; `findAll` doesn't. This means an admin UI listing every template
   (including non-visible ones) is one call away without a separate API.

7. **`OrderService` stays focused on order observation.** Template-based
   sending lives on `TemplateService` because templates aren't an
   order-only concept — eventually the tester will template OrderStatusRequest,
   QuoteRequest, anything. Don't pile more send methods onto `OrderService`.

8. **Backward compatibility is real.** `OrderService.sendNewOrder` and the
   existing form are intentionally left functional. Migrate the form to
   `TemplateService.send` (section 4.1), then deprecate `sendNewOrder` in a
   later pass. Don't break existing tests until the UI migration is
   green.

---

## 6. Conventions to follow

- Java 17 target. Records, sealed types, `instanceof` patterns are
  available. **Type-pattern switches are NOT** (they're preview in 17,
  stable in 21). Use `if (x instanceof Foo f)` chains instead.
- Every interface in `template/` extends `Serializable` so Wicket can
  serialise references in its page store. New ports should follow suit.
- Keep package layout flat under `com.npsoftdev.fixsimulator.template`. No
  sub-packages until there's a real reason.
- Javadoc on every interface and on every non-trivial public method. The
  existing files set the tone — match it.
- Comments explain *why*, not *what*. Look at how the two-pass resolution
  is commented in `DefaultFixMessageBuilder` for the right level.
- New UI pages follow the existing pattern: `BasePage` subclass, paired
  `.html`, registered via a plugin in `FixSimulatorApplication`.

---

## 7. Quick verification path

To prove the template engine works end-to-end against a live FIX session:

```java
// In any debug servlet, REPL, or temporary main:
FixSimulatorApplication app = (FixSimulatorApplication) Application.get();
TemplateService svc = app.getTemplateService();
String sessionId = "FIX.4.4:SIMULATOR->EXCHANGE";  // whatever you have configured
Map<String,String> overrides = Map.of(
    "symbol",   "AAPL",
    "side",     "1",      // Buy
    "quantity", "100",
    "price",    "150.50"
);
Message sent = svc.send(sessionId, "built-in.nos.default", overrides);
System.out.println(sent.toString());
// Expect: 35=D, 11=<long>, 60=<utc>, 55=AAPL, 48=US0378331005, 22=4,
//         54=1, 38=100, 44=150.50, 40=2, 59=0, 21=1
```

The `built-in.nos.default` template is seeded automatically on application
startup by `DefaultOrderManagerPlugin.seedBuiltInTemplates`.
