# Encryption flow

How Stamly encrypts a *snak* end to end with MLS (RFC 9420), using `@wireapp/core-crypto`
behind our own `stamly-crypto` adapter.

> **Status:** design. The `stamly-crypto` interface is not built yet. The method names below
> (`init`, `createKeyPackage`, `createGroup`, `invite`, `join`, `encrypt`, `decrypt`) are a
> proposal and may change during implementation.

---

## 1. The layers

```mermaid
flowchart TB
    subgraph device["Browser (one per device)"]
        web["stamly-web<br/>React UI, plain text"]
        adapter["stamly-crypto<br/>our adapter, Stamly types only"]
        cc["@wireapp/core-crypto<br/>MLS engine (WASM)"]
        ks[("Keystore<br/>encrypted IndexedDB")]
        web -- "Stamly calls + plain bytes" --> adapter
        adapter -- "MLS calls" --> cc
        cc -- "keys + group state" --> ks
    end

    server["Stamly server (Spring Boot)<br/>stores and relays bytes, cannot decrypt"]

    web -- "HTTPS / WebSocket:<br/>KeyPackages, ciphertext" --> server
    adapter -. "MlsTransport:<br/>Commit + Welcome" .-> server
```

| Layer | Knows about | Never does |
|---|---|---|
| `stamly-web` | Stamly words: snak, kreds, invitation | Import `@wireapp/core-crypto` |
| `stamly-crypto` | Stamly types **and** core-crypto | Let `ClientId`, `ConversationId` or other library types leave the package |
| `@wireapp/core-crypto` | MLS: keys, epochs, encryption, padding, key wiping | Talk to the network on its own (it hands outgoing messages to `MlsTransport`) |
| Keystore | Private keys and group state, encrypted | Leave the device |
| Server | Who is in which group, where to deliver bytes | See private keys or message content |

The adapter is what makes the library replaceable: `stamly-web` only depends on the `stamly-crypto`
interface, and one shared contract test suite runs against every adapter
(see ADR-0001).

---

## 2. Setup: Alice invites Bob into a snak

```mermaid
sequenceDiagram
    autonumber
    participant AW as Alice: stamly-web
    participant AC as Alice: stamly-crypto + core-crypto
    participant S as Stamly server
    participant BC as Bob: stamly-crypto + core-crypto
    participant BW as Bob: stamly-web

    Note over AW,AC: App start (same on every device)
    AW->>AC: init()
    AC->>AC: load WASM, open encrypted keystore, identity key pair

    Note over BC,BW: Bob makes himself reachable
    BW->>BC: createKeyPackage()
    BC-->>BW: KeyPackage bytes (public), private half stays in keystore
    BW->>S: upload KeyPackage
    S->>S: store KeyPackage for Bob

    Note over AW,AC: Alice creates the group
    AW->>AC: createGroup()
    AC->>AC: new MLS conversation, epoch 0, saved in keystore
    AC-->>AW: Stamly group id

    Note over AW,S: Alice invites Bob
    AW->>S: request Bob's KeyPackage
    S->>S: check: may Alice invite Bob? (shared kreds)
    S-->>AW: Bob's KeyPackage
    AW->>AC: invite(groupId, keyPackage)
    AC->>AC: add member, new epoch
    AC->>S: Commit + Welcome (via MlsTransport)
    S->>S: store invitation, keep Welcome for Bob

    Note over S,BW: Bob accepts
    BW->>S: accept invitation
    S-->>BW: Welcome (only to Bob)
    BW->>BC: join(welcome)
    BC->>BC: open Welcome with KeyPackage private key, save group state
    BC-->>BW: Stamly group id
```

Why the server checks invitations: **anyone holding a user's KeyPackage can add that user to a
group** – MLS itself does not ask. Stamly therefore controls who may invite whom (only members
of a shared kreds) and adds an explicit "accept invitation" step.

---

## 3. Sending and reading a snak

```mermaid
sequenceDiagram
    autonumber
    participant AW as Alice: stamly-web
    participant AC as Alice: stamly-crypto + core-crypto
    participant S as Stamly server
    participant BC as Bob: stamly-crypto + core-crypto
    participant BW as Bob: stamly-web

    AW->>AC: encrypt(groupId, "Hej Bob" as bytes)
    AC->>AC: encrypt with epoch key, pad, wipe used key
    AC-->>AW: ciphertext
    AW->>S: send ciphertext to group
    S->>S: store, find members of the group
    S-->>BW: ciphertext (members only)
    BW->>BC: decrypt(groupId, ciphertext)
    alt valid message for this group
        BC-->>BW: "Hej Bob" as bytes
        BW->>BW: show the snak
    else tampered or from another group
        BC-->>BW: MessageRejected (Stamly error, no library details)
        BW->>BW: show a calm error, log without content
    end
```

---

## 4. What the server sees

| Step | Server sees | Server never sees |
|---|---|---|
| App start | Nothing | – |
| KeyPackage upload | Public KeyPackage of a device | Private keys |
| Create group | Nothing (local only) | – |
| Invite | Group id, epoch, sizes, who invited whom | Group secrets inside the Welcome |
| Join | Who received a Welcome | Group secrets |
| Snak | Group id, epoch, message size, sender, time | Message content |

This matches the data classification in `VISION.md`: metadata needed for delivery is visible,
content is always encrypted. Padding makes message sizes reveal less.

---

## 5. Rules

- Only `stamly-crypto` imports `@wireapp/core-crypto`. Library types never leave the package.
- Versions of `@wireapp/core-crypto` are pinned exactly and updated one major version at a time,
  in their own step.
- Errors from core-crypto are mapped to our own errors (e.g. `MessageRejected`). Logs never
  contain message content or keys.
- The server delivers messages and Welcomes only to members of the group.
- Never build our own crypto.

## Related

- ADR-0001 – `@wireapp/core-crypto` as the MLS library, behind a `stamly-crypto` adapter
- [RFC 9420 – The Messaging Layer Security (MLS) Protocol](https://www.rfc-editor.org/rfc/rfc9420)
