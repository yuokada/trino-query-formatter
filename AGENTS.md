# Repository Guidelines

## プロジェクト構成とモジュール
- `src/main/java/io/github/yuokada`: Java ソース
  - `core`: コアロジック（例: `QueryAnalyzer`）
  - `subcommand`: CLI サブコマンド（`Format`, `Analyze`）
  - `EntryCommand.java`: Picocli のトップレベルコマンド
- `src/test/java`: JUnit 5 テスト（`*Test.java`）
- `src/main/resources/queries`: サンプル SQL（フィクスチャ/手動実行）
- `src/main/docker`: ネイティブイメージ用 Dockerfiles
- ルート: `pom.xml`, `mvnw*`, `Makefile`, `checkstyle.xml`

## ビルド・テスト・開発コマンド
- 開発実行: `./mvnw compile quarkus:dev -Dquarkus.args='format src/main/resources/queries/query1.sql'`
  - ホットリロード + Picocli 実行
- ユニットテスト: `./mvnw test`
- パッケージ（JAR）: `./mvnw package`; 実行: `java -jar target/quarkus-app/quarkus-run.jar --help`
- Uber-jar: `./mvnw package -Dquarkus.package.jar.type=uber-jar`; 実行: `java -jar target/*-runner.jar`
- ネイティブ: `./mvnw package -Dnative`（または `-Dquarkus.native.container-build=true`）
- GraalVM(asdf): `make build_with_graalvm`（Java 17 GraalVM 必須）

## コーディング規約と命名
- Java 17 / Quarkus 3.x / Picocli を前提
- インデント2スペース、説明的な命名（例: `QueryAnalyzer`, `Format`）
- パッケージ: `io.github.yuokada.<module>`
- Checkstyle（`mvn validate` で検証）:
  - 行長100、Javadoc必須、`*` インポート禁止、空白ルール遵守

## テスト方針
- フレームワーク: JUnit 5
- 配置: `src/test/java`、命名: `ClassNameTest.java`（1クラス=1関心ごと）
- フィクスチャ: `src/main/resources/queries`
- 実行: `./mvnw test`、ネイティブプロファイル: `./mvnw -Pnative verify`（Failsafe IT 有効）

## コミット & プルリクエスト
- コミット: 短く命令形。例: `Bump quarkus to 3.20`, `Update pom.xml`
- PR: 変更目的/影響を明記、関連 Issue をリンク、ユーザー向け変更は CLI 実行例と出力を添付
- CI グリーン必須。差分は最小限、無関係なリファクタは避ける

## セキュリティ/設定の注意
- Maven Wrapper（`./mvnw`）と Java 17 を使用
- ネイティブはコンテナビルド推奨、または asdf の GraalVM 使用
- Secrets をコミットしない。リリースは GitHub Actions（repo スコープトークン）

## ブランチ戦略
- 基本方針:
  - 方式: Trunk-based（GitHub Flow）+ 必要時のみ短命リリースブランチ
  - 目的: 常にリリース可能な `master` を維持し、小さく速く統合
- ブランチ命名:
  - 機能: `feature/<short-desc>`（例: `feature/formatter-cli-help`）
  - 修正: `fix/<issue-id>-<short-desc>`
  - リファクタ/雑務/文書: `refactor/...`, `chore/...`, `docs/...`
  - リリース安定化: `release/x.y`
  - ホットフィックス: `hotfix/<x.y.z>-<short-desc>`
- PR/マージ:
  - 小さく頻繁に（1 PR = 1 目的、差分最小）
  - 必須: CI グリーン（`./mvnw validate && ./mvnw test`）、1+ 承認
  - マージ: Squash merge 推奨（履歴を簡潔に）
  - PR本文: 目的/影響、関連 Issue、CLI 実行例と出力（ユーザー影響時）
- リリース:
  - バージョニング: SemVer（`pom.xml` を更新）
  - 通常: `master` からタグ `vX.Y.Z`（安定化不要ならブランチ不要）
  - 安定化あり: `release/x.y` → 微修正 → `vX.Y.Z` タグ → `master` に反映
  - 次開発版: `X.Y+1.0-SNAPSHOT` に更新
  - 配布: GitHub Actions（repo スコープトークン）
- ホットフィックス:
  - 重大不具合: `hotfix/<x.y.z>-...` を `master`（または開いている `release/x.y`）から
  - 反映: 修正後 `vX.Y.Z+1` タグ、`master` と該当 `release/x.y` に適用（必要に応じて cherry-pick）
- 便利コマンド:
  - 新規ブランチ: `git checkout -b feature/formatter-cli-help`
  - リリースブランチ: `git checkout -b release/1.3`
  - バージョン更新: `./mvnw versions:set -DnewVersion=1.3.0`（開発: `1.4.0-SNAPSHOT`）
  - タグ付け: `git tag -a v1.3.0 -m "Release 1.3.0" && git push --tags`

## ブランチ保護設定
- 対象ブランチ: `master`, `release/*`
- 必須要件:
  - PR 経由のみ（直 push 禁止）、レビュー 1 件以上
  - ステータスチェック必須: `validate`（`./mvnw validate`）と `test`（`./mvnw test`）
  - ベースブランチと最新の状態が必須（Require branch to be up to date）
  - フォースプッシュ/ブランチ削除を禁止
- 推奨運用:
  - Squash merge を既定に設定（履歴を簡潔に）
  - 新しいコミットで古い承認を無効化（Dismiss stale approvals）

## リリースノート作成手順
1) バージョン確定: `pom.xml` を `X.Y.Z` に更新し commit
2) タグ作成: `git tag -a vX.Y.Z -m "Release X.Y.Z" && git push --tags`
3) GitHub Releases でタグからリリースを作成し「Generate release notes」を使用
4) セクションを整理:
   - Features（例: `feature/*` ブランチの PR）
   - Fixes（`fix/*`）
   - Breaking changes（あれば明示）
5) 必要に応じて成果物を添付（CI で生成された Uber-jar/Native など）
6) 公開後、`pom.xml` を次開発版 `X.Y+1.0-SNAPSHOT` に更新
7) ラベル運用（任意）: `type:feature`, `type:fix`, `type:docs`, `type:refactor`, `breaking-change` を PR に付与し自動ノートの精度を向上
