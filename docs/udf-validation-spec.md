# UDF Validation 仕様書

## 概要

`analyze` サブコマンドに、SQL 内で使用されている関数を Trino 組み込み関数カタログと照合し、
未知の関数（カスタム UDF の可能性がある関数）を静的に検出する機能を追加する。

クラスター接続なしで実施できる静的解析の範囲に限定する。

---

## 技術的スコープ

### 実装する検証

| 検証内容 | 手法 |
|---|---|
| SQL 構文の正当性 | 既存の `SqlParser` (変更なし) |
| 関数名の収集 | AST の `FunctionCall` ノード走査 (既存) |
| 組み込み関数との照合 | `TrinoBuiltinFunctions` カタログとの Set 比較 |
| 未知関数の報告 | 新しいリスト警告 **W002** として `LintFinding` に追加 |
| ユーザー定義の既知関数指定 | `--known-functions` オプション (カンマ区切り or `@ファイル`) |

### 実装しない検証

- クラスター接続による実在確認
- 引数の型チェック
- 戻り値の型推論
- UDF のバージョン検証

---

## 新しいクラス・変更一覧

### 1. `TrinoBuiltinFunctions.java` (新規)

`src/main/java/io/github/yuokada/core/TrinoBuiltinFunctions.java`

Trino 435 の組み込み関数名を小文字で管理する定数クラス。

```
カテゴリ:
  SCALAR_FUNCTIONS   - 数学・文字列・日時・型変換・配列・Map・JSON・URL・正規表現・バイナリ等
  AGGREGATE_FUNCTIONS - 集約関数 (count, sum, avg, approx_distinct 等)
  WINDOW_FUNCTIONS   - ウィンドウ関数 (row_number, rank, lead, lag 等)
  ALL_BUILTIN_FUNCTIONS = SCALAR_FUNCTIONS ∪ AGGREGATE_FUNCTIONS ∪ WINDOW_FUNCTIONS
```

主要 API:
```java
public static boolean isBuiltin(String functionName)
public static boolean isAggregate(String functionName)
public static boolean isWindow(String functionName)
public static Set<String> allBuiltins()
```

### 2. `QueryAnalyzer.java` (変更)

- `isAggregateName()` のハードコードリストを `TrinoBuiltinFunctions.isAggregate()` に置き換え
- `collectExtendedDetails()` に `knownFunctions` パラメータを追加
- 関数が組み込みカタログにも `knownFunctions` にも含まれない場合、builder に `addUnknownFunction()` で記録
- `analyze()` メソッドのシグネチャ追加:
  ```java
  // 既存 (後方互換維持)
  public static QueryAnalysisResult analyze(String sql, String defaultCatalog, String defaultSchema)
  // 新規オーバーロード
  public static QueryAnalysisResult analyze(String sql, String defaultCatalog,
      String defaultSchema, Set<String> knownFunctions)
  ```

### 3. `QueryAnalysisResult.java` (変更)

- `unknownFunctions` フィールドを `Builder` と結果クラスに追加 (`LinkedHashSet<String>`)
- `getUnknownFunctions()` ゲッター追加
- `getFindings()` に W002 ルールを追加:
  ```
  W002 (WARNING): "Unknown function: {name}; may be a custom UDF or typo"
  ```
- `toJson()` に `"unknownFunctions"` フィールドを追加

### 4. `Analyze.java` (変更)

新しい Picocli オプション:
```
--validate-functions         未知関数の検出を有効化 (デフォルト: false)
--known-functions <list>     カンマ区切りの追加既知関数名リスト
                             例: --known-functions "my_udf,another_udf"
```

`call()` 内の処理:
1. `--validate-functions` が true の場合のみ W002 ルールを有効化
2. `--known-functions` 文字列をパースして `Set<String>` に変換
3. `QueryAnalyzer.analyze(sql, catalog, schema, knownFunctions)` を呼ぶ

### 5. テスト (新規・変更)

| テストクラス | 内容 |
|---|---|
| `TrinoBuiltinFunctionsTest` | カタログの基本的な正当性 (count, array_agg 等が組み込みと判定される) |
| `UnknownFunctionLintTest` | W002 が正しく生成される / 組み込み関数では生成されない |
| `AnalyzeValidateFunctionsTest` | `--validate-functions` オプションの end-to-end テスト |

---

## 旧: 新しい lint ルール (W002 のみ)

| ルール ID | 重大度 | メッセージ | トリガー条件 |
|---|---|---|---|
| W002 | WARNING | `Unknown function: {name}; may be a custom UDF or typo` | 関数名が組み込みカタログにも `--known-functions` リストにも存在しない場合 |

---

## 出力例

### text 形式 (--details full)

```
=========================
Catalogs: [catalog1]
QueryType: Query
Tables: [catalog1.s.t]
Functions: scalar=[my_etl_transform,upper], aggregate=[count], window=[]
UnknownFunctions: [my_etl_transform]
Lint: [WARNING] W002: Unknown function: my_etl_transform; may be a custom UDF or typo
```

### json 形式

```json
{
  "queryType": "Query",
  "catalogs": ["catalog1"],
  "tables": ["catalog1.s.t"],
  "functionsScalar": ["my_etl_transform", "upper"],
  "functionsAggregate": ["count"],
  "functionsWindow": [],
  "unknownFunctions": ["my_etl_transform"],
  "findings": [
    {"ruleId": "W002", "severity": "WARNING",
     "message": "Unknown function: my_etl_transform; may be a custom UDF or typo"}
  ]
}
```

