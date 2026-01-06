package gloomy

import chisel3._
import chisel3.util._
import cats.syntax.either._
import io.circe.yaml
import io.circe.generic.auto._
import io.circe.{Error, yaml}

import java.nio.file.{Files, Paths}
import scala.collection.immutable.SeqMap
import scala.util.matching.Regex


trait ExposedInterface {
  def getBundle: GloomyBundle
  def getInterface: GloomyInterface
  def gloomyClock: chisel3.Clock
}

abstract class ChiselType {
  def toChiselType: Int => Data
}

object ChiselType {
  def fromString(value: String): ChiselType = {
    value match {
      case "signed" => gloomy.Signed()
      case "unsigned" => gloomy.Unsigned()
      case "clock" => gloomy.Clock()
    }
  }
}

case class Signed() extends ChiselType {
  override def toChiselType: Int => Data = {
    (width: Int) => chisel3.SInt.apply(width.W)
  }
}
case class Unsigned() extends ChiselType {
  override def toChiselType: Int => Data = {
    (width: Int) => chisel3.UInt.apply(width.W)
  }
}
case class Clock() extends ChiselType {
  override def toChiselType: Int => Data = {
    (_: Int) => chisel3.Clock()
  }
}

abstract class Direction
object Direction {
  def fromString(value: String): Direction = {
    value match {
      case "input" => Input()
      case "output" => Output()
    }
  }
}
case class Input() extends Direction {}
case class Output() extends Direction {}


class Signal(val name: String, val chisel_type: ChiselType, val width: Int, val direction: Direction)

object Signal {
  def apply(name: String, chisel_type: ChiselType, width: Int, direction: Direction) = new Signal(name, chisel_type, width, direction)

  def toChiselData(value: Signal): Data = {
    val chisel_data_type: ChiselType = value.chisel_type;
    val direction_type: Direction  = value.direction;

    direction_type match {
      case Input() => chisel3.Input(chisel_data_type.toChiselType(value.width))
      case Output() => chisel3.Output(chisel_data_type.toChiselType(value.width))
    }
  }
  def fromChiselData(name: String, value: Data): Signal = {
    // this is a hack via Scala's reflection to get the private value out of the Chisel Data
    val field = classOf[Data].getDeclaredField("_specifiedDirection")
    field.setAccessible(true)
    val gloomyDirection = field.get(value) match {
      case 0 => throw new Exception("syntax is not valid");
      case 1 => Output()
      case 2 => Input()
      case 3 => throw new Exception("syntax is not valid");
    }
    val gloomyType: ChiselType = value.typeName.split("<")(0) match {
      case "Clock" => Clock()
      case "UInt" => Unsigned()
      case "SInt" => Signed()
    }

    new Signal(name = name, chisel_type = gloomyType, width = value.getWidth ,direction = gloomyDirection)
  }
}


class GloomyBundle(input_elements: Map[String, Data], output_elements: Map[String, Data]) extends Record {
  override def elements: SeqMap[String, Data] = SeqMap.from(input_elements ++ output_elements)
  val inputs_with_out_clock: SeqMap[String, Data] = SeqMap.from(input_elements.filter(pair => pair._1 != "clock" ))
  val clock: chisel3.Clock = input_elements.getOrElse("clock",input_elements.get("clk").orNull).asInstanceOf[chisel3.Clock]
  val outputs: SeqMap[String, Data] = SeqMap.from(output_elements)

  def getInputs: List[(String, Data)] = input_elements.toList
  def getOutput: List[(String, Data)] = output_elements.toList

  def access(key: String): Data = {
    elements(key)
  }
}

object GloomyInterface {
  def createFromChisel(io: Bundle): GloomyInterface = {
    var inputs: List[Signal] = List()
    var outputs: List[Signal] = List()
    val elements = io.elements.map(value => {
      Signal.fromChiselData(value._1, value._2)
    })

    elements.foreach(value => {
      value.direction match {
        case Input() => inputs = inputs :+ value
        case Output() => outputs = outputs :+ value
      }
    })

    new GloomyInterface(input_signals = inputs, output_signals = outputs)
  }
}

