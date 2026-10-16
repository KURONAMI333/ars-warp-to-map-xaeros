package com.kuronami.arswarptomapx.sync;

/**
 * 記録の健全性のセッション内遷移（MC に依存しない純粋クラス）。
 *
 * <p>記録ファイルが健全に読めても、その後の同期で例外が出たり保存に失敗したら、記録と
 * 実体がずれている可能性がある。そうなったセッションでは以降を不健全として扱い、記録に
 * 無い同名同座標の実体を取り込む（{@link WaypointReconciler} の {@code recordHealthy=false}）。
 * 手作り地点を取り違えて消すより、重複を作らない方を優先する。</p>
 *
 * <p>不健全への遷移は片方向（一度 false になったらそのセッション中は戻らない）。</p>
 */
public final class RecordHealth {

    private RecordHealth() {
    }

    /**
     * 同期試行後の健全性。
     *
     * @param healthy    直前までの健全性
     * @param syncFailed 同期が例外で中断したなら true
     * @return 遷移後の健全性（失敗したら false・一度 false なら false のまま）
     */
    public static boolean afterSync(boolean healthy, boolean syncFailed) {
        return healthy && !syncFailed;
    }

    /**
     * 記録保存後の健全性。
     *
     * @param healthy       直前までの健全性
     * @param saveSucceeded 記録の保存に成功したなら true
     * @return 遷移後の健全性（失敗したら false・一度 false なら false のまま）
     */
    public static boolean afterSave(boolean healthy, boolean saveSucceeded) {
        return healthy && saveSucceeded;
    }
}
