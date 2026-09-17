package com.hourslot.community.services;

import com.hourslot.community.model.Community;
import com.hourslot.community.model.CommunityComment;
import com.hourslot.community.model.CommunityPost;
import com.hourslot.community.model.CommunityReaction;
import com.hourslot.community.model.CommunityReport;
import com.hourslot.community.repository.CommunityCommentRepository;
import com.hourslot.community.repository.CommunityMemberRepository;
import com.hourslot.community.repository.CommunityPostRepository;
import com.hourslot.community.repository.CommunityReactionRepository;
import com.hourslot.community.repository.CommunityReportRepository;
import com.hourslot.community.repository.CommunityRepository;
import com.hourslot.identity.model.User;
import com.hourslot.organization.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommunityService {
    private final CommunityRepository communityRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityReactionRepository reactionRepository;
    private final CommunityReportRepository reportRepository;
    private final CommunityMemberRepository memberRepository;
    private final BusinessRepository businessRepository;

    public CommunityService(
            CommunityRepository communityRepository,
            CommunityPostRepository postRepository,
            CommunityCommentRepository commentRepository,
            CommunityReactionRepository reactionRepository,
            CommunityReportRepository reportRepository,
            CommunityMemberRepository memberRepository,
            BusinessRepository businessRepository) {
        this.communityRepository = communityRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.reportRepository = reportRepository;
        this.memberRepository = memberRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional(readOnly = true)
    public List<Community> listCommunities() {
        return communityRepository.findActive();
    }

    @Transactional
    public Map<String, Object> join(User user, Long communityId) {
        requireCommunity(communityId);
        memberRepository.join(communityId, user.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("communityId", communityId);
        result.put("joined", true);
        return result;
    }

    @Transactional
    public Map<String, Object> leave(User user, Long communityId) {
        requireCommunity(communityId);
        memberRepository.leave(communityId, user.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("communityId", communityId);
        result.put("joined", false);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> postsForCommunity(Long communityId) {
        requireCommunity(communityId);
        return postRepository.findByCommunity(communityId).stream()
                .map(this::toPostView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> postDetail(Long postId) {
        CommunityPost post = requirePost(postId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("post", toPostView(post));
        map.put("comments", commentRepository.findByPost(postId));
        map.put("reactions", reactionRepository.findByPost(postId));
        return map;
    }

    @Transactional
    public CommunityPost createPost(User author, Long communityId, String category, String title, String body,
                                    Long providerBusinessId) {
        requireCommunity(communityId);
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("Title and body are required.");
        }
        return postRepository.save(CommunityPost.builder()
                .communityId(communityId)
                .authorUserId(author.getId())
                .providerBusinessId(providerBusinessId)
                .category(category == null ? "GENERAL" : category)
                .title(title.trim())
                .body(body.trim())
                .build());
    }

    @Transactional
    public CommunityComment addComment(User author, Long postId, String body) {
        requirePost(postId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body is required.");
        }
        return commentRepository.save(CommunityComment.builder()
                .postId(postId)
                .authorUserId(author.getId())
                .body(body.trim())
                .build());
    }

    @Transactional
    public CommunityReaction react(User user, Long postId, String reactionType) {
        requirePost(postId);
        CommunityReaction reaction = CommunityReaction.builder()
                .postId(postId)
                .userId(user.getId())
                .reactionType(reactionType == null ? "LIKE" : reactionType.toUpperCase())
                .build();
        reactionRepository.upsert(reaction);
        return reaction;
    }

    @Transactional
    public CommunityReport reportPost(User reporter, Long postId, String reason, String description) {
        requirePost(postId);
        return reportRepository.save(CommunityReport.builder()
                .postId(postId)
                .reportedByUserId(reporter.getId())
                .reason(reason == null ? "OTHER" : reason)
                .description(description)
                .build());
    }

    @Transactional
    public CommunityReport reportComment(User reporter, Long commentId, String reason, String description) {
        CommunityComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found."));
        return reportRepository.save(CommunityReport.builder()
                .postId(comment.getPostId())
                .commentId(commentId)
                .reportedByUserId(reporter.getId())
                .reason(reason == null ? "OTHER" : reason)
                .description(description)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CommunityReport> pendingReports() {
        return reportRepository.findByStatus("PENDING");
    }

    @Transactional
    public CommunityReport reviewReport(User admin, Long reportId, String status, String action) {
        CommunityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found."));
        report.setStatus(status == null ? "REVIEWED" : status.toUpperCase());
        report.setReviewedByUserId(admin.getId());
        report.setReviewedAt(LocalDateTime.now());
        reportRepository.save(report);
        if ("REMOVE_POST".equalsIgnoreCase(action) && report.getPostId() != null) {
            postRepository.softDelete(report.getPostId());
        }
        if ("REMOVE_COMMENT".equalsIgnoreCase(action) && report.getCommentId() != null) {
            commentRepository.softDelete(report.getCommentId());
        }
        return report;
    }

    private Map<String, Object> toPostView(CommunityPost post) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", post.getId());
        row.put("communityId", post.getCommunityId());
        row.put("authorUserId", post.getAuthorUserId());
        row.put("providerBusinessId", post.getProviderBusinessId());
        row.put("category", post.getCategory());
        row.put("title", post.getTitle());
        row.put("body", post.getBody());
        row.put("status", post.getStatus());
        row.put("createdAt", post.getCreatedAt());
        row.put("updatedAt", post.getUpdatedAt());
        if (post.getProviderBusinessId() != null) {
            businessRepository.findById(post.getProviderBusinessId()).ifPresent(business -> {
                Map<String, Object> provider = new LinkedHashMap<>();
                provider.put("id", business.getId());
                provider.put("name", business.getName());
                provider.put("slug", business.getSlug());
                provider.put("verified", business.isVerified());
                row.put("provider", provider);
            });
        }
        return row;
    }

    private Community requireCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new RuntimeException("Community not found."));
    }

    private CommunityPost requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found."));
    }
}
