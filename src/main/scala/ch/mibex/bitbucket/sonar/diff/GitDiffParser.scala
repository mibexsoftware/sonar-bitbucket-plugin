  case class ExtendedDiffHeader(headerLines: Seq[HeaderLine], index: Option[Index])
  def nl: Parser[String] = """(\r?\n)+""".r
    ) ~ opt(index) ^^