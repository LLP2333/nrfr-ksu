# Nrfr KSU

这是 [Nrfr](https://github.com/Ackites/Nrfr) 的 KernelSU 模块版，用来修改 SIM 卡国家码和运营商名称。不需要 Shizuku，也不需要 App。

## 原理

它和 Nrfr 用的是同一个系统接口 `ICarrierConfigLoader.overrideConfig`，覆盖运营商配置中的
`sim_country_iso_override_string`、`carrier_name_override_bool` 和 `carrier_name_string`。
区别是：Nrfr 通过 Shizuku 以 shell 身份调用，本模块则用 `app_process` 以 root 身份运行一个很小的 dex 来调用。

- **只在内存里覆盖**：不修改 SIM 卡、基带、system 分区或 APN，也不写持久化配置。
  以 root 身份写持久化覆盖本来就会被系统拒绝（`persistent=true` 只允许 system app 使用）。
- **可以还原**：在 WebUI 点「还原」立即恢复；在管理器里禁用模块，约 10 秒内全部还原。模块启用时重启会重新应用配置；禁用或卸载模块后，即使脚本没来得及执行，重启也必然恢复原样（覆盖只存在内存里）。
- **还原时恢复真实国家码**：只清空覆盖值的话，系统不会把国家码改回来。所以还原时会用 SIM 卡的 MCC
  算出原始国家码（通过系统自带的 `MccTable`），先写回原值，再清空覆盖。
- **自动重新应用**：开机后，以及电话进程重启、SIM 状态变化时，都会按配置重新应用。

## 使用

从 [Releases](https://github.com/LLP2333/nrfr-ksu/releases/latest) 下载 `nrfr-ksu-<版本>.zip`，在 KernelSU 管理器里安装并重启（之后可以在管理器里检查更新），然后用下面任意一种方式设置：

- **WebUI**：在 KernelSU 管理器里打开本模块，按卡槽填写国家码和（可选的）运营商名称，点"保存并应用"。
- **Action 按钮**：重新应用配置并显示当前状态。
- **配置文件** `/data/adb/nrfr/nrfr.conf`（模块更新后保留，卸载时删除）：
  ```
  # <卡槽>=<两位国家码>[,<运营商名称>]
  1=jp
  2=us,My Carrier
  ```
- **命令行**（需要 root）：
  ```sh
  su -c /data/adb/modules/nrfr_ksu/nrfr list
  su -c /data/adb/modules/nrfr_ksu/nrfr set 2 jp       # 临时设置，不写入配置文件
  su -c /data/adb/modules/nrfr_ksu/nrfr reset all
  su -c /data/adb/modules/nrfr_ksu/nrfr apply          # 按配置文件应用
  ```

日志在 `/data/adb/nrfr/nrfr.log`。

## 构建

需要 JDK 17 及以上和 Android SDK（会自动选用最新的 `platforms/android-*` 和 `build-tools`）：

```sh
./build.sh   # 生成 module/nrfr.dex 和 out/nrfr-ksu-<version>.zip
```

## 已测试

- Xiaomi 15，Android 17（SDK 37，user 版本），KernelSU 3.3.1，双卡

## 致谢

实现思路来自 [Ackites/Nrfr](https://github.com/Ackites/Nrfr)（Apache-2.0）。
