package com.kuronami.arswarptomapx.compat.xaero;

import com.kuronami.arswarptomapx.ArsWarpToMap;
import com.kuronami.arswarptomapx.sync.PointSnapshot;
import com.kuronami.arswarptomapx.sync.SyncWriteStage;
import com.kuronami.arswarptomapx.sync.WaypointRecord;
import com.kuronami.arswarptomapx.sync.WaypointReconciler;

import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Xaero's Minimap へのウェイポイント反映（Reflection 直叩き）。
 * Xaero 型への参照はすべてこのクラス（と Inner）に閉じ込める。解決に失敗したら
 * {@code catch (Throwable)} で吸収し、Xaero 不在・バージョン違いでは静かに何もしない
 * （mod-076 の silent fail 方針を踏襲）。保存だけは失敗した時に一度だけ警告を出す。
 *
 * <p>Reflection チェーン（xaeros-minimap 26.4.2 / 26.5.0・javap 一次確認 2026-10-16。
 * XAERO_NOTES の 2026-05 版からパッケージ移動があるため現行実名を採用）:</p>
 *
 * <pre>
 * xaero.hud.minimap.BuiltInHudModules.MINIMAP          // HudModule (public static final)
 *   → xaero.hud.module.HudModule.getCurrentSession()   // MinimapSession or null
 *     → xaero.hud.minimap.module.MinimapSession.getWorldManager()
 *       → xaero.hud.minimap.world.MinimapWorldManager.getCurrentWorld()  // MinimapWorld or null
 *         → xaero.hud.minimap.world.MinimapWorld.getCurrentWaypointSet() // WaypointSet or null
 *           → WaypointSet.add(Waypoint, boolean) / .remove(Waypoint) / .getWaypoints()
 *     → xaero.hud.minimap.module.MinimapSession.getWorldManagerIO()
 *       → xaero.hud.minimap.world.io.MinimapWorldManagerIO.saveWorld(MinimapWorld)  // 保存
 * </pre>
 *
 * <p>Waypoint 要素型は {@code xaero.common.minimap.waypoints.Waypoint}。
 * コンストラクタは {@code (int x, int y, int z, String name, String initials,
 * WaypointColor, WaypointPurpose, boolean disabled)} を使用する。</p>
 *
 * <p><b>利用者ウェイポイントの保護</b>: Xaero の実体からは UUID が読めないため、
 * 「記録（{@link WaypointRecord}）と名前・座標・略号・色が一致する実体」だけを削除・置換の対象にする。
 * 記録はサーバー/ワールド単位でクライアントに永続化されており、接続が切れている間に
 * 壊れた行き先の残骸も次回同期で消せる。</p>
 *
 * <p><b>作成済みの判定</b>: Xaero は {@code WaypointSet.add} だけではディスクへ保存しない
 * ことがあるため、「作成済み」は記録単独では判定せず、記録と名前・座標・略号・色が一致する実体が
 * 今のセットにあるものに限る（{@link WaypointReconciler}）。実体が無ければ作り直し、
 * 実体だけあって記録が無ければ新規作成せず採用する。変更後は Xaero 自身の GUI と同じ
 * 保存経路（{@code MinimapSession.getWorldManagerIO().saveWorld(world)}）を reflection で呼ぶ。</p>
 */
public final class XaeroBridge {

    /** {@link #syncDimension} の結果。呼び出し側は「適用済み」「次回再試行」「失敗（記録不健全）」を区別する。 */
    public enum Outcome {
        /** 適用した（記録も更新済み）。 */
        APPLIED,
        /** 前提未達（Xaero 不在・セッション未初期化・このワールドが別次元）、または最初の書き込み前の例外。
         *  次回再試行。記録は無事で、不健全に落とす必要はない。 */
        RETRY,
        /** 最初の書き込み後に例外で中断した。部分適用の可能性があるので、呼び出し側はそのセッションの記録を不健全に落とす。 */
        FAILED
    }

    /** Reflection 解決が一度でも失敗したら true（Xaero 不在 / API drift）。以降即 no-op。 */
    private static volatile boolean permanentlyUnavailable = false;

    private XaeroBridge() {
    }

    /**
     * 「プレイヤーが今いる次元」の地点集合を Xaero へ差分同期する。
     *
     * <p>他次元の地点は呼び出し側で除外してある（
     * {@code getCurrentWaypointSet()} が返すのは現在次元のセットのみのため）。</p>
     *
     * @param playerDimId   プレイヤーの現在次元 ID（例: {@code minecraft:overworld}）
     * @param curDimDesired 現在次元のあるべき地点集合（空でもよい＝全削除が正）
     * @param record        この MOD が入れたウェイポイントの記録。適用に成功した分だけ
     *                      更新される（失敗時は記録を変更しない＝部分適用を残さない）
     * @param recordHealthy 記録ファイルが健全に読めたなら true（不在・破損時は false）。
     *                      健全な時に記録に無い同名同座標の実体は利用者の手作りとして触らない
     * @return {@link Outcome#APPLIED}（適用済み）・{@link Outcome#RETRY}（前提未達・最初の書き込み前の例外で
     *         次回再試行）・{@link Outcome#FAILED}（最初の書き込み後の例外で中断。呼び出し側は記録を不健全に落とす）
     */
    public static Outcome syncDimension(String playerDimId, Map<UUID, PointSnapshot> curDimDesired,
                                        WaypointRecord record, boolean recordHealthy) {
        if (permanentlyUnavailable || record == null) {
            return Outcome.RETRY;
        }
        try {
            return Inner.syncDimension(playerDimId, curDimDesired, record, recordHealthy);
        } catch (Throwable t) {
            // Inner が例外を分類して返す。ここへ来るのは Inner の外側の想定外の失敗だけ。
            // 書き込みは Inner の中でしか起きないので、記録を不健全にはしない（次回再試行）。
            return Outcome.RETRY;
        }
    }

