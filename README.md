# 開発者向けオプション / developerOptions

## 概要

Android で開発者向けオプションを楽に開けるほか、クイック設定タイルから次を切り替えられるアプリです。

- USB デバッグ
- プライベート DNS
- 仮の現在地情報アプリ（有効 / 無効）

## 権限まわり

### USB デバッグ / プライベート DNS

`WRITE_SECURE_SETTINGS` が必要です。次のいずれかで付与できます。

#### 方法1: Shizuku（推奨・PC 不要）

1. [Shizuku](https://shizuku.rikka.app/download/) をインストールして起動する（ワイヤレスデバッグまたは root）
2. 本アプリの権限ヘルプ（またはアプリの設定）で **「Shizuku で権限を付与」** をタップする
3. 付与後は Shizuku を止めてもこれらのタイルは利用可能

#### 方法2: ADB（PC）

```shell
adb shell pm grant net.ogatomo.developerOptions android.permission.WRITE_SECURE_SETTINGS
```

### 仮の現在地情報アプリ

開発者向けオプションの「仮の現在地情報アプリ」と同じく AppOps を操作します。

1. **アプリの設定**（システム設定 → アプリ → developerOptions → アプリ内の設定）で候補からアプリを選択
2. 「有効化」またはクイック設定タイルで ON/OFF
3. **切り替えのたびに Shizuku の起動が必要**（権限を一度付与すれば済む USB デバッグとは異なります）

## アプリの設定を開く

システム **アプリ情報** 画面の **アプリ内の設定** から、権限状態・仮の現在地の選択などを開けます。

## 対応 OS

Android 7.0 (API 24) 以降  
（プライベート DNS タイルは Android 9 以降）

## 対応言語

日本語, English

## リリース（GitHub Actions）

`v*` タグを push すると release APK をビルドし、GitHub Releases にアップロードします（[KaraPlay](https://github.com/ogatomo21/KaraPlay) と同様）。

```shell
# 例
git tag v1.3.0
git push origin v1.3.0
```

### 必要な Secrets

リポジトリの Settings → Secrets and variables → Actions に以下を登録してください。

| Secret | 内容 |
|--------|------|
| `KEYSTORE_BASE64` | リリース用 keystore を base64 したもの |
| `KEYSTORE_PASSWORD` | keystore パスワード |
| `KEY_ALIAS` | キーエイリアス |
| `KEY_PASSWORD` | キーパスワード |

keystore の base64 化（例）:

```shell
# Linux / macOS / Git Bash
base64 -w0 release.jks > keystore.b64

# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

### ローカルで署名付き release をビルドする場合

`local.properties`（gitignore 済み）に次を追加します。

```properties
storeFile=path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## ライセンス

MIT LICENSE