---

## --known-functions の利用例

```bash
# 単発指定
trino-query-formatter analyze --validate-functions \
    --known-functions "my_etl_transform,date_to_epoch" query.sql

# UDF リストをファイルで指定 (1行1関数)
trino-query-formatter analyze --validate-functions \
    --known-functions @my-udfs.txt query.sql
```

`@ファイル` 形式は Picocli の `@-file` 機能を使わず、ツール側で解釈する。
ファイルは UTF-8、1行1関数名、空行・`#` コメント行は無視する。

---

## --udf-catalog オプション (W003 — アリティ検証)

### 概要

`--udf-catalog <yamlファイル>` を指定すると、YAML で定義した UDF の引数数を
静的に照合し、不一致を **W003** lint 警告として報告する。

### YAML 形式

```yaml
# udfs.yaml
functions:
  - name: my_etl_transform
    description: "ETL transformation function"
    arity: 2               # 引数数が 2 でなければ W003

  - name: date_to_epoch
    arity: 1

  - name: my_variadic_udf
    minArgs: 1             # 1 以上の引数を受け付ける (上限なし)

  - name: my_bounded_udf
    minArgs: 2
    maxArgs: 4             # 2〜4 引数

  - name: my_no_arity_udf  # arity/minArgs を省略するとアリティ検証なし
    description: "Known UDF with no arity constraint"
```

- `arity` — 引数数の完全一致。`minArgs`/`maxArgs` より優先される。
- `minArgs` — 引数数の下限 (省略時 0)。
- `maxArgs` — 引数数の上限 (省略時: 上限なし)。
- `description` — 任意の説明文 (検証には使用しない)。
- YAML ファイルは UTF-8。行コメント (`#`) は YAML 標準として解釈される。

### 利用例

```bash
# アリティ検証のみ (W002 は出力しない)
analyze --udf-catalog udfs.yaml query.sql

# W002 (未知関数) + W003 (アリティ不一致) の両方
analyze --validate-functions --udf-catalog udfs.yaml query.sql

# --known-functions と組み合わせ
analyze --validate-functions \
    --known-functions "extra_udf" \
    --udf-catalog udfs.yaml query.sql
```

### 動作規則

| オプション組み合わせ | W002 | W003 |
|---|---|---|
| なし | なし | なし |
| `--validate-functions` のみ | あり (組み込み外の関数) | なし |
| `--udf-catalog` のみ | なし | あり (アリティ不一致) |
| 両方 | あり (カタログ関数は既知扱い) | あり |

- `--udf-catalog` に含まれる関数は常に「既知」として扱われるため、
  `--validate-functions` と併用しても W002 は発生しない。

### W003 出力例

```
=========================
Catalogs: [catalog1]
QueryType: Query
Tables: [catalog1.s.t]
Functions: scalar=[my_etl_transform,upper], aggregate=[count], window=[]
Lint: [WARNING] W003: Function my_etl_transform expects 2 argument(s), got 1
```

JSON:
```json
{
  "findings": [
    {"ruleId": "W003", "severity": "WARNING",
     "message": "Function my_etl_transform expects 2 argument(s), got 1"}
  ]
}
```

### 新規クラス

| クラス | 役割 |
|---|---|
| `UdfDefinition` | 1 UDF エントリの POJO (name/description/arity/minArgs/maxArgs) |
| `UdfCatalog` | YAML ファイルをロードし `Map<String, UdfDefinition>` を返すユーティリティ |

`UdfCatalog.load(Path)` は `ObjectMapper(new YAMLFactory())` (Jackson YAML) でデシリアライズし、
キーは関数名の小文字。

---

## 新しい lint ルール (全ルール)

| ルール ID | 重大度 | メッセージ | トリガー条件 |
|---|---|---|---|
| W001 | WARNING | `SELECT *` detected; prefer an explicit column list | SELECT * を検出 |
| W002 | WARNING | `Unknown function: {name}; may be a custom UDF or typo` | `--validate-functions` 時、未知の関数名 |
| W003 | WARNING | `Function {name} expects {n} argument(s), got {m}` | `--udf-catalog` 時、アリティ不一致 |
| E001 | ERROR | `DELETE without WHERE clause will affect all rows` | WHERE なしの DELETE |

---

## 後方互換性

- `--validate-functions` を指定しない場合、W002 は生成されない (既存の挙動に変化なし)
- `--udf-catalog` を指定しない場合、W003 は生成されない
- `QueryAnalyzer.analyze(sql, catalog, schema)` および
  `QueryAnalyzer.analyze(sql, catalog, schema, knownFunctions)` の
  既存シグネチャは維持

---

## 実装順序

1. `TrinoBuiltinFunctions.java` を作成 ✅
2. `QueryAnalyzer.java` を更新 (新しい analyze オーバーロード) ✅
3. `QueryAnalysisResult.java` に `unknownFunctions` と W002 を追加 ✅
4. `Analyze.java` に `--validate-functions` / `--known-functions` を追加 ✅
5. テスト作成・全スイート確認 ✅
6. `jackson-dataformat-yaml` を `pom.xml` に追加
7. `UdfDefinition.java` / `UdfCatalog.java` を作成
8. `QueryAnalysisResult.java` に W003 findings を追加
9. `QueryAnalyzer.java` に 5 引数 `analyze()` オーバーロードを追加 (アリティ検証)
10. `Analyze.java` に `--udf-catalog` オプションを追加
11. テスト作成・全スイート確認
