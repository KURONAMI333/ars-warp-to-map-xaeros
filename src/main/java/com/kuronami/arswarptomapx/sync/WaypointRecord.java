package com.kuronami.arswarptomapx.sync;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * この MOD が地図 MOD へ入れたウェイポイントの記録（行き先キー → 最後に反映した内容）。
 *
 * <p>削除・置換の対象を「記録と名前・座標が一致するもの」に限定するために使う。利用者が手で
 * 作ったウェイポイントを消さないための照合元であり、サーバー/ワールド単位でファイルへ
 * 永続化することで、こちらの接続が切れている間に壊れた行き先（＝記録にあり望ましい状態に
 * 無いもの）を次回同期で取り除ける。</p>
 *
 * <p>MC / Xaero の型に依存しない純粋クラス（JUnit が MC クラスパス無しで回る）。
 * テキスト形式は 1 行 1 件
 * {@code uuid|x|y|z|rgb|次元|名前}。次元・名前は URL エンコードするので区切りの
 * {@code |} とは衝突しない（名前の改行も {@code %0A} になり行を壊さない）。</p>
 */
public final class WaypointRecord {

    private static final String SEP = "|";
    private static final int FIELD_COUNT = 7;

    private final Map<UUID, PointSnapshot> entries = new LinkedHashMap<>();

    /** {@link #parse} が非空の壊れた行を読み飛ばしたら true（記録の健全性判定に使う）。 */
    private boolean hadMalformedLines;

    public WaypointRecord() {
    }

    /** 複製。適用は作業コピーに対して行い、成功時だけ差し替える用途に使える。 */
    public WaypointRecord(WaypointRecord source) {
        if (source != null) {
            this.entries.putAll(source.entries);
            this.hadMalformedLines = source.hadMalformedLines;
        }
    }

    public static WaypointRecord empty() {
        return new WaypointRecord();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 読取時に非空の壊れた行を 1 つ以上読み飛ばしたか。true なら記録が欠けている可能性があるため、
     * 呼び出し側は健全でないものとして扱う（空行は壊れた行とみなさない）。
     */
    public boolean hadMalformedLines() {
        return hadMalformedLines;
    }

    public int size() {
        return entries.size();
    }

    /** 記録の読み取り専用ビュー（挿入順）。 */
    public Map<UUID, PointSnapshot> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public PointSnapshot get(UUID id) {
        return entries.get(id);
    }

    public void put(PointSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        entries.put(snapshot.id(), snapshot);
    }

    public PointSnapshot remove(UUID id) {
        return entries.remove(id);
    }

    public void clear() {
        entries.clear();
    }

    /**
     * 指定次元に属する記録だけを抜き出す。
     *
     * @param dimensionId 次元 ID（例: {@code minecraft:overworld}・null 許容）
     * @return 新しいマップ（挿入順保持・null 次元なら空）
     */
    public Map<UUID, PointSnapshot> forDimension(String dimensionId) {
        Map<UUID, PointSnapshot> result = new LinkedHashMap<>();
        if (dimensionId != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : entries.entrySet()) {
                if (dimensionId.equals(entry.getValue().dimensionId())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * 指定次元の記録を丸ごと差し替える（他次元の記録は保持する）。
     *
     * <p>同期で実際に作られた分だけを記録へ載せ直すために使う。この次元の旧記録は消してから
     * {@code content} を載せるので、望ましい集合から外れた分は記録に残らない。</p>
     *
     * @param dimensionId 差し替える次元 ID（null なら何もしない）
     * @param content     差し替え後の内容（null なら空にする＝この次元の記録を全消去）
     */
    public void replaceDimension(String dimensionId, Map<UUID, PointSnapshot> content) {
        if (dimensionId == null) {
            return;
        }
        entries.entrySet().removeIf(entry -> dimensionId.equals(entry.getValue().dimensionId()));
        if (content != null) {
            for (PointSnapshot snapshot : content.values()) {
                put(snapshot);
            }
        }
    }

    /** テキストへ書き出す（1 行 1 件・改行終端あり・空なら空文字）。 */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (PointSnapshot s : entries.values()) {
            sb.append(s.id()).append(SEP)
                    .append(s.x()).append(SEP)
                    .append(s.y()).append(SEP)
                    .append(s.z()).append(SEP)
                    .append(s.rgb()).append(SEP)
                    .append(encode(s.dimensionId())).append(SEP)
                    .append(encode(s.name())).append('\n');
        }
        return sb.toString();
    }

    /**
     * テキストから読む。壊れた行は読み飛ばす（例外にしない）。読み飛ばした事実は
     * {@link #hadMalformedLines()} に残し、呼び出し側が記録の健全性を判断できるようにする。
     */
    public static WaypointRecord parse(String text) {
        WaypointRecord record = new WaypointRecord();
        if (text == null || text.isEmpty()) {
            return record;
        }
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\" + SEP, -1);
            if (parts.length != FIELD_COUNT) {
                record.hadMalformedLines = true;
                continue;
            }
            try {
                UUID id = UUID.fromString(parts[0]);
                record.entries.put(id, new PointSnapshot(
                        id,
                        decode(parts[6]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]),
                        decode(parts[5]),
                        Integer.parseInt(parts[4])));
            } catch (RuntimeException ignored) {
                // 1 行の破損で全体の同期を止めない（その行だけ捨てるが、記録は不健全として扱う）
                record.hadMalformedLines = true;
            }
        }
        return record;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return value;
        }
    }
}
