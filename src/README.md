# FactoryStock Lite

Java製のコンソール在庫管理ミニアプリです。  
登録・入庫・出庫・一覧表示に加え、CSV保存/読み込み、下限在庫アラート、操作ログを実装しています。

## Features
- 在庫の登録/上書き
- 入庫（+）/ 出庫（-）
- 在庫一覧表示
- CSV保存/読み込み（stock.csv）
- 下限在庫アラート（min）
- 操作ログ出力（stock_log.csv）

## How to Run
VS Codeで `StockApp.java` を開き、`Run` を押してください。  
ターミナルでメニュー番号を入力して操作します。

## File Output
- `stock.csv` : 在庫データ（name,qty,min）
- `stock_log.csv` : 操作ログ（datetime,action,name,delta,before,after）

## Tech
- Java (OpenJDK 21 / Temurin)
- VS Code + Extension Pack for Java
