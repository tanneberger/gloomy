Gloomy
======

Gloomy is a Scala/Chisel library for writing hardware test benches. It provides a unified abstraction for testing both 
**Chisel modules** and **Verilog designs** using the same test infrastructure. The core idea is to wrap designs in 
**GloomyBoxes**, which expose a uniform interface for driving inputs, observing outputs, and applying different testing 
strategies.

## Features

- Unified testing for **Chisel** and **Verilog**
- Simple signal access through named bundles
- Clock handling abstracted by the framework
- Multiple testing strategies:
    - Directed tests
    - Random testing
    - Exhaustive (complete) testing
    - CSV-based testing
- Built on top of ScalaTest and Chisel’s simulation infrastructure

## Requirements

- **JDK 11 or newer**
- **Scala / SBT or Mill**
- **Verilator** (required for Verilog simulation)

## Example

### Chisel Design

```scala
class DelayedAdder(entity: ParsedEntity) extends GloomyVerilogBox(entity) with ExposedInterface {
  override def getBundle: GloomyBundle = super.getBundle
}

class GloomySpec extends AnyFreeSpec with Matchers with ChiselSim {
  val parsed_entity: ParsedEntity = GloomyVerilogBox.fromVerilog("./src/test/resources/verilog/delayed_adder.v", "DelayedAdder");

  "random testing with Verilog" in {
    simulate(new GloomyBox[DelayedAdder](() => new DelayedAdder(parsed_entity))) { dut =>
      val test_generator = new RandomTesting[DelayedAdder, GloomyBox[DelayedAdder], List[BigInt]](dut, 50, List(0, 0, 0, 0), (inputs: Seq[BigInt], outputs: Seq[BigInt], state: List[BigInt]) => {
        val desired_result = state.head.toLong & state(1).toLong % 256
        (desired_result == outputs.head.toLong, inputs.toList)
      })
      assert(test_generator.test())
    }
  }
}
```

### Testing Strategies

Gloomy provides several reusable testing classes:

- Directed testing – manually defined input/output checks
- `RandomTesting` – randomized inputs with user-defined check functions
- `CompleteTesting` – exhaustive testing over an input space
- `CSVTesting` – test vectors loaded from CSV files

Each strategy operates on a GloomyBox and uses the same callback interface for validation.

### Use in your Project

```sbt
libraryDependencies ++= Seq(
  "tud" %% "gloomy" % "0.1.0",
)
```

With **SBT**:
```scala
sbt test
```

With **Mill**:

```scala
./mill test
```

## Project Status

Gloomy is a work in progress as a lightweight testing utility rather than a full verification framework.
