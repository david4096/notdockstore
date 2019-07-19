package io.dockstore.common


import java.nio.file.{Files, Paths}
import java.util

import cats.syntax.validated._
import com.typesafe.config.ConfigFactory
import common.Checked
import common.validation.Checked._
import common.validation.ErrorOr.ErrorOr
import cromwell.core.path.DefaultPathBuilder
import cromwell.languages.LanguageFactory
import cromwell.languages.util.ImportResolver
import cromwell.languages.util.ImportResolver.{DirectoryResolver, HttpResolver, ImportResolver, ResolvedImportBundle}
import languages.wdl.biscayne.WdlBiscayneLanguageFactory
import languages.wdl.draft2.WdlDraft2LanguageFactory
import languages.wdl.draft3.WdlDraft3LanguageFactory
import spray.json.DefaultJsonProtocol._
import spray.json._
import wdl.draft3.parser.WdlParser
import wom.callable.{CallableTaskDefinition, WorkflowDefinition}
import wom.executable.WomBundle
import wom.expression.WomExpression
import wom.graph.{ExternalGraphInputNode, OptionalGraphInputNode, OptionalGraphInputNodeWithDefault, RequiredGraphInputNode}
import wom.types.{WomCompositeType, WomOptionalType, WomType}

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.util.Try


/**
  * A bridge class for interacting with the WDL documents
  *
  * Note: For simplicity, uses draft-3/v1.0 for throwing syntax errors from any language
  */
class WdlBridge {
  var secondaryWdlFiles = new util.HashMap[String, String]()

  def main(args: Array[String]): Unit = {
    println("WdlBridge")
  }

