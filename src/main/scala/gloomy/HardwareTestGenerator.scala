package gloomy

import chisel3.util.HasBlackBoxInline
import chisel3.{when, _}

class GloomyTestBox(var interface: GloomyInterface, tests: TestGenerator, size: Int) extends BlackBox with HasBlackBoxInline {
  var inverted_interface = interface.invert()
  val clock_signal = new Signal(name = "clock", width = 1, chisel_type = gloomy.Clock(), direction = gloomy.Input())
  val error = new Signal(name = "error", width = 1, chisel_type = gloomy.Unsigned(), direction = gloomy.Output())
  val error_count = new Signal(name = "error_count", width = 32, chisel_type = gloomy.Unsigned(), direction = gloomy.Output())
  var extended_inverted_interface = new GloomyInterface(input_signals = inverted_interface.input_signals ++ List(clock_signal, error, error_count), output_signals = inverted_interface.output_signals)
  val io: GloomyBundle = IO(extended_inverted_interface.toChiselBundle)

  def translateToInput(signals: List[Signal])  = signals.map((signal) => if(signal.width == 0) { s"${signal.direction.toString} reg ${signal.name}" } else { s"${signal.direction.toString} reg [${signal.width - 1}:0] ${signal.name}" }).mkString(",\n")

  def translateTestCase(index: Int) = {
    val generated_data = tests.data(interface)
    val inputs = generated_data._1
    val outputs = generated_data._2
    s"""
       |$index: begin
       |  ${inputs.map(pair => s"${pair._1} <= ${pair._2};").mkString("\n")}
       |  ${outputs.map(pair => s"error <= (${pair._1} != ${pair._2});").mkString("\n")}
       |end
       |""".stripMargin
  }

  val inlineVerilog =
    s"""module GloomyTestBox(
      |    input wire clock,
      |    output reg error,
      |    output reg[31:0] error_count,
      |    ${translateToInput(inverted_interface.input_signals)},
      |    ${translateToInput(inverted_interface.output_signals)}
      |);
      | reg [31:0] counter = 32'b0;;
      | always @(posedge clock)
      |   begin
      |     case(counter)
      |       ${(0 until size).map(i => translateTestCase(i)).mkString("\n")}
      |       default : assign error = 0;
      |     endcase
      |     error_count <= counter;
      |     counter <= counter + 1;
      |    end
      |endmodule
    """.stripMargin

    println(inlineVerilog)
    setInline("GloomyTestBox.v", inlineVerilog)
}


class HardwareTestSetup[V <: chisel3.experimental.BaseModule with ExposedInterface](tests: TestGenerator, constructor: () => V) extends Module {
  val io = IO(new Bundle {
    val error = Output(Bool())
    val error_count = Output(UInt(32.W))
  });
  val designUnderTest: GloomyBox[V] = Module(new GloomyBox[V](constructor))
  val testGenerator: GloomyTestBox = Module(new GloomyTestBox(designUnderTest.module.getInterface, tests, 10))

  for {signal <- designUnderTest.io.getInputs} {
    signal._2 := testGenerator.io.access(signal._1);
  }

  for {signal <- designUnderTest.io.getOutput} {
    testGenerator.io.access(signal._1) := signal._2
  }

  io.error_count := testGenerator.io.access("error_count")
  io.error := testGenerator.io.access("error");

  designUnderTest.clock := this.clock
  testGenerator.io.access("clock") := this.clock
}