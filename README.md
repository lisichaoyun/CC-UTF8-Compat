# CC UTF-8 兼容补丁

CC UTF-8 兼容补丁为 Minecraft 1.21.1 NeoForged 上的 CC:Tweaked 添加 UTF-8 兼容性补丁。

该模组改进了 CC:Tweaked 终端及相关外设中的 UTF-8 文本处理。它主要面向需要非 ASCII 文本支持的整合包或服务器,例如俄语、中文、西班牙语变音符号以及其他 Unicode 字符。

## 功能特性

* 计算机终端中的 UTF-8 文本渲染
* UTF-8 输入与粘贴支持
* 计算机标签的 UTF-8 支持
* 服务器与客户端之间的 UTF-8 终端同步
* 显示器的 UTF-8 渲染修复
* 掌上电脑的 UTF-8 支持
* 打印机和打印页面的 UTF-8 支持
* 为选定的程序和 API 修补了 CraftOS Lua 文件,以获得更好的 UTF-8 行为

## 相比原版修复

* 修复了脚本程序内部 UTF-8 字符(尤其是中文字符)显示不完整的问题,汉化字符也能完整渲染。
* 除英文和中文外,其他语言用户也可以正常使用本模组来支持各自的 Unicode 字符。

## 欢迎维护改进

欢迎社区对本模组进行维护和改进。如果你修复了 bug、适配了新的 CC:Tweaked 版本,或增加了新的 UTF-8 兼容功能,欢迎提交 PR。

## 测试环境

* Minecraft 1.21.1
* NeoForged 21.1.77
* CC:Tweaked 1.120.2

## 环境要求

* Minecraft 1.21.1
* NeoForged
* CC:Tweaked 1.120.2

该模组是版本特定的,并且依赖于 CC:Tweaked 的内部实现。无法保证其他版本的 CC:Tweaked 能够正常使用。

## 安装方法

客户端和服务器都需要安装该模组。

所需文件:

```text
mods/
  cc-tweaked-1.21.1-forge-1.120.2.jar
  cc_utf8_compat-1.1.0.jar
```

单人模式下,将两个模组都放入客户端的 `mods` 文件夹。

多人模式下,兼容性模组必须在客户端和服务端都安装。

## 配置

首次启动后,会自动生成配置文件:

```text
config/cc_utf8_compat-common.toml
```

可以启用或禁用 UTF-8 兼容层:

```toml
ccUtf8Compat = true
```

将其设置为 `false` 可以在不卸载模组的情况下禁用补丁。

## 已知限制

该模组使用 mixin 修补 CC:Tweaked 的内部类。因此,它仅在 CC:Tweaked 1.120.2 上进行了测试。

较新版本的 CC:Tweaked 可能会更改内部类名、方法名或行为,这可能会破坏兼容性。

## 许可证

本项目基于 MIT 许可证发布。

## 其他语言

* [English](README_EN.md)
