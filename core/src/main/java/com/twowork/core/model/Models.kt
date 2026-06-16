package com.twowork.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Errors / generic
// ---------------------------------------------------------------------------
@Serializable
data class ApiError(val error: String = "Request failed")

// ---------------------------------------------------------------------------
// Auth & identity
// ---------------------------------------------------------------------------
@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    val role: String = "",
    @SerialName("kyc_status") val kycStatus: String? = null,
    @SerialName("payout_status") val payoutStatus: String? = null,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("email_verified_at") val emailVerifiedAt: String? = null,
    @SerialName("mobile_verified_at") val mobileVerifiedAt: String? = null,
    @SerialName("admin_access") val adminAccess: Boolean = false
) {
    val isClient get() = role == "client"
    val isFreelancer get() = role == "freelancer"
    val isAdmin get() = role == "admin"
}

@Serializable
data class MeResponse(val user: User? = null)

@Serializable
data class RegisterResponse(
    val user: User? = null,
    @SerialName("requiresEmailVerification") val requiresEmailVerification: Boolean = false,
    @SerialName("emailSent") val emailSent: Boolean = false
)

@Serializable
data class MessageResponse(val message: String = "")

@Serializable
data class LoginRequest(val email: String, val password: String, val totpCode: String? = null)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: String,
    // Server validates these with z.literal(true). They must be present in the
    // body, so force encoding even though they equal their default (the global
    // Json has encodeDefaults=false, which would otherwise drop them).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val termsAccepted: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val privacyAccepted: Boolean = true
)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class TokenRequest(val token: String)

@Serializable
data class ResetRequest(val token: String, val password: String)

@Serializable
class EmptyBody

// ---------------------------------------------------------------------------
// Profiles, verification, dashboard
// ---------------------------------------------------------------------------
@Serializable
data class Profile(
    // client fields
    @SerialName("company_name") val companyName: String? = null,
    val description: String? = null,
    val website: String? = null,
    @SerialName("contact_title") val contactTitle: String? = null,
    @SerialName("organisation_type") val organisationType: String? = null,
    @SerialName("mobile_number") val mobileNumber: String? = null,
    @SerialName("billing_state") val billingState: String? = null,
    // freelancer fields
    val handle: String? = null,
    val headline: String? = null,
    val bio: String? = null,
    val skills: List<String> = emptyList(),
    @SerialName("hourly_rate_paise") val hourlyRatePaise: Long? = null,
    @SerialName("availability") val availability: String? = null,
    @SerialName("preferred_engagement") val preferredEngagement: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("verification_state") val verificationState: String? = null,
    // shared
    val location: String? = null
)

@Serializable
data class VerificationRequest(
    val id: String? = null,
    val status: String? = null,
    @SerialName("verification_type") val verificationType: String? = null
)

@Serializable
data class ProfileResponse(
    val user: User? = null,
    val profile: Profile? = null,
    val verification: VerificationRequest? = null,
    val verificationType: String? = null
)

@Serializable
data class Stats(
    val projects: Int? = null,
    val proposals: Int? = null,
    val contracts: Int? = null,
    @SerialName("active_milestones") val activeMilestones: Int? = null,
    val ratings: Int? = null,
    val conversations: Int? = null,
    val users: Int? = null,
    @SerialName("pending_verifications") val pendingVerifications: Int? = null
)

@Serializable
data class DashboardResponse(val stats: Stats = Stats())

@Serializable
data class ClientProfileRequest(
    val companyName: String,
    val description: String = "",
    val website: String = "",
    val contactTitle: String = "",
    val location: String = "",
    val organisationType: String = "",
    val mobileNumber: String = ""
)

@Serializable
data class PortfolioItem(val title: String, val url: String)

@Serializable
data class FreelancerProfileRequest(
    val headline: String = "",
    val bio: String = "",
    val skills: List<String> = emptyList(),
    val hourlyRate: String? = null,
    val portfolio: List<PortfolioItem> = emptyList(),
    val location: String = "",
    val availability: String = "",
    val preferredEngagement: String = "",
    val handle: String? = null,
    val isPublic: Boolean = false
)

@Serializable
data class VerificationSubmitRequest(
    val legalName: String,
    val country: String,
    val pan: String,
    val aadhaar: String = "",
    val gstin: String = "",
    val organisationName: String = "",
    val registrationReference: String = "",
    val payoutReady: Boolean = false,
    val checks: Map<String, Boolean> = emptyMap()
)

// ---------------------------------------------------------------------------
// Discovery — projects & talent
// ---------------------------------------------------------------------------
@Serializable
data class Project(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("required_skills") val requiredSkills: List<String> = emptyList(),
    @SerialName("budget_paise") val budgetPaise: Long = 0,
    val currency: String = "INR",
    val status: String = "",
    @SerialName("project_type") val projectType: String = "fixed",
    @SerialName("experience_level") val experienceLevel: String = "",
    val duration: String? = null,
    val deadline: String? = null,
    @SerialName("is_sealed") val isSealed: Boolean = false,
    @SerialName("nda_required") val ndaRequired: Boolean = false,
    @SerialName("allow_messages") val allowMessages: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("proposal_count") val proposalCount: Int? = null,
    @SerialName("client_rating") val clientRating: String? = null,
    @SerialName("client_rating_count") val clientRatingCount: Int = 0
)

