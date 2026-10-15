package com.kuronami.arswarptomapx.sync;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 行き先（次元＋座標）から決まる安定キー。
 *
 * <p>同じ行き先を指す巻物が複数あっても同じキーになるため、「同一の行き先は1本の
 * ウェイポイントに集約する」をキー重複だけで自然に満たせる。名前や回転は
 * キーに含めない（名前を変えても同じ行き先として扱い、内容差分で名前だけ更新する）。</p>
 */
public final class DestinationKey {

    private DestinationKey() {
    }

    public static UUID of(String dimensionId, int x, int y, int z) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        // NUL 区切りで座標と次元名の境界が衝突しないようにする
        String key = dimensionId + '\u0000' + x + '\u0000' + y + '\u0000' + z;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
