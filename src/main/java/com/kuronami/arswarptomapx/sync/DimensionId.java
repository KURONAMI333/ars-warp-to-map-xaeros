package com.kuronami.arswarptomapx.sync;

/**
 * 次元 ID 文字列が {@code ResourceLocation} として解釈できるかの純粋判定。
 *
 * <p>{@code ResourceLocation.tryParse} は Minecraft クラスパスを要するため、単体テストから
 * 直接は呼べない。読み取り側（{@code ArsScrollReader}）は tryParse で不正な次元を捨てるが、
 * 「不正な次元の巻物は黙って無視され、他の巻物の同期は続く」という性質を
 * Minecraft クラスパス無しで検証できるよう、同じシンタックス判定をここに置く。</p>
 *
 * <p>判定則は {@code ResourceLocation} に合わせる: {@code namespace:path} 形式で、コロンは
 * 多くとも 1 個。namespace は {@code [a-z0-9_.-]}、path は {@code [a-z0-9/._-]} のみ。
 * namespace を省略した場合は {@code minecraft} が既定。</p>
 */
public final class DimensionId {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    private DimensionId() {
    }

    /** ResourceLocation として解釈できれば true。null・空・コロン 2 個以上・不正文字は false。 */
    public static boolean isWellFormed(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int colon = value.indexOf(':');
        String namespace = DEFAULT_NAMESPACE;
        String path = value;
        if (colon >= 0) {
            if (value.indexOf(':', colon + 1) >= 0) {
                return false; // コロンは 1 個まで
            }
            path = value.substring(colon + 1);
            if (colon > 0) {
                namespace = value.substring(0, colon);
            }
        }
        return !namespace.isEmpty() && !path.isEmpty()
                && isNamespace(namespace) && isPath(path);
    }

    private static boolean isNamespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPath(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
