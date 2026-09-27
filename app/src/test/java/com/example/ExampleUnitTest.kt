package com.example

import com.example.sim.ScenarioId
import com.example.sim.TrainPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun deterministicPhysics_producesBitIdenticalPositions() {
    val engineA = TrainPhysicsEngine()
    val engineB = TrainPhysicsEngine()

    engineA.resetScenario(ScenarioId.FREIGHT_HAUL, customSeed = 998877)
    engineB.resetScenario(ScenarioId.FREIGHT_HAUL, customSeed = 998877)

    engineA.autoBrakePercent = 0f
    engineB.autoBrakePercent = 0f
    engineA.throttleNotch = 5
    engineB.throttleNotch = 5

    for (tick in 0 until 300) {
      engineA.stepFixed60Hz(reducedMotion = false)
      engineB.stepFixed60Hz(reducedMotion = false)
    }

    assertEquals(engineA.positionMeters, engineB.positionMeters, 0.0)
    assertEquals(engineA.speedMps, engineB.speedMps, 0.0)
  }

  @Test
  fun automatedSelfTest_passesAllSubsystems() {
    val engine = TrainPhysicsEngine()
    val report = engine.runAutomatedSelfTest()
    assertTrue(report.overallPass)
    assertEquals(35, report.assetsVerifiedCount)
  }
}
