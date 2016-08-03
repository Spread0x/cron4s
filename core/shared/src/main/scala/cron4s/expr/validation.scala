package cron4s.expr

import cron4s.{CronField, CronUnit}

import scalaz._
import Scalaz._

/**
  * Created by alonsodomin on 02/08/2016.
  */
private[expr] object validation {

  type ValidatedExpr[F <: CronField] = ValidationNel[String, SeveralExpr[F]]

  def validateSeveralExpr[F <: CronField](exprs: NonEmptyList[EnumerableExpr[F]])
                                         (implicit unit: CronUnit[F]): ValidatedExpr[F] = {

    def validateImplication(expr: EnumerableExpr[F],
                            processed: Vector[EnumerableExpr[F]]
                           ): ValidationNel[String, EnumerableExpr[F]] = {
      val alreadyImplied = processed.find(e => expr.impliedBy(e)).
        map(found => s"Expression $expr is implied by $found".failureNel[EnumerableExpr[F]])
      val impliesOther = processed.find(_.impliedBy(expr)).
        map(found => s"Expression $found is implied by $expr".failureNel[EnumerableExpr[F]])

      alreadyImplied.orElse(impliesOther).getOrElse(expr.successNel[String])
    }

    val zero = Vector.empty[EnumerableExpr[F]]
    val (_, result) = exprs.foldLeft((zero, zero.successNel[String])) { case ((seen, acc), expr) =>
      val validated = (acc |@| validateImplication(expr, seen))((prev, next) => prev :+ next)
      (seen :+ expr, validated)
    }
    result.map(vec => SeveralExpr(vec.sorted))
  }

}