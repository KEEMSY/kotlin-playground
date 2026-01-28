package com.playground.webflux.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.random.Random

data class News(val id: Int, val title: String, val timestamp: LocalDateTime)
data class StockPrice(val symbol: String, val price: Double, val timestamp: LocalDateTime)

@Service
class StreamingService {

    fun getNewsStream(): Flow<News> = flow {
        var count = 1
        while (true) {
            emit(News(count++, "실시간 뉴스 제목 #${Random.nextInt(1000, 9999)}", LocalDateTime.now()))
            delay(1000)
        }
    }

    fun getStockPriceStream(symbol: String): Flow<StockPrice> = flow {
        var currentPrice = 100.0
        while (true) {
            currentPrice += Random.nextDouble(-1.0, 1.0)
            emit(StockPrice(symbol, currentPrice, LocalDateTime.now()))
            delay(500)
        }
    }

    fun getCombinedStream(): Flow<String> {
        val news = getNewsStream().map { "[뉴스] ${it.title}" }
        val stocks = getStockPriceStream("KOTL").map { "[주식] KOTL: ${String.format("%.2f", it.price)}" }

        return merge(news, stocks)
            .onStart { emit("--- 스트리밍 시작 ---") }
            .catch { e -> emit("에러 발생: ${e.message}") }
    }
}
