package com.example.api.dto.board

import com.example.application.board.BoardQueryResult

object BoardResponses {
    data class BoardItem(
        val boardId: Long,
        val code: String,
        val name: String,
        val isAnonymousAllowed: Boolean
    ) {
        companion object {
            fun from(result: BoardQueryResult.BoardItem): BoardItem {
                return BoardItem(
                    boardId = result.boardId,
                    code = result.code,
                    name = result.name,
                    isAnonymousAllowed = result.isAnonymousAllowed
                )
            }
        }
    }
}
