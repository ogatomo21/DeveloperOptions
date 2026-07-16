# AGENTS.md — developerOptions

このファイルは、コーディングエージェントが本リポジトリで作業する際のプロジェクト構成・方針の参照用ドキュメントです。

---

## 概要

| 項目 | 内容 |
|------|------|
| アプリ名 | developerOptions |
| パッケージ / applicationId | `net.ogatomo.developerOptions` |
| 目的 | 開発者向けオプションを素早く開く + QS で USB デバッグ / Private DNS / 仮の現在地を切替 |
| 言語 | Kotlin |
| UI | 従来の View 系 (AppCompatActivity)。Compose 未使用 |
| ライセンス | MIT |
| 対応 OS | Android 7.0 (API 24) 以降（Shizuku API / TileService） |
| 対応言語 | 英語 (default) / 日本語 (`values-ja`) |
| バージョン | versionCode `4` / versionName `1.3` |

### 主要機能

1. **ランチャー起動** (`MainActivity`)  
   開発者向けオプションが有効ならその画面へ、無効なら端末情報画面へ遷移して終了。Android 13+ では通知権限の説明ダイアログを挟む場合あり。
2. **クイック設定タイル — USB デバッグ** (`AdbToggleTileService`)  
   `Settings.Global.ADB_ENABLED` をトグル。ラベル固定「USB デバッグ」、字幕 ON/OFF。`WRITE_SECURE_SETTINGS` 不足時は通知。
3. **クイック設定タイル — プライベート DNS** (`PrivateDnsTileService`)  
   `private_dns_mode` / `private_dns_specifier` をトグル（API 28+）。ラベル固定、OFF 時字幕 OFF、ON 時はモード/ホスト。
4. **クイック設定タイル — 仮の現在地** (`MockLocationTileService`)  
   AppOps `android:mock_location` を allow/deny。**操作のたび Shizuku 必須**。対象未選択時は `AppSettingsActivity` を開く。
5. **アプリ設定** (`AppSettingsActivity` + `APPLICATION_PREFERENCES`)  
   システム「アプリ情報」→「アプリ内の設定」。権限導線 + 仮の現在地アプリ選択。
6. **権限ヘルプ** (`WriteSecureSettingsHelpActivity`)  
   Shizuku で `WRITE_SECURE_SETTINGS` を grant、または ADB コマンド。

権限付与（ユーザー向け）:

1. Shizuku 起動後、ヘルプ画面の「Shizuku で権限を付与」（推奨）→ USB/DNS タイルは以後 Shizuku 不要
2. 仮の現在地は **毎回 Shizuku 稼働が必要**（AppOps）
3. または PC から:

```shell
adb shell pm grant net.ogatomo.developerOptions android.permission.WRITE_SECURE_SETTINGS
```
---

## ディレクトリ構成

```
developerOptions/
├── AGENTS.md                 # 本ファイル（エージェント向け）
├── README.md                 # ユーザー向け概要
├── LICENSE
├── settings.gradle.kts       # ルートプロジェクト名 / :app モジュール
├── build.gradle.kts          # ルート（プラグイン宣言のみ apply false）
├── gradle.properties
├── gradlew / gradlew.bat
├── local.properties          # SDK パス（gitignore・コミット不可）
├── gradle/
│   ├── libs.versions.toml    # Version Catalog（依存・プラグイン版）
│   ├── gradle-daemon-jvm.properties
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/                      # 唯一のアプリモジュール
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/net/ogatomo/developerOptions/
        │   ├── MainActivity.kt
        │   ├── AdbToggleTileService  ← ファイル名: DeveloperOptionTileService.kt
        │   ├── PrivateDnsTileService.kt
        │   ├── MockLocationTileService.kt
        │   ├── OpenDeveloperOptionService.kt
        │   ├── WriteSecureSettingsHelpActivity.kt
        │   ├── AppSettingsActivity.kt
        │   └── permission/
        │       ├── SecureSettingsPermission.kt
        │       ├── ShizukuGrantHelper.kt
        │       ├── ShizukuAvailability.kt
        │       └── MockLocationOps.kt
        └── res/
            ├── layout/           # write_secure_help / app_settings
            ├── drawable/         # ADB / Private DNS / Mock location アイコン
            ├── drawable-v24/
            ├── mipmap-*/
            ├── mipmap-anydpi-v26/
            ├── values/
            └── values-ja/
```

### 生成物・無視対象（編集しない）

