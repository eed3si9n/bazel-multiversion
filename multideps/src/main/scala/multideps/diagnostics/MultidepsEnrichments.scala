package multideps.diagnostics

import java.{util => ju}

import scala.collection.mutable

import multideps.resolvers.DependencyId

import coursier.core.Configuration
import coursier.core.Dependency
import coursier.error.ResolutionError
import coursier.util.Task
import moped.cli.Application
import moped.json.ErrorResult
import moped.json.Result
import moped.json.ValueResult
import moped.reporters.Diagnostic
import moped.reporters.Reporter

object MultidepsEnrichments {
  implicit class XtensionString(string: String) {
    def asLiteral: String = pprint.PPrinter.BlackWhite.tokenize(string).mkString
  }
  implicit class XtensionReporter(reporter: Reporter) {
    def exitCode(): Int = if (reporter.hasErrors()) 1 else 0
  }
  implicit class XtensionStrings(xs: Iterable[String]) {
    def commas: String =
      if (xs.isEmpty) ""
      else if (xs.size == 1) xs.head
      else xs.mkString(", ")
  }
  implicit class XtensionApplication(app: Application) {
    def isTesting: Boolean =
      "true" == app.env.environmentVariables
        .getOrElse("MULTIDEPS_TESTING", "false")
    def complete(result: Result[Unit]): Int =
      result match {
        case ValueResult(()) =>
          app.reporter.exitCode()
        case ErrorResult(error) =>
          app.reporter.log(error)
          1
      }
  }
  private val isEmptyConfiguration = Set(
    Configuration.empty,
    Configuration.compile,
    Configuration.defaultCompile,
    Configuration.default,
    Configuration.runtime,
    Configuration.test
  )
  implicit class XtensionDependency(dep: Dependency) {
    def toId: DependencyId =
      DependencyId(
        dep.module.organization.value,
        dep.module.name.value,
        dep.version
      )
    def configRepr: String =
      if (isEmptyConfiguration(dep.configuration)) ""
      else s"_${dep.configuration.value}"
    def repr: String = {
      s"${dep.module.repr}:${dep.version}${configRepr}"
    }
    def withoutMetadata: Dependency = Dependency(dep.module, dep.version)
  }
  implicit class XtensionSeq[A](xs: Seq[A]) {
    def sortByCachedFunction[B: Ordering](fn: A => B): Seq[A] = {
      val map = new ju.IdentityHashMap[A, B]
      xs.foreach { x =>
        // Cache the function value to obey sorting invariants.
        map.put(x, fn(x))
      }
      xs.sortBy(x => map.get(x))
    }
  }
  implicit class XtensionList[A](xs: List[A]) {
    def distinctBy[B](fn: A => B): List[A] = {
      val seen = mutable.LinkedHashSet.empty[B]
      val buf = mutable.ListBuffer.empty[A]
      xs.foreach { x =>
        val isNew = seen.add(fn(x))
        if (isNew) buf += x
      }
      buf.toList
    }
  }
  implicit class XtensionTaskIO[T](t: Task[T]) {
    def toResult: Task[Result[T]] =
      t.map(ValueResult(_)).handle {
        case ex: ResolutionError =>
          ErrorResult(Diagnostic.error(ex.getMessage()))
      }
  }

}
