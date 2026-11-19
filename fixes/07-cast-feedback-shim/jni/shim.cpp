// Quest 1 cast feedback shim (Zygisk + xHook).
//
// Why this exists
// ---------------
// Casting from a rooted Quest 1 (Horizon) to a Chromecast/TV is slow and laggy
// when the receiver has TWO live network interfaces (e.g. a TV on Ethernet AND
// Wi-Fi). vrcast negotiates the cast against ONE of the receiver's addresses and
// Horizon's WebRTC (liboculus_p2p.so, WebRTC branch-heads/50, ~2016 libjingle)
// validates ICE to it, but the receiver then streams its RTP/RTCP *feedback*
// from the OTHER interface.
//
// That WebRTC matches an inbound packet to a connection by an EXACT address
// lookup (Port::GetConnection -> connections_.find(remote_addr)). A packet from
// the receiver's other interface matches no connection, so it falls through to
// Port::OnReadPacket and is dropped:
//     port.cc: Received non-STUN packet from unknown address (<ip>:<port>)
// ~74x/second, the whole cast. With no RTCP feedback the bandwidth estimate
// never grows, the pacer stays near its ~750 kbps start, the send queue fills to
// its ~2 s cap, the encoder is paused most of the time, and you get ~2 fps with
// 2-5 s of lag. (Proven: forcing WebRTC to accept the 2nd address -> instant
// 24 fps, BWE self-raised to 3 Mbps.)
//
// What this does
// --------------
// It normalizes the SOURCE ADDRESS of inbound UDP packets so WebRTC's exact-match
// lookup succeeds, WITHOUT editing Meta's binary and WITHOUT a hard-coded IP (so
// it follows DHCP churn). Self-learning and self-limiting:
//
//   * On sendto()/sendmsg() it records, per socket fd, the (ip,port) we send to.
//   * On recvfrom()/recvmsg(), if a packet arrives on a PORT we have sent to but
//     from a DIFFERENT IP than the one we sent to on that port, it rewrites the
//     reported source IP to the IP we sent to. GetConnection then matches the
//     existing writable connection and the packet is delivered.
//   * On close() it forgets that fd.
//
// A healthy connection (party, voice, data channel, a single-homed receiver)
// always replies from the address we sent to, so the source already matches and
// the rule never fires there. The ONLY thing it rewrites is the dual-homed
// receiver that caused the bug. It never changes payload, port, or length, and
// never touches the send path.
//
// Guards (v6)
// -----------
// liboculus_p2p carries every WebRTC session in Horizon (TV, phone and browser
// casts, parties, voice), so the rule holds back whenever WebRTC could already
// be handling the packet itself:
//   * stale      -- nothing sent to that port for 30 s (old socket, reused fd);
//   * ambiguous  -- we sent to two IPs on that port in the last 30 s;
//   * learned    -- that source sent a STUN Binding request on this socket, so
//                   WebRTC made a peer-reflexive connection for it;
//   * known-peer -- we sent to that source ourselves in the last 30 s.
// Each held-back class is counted in the 10 s stats line.
//
// Hooking (v3)
// ------------
// Only liboculus_p2p.so's GOT is patched, so only Horizon's WebRTC sockets ever
// pass through this code; every other library (the GPU driver included) is
// untouched. That library is loaded lazily (when casting is first set up) and
// straight out of Horizon.apk, so:
//   * a small thread watches dl_iterate_phdr() until liboculus_p2p.so appears,
//     then patches its GOT once with xHook's ELF patcher (xh_elf_*), using the
//     load address the linker reports, and exits.
//   * xHook's /proc/self/maps scanner (xhook_register/xhook_refresh) is NOT
//     used: it skips mappings with a non-zero file offset, which is exactly how
//     a library loaded from inside an APK is mapped, so v1/v2 never reached
//     liboculus_p2p.so at all.
//   * dlopen/android_dlopen_ext are NOT hooked: the linker picks the namespace
//     from dlopen's caller, so a dlopen proxy made vendor libraries (the Adreno
//     driver loading its compiler) look like they were loaded by us. v1/v2 did
//     that and Horizon's cast encoder aborted in its first shader compile.
//
// Safety
// ------
//   * Installed ONLY in com.oculus.horizon; the module unloads itself elsewhere.
//   * Off by default. Enable at runtime:  setprop debug.qe.cast_shim 1
//     (re-read every ~2 s, so toggle + recast, no reboot). Clear to disable.
//   * IPv4 only. Never rewrites loopback or when the prop is off.
//   * Optional rewrite logging:  setprop debug.qe.cast_shim_log 1  (rate limited).

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/system_properties.h>
#include <android/log.h>
#include <pthread.h>
#include <dlfcn.h>
#include <time.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>
#include <link.h>
#include <atomic>

