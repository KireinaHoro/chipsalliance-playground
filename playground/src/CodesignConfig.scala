import chipsalliance.rocketchip.config._
import chipyard.config.AbstractConfig
import chipyard.fpga.vcu118._
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.diplomacy.DTSTimebase
import freechips.rocketchip.subsystem._
import gemmini.DefaultGemminiConfig
import sifive.fpgashells.shell.xilinx._
import testchipip.SerialTLKey

import sys.process._

// playground has bootrom in a different location
case object BootROMPathKey extends Field[String]("dependencies/chipyard/fpga/src/main/resources/vcu118/sdboot")

// this duplicates much from WithVCU118Tweaks, because WithSystemModifications
// directly built bootrom inside the Config snippet
// this also prevents removal of debug module
class CodesignVCU118 extends Config(
  new WithFPGAFrequency(50) ++
  new WithVCU118ShellPMODSDIO ++
  new WithVCU118ShellPMOD2JTAG ++
  new WithBoardJTAG ++
  new WithJtagDTM ++
  new WithDebugSBA ++
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  new WithUARTIOPassthrough ++
  new WithSPIIOPassthrough ++
  new WithTLIOPassthrough ++
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithCodesignModifications ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class WithCodesignModifications extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(site(FPGAFrequencyKey).toInt*1000000))
  case MemoryBusKey => up(MemoryBusKey, site).copy(dtsFrequency = Some(site(FPGAFrequencyKey).toInt*1000000))
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
  case DTSTimebase => BigInt(1000000)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val bp = up(BootROMPathKey)
    val freqMHz = site(FPGAFrequencyKey).toInt * 1000000
    val make = s"make -C $bp PBUS_CLK=${freqMHz} bin"
    println(f"Overriding bootrom path to $bp")
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"$bp/build/sdboot.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU118DDRSize)))) // set extmem to DDR size
  case SerialTLKey => None // remove serialized tl port
})

class CodesignRocketConfig extends Config(
  new DefaultGemminiConfig ++
  new WithNBreakpoints(4) ++
  new WithNBigCores(1) ++
  new AbstractConfig
)

class CodesignVCU118Config extends Config(
  new CodesignVCU118 ++
  new CodesignRocketConfig
)