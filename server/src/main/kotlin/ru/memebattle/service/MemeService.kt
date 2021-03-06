package ru.memebattle.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.memebattle.common.dto.game.GameState
import ru.memebattle.common.dto.game.MemeResponse
import ru.memebattle.model.MemeModel
import ru.memebattle.model.UserModel
import ru.memebattle.repository.MemeRepository
import ru.memebattle.repository.RateusersRepository

class MemeService(
    private val sendResponse: SendChannel<MemeResponse>,
    private val memeRepository: MemeRepository,
    private val rateusersRepository: RateusersRepository
) {

    private var currentMemes: List<String> = emptyList()
    private var currentLikes: MutableList<Int> = mutableListOf(
        0, 0
    )
    private var firstLikes: MutableList<UserModel> = mutableListOf()
    private var secondLikes: MutableList<UserModel> = mutableListOf()
    private var state: GameState = GameState.START
    private val mutex = Mutex()

    init {
        GlobalScope.launch {
            startRound()
        }
    }

    suspend fun getCurrentState(): MemeResponse = mutex.withLock {
        MemeResponse(state, currentMemes, currentLikes)
    }

    suspend fun rateMeme(memeIndex: Int, user: UserModel?): MemeResponse =
        mutex.withLock {
            currentLikes[memeIndex] = currentLikes[memeIndex].inc()
            if (memeIndex == 0 && user != null) {
                firstLikes.add(user)
            }
            if (memeIndex == 1 && user != null) {
                secondLikes.add(user)
            }
            MemeResponse(state, currentMemes, currentLikes)
        }

    private suspend fun startRound() {

        withContext(Dispatchers.Default) {

            while (true) {
                val photos = getMemeModels().map { it.url }

                val pairs = mutex.withLock {
                    val pairs: MutableList<Pair<String, String>> = mutableListOf()
                    for (s in 0..photos.size step 2) {
                        if (s.inc() <= photos.lastIndex) {
                            pairs.add(photos[s] to photos[s.inc()])
                        }
                    }
                    pairs
                }

                pairs.forEach {

                    mutex.withLock {
                        state = GameState.MEMES

                        currentMemes = listOf(it.first, it.second)

                        sendResponse.send(MemeResponse(state, currentMemes, currentLikes))
                    }

                    delay(10000)

                    mutex.withLock {
                        state = GameState.RESULT

                        sendResponse.send(MemeResponse(state, currentMemes, currentLikes))

                        if (currentLikes[0] > currentLikes[1]) {
                            firstLikes.forEach {
                                rateusersRepository.add(it.id, it.username)
                            }
                        }

                        if (currentLikes[1] > currentLikes[0]) {
                            secondLikes.forEach {
                                rateusersRepository.add(it.id, it.username)
                            }
                        }
                    }

                    delay(5000)

                    mutex.withLock {
                        currentLikes = mutableListOf(0, 0)
                        firstLikes = mutableListOf()
                        secondLikes = mutableListOf()
                    }
                }
            }
        }
    }

    private suspend fun getMemeModels(): List<MemeModel> =
        withContext(Dispatchers.IO) {
            memeRepository.getAll()
        }
}