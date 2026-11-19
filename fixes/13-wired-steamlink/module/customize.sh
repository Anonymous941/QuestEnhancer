# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Gregory Anderson
# Quest 1 only: the USB gadget layout below is the monterey one.
[ "$(getprop ro.product.device)" = "monterey" ] || abort "  ! not a Quest 1 (monterey), aborting"
[ -d /config/usb_gadget/g1/functions/ncm.0 ] || abort "  ! no ncm.0 USB function on this kernel, aborting"
ui_print "  Quest 1 wired Steam Link: reboot to start."
ui_print "  Log: /data/adb/q1_steamlink_usb.log"
