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
        physics.world.bodies.last().angularVelocity = 1.0
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
        physics.world.bodies.last().angularVelocity = 1.0
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
        physics.world.bodies.last().angularVelocity = 1.0
        repeat(58) { now += 50_000_000; run.tick() } // 2.9 秒
        assertEquals(ChallengeRun.Phase.FALLING, run.phase)
        repeat(3) { now += 50_000_000; run.tick() } // 3.05 秒
        assertEquals(ChallengeRun.Phase.AIMING, run.phase)
        assertNotEquals(first, run.current.id)
    }

    @Test fun `記録にはいまの決まりの ID を入れる`() {
        var now = 0L
        val run = ChallengeRun(5, clock = { now }, wallClock = { 42 })
        run.resume(); now = ChallengeRules.DURATION_NS; run.tick()
        repeat(20) { now += 50_000_000; run.tick() }
        assertEquals(ChallengeRules.ID, run.score!!.rule)
        assertEquals("manual-180-v3", ChallengeRules.ID)
    }
    @Test fun `moving tower times out without a ranked airborne height`() {
        var now = 0L
        val physics = com.rein4ce.roadcollector.core.physics.StackWorld()
        physics.world.setGravity(0.0, 0.0)
        val run = ChallengeRun(1, clock = { now }, world = physics)
        run.resume(); run.place()
        physics.world.bodies.last().angularVelocity = 1.0
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
}
