# TODO Roadmap

## 全体方針
- 目的: Trino SQL のフォーマット/解析品質を高め、CLI 体験と配布を強化
- 優先度タグ: P0=最優先, P1=高, P2=中 / 規模: S/M/L

## CLI/UX 改善 (P0-P1)
- ✅ 標準入出力対応 (P0/S): `format -` で stdin、`-o` で出力先指定
- ✅ チェックモード (P0/S): `--check` で差分検出のみ（差分時は終了コード≠0）
- ✅ 差分表示 (P1/M): `--diff` で色付き unified diff
- ✅ キーワード大小写 (P1/S): `--keyword-case [upper|lower|keep]`
- ✅ 行幅/インデント (P1/S): `--max-line-length`, `--indent-size`
- ✅ 補完スクリプト (P1/S): `--generate-completion`（bash/zsh/fish）

## フォーマッタ/アナライザ機能 (P0-P1)
- ✅ コメント保持 (P0/M): インライン/ブロックコメントの位置保持
- ✅ Trino 方言対応 (P1/M): `TRY`, `WITH`, `UNNEST`, `MAP`, `JSON` 等
- ✅ CASE/JOIN/サブクエリ整形 (P1/M): 改行・インデント規則を明文化し実装
- ✅ Analyze 出力強化 (P1/M): JSON 出力、重大度・規則ID付与 (W001/E001)
- ✅ 終了コード規約 (P0/S): 警告=1, エラー=2, 例外=3

## 品質/テスト (P0-P1)
- ✅ ゴールデンテスト (P0/M): `src/main/resources/queries` を基に入出力スナップショット
- ✅ 端ケース追加 (P1/M): 長大行、深いネスト、CTE、多重 JOIN、コメント密集
- ✅ ベンチ (P1/S): 大規模 SQL の処理時間/メモリ計測

## DX/CI/CD (P1)
- ✅ CI 強化 (P1/S): `validate`/`test`/Uber-jar アーティファクトを公開
- ✅ ネイティブ配布 (P1/M): `-Dnative` の macOS/Linux バイナリを Release 添付

## 設定/ログ/エラー (P1)
- ✅ ログ詳細度 (P1/S): `-v/--verbose`, `--quiet`、エラー時ヒント出力

## ドキュメント (P1)
- ✅ README 充実 (P1/S): 主要フラグ例、Before/After、終了コード表、パイプ例

## 将来拡張 (P2)
- ✅ ライブラリ化 (P2/M): `core` 向け API を公開し `core` 分離アーティファクトを生成
- ✅ エディタ連携 (P2/L): LSP/Editor プラグイン向け JSON-RPC 入口を追加
