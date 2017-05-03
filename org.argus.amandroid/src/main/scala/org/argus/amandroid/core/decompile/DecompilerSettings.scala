/*
 * Copyright (c) 2017. Fengguo Wei and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Detailed contributors are listed in the CONTRIBUTOR.md
 */

package org.argus.amandroid.core.decompile

import java.io.{File, PrintStream}

import hu.ssh.progressbar.ProgressBar
import hu.ssh.progressbar.console.ConsoleProgressBar
import org.argus.amandroid.core.AndroidConstants
import org.argus.amandroid.core.dedex.JawaStyleCodeGeneratorListener
import org.argus.jawa.core.{JawaType, LibraryAPISummary, Reporter}
import org.argus.jawa.core.util._

object DecompileLevel extends Enumeration {
  val NO, SIGNATURE, UNTYPED, TYPED = Value
}

/**
  * @author <a href="mailto:fgwei521@gmail.com">Fengguo Wei</a>
  */
case class DecompilerSettings(
    debugMode: Boolean,
    forceDelete: Boolean,
    strategy: DecompileStrategy,
    reporter: Reporter,
    listener: Option[JawaStyleCodeGeneratorListener] = None,
    api: Int = 15,
    progressBar: ProgressBar = ConsoleProgressBar.on(System.out)
      .withFormat("[:bar] :percent% :elapsed/:total ETA: :eta"))

case class DecompileStrategy(
    libraryAPISummary: LibraryAPISummary,
    layout: DecompileLayout,
    sourceLevel: DecompileLevel.Value = DecompileLevel.UNTYPED,
    thirdPartyLibLevel: DecompileLevel.Value = DecompileLevel.SIGNATURE,
    removeSupportGen: Boolean = true) {
  def recordFilter(ot: JawaType): DecompileLevel.Value = {
    if(removeSupportGen) {
      if(ot.name.startsWith("android.support.v4")){
        layout.dependencies += AndroidConstants.MAVEN_SUPPORT_V4
        return DecompileLevel.NO
      } else if (ot.name.startsWith("android.support.v13")) {
        layout.dependencies += AndroidConstants.MAVEN_SUPPORT_V13
        return DecompileLevel.NO
      } else if (ot.name.startsWith("android.support.v7")){
        layout.dependencies += AndroidConstants.MAVEN_APPCOMPAT
        return DecompileLevel.NO
      } else if (ot.name.startsWith("android.support.design")) {
        layout.dependencies += AndroidConstants.MAVEN_DESIGN
        return DecompileLevel.NO
      } else if (ot.name.startsWith("android.support.annotation")) {
        layout.dependencies += AndroidConstants.MAVEN_SUPPORT_ANNOTATIONS
        return DecompileLevel.NO
      } else if (ot.name.startsWith("android.support.constraint")) {
        layout.dependencies += AndroidConstants.MAVEN_CONSTRAINT_LAYOUT
        return DecompileLevel.NO
      } else if(ot.name.endsWith(layout.pkg + ".BuildConfig") ||
        ot.name.endsWith(layout.pkg + ".Manifest") ||
        ot.name.contains(layout.pkg + ".Manifest$") ||
        ot.name.endsWith(layout.pkg + ".R") ||
        ot.name.contains(layout.pkg + ".R$")) {
        return DecompileLevel.NO
      }
    }
    if(!ot.getPackageName.startsWith(layout.pkg) && libraryAPISummary.isLibraryClass(ot)) {
      layout.thirdPartyLibraries += ot.getPackageName
      thirdPartyLibLevel
    } else {
      sourceLevel
    }
  }
  def outputCode(recType: JawaType, code: String, dexUri: FileResourceUri): Unit = {
    if(layout.outputUri.nonEmpty) {
      val classPath = recType.jawaName.replaceAll("\\.", "/")
      val outputUri: FileResourceUri = if (layout.thirdPartyLibraries.contains(recType.getPackageName)) {
        layout.libOutUri(dexUri)
      } else {
        layout.sourceOutUri(dexUri)
      }
      var targetFile = FileUtil.toFile(FileUtil.appendFileName(outputUri, classPath + ".jawa"))
      var i = 0
      while (targetFile.exists()) {
        i += 1
        targetFile = FileUtil.toFile(FileUtil.appendFileName(outputUri, classPath + "." + i + ".jawa"))
      }
      val parent = targetFile.getParentFile
      if (parent != null)
        parent.mkdirs()
      val outputStream = new PrintStream(targetFile)
      try {
        outputStream.println(code)
      } finally {
        outputStream.close()
      }
    }
  }
  def outputThirdPartyLibs(): Unit = {
    if(layout.outputUri.nonEmpty) {
      val outputUri: FileResourceUri = layout.outputSrcUri
      val targetFile = FileUtil.toFile(FileUtil.appendFileName(outputUri, "third_party_libs.txt"))
      val parent = targetFile.getParentFile
      if (parent != null)
        parent.mkdirs()
      val outputStream = new PrintStream(targetFile)
      try {
        outputStream.println(layout.thirdPartyLibraries.mkString("\n"))
      } finally {
        outputStream.close()
      }
    }
  }
}

case class DecompileLayout(
    outputUri: FileResourceUri,
    createFolder: Boolean = true,
    srcFolder: String = "src",
    libFolder: String = "lib",
    createSeparateFolderForDexes: Boolean = true) {
  var pkg: String = ""
  var outputSrcUri: FileResourceUri = outputUri
  var sourceFolders: ISet[String] = isetEmpty
  var libFolders: ISet[String] = isetEmpty
  var dependencies: ISet[String] = isetEmpty
  var thirdPartyLibraries: ISet[String] = isetEmpty

  def sourceFolder(dexUri: FileResourceUri): String = {
    val folder = srcFolder + dexFolder(dexUri)
    sourceFolders += folder
    folder
  }
  def libFolder(dexUri: FileResourceUri): String = {
    val folder = libFolder + dexFolder(dexUri)
    libFolders += folder
    folder
  }

  private def dexFolder(dexUri: FileResourceUri): String = {
    if (createSeparateFolderForDexes) File.separator + {
      if (dexUri.startsWith(outputSrcUri)) dexUri.replace(outputSrcUri, "").replace(".dex", "").replace(".odex", "")
      else dexUri.substring(dexUri.lastIndexOf("/") + 1, dexUri.lastIndexOf("."))
    }.replaceAll("/", "_")
    else ""
  }

  def sourceOutUri(dexUri: FileResourceUri): FileResourceUri = {
    val outPath = FileUtil.toFilePath(this.outputSrcUri)
    FileUtil.toUri(outPath + File.separator + sourceFolder(dexUri))
  }
  def libOutUri(dexUri: FileResourceUri): FileResourceUri = {
    val outPath = FileUtil.toFilePath(this.outputSrcUri)
    FileUtil.toUri(outPath + File.separator + libFolder(dexUri))
  }
}