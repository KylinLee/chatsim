# 发布签名 / Release Signing

ChatS IM 的所有正式发行 APK 都使用同一把发布密钥签名。安装前请核对手中 APK 的签名证书指纹，确认与下方一致，以防被二次打包。

## 发布证书指纹

| 算法 | 指纹 |
| --- | --- |
| SHA-256 | `4D:D3:DC:FD:55:EA:6A:95:B1:B9:73:60:88:81:76:9F:2A:09:26:25:BB:2F:6B:8A:89:DC:B0:FB:DB:EF:A5:C8` |
| SHA-1 | `C0:84:DB:2A:EE:2B:64:1B:76:6D:FE:5A:DD:3E:65:BA:BD:8A:53:1D` |

- 证书主体（Owner）：`CN=ChatS IM, OU=Release, O=ChatS IM, C=CN`
- 有效期：2026-09-28 至 2054-02-13

## 校验方法

使用 Android SDK 的 `apksigner` 打印 APK 签名证书：

```bash
apksigner verify --print-certs chatsim-core-<version>.apk
```

输出中的 `Signer #1 certificate SHA-256 digest` 应与上表 SHA-256 一致。

若使用 `keytool` 直接查看密钥库：

```bash
keytool -list -v -keystore keystore.jks -alias "<keyAlias>"
```

## CI 发布密钥

GitHub Actions 的发布流程（`.github/workflows/release.yml`）从仓库 Secrets 读取签名材料，不落盘到版本库：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | `keystore.jks` 的 Base64 编码（`base64 -w0 keystore.jks`） |
| `KEYSTORE_PASSWORD` | 密钥库口令（`storePassword`） |
| `KEY_ALIAS` | 密钥别名（`keyAlias`） |
| `KEY_PASSWORD` | 密钥口令（`keyPassword`） |

`keystore.jks` 与 `keystore.properties` 均已在 `.gitignore` 中忽略，切勿提交到仓库。
