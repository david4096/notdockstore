/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client

import java.io.{File => JFile}

import wdl4s.formatter.{AnsiSyntaxHighlighter, HtmlSyntaxHighlighter, SyntaxFormatter}
import wdl4s.{AstTools, _}
import spray.json._

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * This exposes the Cromwell methods in an easier way to access from Java
  * until wdltool is released to artifactory.
  */
class Bridge {
  def main(args: Array[String]): Unit = {
    println("Hello, world!")
  }



  def inputs(args: Seq[String]): String = {
      loadWdl(args.head) { namespace =>
        import wdl4s.types.WdlTypeJsonFormatter._
        namespace match {
          case x: NamespaceWithWorkflow => x.workflow.inputs.toJson.prettyPrint
        }
      }
  }

  private[this] def loadWdl(path: String)(f: WdlNamespace => String): String = {
    Try(WdlNamespace.load(new JFile(path))) match {
      case Success(namespace) => f(namespace)
      case Failure(t) =>
        println(t.getMessage)
        null
    }
  }
}