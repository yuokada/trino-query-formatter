# analyzeAST Display Improvement Plan / analyzeASTの見せ方改善計画

## 1) Background / 背景

### English
Current `analyze --show-ast` output prints the indentation-based node list from `QueryAnalyzer.dumpAst` almost as-is.
While simple to implement, it has several usability issues:

- It is class-name centric and not aligned with user-facing semantic intent (clauses, expressions, targets).
- For long SQL, important structures (FROM/JOIN/WHERE/CTE) are hard to spot.
- Text and JSON outputs are not aligned in granularity.
- JSON embeds AST as a plain string, which is not ideal for machine processing.

### 日本語
現状の `analyze --show-ast` は、`QueryAnalyzer.dumpAst` が出力する
「ノード型のインデント列」をほぼそのまま表示します。
実装はシンプルですが、以下の課題があります。

- ノード名（クラス名）中心で、利用者が読みたい意味情報（句・式・対象）に寄っていない。
- 長いSQLでは情報量が多く、重要箇所（FROM/JOIN/WHERE/CTE）が埋もれやすい。
- text と json の見せ方の粒度が揃っていない。
- JSON埋め込み時に文字列として扱うため、機械処理しづらい。

## 2) Goals / 目標

### English
- Provide a human-readable AST view by default.
- Provide a machine-friendly AST structure (JSON object/array).
- Keep output size/performance under control and preserve CLI compatibility.

### 日本語
- **人が読みやすいAST表示**をデフォルト提供する。
- **機械が扱いやすいAST構造**（JSON object/array）を提供する。
- 出力コスト（文字数・処理時間）を抑えつつ、既存CLI互換性を維持する。

## 3) Non-goals / 非目標

### English
- No change to Trino AST semantics itself.
- No complete semantic labeling for every node in the first iteration.

### 日本語
- Trino AST自体の意味解析ロジックは変更しない。
- すべてのノードに初回から完全な意味ラベルを付けることは目指さない。

## 4) Improvement Strategy / 改善方針

### 4.1 AST output modes / AST出力モード

### English
In addition to `--show-ast`, support explicit AST view modes:

- `--ast-view tree` (default): human-friendly formatted tree
- `--ast-view outline`: clause-focused summary (WITH/SELECT/FROM/JOIN/WHERE/GROUP/ORDER/LIMIT)
- `--ast-view raw`: current class-name style (migration compatibility)

Also add `--ast-depth <n>` for depth limiting.

### 日本語
`--show-ast` に加え、表示モードを指定可能にする。

- `--ast-view tree`（既定）: 人間向け整形ツリー
- `--ast-view outline`: 主要句中心の要約
- `--ast-view raw`: 現行互換のクラス名ベース

併せて `--ast-depth <n>` を追加し、深さ制限を可能にする。

### 4.2 Text output readability / Text出力の可読性

### English
For `TextAnalysisPrinter`:

- Add section headers (e.g. `AST (tree):`).
- Show `node type + key attributes`.
  - Example: `Table name=tpch.sf1.orders`
  - Example: `Join type=LEFT criteria=ON`
- Optionally hide noisy leaf nodes with `--ast-compact`.
- Show statement index when multiple statements are present.

### 日本語
`TextAnalysisPrinter` で以下を実施。

- 見出しを追加（`AST (tree):` など）
- ノード行に「種別 + 主要属性」を表示
- 冗長ノードを `--ast-compact` で省略可能にする
- 複数ステートメント時に番号対応を明示する

### 4.3 JSON structure / JSON出力の構造化

### English
Evolve `JsonAnalysisPrinter` from string AST to structured AST:

- New field: `astTree` (object)
  - Example: `{ "nodeType": "Query", "children": [...] }`
- Keep legacy `ast` (string) temporarily for compatibility, with deprecation notice.
- Extend limits beyond `--ast-limit` (chars) to include node/depth limits.
  - e.g. `--ast-node-limit`, `--ast-depth`