- `build/`, `app/build/`, `app/release/` — ビルド成果物
- `.gradle/`, `.idea/`, `.kotlin/` — ローカル IDE / ツール状態
- `local.properties` — マシン固有 SDK パス
- `*.apk`, `*.aab`

---

## ソースコンポーネント

パッケージ: `net.ogatomo.developerOptions`

| クラス | ファイル | 種別 | 役割 |
|--------|----------|------|------|
| `MainActivity` | `MainActivity.kt` | Activity (LAUNCHER) | 通知権限確認 → 開発者向けオプション or 端末情報を開いて finish |
| `AdbToggleTileService` | `DeveloperOptionTileService.kt` | `TileService` | USB デバッグ ON/OFF |
| `PrivateDnsTileService` | `PrivateDnsTileService.kt` | `TileService` | プライベート DNS ON/OFF |
| `MockLocationTileService` | `MockLocationTileService.kt` | `TileService` | 仮の現在地 allow/deny（Shizuku 必須） |
| `OpenDeveloperOptionService` | `OpenDeveloperOptionService.kt` | Service | 開発者向けオプション補助（exported=false） |
| `WriteSecureSettingsHelpActivity` | `WriteSecureSettingsHelpActivity.kt` | Activity | Shizuku / ADB 権限付与 |
| `AppSettingsActivity` | `AppSettingsActivity.kt` | Activity | アプリ情報からの設定・仮の現在地選択 |
| `SecureSettingsPermission` | `permission/SecureSettingsPermission.kt` | util | `WRITE_SECURE_SETTINGS` 判定 |
| `ShizukuGrantHelper` | `permission/ShizukuGrantHelper.kt` | util | WRITE_SECURE grant |
| `ShizukuAvailability` | `permission/ShizukuAvailability.kt` | util | Shizuku READY 判定（AppOps 用） |
| `MockLocationOps` | `permission/MockLocationOps.kt` | util | appops コマンド経由で mock_location 読み書き |
| `ShellUserService` | `shizuku/ShellUserService.kt` | UserService | Shizuku shell プロセスでコマンド実行（IAppOpsService 不使用） |
| `ShellUserServiceClient` | `shizuku/ShellUserServiceClient.kt` | util | UserService bind / exec |

### マニフェスト上の注意

- QS タイルの `android:name` は **`AdbToggleTileService`**（クラス名）。ソースファイル名は `DeveloperOptionTileService.kt` のままなので、リネーム時は両方を揃えること。
- `AppSettingsActivity` は `android.intent.action.APPLICATION_PREFERENCES` を公開（アプリ情報 → アプリ内の設定）。
- `ShizukuProvider` を application 内に登録。authority は `${applicationId}.shizuku`。
- 権限:
  - `WRITE_SECURE_SETTINGS`（protected / Shizuku または ADB で grant）
  - `POST_NOTIFICATIONS`（Android 13+ でタイル失敗時の通知用）
  - `QUERY_ALL_PACKAGES`（仮の現在地候補の列挙）

### リソース

| パス | 用途 |
|------|------|
| `res/layout/activity_write_secure_help.xml` | 権限ヘルプ画面 |
| `res/layout/activity_app_settings.xml` | アプリ設定・仮の現在地選択 |
| `res/drawable/ic_adb_icon.xml` | USB デバッグ QS / 通知 |
| `res/drawable/ic_private_dns_icon.xml` | Private DNS QS |
| `res/drawable/ic_mock_location_icon.xml` | 仮の現在地 QS |
| `res/values/strings.xml` | 英語（default） |
| `res/values-ja/strings.xml` | 日本語 |
| `res/values/colors.xml`, `style.xml` | テーマ・色 |

文字列を追加・変更する場合は **英語 default と `values-ja` の両方** を更新する。

---

## ビルド・依存関係

| 項目 | 値 |
|------|-----|
| Gradle | 9.3.1 (wrapper) |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 |
| compileSdk / targetSdk | 37（Android 17） |
| minSdk | 24 |
| JVM target | 1.8 |
| Version Catalog | `gradle/libs.versions.toml` |

### 実行時依存（`app/build.gradle.kts` で実使用）

