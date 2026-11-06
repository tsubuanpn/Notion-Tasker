# Codex Cloud 改善作業記録

最終更新: 2026-11-05 (UTC)

## 開始時点と検証基盤 (T6-001)

- 開始コミット: `e4f1465453bbee4c4907283b0313259a1cd22194`
- ブランチ: `work`
- 開始時の作業ツリー: clean (`git diff --check` も成功)
- Java: Temurin 21.0.13
- Gradle: 9.8.2
- Android SDK: Platform 37 / Build Tools 36.0.0, 37.0.0
- テスト隔離: JVM テストはモックを利用する。Android instrumentation の移行テストは専用 DB 名を使い、終了時に削除する。
- 実機・エミュレータ: 利用不可。instrumentation テストは未実行。

開始時の検証では `testDebugUnitTest lintDebug assembleDebug` が成功した。Notion API の本番書込みは行っていない。

## 実施済み

| タスク | 状態 | 作業内容 | 変更ファイル | 検証 |
| --- | --- | --- | --- | --- |
| T6-001 | 完了 | Git、JDK、Gradle、Android SDK、既存テストとテスト隔離を確認した。 | なし | `testDebugUnitTest lintDebug assembleDebug` 成功 |
| T6-016 | 実装済み・実機未検証 | 作成操作と同一タスクの保存操作を実行中キーで直列化し、保存中は送信ボタンを無効化する。 | `TaskViewModel.kt`, `MainScreen.kt`, `TaskComponents.kt`, `TaskViewModelTest.kt` | 重複作成防止テストを追加。全 JVM 43 件、Lint、debug ビルド成功 |
| T6-017 | 完了 | データソース解決済みの一時 HTTP 失敗時、代替プロパティで再 POST せず、元の作成要求を一度だけ保留キューへ記録する。 | `TaskRepository.kt`, `TaskRepositoryTest.kt` | 単一 POST・保留キュー検証を追加。全 JVM 43 件、Lint、debug ビルド成功 |
| T6-021 | 実装済み・実機未検証 | 検索欄を閉じた後も検索語とフィルターを表示し、個別／一括で解除できるようにした。 | `TasksScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |
| T6-027 | 実装済み・実機未検証 | 追加・編集の予定日と締切日の解除を可能にし、picker を現在値（未設定時は当日）で開く。 | `TaskComponents.kt`, `TaskComponentsTest.kt` | picker 初期日テストを追加。全 JVM 43 件、Lint、debug ビルド成功 |
| T6-028 | 実装済み・実機未検証 | タスク/カレンダーの読込中、DB エラー、データなし、フィルター結果なしを別の表示にした。DB エラー時の同期誘導は追加していない。 | `TasksScreen.kt`, `PlanOverviewScreen.kt` | 既存の状態遷移 JVM テストを確認。全 JVM 43 件、Lint、debug ビルド成功 |
| T6-029 | 実装済み・実機未検証 | タスクが未取得または取得エラーでも、振り返り画面から集中記録を開けるようにした。 | `AchievementsScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |
| T6-034 | 実装済み・実機未検証 | 生活活動プリセットの編集時に既存 `sortOrder` を保持する。新規追加の末尾追加規則は維持する。 | `SettingsScreen.kt` | 既存の順序保存テストを確認。全 JVM 43 件、Lint、debug ビルド成功 |
| T6-035 | 実装済み・実機未検証 | 24:00 を終了時刻として保持し、短縮操作や開始時刻の自動調整で 0:00 へ折り返さないようにした。翌日跨ぎは追加していない。 | `ScheduleScreen.kt`, `SettingsScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |
| T6-036 | 実装済み・実機未検証 | 時間ブロックの削除前に対象名・日時・削除範囲を示す確認を追加した。 | `ScheduleScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |
| T6-042 | 実装済み・実機未検証 | 振り返りの集計が予定日・締切日と現在状態に基づき、実完了日時の履歴ではないことを表示した。 | `AchievementsScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |
| T6-055 | 実装済み・実機未検証 | 完了メッセージの誤字を修正し、要対応状態を完了チェックではなく警告アイコンで示す。 | `PomodoroScreen.kt`, `AchievementsScreen.kt` | 全 JVM 43 件、Lint、debug ビルド成功 |

## 保留

| タスク | 理由 | 依存・次の条件 |
| --- | --- | --- |
| T6-038 | 通知の再開動作は Android サービスと通知操作の検証を要する。現環境には実機・エミュレータがない。 | 実機またはエミュレータでの通知アクション検証後に実施 |
| T6-043 | 画面文言以外の初期設定仕様を決める根拠がない。 | Notion 初期設定の案内内容・導線を製品として確認後に実施 |
| T6-003/T6-004 | 旧 DB スキーマと移行条件が未確定で、データ互換性を安全に検証できない。 | Q6-18 の解決と旧 DB 実データでの移行検証 |

## 未解決事項と制約

- 添付の `06-open-questions.md` にある仕様未確定事項には判断を加えていない。
- Android instrumentation / 実機検証は未実施である。
- T6-016、T6-021、T6-027、T6-028、T6-029、T6-034、T6-035、T6-036、T6-042、T6-055 は画面操作を含むため、タスク定義の最終完了には実機またはエミュレータでの確認が必要である。
- 最終一括 `testDebugUnitTest lintDebug assembleDebug` は成功した。Gradle 10 互換性に関する既存の非推奨 API 警告が出力されたが、失敗や Lint エラーはない。

## 実機代替の確認

- Android Emulator 37.2.13 を導入して起動可否を確認後、検証不能と判断して削除した。
- `emulator -accel-check` は終了コード 3 で `KVM requires a CPU that supports vmx or svm` を返した。`/dev/kvm` も利用できない。
- API 35 Google APIs system image の取得を試みたが、完了前に停止した。未完了データも削除済み。KVM 非対応のソフトウェアエミュレーションは、この環境で回帰試験に使える性能・安定性を確認できないため AVD は作成していない。
