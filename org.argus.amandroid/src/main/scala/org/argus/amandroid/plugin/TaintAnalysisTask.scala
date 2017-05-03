/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.amandroid.plugin

import java.io.PrintWriter

import org.argus.amandroid.alir.componentSummary.{ApkYard, ComponentBasedAnalysis}
import org.argus.amandroid.alir.dataRecorder.DataCollector
import org.argus.amandroid.alir.taintAnalysis.{AndroidDataDependentTaintAnalysis, DataLeakageAndroidSourceAndSinkManager}
import org.argus.amandroid.core.AndroidGlobalConfig
import org.argus.amandroid.core.decompile.{DecompileLayout, DecompileStrategy, DecompilerSettings}
import org.argus.amandroid.plugin.communication.CommunicationSourceAndSinkManager
import org.argus.amandroid.plugin.dataInjection.IntentInjectionSourceAndSinkManager
import org.argus.amandroid.plugin.oauth.OAuthSourceAndSinkManager
import org.argus.amandroid.plugin.password.PasswordSourceAndSinkManager
import org.argus.jawa.alir.dataDependenceAnalysis.InterProceduralDataDependenceAnalysis
import org.argus.jawa.alir.taintAnalysis.TaintAnalysisResult
import org.argus.jawa.core.{DefaultLibraryAPISummary, Reporter}
import org.argus.jawa.core.util.FileUtil
import org.argus.jawa.core.util._

import scala.concurrent.duration._
import scala.language.postfixOps

object TaintAnalysisModules extends Enumeration {
  val INTENT_INJECTION, PASSWORD_TRACKING, OAUTH_TOKEN_TRACKING, DATA_LEAKAGE, COMMUNICATION_LEAKAGE = Value
}

case class TaintAnalysisTask(module: TaintAnalysisModules.Value, fileUris: ISet[FileResourceUri], outputUri: FileResourceUri, forceDelete: Boolean, reporter: Reporter) {
  import TaintAnalysisModules._
//  private final val TITLE = "TaintAnalysisTask"
  def run: Option[TaintAnalysisResult[AndroidDataDependentTaintAnalysis.Node, InterProceduralDataDependenceAnalysis.Edge]] = {
    val yard = new ApkYard(reporter)
    val layout = DecompileLayout(outputUri)
    val strategy = DecompileStrategy(new DefaultLibraryAPISummary(AndroidGlobalConfig.settings.third_party_lib_file), layout)
    val settings = DecompilerSettings(debugMode = false, forceDelete = forceDelete, strategy, reporter)
    val apks = fileUris.map(yard.loadApk(_, settings, collectInfo = true))
    val ssm = module match {
      case INTENT_INJECTION =>
        new IntentInjectionSourceAndSinkManager(AndroidGlobalConfig.settings.sas_file)
      case PASSWORD_TRACKING =>
        new PasswordSourceAndSinkManager(AndroidGlobalConfig.settings.sas_file)
      case OAUTH_TOKEN_TRACKING =>
        new OAuthSourceAndSinkManager(AndroidGlobalConfig.settings.sas_file)
      case DATA_LEAKAGE =>
        new DataLeakageAndroidSourceAndSinkManager(AndroidGlobalConfig.settings.sas_file)
      case COMMUNICATION_LEAKAGE =>
        new CommunicationSourceAndSinkManager(AndroidGlobalConfig.settings.sas_file)
    }
    ComponentBasedAnalysis.prepare(apks)(AndroidGlobalConfig.settings.timeout minutes)
    val cba = new ComponentBasedAnalysis(yard)
    cba.phase1(apks)
    val iddResult = cba.phase2(apks)
    val tar = cba.phase3(iddResult, ssm)
    apks.foreach { apk =>
      val appData = DataCollector.collect(apk)
      val outputDirUri = FileUtil.appendFileName(apk.model.layout.outputSrcUri, "result")
      val outputDir = FileUtil.toFile(outputDirUri)
      if (!outputDir.exists()) outputDir.mkdirs()
      val out = new PrintWriter(FileUtil.toFile(FileUtil.appendFileName(outputDirUri, "AppData.txt")))
      out.print(appData.toString)
      out.close()
    }
    tar
  }
  
}