package com.example.application.post

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostAnonymousMappingRepository
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PostQueryService(
    private val memberRepository: MemberRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val postImageRepository: PostImageRepository,
    private val postLikeRepository: PostLikeRepository,
    private val commentRepository: CommentRepository,
    private val postAnonymousMappingRepository: PostAnonymousMappingRepository
) {
    fun getBoardPosts(userId: String, boardId: Long): List<PostQueryResult.PostListItem> {
        val member = findMember(userId)
        ensureBoardAccessible(member, boardId)
        val posts = postRepository.findAllByBoardIdAndBoardUniversityIdAndIsDeletedFalseOrderByCreatedAtDesc(boardId, member.university.id)
        return posts.toPostListItems()
    }

    fun getHotPosts(userId: String): List<PostQueryResult.HotPostItem> {
        val member = findMember(userId)
        val posts = postRepository.findAllByBoardUniversityIdAndIsDeletedFalseAndLikeCountGreaterThanEqualAndCreatedAtGreaterThanEqual(
            universityId = member.university.id,
            likeCount = HOT_POST_MIN_LIKE_COUNT,
            createdAt = LocalDateTime.now().minusDays(HOT_POST_LOOKBACK_DAYS),
            pageable = PageRequest.of(
                0,
                HOT_POST_LIMIT,
                Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
                )
            )
        )
        val anonNumbersByPostAndMember = getAnonNumbersByPostAndMember(posts)

        return posts.map { post ->
            PostQueryResult.HotPostItem(
                postId = post.id,
                boardId = post.board.id,
                boardCode = post.board.code,
                boardName = post.board.name,
                title = post.title,
                authorDisplayName = hotAuthorDisplayName(post, anonNumbersByPostAndMember),
                isAnonymous = post.isAnonymous,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                createdAt = post.createdAt?.toString().orEmpty()
            )
        }
    }

    fun getPostDetail(userId: String, postId: Long): PostQueryResult.PostDetail {
        val member = findMember(userId)
        val post = postRepository.findByIdAndBoardUniversityIdAndIsDeletedFalse(postId, member.university.id)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
        val anonNumbersByMemberId = postAnonymousMappingRepository.findAllByPostId(post.id)
            .associate { mapping -> mapping.member.id to mapping.anonNumber }
        val images = postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(post.id)
            .map { image ->
                PostQueryResult.PostImageItem(
                    imageUrl = image.imageUrl,
                    displayOrder = image.displayOrder
                )
            }
        val comments = buildCommentTree(
            member = member,
            comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(post.id),
            anonNumbersByMemberId = anonNumbersByMemberId
        )

        return PostQueryResult.PostDetail(
            postId = post.id,
            boardId = post.board.id,
            boardCode = post.board.code,
            boardName = post.board.name,
            title = post.title,
            content = post.content,
            authorDisplayName = authorDisplayName(post, anonNumbersByMemberId),
            isAnonymous = post.isAnonymous,
            isMine = post.member.id == member.id,
            isLikedByMe = postLikeRepository.existsByPostIdAndMemberId(post.id, member.id),
            likeCount = post.likeCount,
            commentCount = post.commentCount,
            comments = comments,
            images = images,
            createdAt = post.createdAt?.toString().orEmpty(),
            updatedAt = post.updatedAt?.toString().orEmpty()
        )
    }

    private fun ensureBoardAccessible(member: Member, boardId: Long) {
        boardRepository.findByIdAndUniversityId(boardId, member.university.id)
            ?: throw BusinessException(ErrorCode.BOARD_NOT_FOUND)
    }

    private fun List<Post>.toPostListItems(): List<PostQueryResult.PostListItem> {
        val anonNumbersByPostAndMember = getAnonNumbersByPostAndMember(this)

        return map { post ->
            PostQueryResult.PostListItem(
                postId = post.id,
                title = post.title,
                authorDisplayName = postListAuthorDisplayName(post, anonNumbersByPostAndMember),
                isAnonymous = post.isAnonymous,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                createdAt = post.createdAt?.toString().orEmpty()
            )
        }
    }

    private fun getAnonNumbersByPostAndMember(posts: List<Post>): Map<Pair<Long, Long>, Int> {
        if (posts.isEmpty()) {
            return emptyMap()
        }

        return postAnonymousMappingRepository.findAllByPostIdIn(posts.map { it.id })
            .associateBy({ it.post.id to it.member.id }, { it.anonNumber })
    }

    private fun buildCommentTree(
        member: Member,
        comments: List<Comment>,
        anonNumbersByMemberId: Map<Long, Int>
    ): List<PostQueryResult.CommentItem> {
        val childrenByParentId = comments.filter { it.parent != null }
            .groupBy { it.parent!!.id }

        return comments.filter { it.parent == null }
            .map { root ->
                root.toCommentItem(
                    currentMember = member,
                    anonNumbersByMemberId = anonNumbersByMemberId,
                    children = childrenByParentId[root.id].orEmpty()
                        .sortedBy { it.createdAt }
                        .map { child ->
                            child.toCommentItem(
                                currentMember = member,
                                anonNumbersByMemberId = anonNumbersByMemberId,
                                children = emptyList()
                            )
                        }
                )
            }
            .sortedBy { it.createdAt }
    }

    private fun Comment.toCommentItem(
        currentMember: Member,
        anonNumbersByMemberId: Map<Long, Int>,
        children: List<PostQueryResult.CommentItem>
    ): PostQueryResult.CommentItem {
        return PostQueryResult.CommentItem(
            commentId = id,
            parentId = parent?.id,
            content = if (isDeleted) "삭제된 댓글입니다." else content,
            authorDisplayName = if (isDeleted) "알 수 없음" else commentAuthorDisplayName(this, anonNumbersByMemberId),
            isAnonymous = isAnonymous,
            isMine = member.id == currentMember.id,
            isDeleted = isDeleted,
            createdAt = createdAt?.toString().orEmpty(),
            children = children
        )
    }

    private fun authorDisplayName(post: Post, anonNumbersByMemberId: Map<Long, Int>): String {
        return if (post.isAnonymous) {
            anonNumbersByMemberId[post.member.id]?.let { anonNumber -> "익명 $anonNumber" } ?: "익명"
        } else {
            post.member.nickname
        }
    }

    private fun postListAuthorDisplayName(post: Post, anonNumbersByPostAndMember: Map<Pair<Long, Long>, Int>): String {
        return hotAuthorDisplayName(post, anonNumbersByPostAndMember)
    }

    private fun hotAuthorDisplayName(post: Post, anonNumbersByPostAndMember: Map<Pair<Long, Long>, Int>): String {
        return if (post.isAnonymous) {
            anonNumbersByPostAndMember[post.id to post.member.id]?.let { anonNumber -> "익명 $anonNumber" } ?: "익명"
        } else {
            post.member.nickname
        }
    }

    private fun commentAuthorDisplayName(comment: Comment, anonNumbersByMemberId: Map<Long, Int>): String {
        return if (comment.isAnonymous) {
            anonNumbersByMemberId[comment.member.id]?.let { anonNumber -> "익명 $anonNumber" } ?: "익명"
        } else {
            comment.member.nickname
        }
    }

    private fun findMember(userId: String): Member {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return memberRepository.findById(parsedUserId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
    }

    companion object {
        private const val HOT_POST_LOOKBACK_DAYS = 7L
        private const val HOT_POST_MIN_LIKE_COUNT = 1
        private const val HOT_POST_LIMIT = 20
    }
}
