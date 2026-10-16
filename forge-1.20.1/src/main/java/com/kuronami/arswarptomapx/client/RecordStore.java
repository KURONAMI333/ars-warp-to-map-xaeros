package com.kuronami.arswarptomapx.client;

import com.kuronami.arswarptomapx.sync.WaypointRecord;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * ウェイポイント記録（{@link WaypointRecord}）のファイル入出力。
 *
 * <p>置き場はゲームディレクトリ配下の {@code config/arswarptomapx/}（ワールドを汚さない）。
 * サーバー/ワールド単位のキーで 1 ファイル。ローダー固有 API を使わず
 * クライアントの {@link Minecraft} から場所を決めるため、ローダー違いのセルで同一コードが通る。</p>
 *
 * <p>書けなくても反映自体は済んでいるため、失敗は静かに握りつぶし次回同期に委ねる
 * （{@code compat/xaero} の silent fail 方針と同じ）。</p>
 */
final class RecordStore {

    private static final String DIR = "config/arswarptomapx";

    private RecordStore() {
    }

    /**
     * サーバー/ワールドの識別子。シングルプレイ（統合サーバー）はワールドフォルダの絶対パス
     * から、マルチプレイは接続先 {@code ip} から作る。どちらも特定できないなら null。
     */
    static String worldKey(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        if (mc.hasSingleplayerServer()) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                try {
                    Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                    return "world-" + sha256Hex(root.toString()).substring(0, 16);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        ServerData current = mc.getCurrentServer();
        if (current != null && current.ip != null && !current.ip.isBlank()) {
            return "server-" + sanitize(current.ip);
        }
        return null;
    }

    /**
     * 読み込み結果。{@code healthy} はファイルが存在し内容を最後まで解釈できたか。ファイル不在・
     * 読取失敗・壊れた行の読み飛ばしは {@code false}（記録は読めた分だけ空扱いに近い）。呼び出し側は
     * この区別で「この MOD の記録が無い」のか「記録が読めない」のかを判断する
     * （{@link com.kuronami.arswarptomapx.sync.WaypointReconciler}）。
     */
    record LoadResult(WaypointRecord record, boolean healthy) {
    }

    /**
     * 記録を読む。ファイル不在・読取失敗時は空の記録（{@code healthy=false}）。壊れた行を
     * 読み飛ばした場合も {@code healthy=false}（記録が欠けている可能性があるため不健全扱い）。
     */
    static LoadResult load(String worldKey) {
        Path file = fileFor(worldKey);
        if (file == null || !Files.isRegularFile(file)) {
            return new LoadResult(WaypointRecord.empty(), false);
        }
        try {
            WaypointRecord parsed = WaypointRecord.parse(Files.readString(file, StandardCharsets.UTF_8));
            return new LoadResult(parsed, !parsed.hadMalformedLines());
        } catch (IOException | RuntimeException e) {
            return new LoadResult(WaypointRecord.empty(), false);
        }
    }

    /**
     * 記録を書く。空になってもファイルは消さず、空ファイルとして残す。「ファイルが無い」を
     * その世界で初めて使う時かキーがずれた時だけに限定し、空にした結果が次セッションで
     * 不健全（＝手作り地点を取り込みうる）と誤判定されるのを防ぐ。
     *
     * @return 書けたら true。書けなかったら false（呼び出し側は記録を不健全に落とす）
     */
    static boolean save(String worldKey, WaypointRecord record) {
        Path file = fileFor(worldKey);
        if (file == null) {
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, record == null ? "" : record.serialize(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException ignored) {
            // 記録が書けなくても本期の反映は済んでいる。次回同期で作り直す
            return false;
        }
    }

    private static Path fileFor(String worldKey) {
        if (worldKey == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath().resolve(DIR).resolve(sanitize(worldKey) + ".txt");
    }

    /** ファイル名に使える文字だけ残す（パス区切り・空白・非 ASCII を潰す）。 */
    private static String sanitize(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length() && sb.length() < 96; i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
