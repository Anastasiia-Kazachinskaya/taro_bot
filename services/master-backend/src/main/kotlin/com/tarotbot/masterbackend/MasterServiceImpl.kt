package com.tarotbot.masterbackend

import com.tarotbot.masterbackend.orchestration.MasterOrchestrator
import com.tarotbot.proto.gateway.IncomingUpdate
import com.tarotbot.proto.gateway.MasterServiceGrpcKt
import com.tarotbot.proto.gateway.OutgoingResponse

class MasterServiceImpl(private val orchestrator: MasterOrchestrator) : MasterServiceGrpcKt.MasterServiceCoroutineImplBase() {
    override suspend fun handleUpdate(request: IncomingUpdate): OutgoingResponse = orchestrator.handle(request)
}
