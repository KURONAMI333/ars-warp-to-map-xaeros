package com.kuronami.arswarptomapx.sync;

import java.util.Objects;

/**
 * 巻物 1 本が指す行き先の不変スナップショット。Ars Nouveau / Minecraft のどちらの型にも
 * 依存しない純粋なデータ保持用レコード。行き先キー生成（{@link DestinationKey}）と
 * ウェイポイント計画（{@link WaypointPlanner}）は Minecraft クラスパス無しで動く。
 *
 * @param name        表示名（金床名があればそれ、無ければ巻物の既定表示名）
 * @param x           行き先ブロック座標 X
 * @param y           行き先ブロック座標 Y
 * @param z           行き先ブロック座標 Z
 * @param dimensionId 次元の ResourceLocation 文字列（例: {@code minecraft:overworld}）
 */
public record ScrollDestination(String name, int x, int y, int z, String dimensionId) {

    public ScrollDestination {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }
}
