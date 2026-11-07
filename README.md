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

Jetpack Compose + MVVM アーキテクチャに基づき、Kotlin Coroutines & Flow (`StateFlow`) を活用したリアクティブな状態管理を実現。高速かつ堅牢な Jetpack Room によるローカルデータベースキャッシュに加え、**オフライン操作をオンライン復帰時に自動反映する同期キュー (`PendingSyncAction`)** を搭載しています。

---

## ✨ 主な機能

1. **🚀 状態が一目でわかるモダンなタスクカード**
   * **未着手 (Unstarted)** : 純粋な白色 (`#FFFFFF`) のシンプルで視認性の高いミニマルカード。
   * **進行中 (In Progress)** : 集中力を高める爽やかなブルーのテーマカード。
   * **完了 (Completed)** : 達成感のある優しいミントグリーンのチェックカード。

2. **📱 7つの多機能画面**
   * 🏠 **Today (今日)** : 直近のタスクを整理して一覧表示。1タップで状態をサイクル切り替え可能。
   * 📂 **Tasks (タスク)** : 課題、学習、作業、趣味などのカテゴリ別にタスクを集約。
   * 📅 **Plan (計画)** : 期限日や予定日に基づく月間カレンダービュー。日付ごとのタスク確認が容易です。
   * 🕒 **Schedule (時間割)** : 毎日のルーティンやタイムブロックを管理するタイムブロッキング機能。**テキスト形式でのスケジュールエクスポート機能**も搭載。
   * 🍅 **Pomodoro (集中)** : フォアグラウンドサービスによるバックグラウンド実行に対応したタイマー。タスクとの紐付けやプッシュ通知機能を完備。
   * 🏆 **Achievements (実績)** : 累計完了数、達成率、ストリーク、ポモドーロ統計をグラフで可視化。
   * ⚙️ **Settings (設定)** : Notion連携、通知スケジュール、外観テーマ、表示タブ、生活習慣マスタ管理など多岐にわたるカスタマイズが可能。

3. **🔁 高度な同期システムとオフライン対応**
   * **双方向同期**: オンライン時は即座にNotionへ反映。
   * **同期キュー**: オフライン時の編集はローカルに保存され、オンライン復帰時に自動でリトライ・同期されます。
   * **データベース自動解析**: Notionデータベースのプロパティ構造を自動取得し、アプリ側の項目と柔軟にマッピングできます。

4. **🔒 セキュリティとデータ管理**
   * **暗号化保存**: Notion APIトークンなどの機密情報は `EncryptedSharedPreferences` (Android Jetpack Security) を用いて端末内に安全に暗号化保存されます。
   * **データクリーンアップ**: 古い完了済みタスクの自動削除や、統計データの管理機能を搭載。

---

## 🛠️ 使用している主な技術 (Tech Stack)

| カテゴリ | 技術名 / ライブラリ | 概要 |
| :--- | :--- | :--- |
| **UI Framework** | Jetpack Compose (Material 3) | モダンな宣言型UIとダイナミックカラー対応。 |
| **Architecture** | MVVM / Repository パターン | `StateFlow` と Coroutines によるリアクティブな設計。 |
| **Local DB** | Jetpack Room | 複数テーブル（タスク、ログ、ブロック等）とマイグレーション対応。 |
| **Security** | Jetpack Security (Crypto) | 認証情報の AES256 方式による暗号化。 |
| **Sync** | Retrofit / OkHttp / Coroutines | Notion APIとの効率的な非同期通信とリトライ制御。 |
| **Background** | Foreground Service | タイマーの正確な動作と通知管理。 |

---

## 📂 ディレクトリ構成

```text
app/src/main/java/com/notiontasks/app/
├── data/
│   ├── local/       # Roomデータベース、DAO (Task, Pomodoro, Schedule, LifeActivity)
│   ├── model/       # データモデル (TaskModel, TimeBlock, LifeActivity等)
│   ├── remote/      # Notion API (Retrofit), DTO
│   └── repository/  # データソースの統合管理
├── ui/
│   ├── components/  # 再利用可能なUIコンポーネント (ダイアログ、カード)
│   ├── navigation/  # 画面遷移定義 (Screen.kt)
│   ├── screens/     # 各機能の画面実装 (Today, Plan, Pomodoro等)
│   ├── theme/       # カラー、テーマ定義
│   └── viewmodel/   # ビジネスロジックと状態管理 (TaskViewModel, SettingsViewModel)
├── utils/           # セキュリティ、エクスポート等のユーティリティ
├── MainActivity.kt  # エントリーポイント
└── PomodoroService.kt # バックグラウンドタイマーサービス
```

---
## 🚀 セットアップ & 連携

   アプリを Notion と連携させるために、アクセストークンおよびデータベースIDの設定を行います。

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

   4. **設定の保存:**
      * アプリ内の「設定 > Notion接続設定」にトークンとデータベースIDを入力してください。

   5. **マッピング:**
      * 「設定 > プロパティマッピング」で、Notionの各プロパティ名とアプリの機能を紐付けます（自動取得機能が利用可能です）。

---

## 🩺 同期・復旧の扱い

### 同期状態の見方

画面上部の同期表示は、現在の結果を区別して示します。

* **同期済**: ローカルの変更が Notion へ反映済みです。
* **同期中**: 通信中です。完了するまで画面を閉じずに待つ必要はありません。
* **同期保留**: 一時的な通信失敗などで、変更は端末内の同期キューに保持されています。接続が回復した後に同期を実行すると自動で再試行されます。
* **同期エラー**: 設定や権限など、利用者の確認が必要な失敗です。設定画面に表示される失敗項目ごとに、内容を確認して **再試行** または **破棄** を選びます。破棄は Notion へ未反映のその操作だけを取り除くため、内容を確認してから行ってください。

未確定のタスク追加にはアプリ用識別子を使うため、応答を受け取れなかった場合でも、同じタスクを重複して自動作成しないよう照合します。

### 暗号化された接続設定を読めない場合

起動時に「暗号化設定を読み込めません」と表示された場合は、まず **再試行** を選びます。解決しない場合に限り、画面で影響を確認したうえで **暗号化設定を再作成** を選んでください。この操作では Notion の接続情報、プロパティ対応、通知・外観などの設定を再入力する必要があります。一方で、端末内のタスク、予定、集中記録、保留操作は削除しません。

### 端末移行・バックアップと集中タイマー

Notion のトークンと接続マッピングは、クラウドバックアップおよび端末間転送の対象から除外されます。復元・移行後は、Notion 接続を確認してから同期してください。未確認の接続先へ、以前の保留操作を自動送信することはありません。

実行中または一時停止中の集中セッションは端末内に保存され、通常のアプリ／プロセス再起動後に復元されます。通知の **終了** 操作ではタイマーと画面表示を同時に停止します。Android の強制停止は OS がサービス再起動を禁止する操作なので、通常の終了・再起動とは別の扱いです。

日付・週の境界は日本時間（Asia/Tokyo）、週の開始は月曜日として扱います。

---

## 🤖 Google AI Studioの活用について

本プロジェクトは、主に**Gemini (Google AI Studio)** との対話を通じて、高度なアーキテクチャ設計、複雑な同期ロジックの実装、UI/UXの改善を行っています。

---

## 📝 ライセンス

[MIT License](LICENSE) の元で提供されています。

---

## 👤 開発者
* **tsubuanpn** - [GitHub Profile](https://github.com/tsubuanpn)
