# ローカル Codex に渡すプロンプト

以下をそのままローカル Codex に渡す。

```text
Notion-Tasker の実機検証と、実機で再現した安全な修正を引き継いでください。

最初に docs/audits/08-local-device-handover.md、docs/audits/07-progress.md、docs/audits/07-codex-progress.md、docs/audits/06-open-questions.md を読み、現在のソースと照合してください。監査資料は計画資料であり、未解決事項を推測で製品仕様にしないでください。

リポジトリは origin/work を基準にします。開始時に git fetch origin、git switch work、git pull --ff-only origin work、git status --short を実行し、既存の変更を破棄しないでください。まず ./gradlew testDebugUnitTest lintDebug assembleDebug を実行してください。

USB デバッグまたは VPN 内のネットワーク ADB で実機を接続し、引継ぎ文書の T6-038、T6-046/T6-047、T6-048/T6-049 を検証してください。続いて T6-050〜052 の実機検証を実施してください。問題がなければ、変更せず検証結果、端末、OS、画面条件を docs/audits/07-progress.md と docs/audits/07-codex-progress.md に記録してください。

問題を再現した場合だけ、影響範囲を調査して必要最小限に修正してください。修正ごとに関連テスト、testDebugUnitTest、lintDebug、assembleDebug、差分確認を行い、タスク単位で commit してください。実機検証した事実と、未検証の事項を明確に区別してください。

本番 Notion API への書込み、実データ削除、destructive migration、秘密情報の出力・commit、main への直接 push・マージはしないでください。T6-003/004 と、06-open-questions.md の未決事項に依存するタスク、明示承認が必要な任意拡張は実装しないでください。

各タスクの完了・保留・失敗、変更ファイル、テスト結果、端末/OS、残る確認事項を進捗文書へ残し、最後に実施内容と次の安全な候補を報告してください。
```
