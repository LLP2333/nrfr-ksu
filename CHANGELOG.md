# 更新日志

## v1.0.1
- WebUI：国家选择改为可搜索的底部选择面板（中文名 / 代码搜索、国旗、常用国家快捷选择、自定义代码）
- 修正说明文字：模块启用时重启会自动重新应用配置，禁用或卸载模块才会还原
- 支持在 KernelSU 管理器中检查更新（updateJson）

## v1.0.0
- 首个版本：以 root 调用 `ICarrierConfigLoader.overrideConfig` 修改 SIM 国家码 / 运营商名称（仅内存覆盖）
- 开机、电话进程重启、SIM 状态变化时自动重新应用；禁用模块即还原
- WebUI、Action 按钮、命令行