#include "zygisk.hpp"
#include "xh_elf.h"     // xHook's ELF GOT patcher (MIT), used directly

#define LOG_TAG "QECastShim"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Diagnostics that can't scroll away: every status line also goes to a small
// file in Horizon's own data dir (readable with su), truncated at each Horizon
// start and capped in size. Only called from install and the watcher thread,
// never from the socket hot path.
// ---------------------------------------------------------------------------
#define STATUS_FILE "/data/data/com.oculus.horizon/files/qecastshim.log"
#define STATUS_CAP  (256 * 1024)

static pthread_mutex_t g_file_lock = PTHREAD_MUTEX_INITIALIZER;

static void status(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void status(const char *fmt, ...) {
    char line[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", line);

    pthread_mutex_lock(&g_file_lock);
    FILE *f = fopen(STATUS_FILE, "a");
    if (f) {
        if (ftell(f) < STATUS_CAP) {
            struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
            struct tm tm; localtime_r(&ts.tv_sec, &tm);
            fprintf(f, "%02d:%02d:%02d.%03ld pid %d  %s\n", tm.tm_hour, tm.tm_min, tm.tm_sec,
                    ts.tv_nsec / 1000000, (int)getpid(), line);
        }
        fclose(f);
    }
    pthread_mutex_unlock(&g_file_lock);
}

// Traffic counters (relaxed atomics; read by the watcher thread every 10 s).
static std::atomic<uint32_t> c_recv{0}, c_recv_v4{0}, c_send{0};
static std::atomic<uint32_t> c_rewrite{0}, c_stun{0}, c_nocast{0}, c_nolan{0}, c_xsub{0};
static std::atomic<uint32_t> c_stale{0}, c_ambig{0}, c_learned{0}, c_known{0}, c_ipv6{0};

// ---------------------------------------------------------------------------
// Runtime gate (cached; refreshed every ~2 s so setprop takes effect live).
// ---------------------------------------------------------------------------
static std::atomic<bool> g_enabled{false};
static std::atomic<bool> g_log{false};

static bool prop_true(const char *name) {
    char v[PROP_VALUE_MAX] = {0};
    if (__system_property_get(name, v) <= 0) return false;
    return v[0] == '1' || v[0] == 't' || v[0] == 'T' || v[0] == 'y' || v[0] == 'Y';
}

static void refresh_gate() {
    static std::atomic<time_t> last{0};
    time_t now = time(nullptr);
    time_t prev = last.load(std::memory_order_relaxed);
    if (now - prev < 2) return;                 // at most once per 2 s
    if (!last.compare_exchange_strong(prev, now, std::memory_order_relaxed)) return;
    g_enabled.store(prop_true("debug.qe.cast_shim"),     std::memory_order_relaxed);
    g_log.store(    prop_true("debug.qe.cast_shim_log"), std::memory_order_relaxed);
}

// ---------------------------------------------------------------------------
// Per-fd memory (v6). For each socket fd (< FD_CAP) we remember, per destination
// port, the last two distinct IPs we sent to and when; and the source IPs that
// have sent us STUN on that fd. All IPv4, network byte order; times are
// CLOCK_MONOTONIC milliseconds.
//
// The guards this feeds (see fixup_src):
//   * stale    -- nothing sent to that port for TTL_MS: the memory may belong to
//                 an old socket that reused this fd number, so don't trust it.
//   * ambiguous-- two different IPs were sent to on that port within TTL_MS
//                 (e.g. ICE checks to both of a peer's candidates): no single
//                 rewrite target, so leave packets alone.
//   * learned  -- that source already sent STUN on this fd, so WebRTC knows it
//                 (peer-reflexive connection) and accepts its media natively.
//   * known-peer-- we have sent to that source IP ourselves recently on this fd,
//                 so it is a peer WebRTC talks to, not a stray second interface.
// ---------------------------------------------------------------------------
#define FD_CAP   4096          // horizon's fds stay well under this
#define RING     8             // distinct dest ports remembered per fd
#define STUN_SRC 4             // STUN sources remembered per fd
#define TTL_MS   30000u        // how long a send keeps a destination "live"

struct DestSlot {
    uint16_t port;             // 0 = empty
    uint32_t ip,  t;           // most recent IP sent to on this port, and when
    uint32_t ip2, t2;          // the previous, different IP (0 = none)
};
struct FdState {
    DestSlot slot[RING];
    uint32_t stun_src[STUN_SRC];
    uint8_t  stun_next;
};

static FdState g_fd[FD_CAP];
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static std::atomic<uint32_t> c_ring_full{0};

static uint32_t now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000u + ts.tv_nsec / 1000000u);
}
static bool live(uint32_t t, uint32_t now) { return t != 0 && (uint32_t)(now - t) < TTL_MS; }

