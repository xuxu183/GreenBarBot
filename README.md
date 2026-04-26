# GreenBarBot — 安卓自动识别控制器

## 功能总览

| 功能 | 说明 |
|------|------|
| **OpenCV HSV 识别** | 每 25ms 截图，检测绿条中心 vs 黄杠位置 |
| **模板匹配** | HSV 失效时自动切换，彻底摆脱颜色依赖 |
| **自动长按/松开** | 通过辅助功能 API 实现真实触摸，无 Root |
| **可拖动悬浮窗** | 左/右两块，拖到目标按钮位置即为点击坐标 |
| **自适应分辨率** | 基于 1080p 参考缩放，适配所有机型 |
| **一键调参 UI** | 实时 HSV 滑块 + 实时预览蒙版 |
| **3 个场景** | 独立存储参数，一键切换不同游戏 |
| **配置持久化** | SharedPreferences 自动保存 |

---

## 快速开始

### 1. 编译环境
- Android Studio Hedgehog 或更新
- JDK 11+
- Android SDK API 26–34

### 2. 导入项目
```
File → Open → 选择 GreenBarBot 文件夹
等待 Gradle Sync 完成
```

### 3. 首次运行权限流程
1. 安装后打开 APP
2. **悬浮窗权限**：APP 自动弹出系统页面 → 开启
3. **辅助功能**：设置 → 辅助功能 → 已安装的服务 → GreenBarBot → 开启
4. **屏幕录制**：点击「一键启动」时系统弹窗 → 允许

### 4. 操作步骤
1. 拖动蓝色「左」悬浮块到游戏中向左的按钮上
2. 拖动红色「右」悬浮块到游戏中向右的按钮上
3. 调整 HSV 滑块让绿条和黄杠都能被检测到（用校准页验证）
4. 打开目标游戏界面，回到 APP 开启主开关

---

## HSV 参数调节指南

| 颜色 | Hue 范围 | 说明 |
|------|----------|------|
| 绿色 | 35–85 | 纯绿 ~60，黄绿 ~35，青绿 ~85 |
| 黄色 | 20–35 | 黄色固定范围 |

- **饱和度 (Sat)**: 调高可过滤灰色/白色干扰
- **亮度 (Val)**: 调低可适应暗色界面

---

## 识别逻辑

```
截图 → HSV 分割 → 找绿条最大轮廓 → 取中心 X
                → 找黄杠最大轮廓 → 取中心 X

若 绿条中心 < 黄杠中心 - threshold:
    长按 左悬浮块坐标，松开 右悬浮块坐标

若 绿条中心 > 黄杠中心 + threshold:
    长按 右悬浮块坐标，松开 左悬浮块坐标

否则:
    全部松开
```

---

## 文件结构

```
GreenBarBot/
├── app/src/main/
│   ├── java/com/greenbarbot/
│   │   ├── ui/
│   │   │   ├── MainActivity.java          # 主界面 + 调参 UI
│   │   │   └── CalibrationActivity.java   # 实时校准预览
│   │   ├── service/
│   │   │   ├── FloatingWindowService.java # 悬浮窗 + 坐标管理
│   │   │   └── ScreenCaptureService.java  # 25ms 截图识别循环
│   │   └── vision/
│   │       ├── GreenBarDetector.java      # OpenCV HSV + 模板匹配
│   │       ├── TouchInjector.java         # 长按/松开 手势注入
│   │       └── GreenBarAccessibilityService.java
│   ├── res/
│   │   ├── layout/{activity_main, activity_calibration}.xml
│   │   ├── values/{strings, styles}.xml
│   │   └── xml/accessibility_service_config.xml
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 常见问题

**Q: 点击没反应？**
A: 确认辅助功能服务已开启，且悬浮块已拖到正确位置

**Q: 检测不到绿条？**
A: 进入「实时校准」页面，观察蒙版图像，调整 Hue 范围直到绿条变白

**Q: 手机卡顿？**
A: 将识别间隔从 25ms 调到 50ms

**Q: 不同游戏需要不同参数？**
A: 使用场景1/2/3 分别保存，切换场景即切换参数组

---

## 依赖版本

- OpenCV Android: `com.quickbirdstudios:opencv:4.5.3.0`
- AndroidX AppCompat: `1.6.1`
- Material: `1.11.0`
- Gradle: `8.4`
- Android Gradle Plugin: `8.2.2`
