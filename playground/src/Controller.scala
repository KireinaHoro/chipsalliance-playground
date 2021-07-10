package hermes

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._

case class HermesConfig(opcodes: OpcodeSet = OpcodeSet.custom3)

class Hermes(implicit p: Parameters, val config: HermesConfig)
    extends LazyRoCC(opcodes = config.opcodes) {
  val lsu = LazyModule(new LSU)

  override lazy val module = new HermesModuleImp(this)
  override val atlNode = lsu.idNode
}

class HermesModuleImp(outer: Hermes) extends LazyRoCCModuleImp(outer) {
  import outer.config._

  val inputCmd = Queue(io.cmd)

  val counter = RegInit(UInt(32.W), 0.U)
  when(inputCmd.fire && inputCmd.bits.inst.funct === 4.U) {
    counter := counter + 1.U
  }
  inputCmd.ready := true.B

  io.resp.valid := inputCmd.valid
  io.resp.bits.rd := inputCmd.bits.inst.rd
  io.resp.bits.data := counter

  io.busy := inputCmd.valid
  io.interrupt := false.B
}
