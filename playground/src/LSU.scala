package hermes

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import testchipip._

class DMAReader(implicit p: Parameters, config: HermesConfig)
    extends LazyModule {
  import config._

  val node = TLHelper.makeClientNode("dma-reader", IdRange(0, 1))
  lazy val module = new LazyModuleImp(this) {}
}

class DMAWriter(implicit p: Parameters, config: HermesConfig)
    extends LazyModule {
  import config._

  val node = TLHelper.makeClientNode("dma-writer", IdRange(0, 1))
  lazy val module = new LazyModuleImp(this) {}
}

class LSU(implicit p: Parameters, config: HermesConfig) extends LazyModule {
  import config._

  val idNode = TLIdentityNode()
  val xbarNode = TLXbar()

  val reader = LazyModule(new DMAReader)
  val writer = LazyModule(new DMAWriter)

  xbarNode := reader.node
  xbarNode := writer.node
  idNode := xbarNode

  lazy val module = new LazyModuleImp(this) with HasCoreParameters {}
}
