import barstools.tapeout.transforms.GenerateTopAndHarness
import barstools.tapeout.transforms.stage._
import chipyard._
import firrtl.options._
import firrtl.stage._
import firrtl.passes.memlib._
import freechips.rocketchip.stage._
import utilities.{GenerateSimConfig, GenerateSimFiles}

import java.nio.file._
import scala.reflect.io.Directory

object Runner extends App {
  def info(msg: Any) = println(Console.GREEN + msg + Console.RESET)

  val build = Paths.get("out", "generated-src")
  if (Files.exists(build)) {
    val dir = new Directory(build.toFile)
    dir.deleteRecursively()
  }
  Files.createDirectories(build)

  val topClass = classOf[CodesignHarness]
  val configClass = classOf[CodesignVCU118Config]
  val synTopClass = classOf[ChipTop]
  val outputBaseName = f"${topClass.getName}.${configClass.getName}"

  def bp(file: String) = Paths.get(build.toString, file).toString
  def fn(suffix: String) = f"$outputBaseName.$suffix"
  def p(suffix: String) = bp(fn(suffix))

  info("Generating simulation files...")
  GenerateSimFiles.writeFiles(GenerateSimConfig(
    targetDir = build.toString,
    simulator = None,
  ))

  val bootromDir = "bootrom"
  Files.move(Paths.get(bootromDir), Paths.get(build.toString, bootromDir))

  info("Running generator...")
  Generator.stage.transform(Seq(
    new TargetDirAnnotation(build.toString), // --target-dir
    new OutputBaseNameAnnotation(outputBaseName), // --name
    new TopModuleAnnotation(topClass), // --top-module
    new ConfigsAnnotation(Seq(configClass.getName)), // --legacy-configs
  ))

  val gemminiConf = "gemmini_params.h"
  Files.move(Paths.get(gemminiConf), Paths.get(build.toString, gemminiConf))

  info("Generating top and harness...")
  GenerateTopAndHarness.stage.transform(Seq(
    new OutputFileAnnotation(fn("top.v")), // -o
    new HarnessOutputAnnotation(fn("harness.v")), // -tho
    new FirrtlFileAnnotation(p("fir")), // -i
    new SynTopAnnotation(synTopClass.getSimpleName), // --syn-top
    new HarnessTopAnnotation(topClass.getName), // --harness-top
    new InputAnnotationFileAnnotation(p("anno.json")), // -faf
    new TopAnnoOutAnnotation(p("top.anno.json")), // -tsaof
    new TopDotfOutAnnotation(bp("firrtl_black_box_resource_files.top.f")), // -tdf
    new TopFirAnnotation(p("top.fir")), // -tsf
    new HarnessAnnoOutAnnotation(p("harness.anno.json")), // -thaof
    new HarnessDotfOutAnnotation(bp("firrtl_black_box_resource_files.harness.f")), // -hdf
    new HarnessFirAnnotation(p("harness.fir")), // -thf
    new HarnessConfAnnotation(p("harness.mems.conf")), // -thconf
    InferReadWriteAnnotation,
    RunFirrtlTransformAnnotation(new InferReadWrite),
    ReplSeqMemAnnotation("", p("top.mems.conf")),
    RunFirrtlTransformAnnotation(new ReplSeqMem),
    new TargetDirAnnotation(build.toString), // -td
  ))

  info("All done.")
}