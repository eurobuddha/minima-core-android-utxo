# Minima UTXO Wallet (native Android)

A native Android port of the **utxoWallet** MiniDapp — a [Minima](https://minima.global) wallet with
**explicit UTXO (coin) selection**: pick exactly which coins you spend, construct the whole transaction,
and review before signing.

Native Java, talking to a local **Minima Core** node over the node's **broadcast‑Intent IPC**
(`minimaapi`) — no MDS, no RPC, no browser.

## Features
- **Wallet** — every address (coins‑first), single‑tokenid multi‑select, tap‑to‑copy.
- **Send** — full UTXO construction: pick inputs, recipient, **editable change address**, burn, confirm.
- **Tools** — Split / Consolidate / Distribute (multi‑batch) / Untrack, from the selection bar.
- **Balances** — rich token cards (icon, sendable/locked, decimals, description).
- **Receive** — address + QR.
- **History** — sent + received from the node's `history` (bounded + lazy so it never overloads the
  node), plus this wallet's own postings on top (posting / posted / failed, with the node's error and
  every input coinid copyable); a posting is confirmed against the on‑chain txpow that spent its inputs,
  and confirmed transactions link to the Explorer.
- **Burn** — a Minima burn is realised as the inputs−outputs gap only (never `txnpostburn` as well, which
  would burn twice from a second coin).
- Dark + orange theme.

## Build
Requires a **JDK 17/21** (the Android Studio JBR works):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Install, then enable **Minima UTXO** in Minima Core → Apps to authorize the IPC.

## Releases
Versioned APKs + changelog: **[eurobuddha/minima-core-apks](https://github.com/eurobuddha/minima-core-apks)**
(tags `minima-utxo-wallet-v<version>`).
