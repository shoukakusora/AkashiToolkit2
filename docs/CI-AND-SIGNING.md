## 构建与发布（CI）

两个 GitHub Actions 工作流：

| 工作流 | 触发 | 产物 |
|---|---|---|
| `build` | push 到 `main` / PR / 手动 | `debug-apk`（未签名调试包，作为 artifact） |
| `release` | 推送 `v*` 标签 / 手动 | 签名后的 release APK，并自动创建 GitHub Release |

### 用你自己的 JKS keystore 打正式包

**方式一：本地构建**

1. 生成密钥库（若还没有）：
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias akashi \
           -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 复制签名模板：`cp signing.gradle.sample signing.gradle`
3. 设置环境变量后构建：
   ```bash
   export AKASHI_KEYSTORE="$PWD/release.jks"
   export AKASHI_KEYSTORE_PASSWORD='你的口令'
   export AKASHI_KEY_ALIAS='akashi'
   export AKASHI_KEY_PASSWORD='你的口令'
   ./gradlew assembleRelease -PnoVersionBump
   ```
   产物：`app/build/outputs/apk/release/akashi-toolkit-<versionCode>.apk`
   > 不加 `-PnoVersionBump` 时，构建成功会自动把 `version.properties` 的版本号 +1（见「版本号规则」）。

**方式二：交给 GitHub Actions（无需本地 keystore）**

在仓库 `Settings → Secrets and variables → Actions` 添加：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 口令 |

然后推送标签即可自动出包并发布：

```bash
git tag v1.3.2
git push origin v1.3.2
```

> 注意：签名密钥必须长期保持一致，否则无法覆盖安装旧版本。
