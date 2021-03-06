package ru.memebattle.route

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.websocket.webSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BroadcastChannel
import ru.memebattle.auth.BasicAuth
import ru.memebattle.auth.JwtAuth
import ru.memebattle.common.dto.AuthenticationRequestDto
import ru.memebattle.common.dto.game.MemeRequest
import ru.memebattle.common.dto.game.MemeResponse
import ru.memebattle.common.dto.user.UserRegisterRequestDto
import ru.memebattle.common.model.RatingModel
import ru.memebattle.model.UserModel
import ru.memebattle.model.toDto
import ru.memebattle.repository.RateusersRepository
import ru.memebattle.service.MemeService
import ru.memebattle.service.UserService

class RoutingV1(
    private val userService: UserService,
    private val memeService: MemeService,
    private val rateusersRepository: RateusersRepository,
    private val memeChannel: BroadcastChannel<MemeResponse>,
    private val gson: Gson
) {
    fun setup(configuration: Routing) {
        with(configuration) {
            route("/api/v1/") {

                route("/") {
                    post("/registration") {
                        val input = call.receive<UserRegisterRequestDto>()
                        val response = userService.register(input.username, input.password)
                        call.respond(response)
                    }

                    post("/authentication") {
                        val input = call.receive<AuthenticationRequestDto>()
                        val response = userService.authenticate(input)
                        call.respond(response)
                    }

                    get("/rating") {
                        val response = rateusersRepository.getAll()
                            .mapIndexed { index, rateuserModel ->
                                RatingModel(
                                    rateuserModel.name,
                                    rateuserModel.likes,
                                    index.toLong()
                                )
                            }
                        call.respond(response)
                    }
                }

                authenticate(BasicAuth.NAME, JwtAuth.NAME) {
                    route("/me") {
                        get {
                            call.respond(requireNotNull(me).toDto())
                        }
                    }

                    webSocket {
                        outgoing.send(Frame.Text(gson.toJson(memeService.getCurrentState())))

                        val memes = async {
                            for (memes in memeChannel.openSubscription()) {
                                if (!outgoing.isClosedForSend) {
                                    outgoing.send(Frame.Text(gson.toJson(memes)))
                                }
                            }
                        }

                        val frames = async {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val user = call.authentication.principal<UserModel>()
                                        val memeRequest =
                                            gson.fromJson(frame.readText(), MemeRequest::class.java)
                                        memeService.rateMeme(memeRequest.number, user)
                                    }
                                }
                            }
                        }

                        memes.await()
                        frames.await()
                    }
                }
            }
        }
    }
}