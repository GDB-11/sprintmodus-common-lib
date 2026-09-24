package com.sprintmodus.common_lib.result;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The outcome of an operation that can fail for a business reason: either a {@link Success} carrying a value or a
 * {@link Failure} carrying an error. Use cases return it instead of throwing, so the possible failures are part of
 * the method signature. Infrastructure failures (database down, missing configuration) are still exceptions.
 *
 * @param <T> the success value
 * @param <E> the error, normally a sealed {@link ApplicationError} hierarchy
 */
public sealed interface Result<T, E> {

	record Success<T, E>(T value) implements Result<T, E> {
	}

	record Failure<T, E>(E error) implements Result<T, E> {
	}

	static <T, E> Result<T, E> success(T value) {
		return new Success<>(value);
	}

	static <T, E> Result<T, E> failure(E error) {
		return new Failure<>(error);
	}

	default boolean isSuccess() {
		return this instanceof Success;
	}

	default boolean isFailure() {
		return this instanceof Failure;
	}

	/** The success value. Calling this on a failure is a programming error. */
	default T getValue() {
		return switch (this) {
			case Success<T, E> success -> success.value();
			case Failure<T, E> failure -> throw new IllegalStateException("Result is a failure: " + failure.error());
		};
	}

	/** The error. Calling this on a success is a programming error. */
	default E getError() {
		return switch (this) {
			case Failure<T, E> failure -> failure.error();
			case Success<T, E> _ -> throw new IllegalStateException("Result is a success");
		};
	}

	default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
		return switch (this) {
			case Success<T, E> success -> Result.success(mapper.apply(success.value()));
			case Failure<T, E> failure -> Result.failure(failure.error());
		};
	}

	default <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> mapper) {
		return switch (this) {
			case Success<T, E> success -> mapper.apply(success.value());
			case Failure<T, E> failure -> Result.failure(failure.error());
		};
	}

	/** Runs a side effect on the success value and returns this result unchanged. */
	default Result<T, E> tap(Consumer<? super T> action) {
		if (this instanceof Success<T, E> success) {
			action.accept(success.value());
		}
		return this;
	}

	/** Runs a side effect on the error and returns this result unchanged. */
	default Result<T, E> tapError(Consumer<? super E> action) {
		if (this instanceof Failure<T, E> failure) {
			action.accept(failure.error());
		}
		return this;
	}

	default <U> Result<T, U> mapError(Function<? super E, ? extends U> mapper) {
		return switch (this) {
			case Success<T, E> success -> Result.success(success.value());
			case Failure<T, E> failure -> Result.failure(mapper.apply(failure.error()));
		};
	}

	/** Collapses both branches into one value, e.g. to build an HTTP response. */
	default <R> R fold(Function<? super T, ? extends R> onSuccess, Function<? super E, ? extends R> onFailure) {
		return switch (this) {
			case Success<T, E> success -> onSuccess.apply(success.value());
			case Failure<T, E> failure -> onFailure.apply(failure.error());
		};
	}

}
