package com.playground.webflux.controller

import com.playground.webflux.service.News
import com.playground.webflux.service.StockPrice
import com.playground.webflux.service.StreamingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stream")
@Tag(name = "Streaming", description = "Kotlin Flow Streaming APIs (SSE)")
class StreamingController(
    private val streamingService: StreamingService
) {

    @Operation(summary = "Real-time news stream (SSE)")
    @GetMapping("/news", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getNewsStream(): Flow<News> {
        return streamingService.getNewsStream()
    }

    @Operation(summary = "Stock price stream for a symbol (SSE)")
    @GetMapping("/stocks/{symbol}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getStockPriceStream(@PathVariable symbol: String): Flow<StockPrice> {
        return streamingService.getStockPriceStream(symbol)
    }

    @Operation(summary = "Combined stream (News + Stocks) limited to 20 events")
    @GetMapping("/combined", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getCombinedStream(): Flow<String> {
        return streamingService.getCombinedStream().take(20)
    }
}
