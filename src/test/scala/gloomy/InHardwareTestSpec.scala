package gloomy.test

import chisel3.{Bundle, Module, RegInit, UInt, fromIntToLiteral, fromIntToWidth, withClock}
import chisel3.simulator.scalatest.ChiselSim
import gloomy.{ExposedInterface, GloomyBox, GloomyBundle, GloomyInterface, GloomyTestBox, RandomTestGenerator, TestBench, HardwareTestSetup}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class InHardwareTestSpec extends AnyFreeSpec with Matchers with ChiselSim {
  "foobar" in {
    val test_generator = new RandomTestGenerator[Map[String, BigInt]](Map("value_1"-> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
      val expectedOutcome = (state("value_1") & state("value_2").toLong) % 256

      val outputs: Map[String, BigInt] = Map("out" -> expectedOutcome);
      (outputs, inputs)
    })

    simulate(new HardwareTestSetup[ChiselDelayedAnd](test_generator, () => new ChiselDelayedAnd())) { dut =>
      for (i <- 0 to 10) {
        //println("clock: " + i + dut.io.error.peekValue())
        dut.clock.step()
        println(dut.io.error_count.peekValue().asBigInt, dut.io.error.peekValue().asBigInt)
      }
    }
  }
}

object Elaborate extends App {
  val firtoolOptions = Array("--lowering-options=" + List(
    "disallowLocalVariables",
    "disallowPackedArrays",
    "locationInfoStyle=wrapInAtSquareBracket"
  ).reduce(_ + "," + _))
  val test_generator = new RandomTestGenerator[Map[String, BigInt]](Map("value_1"-> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
    val expectedOutcome = (state("value_1") & state("value_2").toLong) % 256

    val outputs: Map[String, BigInt] = Map("out" -> expectedOutcome);
    (outputs, inputs)
  })

  print(circt.stage.ChiselStage.emitSystemVerilogFile(new HardwareTestSetup[ChiselDelayedAnd](test_generator, () => new ChiselDelayedAnd()), args))
}