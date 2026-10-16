package com.kuronami.arswarptomapx.sync;

/**
 * 地図 MOD への書き込みが始まったかどうかの進行状態（MC・Xaero に依存しない純粋クラス）。
 *
 * <p>ウェイポイントの反映は「実体の削除」→「地点の作成」の順に1件ずつ行う。まだ1件も
 * 書き込みを始めていない段階（Xaero の状態や実体を読むだけ）で例外が出た場合、記録と実体は
 * ずれていないので次回の再試行でよく、記録を不健全に落とす必要はない。一度でも書き込みを
 * 始めた後に例外が出ると部分適用の可能性があるため、呼び出し側は記録を不健全に落とす。</p>
 *
 * <p>この遷移（未開始 → 開始済み）は片方向。</p>
 */
public final class SyncWriteStage {

    private boolean writesStarted;

    /** 削除・作成を1件行う直前に呼ぶ。以降は「書き込み開始済み」のまま。 */
    public void beginWrite() {
        writesStarted = true;
    }

    /** まだ1件も書き込みを始めていない（＝状態を読むだけの段階）なら true。 */
    public boolean beforeFirstWrite() {
        return !writesStarted;
    }

    /**
     * この時点で例外が出た場合に、記録を不健全に落とすべきか。
     * 書き込み開始済みなら true（部分適用の可能性）、未開始なら false（再試行でよい）。
     */
    public boolean failureCorruptsRecord() {
        return writesStarted;
    }
}
