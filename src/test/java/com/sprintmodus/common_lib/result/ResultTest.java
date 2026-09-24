package com.sprintmodus.common_lib.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResultTest {

	private static Result<Integer, String> parse(String text) {
		try {
			return Result.success(Integer.parseInt(text));
		}
		catch (NumberFormatException e) {
			return Result.failure("not a number: " + text);
		}
	}

	@Test
	void successExposesItsValue() {
		Result<Integer, String> result = Result.success(42);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.isFailure()).isFalse();
		assertThat(result.getValue()).isEqualTo(42);
	}

	@Test
	void failureExposesItsError() {
		Result<Integer, String> result = Result.failure("boom");

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.isFailure()).isTrue();
		assertThat(result.getError()).isEqualTo("boom");
	}

	@Test
	void readingTheWrongSideIsAProgrammingError() {
		assertThatThrownBy(() -> Result.<Integer, String>failure("boom").getValue())
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> Result.<Integer, String>success(1).getError())
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void mapTransformsOnlyTheSuccessValue() {
		assertThat(parse("20").map(n -> n * 2).getValue()).isEqualTo(40);
		assertThat(parse("x").map(n -> n * 2).getError()).isEqualTo("not a number: x");
	}

	@Test
	void flatMapChainsAndShortCircuitsOnTheFirstFailure() {
		List<String> calls = new ArrayList<>();

		Result<Integer, String> result = parse("x").flatMap(n -> {
			calls.add("second");
			return parse("2");
		});

		assertThat(result.getError()).isEqualTo("not a number: x");
		assertThat(calls).isEmpty();
		assertThat(parse("1").flatMap(n -> parse("2")).getValue()).isEqualTo(2);
	}

	@Test
	void mapErrorTransformsOnlyTheError() {
		assertThat(parse("x").mapError(String::length).getError()).isEqualTo("not a number: x".length());
		assertThat(parse("7").mapError(String::length).getValue()).isEqualTo(7);
	}

	@Test
	void tapRunsSideEffectsWithoutChangingTheResult() {
		List<String> seen = new ArrayList<>();

		Result<Integer, String> ok = parse("5").tap(n -> seen.add("value " + n)).tapError(e -> seen.add("error " + e));
		Result<Integer, String> bad = parse("x").tap(n -> seen.add("value " + n)).tapError(e -> seen.add("error " + e));

		assertThat(seen).containsExactly("value 5", "error not a number: x");
		assertThat(ok.getValue()).isEqualTo(5);
		assertThat(bad.isFailure()).isTrue();
	}

	@Test
	void foldCollapsesBothBranches() {
		assertThat(parse("3").<String>fold(n -> "ok " + n, e -> "err")).isEqualTo("ok 3");
		assertThat(parse("x").<String>fold(n -> "ok", e -> "err")).isEqualTo("err");
	}

	@Test
	void unitIsASingleValue() {
		Result<Unit, String> result = Result.success(Unit.VALUE);

		assertThat(result.getValue()).isSameAs(Unit.VALUE);
	}

}
