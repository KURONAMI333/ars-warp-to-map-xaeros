package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 次元 ID のシンタックス判定（{@code ResourceLocation} 相当）の単体テスト。MC クラスパス無しで完結。
 */
class DimensionIdTest {

    @Test
    void acceptsWellFormedResourceLocations() {
        assertTrue(DimensionId.isWellFormed("minecraft:overworld"));
        assertTrue(DimensionId.isWellFormed("minecraft:the_nether"));
        assertTrue(DimensionId.isWellFormed("twilightforest:twilight_forest"));
        assertTrue(DimensionId.isWellFormed("mypack:dims/deep/dark")); // path は / を許す
    }

    @Test
    void omittingNamespaceUsesDefault() {
        assertTrue(DimensionId.isWellFormed("overworld"));
    }

    @Test
    void rejectsMalformedValues() {
        assertFalse(DimensionId.isWellFormed(null));
        assertFalse(DimensionId.isWellFormed(""));
        assertFalse(DimensionId.isWellFormed("minecraft:the nether")); // 空白
        assertFalse(DimensionId.isWellFormed("a:b:c"));                // コロン 2 個
        assertFalse(DimensionId.isWellFormed("Minecraft:overworld"));  // namespace は小文字のみ
        assertFalse(DimensionId.isWellFormed("minecraft:Overworld"));  // path は小文字のみ
    }
}
