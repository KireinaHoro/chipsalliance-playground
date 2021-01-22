package playground

import chisel3._
import chipsalliance.rocketchip.config.{Parameters, View}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink.{TLFuzzer, TLToAXI4}
import freechips.rocketchip.amba.axi4.AXI4RAM

class LazyMem()(implicit p: Parameters) extends LazyModule {
  val addressSet = AddressSet(0x38000000L, 0x0000ffffL)
  val fuzz = LazyModule(new TLFuzzer(nOperations = 10, overrideAddress = Some(addressSet), inFlight = 1))

  val ram = AXI4RAM(AddressSet(0x0L, 0x7ffffffL))
  ram := TLToAXI4() := fuzz.node
  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle() {})
    val array = Module(new SRAMWrapper(
      "Dcache_Data",
      Bits(6.W),
      set = 2,
      way = 1,
      shouldReset = false,
      holdRead = false,
      singlePort = true
    ))
    array.io := DontCare
    dontTouch(array.io)
    fuzz.module.io <> DontCare
    dontTouch(fuzz.module.io)
  }
}

class SimpleMem extends MultiIOModule {
  val array = Module(new SRAMWrapper(
    "Dcache_Data",
    Bits(6.W),
    set = 2,
    way = 1,
    shouldReset = false,
    holdRead = false,
    singlePort = true
  ))
  array.io := DontCare
  dontTouch(array.io)
}

object LazyTest extends App {
  val config: (View, View, View) => PartialFunction[Any, Any] = (site: View, here: View, up: View) => {
    case MonitorsEnabled => true
  }
  implicit val p: Parameters = Parameters(config)

  (new chisel3.stage.ChiselStage).execute(Array(
    "-frsq", "-c:LazyMem:-o:LazyMem.mem.conf",
    "-ll", "debug",
    "-X", "verilog"
  ), Seq(
    chisel3.stage.ChiselGeneratorAnnotation(() => LazyModule(new LazyMem).module)
  ))
}

object SimpleTest extends App {
  (new chisel3.stage.ChiselStage).execute(Array(
    "-frsq", "-c:SimpleMem:-o:SimpleMem.mem.conf",
    "-X", "verilog"
  ), Seq(
    chisel3.stage.ChiselGeneratorAnnotation(() => new SimpleMem())
  ))
}