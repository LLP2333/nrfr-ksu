#!/system/bin/sh
SKIPUNZIP=0

DATADIR=/data/adb/nrfr
mkdir -p "$DATADIR"
if [ ! -f "$DATADIR/nrfr.conf" ]; then
    cat > "$DATADIR/nrfr.conf" <<'EOF'
# Nrfr KSU 配置。每行: <卡槽>=<两位国家码>[,<运营商名称>]
# 例:
# 1=jp
# 2=us,My Carrier
EOF
    ui_print "- 已创建配置文件 $DATADIR/nrfr.conf"
else
    ui_print "- 保留已有配置 $DATADIR/nrfr.conf"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/nrfr" 0 0 0755
ui_print "- 在 KernelSU 管理器打开本模块的 WebUI 进行设置"
ui_print "- 或命令行: su -c /data/adb/modules/nrfr_ksu/nrfr list"
