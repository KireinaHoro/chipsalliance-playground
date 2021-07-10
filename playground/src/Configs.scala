package hermes

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._

class GenerateHermes
    extends Config((site, here, up) => {
      case BuildRoCC =>
        up(BuildRoCC) :+ { implicit p: Parameters =>
          implicit val c = HermesConfig()
          val hermes = LazyModule(new Hermes)
          hermes
        }
    })
