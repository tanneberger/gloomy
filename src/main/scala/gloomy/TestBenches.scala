package gloomy

import chisel3.simulator.PeekPokeAPI.{testableClock, testableData}
import scala.util.Random
import scala.math.pow

trait TestBench {
  def test(): Boolean;
}

class RandomTesting[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                          gloomyBox: GloomyBox[V],
                                                                          tries: Int,
                                                                          initial_state: X,
                                                                          validation: (Seq[BigInt], Seq[BigInt], X) => (Boolean, X)) extends TestBench {
  val signals: GloomyBundle = gloomyBox.io

  override def test(): Boolean = {
    val rand = new Random()
    var state: X = initial_state
    for (_ <- 0 until tries) {
      var inputs: Seq[BigInt] = Seq()
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = rand.nextInt() % input_signal._2.getWidth
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs = inputs :+ value
      }
      gloomyBox.io.clock.step(1)

      var outputs: Seq[BigInt] = Seq()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs = outputs :+ output_signal._2.peekValue().asBigInt
      }
      val lambda = validation(inputs, outputs, state)
      state = lambda._2
      if (!lambda._1) {
        return false
      }
    }

    true
  }
}


class CompleteTesting[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                             gloomyBox: GloomyBox[V],
                                                                             initial_state: X,
                                                                             validation: (Seq[BigInt], Seq[BigInt], X) => (Boolean, X)) extends TestBench {
  val signals: GloomyBundle = gloomyBox.io

  override def test(): Boolean = {
    var state: X = initial_state;
    val total_width = gloomyBox.io.inputs_with_out_clock.foldLeft(0)((a, b) => a + b._2.getWidth)
    for (i <- 0 until pow(2, total_width).toInt) {
      var inputs: Seq[BigInt] = Seq()
      var current_index: Int = 0
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = (i >> current_index) % pow(2, input_signal._2.getWidth).toInt
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs = inputs :+ value
        current_index = current_index + input_signal._2.getWidth
      }
      gloomyBox.io.clock.step(1)

      var outputs: Seq[BigInt] = Seq()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs = outputs :+ output_signal._2.peekValue().asBigInt
      }
      val lambda = validation(inputs, outputs, state);
      state = lambda._2;
      if (!lambda._1) {
        return false;
      }
    }

    true
  }
}

class CSVTesting[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                               gloomyBox: GloomyBox[V],
                                                                               csv_file_path: String,
                                                                               delimiter: String,
                                                                               initial_state: X,
                                                                               validation: (Seq[BigInt], Seq[BigInt], X) => (Boolean, X)) extends TestBench {
  override def test(): Boolean = {
    val csv_file_lines = scala.io.Source.fromFile(csv_file_path).mkString.split("\n")
    val header = csv_file_lines.head.split(delimiter)
    var header_map: Map[String, Int] = Map()

    var counter = 0;
    for (header_value <- header) {
      header_map += header_value.strip() -> counter
      counter += 1
    }

    // verify that the csv header fits the io of the module

    for (signal <- gloomyBox.io.inputs_with_out_clock) {
      if (!header_map.contains(signal._1)) {
        throw new Exception("map doesn't match: " + signal._1 + "/" + header_map)
      }
    }

    var state: X = initial_state;
    for (i <- 1 until csv_file_lines.length) {
      var inputs: Seq[BigInt] = Seq()
      val lines: List[String] = csv_file_lines.apply(i).split(delimiter).toList
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = lines.apply(header_map(input_signal._1)).strip().toInt;
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs = inputs :+ value
      }

      gloomyBox.io.clock.step(1)

      var outputs: Seq[BigInt] = Seq()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs = outputs :+ output_signal._2.peekValue().asBigInt
      }
      val lambda = validation(inputs, outputs, state);
      state = lambda._2;

      if (!lambda._1) {
        return false;
      }
    }

    true
  }
}
