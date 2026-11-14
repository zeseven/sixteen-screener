package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.ResourceNotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

val ProblemJsonContentType: ContentType = ContentType("application", "problem+json")

private val ProblemJson: Json = Json { explicitNulls = false }

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<InvalidInputException> { call, cause ->
            call.respondProblem(HttpStatusCode.BadRequest, cause.message)
        }
        exception<BadRequestException> { call, cause ->
            call.respondProblem(HttpStatusCode.BadRequest, cause.message)
        }
        exception<ContentTransformationException> { call, cause ->
            call.respondProblem(HttpStatusCode.UnsupportedMediaType, cause.message)
        }
        exception<ResourceNotFoundException> { call, cause ->
            call.respondProblem(HttpStatusCode.NotFound, cause.message)
        }
        exception<NotFoundException> { call, cause ->
            call.respondProblem(HttpStatusCode.NotFound, cause.message)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception for ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respondProblem(HttpStatusCode.InternalServerError, detail = null)
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondProblem(status, "No resource at ${call.request.local.uri}")
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respondProblem(status, detail = null)
        }
    }
}

suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, detail: String?) {
    val problem = Problem(title = status.description, status = status.value, detail = detail)
    respondText(ProblemJson.encodeToString(Problem.serializer(), problem), ProblemJsonContentType, status)
}
