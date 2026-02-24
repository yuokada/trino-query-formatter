# Remote Server Validation 仕様書

## 概要

`analyze` サブコマンドに、実際の Trino クラスターへ接続して SQL を検証する
**2段階バリデーション** を追加する。

```
Phase 1 (ローカル静的解析) → Phase 2 (リモート EXPLAIN (TYPE VALIDATE) 検証)
```

- **Phase 1**: 構文パース・W001〜W003・E001 などの静的解析を実行する（既存機能）。
- **Phase 2**: Phase 1 で ERROR レベルの finding が発生しなかった場合に限り、
  Trino サーバーへ `EXPLAIN (TYPE VALIDATE) <query>` を送信してリモート検証を実行する。

Phase 1 で ERROR が出た場合は Phase 2 をスキップし、即座に結果を返す。
これにより、構文エラーなどのクエリで不要なネットワーク通信を行わない。

---

## 2段階バリデーション フロー

```
analyze --server localhost:8080 query.sql
        │
        ▼
 ┌─────────────────────────────────┐
 │  Phase 1: ローカル静的解析       │
 │  ・SqlParser 構文チェック         │
 │  ・W001 / W002 / W003 / E001     │
 └─────────────┬───────────────────┘
               │
        ERROR あり?
       ┌───────┴────────┐
      YES               NO
       │                │
       ▼                ▼
   結果出力      ┌──────────────────────────────────────┐
   (Phase 2     │  Phase 2: リモート検証                 │
    スキップ)    │  ・EXPLAIN (TYPE VALIDATE) <query> 送信│
                │  ・Valid=true → findings なし          │
                │  ・エラー → LintFinding 変換           │
                └──────────────┬─────────────┘
                               │
                           結果出力
                    (Phase 1 + Phase 2 の findings)
```

---

## 技術的スコープ

### Phase 2 で追加される検証

| 検証内容 | Trino のエラーコード |
|---|---|
| テーブル / ビューの存在確認 | `TABLE_NOT_FOUND` |
| カラム名の存在確認 | `COLUMN_NOT_FOUND` |
| 関数名・シグネチャの検証 | `FUNCTION_NOT_FOUND` |
| 型不一致の検出 | `TYPE_MISMATCH` |
| その他 USER_ERROR | (汎用 W004 として報告) |

### 実装しない検証

- クエリの実際の実行 (DML / DDL の変更操作)
- パフォーマンス測定 (`EXPLAIN ANALYZE`)
- DDL 文の `EXPLAIN (TYPE VALIDATE)` (将来拡張)
- バッチ / 複数ステートメントのリモート実行

---

## EXPLAIN (TYPE VALIDATE) について

Trino 435 が提供する `EXPLAIN (TYPE VALIDATE)` は、クエリを**実行せず**に
構文・意味論（テーブル・カラム・関数の存在、型整合性）を一括検証する専用モードである。

参照: https://trino.io/docs/435/sql/explain.html

### 構文

```sql
EXPLAIN (TYPE VALIDATE) <statement>
```

### 成功時の応答

検証が通過した場合、`Valid` 列が `true` の 1 行が返される。

```
 Valid
-------
 true
(1 row)
```

`StatementClient.isFailed()` が `false` のまま `isFinished()` が `true` になる。

### 失敗時の応答

検証エラーが発生した場合、Trino はクエリを失敗扱いにし、
`QueryError` にエラー名・メッセージ・行列位置 (`errorLocation`) を返す。

**構文エラー例:**
```
line 1:8: mismatched input 'FORM'. Expecting: ...
```

**意味論エラー例 (テーブル不存在):**
```
line 1:15: Table 'mycat.myschema.nations' does not exist
```

`QueryError.errorLocation` (`lineNumber`, `columnNumber`) が得られる場合は、
LintFinding のメッセージに `(line N, col M)` として付記する。

### plain `EXPLAIN` との違い

| 項目 | `EXPLAIN` | `EXPLAIN (TYPE VALIDATE)` |
|---|---|---|
| 目的 | 実行計画の表示 | 構文・意味論の検証のみ |
| 返却データ | 実行計画テキスト (大量) | `Valid = true` の 1 行 |
| 検証範囲 | 計画生成できる範囲 | 検証に特化 (より厳密) |
| ネットワーク負荷 | 計画テキスト分のデータ転送 | 最小限 |