### 日本語
`JsonAnalysisPrinter` の `ast` を文字列から段階的に構造化する。

- 新フィールド: `astTree`（object）
- 互換のため当面 `ast`（string）も残す（deprecation告知）
- `--ast-limit` を文字数だけでなくノード数/深さにも拡張する

### 4.4 Semantic labels for key nodes / 重要ノードの意味ラベル

### English
Add key attributes to representative nodes in dump/build logic:

- Table: `name`
- Join: `joinType`, `criteriaType`
- FunctionCall: `name`, `category` (scalar/aggregate/window)
- WithQuery: `cteName`
- Limit: `rowCount` (when obtainable)

### 日本語
代表ノードに意味属性を付与する。

- Table: `name`
- Join: `joinType`, `criteriaType`
- FunctionCall: `name`, `category`
- WithQuery: `cteName`
- Limit: `rowCount`（取得可能範囲）

### 4.5 UX for failures/large queries / エラー・巨大クエリ時のUX

### English
- If AST generation fails, keep analysis result and add `astError`.
- For truncated AST, surface:
  - truncation reason (`depth_limit` / `node_limit` / `char_limit`)
  - estimated original node count (if available)

### 日本語
- AST生成失敗時は解析結果本体を返しつつ `astError` を出力する。
- 打ち切り時は理由と推定ノード数（可能なら）を出力する。

## 5) Implementation Phases / 実装ステップ

### Phase 1 (compat-first) / 互換重視
- Add `--ast-view` and `--ast-depth`
- Improve text tree output while preserving raw compatibility
- Add unit tests for view mode and depth limit

### Phase 2 (structured JSON) / 構造化
- Add `astTree` in JSON
- Keep `ast` string for compatibility mode
- Add `--ast-node-limit`

### Phase 3 (polish) / 磨き込み
- Expand semantic labels on key nodes
- Refresh README examples
- Document timeline for deprecating `ast` string

## 6) Test Plan / テスト計画

### English
- Extend `AnalyzeTextDetailsTest` family with AST snapshot checks.
- Extend `AnalyzeJsonAstTest` with:
  - required key checks for `astTree`
  - metadata checks for limit/depth truncation
- Regression checks:
  - existing `--show-ast` cases remain compatible
  - `--format json --details full` compatibility is preserved

### 日本語
- `AnalyzeTextDetailsTest` 系にAST表示のスナップショット検証を追加
- `AnalyzeJsonAstTest` に `astTree` 必須キー検証と超過時メタ情報検証を追加
- 既存 `--show-ast` と `--format json --details full` の互換性を確認

## 7) Acceptance Criteria / 受け入れ基準

### English
- New users can understand major query structure from `--show-ast --ast-view tree`.
- JSON consumers can parse and traverse `astTree`.
- No breaking change for existing options (or explicit migration guidance if needed).

### 日本語
- 初見ユーザーが `--show-ast --ast-view tree` で主要構造を把握できる。
- JSON利用者が `astTree` をパースして走査できる。
- 既存オプションに破壊的変更がない（必要時は移行手順を明示）。

## 8) Risks and Mitigations / リスクと対策

### English
- **Risk:** Output format growth increases test brittleness.
  - **Mitigation:** combine snapshot checks with key/section assertions.
- **Risk:** Large AST affects CLI responsiveness.
  - **Mitigation:** enforce depth/node/char limit controls.
- **Risk:** Compatibility mode lingers too long.
  - **Mitigation:** document deprecation deadline in README and release notes.

### 日本語
- **リスク:** 出力拡張によりテストが壊れやすくなる。
  - **対策:** 完全一致だけでなくキー/セクション検証を併用する。
- **リスク:** ASTサイズ増大でCLI応答が悪化する。
  - **対策:** 深さ・ノード数・文字数の3系統リミットを導入する。
- **リスク:** 互換モードが長期化する。
  - **対策:** README/リリースノートでdeprecation期限を明示する。
