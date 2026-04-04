package com.example.domain.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatReportRepository : JpaRepository<ChatReport, Long>
