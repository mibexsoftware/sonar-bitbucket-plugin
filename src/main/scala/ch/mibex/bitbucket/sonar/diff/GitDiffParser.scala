package ch.mibex.bitbucket.sonar.diff

import scala.util.parsing.combinator._

// see https://www.kernel.org/pub/software/scm/git/docs/git-diff.html
object GitDiffParser extends RegexParsers {

  override def skipWhitespace = false

  sealed trait HeaderLine
  case class OldMode(mode: Int) extends HeaderLine
  case class NewMode(mode: Int) extends HeaderLine
  case class DeletedFileMode(mode: Int) extends HeaderLine
  case class NewFileMode(mode: Int) extends HeaderLine
  case class CopyFrom(filePath: String) extends HeaderLine
  case class CopyTo(filePath: String) extends HeaderLine
  case class RenameFrom(filePath: String) extends HeaderLine
  case class RenameTo(filePath: String) extends HeaderLine
  case class SimilarityIndex(percentage: Int) extends HeaderLine
  case class DissimilarityIndex(percentage: Int) extends HeaderLine
  case class Index(fromHash: String, toHash: String, mode: Option[Int] = None) extends HeaderLine

  case class ExtendedDiffHeader(headerLines: Seq[HeaderLine], index: Index)

  case class FileChange(oldFile: String, newFile: String)

  case class GitDiff(gitDiffHeader: FileChange, header: ExtendedDiffHeader, hunks: List[Hunk]) {
    val isNewFile = header.headerLines.exists {
      case NewFileMode(_) => true
      case _ => false
    }
    val isDeletedFile = header.headerLines.exists {
      case DeletedFileMode(_) => true
      case _ => false
    }
  }

  case class FromToRange(fromLineStart: Int, fromNumLines: Int = 0, toLineStart: Int, toNumLines: Int = 0)

  case class HunkHeader(fromToRange: FromToRange, context: Option[CtxLine])

  case class Hunk(hunkHeader: HunkHeader, changedLines: List[LineChange])

  sealed trait LineChange { def line: String }
  case class CtxLine(line: String) extends LineChange
  case class AddedLine(line: String) extends LineChange
  case class RemovedLine(line: String) extends LineChange

  case class ParsingFailure(reason: String)


  def allDiffs: Parser[List[GitDiff]] = rep1(gitDiff)

  def gitDiff: Parser[GitDiff] = gitDiffHeader ~ extendedDiffHeader ~ hunks ^^ { case fc ~ h ~ hs => GitDiff(fc, h, hs) }

  def gitDiffHeader: Parser[FileChange] = "diff --git " ~> ("a/" ~> filePath) ~ (" b/" ~> filePath) <~ nl ^^ {
    case oldF ~ newF => FileChange(oldF, newF)
  }

  def oldMode: Parser[OldMode] = "old mode " ~> fileMode <~ nl ^^ { m => OldMode(m) }

  def newMode: Parser[NewMode] = "new mode " ~> fileMode <~ nl ^^ { m => NewMode(m) }

  def deletedFileMode: Parser[DeletedFileMode] = "deleted file mode " ~> fileMode <~ nl ^^ { m => DeletedFileMode(m) }

  def newFileMode: Parser[NewFileMode] = "new file mode " ~> fileMode <~ nl ^^ { m => NewFileMode(m) }

  def copyFrom: Parser[CopyFrom] = "copy from " ~> filePath <~ nl ^^ { p => CopyFrom(p) }

  def copyTo: Parser[CopyTo] = "copy to " ~> filePath <~ nl ^^ { p => CopyTo(p) }

  def renameFrom: Parser[RenameFrom] = "rename from " ~> filePath <~ nl ^^ { p => RenameFrom(p) }

  def renameTo: Parser[RenameTo] = "rename to " ~> filePath <~ nl ^^ { p => RenameTo(p) }

  def similarityIndex: Parser[SimilarityIndex] = "similarity index " ~> similarity <~ "%" <~ nl ^^ {
    s => SimilarityIndex(s)
  }

  def dissimilarityIndex: Parser[DissimilarityIndex] = "dissimilarity index " ~> similarity <~ "%" <~ nl ^^ {
    s => DissimilarityIndex(s)
  }

  def index: Parser[Index] = "index " ~> hash ~ (".." ~> hash) ~ opt(" " ~> fileMode) <~ nl ^^ {
    case fh ~ th ~ optFm => Index(fh, th, optFm)
  }

  def nl: Parser[String] = """\n""".r

  def fileMode: Parser[Int] = """[0-7]{6}""".r ^^ { _.toInt }

  def filePath: Parser[String] = """[\S]+""".r

  def similarity: Parser[Int] = """\d{1,3}""".r ^^ { _.toInt }

  def hash: Parser[String] = """[0-9a-f]{7}""".r

  def extendedDiffHeader: Parser[ExtendedDiffHeader] =
    rep(oldMode | newMode | deletedFileMode | newFileMode | copyFrom | copyTo | renameFrom | renameTo
      | similarityIndex | dissimilarityIndex) ~ index ^^
      { case lines ~ i => ExtendedDiffHeader(lines, i) }


  //  --- a/builtin-http-fetch.c
  //  +++ b/http-fetch.c
  def unifiedDiffHeader: Parser[FileChange] = fromFile ~ toFile ^^ { case oldF ~ newF => FileChange(oldF, newF) }

  def hunks: Parser[List[Hunk]] = unifiedDiffHeader ~> rep1(hunk)

  def hunk: Parser[Hunk] = hunkStart ~ rep1(lineChange) ^^ { case hh ~ lines => Hunk(hh, lines) }

  def lineChange: Parser[LineChange] = ctxLine | addedLine | removedLine

  def fromToRange: Parser[FromToRange] =
    ("@@ " ~> "-" ~> num) ~ opt("," ~> num) ~ (" +" ~> num) ~ opt("," ~> num) <~ " @@" ^^ {
      case startLineFrom ~ optNumLinesFrom ~ startLineTo ~ optNumLinesTo =>
        FromToRange(startLineFrom, optNumLinesFrom.getOrElse(0), startLineTo, optNumLinesTo.getOrElse(0))
    }

  //  @@ from-file-range to-file-range @@ [header]
  def hunkStart: Parser[HunkHeader] =
    fromToRange ~ opt(ctxLine) <~ opt(nl) ^^ { case ftr ~ optC => HunkHeader(ftr, optC) }

  def ctxLine: Parser[CtxLine] = " " ~> """.*""".r <~ nl ^^ { l => CtxLine(l) }

  def addedLine: Parser[AddedLine] = "+" ~> """.*""".r <~ nl ^^ { l => AddedLine(l) }

  def removedLine: Parser[RemovedLine] = "-" ~> """.*""".r <~ nl ^^ { l => RemovedLine(l) }

  def fromFile: Parser[String] = "--- " ~> filePath <~ nl

  def toFile: Parser[String] = "+++ " ~> filePath <~ nl

  def num: Parser[Int] = """\d+""".r ^^ { _.toInt }

  def parse(diff: String): Either[ParsingFailure, List[GitDiff]] = {
    parseAll(allDiffs, diff) match {
      case Success(s, _) => Right(s)
      case NoSuccess(msg, _) => Left(ParsingFailure(msg))
    }
  }

}