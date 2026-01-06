package gloomy

class ParsedEntity(val name: String, val verilog_file: String, val interface: GloomyInterface) {
  private val verilog_source = scala.io.Source.fromFile(verilog_file).mkString

  private def usingRegex(camelCaseString: String): String = {
    val regex = "([A-Z])".r
    regex.replaceAllIn(
      camelCaseString,
      m => s"_${m.group(1).toLowerCase}"
    )
  }

  def getVerilog: String = verilog_source
  def getFileName: String = usingRegex(name) + ".v"
}

case class ConfigSignal(name: String, data_type: String, width: Int, direction: String) {
  def toSignal: Signal = {
    new Signal(name=name, chisel_type = ChiselType.fromString(data_type), width=width, direction=Direction.fromString(direction))
  }
}
case class ConfigInterfaceDefinition(interface: String, parent: String, signals: List[ConfigSignal])
case class ConfigEntity(entity: String, verilog_source: String, interfaces: List[String], signals: List[ConfigSignal]) {
  def toEntity(interfaces: Seq[ConfigInterfaceDefinition]): ParsedEntity = {
    val config_signals: List[ConfigSignal] = (interfaces.map(interface => {
      val index = interfaces.indexWhere(p => p.interface == interface.interface);
      interfaces(index).signals
    }).flatMap(_.toList) ++ signals).toList
    var config_input_signals: List[ConfigSignal] = List()
    var config_output_signals: List[ConfigSignal] = List()
    for {signal: ConfigSignal <- config_signals} {
      //TODO: do inversion of direction here
      if (signal.direction == "input") {
        config_input_signals = config_input_signals ++ Seq(signal)
      } else if (signal.direction == "output") {
        config_output_signals = config_output_signals ++ Seq(signal)
        } else {
        //TODO: throw
      }
    }

    val input_signals = config_input_signals.map(value => {
      value.toSignal
    })

    val output_signals = config_output_signals.map(value => {
      value.toSignal
    })

    new ParsedEntity(entity, verilog_source, new GloomyInterface(input_signals, output_signals))
  }
}
case class Config(interface_definitions: List[ConfigInterfaceDefinition], entities: List[ConfigEntity])