- `androidx.annotation:annotation`
- `androidx.core:core`
- `androidx.appcompat:appcompat`
- `dev.rikka.shizuku:api` / `provider`（13.1.5）
- `org.lsposed.hiddenapibypass:hiddenapibypass`（IPackageManager 反射用）
- `coreLibraryDesugaring` + `desugar_jdk_libs`（Shizuku 13.1+ / minSdk 23 要件）
`libs.versions.toml` には material / junit / espresso 等の未使用エントリも残っている。新規依存は Catalog 経由で追加し、未使用定義の整理は意図的なクリーンアップ時のみ行う。

### ビルドタイプ

- **release**: minify + shrinkResources 有効。ProGuard は `proguard-android-optimize.txt` + `app/proguard-rules.pro`。

### よく使うコマンド

```shell
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease

# Unix
./gradlew assembleDebug
./gradlew assembleRelease
```

### リリース（GHA）

- ワークフロー: `.github/workflows/release.yaml`
- トリガー: `v*` タグ push（例: `v1.3.0`）
- 処理: `assembleRelease` → APK リネーム → GitHub Release に添付
- 署名: Secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
- ローカル署名: `local.properties` の `storeFile` / `storePassword` / `keyAlias` / `keyPassword`
- 参考: [KaraPlay release.yaml](https://github.com/ogatomo21/KaraPlay/blob/master/.github/workflows/release.yaml)

---

## アーキテクチャ方針

- **単一モジュール** (`:app` のみ)。ライブラリ分割は現状なし。
- **薄いアプリ**: 画面遷移 + Settings Global の読み書きが中心。Repository / ViewModel / DI は未導入。
- **Compose なし**。変更時も既存の AppCompat / View スタイルに合わせる。
- 設定の読み書き:
  - 開発者向けオプション有効: `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`
  - ADB: `Settings.Global.ADB_ENABLED`
  - プライベート DNS: `private_dns_mode` / `private_dns_specifier`（@hide・文字列リテラル）
  - 仮の現在地: AppOps `android:mock_location`（IAppOpsService via Shizuku）。選択パッケージは SharedPreferences
- 失敗時 UX: Toast / 通知 / ヘルプ Activity。ネットワークや永続 DB はなし。

---

## エージェント向け作業ルール

1. **スコープを狭く保つ**  
   依頼された変更に関係ないリファクタ・未使用 Catalog 整理・フォーマット一括変更はしない。

2. **ファイル名とクラス名の不一致に注意**  
   `DeveloperOptionTileService.kt` ↔ `AdbToggleTileService`。マニフェスト・参照を壊さない。

3. **文字列・i18n**  
   ユーザー向け文言は `values` と `values-ja` を同期。コードにハードコードした日本語（例: 通知アクション文言）がある場合は、可能なら string リソース化を検討。

4. **権限・セキュリティ**  
   `WRITE_SECURE_SETTINGS` は通常のランタイム権限ダイアログでは付与できない。  
   **案 A**: Shizuku で一度 `pm grant` 相当（IPackageManager）→ 以後は通常 API。ADB コマンドはフォールバック。  
   タイル操作のたびに Shizuku を必須にしない。

5. **API レベル**  
   minSdk 24。`TileService` / 通知チャネルは O+、`POST_NOTIFICATIONS` は T+、Private DNS は P+ を既存パターンに合わせる。

6. **コミット対象外**  
   `local.properties`、`build/`、`app/release/`、`.idea/` 等をコミットしない。

7. **テスト**  
   現状 unit / instrumented テストソースは未整備。追加する場合は `app/src/test` / `app/src/androidTest` に置く。

---

## 変更時の確認ポイント

- [ ] 開発者向けオプション ON/OFF 時の遷移先（設定画面 vs 端末情報）
- [ ] QS タイル: ADB トグル成功時の `Tile.STATE_*` / ラベル更新
- [ ] QS タイル: プライベート DNS の OFF→前回モード復元、ON→OFF、subtitle（API 29+）
- [ ] QS タイル: 仮の現在地 — 未選択で設定へ、Shizuku 必須、字幕にアプリ名 or OFF
- [ ] アプリ情報 → アプリ内の設定 → `AppSettingsActivity`
- [ ] 権限なし時: 通知 → `WriteSecureSettingsHelpActivity`
- [ ] Shizuku: 未導入 / 未起動 / 未許可 / READY / 付与済みの各 UI、grant 成功後 USB/DNS タイルが動くこと
- [ ] Android 13+: 通知権限ダイアログ後も開発者向けオプション起動が続行されること
- [ ] 英語 / 日本語の文字列欠落がないこと
- [ ] release ビルドで R8 による壊れがないこと（特に TileService / リフレクションなし想定）