static void record_dest(int fd, const sockaddr *addr, socklen_t len) {
    if (fd < 0 || fd >= FD_CAP || addr == nullptr || len < (socklen_t)sizeof(sockaddr_in)) return;
    if (addr->sa_family != AF_INET) return;
    const sockaddr_in *in = reinterpret_cast<const sockaddr_in *>(addr);
    uint16_t port = in->sin_port;
    uint32_t ip   = in->sin_addr.s_addr;
    if (port == 0) return;
    uint32_t now = now_ms();
    if (now == 0) now = 1;                                   // 0 means "never"

    pthread_mutex_lock(&g_lock);
    FdState &f = g_fd[fd];
    int empty = -1, oldest = 0;
    for (int i = 0; i < RING; i++) {
        DestSlot &s = f.slot[i];
        if (s.port == port) {
            if (s.ip != ip) { s.ip2 = s.ip; s.t2 = s.t; s.ip = ip; }
            s.t = now;
            pthread_mutex_unlock(&g_lock);
            return;
        }
        if (empty < 0 && s.port == 0) empty = i;
        if (s.port != 0 && (uint32_t)(now - s.t) > (uint32_t)(now - f.slot[oldest].t)) oldest = i;
    }
    int idx = empty;
    if (idx < 0) { idx = oldest; c_ring_full.fetch_add(1, std::memory_order_relaxed); }
    f.slot[idx] = DestSlot{port, ip, now, 0, 0};
    pthread_mutex_unlock(&g_lock);
}

static void note_stun_src(int fd, uint32_t ip) {
    if (fd < 0 || fd >= FD_CAP) return;
    pthread_mutex_lock(&g_lock);
    FdState &f = g_fd[fd];
    for (int i = 0; i < STUN_SRC; i++)
        if (f.stun_src[i] == ip) { pthread_mutex_unlock(&g_lock); return; }
    f.stun_src[f.stun_next] = ip;
    f.stun_next = (uint8_t)((f.stun_next + 1) % STUN_SRC);
    pthread_mutex_unlock(&g_lock);
}

static void forget_fd(int fd) {
    if (fd < 0 || fd >= FD_CAP) return;
    pthread_mutex_lock(&g_lock);
    memset(&g_fd[fd], 0, sizeof(g_fd[fd]));
    pthread_mutex_unlock(&g_lock);
}

