import barstools.tapeout.transforms.GenerateTopAndHarness
import chipyard._
import firrtl.options._
import firrtl.stage.FirrtlFileAnnotation
import freechips.rocketchip.stage._

import java.nio.file._
import java.io._
import scala.reflect.io.Directory

object Runner extends App {
  val build = Paths.get("out/generated-src")
  if (Files.exists(build)) {
    val dir = new Directory(new File(build.toString))
    dir.deleteRecursively()
  }
  Files.createDirectories(build)

  val topClass = classOf[CodesignHarness]
  val configClass = classOf[CodesignVCU118Config]
  val outputBaseName = f"${topClass.getName}.${configClass.getName}"

  Generator.stage.transform(Seq(
    new TargetDirAnnotation(build.toString),
    new TopModuleAnnotation(topClass),
    new ConfigsAnnotation(Seq(configClass.getName)),
    new OutputBaseNameAnnotation(outputBaseName),
  ))
}