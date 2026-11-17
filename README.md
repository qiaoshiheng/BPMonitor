# BPMonitor 血压记录

一款简洁美观的安卓血压记录 APP：纯 Java + 平台原生控件实现，零第三方依赖，数据全部保存在本地。

## 功能

- **血压记录**：高压/低压/心率录入，支持自定义测量时间、备注；"脉搏不规律"与"疑似房颤"两个独立开关
- **多用户档案**：新增/切换家庭成员，每人独立记录与基础资料（性别、年龄、身高、体重，BMI 自动计算）
- **趋势图表**：日（按真实时刻定位）/ 周 / 月 / 年四档折线图，收缩压/舒张压/心率三线 + 统计摘要（Canvas 自绘）
- **健康提示引擎**：基于《中国高血压防治指南（2024年修订版）》、2020 ESC 房颤管理指南、STROKESTOP II 等文献，结合血压分级、房颤/不规律脉搏记录、BMI、年龄、CHA₂DS₂-VA 卒中风险初评生成针对性建议（按严重程度排序）
- **AI 咨询文本一键生成**：汇总统计数据 + 预置问诊提示词，复制或分享给任意 AI
- **应用内自动更新**：启动自动检查新版本；多更新源并发竞速（直连 + 国内镜像 + GitHub API 兜底），全部源不可达时使用本地缓存版本信息，国内弱网环境可靠更新
- **自适应启动图标**（含 Android 13+ 单色主题图标）
- 全应用统一圆角卡片弹窗风格（用户选择/新增/资料编辑/删除确认/日期时间选择）

## 技术栈

| 项 | 说明 |
|---|---|
| 语言 | Java（无 Kotlin） |
| UI | 平台原生控件 + Canvas 自绘图表，无第三方库 |
| 构建 | AGP 9.1.0 / Gradle 9.3.1 / JDK 21 / compileSdk 36 |
| 兼容 | minSdk 26（Android 8.0） |
| 数据 | SQLite 本地存储（当前 schema v3，自动迁移），无网络权限 |

## 构建

用 Android Studio 打开工程目录即可；或命令行：

```bash
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
app/src/main/java/com/q/bpmonitor/
├── MainActivity.java        # 主界面：最新读数卡片、趋势图、健康提示、记录列表、用户/AI 文本弹窗
├── AddRecordActivity.java   # 录入页
├── ChartView.java           # Canvas 自绘折线图
├── DbHelper.java            # SQLite（v3：多用户 + 基础资料）
├── Record.java / User.java  # 数据模型
app/src/main/res/
├── layout/                  # 主界面 / 录入页 / 各弹窗布局
├── drawable/                # 图标与背景样式
└── mipmap-anydpi-v26/       # 自适应启动图标
```

## 说明

- 应用内健康提示仅供参考，不构成医疗诊断依据
- 删除记录为长按操作，删除后不可恢复

## 许可协议（License）

本项目自 **v1.4 起采用自定义非商用许可协议**（详见 [LICENSE](LICENSE)）：

- ✅ 允许：个人使用、学习、修改、分享（需保留版权声明与本协议）
- ✅ 修改分发需同时开源，并采用相同协议
- ❌ 禁止：出售、上架应用商店收费、植入广告或任何商业变现
- 历史版本（≤ v1.3）曾以 MIT License 发布，既有授权不受追溯影响
- 商业使用请联系作者获取书面授权

[![License](https://img.shields.io/badge/license-Non--Commercial-red.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/qiaoshiheng/BPMonitor)](https://github.com/qiaoshiheng/BPMonitor/releases)
