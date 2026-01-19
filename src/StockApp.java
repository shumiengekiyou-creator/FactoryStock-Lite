import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class StockApp {

    // 在庫：部品名 -> 数量
    static Map<String, Integer> stock = new HashMap<>();
    // 下限在庫：部品名 -> 下限
    static Map<String, Integer> minStock = new HashMap<>();

    // 保存先
    static final Path CSV_PATH = Path.of("stock.csv");          // 在庫保存（name,qty,min）
    static final Path LOG_PATH = Path.of("stock_log.csv");      // 操作ログ

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== 在庫管理ミニ（下限アラート＆ログ付） ===");
            System.out.println("1: 登録/上書き");
            System.out.println("2: 入庫（+）");
            System.out.println("3: 出庫（-）");
            System.out.println("4: 一覧表示");
            System.out.println("5: CSV読み込み");
            System.out.println("6: CSV保存");
            System.out.println("7: 下限在庫を設定/変更");
            System.out.println("0: 終了");
            System.out.print("選択 > ");

            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1" -> register(sc);
                case "2" -> inbound(sc);
                case "3" -> outbound(sc);
                case "4" -> list();
                case "5" -> loadCsv();
                case "6" -> saveCsv();
                case "7" -> setMin(sc);
                case "0" -> {
                    System.out.println("終了します。");
                    sc.close();
                    return;
                }
                default -> System.out.println("入力が違います。0〜7で選んでね。");
            }
        }
    }

    // ---- メニュー処理 ----

    static void register(Scanner sc) {
        System.out.print("部品名 > ");
        String name = sc.nextLine().trim();

        if (!validName(name)) return;

        int qty = readInt(sc, "数量 > ");
        if (qty < 0) {
            System.out.println("数量は0以上にしてね。");
            return;
        }

        int before = stock.getOrDefault(name, 0);
        stock.put(name, qty);

        // 下限が未設定なら、ついでに設定してもらう（任意）
        if (!minStock.containsKey(name)) {
            System.out.print("下限在庫（未設定ならEnterでスキップ） > ");
            String s = sc.nextLine().trim();
            if (!s.isEmpty()) {
                try {
                    int min = Integer.parseInt(s);
                    if (min >= 0) minStock.put(name, min);
                } catch (NumberFormatException ignore) {}
            }
        }

        System.out.println("登録OK: " + name + " = " + qty);
        log("REGISTER", name, qty - before, before, qty);

        checkAlert(name);
    }

    static void inbound(Scanner sc) {
        System.out.print("部品名 > ");
        String name = sc.nextLine().trim();
        if (!validName(name)) return;

        int add = readInt(sc, "入庫数（+） > ");
        if (add <= 0) {
            System.out.println("入庫数は1以上にしてね。");
            return;
        }

        int before = stock.getOrDefault(name, 0);
        int after = before + add;
        stock.put(name, after);

        System.out.println("入庫OK: " + name + " " + before + " -> " + after);
        log("IN", name, +add, before, after);

        checkAlert(name);
    }

    static void outbound(Scanner sc) {
        System.out.print("部品名 > ");
        String name = sc.nextLine().trim();
        if (!validName(name)) return;

        int sub = readInt(sc, "出庫数（-） > ");
        if (sub <= 0) {
            System.out.println("出庫数は1以上にしてね。");
            return;
        }

        int before = stock.getOrDefault(name, 0);
        int after = before - sub;

        if (after < 0) {
            System.out.println("在庫不足！現在 " + before + " なので出庫できません。");
            log("OUT_FAIL", name, -sub, before, before);
            return;
        }

        stock.put(name, after);

        System.out.println("出庫OK: " + name + " " + before + " -> " + after);
        log("OUT", name, -sub, before, after);

        checkAlert(name);
    }

    static void setMin(Scanner sc) {
        System.out.print("部品名 > ");
        String name = sc.nextLine().trim();
        if (!validName(name)) return;

        int min = readInt(sc, "下限在庫（0以上） > ");
        if (min < 0) {
            System.out.println("下限は0以上にしてね。");
            return;
        }

        int beforeMin = minStock.getOrDefault(name, -1);
        minStock.put(name, min);
        System.out.println("下限設定OK: " + name + " min=" + min + (beforeMin >= 0 ? "（前 " + beforeMin + "）" : ""));

        log("SET_MIN", name, 0, stock.getOrDefault(name, 0), stock.getOrDefault(name, 0));
        checkAlert(name);
    }

    static void list() {
        System.out.println("\n--- 在庫一覧（qty / min） ---");
        if (stock.isEmpty()) {
            System.out.println("(まだ何も登録されていません)");
            return;
        }
        for (Map.Entry<String, Integer> e : stock.entrySet()) {
            String name = e.getKey();
            int qty = e.getValue();
            int min = minStock.getOrDefault(name, 0);
            String warn = (qty < min) ? "  <-- 下限割れ!" : "";
            System.out.println(name + " : " + qty + " / min " + min + warn);
        }
    }

    // ---- CSV保存/読み込み ----

    static void saveCsv() {
        try (BufferedWriter w = Files.newBufferedWriter(CSV_PATH, StandardCharsets.UTF_8)) {
            w.write("name,qty,min");
            w.newLine();

            for (Map.Entry<String, Integer> e : stock.entrySet()) {
                String name = e.getKey();
                int qty = e.getValue();
                int min = minStock.getOrDefault(name, 0);
                w.write(name + "," + qty + "," + min);
                w.newLine();
            }

            System.out.println("CSV保存OK: " + CSV_PATH.toAbsolutePath());
            log("SAVE_CSV", "-", 0, 0, 0);
        } catch (IOException ex) {
            System.out.println("CSV保存に失敗: " + ex.getMessage());
        }
    }

    static void loadCsv() {
        if (!Files.exists(CSV_PATH)) {
            System.out.println("CSVが見つかりません: " + CSV_PATH.toAbsolutePath());
            System.out.println("先に「6: CSV保存」をしてね。");
            return;
        }

        try (BufferedReader r = Files.newBufferedReader(CSV_PATH, StandardCharsets.UTF_8)) {
            Map<String, Integer> loadedStock = new HashMap<>();
            Map<String, Integer> loadedMin = new HashMap<>();

            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (first) {
                    first = false;
                    // ヘッダーならスキップ
                    if (line.equalsIgnoreCase("name,qty,min") || line.equalsIgnoreCase("name,qty")) continue;
                }

                String[] parts = line.split(",", -1);

                // 旧形式 name,qty にも対応
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    String qtyStr = parts[1].trim();
                    if (name.isEmpty()) continue;
                    try {
                        int qty = Integer.parseInt(qtyStr);
                        if (qty < 0) continue;
                        loadedStock.put(name, qty);
                        // min未設定は0扱い
                    } catch (NumberFormatException ignore) {}
                }

                // 新形式 name,qty,min
                if (parts.length >= 3) {
                    String name = parts[0].trim();
                    String qtyStr = parts[1].trim();
                    String minStr = parts[2].trim();
                    if (name.isEmpty()) continue;

                    try {
                        int qty = Integer.parseInt(qtyStr);
                        if (qty < 0) continue;
                        loadedStock.put(name, qty);
                    } catch (NumberFormatException ignore) {}

                    try {
                        int min = Integer.parseInt(minStr);
                        if (min >= 0) loadedMin.put(name, min);
                    } catch (NumberFormatException ignore) {}
                }
            }

            stock = loadedStock;
            minStock = loadedMin;

            System.out.println("CSV読み込みOK: " + stock.size() + "件");
            log("LOAD_CSV", "-", 0, 0, 0);

            // 読み込み後、下限割れを一括チェック
            checkAllAlerts();
        } catch (IOException ex) {
            System.out.println("CSV読み込みに失敗: " + ex.getMessage());
        }
    }

    // ---- アラート ----

    static void checkAlert(String name) {
        int qty = stock.getOrDefault(name, 0);
        int min = minStock.getOrDefault(name, 0);
        if (qty <= min) {
            System.out.println("⚠ 下限アラート: " + name + " 在庫 " + qty + "（下限 " + min + "）");
            log("ALERT", name, 0, qty, qty);
        }
    }

    static void checkAllAlerts() {
        for (String name : stock.keySet()) {
            checkAlert(name);
        }
    }

    // ---- ログ ----

    static void log(String action, String name, int delta, int before, int after) {
        // 1行：datetime,action,name,delta,before,after
        try {
            boolean newFile = !Files.exists(LOG_PATH);
            try (BufferedWriter w = Files.newBufferedWriter(
                    LOG_PATH,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {

                if (newFile) {
                    w.write("datetime,action,name,delta,before,after");
                    w.newLine();
                }

                String dt = LocalDateTime.now().format(FMT);
                w.write(dt + "," + action + "," + safe(name) + "," + delta + "," + before + "," + after);
                w.newLine();
            }
        } catch (IOException ignore) {
            // ログが書けなくても本体機能は止めない
        }
    }

    static String safe(String s) {
        // CSVの区切り（,）が入ると崩れるので、置換しておく
        return s.replace(",", " ");
    }

    // ---- 入力/チェック ----

    static boolean validName(String name) {
        if (name.isEmpty()) {
            System.out.println("部品名が空です。");
            return false;
        }
        if (name.contains(",")) {
            System.out.println("部品名にカンマ(,)は使えません（CSVの区切りになるため）。");
            return false;
        }
        return true;
    }

    static int readInt(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                System.out.println("数字で入力してね（例：10）");
            }
        }
    }
}
