/**
 * WorleyCaves 洞穴生成（移植自 WorleyCaves 1.5.2，MIT License）。
 *
 * <p>为什么内置而不是让玩家另装模组：外置版本虽然能通过 `InitMapGenEvent` 接管 RTG 的洞穴，
 * 但它有自己的全局 config（不进存档）、会静默顶掉创造界面里的洞穴滑块、破土补顶用的是
 * MC 群系的 `topBlock`（与 RTG 的 `SurfaceBase` 不是一套）。内置后这些都能按本仓库的口径修正。
 *
 * <p>入口：{@link rtg.world.gen.worley.WorleyCavesGenerator}；参数：{@link rtg.world.gen.worley.WorleyCaveParams}。
 * 设计与实测见 {@code docs/worley-caves-merge-feasibility.md}。
 */
package rtg.world.gen.worley;