// A private/LAN IPv4 address (RFC1918 + link-local). The dual-homed receiver we
// fix is always on the LAN; a public/relayed source is never our target, so we
// only ever engage when the peer we learned is private. Argument in network order.
static bool is_private_v4(uint32_t ip_net) {
    uint32_t h = ntohl(ip_net);
    if ((h & 0xFF000000u) == 0x0A000000u) return true;            // 10.0.0.0/8
    if ((h & 0xFFF00000u) == 0xAC100000u) return true;            // 172.16.0.0/12
    if ((h & 0xFFFF0000u) == 0xC0A80000u) return true;            // 192.168.0.0/16
    if ((h & 0xFFFF0000u) == 0xA9FE0000u) return true;            // 169.254.0.0/16 link-local
    return false;
}

// STUN detection, mirroring WebRTC's own demux (port.cc): a UDP payload is a STUN
// message when its first two bits are 0 and the magic cookie 0x2112A442 sits at
// bytes 4-7. RTP/RTCP media never match (RTP's first byte is 0x80+, version 2).
static bool is_stun(const void *data, size_t n) {
    if (data == nullptr || n < 8) return false;
    const unsigned char *b = reinterpret_cast<const unsigned char *>(data);
    if ((b[0] & 0xC0) != 0x00) return false;                      // top 2 bits must be 0
    return b[4] == 0x21 && b[5] == 0x12 && b[6] == 0xA4 && b[7] == 0x42;
}

// Classify an inbound datagram and, if it is the dual-homed TV's media feedback,
// rewrite its reported source IP to the address we negotiated against so WebRTC's
// exact-match lookup (Port::GetConnection) succeeds. Returns true if it rewrote;
// *cls is set to a short classification in every case we examined.
//
// Rewrite ONLY when ALL hold:
//   * the feature is enabled and the source is IPv4, not loopback;
//   * this fd sent to exactly ONE IP on that port in the last TTL_MS ("stale" /
//     "ambiguous" otherwise), and that IP is a private LAN address;
//   * the source differs from it but is in the same /24 ("cross-subnet");
//   * the packet is not STUN ("stun": ICE stays native, and the source is then
//     remembered as "learned" for every later packet);
//   * the source never sent STUN on this fd ("learned") and we never sent to it
//     ourselves on this fd recently ("known-peer").
// Anything else passes through verbatim.
static bool fixup_src(int fd, sockaddr *addr, socklen_t len,
                      const void *data, size_t datalen, const char **cls) {
    *cls = nullptr;
    if (!g_enabled.load(std::memory_order_relaxed)) return false;
    if (fd < 0 || fd >= FD_CAP || addr == nullptr || len < (socklen_t)sizeof(sockaddr_in)) return false;
    if (addr->sa_family != AF_INET) { *cls = "ipv6"; return false; }
    sockaddr_in *in = reinterpret_cast<sockaddr_in *>(addr);
    uint16_t port = in->sin_port;
    uint32_t ip   = in->sin_addr.s_addr;
    if (port == 0) return false;
    if (ip == htonl(INADDR_LOOPBACK)) return false;
    uint32_t now = now_ms();

    uint32_t want = 0;
    bool have = false, stale = false, ambiguous = false, learned = false, known = false;
    pthread_mutex_lock(&g_lock);
    FdState &f = g_fd[fd];
    for (int i = 0; i < RING; i++) {
        const DestSlot &s = f.slot[i];
        if (s.port == 0) continue;
        if (s.port == port) {
            have = true;
            want = s.ip;
            stale = !live(s.t, now);
            ambiguous = s.ip2 != 0 && s.ip2 != s.ip && live(s.t2, now);
        }
        if ((s.ip == ip && live(s.t, now)) || (s.ip2 == ip && live(s.t2, now))) known = true;
    }
    for (int i = 0; i < STUN_SRC; i++) if (f.stun_src[i] == ip) learned = true;
    pthread_mutex_unlock(&g_lock);

    if (!have) { *cls = "local/non-cast"; return false; }      // not a fd we send on this port
    if (want == ip) return false;                              // already matches (healthy)
    if (!is_private_v4(want)) { *cls = "non-lan"; return false; } // relayed/public peer: never ours
    if ((want & htonl(0xFFFFFF00u)) != (ip & htonl(0xFFFFFF00u))) { *cls = "cross-subnet"; return false; }
    if (is_stun(data, datalen)) {                              // leave ICE alone -- the critical guard
        // Only a Binding REQUEST (type 0x0001) makes WebRTC add a peer-reflexive
        // connection for its source; a stray response from an unknown address is
        // dropped, so it must not stop the rewrite.
        const unsigned char *b = reinterpret_cast<const unsigned char *>(data);
        if (b[0] == 0x00 && b[1] == 0x01) note_stun_src(fd, ip);
        *cls = "stun";
        return false;
    }
    if (stale)     { *cls = "stale";      return false; }
    if (ambiguous) { *cls = "ambiguous";  return false; }
    if (learned)   { *cls = "learned";    return false; }
    if (known)     { *cls = "known-peer"; return false; }

    in->sin_addr.s_addr = want;
    *cls = "tv-feedback";
    return true;
}
// Rate-limited diagnostic (setprop debug.qe.cast_shim_log 1). Logs the rewrite we
// made, and -- when the same-/24 dual-homed case arrived as STUN -- logs that we
// correctly left it alone, so on-device we can see the classifier working.
static void maybe_log(uint32_t from, uint32_t to, uint16_t port, const char *cls) {
    if (!g_log.load(std::memory_order_relaxed)) return;
    if (cls == nullptr) return;
    static std::atomic<time_t> last{0};
    time_t now = time(nullptr);
    time_t prev = last.load(std::memory_order_relaxed);
    if (now == prev) return;                         // at most once per second
    last.store(now, std::memory_order_relaxed);
    char a[INET_ADDRSTRLEN], b[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &from, a, sizeof(a));
    inet_ntop(AF_INET, &to,   b, sizeof(b));
    if (strcmp(cls, "tv-feedback") == 0)
        LOGI("classified tv-feedback: src %s -> %s (port %u) rewritten so WebRTC accepts it", a, b, ntohs(port));
    else if (strcmp(cls, "stun") == 0)
        LOGI("classified stun: src %s (port %u) left untouched (ICE stays native)", a, ntohs(port));
    else if (!strcmp(cls, "stale") || !strcmp(cls, "ambiguous") || !strcmp(cls, "learned") ||
             !strcmp(cls, "known-peer"))
        LOGI("held back (%s): src %s (port %u) left untouched", cls, a, ntohs(port));
}

