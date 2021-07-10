package hermes

import utest._
import sanitytests.rocketchip._

object VerilatorTest extends TestSuite {
  val outputDir = os.pwd / "out" / "hermes-verilator"
  os.remove.all(outputDir)
  os.makeDir(outputDir)
  val tests = Tests {
    test("build hermes emulator") {
      val harness = classOf[freechips.rocketchip.system.TestHarness]
      val configs = Seq(classOf[GenerateHermes], classOf[TestBootRom], classOf[freechips.rocketchip.system.DefaultConfig])
      TestHarness(harness, configs, Some(outputDir)).emulator
    }
  }
}