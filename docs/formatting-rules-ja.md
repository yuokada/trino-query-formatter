# フォーマット規則（CASE/JOIN/サブクエリ）

このドキュメントは、`format` サブコマンドの主要な整形規則を明文化したものです。

## CASE 式
- `CASE` / `WHEN` / `THEN` / `ELSE` / `END` は句境界ごとに改行されます。
- 深いネストでもインデントは `--indent-size` に従って再スケールされます。

## JOIN
- `JOIN` 句は `FROM` 句の直後に続きます。
- `ON (...)` 条件は Trino フォーマッタ準拠で括弧付き表記になります。
- `LEFT JOIN` / `RIGHT JOIN` / `FULL JOIN` / `CROSS JOIN` を保持します。

## サブクエリ
- `IN (SELECT ...)` や `FROM (SELECT ...)` のサブクエリは句単位で改行されます。
- CTE（`WITH ... AS (...)`）を含む場合も、`WITH` 本体と主問い合わせを分離して整形します。

## Trino 方言
- `TRY`, `TRY_CAST`, `UNNEST`, `MAP`, `ARRAY`, `ROW`, `JSON_*` 系関数を含む SQL を対象に
  回帰テストを実施しています（`TrinoDialectFormatTest`）。
