import chisel3._
import chipsalliance.rocketchip.config._
import chipyard._
import chipyard.fpga.vcu118._
import chipyard.harness._
import chipyard.iobinders.JTAGChipIO
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class WithBoardJTAG extends OverrideHarnessBinder({
  (_: HasPeripheryDebug, th: HasHarnessSignalReferences, ports: Seq[Data]) =>
    th match { case vcu118th: VCU118FPGATestHarnessImp =>
      ports.map { case sj: JTAGChipIO =>
        vcu118th.vcu118Outer match { case ch: CodesignHarness =>
          val bj = ch.io_jtag_bb.getWrappedValue
          sj.TDI := bj.TDI
          sj.TMS := bj.TMS
          sj.TCK := bj.TCK
          // FIXME: verify if this works
          bj.TDO.data := sj.TDO
          bj.TDO.driven := true.B
        }
      }
    }
})

class CodesignHarness(override implicit val p: Parameters) extends VCU118FPGATestHarness {
  val pmod_j53_is_jtag = p(VCU118ShellPMOD2) == "PMODJ53_JTAG"
  val jl = Some(if (pmod_is_sdio) if (pmod_j53_is_jtag) "PMOD_J53" else "FMC_J2" else "PMOD_J52")

  val jtagOverlay = Overlay(JTAGDebugOverlayKey, new JTAGDebugVCU118ShellPlacer(this, JTAGDebugShellInput(location = jl)))
  val io_jtag_bb = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  override lazy val module = new VCU118FPGATestHarnessImp(this)
}