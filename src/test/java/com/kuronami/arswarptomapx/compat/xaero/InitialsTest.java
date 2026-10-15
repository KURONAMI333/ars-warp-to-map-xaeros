package com.kuronami.arswarptomapx.compat.xaero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ウェイポイント初期文字抽出（Initials）の単体テスト。
 * サロゲートペアを 1 文字として扱うことを守る回帰ガード。
 */
class InitialsTest {

    @Test
    void asciiNameYieldsFirstChar() {
        assertEquals("H", Initials.of("Home"));
        assertEquals("B", Initials.of("Base Camp"));
    }

    @Test
    void japaneseNameYieldsFirstChar() {
        assertEquals("家", Initials.of("家"));
        assertEquals("拠", Initials.of("拠点"));
    }

    @Test
    void surrogatePairIsKeptWhole() {
        // サロゲートペア 1 文字が壊れない（U+29E3D「𩸽」= \uD867\uDE3D）
        String fish = "\uD867\uDE3D";
        assertEquals(fish, Initials.of(fish + "定食"));
        assertEquals(2, Initials.of(fish + "定食").length()); // code point 1 文字 = char 2 個

        String emoji = "\uD83C\uDF7A"; // 🍺
        assertEquals(emoji, Initials.of(emoji + "Bar"));
    }

    @Test
    void emptyAndNullAreSafe() {
        assertEquals("", Initials.of(""));
        assertEquals("", Initials.of(null));
    }
}
