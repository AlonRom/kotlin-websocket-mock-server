package com.websocketmockserver.actions

import com.websocketmockserver.models.ApiRequest
import com.websocketmockserver.models.ApiResponse

interface ActionHandler {
    suspend fun handle(request: ApiRequest): ApiResponse
}

