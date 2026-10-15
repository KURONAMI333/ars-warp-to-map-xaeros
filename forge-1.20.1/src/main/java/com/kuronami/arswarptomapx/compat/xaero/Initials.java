package com.kuronami.arswarptomapx.compat.xaero;

/**
 * ウェイポイント初期文字（ミニマップ上の略号）の抽出（純粋関数・JUnit 対象）。
 *
 * <p>地点名の最初の 1 文字を使う。サロゲートペア（絵文字・漢字拡張など）を
 * 1 文字として扱うため code point 単位で切り出す。</p>
 */
public final class Initials {

    private Initials() {
    }

    /**
     * 名前の最初の 1 文字を返す（code point 単位）。
     *
     * @param name 地点名（null と空文字は空文字を返す）
     * @return 最初の 1 文字（サロゲートペア全体・長さ 2 の文字列になりうる）
     */
    public static String of(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int first = name.codePointAt(0);
        return new String(Character.toChars(first));
    }
}
