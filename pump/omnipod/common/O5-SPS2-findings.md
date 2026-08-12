# Omnipod 5 pairing: SPS2 fails on Android, succeeds on iOS — findings

**Date:** 2026-08-12
**Context:** An independent Kotlin port of the Omnipod 5 BLE pairing protocol (AndroidAPS
fork), written against `loopandlearn/OmnipodKit` as the reference. It completes SPS0,
SPS1 and SPS2.1 against a real pod and is then dropped at SPS2. OmnipodKit on iOS pairs
the same pods with the same certificates without trouble.

This writes up what was compared between a failing Android attempt and a captured
successful iOS pairing, in case it is useful to anyone else working on O5.

---

## The failure

Three attempts, two different controller certificates, identical result each time:

```
SPS0    -> pod replies          OK
SPS1    -> pod replies          OK
SPS2.1  -> pod replies          OK   (642 sent / 641 received)
SPS2    -> pod terminates the connection
```

The disconnect is pod-initiated: GATT `status 19` = `GATT_CONN_TERMINATE_PEER_USER`,
roughly 480 ms after the SPS2 write completes. Not a timeout on our side — the client
then spends 15 s trying to read a reply from a link the pod has already dropped, and
reports `Could not read SPS2`.

## What was compared

A successful iOS pairing was captured by adding a file mirror to OmnipodKit's `OSLog`
wrapper (its logging is `os_log`-only, and the lines of interest are at `.info`, which
os_log discards rather than persisting — so they never reach an exported app log).

Every element below was then compared between that working exchange and the failing
Android one. **All identical:**

| Element | Result |
|---|---|
| 171-byte channel-binding transcript | **byte-identical** (replayed, see below) |
| AES-CCM nonces at every SPS2.1/SPS2 step | **byte-identical** (replayed, see below) |
| `FIRMWARE_ID` constant (`9b0ab96a76f4`) | identical |
| Nonce state in the transcript — both nonces incremented once | identical |
| KDF input: 210 bytes, `u64(len)‖value` framing | identical |
| `conf` / `ltk` derivation: SHA-256, split 16/16 | identical |
| Intermediate CA certificate size (634 bytes) | identical |
| TLS certificate size (880 bytes) | identical |
| Message payload sizes and sequence numbers | identical |

Message-level comparison, working vs failing:

```
SP1+SP2    28 bytes, seq 1     28 bytes, seq 1
SPS0       12 bytes, seq 2     12 bytes, seq 2
SPS1       87 bytes, seq 3     87 bytes, seq 3
SPS2.1    651 bytes, seq 4    651 bytes, seq 4
SPS2      959 bytes, seq 5    959 bytes, seq 5
```

### Replaying the capture through the implementation

The comparisons above were made by reading both implementations and reconstructing bytes
by hand, which assumes each does what it appears to do. That assumption can be removed.

Only four values feed the transcript and the AES-CCM nonces — `pdmPublic`, `podPublic`,
`pdmNonce`, `podNonce` — and all four are recoverable from the capture: the two public keys
and the incremented nonces from the logged transcript, the pod's original nonce from its
SPS1 reply, and the controller's original nonce by decrementing (which independently
cross-checks against the logged CCM nonce). The controller's ephemeral *private* key is
not recoverable, but neither value under test depends on it.

Feeding those captured inputs into the Android implementation's real `O5KeyExchange` and
walking the same increment sequence the pairing performs gives byte equality on both:

```
buildChannelBindingTranscript()  ==  the 171-byte transcript the pod accepted
getSPSNonce()                    ==  the logged CCM nonce at all four SPS2.1/SPS2 steps
```

This is worth doing on any port of this protocol. It converts "we read the reference
carefully" into "the code produces bytes a pod has actually accepted", costs nothing, and
becomes permanent regression cover — a later change to the transcript layout made on a
hunch fails immediately against a vector the hardware itself validated.

### Signature

Separately, an ECDSA signature taken from the working capture was verified against its own
transcript using the controller certificate's public key, a raw `r‖s` → DER conversion and
`SHA256withECDSA` — the same crypto path the Android port uses. It verifies.

**Conclusion: the SPS2 payload built on Android is correct, confirmed against bytes a real
pod accepted rather than against a reading of the reference implementation. Whatever
differs is below the message content.**

## Status: cause still unidentified

No difference has yet been found. The message *content* is no longer merely "believed
correct" — the transcript and CCM nonces are confirmed byte-identical to a pairing the pod
accepted, by replaying that capture through the implementation itself. The transport looks
the same too (see the MTU note below).