class GloomyInterface(val input_signals: List[Signal], val output_signals: List[Signal]) {
  def toChiselBundle: GloomyBundle = {
    val chisel_input_signals: Map[String, Data] = input_signals.map(x => {x.name -> Signal.toChiselData(x)}).toMap
    val chisel_output_signals: Map[String, Data] = output_signals.map(x => {x.name -> Signal.toChiselData(x)}).toMap
    new GloomyBundle(chisel_input_signals, chisel_output_signals)
  }
}

class GloomyVerilogBox(entity: ParsedEntity) extends BlackBox with HasBlackBoxInline with ExposedInterface {
  var io: GloomyBundle = IO(entity.interface.toChiselBundle)
  setInline(entity.getFileName, entity.getVerilog)

  override def getBundle: GloomyBundle = this.io
  override def getInterface: GloomyInterface = entity.interface
  override def gloomyClock: chisel3.Clock = this.io.clock
}

class GloomyBox[V <: chisel3.experimental.BaseModule with ExposedInterface](constructor: () => V) extends Module {
  private val module: V = Module(constructor.apply())
  val io: GloomyBundle = IO(module.getInterface.toChiselBundle)

  for {signal <- module.getBundle.getInputs} {
    signal._2 := io.access(signal._1);
  }

  for {signal <- module.getBundle.getOutput} {
    io.access(signal._1)  := signal._2
  }
  module.gloomyClock := this.io.clock
}


object GloomyVerilogBox {
  def fromConfig(config_file: String, entity_name: String): ParsedEntity = {
    if (!Files.exists(Paths.get(config_file))) {
      throw new Exception("cannot find specified gloomy config file")
    }

    val file_content = scala.io.Source.fromFile(config_file).mkString
    val rawInterfaceConfig = yaml.parser.parse(file_content)
    val interfaceConfig: gloomy.Config = rawInterfaceConfig.leftMap(err => err: Error).flatMap(_.as[gloomy.Config]).valueOr(throw _);
    val parsed_entity = interfaceConfig.entities.find(p => p.entity == entity_name) match {
      case None => throw new Exception("cannot find specified gloomy entity inside config file")
      case Some(raw_entity: ConfigEntity) => raw_entity.toEntity(interfaceConfig.interface_definitions)
    }

    parsed_entity
  }

  def fromVerilog(verilog_file: String, module_name: String): ParsedEntity = {
    if (!Files.exists(Paths.get(verilog_file))) {
      throw new Exception("cannot find specified verilog file")
    }

    val file_content = scala.io.Source.fromFile(verilog_file).mkString

    if (!file_content.contains("module " + module_name)) {
      throw new Exception("cannot find module inside verilog file")
    };

    val modules = file_content.split("module");

    for (module <- modules) {
      if (module.contains(module_name)) {
        var temp = module.split("""\(""");
        val body = temp.apply(1);
        temp = temp.apply(1).split("""\)""");

        val statements = body.split(";");
        val keyValPattern: Regex = """(input|output|reg)(\[([0-9]+):([0-9]+)\])?\s([0-9a-zA-Z-_]+)""".r
        var input_signals: List[Signal] = List()
        var output_signals: List[Signal] = List()

        for (statement <- statements) {
          val port  = statement.replaceAll("\n", " ").trim;
          for (patternMatch <- keyValPattern.findAllMatchIn(port)) {

            if (patternMatch.group(1) != "reg") {
              val chisel_type = if (patternMatch.group(5) == "clock" || patternMatch.group(5) == "clk") {
                "clock"
              } else {
                "unsigned"
              };

              val signal = if (patternMatch.group(2) != null) {
                Signal(name=patternMatch.group(5), direction = Direction.fromString(patternMatch.group(1)), width = 1 + patternMatch.group(3).toInt - patternMatch.group(4).toInt, chisel_type = ChiselType.fromString(chisel_type))
              } else {
                Signal(name=patternMatch.group(5), direction = Direction.fromString(patternMatch.group(1)), width = 1, chisel_type = ChiselType.fromString(chisel_type))
              }

              signal.direction match {
                case Input() => input_signals = input_signals.:: (signal)
                case Output() => output_signals = output_signals.::(signal)
              }
            }
          }
        }
        return new ParsedEntity(
          name = module_name,
          verilog_file = verilog_file, 
          interface = new GloomyInterface(input_signals = input_signals, output_signals = output_signals)
        )
      }
    }
    throw new Exception("requested entity not found inside the verilog file!")
  }
}
