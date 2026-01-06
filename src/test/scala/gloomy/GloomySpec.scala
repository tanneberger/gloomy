package gloomy.test

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import gloomy._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ChiselDelayedAdder extends Module with ExposedInterface {
  val io = IO(new Bundle {
    val value_1 = chisel3.Input(UInt(8.W));
    val value_2 = chisel3.Input(UInt(8.W));
    val out = chisel3.Output(UInt(8.W))
    val clock = chisel3.Input(chisel3.Clock())
  });

  val r0 = RegInit(0.U(8.W))
  val r1 = RegInit(0.U(8.W))
  val r2 = RegInit(0.U(8.W))
  val r3 = RegInit(0.U(8.W))

  withClock(io.clock) {
    r0 := io.value_1
    r1 := io.value_2
    r2 := r0
    r3 := r1

    io.out := r2 & r3;
  }

  override def getBundle: GloomyBundle = new GloomyBundle(input_elements = Map("value_1" -> io.value_1, "value_2" -> io.value_2, "clock" -> io.clock), output_elements = Map("out" -> io.out))

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
    simulate(new GloomyBox[ChiselDelayedAdder](() => new ChiselDelayedAdder())) { dut =>
      dut.io.elements("value_1").poke(4.U(32.W));
      dut.io.elements("value_2").poke(5.U(32.W));
      dut.io.clock.step()
      dut.io.clock.step()
      dut.io.clock.step()
      assert(dut.io.elements("out").peekValue().asBigInt == 4);
    }
  }

  "random testing with Verilog" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new RandomTesting[DelayedAdder, GloomyBox[DelayedAdder], List[BigInt]](dut, 50, List(0, 0, 0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val x = (256 + state.head.toLong & state(1).toLong) % 256
        val outcome = x == outputs.head.toLong || (state(0) == 0 && state(1) == 0)
        (outcome, inputs.toList)
      })
      assert(test_generator.test())
    }
  }

  "random testing with Chisel" in {
    simulate(new GloomyBox[ChiselDelayedAdder](() => new ChiselDelayedAdder())) { dut =>
      val test_generator = new RandomTesting[ChiselDelayedAdder, GloomyBox[ChiselDelayedAdder], List[BigInt]](dut, 50, List(0, 0, 0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val x = (256 + state(0) & state(1).toLong) % 256
        val outcome = x == outputs.head.toLong || (state(0) == 0 && state(1) == 0)
        (outcome, inputs.toList)
      })

      assert(test_generator.test())
    }
  }

  "complete testing" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new CompleteTesting[DelayedAdder, GloomyBox[DelayedAdder], List[BigInt]](dut, List(0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val x = (256 + state(0) & state(1).toLong) % 256
        val outcome = x == outputs.head.toLong || (state(0) == 0 && state(1) == 0)
        (outcome, inputs.toList)
      })

      assert(test_generator.test())
    }
  }

  "csv testing" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>

      val test_generator = new CSVTesting[DelayedAdder, GloomyBox[DelayedAdder], List[BigInt]](dut, "src/test/resources/configs/test_data.csv", ",", List(0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val x = (256 + state(0) & state(1).toLong) % 256
        val outcome = x == outputs.head.toLong || (state(0) == 0 && state(1) == 0)
        (outcome, inputs.toList)
      })

      assert(test_generator.test())
    }
  }

  "csv testing chisel" in {
    simulate(new GloomyBox[ChiselDelayedAdder](() => new ChiselDelayedAdder())) { dut =>

      val test_generator = new CSVTesting[ChiselDelayedAdder, GloomyBox[ChiselDelayedAdder], List[BigInt]](dut, "src/test/resources/configs/test_data.csv", ",", List(0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val x = (256 + state(0) & state(1).toLong) % 256
        val outcome = x == outputs.head.toLong || (state(0) == 0 && state(1) == 0)
        (outcome, inputs.toList)
      })

      assert(test_generator.test())
    }
  }
}
