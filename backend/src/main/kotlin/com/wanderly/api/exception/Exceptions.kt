package com.wanderly.api.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/** Business error that maps straight to an HTTP status. */
class ApiException(val status: HttpStatus, message: String) : RuntimeException(message) {
    companion object {
        fun notFound(what: String, id: Any?) = ApiException(HttpStatus.NOT_FOUND, "$what '$id' not found")
        fun badRequest(message: String) = ApiException(HttpStatus.BAD_REQUEST, message)
        fun conflict(message: String) = ApiException(HttpStatus.CONFLICT, message)
        fun forbidden(message: String) = ApiException(HttpStatus.FORBIDDEN, message)
    }
}

/** Every error is returned as RFC 9457 problem+json. */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ProblemDetail = ProblemDetail.forStatusAndDetail(e.status, e.message ?: "")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed").apply {
            val errors = linkedMapOf<String, String?>()
            e.bindingResult.fieldErrors.forEach { errors.putIfAbsent(it.field, it.defaultMessage) }
            setProperty("errors", errors)
        }

    /** Malformed JSON or a missing required field (Kotlin non-null property). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing a required field or is not valid JSON")

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(e: BadCredentialsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleDenied(e: AccessDeniedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have access to this resource")

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadSize(e: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (max 10 MB)")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Bad request")
}
