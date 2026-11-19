#!/system/bin/sh
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Gregory Anderson
# Wired Steam Link for the Quest 1: keep USB networking (NCM) up.
#
# Newer Quest headsets do this themselves when Steam Link asks for it
# (`svc usb setFunctions ncm`); the Quest 1's USB HAL refuses NCM, but its
# kernel has it. This adds the NCM function next to adb/Link, gives the
# headset Valve's wired address, lets apps route to it, and hands the PC its
# address by DHCP (no gateway, so the PC never routes internet through it).
# Steam Link's multi-link then finds the link by itself and uses the cable
# alongside Wi-Fi (either one can drop out while streaming).
MODDIR=${0%/*}
LOG=/data/adb/q1_steamlink_usb.log
G=/config/usb_gadget/g1
BB=/data/adb/magisk/busybox
IP=10.86.13.37
PEER=10.86.13.38
PREFIX=29
DEV_MAC=02:51:31:00:00:01    # headset side
HOST_MAC=02:51:31:00:00:02   # the PC's side

say() {
  echo "$(date '+%m-%d %H:%M:%S') $*" >> $LOG
  /system/bin/log -t q1usb "$*" 2>/dev/null
}

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
sleep 5
say "starting"

cat > $MODDIR/udhcpd.conf <<CONF
interface usb0
start $PEER
end $PEER
max_leases 1
lease_file $MODDIR/udhcpd.leases
pidfile $MODDIR/udhcpd.pid
option subnet 255.255.255.248
option lease 86400
CONF
touch $MODDIR/udhcpd.leases

dhcpd_running() {
  pid=$(cat $MODDIR/udhcpd.pid 2>/dev/null) || return 1
  grep -q udhcpd /proc/$pid/cmdline 2>/dev/null
}

start_dhcpd() {
  # One PC per cable: forget earlier leases, or the single address stays
  # reserved for an adapter that no longer exists.
  : > $MODDIR/udhcpd.leases
  $BB udhcpd $MODDIR/udhcpd.conf && say "DHCP server started (PC gets $PEER)"
}

rebinds=0
last_ifindex=
while true; do
  # 1. NCM in the active USB configuration. Meta's USB HAL rebuilds the
  #    config on mode changes but leaves our extra link alone; if it ever
  #    goes, re-add it and rebind (at most a few times, so a fight with the
  #    HAL can't keep knocking adb off USB).
  if [ ! -e $G/configs/b.1/f9 ] && [ $rebinds -lt 5 ]; then
    # Fixed (locally administered) MACs: otherwise the kernel picks random
    # ones on every bind, and the PC sees a "new" adapter each time.
    echo $DEV_MAC > $G/functions/ncm.0/dev_addr 2>/dev/null
    echo $HOST_MAC > $G/functions/ncm.0/host_addr 2>/dev/null
    ln -s $G/functions/ncm.0 $G/configs/b.1/f9
    udc=$(ls /sys/class/udc | head -1)
    echo "" > $G/UDC; sleep 1; echo "$udc" > $G/UDC 2>/dev/null
    rebinds=$((rebinds + 1))
    say "added NCM to the USB config, rebound (#$rebinds)"
  fi

  if [ -e /sys/class/net/usb0 ]; then
    # 2. Address and DHCP. usb0 is recreated, down and empty, on every
    #    (re)plug (and adbd restart), and the DHCP server stays bound to the
    #    old one. The PC only sees the link once usb0 is up (NCM tells it
    #    then), so bring it up last: address, DHCP server, link. Otherwise
    #    the PC's first requests can arrive before the server, and a Mac
    #    then waits minutes before asking again.
    ifindex=$(cat /sys/class/net/usb0/ifindex)
    if [ "$ifindex" != "$last_ifindex" ] || ! ip -4 addr show usb0 | grep -q "inet $IP/"; then
      ip link set usb0 down
      ip addr add $IP/$PREFIX dev usb0 2>/dev/null
      if dhcpd_running; then
        kill "$(cat $MODDIR/udhcpd.pid)"
        sleep 1
      fi
      start_dhcpd
      ip link set usb0 up
      say "usb0 up as $IP/$PREFIX"
    fi
    last_ifindex=$ifindex
    # 3. Routing. Android routes apps per network and doesn't know usb0;
    #    these let traffic for usb0's own subnet (and link-local) through.
    ip rule | grep -q "^99:" || ip rule add from all lookup main pref 99
    ip -6 rule | grep -q "^99:" || ip -6 rule add from all lookup main pref 99
    ip -6 route show table main | grep -q "fe80::/64 dev usb0" \
      || ip -6 route add fe80::/64 dev usb0 table main 2>/dev/null
    # 4. Keep the DHCP server running if it ever exits.
    dhcpd_running || start_dhcpd
  fi
  sleep 1
done
