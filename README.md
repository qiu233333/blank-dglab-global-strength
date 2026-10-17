# Blank DG-LAB 全局电击强度补丁

为 [Blank DG-LAB](https://www.curseforge.com/minecraft/mc-mods/blank-dg-lab) 添加 A、B 通道独立的全局强度倍率。这个仓库是基于指定上游 JAR 制作的社区修改版，与原作者无关联。

**已验证环境：** Minecraft 26.2、Fabric Loader 0.19.5、Java 25、Fabric API 0.161.0+26.2、YetAnotherConfigLib (YACL) 3.9.7+26.2-fabric。用户已在游戏中确认菜单和功能可用。其他 Minecraft 版本及 NeoForge 尚未验证；本补丁不能仅靠修改版本声明移植。

## 下载与安装

1. 下载 [`dist/Blank-DG-LAB-global-strength-MC26.2-Fabric.jar`](dist/Blank-DG-LAB-global-strength-MC26.2-Fabric.jar)。
2. 在目标 **Minecraft 26.2 + Fabric** 实例的 `mods` 文件夹中，先移走原版或旧版 Blank DG-LAB JAR，然后放入本 JAR。不能让两个 `blankdglab` 模组同时加载。
3. 安装适用于 Minecraft 26.2 的 Fabric API 和 YACL。此 JAR 声明需要 Fabric Loader ≥ 0.18.0、YACL ≥ 3.9.0；上面的版本组合是实际验证过的组合。
4. 进入游戏按默认的 `B` 键，打开“全局电击强度”，分别输入 A、B 通道倍率并保存。

配置文件位于游戏实例的 `config/blankdglab-global-strength.properties`。首次安装没有配置文件时，A、B 倍率均为 `1.0`。配置属于实例，不会随 JAR 自动带到其他整合包；若想复制原来的设置，可另外复制此文件。公开仓库未包含个人配置。

## 计算方式

每个通道独立计算：`输出强度 = 四舍五入(原强度 × 该通道倍率)`，然后仍受原模组的强度限制和 DG-LAB App 通道上限约束。倍率范围是 `0.0`～`1.0`；`0.0` 关闭该通道输出，`1.0` 保持原强度。保存新倍率时，当前输出先归零，之后的新反馈采用新倍率。

例如事件原强度为 `100`，A 倍率 `0.1`、B 倍率 `0.2`，且各通道有效上限至少为 `20`，则发出的 A 强度为 `10`、B 强度为 `20`。若上限更低，则按上限截断。多人联机的转发逻辑仍由原模组处理。

## 源码与构建

`source/` 包含两个新增类和三个重新编译的原类。原类取自本地上游 JAR 的反编译结果，再加入菜单入口和强度计算；仓库没有上游完整源码。`build.ps1` 将这五个类编译后覆盖到**你提供的原始 JAR 副本**中，不会修改传入的原始文件。构建结果位于 `build/Blank-DG-LAB-global-strength-26.2-Fabric.jar`。

此补丁锁定的原始 JAR SHA-256 为：

```text
4D688A9743E35EF6D02E18A94EED1D3259C02A0A09A03FA4DEB164255701CF37
```

**版本标注说明：** 本地原始文件名带有 `v2.2.0`，但其 `fabric.mod.json` 内部版本为 `1.1.0`。本仓库以 SHA-256 指定构建基底，发布文件名不声称其为官方 v2.2.0。请不要把其他上游版本的 JAR 直接作为构建基底。

在 Windows PowerShell 中，从仓库根目录运行：

```powershell
$instance = 'C:\path\to\.minecraft\versions\26.2-Fabric 0.19.5'
$minecraftHome = 'C:\path\to\.minecraft'
.\build.ps1 `
  -OriginalJar 'C:\path\to\original-blank-dg-lab.jar' `
  -MinecraftJar (Join-Path $instance '26.2-Fabric 0.19.5.jar') `
  -LibrariesDirectory (Join-Path $minecraftHome 'libraries') `
  -ProcessedModsDirectory (Join-Path $instance '.fabric\processedMods') `
  -ModsDirectory (Join-Path $instance 'mods') `
  -JavaHome 'C:\path\to\jdk-25'
```

构建脚本会校验原始 JAR 的 SHA-256，从其中提取已打包的 Java-WebSocket 依赖，再用所给 Minecraft 实例的依赖编译。`test/GlobalStrengthSettingsCheck.java` 覆盖 A/B 倍率计算、零倍率和配置读取；此外，已验证游戏内 B 键菜单及实际使用。公开的 `dist/` JAR 与本机已验证版本相同，SHA-256 为：

```text
2151FDA4F27C958CD4D3E5FEE8A80817A284B65EC2029BE8415C81DE1778859C
```

## 上游、授权与说明

上游项目：[GitHub](https://github.com/kskongbai/Blank-DG-LAB) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/blank-dg-lab)。上游公开页面和仓库 `LICENSE` 标注 **LGPL-3.0**，本仓库保留相同的 [`LICENSE`](LICENSE)，并标明所作修改。需注意，构建基底 JAR 的 `fabric.mod.json` 中写的是 `All Rights Reserved`，与公开页面的授权标注不一致；这是上游发布物中的现有差异，不应理解为本项目能代表原作者解释授权范围。

本补丁仅为 Minecraft 26.2 Fabric 的已验证构建。更换游戏版本、模组加载器或上游 JAR 后，需要重新适配并测试。
