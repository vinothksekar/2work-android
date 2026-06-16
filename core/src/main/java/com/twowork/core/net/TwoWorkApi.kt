package com.twowork.core.net

import com.twowork.core.model.*
import kotlinx.serialization.json.JsonElement
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TwoWorkApi {

    // ---- Auth ----
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): JsonElement

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body body: EmptyBody = EmptyBody()): JsonElement

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body body: TokenRequest): MessageResponse

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body body: EmailRequest): MessageResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: EmailRequest): MessageResponse

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetRequest): MessageResponse

    // ---- Profile / verification / dashboard ----
    @GET("api/profile")
    suspend fun profile(): ProfileResponse

    @PUT("api/client/profile")
    suspend fun saveClientProfile(@Body body: ClientProfileRequest): JsonElement

    @PUT("api/freelancer/profile")
    suspend fun saveFreelancerProfile(@Body body: FreelancerProfileRequest): JsonElement

    @POST("api/verification/submit")
    suspend fun submitVerification(@Body body: VerificationSubmitRequest): JsonElement

    @POST("api/verification/documents")
    suspend fun uploadVerificationDocument(@Query("type") type: String, @Body image: RequestBody): JsonElement

    @POST("api/profile/media")
    suspend fun uploadProfileMedia(
        @Query("kind") kind: String,
        @Query("caption") caption: String,
        @Body image: RequestBody
    ): ProfileMediaResponse

    @DELETE("api/profile/media/{id}")
    suspend fun deleteProfileMedia(@Path("id") id: String): JsonElement

    // ---- Wallet / plans / subscription / quota / bank / settlements ----
    @GET("api/wallet")
    suspend fun wallet(): WalletResponse

    @POST("api/wallet/topup")
    suspend fun topupWallet(@Body body: TopupRequest): JsonElement

    @GET("api/subscription")
    suspend fun subscription(): SubscriptionResponse

    @POST("api/subscription")
    suspend fun subscribe(@Body body: SubscribeRequest): JsonElement

    @GET("api/quota")
    suspend fun quota(): QuotaResponse

    @GET("api/bank-account")
    suspend fun bankAccount(): BankAccountResponse

    @PUT("api/bank-account")
    suspend fun saveBankAccount(@Body body: BankAccountRequest): JsonElement

    @GET("api/settlements")
    suspend fun settlements(): SettlementsResponse

    @POST("api/settlements")
    suspend fun requestSettlement(@Body body: SettlementRequest): JsonElement

    @GET("api/affiliate")
    suspend fun affiliate(): AffiliateResponse

    // ---- Skill assessments (freelancer) ----
    @GET("api/assessments/available")
    suspend fun assessmentsAvailable(): AssessmentsResponse

    @POST("api/assessments/{skill}/{level}/start")
    suspend fun startAssessment(
        @Path("skill") skill: String,
        @Path("level") level: Int,
        @Body body: EmptyBody = EmptyBody()
    ): StartAttemptResponse

    @GET("api/assessments/attempts/{attemptId}/questions")
    suspend fun attemptQuestions(@Path("attemptId") attemptId: String): AttemptQuestionsResponse

    @POST("api/assessments/attempts/{attemptId}/submit")
    suspend fun submitAttempt(@Path("attemptId") attemptId: String, @Body body: SubmitAnswersRequest): SubmitResultResponse

    // ---- In-app update (self-hosted version manifest) ----
    @GET("app/android-latest.json")
    suspend fun androidLatest(): UpdateInfo

    @GET("api/dashboard")
    suspend fun dashboard(): DashboardResponse

    // ---- Discovery (public) ----
    @GET("api/projects")
    suspend fun projects(
        @Query("q") q: String? = null,
        @Query("skills") skills: String? = null,
        @Query("projectType") projectType: String? = null,
        @Query("experienceLevel") experienceLevel: String? = null,
        @Query("minBudget") minBudget: String? = null,
        @Query("maxBudget") maxBudget: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1
    ): ProjectListResponse

    @GET("api/freelancers")
    suspend fun freelancers(
        @Query("q") q: String? = null,
        @Query("skills") skills: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1
    ): FreelancerListResponse

    @GET("api/matching/projects")
    suspend fun matching(): MatchingResponse

    @GET("api/public/metrics")
    suspend fun metrics(): MetricsResponse

    // ---- Projects (host) ----
    @POST("api/projects")
    suspend fun createProject(@Body body: ProjectRequest): CreatedProjectResponse

    @GET("api/my/projects")
    suspend fun myProjects(): MyProjectsResponse

    @POST("api/projects/{id}/publish")
    suspend fun publishProject(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): JsonElement

    @POST("api/projects/{id}/cancel")
    suspend fun cancelProject(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): JsonElement

    // ---- Proposals ----
    @POST("api/projects/{id}/proposals")
    suspend fun sendProposal(@Path("id") id: String, @Body body: ProposalRequest): JsonElement

    @GET("api/projects/{id}/proposals")
    suspend fun projectProposals(@Path("id") id: String): ProposalsResponse

    @POST("api/proposals/{id}/accept")
    suspend fun acceptProposal(@Path("id") id: String, @Body body: AcceptRequest): JsonElement

    // ---- Contracts / milestones / deliverables / disputes / ratings ----
    @GET("api/contracts")
    suspend fun contracts(): ContractsResponse

    @POST("api/milestones/{id}/funding-intents")
    suspend fun createFundingIntent(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): FundingIntentResponse

    @POST("api/milestones/{id}/deliverables")
    suspend fun submitDeliverable(@Path("id") id: String, @Body body: DeliverableRequest): JsonElement

    @POST("api/deliverables/{id}/revision")
    suspend fun requestRevision(@Path("id") id: String, @Body body: FeedbackRequest): JsonElement

    @POST("api/deliverables/{id}/accept")
    suspend fun acceptDeliverable(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): JsonElement

    @POST("api/milestones/{id}/disputes")
    suspend fun openDispute(@Path("id") id: String, @Body body: DisputeRequest): JsonElement

    @POST("api/milestones/{id}/ratings")
    suspend fun rateFreelancer(@Path("id") id: String, @Body body: RatingRequest): JsonElement

    @POST("api/milestones/{id}/client-ratings")
    suspend fun rateClient(@Path("id") id: String, @Body body: RatingRequest): JsonElement

    @POST("api/contracts/{id}/cancel")
    suspend fun cancelContract(@Path("id") id: String, @Body body: CancelContractRequest): JsonElement

    // ---- Conversations ----
    @GET("api/conversations")
    suspend fun conversations(): ConversationsResponse

    @GET("api/conversations/{id}/messages")
    suspend fun messages(@Path("id") id: String, @Query("after") after: String? = null): MessagesResponse

    @POST("api/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: MessageRequest): JsonElement

    @POST("api/proposals/{id}/conversation")
    suspend fun openConversation(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): OpenConversationResponse

    // ---- Attachments ----
    @POST("api/attachments")
    suspend fun uploadAttachment(@Query("name") name: String, @Body file: RequestBody): AttachmentUploadResponse

    @GET("api/attachments/{id}")
    suspend fun downloadAttachment(@Path("id") id: String): ResponseBody

    // ---- Notifications ----
    @GET("api/notifications")
    suspend fun notifications(): NotificationFeed

    @POST("api/notifications/read")
    suspend fun markNotificationsRead(@Body body: NotificationReadRequest = NotificationReadRequest()): UnreadResponse

    // ---- Saved jobs ----
    @POST("api/projects/{id}/save")
    suspend fun saveProject(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): SavedResponse

    @DELETE("api/projects/{id}/save")
    suspend fun unsaveProject(@Path("id") id: String): SavedResponse

    @GET("api/saved/projects")
    suspend fun savedProjects(): ProjectListResponse

    // ---- Invitations ----
    @POST("api/projects/{id}/invitations")
    suspend fun invite(@Path("id") id: String, @Body body: InviteRequest): JsonElement

    @GET("api/invitations")
    suspend fun invitations(): InvitationsResponse

    // ---- Trust & safety ----
    @POST("api/reports")
    suspend fun report(@Body body: ReportRequest): JsonElement

    // ---- Skill certificates ----
    @GET("api/profile/certificates")
    suspend fun myCertificates(): SkillCertificatesResponse

    // ---- Categories ----
    @GET("api/categories")
    suspend fun categories(): CategoriesResponse

    // ---- Admin extras ----
    @POST("api/admin/wallet/{userId}/adjust")
    suspend fun adminAdjustWallet(
        @Path("userId") userId: String,
        @Body body: AdminWalletAdjustRequest
    ): AdminWalletAdjustResponse

    @POST("api/admin/users")
    suspend fun adminCreateUser(@Body body: AdminCreateUserRequest): JsonElement

    @POST("api/admin/users/{userId}/reset-password")
    suspend fun adminResetPassword(
        @Path("userId") userId: String,
        @Body body: AdminResetPasswordRequest
    ): JsonElement

    @GET("api/admin/assessments/questions")
    suspend fun adminQuestions(
        @Query("skill") skill: String? = null,
        @Query("level") level: Int? = null
    ): AdminQuestionsResponse

    @POST("api/admin/assessments/questions")
    suspend fun adminCreateQuestion(@Body body: AdminQuestionRequest): JsonElement

    @PUT("api/admin/assessments/questions/{id}")
    suspend fun adminUpdateQuestion(@Path("id") id: String, @Body body: AdminQuestionRequest): JsonElement

    @DELETE("api/admin/assessments/questions/{id}")
    suspend fun adminDeleteQuestion(@Path("id") id: String): JsonElement
}
