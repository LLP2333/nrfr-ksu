#!/system/bin/sh
# KernelSU "Action" button: re-apply the config and show the current state.
MODDIR=${0%/*}
. "$MODDIR/common.sh"

echo "配置文件: $CONF"
grep -vE '^\s*(#|$)' "$CONF" 2>/dev/null || echo "(空)"
echo
echo "== 应用 =="
apply_conf
echo
echo "== 当前状态 =="
nrfr list
