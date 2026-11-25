# FAQ – why is the code the way it is?

Short answers to "why do we …?" questions about Linden's code. Each entry links to where the answer lives in the code
or in another doc. New entries are added when a question comes up that every developer will ask sooner or later.

## Contents

1. [What are `package-info.java` and `@NullMarked` for?](#1-what-are-package-infojava-and-nullmarked-for)
2. [Why don't we use Lombok?](#2-why-dont-we-use-lombok)
3. [Why must every database column be in
   `schema-classification.csv`?](#3-why-must-every-database-column-be-in-schema-classificationcsv)
4. [Why is the backend all English, when the app uses Nordic words?](#4-why-is-the-backend-all-english-when-the-app-uses-nordic-words)
5. [Why are packages organised by feature, not by layer?](#5-why-are-packages-organised-by-feature-not-by-layer)

---

## 1. What are `package-info.java` and `@NullMarked` for?

`package-info.java` is a special file for things that apply to a **whole package**:

- the package's Javadoc – every package has one;
- annotations for the whole package – for us, `@NullMarked`.

`@NullMarked` (from [JSpecify](https://jspecify.dev)) says: *in this package, nothing is `null` unless it is marked
`@Nullable`*. IntelliJ (and other tools) then warn when `null` might flow where it isn't allowed – for example when a
Spring method marked `@Nullable` is used as if it never returned `null`. It has **no effect at runtime**; it is
documentation that tools can check.

- It does **not** cover sub-packages – each package has its own `package-info.java`.
- `@Nullable` goes right before the type it describes: `private @Nullable Instant createdAt;`, and for an array that
  may be `null`: `byte @Nullable [] payload`.
- Use JSpecify's annotations (`org.jspecify.annotations.*`), not IntelliJ's `org.jetbrains.annotations.*`.

Example: [
`core/circle/package-info.java`](../linden-core/src/main/java/io/github/projectlinden/core/circle/package-info.java),
[`Circle.getCreatedAt()`](../linden-core/src/main/java/io/github/projectlinden/core/circle/Circle.java).

---

## 2. Why don't we use Lombok?

**No Lombok by default.** It is added only where it clearly makes the code better, with a reason in the pull request.

- **Records** cover data classes (`CircleMemberId`, `CircleResponse`).
- **A constructor is a few lines** – and written out, it is obvious what a class needs.
- **Lombok hooks into the compiler's internals**, which is a risk with every new Java version.
- **`@Data` is dangerous on JPA entities:** its generated `equals`, `hashCode` and `toString` can trigger lazy loading
  or break identity.

Example: [`CircleService`](../linden-core/src/main/java/io/github/projectlinden/core/circle/CircleService.java) – plain
constructor injection.

---

## 3. Why must every database column be in `schema-classification.csv`?

Because the server must **never store readable content** – that is the promise of end-to-end encryption. The file is
the guest list of the database: every column, with its level and the reason the server needs it.

```
table,column,classification,reason
circle,payload,encrypted,The circle name – written by people, so it is content; …
```

- `metadata` – the server can read it, so it needs a reason (data minimisation).
- `encrypted` – an opaque blob the server can never read.
- There is **no level for readable content**, so such a column can't be classified – and can't exist.

`SchemaClassificationIT` runs the Flyway migrations on a real Postgres and compares the real columns with the list, in
both directions: a column that isn't listed turns the build red, and so does a line for a column that no longer exists.
It runs as the step **"No content in the database"** in the Project rules job.

What the test can't see – whether a `metadata` column reveals too much – is a question for the pull request review.

Details: [project rules](project-rules.md#no-content-in-the-database), [security § 6](security.md#6-the-database).

---

## 4. Why is the backend all English, when the app uses Nordic words?

The Nordic words are **labels in the app**, not names in the code. Keeping them out of the backend means a label can
change – or get a translation – without touching code, database, API or tests.

| In code, database, API and docs | Shown in the app as |
|---------------------------------|---------------------|
| `Circle` / circle               | *kreds*             |
| `Post` / post                   | *glimt*             |
| `Comment` / comment             | *svar*              |
| `Reaction` / reaction           | *koselig*           |
| `Invitation` / invitation       | *bifrost*           |
| `Gathering` / gathering         | *fika*              |

This applies to the **whole backend**: class and method names, comments, Javadoc, exception and log messages, SQL,
the classification reasons and test names. Docs may name the app word once, as a glossary entry, like this table.

---

## 5. Why are packages organised by feature, not by layer?

`circle/` holds everything about circles – entity, enum, key, repository, service. The alternative would be folders
like `entity/`, `repository/`, `service/`, each holding a bit of every feature.

|                    | By feature (what we do)                                                                                                                             | By layer                                                                                      |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| One change         | Usually stays in one folder                                                                                                                         | Touches several folders                                                                       |
| Hiding details     | **Package-private works:** `CircleRole.fromDatabaseValue()` is visible only inside `circle/`, so nothing outside can depend on how roles are stored | Classes in different folders only see each other's `public` parts – everything becomes public |
| Removing a feature | Delete one folder                                                                                                                                   | Search through every folder                                                                   |

- **When a feature package grows** past roughly 15–20 classes, split it **by sub-feature** (e.g. `circle/invitation/`),
  still not by layer.
- **The modules are the layers:** `linden-core` holds the rules, `linden-api` speaks HTTP, `linden-app` wires them
  together. So: feature packages inside layered modules.

Example: [`linden-core/…/core/circle/`](../linden-core/src/main/java/io/github/projectlinden/core/circle).