// ---------------------------------------------------------------------------
// Hooked functions. Originals are resolved once via dlsym and called through a
// function pointer, which bypasses the GOT -- so even though we hook these
// symbols everywhere, our own call to the original never re-enters the proxy.
// The semantic rule above keeps this scoped to the broken flow; with the prop
// off, every path is a pure pass-through.
// ---------------------------------------------------------------------------
typedef ssize_t (*recvfrom_t)(int, void *, size_t, int, sockaddr *, socklen_t *);
typedef ssize_t (*recvmsg_t)(int, msghdr *, int);
typedef ssize_t (*sendto_t)(int, const void *, size_t, int, const sockaddr *, socklen_t);
typedef ssize_t (*sendmsg_t)(int, const msghdr *, int);
typedef int     (*close_t)(int);

static recvfrom_t o_recvfrom = nullptr;
static recvmsg_t  o_recvmsg  = nullptr;
static sendto_t   o_sendto   = nullptr;
static sendmsg_t  o_sendmsg  = nullptr;
static close_t    o_close    = nullptr;

// Tally what the classifier decided (for the 10 s stats line).
static void tally(bool rewrote, const char *cls, bool v4) {
    if (v4) c_recv_v4.fetch_add(1, std::memory_order_relaxed);
    if (rewrote) { c_rewrite.fetch_add(1, std::memory_order_relaxed); return; }
    if (cls == nullptr) return;
    if      (!strcmp(cls, "stun"))           c_stun.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "local/non-cast")) c_nocast.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "non-lan"))        c_nolan.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "cross-subnet"))   c_xsub.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "stale"))          c_stale.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "ambiguous"))      c_ambig.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "learned"))        c_learned.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "known-peer"))     c_known.fetch_add(1, std::memory_order_relaxed);
    else if (!strcmp(cls, "ipv6"))           c_ipv6.fetch_add(1, std::memory_order_relaxed);
}

