/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.jawa.alir.taintAnalysis

import org.argus.jawa.core.util._
import java.io.BufferedReader
import java.io.FileReader
import java.util.regex.Pattern
import java.util.regex.Matcher

import org.argus.jawa.alir.InterProceduralNode
import org.argus.jawa.alir.pta.PTAResult
import org.argus.jawa.compiler.parser.Location
import org.argus.jawa.core.{Global, Signature}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
trait SourceAndSinkManager[T<: Global] {
  def sasFilePath: String
  /**
   * it's a map from source API sig to it's category
   */
  protected val sources: MMap[Signature, ISet[String]] = mmapEmpty
  /**
   * it's a map from sink API sig to it's category
   */
  protected val sinks: MMap[Signature, (ISet[Int], ISet[String])] = mmapEmpty

  def parse(): Unit =
    SSParser.parse(sasFilePath) match {
      case (srcs, sins) =>
        srcs.foreach{
          case (sig, tags) =>
            this.sources += (sig -> tags)
        }
        sins.foreach{
          case (sig, (poss, tags)) =>
            this.sinks += (sig -> (poss, tags))
        }
//        System.err.println("source size: " + this.sources.size + " sink size: " + this.sinks.size)
    }
  
  
  def addSource(source: Signature, tags: ISet[String]): Unit = {
    this.sources += (source -> tags)
  }

  def addSink(sink: Signature, positions: ISet[Int], tags: ISet[String]): Unit = {
    this.sinks += (sink -> (positions, tags))
  }
  
  def getSourceAndSinkNode[N <: InterProceduralNode](global: T, node: N, ptaResult: PTAResult): (ISet[TaintSource[N]], ISet[TaintSink[N]])
  def isSource(global: T, loc: Location, ptaresult: PTAResult): Boolean
  def isSource(global: T, calleeSig: Signature, callerSig: Signature, callerLoc: Location): Boolean
  def isSourceMethod(global: T, procedureSig: Signature): Boolean
  def isSink(global: T, loc: Location, ptaresult: PTAResult): Boolean
  def isSink(global: T, procedureSig: Signature): Boolean
}

/**
 * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
 * @author <a href="mailto:sroy@k-state.edu">Sankardas Roy</a>
 */ 
object SSParser{
  final val TITLE = "SSParser"
  final val DEBUG = false
  //                           1            2                   3            4
  private val regex = "([^\\s]+)\\s+([^\\s]+)?\\s*->\\s+([^\\s]+)\\s*([^\\s]+)?\\s*"
  def parse(filePath: String): (IMap[Signature, ISet[String]], IMap[Signature, (ISet[Int], ISet[String])]) = {
    val rdr: BufferedReader = new BufferedReader(new FileReader(filePath))
    val sources: MMap[Signature, ISet[String]] = mmapEmpty
    val sinks: MMap[Signature, (ISet[Int], ISet[String])] = mmapEmpty
    val p: Pattern = Pattern.compile(regex)
    var line = rdr.readLine()
    while(line != null){
      try{
        val m = p.matcher(line)
        if(m.find()){
          val (tag, apiSig, positions, tainttags) = parseLine(m)
          tag match{
            case "_SOURCE_" => sources += (apiSig -> tainttags)
            case "_SINK_" => sinks += (apiSig -> (positions, tainttags))
            case "_NONE_" =>
            case _ => throw new RuntimeException("Not expected tag: " + tag)
          }
        } else {
          throw new RuntimeException("Did not match the regex: " + line)
        }
      } catch {
        case ex: Exception =>
          if(DEBUG) ex.printStackTrace()
          System.err.println(TITLE + " exception occurs: " + ex.getMessage)
      }
      line = rdr.readLine()
    }
    rdr.close()
    (sources.toMap, sinks.toMap)
  }
  
  def parseLine(m: Matcher): (String, Signature, ISet[Int], ISet[String]) = {
    require(m.group(1) != null && m.group(3) != null)
    val apiSig = new Signature(m.group(1))
    val taintTag = m.group(2)
    val taintTags: ISet[String] = if(taintTag == null) isetEmpty else taintTag.split("\\|").toSet
    val tag = m.group(3)
    val rawpos = m.group(4)
    val positions: MSet[Int] = msetEmpty
    if(rawpos != null) {
      positions ++= rawpos.split("\\|").map(_.toInt)
    }
    (tag, apiSig, positions.toSet, taintTags)
  }
}
