package gloomy.test

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.util.circt.PlusArgsValue
import gloomy._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import svsim.CommonCompilationSettings
import svsim.PlusArg

import scala.collection.immutable.Range


class alu(entity: ParsedEntity) extends GloomyVerilogBox(entity) {}

object AluTesting {
  def calculate_expected_value(data: Map[String, BigInt], state: Int): BigInt = {
    if (data("reset") == 0) {
      return 0
    }
    return if (data("enable") == 1 && data("core_state") == 5) {
      if (data("decoded_alu_output_mux") == 0) {
        data("decoded_alu_arithmetic_mux").toInt match {
          case 0 => (data("rs") + data("rt")) % 256
          case 1 => (256 + data("rs") - data("rt")) % 256
          case 2 => (data("rs") * data("rt")) % 256
          case 3 => {
            if (data("rt") == 0) {
              0
            } else {
              (data("rs") / data("rt")) % 256
            }
          }
        }
      } else {
        //  {5'b0, (rs - rt > 0), (rs - rt == 0), 1'b0}
        val first = "1" // if (data("rs") > data("rt")) {"1"} else {"0"}
        val second = if (data("rs") == data("rt")) {"1"} else {"0"}
        Integer.parseInt("00000" + first + second + "0", 2)
      }
    } else {
      state
    }
  }
}
class HvxAddSpec extends AnyFreeSpec with Matchers with ChiselSim {
  override implicit def commonSettingsModifications: svsim.CommonSettingsModifications =
    (original: CommonCompilationSettings) => {
      original.copy(
        includeDirs = Some(original.includeDirs.get ++ Seq("/home/tanneberger/workspace/uni/chipstuff/chisel-template/src/test/resources/hvx_add/hdl/verilog/")),
      ).copy(
      )
    }

  val parsed_entity: ParsedEntity = GloomyVerilogBox.fromVerilog("./src/test/resources/verilog/alu.v", "alu");
  println(parsed_entity.interface.input_signals.length)
  "testing verilog gloomy box" in {
    simulate(new GloomyBox[alu](() => new alu(parsed_entity))) { dut =>
      //input wire clk,
      //input wire reset,
      //input wire enable, // If current block has less threads then block size, some ALUs will be inactive

      //input reg [2:0] core_state,

      //input reg [1:0] decoded_alu_arithmetic_mux,
      //input reg decoded_alu_output_mux,

      //input reg [7:0] rs,
      //input reg [7:0] rt,
      //output wire [7:0] alu_out

      for (i <- 0 to 10) {
        dut.io.elements("enable").poke(1.U);
        dut.io.elements("rs").poke(5.U(32.W));
        dut.io.elements("rt").poke(5.U(32.W))
        dut.io.elements("core_state").poke("b101".U)
        dut.io.elements("decoded_alu_arithmetic_mux").poke(2.U)
        dut.io.clock.step()
        dut.io.elements("clk").asInstanceOf[chisel3.Clock].step()
        println(dut.io.elements("alu_out").peekValue())
      }

    }
  }

  "random alu testing" in {
    simulate(new GloomyBox[alu](() => new alu(parsed_entity))) { dut =>
      val test_generator = new RandomTesting[alu, GloomyBox[alu], Int](dut, 10000, 0, (inputs: Map[String, BigInt], outputs: Map[String, BigInt], state: Int) => {
        val result = AluTesting.calculate_expected_value(inputs, state)
        (outputs("alu_out") == result, outputs("alu_out").toInt)
      })
      assert(test_generator.test())
    }
  }
}