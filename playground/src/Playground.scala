import barstools.macros.MacroCompiler
import barstools.tapeout.transforms.GenerateTopAndHarness
import barstools.tapeout.transforms.stage._
import chipyard._
import firrtl.options._
import firrtl.stage._
import firrtl.passes.memlib._
import freechips.rocketchip.stage._
import utilities.{GenerateSimConfig, GenerateSimFiles}

import java.io.{File, PrintWriter}
import java.nio.file._
import java.time.LocalDateTime
import scala.reflect.io.Directory
import scala.io.Source

object Runner extends App {
  def info(msg: Any) = println(Console.GREEN + msg + Console.RESET)

  def using[A <: { def close(): Unit }, B](r: A)(f: A => B): B = try { f(r) } finally { r.close() }

  val builds = Paths.get("out", "history-builds")
  new Directory(builds.toFile).createDirectory()

  val thisBuild = Paths.get(builds, LocalDateTime.now.toString)
  new Directory(thisBuild.toFile).createDirectory(failIfExists = true)

  val build = Paths.get("out", "generated-src")
  Files.deleteIfExists(build)
  Files.createSymbolicLink(build, Paths.get("../", thisBuild))

  val topClass = classOf[CodesignHarness]
  val configClass = classOf[CodesignVCU118Config]
  val synTopClass = classOf[ChipTop]
  val outputBaseName = f"${topClass.getName}.${configClass.getName}"

  implicit def pathToString(p: Path): String = p.toString
  implicit def stringToPath(s: String): Path = Paths.get(s)
  def bp(file: String) = Paths.get(build, file)
  def dp(dep: String, file: String) = Paths.get("dependencies", dep, file)
  def fn(suffix: String) = f"$outputBaseName.$suffix"
  def p(suffix: String) = bp(fn(suffix))

  info("Generating simulation files...")
  GenerateSimFiles.writeFiles(GenerateSimConfig(
    targetDir = build,
    simulator = None,
  ))

  val bootromDir = "bootrom"
  Files.move(bootromDir, Paths.get(build, bootromDir))

  info("Running generator...")
  Generator.stage.transform(Seq(
    new TargetDirAnnotation(build), // --target-dir
    new OutputBaseNameAnnotation(outputBaseName), // --name
    new TopModuleAnnotation(topClass), // --top-module
    new ConfigsAnnotation(Seq(configClass.getName)), // --legacy-configs
  ))

  val gemminiConf = "gemmini_params.h"
  Files.move(gemminiConf, Paths.get(build, gemminiConf))

  info("Generating top and harness...")

  val harnessBBList = bp("firrtl_black_box_resource_files.harness.f")
  val topBBList = bp("firrtl_black_box_resource_files.top.f")

  GenerateTopAndHarness.stage.transform(Seq(
    new OutputFileAnnotation(fn("top.v")), // -o
    new HarnessOutputAnnotation(fn("harness.v")), // -tho
    new FirrtlFileAnnotation(p("fir")), // -i
    new SynTopAnnotation(synTopClass.getSimpleName), // --syn-top
    new HarnessTopAnnotation(topClass.getName), // --harness-top
    new InputAnnotationFileAnnotation(p("anno.json")), // -faf
    new TopAnnoOutAnnotation(p("top.anno.json")), // -tsaof
    new TopDotfOutAnnotation(topBBList), // -tdf
    new TopFirAnnotation(p("top.fir")), // -tsf
    new HarnessAnnoOutAnnotation(p("harness.anno.json")), // -thaof
    new HarnessDotfOutAnnotation(harnessBBList), // -hdf
    new HarnessFirAnnotation(p("harness.fir")), // -thf
    new HarnessConfAnnotation(p("harness.mems.conf")), // -thconf
    InferReadWriteAnnotation,
    RunFirrtlTransformAnnotation(new InferReadWrite),
    ReplSeqMemAnnotation("", p("top.mems.conf")),
    RunFirrtlTransformAnnotation(new ReplSeqMem),
    new TargetDirAnnotation(build), // -td
  ))

  Seq("top", "harness") foreach { m: String =>
    info(f"Running macro compiler for $m...")
    // gave up; pass commandline directly
    MacroCompiler.run(Seq[String](
      "-n", p(m + ".mems.conf"),
      "-v", p(m + ".mems.v"),
      "-f", p(m + ".mems.fir"),
      "--mode", "synflops",
    ).toList)
  }

  info("Generating file lists for synthesis...")
  val files = Seq(
    p("top.v"), p("top.mems.v"),
    p("harness.v"), p("harness.mems.v"),
    dp("sifive-blocks", "vsrc/SRLatch.v"),
    dp("fpga-shells", "xilinx/common/vsrc/PowerOnResetFPGAOnly.v"),
  ).map(_.toAbsolutePath) ++ (Seq(topBBList, harnessBBList, bp("sim_files.f")) flatMap { fl =>
    using(Source.fromFile(fl))(_.getLines.toList)
  }).sorted

  val vsrcsList = p("vsrcs.f")
  using(new PrintWriter(new File(vsrcsList))) {
    _.write(files.mkString("\n"))
  }

  info("Writing vivado command script...")
  using(new PrintWriter(new File(bp("run_impl.sh")))) {
    _.write(
      f"""
        |#!/usr/bin/env bash
        |
        |cd $build
        |
        |vivado -nojournal -mode batch \\
        |  -source ${dp("fpga-shells", "xilinx/common/tcl/vivado.tcl").toAbsolutePath} \\
        |  -tclargs -top-module ${topClass.getSimpleName} \\
        |    -F ${vsrcsList.toAbsolutePath} \\
        |    -ip-vivado-tcls "$$(ls ${bp("*.vivado.tcl").toAbsolutePath})" \\
        |    -board vcu118
        |""".stripMargin)
  }

  info("All done.")
}