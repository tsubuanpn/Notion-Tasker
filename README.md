# Notion Tasker 📝📱

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-1.5.4-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Notion](https://img.shields.io/badge/Notion_API-Integrated-000000?style=for-the-badge&logo=notion&logoColor=white)
![Google AI Studio](https://img.shields.io/badge/Google_AI_Studio-Co--Created-F4B400?style=for-the-badge&logo=google&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

<p align="center">
  <strong>Notionのデータベースと双方向同期する、洗練されたモダンな高機能Androidタスク＆時間管理アプリ</strong>
  <br />
  オフライン対応Roomキャッシュ、タイムブロッキング、バックグラウンド動作対応ポモドーロタイマー、ゲーム感覚の実績トラッカーを統合したオールインワン・プロダクティビティツールです。
</p>

[📌 GitHub Repository: Notion-Tasker](https://github.com/tsubuanpn/Notion-Tasker)

</div>

---

## 📖 プロジェクトの概要

**Notion Tasker** は、Notionのデータベース内のタスクをAndroidデバイスから快適に閲覧・操作・管理できるだけでなく、日々の時間管理や集中セッションまで強力にサポートするネイティブタスク＆時間管理アプリケーションです。

Jetpack Compose + MVVM アーキテクチャに基づき、Kotlin Coroutines & Flow (`StateFlow`) を活用したリアクティブな状態管理を実現。高速かつ堅牢な Jetpack Room によるローカルデータベースキャッシュを駆使することで、**オフライン状態でもタスクやスケジュール、ポモドーロログの確認・編集**が可能です。オンライン復帰時にはNotion APIとスムーズに双方向同期し、データの一貫性を保ちます。

---

## ✨ 主な機能

1. **🚀 状態が一目でわかるモダンなタスクカード**
   * **未着手 (Unstarted)** : 純粋な白色 (`#FFFFFF`) のシンプルで視認性の高いミニマルカード。
   * **進行中 (In Progress)** : 集中力を高める爽やかなブルーのテーマカード。
   * **完了 (Completed)** : 達成感のある優しいミントグリーンのチェックカード。

2. **📱 6つの多機能ナビゲーション画面**
   * 🏠 **Home (ホーム)** : 「未着手」「進行中」のタスクを整理して一覧表示。1タップで状態をサイクル（未着手 ➔ 進行中 ➔ 完了）切り替えでき、スピーディーなタスク管理が可能。
   * 📂 **Category (カテゴリ)** : 課題、学習、作業、趣味などのカテゴリ別にタスクを集約し、目的別に整理・閲覧。
   * 📅 **Schedule / Calendar (スケジュール / カレンダー)** : 期限日や予定日に基づく月間カレンダー型ビューに加え、毎日のルーティン（LifeActivity）や時間帯ごとのタイムブロックを登録・管理するタイムブロッキング機能を搭載。
   * 🍅 **Pomodoro (ポモドーロタイマー)** : フォアグラウンドサービス (`PomodoroService`) による安定したバックグラウンド実行に対応する本格的ポモドーロタイマー。タスクとの紐付け機能、着信音選択、セッション完了時（集中完了・休憩終了）の正確なプッシュ通知・アラーム機能を完備。
   * 🏆 **Achievements (実績 & 統計)** : 完了済みのタスク累計数、週・月のタスク達成率、連続完了日数（Streak）、ポモドーロ集中セッションの実行履歴をグラフと分析値で可視化。
   * ⚙️ **Settings (設定)** : Notion連携資格情報・データベースID設定、データベース構造の自動取得＆プロパティマッピング機能、ステータス／カテゴリの選択肢管理、各画面タブの表示／非表示トグル、朝・夕の定時リマインダー通知設定。

3. **🔁 Notion APIとの双方向同期＆自動保存構成**
   * オンライン時は変更を即座にNotionへ反映。オフライン時はRoomローカルデータベースやSharedPreferencesに安全にキャッシュし、アプリ起動時や同期トリガー時に自動で認証情報とプロパティ構成を読み込み不整合を防止します。

---

## 🛠️ 使用している主な技術 (Tech Stack)

| カテゴリ | 技術名 / ライブラリ | 概要 |
| :--- | :--- | :--- |
| **言語 / ランタイム** | Kotlin | モダンで表現豊かな最新のAndroidネイティブ開発言語。 |
| **Android UI** | Jetpack Compose (Material Design 3) | 完全宣言的でピクセル微調整が施された洗練されたUIフレームワーク。 |
| **アーキテクチャ** | MVVM, Coroutines & Flow | `StateFlow` を用いた単一データソース（SSOT）と堅牢な状態制御。 |
| **ローカルデータベース** | Jetpack Room | タスク、ポモドーロログ、タイムブロック等の安全なローカル保存と高速クエリ。 |
| **バックグラウンド処理** | Android Service & NotificationManager | フォアグラウンドサービスによる確実なタイマー計測とプッシュ通知通知。 |
| **ネットワーク通信** | Ktor Client / Retrofit | Notion REST APIとの高速な通信及びJSONシリアライズ対応。 |

---

## 📂 ディレクトリ構成

プロジェクトは保守性と拡張性を重視したモダンなAndroidネイティブ構成を採用しています。

```text
Notion-Tasker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/notiontasks/app/
│   │   │       ├── data/
│   │   │       │   ├── local/       # Roomデータベース, Entity, Dao
│   │   │       │   ├── model/       # TaskModel, TimeBlock, LifeActivity等
│   │   │       │   ├── remote/      # Notion API通信 DTO, APIクライアント
│   │   │       │   └── repository/  # ローカルとリモートを統合するリポジトリ
│   │   │       ├── ui/
│   │   │       │   ├── components/  # 再利用可能なダイアログ・カードUI部品
│   │   │       │   ├── screens/     # Home, Category, Calendar, Pomodoro, Settings等の画面
│   │   │       │   ├── theme/       # 統一テーマ、カラースキーム、タイポグラフィ
│   │   │       │   └── viewmodel/   # TaskViewModel（状態管理＆ビジネスロジック）
│   │   │       ├── PomodoroService.kt          # バックグラウンドタイマーサービス
│   │   │       ├── TaskNotificationReceiver.kt # アラーム・通知制御ブロードキャストレシーバー
│   │   │       └── MainActivity.kt             # アプリケーションエントリーポイント
│   └── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 インストール & セットアップ手順

### 📲 クイックインストール (推奨)
手軽にアプリをお手元のスマートフォンで体験したい場合：
1. お使いのAndroid端末から [Releases](https://github.com/tsubuanpn/Notion-Tasker/releases) にアクセスします。
2. 最新リリースの `Assets` から `.apk` ファイルをダウンロードします。
3. ダウンロードしたAPKファイルを実行し、端末にインストールします（※提供元不明のアプリのインストール許可を求められた場合は、許可を与えてください）。

### 🛠️ ソースコードからのビルドと起動

**前提条件:**
* Android Studio (Hedgehog以降推奨)
* JDK 17
* Android SDK (API Level 34以降)

**手順:**
1. リポジトリをクローンします。
   ```bash
   git clone https://github.com/tsubuanpn/Notion-Tasker.git
   ```
2. Android Studioを開き、 `NotionTasker` ディレクトリを選択してプロジェクトをインポートします。
3. Gradleの同期（Sync）完了を待ちます。
4. エミュレータ、またはUSBデバッグを有効にした実機デバイスを用意します。
5. ビルド・実行します。(「Run」ボタン、または `./gradlew :app:installDebug` コマンド)

---

## 🔑 Notion連携の設定方法

アプリを Noton と連携させるために、アクセストークンおよびデータベースIDの設定を行います。

1. **Notionインテグレーションの作成:**
   * [Notion開発者向けハブ](https://www.notion.so/my-integrations) へアクセスし、「新規コネクト」をクリックします。
   * 以下の通りに設定してコネクトを作成します：
     * コネクト名：任意の名前（例：`NotionTasker`）
     * 認証方法：アクセストークン
     * インストール可能なワークスペース：タスク管理用のデータベースがあるワークスペース
   * 発行されたアクセストークン（APIキー）をコピーしておきます。

2. **データベースへのアクセス権付与:**
   * 対象のNotionデータベースを開き、ページ右上の「･･･」メニューから「コネクト」を選択して作成したインテグレーションを追加します。

3. **データベースIDの取得:**
   * ブラウザのURLから32文字の英数字部分（`?v=` の直前の文字列）をコピーします。

4. **プロパティ構成とマッピング:**
   * 基本的なプロパティ名（名前、状態、種類、予定日、締め切り等）が異なっている場合でも、アプリの **Settings (設定)** 画面にある「データベース構造を自動取得」ボタンを押すことで自動的にプロパティ一覧を解析し、柔軟にマッピング名を設定できます。

5. **設定の保存:**
   * アプリの設定画面にアクセストークンとデータベースIDを入力して「設定を保存」をタップすると、即座に同期が開始されます。

---

## 🤖 Google AI Studioの活用について

本プロジェクトの開発において、実装構成の相談、コーディング補助やUIの微調整、エラー等が発生した際のトラブルシューティングなど、主に**コーディング補助や実装のアイデア出し**のために **Google AI Studio (Gemini)** を利用しています。

---

## 📝 ライセンス

このパッケージは [MIT License](LICENSE) の元で提供されています。

---

## 👤 コントリビューター / 開発者
* **tsubuanpn** - [GitHub Profile](https://github.com/tsubuanpn)


