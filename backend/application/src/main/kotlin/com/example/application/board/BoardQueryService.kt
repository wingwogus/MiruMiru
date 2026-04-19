package com.example.application.board

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BoardQueryService(
    private val memberRepository: MemberRepository,
    private val boardRepository: BoardRepository
) {
    fun getMyBoards(userId: String): List<BoardQueryResult.BoardItem> {
        val member = findMember(userId)

        return boardRepository.findAllByUniversityIdOrderByIdAsc(member.university.id)
            .map { board ->
                BoardQueryResult.BoardItem(
                    boardId = board.id,
                    code = board.code,
                    name = board.name,
                    isAnonymousAllowed = board.isAnonymousAllowed
                )
            }
    }

    private fun findMember(userId: String): Member {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return memberRepository.findById(parsedUserId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
    }
}