So the Android side builds a correct SPS2 and sends it in the same shape, and the pod
accepts it from iOS and rejects it from Android. What remains unverified is whether the
bytes actually transmitted match the bytes constructed — everything between the payload
being assembled and it reaching the wire.

## MTU — investigated and ruled out

Worth writing up because it looks like a promising lead and is not one.

The iOS capture shows the pod peripheral at `mtu = 23` in every `didConnect` log line,
which suggests CoreBluetooth never negotiates upward and that Android's explicit
`requestMtu(244 + 3)` is therefore a divergence.

**It isn't.** Those values are stale. `CBPeripheral`'s debug description is emitted at
connect time, before iOS's asynchronous MTU negotiation settles — OmnipodKit's own code
says so: *"iOS auto-negotiates the MTU asynchronously after connect."*

The decisive detail is the write type. OmnipodKit's O5 profile uses:

```swift
commandWriteType: .withoutResponse
packetLayout: maxPayloadSize: 244        // Dash: .withResponse, 20
```

`.withoutResponse` does **not** perform ATT long writes — iOS silently truncates anything
exceeding `maximumWriteValueLength` (MTU − 3). OmnipodKit also polls that value on connect,
waiting for it to reach 244 and warning *"Large writes may be truncated!"* otherwise.

So a successful iOS pairing that sends 244-byte packets `.withoutResponse` proves the ATT
MTU was **≥ 247** during that pairing. Both implementations negotiate a large MTU. The MTU
is not a difference, and lowering it on Android would truncate every large write and break
pairing earlier than it currently fails.

## Things that were ruled out

Recorded here so nobody repeats them:

- **The certificates.** All valid, correctly chained (Insulet intermediate → leaf), in
  date, with the cert-embedded public key matching the credential's signing key. Two
  different certificates failed identically.
- **Certificate size.** An OmnipodKit comment documents a 1017-byte TLS certificate; the
  working capture shows 880, the same as the failing Android attempt. That figure is one
  developer's own certificate, not a specification.
- **The transcript format.** OmnipodKit carries a `"transcript format may differ"` log
  line and a commit history whose furthest pairing-named milestone is "Get SPS2.1 reply
  from pod", which can give the impression the transcript is an unverified guess. It is
  not — it is confirmed correct by the working capture.
- **Message framing and fragmentation.** Declared lengths match bytes actually
  transmitted; nothing is truncated.
- **The ATT MTU.** See above — both sides negotiate ≥ 247. The `mtu = 23` visible in iOS
  connect-time logs is stale and does not reflect what the writes actually used.

## Where this leaves it

Everything comparable between a working and a failing exchange has been compared and
matches, and the content layer is now confirmed by replay rather than by inspection.

That narrows the remaining search to the path between constructing the SPS2 payload and
it arriving at the pod: the AES-CCM ciphertext itself (its key and nonce inputs are
confirmed, the encryption step is not), packet fragmentation and reassembly, and the GATT
write mechanics.

### Current step: comparing the actual BLE writes

The Android side's per-packet writes are already captured in full hex, from a logcat trace
taken during a failing attempt. The equivalent view from the working implementation is not,
because OmnipodKit's per-packet logging (`bleDebug`) is compiled off by default.

Enabling it and capturing one successful pairing supplies the missing half. That costs
nothing — the pod pairs normally and stays in use — and yields two things:

- `maximumWriteValueLength settled after N polls: maximumWriteValueLength=…`, which
  settles the MTU question with a measured value rather than an inference from the stale
  connect-time field described above
- `[sendMessagePacket]` detail, allowing the two implementations' writes to be diffed
  directly

If those writes match, the entire byte path is identical end to end and the fault lies in
GATT mechanics or timing. If they differ, the difference names the bug.

If anyone has a BLE sniffer capture of a real PDM or the Omnipod 5 app performing SPS2,
that would settle it faster than anything else — no such capture appears to be publicly
available.

## Useful to know if you are debugging this

OmnipodKit logs the SPS2 internals (`Channel-binding transcript`, `ECDSA signature`,
`SPS2: TLS cert (N bytes)`) at `os_log` `.info`, which is **not persisted** — it needs a
Mac streaming Console.app at the moment of pairing, or a patch. There is also no bridge
from OmnipodKit's `OSLog` wrapper to Trio's `SimpleLogReporter`, so none of it reaches the
`log.txt` a user can export. No amount of collecting ordinary Trio logs will surface it.

Mirroring inside the private `log(_:type:_:)` function of OmnipodKit's `OSLog` extension
captures every call site at every level in one place, and writes to the app's Documents
directory where it can be exported from the Files app.
