#!/system/bin/sh
# Clear overrides right away; they are in-memory only, so a reboot would clear them anyway.
MODDIR=${0%/*}
. "$MODDIR/common.sh"
# KernelSU may run this early during boot, before telephony exists; only touch it when up.
if [ "$(getprop sys.boot_completed)" = 1 ]; then
    nrfr reset all >/dev/null 2>&1
fi
rm -rf "$DATADIR"
