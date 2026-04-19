package com.example.application.board

object BoardQueryResult {
    data class BoardItem(
        val boardId: Long,
        val code: String,
        val name: String,
        val isAnonymousAllowed: Boolean
    )
}