    private static final class Inner {

        static Object minimapModule;         // BuiltInHudModules.MINIMAP の値
        static Method getCurrentSession;     // HudModule -> MinimapSession
        static Method getWorldManager;       // MinimapSession -> MinimapWorldManager
        static Method getCurrentWorld;       // MinimapWorldManager -> MinimapWorld
        static Method getCurrentWaypointSet; // MinimapWorld -> WaypointSet
        static Method getDimId;              // MinimapWorld -> ResourceKey<Level>
        static Method getWaypoints;          // WaypointSet -> Iterable<Waypoint>
        static Method addWithFlag;           // WaypointSet.add(Waypoint, boolean)
        static Method removeWaypoint;        // WaypointSet.remove(Waypoint)
        static Method waypointGetX;          // Waypoint.getX()
        static Method waypointGetY;          // Waypoint.getY()
        static Method waypointGetZ;          // Waypoint.getZ()
        static Method waypointGetName;       // Waypoint.getName()
        static Method waypointGetInitials;   // Waypoint.getInitials()
        static Method waypointGetColor;      // Waypoint.getColor() = WaypointColor.ordinal()
        static Constructor<?> waypointCtor;  // Waypoint(x,y,z,name,initials,color,purpose,disabled)
        static Method colorFromIndex;        // WaypointColor.fromIndex(int)
        static Object normalPurpose;         // WaypointPurpose.values()[0]
        static Class<?> sessionClass;        // MinimapSession
        static Class<?> worldClass;          // MinimapWorld

        /** 保存経路（{@code getWorldManagerIO()} / {@code saveWorld(MinimapWorld)}）は遅延解決。 */
        static Method getWorldManagerIO;
        static Method saveWorldMethod;
        static boolean saveWarned;

        static void resolve() throws Exception {
            Class<?> hudModulesClass = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            minimapModule = hudModulesClass.getField("MINIMAP").get(null);

            Class<?> hudModuleClass = Class.forName("xaero.hud.module.HudModule");
            getCurrentSession = hudModuleClass.getMethod("getCurrentSession");

            sessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession");
            getWorldManager = sessionClass.getMethod("getWorldManager");

            Class<?> managerClass = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            getCurrentWorld = managerClass.getMethod("getCurrentWorld");

            worldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld");
            getCurrentWaypointSet = worldClass.getMethod("getCurrentWaypointSet");
            getDimId = worldClass.getMethod("getDimId");

            Class<?> setClass = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet");
            getWaypoints = setClass.getMethod("getWaypoints");
            addWithFlag = setClass.getMethod("add", resolveElementClass(setClass), boolean.class);
            removeWaypoint = setClass.getMethod("remove", resolveElementClass(setClass));

            Class<?> elementClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            waypointGetX = elementClass.getMethod("getX");
            waypointGetY = elementClass.getMethod("getY");
            waypointGetZ = elementClass.getMethod("getZ");
            waypointGetName = elementClass.getMethod("getName");
            waypointGetInitials = elementClass.getMethod("getInitials");
            waypointGetColor = elementClass.getMethod("getColor");

            Class<?> colorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            colorFromIndex = colorClass.getMethod("fromIndex", int.class);
            Class<?> purposeClass = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");
            normalPurpose = ((Object[]) purposeClass.getMethod("values").invoke(null))[0];

            waypointCtor = elementClass.getConstructor(
                    int.class, int.class, int.class,
                    String.class, String.class,
                    colorClass, purposeClass, boolean.class);
        }

        /** WaypointSet の要素型（add(Waypoint, boolean) の引数から解決）。 */
        static Class<?> resolveElementClass(Class<?> setClass) throws NoSuchMethodException {
            for (Method m : setClass.getMethods()) {
                if (m.getName().equals("add") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == boolean.class) {
                    return m.getParameterTypes()[0];
                }
            }
            throw new NoSuchMethodException("WaypointSet.add(Waypoint, boolean) not found");
        }

