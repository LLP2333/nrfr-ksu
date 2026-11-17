#!/system/bin/sh
# Late-start service: apply the config once SIMs are up, then re-apply whenever the
# phone process restarts or SIM state changes (in-memory overrides are lost then).
MODDIR=${0%/*}
. "$MODDIR/common.sh"

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done

sim_ready() {
    case "$(getprop gsm.sim.state)" in *LOADED*|*READY*) return 0 ;; esac
    return 1
}
i=0
until sim_ready || [ $i -ge 60 ]; do sleep 2; i=$((i + 1)); done
sleep 3

log "boot: sim.state=$(getprop gsm.sim.state)"
apply_conf >/dev/null

state() {
    echo "$(pidof com.android.phone)|$(getprop gsm.sim.state)|$(getprop gsm.sim.operator.numeric)"
}
last=$(state)
while true; do
    sleep 10
    if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; then
        # module switched off in the manager: drop overrides now instead of waiting for reboot
        if [ -z "$off" ]; then
            log "module disabled: reset all"
            nrfr reset all >> "$LOG" 2>&1
            off=1
        fi
        continue
    fi
    if [ -n "$off" ]; then
        log "module re-enabled"
        off=
        apply_conf >/dev/null
        last=$(state)
        continue
    fi
    now=$(state)
    if [ "$now" != "$last" ]; then
        log "change: $last -> $now"
        sleep 5
        apply_conf >/dev/null
        last=$(state)
    fi
done
