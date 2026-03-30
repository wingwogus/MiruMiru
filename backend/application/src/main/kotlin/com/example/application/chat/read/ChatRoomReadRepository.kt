package com.example.application.chat.read

interface ChatRoomReadRepository {
    fun findMyRooms(memberId: Long, limit: Int): List<ChatQueryResult.RoomSummaryRow>
}
