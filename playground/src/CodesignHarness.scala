import chisel3._
import chipsalliance.rocketchip.config._
import chipyard._
import chipyard.fpga.vcu118._
import chipyard.harness._
import chipyard.iobinders.{GetSystemParameters, OverrideLazyIOBinder}
import chisel3.experimental.IO
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, HasPeripheryDebug, HasPeripheryDebugModuleImp, JtagDTMKey}
import freechips.rocketchip.diplomacy.InModuleBody
import freechips.rocketchip.jtag.JTAGIO
import freechips.rocketchip.prci.{ClockSinkNode, ClockSinkParameters}
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.util.PSDTestMode
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class WithJTAGIOPassthrough extends OverrideLazyIOBinder({
  (system: HasPeripheryDebug) => {
    implicit val p = GetSystemParameters(system)
    val tlbus = system.asInstanceOf[BaseSubsystem].locateTLBusWrapper(p(ExportDebug).slaveWhere)
    val clockSinkNode = system.debugOpt.map(_ => ClockSinkNode(Seq(ClockSinkParameters())))
    clockSinkNode.map(_ := tlbus.fixedClockNode)
    def clockBundle = clockSinkNode.get.in.head._1

    InModuleBody { system.asInstanceOf[BaseSubsystem].module match { case system: HasPeripheryDebugModuleImp => {
      system.psd.psd.foreach(_ <> 0.U.asTypeOf(new PSDTestMode))
      system.resetctrl.foreach { rcio => rcio.hartIsInReset.map(_ := clockBundle.reset.asBool) }
      system.debug.foreach { debug =>
        debug.extTrigger.foreach { t =>
          t.in.req := false.B
          t.out.ack := t.out.req
        }
        debug.disableDebug.foreach(_ := false.B)
        debug.systemjtag.foreach { j =>
          j.reset := clockBundle.reset
          j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
          j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
          j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
        }
        Debug.connectDebugClockAndReset(Some(debug), clockBundle.clock)
      }

      val sj = system.debug.get.systemjtag.get.jtag
      val io_jtag_pins_temp = IO(Flipped(sj.cloneType)).suggestName("system_jtag")
      io_jtag_pins_temp <> sj
      (Seq(io_jtag_pins_temp), Nil)
    }}}
  }
})

class WithBoardJTAG extends OverrideHarnessBinder({
  (_: HasPeripheryDebug, th: HasHarnessSignalReferences, ports: Seq[Data]) =>
    th match { case vcu118th: VCU118FPGATestHarnessImp =>
      ports.map { case sj: JTAGIO =>
        vcu118th.vcu118Outer match { case ch: CodesignHarness =>
          val bj = ch.io_jtag_bb.getWrappedValue
          bj.TDO := sj.TDO
          sj.TDI := bj.TDI
          sj.TMS := bj.TMS
          sj.TCK := bj.TCK
        }
      }
    }
})

class CodesignHarness(override implicit val p: Parameters) extends VCU118FPGATestHarness {
  val pmod_j53_is_jtag = p(VCU118ShellPMOD2) == "PMODJ53_JTAG"
  val jl = Some(if (pmod_is_sdio) if (pmod_j53_is_jtag) "PMOD_J53" else "FMC_J2" else "PMOD_J52")

  val io_jtag_bb = new JTAGDebugVCU118ShellPlacer(this, JTAGDebugShellInput(location = jl)).place(JTAGDebugDesignInput()).overlayOutput.jtag

  override lazy val module = new VCU118FPGATestHarnessImp(this)
}