バリデーション用途では `EXPLAIN (TYPE VALIDATE)` を使用する。

---

## 依存ライブラリ

`io.trino:trino-client` を追加する。バージョンはプロジェクトの
`${trino.version}` に揃える（現在 `435`）。

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.trino</groupId>
    <artifactId>trino-client</artifactId>
    <version>${trino.version}</version>
</dependency>
```

`trino-client` が依存する `okhttp3` はこのライブラリが推移的に持ち込む。
追加の HTTP クライアントライブラリは不要。

### 主要クラス (trino-client 435)

| クラス | 用途 |
|---|---|
| `ClientSession` | サーバー URI・ユーザー・カタログ・スキーマなどのセッション設定 |
| `StatementClientFactory` | `StatementClient` のファクトリ (`newStatementClient()`) |
| `StatementClient` | 非同期クエリ実行。`advance()` でポーリング |
| `QueryError` | エラー情報 (`errorName`, `message`, `errorLocation`) |
| `OkHttpUtil` | 認証 / SSL の `OkHttpClient` 設定ユーティリティ |

---

## 新しい CLI オプション

`analyze` サブコマンドに以下のオプションを追加する。
`--server` を指定しない場合はリモート検証は行われず、既存の動作に変化はない。

```
--server <host:port>           接続先 Trino サーバー (例: localhost:8080)
--server-user <name>           Trino ユーザー名 (デフォルト: システムユーザー)
--server-password <password>   パスワード (平文指定)
--server-access-token <token>  Bearer トークン認証
--server-ssl                   SSL/TLS を有効化
--server-ssl-trust-all         SSL 証明書検証を無効化 (開発環境用)
--explain-timeout <sec>        EXPLAIN のタイムアウト秒数 (デフォルト: 30)
```

> **注意**: `--server-password` は平文オプションとして提供するが、
> セキュリティを重視する場合は環境変数 `TRINO_PASSWORD` の使用を推奨する。

### 環境変数 (CI/CD 用)

| 環境変数 | 対応オプション |
|---|---|
| `TRINO_SERVER` | `--server` |
| `TRINO_USER` | `--server-user` |
| `TRINO_PASSWORD` | `--server-password` |
| `TRINO_ACCESS_TOKEN` | `--server-access-token` |

環境変数はオプション指定で上書きされる。

---

## 実装クラス構成

### 新規クラス

| クラス | パッケージ | 役割 |
|---|---|---|
| `TrinoConnectionOptions` | `subcommand` | Picocli `@ArgGroup` で接続オプションをまとめた Mixin |
| `TrinoRemoteValidator` | `core` | 2段階フロー制御。Phase 1 結果を受け取り Phase 2 を実行 |
| `TrinoExplainClient` | `core` | `trino-client` ラッパー。`explain(sql)` メソッドを提供 |
| `RemoteValidationResult` | `core` | EXPLAIN 結果 (成功/失敗) と生成された `LintFinding` リスト |

### 変更クラス

| クラス | 変更内容 |
|---|---|
| `Analyze.java` | `TrinoConnectionOptions` を `@ArgGroup` で組み込み、`TrinoRemoteValidator` を呼び出す |
| `QueryAnalysisResult.java` | Phase 2 由来の findings (E002〜E005, W004) を格納する `remotefindings` フィールド追加 |

---

## 実装詳細

### TrinoExplainClient

```java
public final class TrinoExplainClient implements Closeable {

    private final OkHttpClient httpClient;
    private final ClientSession session;

    /**
     * Creates an OkHttpClient with the given authentication and SSL settings.
     * Uses OkHttpUtil.basicAuth() / OkHttpUtil.tokenAuth() / OkHttpUtil.setupInsecureSsl().
     */
    public static TrinoExplainClient create(TrinoConnectionOptions opts) { ... }