@Serializable
data class ProjectListResponse(
    val projects: List<Project> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val savedIds: List<String> = emptyList()
)

@Serializable
data class CreatedProjectResponse(val project: Project? = null, val notice: String = "")

@Serializable
data class Freelancer(
    @SerialName("full_name") val fullName: String = "",
    val handle: String? = null,
    val headline: String? = null,
    val skills: List<String> = emptyList(),
    @SerialName("hourly_rate_paise") val hourlyRatePaise: Long? = null,
    val location: String? = null,
    val availability: String? = null,
    val rating: String? = null,
    @SerialName("completed_ratings") val completedRatings: Int = 0,
    val badges: List<SkillBadge> = emptyList()
)

@Serializable
data class FreelancerListResponse(
    val freelancers: List<Freelancer> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class MatchingResponse(val matches: List<Project> = emptyList())

// ---------------------------------------------------------------------------
// Projects (host) & proposals
// ---------------------------------------------------------------------------
@Serializable
data class ProjectRequest(
    val title: String,
    val description: String,
    val requiredSkills: List<String>,
    val budget: String,
    val projectType: String = "fixed",
    val experienceLevel: String = "intermediate",
    val duration: String = "",
    val deadline: String = "",
    val isSealed: Boolean = false,
    val ndaRequired: Boolean = false,
    val allowMessages: Boolean = true
)

@Serializable
data class MyProjectsResponse(val projects: List<Project> = emptyList())

@Serializable
data class Proposal(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    @SerialName("freelancer_id") val freelancerId: String = "",
    @SerialName("cover_letter") val coverLetter: String = "",
    @SerialName("amount_paise") val amountPaise: Long = 0,
    @SerialName("duration_days") val durationDays: Int = 0,
    val status: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("freelancer_name") val freelancerName: String? = null,
    val skills: List<String> = emptyList(),
    @SerialName("verification_state") val verificationState: String? = null,
    val attachments: List<Attachment> = emptyList()
)

@Serializable
data class ProposalsResponse(val proposals: List<Proposal> = emptyList())

@Serializable
data class ProposalRequest(
    val coverLetter: String,
    val amount: String,
    val durationDays: Int,
    val attachmentIds: List<String> = emptyList()
)

@Serializable
data class MilestoneInput(val title: String, val amount: String)

@Serializable
data class AcceptRequest(val milestones: List<MilestoneInput>)

// ---------------------------------------------------------------------------
// Contracts, milestones, deliverables, disputes, ratings, payments
// ---------------------------------------------------------------------------
@Serializable
data class Milestone(
    val id: String = "",
    @SerialName("sequence_no") val sequenceNo: Int = 0,
    val title: String = "",
    @SerialName("amount_paise") val amountPaise: Long = 0,
    val currency: String = "INR",
    val status: String = ""
)

@Serializable
data class Deliverable(
    val id: String = "",
    @SerialName("milestone_id") val milestoneId: String = "",
    @SerialName("revision_no") val revisionNo: Int = 0,
    val summary: String = "",
    @SerialName("artifact_url") val artifactUrl: String = "",
    val status: String = "",
    @SerialName("client_feedback") val clientFeedback: String? = null
)

@Serializable
data class Payment(
    val id: String = "",
    @SerialName("milestone_id") val milestoneId: String = "",
    @SerialName("checkout_reference") val checkoutReference: String = "",
    @SerialName("amount_paise") val amountPaise: Long = 0,
    val status: String = "",
    val provider: String? = null
)

@Serializable
data class Dispute(
    val id: String = "",
    @SerialName("milestone_id") val milestoneId: String = "",
    val reason: String = "",
    val status: String = "",
    @SerialName("decision_notes") val decisionNotes: String? = null
)

@Serializable
data class Rating(
    val id: String = "",
    @SerialName("milestone_id") val milestoneId: String = "",
    val score: Int = 0,
    val review: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Contract(
    val id: String = "",
    val status: String = "",
    @SerialName("total_paise") val totalPaise: Long = 0,
    val currency: String = "INR",
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("project_title") val projectTitle: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("freelancer_name") val freelancerName: String? = null,
    val milestones: List<Milestone> = emptyList(),
    val deliverables: List<Deliverable> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val disputes: List<Dispute> = emptyList(),
    val ratings: List<Rating> = emptyList(),
    @SerialName("client_ratings") val clientRatings: List<Rating> = emptyList()
)

@Serializable
data class ContractsResponse(val contracts: List<Contract> = emptyList())

@Serializable
data class DeliverableRequest(val summary: String, val artifactUrl: String)

@Serializable
data class FeedbackRequest(val feedback: String)

@Serializable
data class DisputeRequest(val reason: String, val evidence: String, val artifactUrl: String = "")

@Serializable
data class RatingRequest(val score: Int, val review: String = "")

@Serializable
data class RazorpayOrder(
    val keyId: String = "",
    val orderId: String = "",
    val amount: Long = 0,
    val currency: String = "INR"
)

@Serializable
data class FundingIntentResponse(
    val payment: Payment? = null,
    val paymentMode: String = "demo",
    val razorpay: RazorpayOrder? = null
)

// ---------------------------------------------------------------------------
// Skill assessments
// ---------------------------------------------------------------------------
@Serializable
data class AssessmentsResponse(
    val assessments: List<AssessmentItem> = emptyList(),
    val badges: List<SkillBadge> = emptyList()
)

@Serializable
data class AssessmentItem(
    val skill: String,
    val maxLevel: Int = 1,
    val badgeLevel: Int = 0,
    val nextLevel: Int? = null,
    val feePaise: Long? = null
)

@Serializable
data class SkillBadge(val skill: String, val level: Int)

@Serializable
data class AssessmentAttempt(
    val id: String,
    val skill: String = "",
    val level: Int = 0,
    val status: String = "",
    @SerialName("payment_status") val paymentStatus: String = "",
    @SerialName("fee_paise") val feePaise: Long = 0
)

@Serializable
data class StartAttemptResponse(
    val attempt: AssessmentAttempt,
    val reference: String? = null,
    val paymentMode: String = "demo",
    val razorpay: RazorpayOrder? = null
)

@Serializable
data class AttemptInfo(val id: String, val skill: String = "", val level: Int = 0, val total: Int = 0)

@Serializable
data class QuestionOption(val key: String, val text: String)

@Serializable
data class AssessmentQuestion(
    val id: String,
    val question: String,
    val options: List<QuestionOption> = emptyList()
)

@Serializable
data class AttemptQuestionsResponse(
    val attempt: AttemptInfo,
    val questions: List<AssessmentQuestion> = emptyList()
)

@Serializable
data class SubmitAnswersRequest(val answers: Map<String, String>)

@Serializable
data class SubmitResultResponse(
    val passed: Boolean = false,
    val score: Int = 0,
    val total: Int = 0,
    val badge: SkillBadge? = null
)

// ---------------------------------------------------------------------------
// In-app update (self-hosted version manifest)
// ---------------------------------------------------------------------------
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val mandatory: Boolean = false,
    val notes: String = ""
)

// ---------------------------------------------------------------------------
// Conversations / messages
// ---------------------------------------------------------------------------
@Serializable
data class Conversation(
    val id: String = "",
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("project_title") val projectTitle: String? = null,
    @SerialName("is_sealed") val isSealed: Boolean = false,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("freelancer_name") val freelancerName: String? = null,
    @SerialName("latest_message") val latestMessage: String? = null,
    val status: String = "open"
)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation> = emptyList())

