# 開発者向けオプション / developerOptions

## 概要

Androidで開発者向けオプションを楽に開ける＋クイック設定タイルからADBをON/OFFできるアプリケーションです。

クイック設定タイルからADBのON/OFFを行う際は下記のADBコマンドをPCで実行して、`WRITE_SECURE_SETTINGS` 権限をこのアプリに付与してください。

```shell
adb shell pm grant net.ogatomo.developerOptions android.permission.WRITE_SECURE_SETTINGS
```

## 対応OS

Android 6 以降の端末

## 対応言語

日本語, English

## ライセンス

MIT LICENSE