    /**
     * Executes "EXPLAIN (TYPE VALIDATE) <sql>" on the remote server.
     * Polls StatementClient.advance() until isFinished() or isFailed().
     * On success (Valid = true), returns an empty findings list.
     * On failure, converts QueryError to LintFinding list.
     */
    public RemoteValidationResult validate(String sql) { ... }
}
```

### ClientSession の構築

```java
ClientSession session = ClientSession.builder()
    .server(URI.create("http://" + opts.getServer()))   // https の場合はそのまま
    .user(Optional.ofNullable(opts.getUser()))
    .catalog(Optional.ofNullable(catalog))              // Analyze の --catalog を転用
    .schema(Optional.ofNullable(schema))                // Analyze の --schema を転用
    .timeZone(ZoneId.systemDefault())
    .locale(Locale.getDefault())
    .clientRequestTimeout(Duration.ofSeconds(opts.getExplainTimeout()))
    .build();
```

### OkHttpClient の構築

```java
OkHttpClient.Builder builder = new OkHttpClient.Builder();

if (opts.getAccessToken() != null) {
    OkHttpUtil.tokenAuth(opts.getAccessToken()).apply(builder);
} else if (opts.getUser() != null && opts.getPassword() != null) {
    OkHttpUtil.basicAuth(opts.getUser(), opts.getPassword()).apply(builder);
}

if (opts.isSslTrustAll()) {
    OkHttpUtil.setupInsecureSsl(builder);
} else if (opts.isSsl()) {
    OkHttpUtil.setupSsl(builder, ...);
}

OkHttpClient httpClient = builder.build();
```

### EXPLAIN (TYPE VALIDATE) 実行とポーリング

```java
// EXPLAIN (TYPE VALIDATE) を使用。クエリを実行せず検証のみ実施。
String validateSql = "EXPLAIN (TYPE VALIDATE) " + originalSql;
StatementClient client =
    StatementClientFactory.newStatementClient(httpClient, session, validateSql);

while (client.isRunning()) {
    client.advance();
}

if (client.isFinished()) {
    // 成功: Valid = true の 1 行が返る。findings は空。
    return RemoteValidationResult.valid();
}

if (client.isFailed()) {
    QueryError err = client.finalStatusInfo().getError();
    // err.getErrorName()  → LintFinding のルール ID を決定
    // err.getMessage()    → メッセージ本文
    // err.getErrorLocation() → 行・列番号 (null の場合あり)
    return RemoteValidationResult.failed(toFindings(err));
}
```

`errorLocation` が非 null の場合、LintFinding のメッセージに
`(line N, col M)` を付記してエラー箇所を示す。

---

## エラー → LintFinding 変換規則

Trino の `QueryError.errorName` をもとに LintFinding を生成する。

| `errorName` (Trino) | 生成する LintFinding | 重大度 |
|---|---|---|
| `TABLE_NOT_FOUND` | E002: `Table not found: {message}` | ERROR |
| `COLUMN_NOT_FOUND` | E003: `Column not found: {message}` | ERROR |
| `FUNCTION_NOT_FOUND` | E004: `Function not found: {message}` | ERROR |
| `TYPE_MISMATCH` | E005: `Type mismatch: {message}` | ERROR |
| その他 `USER_ERROR` | W004: `Server validation warning: {message}` | WARNING |
| `INTERNAL_ERROR` / 接続失敗 | (stderr に警告を出力して Phase 2 の結果は省略) | — |

`INTERNAL_ERROR` などサーバー起因のエラーはクエリの問題ではないため、
findings には追加せず stderr に警告のみ出力して終了コード 0 を維持する。

---

## 新しい lint ルール (全ルール更新版)

| ルール ID | 重大度 | メッセージ例 | トリガー条件 |
|---|---|---|---|
| W001 | WARNING | `SELECT * detected; prefer an explicit column list` | SELECT * を検出 |
| W002 | WARNING | `Unknown function: {name}; may be a custom UDF or typo` | `--validate-functions` 時、未知の関数名 |
| W003 | WARNING | `Function {name} expects {n} argument(s), got {m}` | `--udf-catalog` 時、アリティ不一致 |
| W004 | WARNING | `Server validation warning: {message}` | Phase 2 で USER_ERROR (非 ERROR 系) |
| E001 | ERROR | `DELETE without WHERE clause will affect all rows` | WHERE なしの DELETE |
| E002 | ERROR | `Table not found: {message}` | Phase 2: TABLE_NOT_FOUND |
| E003 | ERROR | `Column not found: {message}` | Phase 2: COLUMN_NOT_FOUND |
| E004 | ERROR | `Function not found: {message}` | Phase 2: FUNCTION_NOT_FOUND |
| E005 | ERROR | `Type mismatch: {message}` | Phase 2: TYPE_MISMATCH |

---

## 出力例

### テーブルが存在しない場合 (text 形式)

```
=========================
Catalogs: [mycat]
QueryType: Query
Tables: [mycat.myschema.unknown_table]
Functions: scalar=[], aggregate=[count], window=[]
Lint: [ERROR] E002: Table not found: Table mycat.myschema.unknown_table does not exist
```

### W002 (ローカル) + E002 (リモート) の組み合わせ (json 形式)

Phase 1 は WARNING のみ（ERROR なし）のためリモート検証に進む例:

```json
{
  "queryType": "Query",
  "catalogs": ["mycat"],
  "tables": ["mycat.s.t"],
  "unknownFunctions": ["my_udf"],
  "findings": [
    {"ruleId": "W002", "severity": "WARNING",
     "message": "Unknown function: my_udf; may be a custom UDF or typo"},
    {"ruleId": "E002", "severity": "ERROR",
     "message": "Table not found: Table mycat.s.t does not exist"}
  ]
}
```

### Phase 1 で ERROR → Phase 2 スキップの例

```bash
# DELETE without WHERE → E001 が出るためリモート検証はスキップ
$ analyze --server localhost:8080 query.sql   # query: DELETE FROM t