static ssize_t h_recvfrom(int fd, void *buf, size_t n, int flags,
                          sockaddr *src, socklen_t *src_len) {
    refresh_gate();
    ssize_t r = o_recvfrom(fd, buf, n, flags, src, src_len);
    if (r >= 0) c_recv.fetch_add(1, std::memory_order_relaxed);
    if (r >= 0 && src && src_len) {
        sockaddr_in before = {};
        if (*src_len >= (socklen_t)sizeof(sockaddr_in) && src->sa_family == AF_INET)
            before = *reinterpret_cast<sockaddr_in *>(src);
        const char *cls = nullptr;
        // The received bytes ARE the classifier's payload (buf..buf+r), so STUN is
        // recognised from the real packet and never rewritten.
        bool rewrote = fixup_src(fd, src, *src_len, buf, (r > 0) ? (size_t)r : 0, &cls);
        tally(rewrote, cls, before.sin_family == AF_INET);
        maybe_log(before.sin_addr.s_addr,
                  reinterpret_cast<sockaddr_in *>(src)->sin_addr.s_addr,
                  before.sin_port, rewrote ? "tv-feedback" : cls);
    }
    return r;
}

static ssize_t h_recvmsg(int fd, msghdr *msg, int flags) {
    refresh_gate();
    ssize_t r = o_recvmsg(fd, msg, flags);
    if (r >= 0) c_recv.fetch_add(1, std::memory_order_relaxed);
    if (r >= 0 && msg && msg->msg_name && msg->msg_namelen >= (socklen_t)sizeof(sockaddr_in)) {
        sockaddr *src = reinterpret_cast<sockaddr *>(msg->msg_name);
        sockaddr_in before = {};
        if (src->sa_family == AF_INET) before = *reinterpret_cast<sockaddr_in *>(src);
        // A datagram's header bytes are in the first iovec; use them to classify.
        const void *data = nullptr; size_t datalen = 0;
        if (msg->msg_iov && msg->msg_iovlen > 0) {
            data = msg->msg_iov[0].iov_base;
            datalen = msg->msg_iov[0].iov_len;
            if ((size_t)r < datalen) datalen = (size_t)r;   // never read past what arrived
        }
        const char *cls = nullptr;
        bool rewrote = fixup_src(fd, src, msg->msg_namelen, data, datalen, &cls);
        tally(rewrote, cls, before.sin_family == AF_INET);
        maybe_log(before.sin_addr.s_addr,
                  reinterpret_cast<sockaddr_in *>(src)->sin_addr.s_addr,
                  before.sin_port, rewrote ? "tv-feedback" : cls);
    }
    return r;
}

static ssize_t h_sendto(int fd, const void *buf, size_t n, int flags,
                        const sockaddr *dst, socklen_t dst_len) {
    c_send.fetch_add(1, std::memory_order_relaxed);
    record_dest(fd, dst, dst_len);
    return o_sendto(fd, buf, n, flags, dst, dst_len);
}

static ssize_t h_sendmsg(int fd, const msghdr *msg, int flags) {
    c_send.fetch_add(1, std::memory_order_relaxed);
    if (msg && msg->msg_name)
        record_dest(fd, reinterpret_cast<const sockaddr *>(msg->msg_name), msg->msg_namelen);
    return o_sendmsg(fd, msg, flags);
}

static int h_close(int fd) {
    forget_fd(fd);
    return o_close(fd);
}

// ---------------------------------------------------------------------------
// Install: patch ONLY liboculus_p2p.so (Horizon's WebRTC), once it is loaded.
// ---------------------------------------------------------------------------
#define CAST_LIB "liboculus_p2p.so"

