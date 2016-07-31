package cron4s.expr

import cron4s.{CronField, CronUnit}
import cron4s.types.std.all._
import cron4s.core.Sequential
import cron4s.matcher._

/**
  * Created by alonsodomin on 07/11/2015.
  */
sealed trait Expr[F <: CronField] extends Sequential[Int] {

  def matches: Matcher[Int]

  def unit: CronUnit[F]

}

object Expr {

  sealed trait EnumerableExpr[F <: CronField] extends Expr[F]

  sealed trait DivisibleExpr[F <: CronField] extends Expr[F]

  sealed trait SpecialChar

  final case class AlwaysExpr[F <: CronField](implicit val unit: CronUnit[F])
    extends DivisibleExpr[F] with SpecialChar {

    def min: Int = unit.min

    def max: Int = unit.max

    def matches: Matcher[Int] = always(true)

    def step(from: Int, step: Int): Option[(Int, Int)] =
      unit.step(from, step)

  }

  case object Last extends SpecialChar

  final case class ConstExpr[F <: CronField](field: F, value: Int, textValue: Option[String] = None)
                                            (implicit val unit: CronUnit[F])
    extends EnumerableExpr[F] {

    require(unit.indexOf(value).nonEmpty, s"Value $value is out of bounds for field: ${unit.field}")

    def min: Int = value

    def max: Int = value

    def matches: Matcher[Int] = equal(value)

    def step(from: Int, step: Int): Option[(Int, Int)] = {
      Some((value, step))
    }

  }

  final case class BetweenExpr[F <: CronField](begin: ConstExpr[F], end: ConstExpr[F])
                                              (implicit val unit: CronUnit[F])
    extends EnumerableExpr[F] with DivisibleExpr[F] {

    require(begin.value < end.value, s"$begin should be less than $end")

    def min: Int = begin.value

    def max: Int = end.value

    def matches: Matcher[Int] = Matcher { x =>
      unit.gteq(x, begin.value) && unit.lteq(x, end.value)
    }

    def step(from: Int, step: Int): Option[(Int, Int)] = {
      if (matches(from))
        unit.narrow(min, max).step(from, step)
      else
        None
    }

  }

  final case class SeveralExpr[F <: CronField](values: Vector[EnumerableExpr[F]])
                                              (implicit val unit: CronUnit[F])
    extends Expr[F] with DivisibleExpr[F] {

    def min: Int = values.head.min

    def max: Int = values.last.max

    def matches: Matcher[Int] = exists(values.map(_.matches))

    def step(from: Int, step: Int): Option[(Int, Int)] = {
      if (matches(from)) {
        val range = min to max
        Option(range.indexOf(from)).filter(_ >= 0).map(values).
          flatMap {
            _.step(from, step)
          }
      } else None
    }

  }

  final case class EveryExpr[F <: CronField](value: DivisibleExpr[F], freq: Int)
                                            (implicit val unit: CronUnit[F])
    extends Expr[F] {

    def min: Int = value.min

    def max: Int = value.max

    def matches: Matcher[Int] = {
      val valuesToMatch = Stream.iterate[Option[(Int, Int)]](Some((min, 0))) {
        prev => prev.flatMap { case (v, _) => value.step(v, freq) }
      }.takeWhile(_.isDefined).flatten.takeWhile(_._2 < 1).map(_._1)

      exists(valuesToMatch.toVector.map(x => equal(x)))
    }

    override def next(from: Int): Option[Int] = value.step(from, freq).map(_._1)

    override def previous(from: Int): Option[Int] = value.step(from, -freq).map(_._1)

    def step(from: Int, step: Int): Option[(Int, Int)] = value.step(from, step)

  }

}