package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface MessageRoomRepository : JpaRepository<MessageRoom, Long>, MessageRoomQueryRepository