template <class T> static void seed(T &p, const char *name) {
    p = reinterpret_cast<T>(dlsym(RTLD_DEFAULT, name));
}

struct LibInfo { uintptr_t base; char name[512]; };

// dl_iterate_phdr callback: find the cast library and the address of its ELF
// header (load bias + vaddr of the first PT_LOAD at file offset 0). For a
// library loaded from inside an APK, dlpi_name is "<apk>!/lib/<abi>/<name>".
static int find_cast_lib(struct dl_phdr_info *info, size_t, void *data) {
    if (info->dlpi_name == nullptr || strstr(info->dlpi_name, CAST_LIB) == nullptr) return 0;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) &ph = info->dlpi_phdr[i];
        if (ph.p_type == PT_LOAD && ph.p_offset == 0) {
            LibInfo *li = static_cast<LibInfo *>(data);
            li->base = (uintptr_t)(info->dlpi_addr + ph.p_vaddr);
            strncpy(li->name, info->dlpi_name, sizeof(li->name) - 1);
            li->name[sizeof(li->name) - 1] = '\0';
            return 1;                                   // stop iterating
        }
    }
    return 0;
}

static bool patch_cast_lib(const LibInfo &li) {
    static xh_elf_t elf;                               // xh_elf keeps a pointer to the name
    static char name[512];
    memcpy(name, li.name, sizeof(name));
    status("found %s at base %p", name, (void *)li.base);
    // xh_elf looks the GOT page up in /proc/self/maps by this name before it
    // mprotects it. For a library inside an APK the linker's name is
    // "<apk>!/lib/<abi>/<lib>", but maps only shows "<apk>", so no line matched
    // and every imported symbol failed with XH_ERRNO_SEGVERR (1009) in v3/v4.
    // Use the APK path; the address range still picks the right mapping.
    if (char *bang = strstr(name, "!/")) *bang = '\0';
    int rc = xh_elf_init(&elf, li.base, name);
    if (rc != 0) {
        status("ERROR: xh_elf_init failed (rc %d) for %s; shim inactive", rc, name);
        return false;
    }
    // Originals stay the dlsym'd libc functions (seeded in install_hooks), so the
    // proxies never call back into a GOT entry.
    struct { const char *sym; void *fn; } hooks[] = {
        {"recvfrom", (void *)h_recvfrom},
        {"recvmsg",  (void *)h_recvmsg},
        {"sendto",   (void *)h_sendto},
        {"sendmsg",  (void *)h_sendmsg},
        {"close",    (void *)h_close},
    };
    // xh_elf_hook returns 0 both when it patched the GOT and when the library
    // does not import the symbol at all, so tell them apart by whether it
    // handed back the previous GOT value.
    int ok = 0;
    char res[256] = {0};
    for (auto &h : hooks) {
        void *prev = nullptr;
        int hr = xh_elf_hook(&elf, h.sym, h.fn, &prev);
        const char *what;
        char err[16];
        if (hr != 0)            { snprintf(err, sizeof(err), "err%d", hr); what = err; }
        else if (prev != nullptr) { what = "hooked"; ok++; }
        else                     what = "not-imported";
        char one[48];
        snprintf(one, sizeof(one), " %s=%s", h.sym, what);
        strncat(res, one, sizeof(res) - strlen(res) - 1);
    }
    status("patched %s (%s): %d hooked:%s", CAST_LIB, name, ok, res);
    return ok > 0;
}

// Logs, once, every library the linker reports from inside an APK, so if the
// name match ever fails we can see the real names in one run.
static int list_apk_libs(struct dl_phdr_info *info, size_t, void *) {
    if (info->dlpi_name && (strstr(info->dlpi_name, ".apk") || strstr(info->dlpi_name, "p2p")))
        status("  linker reports: %s", info->dlpi_name);
    return 0;
}

