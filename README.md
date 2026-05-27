# Notion Tasker 📝📱

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-1.5.4-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Notion](https://img.shields.io/badge/Notion_API-Integrated-000000?style=for-the-badge&logo=notion&logoColor=white)
![Google AI Studio](https://img.shields.io/badge/Google_AI_Studio-Co--Created-F4B400?style=for-the-badge&logo=google&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

<p align="center">
  <strong>Notionのデータベースと双方向同期する、洗練されたモダンなAndroidタスク管理アプリ</strong>
  <br />
  オフライン対応キャッシュや、ゲーム感覚で進められる実績トラッカーを搭載した高性能なタスクマネージャーです。
</p>

[📌 GitHub Repository: Notion-Tasker](https://github.com/tsubuanpn/Notion-Tasker)

</div>

---

## 📖 プロジェクトの概要

**Notion Tasker** は、Notionのデータベース内のタスクテーブルをAndroidデバイスからシームレスに操作・更新できるネイティブタスク管理アプリケーションです。

Jetpack Compose + MVVMによる現代的なAndroid設計パターンに加え、高速で堅牢なRoomによるローカルデータベースキャッシュを駆使することで、**オフライン状態でもタスクの閲覧や編集**が可能です。オンラインに復帰した際には、変更内容が自動調整されNotion APIとスムーズに双方向同期します。

---

## ✨ 主な機能

1. **🚀 状態が一目でわかる美しいタスクカード**
   * **未着手 (Unstarted)** : 純粋な白色 (`#FFFFFF`) のシンプルでミニマルなカード。枠線には視認性を調整した程よい濃さのグレー (`#CCCCCC`) を採用。
   * **進行中 (In Progress)** : 透き通る爽やかなブルーのテーマカード。
   * **完了 (Completed)** : 優しいミントグリーンのチェックカード。

2. **📱 5つの多機能ナビゲーション画面**
   * 🏠 **Home (ホーム)** : 「未着手」「進行中」のタスクをステータスごとに整理し、1タップで状況のサイクル（未着手 ➔ 進行中 ➔ 完了）切り替えが可能。
   * 📂 **Category (カテゴリ)** : 課題、学習、作業、趣味、その他などのカテゴリ別にタスクを集約。
   * 📅 **Calendar (カレンダー)** : 期限日 (Due Date) や実施予定日 (Scheduled Date) をベースに、使いやすい月間カレンダー型ビューで工程を把握。
   * 🏆 **Achievements (実績 & 統計)** : 完了済みの累計数、今週・今月のタスク達成率、連続作成/完了日数 (Streak) などのインサイトを美しく可視化。
   * ⚙️ **Settings (設定)** : NotionのAPIキー、データベースID、プロパティ名マッピングやステータス選択肢のローカルカスタマイズ。

3. **🔁 Notion APIとの双方向同期システム**
   * オンライン時は即時Notionをアップデート。オフライン時には変更をRoomDBで安全に保持し、デバイス再接続時にシームレスに双方向パッチ（PATCH）同期を実行。

---

## 🛠️ 使用している主な技術 (Tech Stack)

| カテゴリ | 技術名 / ライブラリ | 概要 |
| :--- | :--- | :--- |
| **言語 / ランタイム** | Kotlin | 最新のモダンで表現豊かなAndroid開発言語。 |
| **Android UI** | Jetpack Compose (Material Design 3) | 完全に宣言的で美しいピクセル微調整レイアウト。 |
| **アーキテクチャ** | MVVM, LiveData, StateFlow | データの明瞭な流れを保証するJetpack標準構成。 |
| **データベース** | Jetpack Room | ローカルオフラインデータをデバイス上で安全にクエリ・高速キャッシュ。 |
| **非同期制御** | Kotlin Coroutines & Flow | コルーチンとFlowを用いた軽量かつリアクティブな非同期制御。 |
| **ネットワーク** | Ktor Client / Retrofit | Notion REST APIとのシームレスな同期・マッピング通信。 |

---

## 📂 ディレクトリ構成

プロジェクトは純粋なAndroid Kotlinアプリプロジェクトとして洗練されています。

```text
Notion-Tasker/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/notiontasks/app/
│   │   │       ├── data/            # データ階層 (Repository, Model, Room LocalDB, Entity)
│   │   │       ├── ui/theme/        # 統一カラー・テーマ、ダークテーマ設計 (Theme.kt)
│   │   │       └── MainActivity.kt  # Composeによる5つの画面と動的コンポーネント
│   └── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 インストール & セットアップ手順

### 📲 クイックインストール (推奨)
手軽にアプリをお手元のスマートフォンで体験したい場合は、開発環境を構築する必要はありません。
1. お使いのAndroid端末から [Releases](https://github.com/tsubuanpn/Notion-Tasker/releases) にアクセスします。
2. 最新リリースの `Assets` から `.apk` ファイルをダウンロードします。
3. ダウンロードしたAPKファイルを実行し、端末にインストールします（※提供元不明のアプリのインストール許可を求められた場合は、ブラウザやファイル管理者アプリに対して許可を与えてください）。

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

アプリを使用するためには、Notion APIのアクセストークンと、タスク管理用のデータベースと、そのIDが必要です。

1. **Notionインテグレーションの作成:**
   * [Notion開発者向けハブ](https://www.notion.so/my-integrations) へアクセスし、「新規コネクト」を押します。
   * 以下の通りに設定して、コネクトを作成をクリック
     * コネクト名：任意のコネクト名（NotionTasker 等）
     * 認証方法　：アクセストークン
     * インストール可能なワークスペース：タスク管理用のデータベースがあるワークスペース
   * アクセストークン（APIキー）をコピーしておきます。

2. **データベースへのアクセス権付与:**
   * コンテンツへのアクセスをクリック、アクセス権限を編集をクリック。
   * 検索欄に、タスク管理用のDB名を入力し、該当のDBをクリック。
   * 保存をクリック
   
3. **データベースID（DBのID）の取得:**
   * ブラウザで対象のNotionデータベースをフルページ等で開きます。
   * ブラウザのURLを確認します。URLは通常以下のいずれかの形式になっています：
     * `https://www.notion.so/ワークスペース名/【32文字の英数字のデータベースID】?v=...`
     * `https://www.notion.so/【32文字の英数字のデータベースID】?v=...`
   * この `?v=` の直前にある32文字の英数字部分がデータベースIDです。この文字列をコピー。

4. **プロパティ情報の確認:**
   * 以下のプロパティがデータベースに存在していることを確認します。
     - **名前** (Title) ➔ タイトル形式
     - **状態** (Status) ➔ ステータス、またはセレクト形式（「未着手」「進行中」「完了」の3つのオプション）
     - **カテゴリ** (Category/Select) ➔ セレクト形式（「課題」「学習」「作業」「趣味」等）
     - **期限日** (Due) ➔ 日付形式
     - **予定日** (Scheduled) ➔ 日付形式
   * ※プロパティの名称がお手元のデータベースと異なる場合は、Androidアプリの **Settings (設定)** 画面からマッピング名（例：「Status」➔「状態」など）を自由に上書きして適用できます。

5. **アプリへの登録:**
   * アプリの設定画面を開きます。
   * Notion Integration ID（一番上の入力欄）に、1でコピーしたアクセストークンを入力。
   * Database ID（二番目の入力欄）に、2でコピーしたデータベースのIDを入力します。
   * データベース構造を自動取得をクリックし、その下のデータベースプロパーティマッピングを行う。
   * 設定を保存を押す。
   * ※設定を保存を押した後、自動でホームタブに移動しなかった場合、本手順の上２つ、Notion Integration IDとDatabase IDの入力を行い、設定を保存を押してください。

---

## 🤖 Google AI Studioの活用について

本プロジェクトの開発において、実装構成の相談、コーディング補助やUIの微調整、エラー等が発生した際のトラブルシューティングなど、主に**コーディング補助や実装のアイデア出し**のために **Google AI Studio (Gemini 3.5)** を一部利用しています。

---

## 📝 ライセンス

このパッケージは [MIT License](LICENSE) の元で提供されています。

---

## 👤 コントリビューター / 開発者
* **tsubuanpn** - [GitHub Profile](https://github.com/tsubuanpn)


