# Product Analytics Event Taxonomy — Web & Mobile

**Status:** Draft v0.2
**Scope:** Standard, domain-agnostic **UI events** (and the generic `track` mechanism) shared across **web**, **iOS**, and **Android**.
**Out of scope:** The transport envelope, `context`, telemetry/SDK fields, sampling, trace/session IDs — all auto-populated by our observability SDK and **owned elsewhere**. This doc governs only the **event name** and the **`event.*` payload**.

---

## 1. Purpose

Standardize the **event name** and the **`event.*` payload** for structural UI events so the same metric (views, clicks, app opens) means the same thing on web and mobile.

Each event in our pipeline is an OpenTelemetry span (or log) where:

- the **span/event name** is the event name (`click`, `page_view`, …), and
- the developer-meaningful UI data lives in the **`event.*` attribute namespace** (rendered as a nested `event` object).

Everything else around it (`context.*`, `url.*`, `user_agent.*`, `viewport.*`, `telemetry.*`, `trace_id`, `span_id`, `service_*`, …) is **filled in automatically by the SDK** and is intentionally **not** defined here. In particular, `context` is already used for company/LaunchDarkly context keys and must not be repurposed.

> **This taxonomy defines exactly two things per event: (1) the name, (2) the `event.*` fields.**

---

## 2. Naming convention

- **Event names:** `snake_case`, pattern **`object_action`** with the action in **base (present) form — never past tense / no `-ed`**.
  - ✅ `page_view`, `screen_view`, `app_open`, `app_foreground`, `notification_open`, `form_submit`
  - ❌ `page_viewed`, `app_opened`, `notification_opened`, `screenView`
  - Single-token verbs are allowed where unambiguous: `click`, `scroll`, `identify`, `error`.
- **`event.*` field names:** keep the existing in-product spelling. Today's interaction fields are camelCase inside `event` (`relativeX`, `classname`, `xpath`); new fields follow the same nested-`event` style.
- **Enum values:** lowercase strings.

---

## 3. Event catalog (overview)

| # | Event name | Type | Platforms | Purpose |
| --- | --- | --- | --- | --- |
| 1 | `click` | span | web, ios, android | Click / tap on an element. |
| 2 | `track` | span | web, ios, android | Generic custom/domain event (see §4.2). |
| 3 | `page_view` | span | web | A web page / route was viewed. |
| 4 | `screen_view` | span | ios, android | A screen / view controller / activity was viewed. |
| 5 | `identify` | log | web, ios, android | Identity resolution. **Existing — do not change** (see §4.5). |
| 6 | `app_open` | span | ios, android | App launched or resumed into use. |
| 7 | `app_foreground` | span | ios, android | App entered foreground. |
| 8 | `app_background` | span | ios, android | App entered background. |
| 9 | `app_install` | span | ios, android | First launch after install. |
| 10 | `app_update` | span | ios, android | First launch after version change. |
| 11 | `error` | span | web, ios, android | A user-facing error/message was displayed. |
| 12 | `permission_prompt` | span | web, ios, android | An OS/app permission prompt was shown. |
| 13 | `permission_response` | span | web, ios, android | User responded to a permission prompt. |
| 14 | `notification_open` | span | ios, android | User opened a push/local notification. |
| 15 | `deep_link_open` | span | ios, android | App opened via deep/universal link. |
| 16 | `form_submit` | span | web, ios, android | A form was submitted. |
| 17 | `scroll` | span | web | Scroll interaction. |

---

## 4. Event details (`event.*` payload + JSON)

For each event below, only the **`event.*` fields** are part of this taxonomy. The JSON samples show the realistic emitted shape; the `event` object is what this doc governs — the rest (`context`, `url`, `user_agent`, `viewport`, `telemetry`, `trace_id`, …) is **auto-populated by the SDK** and shown only for realism.

---

### 4.1 `click` (existing)

