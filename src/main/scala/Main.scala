import cilib._
import cilib.pso._
import cilib.pso.Defaults._
import cilib.exec._
import cilib.io._
import scala.math._
import scala.collection.immutable.{ Stream => SStream }

import zio.prelude.newtypes.Natural

import zio.prelude.{ Comparison => _, _ }
import zio.stream._
import zio._

import _root_.benchmarks._


object CEC2005 extends zio.App {

  var totalIterations: Natural = Natural.make(300000).toOption.get
  val problemDimensions = 50
  val bounds = Interval(-100.0, 100.0) ^ problemDimensions 
  val cmp = Comparison.dominance(Min)

//  val particleIterations : Natural = Natural.make(totalIterations).toOption.get
//      particleIterations = 1000/*ceil(1.0*totalIterations/swarmSize)*/ /*CALCULATE ITERATIONS FOR EACH PARTICLE - THIS HAS NOT WORKED PROPERLY*/

  /* Convert the NonEmtpyVector into a AtLeast2List structure which
   * guaraantees that there are 2 or more elements; fail otherwise
   */
  def mkAtLeast2List(x: NonEmptyVector[Double]) =
    AtLeast2List.make(x) match {
      case ZValidation.Failure(_, e) => sys.error("Input vector requires at least 2 elements")
      case ZValidation.Success(_, result) => result
    }

  // Define a normal GBest PSO and run it for a single iteration
  val cognitive = Guide.pbest[Mem[Double], Double]
  val social = Guide.lbest[Mem[Double]](5)
  //  val gbestPSO = gbest(0.729844, 1.496180, 1.496180, cognitive, social)

  final case class Parameters(name: String, w: Double, c1: Double, c2: Double)

  val parameterSets: List[Parameters] = List(
    Parameters("cpv01", w = 0.729844, c1 = 1.496180, c2 = 1.496180),
    Parameters("cpv02", w = 0.729000, c1 = 2.041200, c2 = 0.947700),
    Parameters("cpv03", w = 0.600,    c1 = 1.700,    c2 = 1.700),
    Parameters("cpv04", w = 0.721,    c1 = 1.193,    c2 = 1.193),
    Parameters("cpv05", w = 0.715,    c1 = 1.700,    c2 = 1.700),
    Parameters("cpv06", w = 0.724,    c1 = 1.468,    c2 = 1.468),
    Parameters("cpv07", w = 0.785,    c1 = 1.331,    c2 = 1.331),
    Parameters("cpv08", w = 0.837,    c1 = 1.255,    c2 = 1.255),
    Parameters("cpv09", w = 0.42,     c1 = 1.55,     c2 = 1.55),
    Parameters("cpv10", w = 0.711897, c1 = 1.711897, c2 = 1.711897),
    Parameters("cpv11", w = 0.5,      c1 = 1.90,     c2 = 1.90),
    Parameters("cpv12", w = 0.6,      c1 = 1.80,     c2 = 1.80),
    Parameters("cpv13", w = 0.1,      c1 = 0.950,    c2 = 2.850),
    Parameters("cpv14", w = -0.1,     c1 = 0.875,    c2 = 2.625),
  )

  // Create a list of problem streams. The name parameter will be present as the name of the problem in the resulting data file
  val listOfProblemStream = List(
    Runner.staticProblem("f2", Eval.unconstrained((x: NonEmptyVector[Double]) => {
      val nev2 = mkAtLeast2List(x)    /*needed for f3, f4, f6, f13, f14, f21, f22, f23*/
      Feasible(benchmarks.cec.cec2005.Benchmarks.f2(x))
    }))//,
//    Runner.staticProblem("f3", Eval.unconstrained((x: NonEmptyVector[Double]) => {
//      val nev2 = mkAtLeast2List(x)    /*needed for f3, f4, f6, f13, f14, f21, f22, f23*/
//      Feasible(benchmarks.cec.cec2005.Benchmarks.f3(nev2))
//    }))
  )

  type Swarm = NonEmptyVector[Particle[Mem[Double], Double]]

  // A data structure to hold the resulting values.
  // Each class member is mapped to a column within the output file
  final case class Results(min: Double, average: Double)

  def extractSolution(collection: Swarm) = {
    val fitnessValues = collection.map(x =>
      x.pos.objective
        .flatMap(_.fitness match {
          case Left(f) =>
            f match {
              case Feasible(v) => Some(v)
              case _           => None
            }
          case _ => None
        })
        .getOrElse(Double.PositiveInfinity)
    )

    Results(
      min = fitnessValues.toChunk.min,
      average = fitnessValues.toChunk.reduceLeft(_ + _) / fitnessValues.size
    )
  }

  val combinations  =
    for {
      swarmSizeValue <- List(2, 3, 4, 5, 7, 10, 20, 30, 40, 50, 70, 100, 200, 300, 400, 500).toStream
      parameters <- parameterSets.toStream
      (problemStream, index) <- listOfProblemStream.toStream.zipWithIndex
    } yield {
      val swarmSize: Natural = Natural.make(swarmSizeValue).toOption.get
//      val particleIterations: Int = (ceil(totalIterations.toFloat/swarmSize)).toInt
var    particleIterations: Int = (ceil(totalIterations.toFloat/swarmSize)).toInt;
        if (swarmSize < 0){
 particleIterations = 1.toInt;
}
       if (((parameters.name=="cpv01")||(parameters.name=="cpv02")||(parameters.name=="cpv03")||(parameters.name=="cpv04")||(parameters.name=="cpv05")||(parameters.name=="cpv06")||(parameters.name=="cpv07")||(parameters.name=="cpv08")||(parameters.name=="cpv09")||(parameters.name=="cpv10")||(parameters.name=="cpv11")||(parameters.name=="cpv12")) && (swarmSize == 0)){
particleIterations = 1.toInt;
}

      // The bounds can change as well! Just need to wire it up like the swarm size.
      // Even better would be to create it in the same list as the problem streams so that they are correctly paired up
      val swarm =
        Position.createCollection(PSO.createParticle(x => Entity(Mem(x, x.zeroed), x)))(bounds, swarmSize)

      // construct the PSO using the paramters defined for the experiment
      val iter = Kleisli(Iteration.sync(gbest(parameters.w, parameters.c1, parameters.c2, cognitive, social)))

      val psoWithParams =
        Runner.staticAlgorithm(s"lbest-${parameters.name}-${swarmSize}", iter)

      val outfil: String = s"results/lbest-pso_50runs_${parameters.name}_${swarmSize.toString}part_${bounds.size}dim_${particleIterations.toString}iter_f${index+3}.parquet";
      println(outfil)
      val outputFile = new java.io.File(outfil)

      ZIO.effectSuspendTotal { // The effect must be suspended to delay computation
        val allRNGs = // list of streams
          for {
            r <- RNG.initN(25, 123456789L)
          } yield
              Runner.foldStep(
                cmp,
                r,
                swarm,
                psoWithParams,
                problemStream,
                (x: Swarm, _) => RVar.pure(x)
              )
                .map(Runner.measure(extractSolution _))
                .take(particleIterations) // 1000 iterations

        allRNGs.reduceLeft(ZStream.mergeAll(1)(_, _)).run(parquetSink(outputFile))
      }
    }

  val threads = 16

  def run(args: List[String]) = {
    println("Preparing to run")

    // Here we are running some of the combinations at once, limited by the number of threads specified
    ZIO.collectAllParN_(threads)(combinations)
      .exitCode
  }
}
