package gloomy

import chisel3.simulator.PeekPokeAPI.{testableClock, testableData}

import scala.util.Random
import scala.math.{abs, pow}

trait TestBench {
  def test(): Boolean;
}

trait TestGenerator {
  def data(interface: GloomyInterface): (Map[String, BigInt], Map[String, BigInt])
}

object TestBench {
  def compareOutputs(expected: Map[String, BigInt], got: Map[String, BigInt]): Boolean = {
    for (pair <- expected) {
      if (!expected.contains(pair._1) || got(pair._1) != pair._2) {
        return false
      }
    }
    true
  }
}


class RandomTestGenerator[X](
                                                                                                       initial_state: X,
                                                                                                       validation: (Map[String, BigInt], X) => (Map[String, BigInt], X)) extends TestGenerator {
  var previous_output = Map()
  var state: X = initial_state

  override def data(interface: GloomyInterface): (Map[String, BigInt], Map[String, BigInt]) = {
    val rand = new Random()

    var inputs: Map[String, BigInt] = Map()
    for (input_signal <- interface.input_signals_without_clock) {
      val value = abs(rand.nextInt()) % pow(2, input_signal.width).toInt
      inputs += input_signal.name -> value
    }

    val lambda = validation(inputs, state)
    state = lambda._2

    println(lambda)

    (inputs, lambda._1)
  }
}

class RandomTestBench[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                          gloomyBox: GloomyBox[V],
                                                                          tries: Int,
                                                                          initial_state: X,
                                                                          validation: (Map[String, BigInt], X) => (Map[String, BigInt], X)) extends TestBench {
  val signals: GloomyBundle = gloomyBox.io

  override def test(): Boolean = {
    val rand = new Random()
    var state: X = initial_state
    for (i <- 0 until tries) {
      var inputs: Map[String, BigInt] = Map()
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = abs(rand.nextInt()) % pow(2, input_signal._2.getWidth).toInt
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs += input_signal._1 -> value
      }
      gloomyBox.clock.step(1)

      var outputs: Map[String, BigInt] = Map()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs += output_signal._1 -> output_signal._2.peekValue().asBigInt
      }
      val lambda = validation(inputs, state)
      state = lambda._2

      if (!TestBench.compareOutputs(lambda._1, outputs) && i != 0) {
        println(i, inputs, lambda, outputs)
        return false
      }
    }

    true
  }

}


class CompleteTestBench[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                             gloomyBox: GloomyBox[V],
                                                                             initial_state: X,
                                                                             validation: (Map[String, BigInt], X) => (Map[String, BigInt], X)) extends TestBench {
  val signals: GloomyBundle = gloomyBox.io

  override def test(): Boolean = {
    var state: X = initial_state;
    val total_width = gloomyBox.io.inputs_with_out_clock.foldLeft(0)((a, b) => a + b._2.getWidth)
    for (i <- 0 until pow(2, total_width).toInt) {
      var inputs: Map[String, BigInt] = Map()
      var current_index: Int = 0
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = (i >> current_index) % pow(2, input_signal._2.getWidth).toInt
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs += input_signal._1 -> value
        current_index = current_index + input_signal._2.getWidth
      }
      gloomyBox.clock.step(1)

      var outputs: Map[String, BigInt] = Map()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs += output_signal._1 -> output_signal._2.peekValue().asBigInt
      }
      val lambda = validation(inputs, state);
      state = lambda._2;

      if (!TestBench.compareOutputs(lambda._1, outputs) && i != 0) {
        println(i, lambda, outputs)
        return false;
      }
    }

    true
  }
}

class CSVTestBench[V <: chisel3.experimental.BaseModule with ExposedInterface, U <: GloomyBox[V], X](
                                                                               gloomyBox: GloomyBox[V],
                                                                               csv_file_path: String,
                                                                               delimiter: String,
                                                                               initial_state: X,
                                                                               validation: (Map[String, BigInt], X) => (Map[String, BigInt], X)) extends TestBench {
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

      var inputs: Map[String, BigInt] = Map()
      val lines: List[String] = csv_file_lines.apply(i).split(delimiter).toList
      for (input_signal <- gloomyBox.io.inputs_with_out_clock) {
        val value = lines.apply(header_map(input_signal._1)).strip().toInt;
        gloomyBox.io.access(input_signal._1).poke(value)
        inputs += input_signal._1 -> value
      }

      gloomyBox.clock.step(1)

      var outputs: Map[String, BigInt] = Map()
      for (output_signal <- gloomyBox.io.outputs) {
        outputs += output_signal._1 -> output_signal._2.peekValue().asBigInt
      }

      val lambda = validation(inputs, state);
      state = lambda._2;

      if (!TestBench.compareOutputs(lambda._1, outputs) && i != 1) {
        println(i, lambda, outputs)
        return false;
      }
    }

    true
  }
}
