# Shared helpers, sourced by service.sh / action.sh / uninstall.sh and the WebUI.
MODDIR=${MODDIR:-/data/adb/modules/nrfr_ksu}
DATADIR=/data/adb/nrfr
CONF=$DATADIR/nrfr.conf
LOG=$DATADIR/nrfr.log

nrfr() {
    CLASSPATH="$MODDIR/nrfr.dex" /system/bin/app_process /system/bin com.github.nrfr.ksu.Main "$@"
}

log() {
    mkdir -p "$DATADIR"
    # keep the log small
    if [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 65536 ]; then
        tail -n 200 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
    fi
    echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"
}

apply_conf() {
    out=$(nrfr apply "$CONF" 2>&1)
    rc=$?
    [ -n "$out" ] && echo "$out" | while IFS= read -r l; do log "$l"; done
    echo "$out"
    return $rc
}
