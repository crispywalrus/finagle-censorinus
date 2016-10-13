package com.crispywalrus
package finagle
package stats

import com.twitter.finagle.stats._
import com.twitter.app.GlobalFlag
import github.gphat.censorinus._

/**
 * wrap up using censorinus to forward metrics to either statsd or
 * dogstatsd based on flags and toggles.
 *
 *  based on the usage of GlobalFlag the flags are
 *  - com.crispywalrus.finagle.stats.hostname, defaults to localhost
 *  - com.crispywalrus.finagle.stats.port, defaults to 8125
 *  - com.crispywalrus.finagle.stats.implementation statsd/dogstatsd defaults to statsd
 */
class CensorinusStatsReceiver
    extends StatsReceiver {

  object hostname extends GlobalFlag[String]("localhost", "statsd hostname")
  object port extends GlobalFlag[Int](8125, "statsd port")

  object implementation extends GlobalFlag[String]("statsd", "type of metrics gathering server [statsd,dogstatsd]")

  lazy val impl: StatsReceiver = implementation() match {
    case "dogstatsd" => new DogStatsDStatsReceiver(hostname(), port())
    case "statsd" => new StatsDStatsReceiver(hostname(), port())
    case iname => throw new IllegalArgumentException(s"$iname is invalid driver name")
  }

  override val repr: AnyRef = this

  def counter(name: String*) = impl.counter(name: _*)

  def addGauge(name: String*)(f: => Float) = impl.addGauge(name: _*)(f)

  def stat(name: String*) = impl.stat(name: _*)

}

trait FinagleStatsD {

  def formatName(description: Seq[String]) =
    description map { _.replaceAll("\\.", "-") } map { _.replaceAll(":", ";") } mkString "."

}

class StatsDStatsReceiver(val host: String, val port: Int)
    extends StatsReceiver
    with FinagleStatsD {

  val c = new StatsDClient(host, port)

  override val repr: AnyRef = this

  def counter(name: String*) = new Counter {
    val counterName = formatName(name)
    c.counter(counterName, value = 0)
    override def incr(delta: Int): Unit = {
      c.increment(counterName, delta.toDouble)
    }
  }

  def stat(name: String*) = new Stat {
    val statName = formatName(name)
    def add(value: Float) = {
      c.set(name = statName, value = value.toString)
    }
  }

  def addGauge(name: String*)(f: => Float) = new Gauge {
    val gaugeName = formatName(name)
    def remove(): Unit = {
    }
    c.gauge(name = gaugeName, value = f.toDouble)
  }

}

class DogStatsDStatsReceiver(host: String, port: Int)
    extends StatsReceiver
    with FinagleStatsD {

  override val repr: AnyRef = this

  val c = new DogStatsDClient(host, port)

  def counter(name: String*) = new Counter {
    val counterName = formatName(name)
    c.counter(counterName, value = 0)
    override def incr(delta: Int): Unit = {
      c.increment(formatName(name), delta.toDouble)
    }
  }

  def stat(name: String*) = new Stat {
    val statName = formatName(name)
    def add(value: Float) = {
      c.set(name = statName, value = value.toString)
    }
  }

  def addGauge(name: String*)(f: => Float) = new Gauge {
    val gaugeName = formatName(name)
    def remove(): Unit = {
    }
    c.gauge(name = gaugeName, value = f.toDouble)
  }
}
