package ch.mibex.bitbucket.sonar.diff

import org.scalatest.FunSuite

class GitDiffParserTest extends FunSuite {
  private def readFile(path: String) =
    scala.io.Source.fromInputStream(getClass.getResourceAsStream(path)).mkString.replaceAll("\u0085", "")

  test("The Binary files differ case should be correctly parsed") {
    val response = GitDiffParser.parse(readFile("/diffs/diff-binary-files.txt"))
    assert(response.isRight)
  }

  test("The File excluded by pattern case should be correctly parsed") {
    val response = GitDiffParser.parse(readFile("/diffs/diff-excluded-file.txt"))
    assert(response.isRight)
  }
}