        static Outcome syncDimension(String playerDimId, Map<UUID, PointSnapshot> curDimDesired,
                                     WaypointRecord record, boolean recordHealthy) {
            // 最初の削除・作成を始めるまでを記録し、例外の扱いを分ける:
            // 書き込み前の例外は再試行（記録は無事）、書き込み開始後の例外は部分適用の可能性があるため失敗。
            SyncWriteStage stage = new SyncWriteStage();
            try {
                if (getCurrentSession == null) {
                    try {
                        resolve();
                    } catch (Throwable t) {
                        permanentlyUnavailable = true; // Xaero 不在 / API drift → 以降静かに no-op
                        return Outcome.RETRY;
                    }
                }
                Object session = getCurrentSession.invoke(minimapModule);
                if (session == null) {
                    return Outcome.RETRY; // HUD セッション未初期化 → 次回再試行
                }
                Object manager = getWorldManager.invoke(session);
                if (manager == null) {
                    return Outcome.RETRY;
                }
                Object world = getCurrentWorld.invoke(manager);
                if (world == null) {
                    return Outcome.RETRY;
                }
                // 次元フィルタ: Xaero 側の current world がプレイヤー次元と一致する時だけ触る
                // （不一致＝切替ラグ中。誤った次元のセットへ書き込む事故を防ぐ）
                ResourceKey<?> xaeroDim = (ResourceKey<?>) getDimId.invoke(world);
                if (xaeroDim == null || !playerDimId.equals(xaeroDim.location().toString())) {
                    return Outcome.RETRY;
                }
                Object set = getCurrentWaypointSet.invoke(world);
                if (set == null) {
                    return Outcome.RETRY;
                }

                // 現セット内の実体。名前・座標・略号・色は判定に、オブジェクト自体は削除に使う
                List<Object> present = new ArrayList<>();
                List<WaypointReconciler.Entity> views = new ArrayList<>();
                for (Object waypoint : (Iterable<?>) getWaypoints.invoke(set)) {
                    present.add(waypoint);
                    views.add(new WaypointReconciler.Entity(
                            (String) waypointGetName.invoke(waypoint),
                            (Integer) waypointGetX.invoke(waypoint),
                            (Integer) waypointGetY.invoke(waypoint),
                            (Integer) waypointGetZ.invoke(waypoint),
                            (String) waypointGetInitials.invoke(waypoint),
                            (Integer) waypointGetColor.invoke(waypoint)));
                }

                // 「作成済み」は記録 ∩ セット内の実体。実体が無ければ作り直し、記録が無く実体だけ
                // あれば新規作成せず採用する（重複を作らない）
                WaypointReconciler.Plan plan = WaypointReconciler.reconcile(
                        curDimDesired, record.forDimension(playerDimId), views, recordHealthy);

                for (int index : plan.removals()) {
                    stage.beginWrite();
                    removeWaypoint.invoke(set, present.get(index));
                }
                for (PointSnapshot snapshot : plan.creations()) {
                    stage.beginWrite();
                    addWithFlag.invoke(set, createWaypoint(snapshot), Boolean.FALSE); // false = 末尾追加
                }

                // ここまで例外が無ければ記録をこの次元分だけ差し替える（部分適用を残さない）
                record.replaceDimension(playerDimId, plan.record());

                // 変更を加えた時だけ Xaero 自身の保存経路を呼ぶ（add だけでは保存されない版がある）
                if (plan.changesTheSet()) {
                    saveWorld(session, world);
                }
                return Outcome.APPLIED;
            } catch (Throwable t) {
                // 反映の失敗は静かに扱い次回再試行（XAERO_NOTES silent fail ポリシー）。
                // 最初の書き込み前なら記録は無事で再試行、開始後なら部分適用の可能性があるため失敗。
                return stage.failureCorruptsRecord() ? Outcome.FAILED : Outcome.RETRY;
            }
        }

        /**
         * Xaero 自身の GUI と同じ経路でワールドを保存する。
         * 失敗しても同期は続ける（この期の表示は済んでいる）。警告は一度だけ出す。
         */
        static void saveWorld(Object session, Object world) {
            try {
                Method ioGetter = getWorldManagerIO;
                if (ioGetter == null) {
                    ioGetter = sessionClass.getMethod("getWorldManagerIO");
                    getWorldManagerIO = ioGetter;
                }
                Object io = ioGetter.invoke(session);
                if (io == null) {
                    return;
                }
                Method save = saveWorldMethod;
                if (save == null) {
                    save = Class.forName("xaero.hud.minimap.world.io.MinimapWorldManagerIO")
                            .getMethod("saveWorld", worldClass);
                    saveWorldMethod = save;
                }
                save.invoke(io, world);
            } catch (Throwable t) {
                if (!saveWarned) {
                    saveWarned = true;
                    ArsWarpToMap.LOGGER.warn(
                            "Ars Warp to Map could not persist Xaero's Minimap waypoints", t);
                }
            }
        }

        static Object createWaypoint(PointSnapshot s) throws Exception {
            int enumIdx = DyeColors.dyeIndexToWaypointColorIndex(DyeColors.nearestDyeIndex(s.rgb()));
            Object color = colorFromIndex.invoke(null, enumIdx);
            return waypointCtor.newInstance(
                    s.x(), s.y(), s.z(),
                    s.name(), Initials.of(s.name()),
                    color, normalPurpose, Boolean.FALSE);
        }
    }
}
