package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.challenge.*
import org.junit.Assert.*
import org.junit.Test

class ChallengeRunTest {
    @Test fun `intermediate moving tower continues until the actual deadline`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        run.resume(); val first = run.current.id; run.place()
        physics.world.bodies.last().apply { angularDamping = 0.0; angularVelocity = 1.0 } // 回り続ける塔（減衰を外す。決定 63 でふだんは 3.0）
        repeat(121) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertNotEquals(first, run.current.id)
        assertTrue(run.remainingNs > 0)
        assertNull(run.score)
    }
    @Test fun `final wait is bounded by foreground time even at low frame rates`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        run.resume(); run.place()
        physics.world.bodies.last().apply { angularDamping = 0.0; angularVelocity = 1.0 } // 回り続ける塔（減衰を外す。決定 63 でふだんは 3.0）
        now = ChallengeRules.DURATION_NS; run.tick()
        run.pause(); now += 100_000_000_000; run.resume()
        repeat(5) { now += 1_000_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.FINALIZING, run.phase)
        now += 1_000_000_000; run.tick()
        assertEquals(ChallengeRun.Phase.UNRESOLVED, run.phase)
    }
    @Test fun `three minute play can place more than five pieces without an early finish`() {
        var now = 0L
        val run = ChallengeRun(123, clock = { now })
        var placed = 0
        run.resume()
        repeat(3600) {
            if (run.place()) placed++
            now += 50_000_000; run.tick()
            assertFalse(run.terminal)
        }
        assertTrue(placed > 5)
        assertEquals(ChallengeRun.Phase.FINALIZING, run.phase)
        repeat(125) { now += 50_000_000; run.tick() }
        assertTrue(run.terminal)
    }
    @Test fun `camera bounds remain stable while aiming and falling`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume(); val top = run.cameraTop
        run.move(1.0); run.rotate(45.0)
        assertEquals(top, run.cameraTop, 0.0)
        run.place(); now += 50_000_000; run.tick()
        assertEquals(top, run.cameraTop, 0.0)
    }

    @Test fun `見せたい上は、次の形が出る高さと回しても入る半径で、塔が高くなると上がる`() {
        var now = 0L
        val run = ChallengeRun(123, clock = { now })
        run.resume()
        assertEquals(run.spawnY + ChallengeRun.reachOf(run.current), run.cameraTop, 1e-9)
        // 積み続けて塔が高くなると、次の形が出る高さと一緒に、見せたい上も上がる（縮尺は画面の側で変えない）
        repeat(600) {
            run.place()
            now += 50_000_000; run.tick()
        }
        assertTrue(run.towerHeight > 0.0)
        assertEquals(run.towerHeight + ChallengeRun.SPAWN_ABOVE_M, run.spawnY, 1e-9)
        assertEquals(run.spawnY + ChallengeRun.reachOf(run.current), run.cameraTop, 1e-9)
    }

    @Test fun `途中の揺れ待ちは 3 秒までで、揺れていても次の形へ進む`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        run.resume(); val first = run.current.id; run.place()
        physics.world.bodies.last().apply { angularDamping = 0.0; angularVelocity = 1.0 } // 回り続ける塔（減衰を外す。決定 63 でふだんは 3.0）
        repeat(58) { now += 50_000_000; run.tick() } // 2.9 秒
        assertEquals(ChallengeRun.Phase.FALLING, run.phase)
        repeat(3) { now += 50_000_000; run.tick() } // 3.05 秒
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertNotEquals(first, run.current.id)
    }

    @Test fun `狙っている間に塔が崩れて低くなったら、出てくる高さと見せたい上も下がる（上がる向きには動かない）`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        // 重さの無い世界で、出てきた高さに浮いたまま次の形へ進む（揺れ待ちの上限まで待つ）。塔の高さは浮いている形のてっぺん
        run.resume(); run.place()
        repeat(61) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        val high = run.towerHeight
        assertTrue(high > 1.0)
        assertEquals(high + ChallengeRun.SPAWN_ABOVE_M, run.spawnY, 1e-9)

        // 狙っている間に塔が崩れて、形が台の近くまで下がった
        val body = physics.world.bodies.last()
        body.translate(0.0, -(high - 0.4))
        now += 50_000_000; run.tick()
        assertTrue("崩れたあとの高さ（${run.towerHeight}）まで下がる", run.towerHeight < high - 0.5)
        assertEquals(run.towerHeight + ChallengeRun.SPAWN_ABOVE_M, run.spawnY, 1e-9)
        assertEquals(run.spawnY + ChallengeRun.reachOf(run.current), run.cameraTop, 1e-9)

        // 狙っている間に高くなることはない（揺れで少し跳ねても、出てくる高さは上げない）
        val low = run.towerHeight
        body.translate(0.0, 0.5)
        now += 50_000_000; run.tick()
        assertEquals(low, run.towerHeight, 0.0)
    }

    @Test fun `記録にはいまの決まりの ID を入れる`() {
        var now = 0L
        val run = ChallengeRun(5, clock = { now }, wallClock = { 42 })
        run.resume(); now = ChallengeRules.DURATION_NS; run.tick()
        repeat(20) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRules.ID, run.score!!.rule)
        assertEquals("manual-180-v6", ChallengeRules.ID)
    }

    @Test fun `記録になった回は、止まった塔を「みんな」に送る形で持つ（決定 73）`() {
        var now = 0L
        val run = ChallengeRun(5, clock = { now }, wallClock = { 42 })
        run.resume()
        assertEquals(null, run.tower)
        // 3 つ置いてから時間切れまで待つ
        repeat(3) {
            run.place()
            repeat(40) { now += 50_000_000; run.tick() }
        }
        now = ChallengeRules.DURATION_NS; run.tick()
        repeat(200) { now += 50_000_000; run.tick() }
        val score = run.score!!
        val tower = run.tower!!
        assertEquals(com.rein4ce.roadcollector.core.model.TowerMode.CHALLENGE, tower.mode)
        assertEquals(score.rule, tower.rule)
        assertEquals(score.height.toFloat(), tower.height)
        assertEquals(ChallengeRules.PLATFORM_WIDTH_M.toFloat(), tower.platformWidth)
        assertTrue(tower.placements.isNotEmpty())
        // パーツは使った形だけ、配置はどれかのパーツを指す
        val ids = tower.parts.map { it.id }.toSet()
        assertTrue(tower.placements.all { it.partId in ids })
        assertTrue(ids.all { id -> ChallengeRules.parts.any { it.id == id } })
    }
    @Test fun `moving tower times out without a ranked airborne height`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        run.resume(); run.place()
        physics.world.bodies.last().apply { angularDamping = 0.0; angularVelocity = 1.0 } // 回り続ける塔（減衰を外す。決定 63 でふだんは 3.0）
        now = ChallengeRules.DURATION_NS; run.tick()
        repeat(125) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.UNRESOLVED, run.phase)
        assertNull(run.score); assertFalse(run.place())
    }
    @Test fun `settled physical tower is ranked and subsequent ticks cannot change it`() {
        var now = 0L
        val seed = (0L..100L).first { ChallengePieces(it).next().kind == com.rein4ce.roadcollector.core.model.Kind.BAR }
        val run = ChallengeRun(seed, clock = { now })
        run.resume(); run.place()
        repeat(120) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        now = ChallengeRules.DURATION_NS; run.tick()
        repeat(20) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.FINISHED, run.phase)
        val result = run.score!!
        assertTrue(result.height > 0.0)
        repeat(100) { now += 50_000_000; run.tick() }
        assertSame(result, run.score)
    }
    @Test fun `same seed reproduces every piece and each bag is balanced`() {
        val a = ChallengePieces(123); val b = ChallengePieces(123)
        repeat(100) {
            val batch = List(5) { a.next().also { assertEquals(it, b.next()) } }
            assertEquals(5, batch.map { it.kind }.toSet().size)
            assertEquals(5, batch.map { it.id }.toSet().size)
        }
    }
    @Test fun `deadline is checked by input without waiting for next frame`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume(); now = ChallengeRules.DURATION_NS
        assertFalse(run.place())
        run.move(1.0); run.rotate(15.0)
        assertEquals(0.0, run.x, 0.0); assertEquals(0.0, run.angle, 0.0)
        assertEquals(ChallengeRun.Phase.FINALIZING, run.phase)
    }
    @Test fun `placement just before deadline is included and locks further input`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume(); now = ChallengeRules.DURATION_NS - 1
        assertTrue(run.place()); assertFalse(run.place())
        now++; run.tick()
        assertEquals(ChallengeRun.Phase.FINALIZING, run.phase)
    }
    @Test fun `pause excludes background time and monotonic clock ignores wall time`() {
        var now = 0L; var wall = 0L
        val run = ChallengeRun(1, clock = { now }, wallClock = { wall })
        run.resume(); now += 1_000_000_000L; run.pause()
        now += 999_000_000_000L; wall = -100000L
        assertFalse(run.place()); run.resume(); run.tick()
        assertEquals(179_000_000_000L, run.remainingNs)
        now += 1_000_000_000L; run.tick()
        assertEquals(178_000_000_000L, run.remainingNs)
    }
    @Test fun `clock consumes full foreground gap despite physics cap and freezes result`() {
        var now = 0L
        val run = ChallengeRun(5, clock = { now }, wallClock = { 42 })
        run.resume(); now = ChallengeRules.DURATION_NS; run.tick()
        repeat(20) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.FINISHED, run.phase)
        val result = run.score!!
        assertEquals(0.0, result.height, 0.0)
        now += 60_000_000_000; run.tick(); run.move(1.0); run.rotate(90.0)
        assertSame(result, run.score); assertFalse(run.place())
    }
    @Test fun `falling advances to the next piece within bounded time`() {
        var now = 0L
        val run = ChallengeRun(2, clock = { now })
        run.resume(); val first = run.current.id; run.place()
        repeat(125) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertNotEquals(first, run.current.id)
        assertNull(run.score)
    }

    @Test fun `自動で落ちる：狙っている間も下がっていき、台に触れたら「置く」を押さなくても落ちる`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        val run = ChallengeRun(1, style = ChallengeStyle.AUTO, clock = { now }, world = physics)
        run.resume()
        val start = run.ghostY
        repeat(10) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertEquals("0.5 秒で 0.2 m 下がる", start - 0.2, run.ghostY, 1e-6)
        // 下がっている間は、台にも置いたパーツにも重ならない
        var landed = false
        repeat(200) {
            now += 50_000_000; run.tick()
            if (run.phase == ChallengeRun.Phase.AIMING) assertFalse(physics.overlaps(run.current, run.x, run.ghostY, Math.toRadians(run.angle)))
            if (run.phase == ChallengeRun.Phase.FALLING) landed = true
        }
        assertTrue("台に触れて、物理で落ちた", landed)
        assertTrue("塔に積まれた", run.towerHeight > 0.0)
    }

    @Test fun `自動で落ちる：台の外を下がったら、台の下で落として次の形へ`() {
        var now = 0L
        val run = ChallengeRun(1, style = ChallengeStyle.AUTO, clock = { now })
        run.resume()
        val first = run.current.id
        repeat(30) { run.move(0.1) } // 台（幅 2.8 m）の外まで寄せる
        assertEquals(ChallengeRun.MOVE_LIMIT_M, run.x, 1e-9)
        var ticks = 0
        while (run.current.id == first && ticks < 400) { now += 50_000_000; run.tick(); ticks++ }
        assertNotEquals("落ちて、次の形に進んだ", first, run.current.id)
        assertEquals("台には何も残っていない", 0.0, run.towerHeight, 1e-9)
    }

    @Test fun `自動で落ちる：「置く」でその場からすぐ落とせる・記録は別の決まりの ID`() {
        var now = 0L
        val run = ChallengeRun(5, style = ChallengeStyle.AUTO, clock = { now }, wallClock = { 42 })
        run.resume()
        repeat(5) { now += 50_000_000; run.tick() }
        val y = run.ghostY
        assertTrue(y < run.spawnY)
        assertTrue(run.place())
        assertEquals(ChallengeRun.Phase.FALLING, run.phase)
        now = ChallengeRules.DURATION_NS; run.tick()
        repeat(200) { now += 50_000_000; run.tick() }
        assertTrue(run.terminal)
        run.score?.let { assertEquals(ChallengeRules.AUTO_ID, it.rule) }
        assertNotEquals(ChallengeRules.ID, ChallengeRules.AUTO_ID)
    }

    @Test fun `自分で落とすときは、狙っている間は下がらない`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume()
        repeat(100) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertEquals(run.spawnY, run.ghostY, 0.0)
    }
}
