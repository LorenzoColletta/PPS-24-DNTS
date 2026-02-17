package domain.sampling

import domain.data.pattern.SpiralCurve
import domain.data.sampling.SpiralDomain
import domain.data.util.Space
import org.scalatest.funsuite.AnyFunSuite
import domain.sampling.*

class SpiralDomainTest extends AnyFunSuite:

  test("domain max is non-negative"):
      given Space = Space(width = 10.0, height = 10.0)

      val curve = SpiralCurve(1.0, 0.5, rotation = 0.0)
      val domain = SpiralDomain.domain(curve)

      assert(domain.max >= 0.0)


  test("all points produced within the domain fit inside the space bounds"):
    given Space = Space(width = 10.0, height = 10.0)

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 0.5,
      rotation = 0.0
    )

    val domain = SpiralDomain.domain(curve)

    val ts = (0 to 1000).map: i =>
      domain.min + i * (domain.max - domain.min) / 1000.0


    ts.foreach: t =>
      val p = curve.at(t)

      assert(p.x <= 10.0)
      assert(p.y <= 10.0)



  test("domain depends on the smallest space dimension"):
    val curve = SpiralCurve(1.0, 0.5, 0.0)

    val d1 = {
      given Space = Space(width = 20.0, height = 5.0)

      SpiralDomain.domain(curve)
    }

    val d2 = {
      given Space = Space(width = 5.0, height = 20.0)

      SpiralDomain.domain(curve)
    }

    assert(d1.max == d2.max)


  test("domain is independent from spiral rotation"):
    given Space = Space(width = 10.0, height = 10.0)

    val curve1 = SpiralCurve(1.0, 0.5, rotation = 0.0)
    val curve2 = SpiralCurve(1.0, 0.5, rotation = math.Pi / 2)

    val d1 = SpiralDomain.domain(curve1)
    val d2 = SpiralDomain.domain(curve2)

    assert(d1 == d2)


//  test("domain max is zero or negative when spiral starts outside space"):
//    given Space = Space(width = 2.0, height = 2.0)
//
//    val curve = SpiralCurve(
//      startDistance = 10.0,
//      branchDistance = 1.0,
//      rotation = 0.0
//    )
//
//    val domain = SpiralDomain.domain(curve)
//
//    assert(domain.max <= 0.0)

