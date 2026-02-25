package gloomy.test

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import gloomy._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ChiselDelayedAnd extends Module with ExposedInterface {
  val io = IO(new Bundle {
    val value_1 = chisel3.Input(UInt(8.W));
    val value_2 = chisel3.Input(UInt(8.W));
    val out = chisel3.Output(UInt(8.W))
    //val clock = chisel3.Input(chisel3.Clock())
  });

  val r0 = RegInit(0.U(8.W))
  val r1 = RegInit(0.U(8.W))

  withClock(this.clock) {
    r0 := io.value_1
    r1 := io.value_2

    io.out := r0 & r1;
  }

  override def getBundle: GloomyBundle = GloomyBundle.createFromChisel(this.io)
  override def getInterface: GloomyInterface = GloomyInterface.createFromChisel(this.io)
  override def gloomyClock: chisel3.Clock = this.clock
}

class DelayedAdder(entity: ParsedEntity) extends GloomyVerilogBox(entity) with ExposedInterface {
  override def getBundle: GloomyBundle = super.getBundle
}


class GloomySpec extends AnyFreeSpec with Matchers with ChiselSim {
  val parsed_entity: ParsedEntity = GloomyVerilogBox.fromVerilog("./src/test/resources/verilog/delayed_adder.v", "DelayedAdder");
  "testing verilog gloomy box" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      dut.io.elements("value_1").poke(4.U(32.W));
      dut.io.elements("value_2").poke(5.U(32.W));
      dut.io.clock.step()
      dut.io.clock.step()
      dut.io.clock.step()
      assert(dut.io.elements("out").peekValue().asBigInt == 4);
    }
  }

  "testing chisel gloomy box" in {
    simulate(new GloomyBox[ChiselDelayedAnd](() => new ChiselDelayedAnd())) { dut =>
      dut.io.elements("value_1").poke(4.U(32.W));
      dut.io.elements("value_2").poke(5.U(32.W));
      dut.clock.step()
      dut.clock.step()
      dut.clock.step()
      assert(dut.io.elements("out").peekValue().asBigInt == 4);
    }
  }

  "random testing with Verilog" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new RandomTestBench[DelayedAdder, GloomyBox[DelayedAdder], Map[String, BigInt]](dut, 50, Map("value_1" -> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
        val expected_value = (state("value_1").toInt & state("value_2").toInt) % 256
        (Map("out" -> expected_value), inputs)
      })
      //assert(test_generator.test())
    }
  }

  "random testing with Chisel" in {
    simulate(new GloomyBox[ChiselDelayedAnd](() => new ChiselDelayedAnd())) { dut =>
      val test_generator = new RandomTestBench[ChiselDelayedAnd, GloomyBox[ChiselDelayedAnd], Map[String, BigInt]](dut, 50, Map("value_1" -> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
        println(state)
        val expected_value = (state("value_1").toLong & state("value_2").toLong) % 256
        (Map("out" -> expected_value), inputs)
      })

      //assert(test_generator.test())
    }
  }

  "complete testing" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new CompleteTestBench[DelayedAdder, GloomyBox[DelayedAdder], Map[String, BigInt]](dut, Map("value_1" -> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
        val expected_value = (state("value_1").toLong & state("value_2").toLong) % 256
        (Map("out" -> expected_value), inputs)
      })
      //assert(test_generator.test())
    }
  }

  "csv testing" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new CSVTestBench[DelayedAdder, GloomyBox[DelayedAdder], Map[String, BigInt]](dut, "src/test/resources/configs/test_data.csv", ",", Map("value_1" -> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
        val expected_value = (state("value_1").toLong & state("value_2").toLong) % 256
        (Map("out" -> expected_value), inputs)
      })
      assert(test_generator.test())
    }
  }

  "csv testing chisel" in {
    simulate(new GloomyBox[ChiselDelayedAnd](() => new ChiselDelayedAnd())) { dut =>
      val test_generator = new CSVTestBench[ChiselDelayedAnd, GloomyBox[ChiselDelayedAnd], Map[String, BigInt]](dut, "src/test/resources/configs/test_data.csv", ",", Map("value_1" -> 0, "value_2" -> 0), (inputs: Map[String, BigInt], state: Map[String, BigInt]) => {
        val expected_value = (state("value_1").toLong & state("value_2").toLong) % 256
        (Map("out" -> expected_value), inputs)
      })
      //assert(test_generator.test())
    }
  }
}
