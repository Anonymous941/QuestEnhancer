# 07 — Cast feedback shim (Zygisk)

Makes casting from a rooted Quest 1 (Horizon) to a Chromecast/TV smooth again
when the receiver has **two live network interfaces** at once.

## The bug

Casting to our Vizio sat at ~2 fps / ~1.4 Mbps with 2–5 s of lag, the H.264
encoder paused ~84% of the time even though a frame only takes ~12 ms to encode.
It is not the encoder and not Wi-Fi (measured ~29 Mbps up, <2 ms ping to the TV).

The TV has Ethernet **and** Wi-Fi up at the same time, so it has two IPs on the
LAN (e.g. `.219` and `.89`, same vendor MAC prefix). vrcast negotiates the cast
against one of them and Horizon's WebRTC validates ICE to it, but the TV then
streams its RTP/RTCP **feedback from the other interface**.

Horizon's WebRTC is old — WebRTC `branch-heads/50` (~2016 libjingle), confirmed
from the exact `p2p/base/port.cc` it was built from. Inbound packets are matched
to a connection by an **exact** address lookup:

```
// stunport.cc, inbound dispatch
Connection* conn = GetConnection(addr);
if (conn) conn->OnReadPacket(...);   // delivered, feedback flows
else      Port::OnReadPacket(...);   // "unknown address" -> dropped
```

```
// port.cc:896
Connection* Port::GetConnection(const rtc::SocketAddress& remote_addr) {
  AddressMap::const_iterator iter = connections_.find(remote_addr);  // exact match
  ...
}
```

The feedback arrives from `.89`, the only connection is keyed on `.219`, so every
packet falls through to `Port::OnReadPacket` and is logged and dropped:

```
port.cc: Received non-STUN packet from unknown address (…:10008)
```

~74 times a second, the whole cast. With no RTCP feedback the bandwidth estimate
never grows, the pacer stays near its ~750 kbps start, the send queue fills to
its ~2 s cap, the encoder is paused, fps collapses, and that queue plus the
receiver's buffer is the 2–5 s of lag.

**Proof:** forcing WebRTC to accept the feedback from the 2nd IP → instant 24 fps
and the estimate self-raised to 3 Mbps.

## The fix

A small Zygisk shim, injected only into `com.oculus.horizon`, that normalizes the
**source address** of inbound UDP packets so WebRTC's exact-match lookup succeeds.
No binary edit, no hard-coded IP (so it follows DHCP changes), removable.

It is self-learning and self-limiting:

- On `sendto()` / `sendmsg()` it records, per socket fd, the `(ip,port)` we send to.
- On `recvfrom()` / `recvmsg()`, if a packet arrives on a **port we have sent to**
  but from a **different IP in the same /24** (the dual-homed-LAN signature), it
  rewrites the reported source IP to the IP we sent to. `GetConnection` then
  matches the existing writable connection and the packet is delivered. A
  cross-subnet source (e.g. a relayed peer) is left untouched.
- **STUN is never rewritten.** A packet carrying the STUN magic cookie
  (`0x2112A442` at bytes 4–7, the same test WebRTC's own demux uses) passes
  through as-is, so the ICE handshake for the TV, parties and everything else
  completes natively. Only non-STUN media (RTP/RTCP), the traffic WebRTC was
  dropping, is ever a rewrite candidate. (v1 rewrote STUN too and broke cast
  setup.)
- It only engages on a socket whose learned peer is a private LAN address
  (10/8, 172.16/12, 192.168/16, 169.254/16).
- **Only `liboculus_p2p.so` is patched** (v3). Horizon loads its native
  libraries straight out of `Horizon.apk`, mapped at a non-zero file offset,
  and xHook's `/proc/self/maps` scanner skips those, so v1/v2 never reached
  WebRTC. v3 finds the library through `dl_iterate_phdr()` (a watcher thread
  polls every 0.5 s until it is loaded), patches its GOT once with xHook's ELF
  patcher, logs `patched … N/5 hooks`, and exits. `dlopen` is no longer hooked
  at all: a `dlopen` proxy changes which linker namespace a load happens in,
  and v1/v2 shim-loaded sessions saw Horizon's cast encoder abort in the
  Adreno shader compiler.

A healthy connection (party, voice, data channel, or a single-homed receiver)
always replies from the address we sent to, so the source already matches and the
rule never fires there. The only thing it ever rewrites is the dual-homed
receiver that caused the bug. It never changes the send path, the payload, the
port, or the length.

## Use

Off by default. After installing + rebooting:

```
adb shell su -c 'setprop debug.qe.cast_shim 1'    # enable, then start a cast
adb shell su -c 'setprop debug.qe.cast_shim 0'    # disable
adb shell su -c 'setprop debug.qe.cast_shim_log 1'  # optional: log rewrites to logcat (rate limited)
```

The prop is re-read every ~2 s, so toggle and re-cast — no reboot needed to flip it.
Watch it work: `adb logcat -s QECastShim`.

## Build

Native source is `jni/shim.cpp` (plus `jni/zygisk.hpp`), built with `ndk-build`
(`jni/Android.mk`, `jni/Application.mk`). It links **xHook** (iqiyi/xHook v1.2.0,
MIT) statically so the module is one self-contained `.so` — a Zygisk module
can't carry a runtime `.so` dependency. xHook's sources are not committed;
CI (`.github/workflows/build.yml`) clones the pinned tag into `jni/xhook/`,
runs `ndk-build` for arm64-v8a and armeabi-v7a, drops each result into
`module/zygisk/<abi>.so`, then packs `module/` into `07-cast-feedback-shim.zip`.
Requires Magisk + Zygisk (or a Zygisk provider such as Vector) on the headset.

## Scope / safety

- Installed only in `com.oculus.horizon`; the module unloads itself in every other
  process.
- IPv4 only; never rewrites loopback.
- Does nothing while `debug.qe.cast_shim` is unset/0.