Click (web) or tap (mobile) on an interactive element. One event for all element types; the element is described via `event.*` fields, not separate event names. **OTel mapping:** `app.widget.click` / `app.screen.click` (`event.id`↔`app.widget.id`, `event.text`↔`app.widget.name`, `event.x/y`↔`app.screen.coordinate.x/y`).

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.type` | string | ✅ | Interaction type, e.g. `click`. |
| `event.tag` | string | ✅ | Element tag, e.g. `BUTTON`, `INPUT`, `A`. |
| `event.id` | string | ⛔ | Element DOM id / accessibility id. |
| `event.classname` | string | ⛔ | Element class list. |
| `event.text` | string | ⛔ | Visible text/label of the element. |
| `event.xpath` | string | ⛔ | XPath (web) / view path (mobile) of the element. |
| `event.url` | string | ⛔ | URL/route the click happened on. |
| `event.x` / `event.y` | int | ⛔ | Click coordinates in screen pixels. |
| `event.relativeX` / `event.relativeY` | number | ⛔ | Click position relative to viewport (0–1). |

```json
{
  "span_name": "click",
  "event": {
    "type": "click",
    "tag": "BUTTON",
    "id": "save_profile_btn",
    "classname": "DJIQHa_base xisFqG_field DJIQHa_minimal",
    "text": "Save",
    "xpath": "//html/body/div[2]/div/main/div/div/ol/li[2]/div/button",
    "url": "https://app.launchdarkly.com/projects/default/flags/my-flag/targeting",
    "x": 586,
    "y": 33,
    "relativeX": 0.3391,
    "relativeY": 0.0381
  },
  "viewport": { "width": 1728, "height": 865 },
  "url": {
    "full": "https://app.launchdarkly.com/projects/default/flags/my-flag/targeting",
    "domain": "app.launchdarkly.com",
    "path": "/projects/default/flags/my-flag/targeting",
    "scheme": "https"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } },
  "service_name": "gonfalon-web",
  "environment": "Production",
  "trace_id": "14bc2f58fef76d58d1c35f499a8c51ca",
  "span_id": "7a8802b7e69c9683"
}
```

---

### 4.2 `track` — generic custom & domain events

`track` is the escape hatch for **arbitrary, domain-specific events** that fall outside the standard UI events in this section. It is emitted through the track channel (`track`, event key `$ld:telemetry:track:<event>`); the supplied payload travels under `event.*`.

**Rules for `track` events**
- Use a clear `event.name`; per Segment's spec the canonical names are Title-Case `Object Action` (e.g. `Product Added`).
- Domain properties go under `event.*`. **Do not** repurpose `context` (reserved) and **do not** add these names to the §3 standard catalog.
- The samples below follow the **Segment E-Commerce Spec** (<https://segment.com/docs/connections/spec/ecommerce/v2/>) so they map cleanly into downstream tools.

#### `Product Viewed`

| `event.*` field | Type | Description |
| --- | --- | --- |
| `event.name` | string | `Product Viewed`. |
| `event.product_id` | string | Product/SKU id. |
| `event.name_label` | string | Product name. |
| `event.category` | string | Product category. |
| `event.price` | number | Unit price. |
| `event.currency` | string | ISO-4217 currency, e.g. `USD`. |

```json
{
  "span_name": "track",
  "event": {
    "name": "Product Viewed",
    "product_id": "SKU-1234",
    "name_label": "Aluminum Water Bottle",
    "category": "Drinkware",
    "price": 24.0,
    "currency": "USD"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

#### `Product Added`

| `event.*` field | Type | Description |
| --- | --- | --- |
| `event.name` | string | `Product Added`. |
| `event.product_id` | string | Product/SKU id. |
| `event.quantity` | int | Quantity added. |
| `event.price` | number | Unit price. |
| `event.cart_id` | string | Cart identifier. |

```json
{
  "span_name": "track",
  "event": {
    "name": "Product Added",
    "product_id": "SKU-1234",
    "quantity": 2,
    "price": 24.0,
    "currency": "USD",
    "cart_id": "cart_98f1"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

#### `Checkout Started`

```json
{
  "span_name": "track",
  "event": {
    "name": "Checkout Started",
    "order_id": "ord_5521",
    "value": 72.0,
    "currency": "USD",
    "products": [
      { "product_id": "SKU-1234", "quantity": 2, "price": 24.0 },
      { "product_id": "SKU-9876", "quantity": 1, "price": 24.0 }
    ]
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

#### `Order Completed`

| `event.*` field | Type | Description |
| --- | --- | --- |
| `event.name` | string | `Order Completed`. |
| `event.order_id` | string | Order/transaction id. |
| `event.total` | number | Order total incl. shipping/tax. |
| `event.revenue` | number | Revenue (excl. shipping/tax). |
| `event.currency` | string | ISO-4217 currency. |
| `event.products` | array | Line items (`product_id`, `quantity`, `price`). |

```json
{
  "span_name": "track",
  "event": {
    "name": "Order Completed",
    "order_id": "ord_5521",
    "total": 78.0,
    "revenue": 72.0,
    "shipping": 6.0,
    "currency": "USD",
    "products": [
      { "product_id": "SKU-1234", "quantity": 2, "price": 24.0, "name": "Aluminum Water Bottle" },
      { "product_id": "SKU-9876", "quantity": 1, "price": 24.0, "name": "Insulated Mug" }
    ]
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.3 `page_view` (web, existing)

Span emitted on initial load and on SPA route changes (`pushState`/`popState`/`replaceState`). Matches GA4 `page_view`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.url` | string | ✅ | Current URL. (Emitted today as `page_view.url`.) |
| `event.previous_url` | string | ⛔ | Previous URL. (Emitted today as `page_view.previous_url`.) |
| `event.name` | string | ⛔ | Logical page/route name, e.g. `Flag Targeting`. |
| `event.category` | string | ⛔ | Page group, e.g. `Flags`. |

```json
{
  "span_name": "page_view",
  "event": {
    "url": "https://app.launchdarkly.com/projects/default/flags/my-flag/targeting",
    "previous_url": "https://app.launchdarkly.com/projects/default/flags",
    "name": "Flag Targeting",
    "category": "Flags"
  },
  "url": {
    "full": "https://app.launchdarkly.com/projects/default/flags/my-flag/targeting",
    "path": "/projects/default/flags/my-flag/targeting"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.4 `screen_view` (mobile)

Mobile equivalent of `page_view`. **GA4/OTel mapping:** `event.name`↔`firebase_screen`/`app.screen.name`; `event.screen_class`↔`firebase_screen_class`; `event.screen_id`↔`app.screen.id`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.name` | string | ✅ | Human screen name, e.g. `Profile`. |
| `event.screen_class` | string | ⛔ | View controller / activity / fragment class. |
| `event.screen_id` | string | ⛔ | Stable screen identifier. |
| `event.previous_screen` | string | ⛔ | Name of the prior screen. |
| `event.category` | string | ⛔ | Screen group, e.g. `Onboarding`. |

```json
{
  "span_name": "screen_view",
  "event": {
    "name": "Profile",
    "screen_class": "ProfileFragment",
    "screen_id": "com.example.app.ProfileFragment",
    "previous_screen": "Home",
    "category": "Account"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.5 `identify` (existing — do not change)

Identity resolution is **already implemented** and is out of scope for changes here — documented only for completeness. It is emitted as a **log** named `LD.identify` (via the LaunchDarkly hook's `afterIdentify`), carrying the resolved context keys and canonical key; on success it also calls the SDK `identify(key, traits, 'LaunchDarkly')`. The payload lives in the LaunchDarkly/context namespaces, **not** under `event.*`.

```json
{
  "message": "LD.identify",
  "level": "info",
  "canonicalKey": "account:6a20…:environment:6a20…:member:6a20…:project:6a20…:user:6a20…",
  "key": "e2e+playwright_3xkuzxw5x4nbhwu9@launchdarkly.com",
  "account": "6a209f38a948ce0a832505d6",
  "environment": "Staging",
  "project": "6a209f38a948ce0a832505d7",
  "member": "6a209f38a948ce0a832505d8",
  "user": "6a209f38a948ce0a832505d6",
  "feature_flag": {
    "provider": { "name": "LaunchDarkly" },
    "set": { "id": "586c33cf1cd88133f9a7804f" }
  },
  "launchdarkly": {
    "account": { "name": "Catamorphic Co." },
    "application": { "id": "gonfalon-web", "version": "007d0cee3" },
    "operation": { "name": "launchdarkly.js.log", "type": "unknown" },
    "project": { "name": "default" }
  },
  "result": { "status": "completed" },
  "service_name": "gonfalon-web",
  "trace_id": "e4a9a947f63be0c923ca5635a3593368"
}
```

> Takeaway: `identify` is the only identity call. UI events above never carry identity traits in `event.*`; identity is associated by the SDK/`identify` flow.

---

### 4.6 `app_open` (mobile)

App launched, or resumed from background into active use.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.from_background` | bool | ✅ | `true` if resumed from background, `false` for cold start. |
| `event.referring_source` | string | ⛔ | `push`, `deep_link`, `icon`, `widget`, … |
| `event.url` | string | ⛔ | Launch URL, if opened via a link. |

```json
{
  "span_name": "app_open",
  "event": {
    "from_background": false,
    "referring_source": "icon"
  },
  "context": { "contextKeys": { "accountId": "64dd…" } }
}
```

---

### 4.7 `app_foreground` / `app_background` (mobile)

App moved to foreground / background. **OTel mapping:** lifecycle state in `event.lifecycle_state` (Android: `foreground`/`background`; iOS: `active`/`inactive`/`foreground`/`background`).

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.lifecycle_state` | enum | ✅ | OTel-aligned state value. |

```json
{
  "span_name": "app_foreground",
  "event": {
    "lifecycle_state": "foreground"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

```json
{
  "span_name": "app_background",
  "event": {
    "lifecycle_state": "background"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.8 `app_install` / `app_update` (mobile)

First launch after a fresh install / after a version change.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.version` | string | ✅ | Current app version. |
| `event.build` | string | ⛔ | Current build number. |
| `event.previous_version` | string | ✅ for `app_update` | Version before the update. |

```json
{
  "span_name": "app_update",
  "event": {
    "version": "4.12.0",
    "build": "4120",
    "previous_version": "4.11.2"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.9 `error` (user-facing)

A user-facing error/message was displayed (validation error, toast, error page). This is about **what the user saw**, not an uncaught exception (those go through crash/error reporting). **OTel mapping:** `event.error_type`↔`exception.type`, `event.message`↔`exception.message`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.error_type` | string | ✅ | Category, e.g. `validation`, `network`, `auth`, `server`. |
| `event.error_code` | string | ⛔ | App/HTTP code, e.g. `429`, `INVALID_EMAIL`. |
| `event.message` | string | ⛔ | Message shown to the user. |
| `event.surface` | enum | ⛔ | `toast`, `inline`, `dialog`, `full_page`, `banner`. |
| `event.url` | string | ⛔ | Page/screen where it appeared. |

```json
{
  "span_name": "error",
  "event": {
    "error_type": "validation",
    "error_code": "INVALID_EMAIL",
    "message": "Please enter a valid email address.",
    "surface": "inline",
    "url": "/signup"
  },
  "context": { "contextKeys": { "accountId": "64dd…" } }
}
```

---

### 4.10 `permission_prompt` / `permission_response`

A permission prompt was shown / responded to.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.permission` | enum | ✅ | `notifications`, `location`, `camera`, `microphone`, `photos`, `contacts`, `tracking`, `bluetooth`, `other`. |
| `event.prompt_type` | enum | ⛔ | `os` (system dialog) or `pre_prompt` (in-app soft ask). |
| `event.response` | enum | ✅ for `permission_response` | `granted`, `denied`, `granted_limited`, `dismissed`. |

```json
{
  "span_name": "permission_prompt",
  "event": {
    "permission": "notifications",
    "prompt_type": "os"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

```json
{
  "span_name": "permission_response",
  "event": {
    "permission": "notifications",
    "response": "granted",
    "prompt_type": "os"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.11 `notification_open` (mobile)

User opened/tapped a push or local notification. **Standard:** GA4 `notification_open`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.notification_id` | string | ⛔ | Message/campaign identifier. |
| `event.channel` | enum | ⛔ | `push`, `local`, `in_app`. |
| `event.title` | string | ⛔ | Notification title. |
| `event.url` | string | ⛔ | Destination/deep link carried by the notification. |

```json
{
  "span_name": "notification_open",
  "event": {
    "notification_id": "camp_5521",
    "channel": "push",
    "title": "Your report is ready",
    "url": "exampleapp://reports/5521"
  },
  "context": { "contextKeys": { "accountId": "64dd…", "userId": "65b8…" } }
}
```

---

### 4.12 `deep_link_open` (mobile)

App opened/routed via a deep link / universal link / app link. **Standard:** Segment `Deep Link Opened`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.url` | string | ✅ | Full deep link URL. |
| `event.referring_source` | string | ⛔ | Source app/campaign, e.g. `safari`, `email`. |
| `event.link_type` | enum | ⛔ | `custom_scheme`, `universal_link`, `app_link`. |

```json
{
  "span_name": "deep_link_open",
  "event": {
    "url": "https://example.com/reports/5521?utm_source=email",
    "referring_source": "email",
    "link_type": "universal_link"
  },
  "context": { "contextKeys": { "accountId": "64dd…" } }
}
```

---

### 4.13 `form_submit`

A form submission attempt. Domain-agnostic: identify the form, not its business meaning. **Standard:** GA4 `form_submit`.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.form_id` | string | ✅ | Stable form identifier. |
| `event.form_name` | string | ⛔ | Human-readable form name. |
| `event.url` | string | ⛔ | Page/screen the form is on. |
| `event.success` | bool | ⛔ | Whether client-side validation/submit succeeded. |
| `event.field_count` | int | ⛔ | Number of fields in the form. |

```json
{
  "span_name": "form_submit",
  "event": {
    "form_id": "signup_form",
    "form_name": "Sign up",
    "url": "/signup",
    "success": true,
    "field_count": 4
  },
  "context": { "contextKeys": { "accountId": "64dd…" } }
}
```

---

### 4.14 `scroll` (existing)

Same `event.*` payload as `click`, plus scroll offset. Emit at most ~once/60ms.

| `event.*` field | Type | Required | Description |
| --- | --- | --- | --- |
| `event.type` | string | ✅ | `scroll`. |
| `event.scrollX` / `event.scrollY` | int | ✅ | Scroll offset in pixels. |
| `event.tag`, `event.url`, … | — | ⛔ | Same optional element fields as `click`. |

```json
{
  "span_name": "scroll",
  "event": {
    "type": "scroll",
    "tag": "DIV",
    "url": "https://app.launchdarkly.com/projects/default/flags",
    "scrollX": 0,
    "scrollY": 1480
  },
  "viewport": { "width": 1728, "height": 865 },
  "context": { "contextKeys": { "accountId": "64dd…" } }
}
```

---

## 5. Web ↔ Mobile parity

| Concept | Web | Mobile |
| --- | --- | --- |
| Content view | `page_view` | `screen_view` |
| Interaction | `click`, `scroll` | `click` (tap) |
| Error surfaced | `error` | `error` |
| Permissions | `permission_prompt` / `permission_response` (subset) | `permission_prompt` / `permission_response` |
| Forms | `form_submit` | `form_submit` |
| Notifications | — | `notification_open` |
| Deep links | (link target) | `deep_link_open` |
| App lifecycle | — | `app_open` / `app_foreground` / `app_background` / `app_install` / `app_update` |
| Identity | `identify` (existing) | `identify` (existing) |
| Domain events | `track` | `track` |

---

## 6. Governance

- This doc governs **only** event names and `event.*` fields. Envelope, `context`, telemetry, and trace/session data are owned by the SDK.
- Event names and `event.*` enum values are a **closed, governed set**; additions require a PR here.
- No past-tense (`-ed`) event names.
- Domain/business events use `track` (§4.2) and must not be promoted into the §3 standard catalog.

---

## 7. References

- GA4 — Automatically collected & enhanced measurement events: <https://support.google.com/analytics/answer/9234069>
- Firebase — Measure screenviews (`screen_view`): <https://firebase.google.com/docs/analytics/screenviews>
- OpenTelemetry — App events (`app.screen.click`, `app.widget.click`): <https://opentelemetry.io/docs/specs/semconv/app/app-events/>
- OpenTelemetry — Mobile events (`device.app.lifecycle`): <https://opentelemetry.io/docs/specs/semconv/mobile/mobile-events/>
- Segment — Spec (Identify / Track / Page / Screen): <https://segment.com/docs/connections/spec/>
- Segment — E-Commerce Spec v2 (event names used in §4.2): <https://segment.com/docs/connections/spec/ecommerce/v2/>

---

## 8. What we align with (existing taxonomies)

| Standard | What we borrow | Reference |
| --- | --- | --- |
| **Google Analytics 4 / Firebase** | Present-tense names; `page_view` (web) vs `screen_view` (mobile); `app_open`, `notification_open`, `form_submit`. | <https://support.google.com/analytics/answer/9234069> |
| **OpenTelemetry app events** | `event.*` click payload, screen/widget identifiers, click coordinates. | <https://opentelemetry.io/docs/specs/semconv/app/app-events/> |
| **OpenTelemetry mobile events** | App lifecycle states (`foreground`/`background`/`active`/`inactive`/…). | <https://opentelemetry.io/docs/specs/semconv/mobile/mobile-events/> |
| **Segment Spec** | The `identify`/`track` call model and the reserved e-commerce event names used in §4.2. | <https://segment.com/docs/connections/spec/> |

> **On OpenTelemetry.** OTel is an observability convention, not a product-analytics one, but it does **not contradict** the web/mobile product taxonomies. We adopt its **attribute names and enum values** (lifecycle states, screen/widget identifiers, click coordinates) and map them into our `event.*` namespace; we keep short, human-readable **event names** that product tools expect.