@Serializable
data class Message(
    val id: String = "",
    @SerialName("thread_id") val threadId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    val body: String = "",
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_role") val senderRole: String? = null,
    val attachments: List<Attachment> = emptyList()
)

@Serializable
data class MessagesResponse(val thread: Conversation? = null, val messages: List<Message> = emptyList())

@Serializable
data class MessageRequest(
    val message: String,
    val attachmentUrl: String = "",
    val attachmentIds: List<String> = emptyList()
)

@Serializable
data class Attachment(
    val id: String,
    val fileName: String = "file",
    val mimeType: String = "application/octet-stream",
    val byteSize: Long = 0
)

@Serializable
data class AttachmentUploadResponse(val attachment: Attachment)

@Serializable
data class ConversationRef(val id: String)

@Serializable
data class OpenConversationResponse(val thread: ConversationRef)

// ---------------------------------------------------------------------------
// Notifications, invitations, saved, reports, metrics
// ---------------------------------------------------------------------------
@Serializable
data class Notification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NotificationFeed(
    val notifications: List<Notification> = emptyList(),
    val unread: Int = 0
)

@Serializable
data class NotificationReadRequest(val id: String? = null)

@Serializable
data class UnreadResponse(val unread: Int = 0)

@Serializable
data class Invitation(
    val id: String = "",
    val message: String = "",
    val status: String = "sent",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("project_id") val projectId: String = "",
    @SerialName("project_title") val projectTitle: String? = null,
    @SerialName("budget_paise") val budgetPaise: Long = 0,
    val currency: String = "INR",
    @SerialName("client_name") val clientName: String? = null
)

@Serializable
data class InvitationsResponse(val invitations: List<Invitation> = emptyList())

@Serializable
data class InviteRequest(val handle: String, val message: String = "")

@Serializable
data class ReportRequest(val entityType: String, val entityId: String, val reason: String)

@Serializable
data class SavedResponse(val saved: Boolean = false, val projectId: String = "")

@Serializable
data class Metrics(
    @SerialName("projects_hosted") val projectsHosted: Int = 0,
    @SerialName("projects_completed") val projectsCompleted: Int = 0,
    @SerialName("talents_joined") val talentsJoined: Int = 0,
    @SerialName("visitors_today") val visitorsToday: Int = 0,
    @SerialName("total_visits") val totalVisits: String? = null
)

@Serializable
data class MetricsResponse(val metrics: Metrics = Metrics())