static void stats_tick() {
    static uint32_t last_recv = 0, last_send = 0;
    uint32_t r = c_recv.load(std::memory_order_relaxed), s = c_send.load(std::memory_order_relaxed);
    if (r == last_recv && s == last_send) return;              // only while there's traffic
    last_recv = r; last_send = s;
    status("stats: shim=%d recv %u (ipv4 %u) send %u | rewrites %u, stun-left %u, "
           "non-cast %u, non-lan %u, cross-subnet %u | held back: stale %u, ambiguous %u, "
           "learned %u, known-peer %u | ipv6 %u, ring-full %u",
           g_enabled.load() ? 1 : 0, r, c_recv_v4.load(), s, c_rewrite.load(), c_stun.load(),
           c_nocast.load(), c_nolan.load(), c_xsub.load(), c_stale.load(), c_ambig.load(),
           c_learned.load(), c_known.load(), c_ipv6.load(), c_ring_full.load());
}

// Waits (cheaply) for the cast library, patches it once, then writes a stats
// line every 10 s while it carries traffic (counters are cumulative).
static void *watch_for_cast_lib(void *) {
    status("watcher started, polling for %s every 0.5 s", CAST_LIB);
    for (unsigned polls = 1;; polls++) {
        LibInfo li = {};
        if (dl_iterate_phdr(find_cast_lib, &li) && li.base != 0) {
            status("%s appeared after %u polls", CAST_LIB, polls);
            if (!patch_cast_lib(li)) dl_iterate_phdr(list_apk_libs, nullptr);
            break;
        }
        if (polls == 60) {                                       // 30 s: show what IS loaded
            status("still waiting for %s after 30 s; APK libraries the linker reports:", CAST_LIB);
            dl_iterate_phdr(list_apk_libs, nullptr);
        } else if (polls % 600 == 0) {
            status("still waiting for %s (%u s)", CAST_LIB, polls / 2);
        }
        usleep(500 * 1000);
    }
    for (;;) {
        sleep(10);
        refresh_gate();
        stats_tick();
    }
    return nullptr;
}

static void install_hooks() {
    {   // fresh status file for this Horizon process
        pthread_mutex_lock(&g_file_lock);
        FILE *f = fopen(STATUS_FILE, "w");
        if (f) fclose(f);
        pthread_mutex_unlock(&g_file_lock);
    }
    seed(o_recvfrom, "recvfrom");
    seed(o_recvmsg,  "recvmsg");
    seed(o_sendto,   "sendto");
    seed(o_sendmsg,  "sendmsg");
    seed(o_close,    "close");
    if (!o_recvfrom || !o_recvmsg || !o_sendto || !o_sendmsg || !o_close) {
        status("ERROR: could not resolve libc socket calls; shim not installed");
        return;
    }
    refresh_gate();
    status("installed in com.oculus.horizon (v6), cast_shim=%d log=%d",
           g_enabled.load() ? 1 : 0, g_log.load() ? 1 : 0);
    pthread_t t;
    int pr = pthread_create(&t, nullptr, watch_for_cast_lib, nullptr);
    if (pr == 0) pthread_detach(t);
    else status("ERROR: watcher thread not started (%d)", pr);
}

// ---------------------------------------------------------------------------
// Zygisk entry: install only in Horizon, unload everywhere else.
// ---------------------------------------------------------------------------
class CastShim : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override { api_ = api; env_ = env; }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        mine_ = false;
        if (args && args->nice_name) {
            const char *name = env_->GetStringUTFChars(args->nice_name, nullptr);
            mine_ = name && strcmp(name, "com.oculus.horizon") == 0;
            if (name && strstr(name, "oculus.horizon"))
                LOGI("preAppSpecialize: %s (uid %d) -> %s", name, args->uid,
                     mine_ ? "installing" : "skipped");
            if (name) env_->ReleaseStringUTFChars(args->nice_name, name);
        }
        // Don't request DLCLOSE when we're going to hook: our code must stay mapped.
        if (!mine_) api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (mine_) install_hooks();
    }

private:
    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
    bool mine_ = false;
};

REGISTER_ZYGISK_MODULE(CastShim)
