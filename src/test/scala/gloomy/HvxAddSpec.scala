package gloomy.test

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.util.circt.PlusArgsValue
import gloomy._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import svsim.CommonCompilationSettings
import svsim.PlusArg


class hvx_add(entity: ParsedEntity) extends GloomyVerilogBox(entity) with ExposedInterface {
}


class HvxAddSpec extends AnyFreeSpec with Matchers with ChiselSim {
  override implicit def commonSettingsModifications: svsim.CommonSettingsModifications =
    (original: CommonCompilationSettings) => {
      original.copy(
        includeDirs = Some(original.includeDirs.get ++ Seq("/home/tanneberger/workspace/uni/chipstuff/chisel-template/src/test/resources/hvx_add/hdl/verilog/",
        "/home/tanneberger/workspace/uni/chipstuff/chisel-template/src/test/resources/hvx_add/hdl/verilog/")),
      ).copy(

      )
    }

  val parsed_entity: ParsedEntity = GloomyVerilogBox.fromVerilog("./src/test/resources/hvx_add/hdl/verilog/hvx_add.v", "hvx_add");
  println(parsed_entity.interface.input_signals.length)
  "testing verilog gloomy box" in {
    simulate(new GloomyBox[hvx_add](() => new hvx_add(parsed_entity))) { dut =>
      // input  [31:0] src1_TDATA;
      // input  [31:0] src2_TDATA;
      // output  [31:0] dst_TDATA;
      // output  [0:0] dst_TLAST;
      // input   ap_clk;
      // input   ap_rst_n;
      // input   src1_TVALID;
      // output   src1_TREADY;
      // input   src2_TVALID;
      // output   src2_TREADY;
      // output   dst_TVALID;
      // input   dst_TREADY;
      dut.io.elements("src1_TDATA").poke(4.U(32.W));
      dut.io.elements("src2_TDATA").poke(5.U(32.W));
      dut.io.elements("src1_TVALID").poke(true)
      dut.io.elements("src2_TVALID").poke(true)
      dut.io.clock.step()
      println(dut.io.elements("dst_TDATA").peekValue())
      println(dut.io.elements("dst_TVALID").peekValue())
      assert(dut.io.elements("out").peekValue().asBigInt == 4);
    }
  }
}