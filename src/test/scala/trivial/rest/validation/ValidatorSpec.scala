package trivial.rest.validation

import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.{Failure, Currency, Gender}

class ValidatorSpec extends WordSpec with MustMatchers {
  "We don't allow clients to provide their own surrogate IDs" in {
    val validator = new Validator {}

    validator.validate(Seq(Currency(Some("Not allowed"), "BOB", "§"))) match {
      case Right(x) => fail("Should have bailed")
      case Left(Failure(code, reason)) => reason must include ("Validation failure. You can't POST an item with an ID")
    }
  }

  "We don't validate HardCoded values, because these should never change" in {
    val validator = new Validator {}

    validator.validate(Seq(Gender(false))) mustEqual Right(Seq(Gender(false)))
  }
}