=========================
QueryType: Delete
Tables: [t]
Lint: [ERROR] E001: DELETE without WHERE clause will affect all rows
# Phase 2 はスキップ (リモートサーバーへの接続なし)
```

---

## 利用例

```bash
# 基本 (認証なし・ローカル開発環境)
analyze --server localhost:8080 query.sql

# カタログ・スキーマを指定
analyze --server localhost:8080 --catalog hive --schema default query.sql

# Basic 認証
analyze --server trino.example.com:443 \
        --server-ssl \
        --server-user alice \
        --server-password s3cr3t \
        query.sql

# Bearer トークン認証
analyze --server trino.example.com:443 \
        --server-ssl \
        --server-access-token "$TOKEN" \
        query.sql

# 静的解析 (W002/W003) + リモート検証を組み合わせ
analyze --validate-functions \
        --udf-catalog udfs.yaml \
        --server localhost:8080 \
        query.sql

# 環境変数を使った CI/CD 利用
TRINO_SERVER=trino.prod:443 \
TRINO_ACCESS_TOKEN="$CI_TOKEN" \
  analyze --server-ssl query.sql
```

---

## 後方互換性

- `--server` を指定しない場合、Phase 2 は実行されず既存の動作に変化なし。
- `--server` を指定した場合も、Phase 1 の静的解析 (W001〜W003、E001) は常に実行される。
- Phase 2 でネットワーク接続失敗・タイムアウトが発生した場合は stderr に警告を出力し、
  Phase 1 の結果のみを返す (終了コードは Phase 1 の結果に基づく)。
- 既存の `QueryAnalyzer.analyze()` シグネチャは変更しない。
  `TrinoRemoteValidator` が `QueryAnalysisResult` を受け取り Phase 2 を追記する形とする。

---

## 実装順序

1. `pom.xml` に `trino-client` 依存を追加
2. `TrinoConnectionOptions.java` (Picocli ArgGroup) を作成
3. `TrinoExplainClient.java` を作成 (OkHttpUtil + StatementClientFactory ラッパー)
4. `RemoteValidationResult.java` を作成
5. `TrinoRemoteValidator.java` を作成 (2段階フロー制御)
6. `QueryAnalysisResult.java` に `remoteFindings` フィールドを追加
7. `Analyze.java` に `TrinoConnectionOptions` を組み込み
8. テスト作成 (WireMock または Trino embedded でスタブ)
9. `./mvnw verify` で全スイート確認
