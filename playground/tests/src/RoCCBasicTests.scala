package hermes

import utest._
import sanitytests.rocketchip._

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._

class Custom3Counter(implicit p: Parameters)
    extends LazyRoCC(opcodes = OpcodeSet.custom3) {
  override lazy val module = new LazyRoCCModuleImp(this) {
    val inputCmd = Queue(io.cmd)
    val counter = RegInit(UInt(p(XLen).W), 0.U)
    when(inputCmd.fire && inputCmd.bits.inst.funct === 4.U) {
      counter := counter + 1.U
    }
    inputCmd.ready := true.B

    io.resp.valid := inputCmd.fire
    io.resp.bits.rd := inputCmd.bits.inst.rd
    io.resp.bits.data := counter

    io.busy := inputCmd.valid
    io.interrupt := false.B
  }
}
object Custom3Counter {
  class GenCfg
      extends Config((site, here, up) => {
        case BuildRoCC =>
          up(BuildRoCC) :+ { implicit p: Parameters =>
            val rocc = LazyModule(new Custom3Counter)
            rocc
          }
      })
}

object RoCCBasicTests extends TestSuite {
  val harness = classOf[freechips.rocketchip.system.TestHarness]
  val configs = Seq(
    classOf[TestBootRom],
    classOf[freechips.rocketchip.system.DefaultConfig]
  )
  def outputDir(implicit tp: utest.framework.TestPath) = {
    val o = os.pwd / "out" / tp.value
      .mkString("_")
      .replaceAll(" +", "-")
    os.remove.all(o)
    os.makeDir(o)
    o
  }
  val tests = Tests {
    test("custom3 counter") {
      TestHarness(
        harness,
        classOf[Custom3Counter.GenCfg] +: configs,
        Some(outputDir)
      ).emulator
    }
  }
}
