# analyzeASTの見せ方改善計画

## 背景
現状の `analyze --show-ast` は、`QueryAnalyzer.dumpAst` が出力する
「ノード型のインデント列」をそのまま表示します。
この形式は実装がシンプルな一方で、以下の課題があります。

- ノード名（クラス名）中心で、利用者が読み取りたい意味情報（句・式・対象）に寄っていない。
- 長いSQLでは情報量が多く、重要箇所（FROM/JOIN/WHERE/CTE）が埋もれやすい。
- text と json で見せ方の粒度が揃っておらず、用途別に使い分けしづらい。
- JSON埋め込み時に文字列として扱うため、機械処理しづらい。

## 目標
- **人が読みやすいAST表示**をデフォルト提供する。
- **機械が扱いやすいAST構造**（JSON object/array）を提供する。
- 表示コスト（文字数・処理時間）を抑制し、既存CLIの互換性を維持する。

## 非目標
- Trino AST自体の意味解析ロジックを変更すること。
- すべてのノードに完全な意味ラベルを初回から付与すること。

## 改善方針

### 1. AST出力モードの明確化
`--show-ast` に加え、表示モードを指定可能にする。

- `--ast-view tree`（既定）: 人間向けの整形ツリー
- `--ast-view outline`: 主要句（WITH/SELECT/FROM/JOIN/WHERE/GROUP/ORDER/LIMIT）中心の要約
- `--ast-view raw`: 現行互換のクラス名ベース（移行期間用）

併せて `--ast-depth <n>` を追加し、深さ制限を可能にする。

### 2. Text出力の可読性改善
`TextAnalysisPrinter` で以下を実施。

- 罫線/見出しを追加（`AST (tree):` 等）
- ノード行に「種別 + 主要属性」を表示
  - 例: `Table name=tpch.sf1.orders`
  - 例: `Join type=LEFT criteria=ON`
- 一部ノード（Identifier, StringLiteral等）は冗長な場合に省略可能（`--ast-compact`）
- 1ステートメント複数件時にステートメント番号と対応づけを明示

### 3. JSON出力の構造化
`JsonAnalysisPrinter` の `ast` を文字列から段階的に構造化へ。

- 新フィールド: `astTree`（object）
  - 例: `{ "nodeType": "Query", "children": [...] }`
- 互換維持のため、当面 `ast`（string）も残す（deprecation告知）
- `--ast-limit` は文字列だけでなくノード数/深さ上限にも拡張
  - 例: `--ast-node-limit`, `--ast-depth`

### 4. 重要ノードの意味ラベル追加
`dumpNodeRecursive` 相当処理で、代表ノードに意味属性を付与。

- Table: `name`
- Join: `joinType`, `criteriaType`
- FunctionCall: `name`, `category`（scalar/aggregate/window）
- WithQuery: `cteName`
- Limit: `rowCount`（取得できる範囲で）

### 5. エラー/巨大クエリ時のUX改善
- AST生成失敗時は、解析結果本体は返しつつ `astError` を出力。
- 大規模ASTでは以下を表示。
  - 打ち切り理由（`depth_limit` / `node_limit` / `char_limit`）
  - 元の推定ノード数（取得可能なら）

## 実装ステップ（短期リリース向け）

### Phase 1（互換重視）
- `--ast-view` と `--ast-depth` を追加
- Textのtree表示を改善（raw互換を維持）
- 単体テスト追加（表示モード/深さ制限）

### Phase 2（構造化）
- JSONに `astTree` を追加
- `ast` stringを互換モードとして維持
- `--ast-node-limit` 導入

### Phase 3（磨き込み）
- 主要ノードの意味ラベル拡充
- READMEのサンプル刷新
- `ast` stringの将来廃止方針を明記

## テスト計画
- `AnalyzeTextDetailsTest` 系にAST表示のスナップショット検証を追加
- `AnalyzeJsonAstTest` に以下を追加
  - `astTree` の必須キー検証
  - limit/depth超過時のメタ情報検証
- 回帰確認
  - 既存 `--show-ast` 利用ケースが壊れないこと
  - `--format json --details full` の互換性

## 受け入れ基準
- 初見ユーザーが `--show-ast --ast-view tree` の出力を見て、主要句の構造を把握できる。
- JSON利用者が `astTree` をパースしてノード走査できる。
- 既存オプション利用者に破壊的変更がない（または明示移行手順がある）。

## リスクと対策
- **リスク**: 出力仕様の拡張でテストが壊れやすくなる。
  - **対策**: 文字列完全一致ではなく、キー/セクション単位検証を併用。
- **リスク**: ASTサイズ増大でCLI応答が悪化。
  - **対策**: 深さ・ノード数・文字数の3系統リミットを実装。
- **リスク**: 互換期間が長引く。
  - **対策**: READMEとリリースノートでdeprecation期限を明記。