  /**
    * Set the secondary files (imports)
    * @param secondaryFiles
    */
  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]): Unit = {
    secondaryWdlFiles = secondaryFiles
  }

  /**
    * Validates the workflow given by filePath
    * @param filePath absolute path to file
    */
  @throws(classOf[WdlParser.SyntaxError])
  def validateWorkflow(filePath: String) = {
    val bundle = getBundle(filePath)

    if (!bundle.primaryCallable.isDefined) {
      throw new WdlParser.SyntaxError("Workflow is missing a workflow declaration.")
    }
  }

  /**
    * Validates the tool given by filePath
    * @param filePath absolute path to file
    */
  @throws(classOf[WdlParser.SyntaxError])
  def validateTool(filePath: String) = {
    validateWorkflow(filePath)
    val bundle = getBundle(filePath)
    val executableCallable = bundle.toExecutableCallable.right.getOrElse(null)
    if (executableCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }

    if (executableCallable.taskCallNodes.seq.size > 1) {
      throw new WdlParser.SyntaxError("A WDL tool can only have one task.")
    }

    executableCallable.taskCallNodes
      .foreach(call => {
        val dockerAttribute = call.callable.runtimeAttributes.attributes.get("docker")
        if (!dockerAttribute.isDefined) {
          throw new WdlParser.SyntaxError(call.identifier.localName + " requires an associated docker container to make this a valid Dockstore tool.")
        }
      })
  }

  /**
    * Retrieves the metadata object for a given workflow
    * @param filePath absolute path to file
    * @throws wdl.draft3.parser.WdlParser.SyntaxError
    * @return list of metadata mappings
    */
  @throws(classOf[WdlParser.SyntaxError])
  def getMetadata(filePath: String, fileContent: String) = {
    val bundle = getBundleFromContent(fileContent, filePath)
    val metadataList = new util.ArrayList[util.Map[String, String]]()
    bundle.allCallables.foreach(callable => {
      callable._2 match {
        case w: WorkflowDefinition => {
          val metadata = JavaConverters.mapAsJavaMap(callable._2.asInstanceOf[WorkflowDefinition].meta)
          if (!metadata.isEmpty) {
            metadataList.add(metadata)
          }
        }
        case c: CallableTaskDefinition => {
          val metadata = JavaConverters.mapAsJavaMap(callable._2.asInstanceOf[CallableTaskDefinition].meta)
          if (!metadata.isEmpty) {
            metadataList.add(metadata)
          }
        }
      }
    })
    metadataList
  }

  /**
    * Create a map of file inputs names to paths
    * @param filePath absolute path to file
    * @throws wdl.draft3.parser.WdlParser.SyntaxError
    * @return mapping of file input name to type
    */
  @throws(classOf[WdlParser.SyntaxError])
  def getInputFiles(filePath: String):  util.HashMap[String, String] = {
    val inputList = new util.HashMap[String, String]()
    val bundle = getBundle(filePath)
    val primaryCallable = bundle.primaryCallable.orNull
    if (primaryCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }

    val workflowName = primaryCallable.name
    primaryCallable.inputs
      .filter(input => input.womType.stableName.toString.equals("File") || input.womType.stableName.toString.equals("Array[File]"))
      .foreach(input => inputList.put(workflowName + "." + input.name, input.womType.stableName.toString))
    inputList
  }

  /**
    * Create a list of all output files for the workflow
    * @param filePath absolute path to file
    * @throws wdl.draft3.parser.WdlParser.SyntaxError
    * @return list of output file names
    */
  @throws(classOf[WdlParser.SyntaxError])
  def getOutputFiles(filePath: String): util.List[String] = {
    val outputList = new util.ArrayList[String]()
    val bundle = getBundle(filePath)
    val primaryCallable = bundle.primaryCallable.orNull
    if (primaryCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }
    val workflowName = primaryCallable.name
    primaryCallable.outputs
      .filter(output => output.womType.stableName.toString.equals("File") || output.womType.stableName.toString.equals("Array[File]"))
      .foreach(output => outputList.add(workflowName + "." + output.name))
    outputList
  }

  /**
    * Create a mapping of import namespace to uri
    * Does not work with new parsing code, may be phased out
    * @param filePath absolute path to file
    * @return map of call names to import path
    */
  def getImportMap(filePath: String): util.LinkedHashMap[String, String] = {
    val importMap = new util.LinkedHashMap[String, String]()
    val bundle = getBundle(filePath)
    val executableCallable = bundle.toExecutableCallable.right.getOrElse(null)
    if (executableCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }
    executableCallable.taskCallNodes
      .foreach(call => {
        val callName = call.identifier.localName.value
        val path = null
        importMap.put(callName, path)
      })
    importMap
  }

  /**
    * Create a mapping of calls to dependencies
    * @param filePath absolute path to file
    * @return mapping of call to a list of dependencies
    */
  def getCallsToDependencies(filePath: String): util.LinkedHashMap[String, util.List[String]] = {
    val dependencyMap = new util.LinkedHashMap[String, util.List[String]]()
    val bundle = getBundle(filePath)
    val executableCallable = bundle.toExecutableCallable.right.getOrElse(null)
    if (executableCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }

    executableCallable.taskCallNodes
      .foreach(call => {
        val callName = call.identifier.localName.value
        dependencyMap.put("dockstore_" + callName, new util.ArrayList[String]())
      })

    executableCallable.taskCallNodes
        .foreach(call => {
          val dependencies = new util.ArrayList[String]()
          call.inputDefinitionMappings
            .foreach(inputMap => {
              inputMap._2.head.get.graphNode.inputPorts
                .foreach(inputPorts => {
                  var inputName = inputPorts.name
                  val lastPeriodIndex = inputName.lastIndexOf(".")
                  if (lastPeriodIndex != -1) {
                    inputName = inputName.substring(0, lastPeriodIndex)
                    dependencies.add("dockstore_" + inputName)
                  }
                })
            })
          dependencyMap.replace("dockstore_" + call.identifier.localName.value, dependencies)

        })

    dependencyMap
  }


  /**
    * Create a mapping of calls to docker images
    * @param filePath absolute path to file
    * @return mapping of call names to docker
    */
  @throws(classOf[WdlParser.SyntaxError])
  def getCallsToDockerMap(filePath: String): util.LinkedHashMap[String, String] = {
    val callsToDockerMap = new util.LinkedHashMap[String, String]()
    val bundle = getBundle(filePath)
    val executableCallable = bundle.toExecutableCallable.right.getOrElse(null)
    if (executableCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }
    executableCallable.taskCallNodes
      .foreach(call => {
        val dockerAttribute = call.callable.runtimeAttributes.attributes.get("docker")
        val callName = "dockstore_" + call.identifier.localName.value
        var dockerString = ""
        if (dockerAttribute.isDefined) {
          dockerString = dockerAttribute.get.sourceString.replaceAll("\"", "")
        }
        callsToDockerMap.put(callName, dockerString)
      })
    callsToDockerMap
  }

  /**
    * Get a parameter file as a string
    * @param filePath absolute path to file
    * @throws wdl.draft3.parser.WdlParser.SyntaxError
    * @return stub parameter file for the workflow
    */
  @throws(classOf[WdlParser.SyntaxError])
  def getParameterFile(filePath: String): String = {
    val bundle = getBundle(filePath)
    val executableCallable = bundle.toExecutableCallable.right.getOrElse(null)
    if (executableCallable == null) {
      throw new WdlParser.SyntaxError("Error parsing WDL file.")
    }
    executableCallable.graph.externalInputNodes.toJson(inputNodeWriter(true)).prettyPrint
  }

  private def inputNodeWriter(showOptionals: Boolean): JsonWriter[Set[ExternalGraphInputNode]] = set => {

    val valueMap: Seq[(String, JsValue)] = set.toList collect {
      case RequiredGraphInputNode(_, womType, nameInInputSet, _) => nameInInputSet -> womTypeToJson(womType, None)
      case OptionalGraphInputNode(_, womOptionalType, nameInInputSet, _) if showOptionals => nameInInputSet -> womTypeToJson(womOptionalType, None)
      case OptionalGraphInputNodeWithDefault(_, womType, default, nameInInputSet, _) if showOptionals => nameInInputSet -> womTypeToJson(womType, Option(default))
    }

    valueMap.toMap.toJson
  }

  private def womTypeToJson(womType: WomType, default: Option[WomExpression]): JsValue = (womType, default) match {
    case (WomCompositeType(typeMap, _), _) => JsObject(
      typeMap.map { case (name, wt) => name -> womTypeToJson(wt, None) }
    )
    case (_, Some(d)) => JsString(s"${womType.stableName} (optional, default = ${d.sourceString})")
    case (_: WomOptionalType, _) => JsString(s"${womType.stableName} (optional)")
    case (_, _) => JsString(s"${womType.stableName}")
  }

  /**
    * Get the WomBundle for a workflow
    * @param filePath absolute path to file
    * @return WomBundle
    */
  def getBundle(filePath: String): WomBundle = {
    val fileContent = readFile(filePath)
    getBundleFromContent(fileContent, filePath)
  }

  /**
    * Get the WomBundle for a workflow given the workflow content
    * To be used when we don't have a file stored locally
    * @param content content of file
    * @param filePath path to file
    * @return WomBundle
    */
  def getBundleFromContent(content: String, filePath: String): WomBundle = {
    val factory = getLanguageFactory(content)
    val filePathObj = DefaultPathBuilder.build(filePath).get

    // Resolve from mapping, local filesystem, or http import
    val mapResolver = MapResolver()
    mapResolver.setSecondaryFiles(secondaryWdlFiles)

    lazy val importResolvers: List[ImportResolver] =
      DirectoryResolver.localFilesystemResolvers(Some(filePathObj)) :+ HttpResolver(relativeTo = None) :+ mapResolver

    val bundle = factory.getWomBundle(content, "{}", importResolvers, List(factory))
    if (bundle.isRight) {
      bundle.getOrElse(null)
    } else {
      throw new WdlParser.SyntaxError(bundle.left.get.head)
    }
  }

  /**
    * Retrieve the language factory for the given primary descriptor file
    * @param fileContent Content of the primary workflow file
    * @return Correct language factory based on the version of WDL
    */
  def getLanguageFactory(fileContent: String) : LanguageFactory = {
    val languageFactory =
      List(
        new WdlDraft3LanguageFactory(ConfigFactory.empty()),
        new WdlBiscayneLanguageFactory(ConfigFactory.empty()))
      .find(_.looksParsable(fileContent))
      .getOrElse(new WdlDraft2LanguageFactory(ConfigFactory.empty()))

    languageFactory
  }

  /**
    * Read the given file into a string
    * @param filePath absolute path to file
    * @return Content of file as a string
    */
  def readFile(filePath: String): String = Try(Files.readAllLines(Paths.get(filePath)).asScala.mkString(System.lineSeparator())).get
}

/**
  * Class for resolving imports defined in memory (mapping of path to content)
  */
case class MapResolver() extends ImportResolver {
  var secondaryWdlFiles = new util.HashMap[String, String]()

  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]): Unit = {
    secondaryWdlFiles = secondaryFiles
  }

  override def name: String = "Map importer"

  override protected def innerResolver(path: String, currentResolvers: List[ImportResolver]): Checked[ImportResolver.ResolvedImportBundle] = {
    val importPath = path.replaceFirst("file://", "")
    val content = secondaryWdlFiles.get(importPath)
    val updatedResolvers = currentResolvers map {
      case d if d == this => MapResolver()
      case other => other
    }
    ResolvedImportBundle(content, updatedResolvers).validNelCheck
  }

  override def cleanupIfNecessary(): ErrorOr[Unit] = ().validNel
}
